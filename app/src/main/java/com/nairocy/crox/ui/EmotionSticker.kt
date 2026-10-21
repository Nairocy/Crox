package com.nairocy.crox.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nairocy.crox.data.ImagePipeline
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val STICKER_SIZE = 176.dp
private const val STICKER_DECODE_PX = 512
private const val VIEWER_DECODE_PX = 1080

/** 本地文件异步解码（IO 线程），成功后 150ms fade-in。 */
@Composable
private fun rememberStickerBitmap(file: File, decodePx: Int): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, file, decodePx) {
        value = withContext(Dispatchers.IO) { ImagePipeline.decodeThumb(file, decodePx) }
    }

/**
 * AI 消息尾部的表情贴纸：固定 176×176 直角盒内 Fit 居中，点击进入全屏查看。
 * 调用前须确保文件存在（拿不到文件则整块不渲染）。
 */
@Composable
fun EmotionSticker(file: File, modifier: Modifier = Modifier) {
    val bitmap by rememberStickerBitmap(file, STICKER_DECODE_PX)
    var viewerOpen by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(150),
        label = "stickerFade",
    )
    Box(modifier.size(STICKER_SIZE), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "表情包",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
                    .sqClickable { viewerOpen = true },
            )
        }
    }
    if (viewerOpen) {
        EmotionStickerViewer(file = file, onDismiss = { viewerOpen = false })
    }
}

/** 全屏查看：黑底、Fit 居中（1080px 解码），点击任意处关闭。 */
@Composable
fun EmotionStickerViewer(file: File, onDismiss: () -> Unit) {
    val bitmap by rememberStickerBitmap(file, VIEWER_DECODE_PX)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sqClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
