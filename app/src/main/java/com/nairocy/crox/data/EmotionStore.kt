package com.nairocy.crox.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale
import kotlin.random.Random

/**
 * 表情包存储：应用专属外部存储 `…/Android/data/com.nairocy.crox/emotion/<word>/`。
 * 无需任何运行时权限。所有方法均为阻塞 IO，调用方必须在 IO 线程执行。
 */
object EmotionStore {

    /** 8 个情绪目录（顺序即设置页展示顺序）。 */
    val WORDS: List<String> = listOf(
        "happy", "sad", "angry", "surprised", "love", "confused", "fearful", "neutral",
    )

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    private val MIME_EXT = mapOf(
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "image/gif" to "gif",
        "image/bmp" to "bmp",
        "image/x-ms-bmp" to "bmp",
    )

    private val LABELS = mapOf(
        "happy" to "开心",
        "sad" to "难过",
        "angry" to "生气",
        "surprised" to "惊讶",
        "love" to "喜欢",
        "confused" to "困惑",
        "fearful" to "害怕",
        "neutral" to "平静",
    )

    @Volatile
    private var cachedRoot: File? = null

    /** 中文行名（设置页展示用），未知词返回 null。 */
    fun label(word: String): String? = LABELS[word]

    /** 创建根目录与 8 个情绪子目录；返回根目录是否可用。 */
    fun ensureDirs(context: Context): Boolean {
        val root = rootDir(context) ?: return false
        var ok = true
        WORDS.forEach { word ->
            val dir = File(root, word)
            if (!dir.isDirectory && !dir.mkdirs()) ok = false
        }
        return ok
    }

    /** 各情绪目录内图片数（缺失目录计 0）。 */
    fun counts(context: Context): Map<String, Int> =
        WORDS.associateWith { word -> listImages(context, word).size }

    /** 目录内随机一张图片；目录缺失或为空返回 null。 */
    fun randomFile(context: Context, word: String): File? {
        val files = listImages(context, word)
        if (files.isEmpty()) return null
        return files[Random.nextInt(files.size)]
    }

    /**
     * 导入图片：字节原样拷贝（不解码不压缩）。
     * 文件名 `System.currentTimeMillis()_序号.扩展名`，扩展名取 mimeType 或原文件名，兜底 jpg。
     * @return Pair(成功数, 失败数)
     */
    fun importImages(context: Context, word: String, uris: List<Uri>): Pair<Int, Int> {
        val root = rootDir(context) ?: return 0 to uris.size
        val dir = File(root, safeName(word)).apply { if (!isDirectory) mkdirs() }
        if (!dir.isDirectory) return 0 to uris.size
        var success = 0
        var failed = 0
        val stamp = System.currentTimeMillis()
        uris.forEachIndexed { index, uri ->
            val target = File(dir, "${stamp}_${index + 1}.${extensionOf(context, uri)}")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("input stream unavailable")
                if (target.length() <= 0L) error("empty file")
            }.isSuccess
            if (copied) {
                success++
            } else {
                target.delete()
                failed++
            }
        }
        return success to failed
    }

    /** 删除该情绪目录内全部图片，返回删除数量。 */
    fun clear(context: Context, word: String): Int =
        listImages(context, word).count { it.delete() }

    /** 实际生效的根目录路径（可能为空字符串：外部存储不可用）。 */
    fun dirPath(context: Context): String = rootDir(context)?.absolutePath.orEmpty()

    // ---------- 内部 ----------

    private fun rootDir(context: Context): File? {
        cachedRoot?.let { return it }
        val base = context.getExternalFilesDir(null) ?: return null
        val primary = File(base.parentFile, "emotion")
        val root = if (primary.mkdirs() || primary.isDirectory) primary else File(base, "emotion")
        if (!root.isDirectory && !root.mkdirs()) return null
        cachedRoot = root
        return root
    }

    private fun listImages(context: Context, word: String): List<File> {
        val root = rootDir(context) ?: return emptyList()
        val dir = File(root, safeName(word))
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && it.extension.lowercase(Locale.US) in IMAGE_EXTS }
    }

    private fun safeName(word: String): String =
        word.lowercase(Locale.US).filter { it.isLetterOrDigit() || it == '_' }.take(32)

    private fun extensionOf(context: Context, uri: Uri): String {
        val mime = runCatching { context.contentResolver.getType(uri) }
            .getOrNull()
            ?.lowercase(Locale.US)
        MIME_EXT[mime]?.let { return it }
        val name = uri.lastPathSegment.orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return if (ext in IMAGE_EXTS) ext else "jpg"
    }
}

// 完整标签（词表内）：允许结尾 '>' 缺失、允许尾随空白/零宽字符（模型偶发漏 '>' 或混入不可见字符）
private val EMO_TAG_SUFFIX = Regex(
    "\\n*<emo>(happy|sad|angry|surprised|love|confused|fearful|neutral)</emo>?[ \\t\\n\\u00a0\\u200b\\ufeff]*$",
    RegexOption.IGNORE_CASE,
)

// 任意词形的收尾标签（表外词如 funny/tired）：仅用于展示剥离，不出贴纸
private val EMO_TAG_ANY_SUFFIX = Regex(
    "\\n*<emo>([a-z]{1,24})</emo>?[ \\t\\n\\u00a0\\u200b\\ufeff]*$",
    RegexOption.IGNORE_CASE,
)

private val EMO_WORDS: Set<String> = EmotionStore.WORDS.toSet()

/**
 * 从消息文本剥离尾部表情标记 `<emo>word</emo>`。
 * - 词表内（含缺 '>' / 尾随不可见字符容错）→ 返回 (剥离文本, word)，可出贴纸
 * - 表外词（模型偶发漏词，如 funny/tired）→ 剥离文本但 word=null（不出图）
 * - streaming=true：额外截断未写完的 `\n\n<emo…` 尾部（防打字机漏标签闪现）
 */
fun splitEmo(text: String, streaming: Boolean): Pair<String, String?> {
    if (text.isEmpty()) return text to null
    EMO_TAG_SUFFIX.find(text)?.let { m ->
        val word = m.groupValues[1].lowercase(Locale.US)
        if (word in EMO_WORDS) return text.substring(0, m.range.first) to word
    }
    EMO_TAG_ANY_SUFFIX.find(text)?.let { m ->
        return text.substring(0, m.range.first) to null
    }
    if (streaming) {
        var cut = text.lastIndexOf("\n\n<emo")
        if (cut < 0) cut = text.lastIndexOf("\n<emo")
        if (cut >= 0) return text.substring(0, cut) to null
    }
    return text to null
}
