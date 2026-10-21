package com.nairocy.crox.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** 图片处理结果：成功给出压缩后的 JPEG 字节；失败给出可读短原因。 */
sealed interface ImageResult {
    class Success(val bytes: ByteArray) : ImageResult
    class Failure(val reason: FailureReason, val detail: String?) : ImageResult
}

enum class FailureReason(val label: String) {
    Unreadable("无法读取"),
    Unsupported("格式不支持"),
    OutOfMemory("内存不足"),
}

/**
 * 图片管线：Photo Picker 输出 → EXIF 方向校正 → 最长边 1280 缩放 → JPEG q85 字节。
 * 多路降级：输入流 openInputStream → openAssetFileDescriptor；解码 BitmapFactory 失败/OOM
 * 逐级加大 inSampleSize 重试（最多 3 次），仍失败则走 API 28+ ImageDecoder（HEIC 等）。
 */
object ImagePipeline {

    const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 85
    private const val DECODE_ATTEMPTS = 4
    private const val MAX_SAMPLE = 64
    private const val TAG = "CroxImage"

    fun compress(context: Context, uri: Uri): ImageResult {
        val source = try {
            readBytes(context, uri)
        } catch (t: Throwable) {
            Log.w(TAG, "read failed uri=$uri", t)
            return ImageResult.Failure(FailureReason.Unreadable, describe(t))
        }
        if (source.isEmpty()) {
            Log.w(TAG, "empty stream uri=$uri")
            return ImageResult.Failure(FailureReason.Unreadable, "empty stream")
        }

        val orientation = readOrientation(source)

        val decoded = try {
            decode(source, uri)
        } catch (e: DecodeException) {
            Log.w(TAG, "decode failed uri=$uri bytes=${source.size}: ${e.message}", e.cause ?: e)
            return ImageResult.Failure(e.reason, e.message)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "decode oom uri=$uri bytes=${source.size}", oom)
            return ImageResult.Failure(FailureReason.OutOfMemory, describe(oom))
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed uri=$uri bytes=${source.size}", t)
            return ImageResult.Failure(FailureReason.Unsupported, describe(t))
        }

        var bitmap = decoded.bitmap
        try {
            if (!decoded.orientationApplied) {
                applyOrientation(bitmap, orientation)?.let {
                    if (it !== bitmap) {
                        bitmap.recycle()
                        bitmap = it
                    }
                }
            }
            scaleToMaxSide(bitmap, MAX_SIDE)?.let {
                if (it !== bitmap) {
                    bitmap.recycle()
                    bitmap = it
                }
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            return ImageResult.Success(out.toByteArray())
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "post-process oom uri=$uri", oom)
            return ImageResult.Failure(FailureReason.OutOfMemory, describe(oom))
        } catch (t: Throwable) {
            Log.w(TAG, "post-process failed uri=$uri", t)
            return ImageResult.Failure(FailureReason.Unsupported, describe(t))
        } finally {
            bitmap.recycle()
        }
    }

    /** 本地文件缩略图解码（预览/消息缩略图）。 */
    fun decodeThumb(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        val options = BitmapFactory.Options()
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            options.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide, exact = true)
        }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "thumb oom file=${file.name}", oom)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "thumb failed file=${file.name}", t)
            null
        }
    }

    // ---------- 输入流获取（降级链） ----------

    private fun readBytes(context: Context, uri: Uri): ByteArray =
        openStream(context, uri).use { it.readBytes() }

    private fun openStream(context: Context, uri: Uri): InputStream {
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(uri)?.let { return it }
        } catch (t: Throwable) {
            Log.w(TAG, "openInputStream failed uri=$uri", t)
        }
        val descriptor = try {
            resolver.openAssetFileDescriptor(uri, "r")
        } catch (t: Throwable) {
            Log.w(TAG, "openAssetFileDescriptor failed uri=$uri", t)
            null
        }
        if (descriptor != null) {
            try {
                return descriptor.createInputStream()
            } catch (t: Throwable) {
                runCatching { descriptor.close() }
                Log.w(TAG, "createInputStream failed uri=$uri", t)
            }
        }
        throw IOException("cannot open input stream for $uri")
    }

    // ---------- 解码（bounds → 采样 → 逐级重试 → ImageDecoder） ----------

    private class Decoded(val bitmap: Bitmap, val orientationApplied: Boolean)

    private class DecodeException(
        message: String,
        val reason: FailureReason,
        cause: Throwable? = null,
    ) : Exception(message, cause)

    private fun decode(source: ByteArray, uri: Uri): Decoded {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val hasBounds = try {
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        } catch (t: Throwable) {
            Log.w(TAG, "bounds probe failed uri=$uri", t)
            false
        }

        var sample = if (hasBounds) {
            sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_SIDE, exact = false)
        } else {
            1
        }
        var sawOom = false
        var last: Throwable? = null

        for (attempt in 0 until DECODE_ATTEMPTS) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, options)
                if (bitmap != null) return Decoded(bitmap, orientationApplied = false)
                last = IOException("decoder returned null (sample=$sample)")
                Log.w(TAG, "decode null uri=$uri sample=$sample")
            } catch (oom: OutOfMemoryError) {
                sawOom = true
                last = oom
                Log.w(TAG, "decode oom uri=$uri sample=$sample", oom)
            } catch (t: Throwable) {
                last = t
                Log.w(TAG, "decode failed uri=$uri sample=$sample", t)
            }
            sample = (sample * 2).coerceAtMost(MAX_SAMPLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return Decoded(decodeWithImageDecoder(source), orientationApplied = true)
            } catch (oom: OutOfMemoryError) {
                sawOom = true
                last = oom
                Log.w(TAG, "ImageDecoder oom uri=$uri", oom)
            } catch (t: Throwable) {
                last = t
                Log.w(TAG, "ImageDecoder failed uri=$uri", t)
            }
        }

        throw DecodeException(
            message = describe(last ?: IOException("unsupported image")),
            reason = if (sawOom) FailureReason.OutOfMemory else FailureReason.Unsupported,
            cause = last,
        )
    }

    private fun decodeWithImageDecoder(source: ByteArray): Bitmap {
        val decoderSource = ImageDecoder.createSource(ByteBuffer.wrap(source))
        return ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > MAX_SIDE && longest > 0) {
                val ratio = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (width * ratio).roundToInt().coerceAtLeast(1),
                    (height * ratio).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    // ---------- EXIF（独立容错，不阻断） ----------

    private fun readOrientation(source: ByteArray): Int = try {
        ByteArrayInputStream(source).use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    } catch (t: Throwable) {
        Log.w(TAG, "exif read failed, assume normal", t)
        ExifInterface.ORIENTATION_NORMAL
    }

    // ---------- 缩放 / 方向 ----------

    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int, exact: Boolean): Int {
        var sample = 1
        val longest = max(width, height)
        if (exact) {
            while (longest / sample > maxSide) sample *= 2
        } else {
            while (longest / (sample * 2) >= maxSide) sample *= 2
        }
        return sample
    }

    private fun scaleToMaxSide(src: Bitmap, maxSide: Int): Bitmap? {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return null
        val ratio = maxSide.toFloat() / longest
        val width = (src.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    private fun applyOrientation(src: Bitmap, orientation: Int): Bitmap? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return null
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun describe(t: Throwable): String = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
}
