package com.nairocy.crox.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 本地会话持久化：
 * - filesDir/sessions/<id>.json（kotlinx-serialization）
 * - filesDir/images/<id>.jpg（发送前压缩产物）
 */
class SessionStore(context: Context) {

    private val sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
    private val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun list(): List<Session> {
        val files = sessionsDir.listFiles { f -> f.isFile && f.extension == "json" } ?: return emptyList()
        return files
            .mapNotNull { f -> runCatching { json.decodeFromString<Session>(f.readText()) }.getOrNull() }
            .sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Session? {
        val file = File(sessionsDir, "$id.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Session>(file.readText()) }.getOrNull()
    }

    fun save(session: Session) {
        File(sessionsDir, "${session.id}.json").writeText(json.encodeToString(session))
    }

    fun delete(id: String) {
        load(id)?.messages
            ?.flatMap { it.imageIds }
            ?.forEach { imageFile(it).delete() }
        File(sessionsDir, "$id.json").delete()
    }

    fun saveImage(bytes: ByteArray): String {
        val id = "img_" + UUID.randomUUID().toString().replace("-", "").take(16)
        imageFile(id).writeBytes(bytes)
        return id
    }

    fun imageFile(id: String): File = File(imagesDir, "$id.jpg")

    fun loadImage(id: String): ByteArray? = imageFile(id).takeIf { it.exists() }?.readBytes()

    fun newId(): String = UUID.randomUUID().toString().replace("-", "")
}
