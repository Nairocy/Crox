package com.nairocy.crox.data

import com.nairocy.crox.ChatMode
import com.nairocy.crox.Terminal
import com.nairocy.crox.ThemeMode
import kotlinx.serialization.Serializable

object Roles {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
}

@Serializable
data class Message(
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    val ts: Long = 0L,
    val imageIds: List<String> = emptyList(),
) {
    val charCost: Int
        get() = content.length + imageIds.size * IMAGE_CHAR_COST

    companion object {
        const val IMAGE_CHAR_COST = 1500
    }
}

@Serializable
data class Session(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val messages: List<Message> = emptyList(),
)

data class SettingsData(
    val token: String = "",
    val terminal: Terminal = Terminal.T1,
    val mode: ChatMode = ChatMode.Chat,
    val thinking: Boolean = true,
    val temperature: Float = 0.85f,
    val maxTokens: String = "16384",
    val systemPrompt: String = "",
    val reasonBudget: String = "256",
    val themeMode: ThemeMode = ThemeMode.System,
)
