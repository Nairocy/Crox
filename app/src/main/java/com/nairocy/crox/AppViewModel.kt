package com.nairocy.crox

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nairocy.crox.data.EmotionStore
import com.nairocy.crox.data.ImagePipeline
import com.nairocy.crox.data.ImageResult
import com.nairocy.crox.data.Message
import com.nairocy.crox.data.Roles
import com.nairocy.crox.data.Session
import com.nairocy.crox.data.SessionStore
import com.nairocy.crox.data.SettingsStore
import com.nairocy.crox.data.splitEmo
import com.nairocy.crox.net.ApiClient
import com.nairocy.crox.net.ChatListener
import com.nairocy.crox.util.plainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Terminal(val label: String, val hero: String, val sub: String) {
    T1("T2", "T2", "CROX · THINKING TERMINAL"),
    Persona("P2", "P2", "CROX · PERSONA TERMINAL"),
}

enum class ChatMode(val label: String) {
    Chat("对话"),
    Raw("续写"),
}

enum class ThemeMode(val label: String) {
    Light("浅色"),
    Dark("深色"),
    System("自动"),
}

enum class DrawerPage {
    History,
    Settings,
}

enum class MsgRole {
    User,
    Ai,
}

sealed interface SwitchState {
    data object Ready : SwitchState
    data object Waking : SwitchState
    data class Error(val message: String) : SwitchState
}

/** 语音模型状态：首次点「朗读」触发下载；下载中再点=取消。 */
data class UiMessage(
    val id: Int,
    val role: MsgRole,
    val text: String,
    val time: String,
    val reasoning: String? = null,
    val reasoningChars: Int = 0,
    val reasoningExpanded: Boolean = false,
    val throughput: String? = null,
    val imageIds: List<String> = emptyList(),
    val streaming: Boolean = false,
)

data class SessionMeta(
    val id: String,
    val no: String,
    val title: String,
    val meta: String,
)

data class AppState(
    val terminal: Terminal = Terminal.T1,
    val switchState: SwitchState = SwitchState.Waking,
    val sessions: List<SessionMeta> = emptyList(),
    val currentSessionId: String? = null,
    val selectedSession: Int = -1,
    val messages: List<UiMessage> = emptyList(),
    val streaming: Boolean = false,
    val pendingImages: List<File> = emptyList(),
    val errorText: String? = null,
    val notice: String? = null,
    val drawerOpen: Boolean = false,
    val drawerPage: DrawerPage = DrawerPage.History,
    val mode: ChatMode = ChatMode.Chat,
    val thinking: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val draft: String = "",
    val token: String = "",
    val tokenSaved: Boolean = false,
    val reasonBudget: String = "256",
    val temperature: Float = 0.85f,
    val maxTokens: String = "16384",
    val systemPrompt: String = "",
    val emotionCounts: Map<String, Int> = emptyMap(),
    val emotionDir: String = "",
    val emotionImporting: Boolean = false,
    val emotionStatus: String? = null,
) {
    val busy: Boolean
        get() = streaming || switchState is SwitchState.Waking

    val title: String
        get() {
            val id = currentSessionId
            if (id != null) {
                val stored = sessions.firstOrNull { it.id == id }?.title
                if (!stored.isNullOrBlank()) return stored
            }
            val firstUser = messages.firstOrNull { it.role == MsgRole.User && it.text.isNotBlank() }
            return firstUser?.text?.take(24) ?: "新对话"
        }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private class StreamContext(
        val sessionId: String,
        val messageId: Int,
        val history: MutableList<Message>,
        val startedAt: Long,
        val thinking: Boolean,
    ) {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        var lastUiPushAt = 0L
        var deltasSincePush = 0
    }

    private val settingsStore = SettingsStore(app)
    private val sessionStore = SessionStore(app)
    private val appContext: Application = app

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val history = mutableListOf<Message>()
    private val deletedSessionIds = mutableSetOf<String>()
    private val persistLock = Any()

    private var nextMessageId = 1
    private var streamContext: StreamContext? = null
    private var activeCall: Call? = null
    private var activationJob: Job? = null
    private var noticeJob: Job? = null
    private var lastThroughput: String? = null

    init {
        val loaded = settingsStore.load()
        _state.update {
            it.copy(
                terminal = loaded.terminal,
                mode = loaded.mode,
                thinking = loaded.thinking,
                themeMode = loaded.themeMode,
                token = loaded.token,
                temperature = loaded.temperature,
                maxTokens = loaded.maxTokens,
                systemPrompt = loaded.systemPrompt,
                reasonBudget = loaded.reasonBudget,
            )
        }
        refreshSessions()
        activate(loaded.terminal)
    }

    // ---------- 终端唤醒 ----------

    private fun activate(terminal: Terminal) {
        activationJob?.cancel()
        activationJob = viewModelScope.launch {
            _state.update { it.copy(switchState = SwitchState.Waking) }
            val ready = withContext(Dispatchers.IO) {
                if (!ApiClient.ensure(terminal)) return@withContext false
                val deadline = System.currentTimeMillis() + HEALTH_POLL_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    if (ApiClient.health(terminal)) return@withContext true
                    delay(HEALTH_POLL_INTERVAL_MS)
                }
                false
            }
            _state.update {
                if (it.terminal != terminal) it
                else it.copy(
                    switchState = if (ready) {
                        SwitchState.Ready
                    } else {
                        SwitchState.Error("无法连接服务，请稍后重试")
                    },
                )
            }
        }
    }

    fun switchTerminal() {
        val snapshot = _state.value
        // 可抢占：唤醒中也能切（activate() 会 cancel 旧任务；terminal 守卫防旧状态回写）
        val next = if (snapshot.terminal == Terminal.T1) Terminal.Persona else Terminal.T1
        settingsStore.terminal = next
        _state.update { it.copy(terminal = next) }
        activate(next)
    }

    // ---------- 发送 / 流式 ----------

    fun send() {
        val snapshot = _state.value
        val text = snapshot.draft.trim()
        if (text.isEmpty() && snapshot.pendingImages.isEmpty()) return
        if (snapshot.streaming || snapshot.switchState is SwitchState.Waking) return
        if (snapshot.mode == ChatMode.Raw && snapshot.pendingImages.isNotEmpty()) {
            showNotice("续写模式不支持图片，请切回对话模式")
            return
        }

        val now = System.currentTimeMillis()
        val imageIds = snapshot.pendingImages.mapNotNull { file ->
            runCatching {
                val bytes = file.readBytes()
                sessionStore.saveImage(bytes)
            }.getOrNull()
        }
        snapshot.pendingImages.forEach { it.delete() }

        val userMessage = Message(role = Roles.USER, content = text, ts = now, imageIds = imageIds)
        history += userMessage
        persistCurrentSession()
        if (_state.value.currentSessionId == null) return

        val userUi = userMessage.toUi(nextMessageId++)
        _state.update {
            it.copy(messages = it.messages + userUi, draft = "", pendingImages = emptyList())
        }
        launchAssistantStream(snapshot.mode, rawPrompt = text.takeIf { snapshot.mode == ChatMode.Raw })
    }

    /** 发起一次流式生成：在当前 messages 末尾追加 AI 占位，以上文 history 为上下文。 */
    private fun launchAssistantStream(mode: ChatMode, rawPrompt: String?) {
        val snapshot = _state.value
        val sessionId = snapshot.currentSessionId ?: return
        val now = System.currentTimeMillis()
        val aiId = nextMessageId++
        val aiUi = UiMessage(id = aiId, role = MsgRole.Ai, text = "", time = timeOf(now), streaming = true)
        val context = StreamContext(sessionId, aiId, history.toMutableList(), now, snapshot.thinking)
        streamContext = context
        lastThroughput = null

        _state.update {
            it.copy(
                messages = it.messages + aiUi,
                streaming = true,
                errorText = null,
                notice = null,
            )
        }

        val listener = object : ChatListener {
            override fun onDelta(content: String, reasoning: String) =
                pushDelta(context, content, reasoning)

            override fun onTimings(predictedPerSecond: Double) {
                val elapsed = (System.currentTimeMillis() - context.startedAt) / 1000.0
                lastThroughput = String.format(
                    Locale.US,
                    "%.1fs · %d tok/s",
                    elapsed,
                    predictedPerSecond.roundToInt(),
                )
            }

            override fun onDone() = finishStream(null, completed = true)

            override fun onStopped() = finishStream(null)

            override fun onError(message: String) = finishStream(message)
        }
        activeCall = if (mode == ChatMode.Raw && rawPrompt != null) {
            // 续写：/completion 端点（纯补全，prompt=输入原文，不拼历史、无思考段）
            ApiClient.complete(
                terminal = snapshot.terminal,
                token = snapshot.token.trim(),
                payload = buildRawPayload(snapshot, rawPrompt),
                listener = listener,
            )
        } else {
            ApiClient.chat(
                terminal = snapshot.terminal,
                token = snapshot.token.trim(),
                payload = buildPayload(snapshot, context.history),
                listener = listener,
            )
        }
    }

    /** 流式增量：思考关闭时丢弃 reasoning（对齐 web 行为；模型可能仍输出少量思考） */
    private fun pushDelta(context: StreamContext, content: String, reasoning: String) {
        if (content.isEmpty() && (reasoning.isEmpty() || !context.thinking)) return
        context.content.append(content)
        if (context.thinking) context.reasoning.append(reasoning)
        val now = System.currentTimeMillis()
        context.deltasSincePush++
        val due = now - context.lastUiPushAt >= STREAM_UI_THROTTLE_MS ||
            context.deltasSincePush >= STREAM_UI_DELTA_BURST
        if (!due) return
        context.lastUiPushAt = now
        context.deltasSincePush = 0
        _state.update { s ->
            s.copy(messages = s.messages.map { m ->
                if (m.id != context.messageId) m
                else m.copy(
                    text = context.content.toString(),
                    reasoning = context.reasoning.toString().takeIf { it.isNotEmpty() },
                    reasoningChars = context.reasoning.length,
                    reasoningExpanded = reasoning.isNotEmpty() || m.reasoningExpanded,
                )
            })
        }
    }

    fun stop() {
        activeCall?.cancel()
    }

    private fun finishStream(error: String?, completed: Boolean = false) {
        val context = streamContext ?: return
        val assistantText = context.content.toString()
        val assistantReasoning = context.reasoning.toString()
        val hasContent = assistantText.isNotEmpty() || assistantReasoning.isNotEmpty()
        val throughput = lastThroughput
        val finishedAt = System.currentTimeMillis()

        synchronized(persistLock) {
            if (hasContent) {
                context.history += Message(
                    role = Roles.ASSISTANT,
                    content = assistantText,
                    reasoning = assistantReasoning,
                    ts = finishedAt,
                )
                saveSession(context.sessionId, context.history)
                if (_state.value.currentSessionId == context.sessionId) {
                    history.clear()
                    history.addAll(context.history)
                    refreshSessions()
                }
            }
        }

        _state.update { s ->
            val isCurrent = s.currentSessionId == context.sessionId
            var messages = s.messages
            if (isCurrent && hasContent && messages.none { it.id == context.messageId }) {
                messages = messages + UiMessage(
                    id = context.messageId,
                    role = MsgRole.Ai,
                    text = assistantText,
                    time = timeOf(finishedAt),
                    reasoning = assistantReasoning.takeIf { it.isNotEmpty() },
                    reasoningChars = assistantReasoning.length,
                    throughput = throughput,
                )
            }
            messages = messages
                .filterNot { !hasContent && it.id == context.messageId }
                .map { m ->
                    if (m.id != context.messageId) m
                    else m.copy(
                        text = assistantText,
                        reasoning = assistantReasoning.takeIf { it.isNotEmpty() },
                        reasoningChars = assistantReasoning.length,
                        reasoningExpanded = false,
                        streaming = false,
                        throughput = throughput,
                    )
                }
            s.copy(streaming = false, errorText = error, messages = messages)
        }
        streamContext = null
        activeCall = null

    }

    private fun buildPayload(snapshot: AppState, messages: List<Message>): String {
        var remaining = HISTORY_BUDGET
        val kept = ArrayDeque<Message>()
        for (i in messages.indices.reversed()) {
            val message = messages[i]
            if (message.charCost > remaining && kept.isNotEmpty()) break
            remaining -= message.charCost
            kept.addFirst(message)
        }

        val payloadMessages = buildJsonArray {
            val systemPrompt = snapshot.systemPrompt.trim()
            if (systemPrompt.isNotEmpty()) {
                add(buildJsonObject {
                    put("role", Roles.SYSTEM)
                    put("content", systemPrompt)
                })
            }
            kept.forEach { message ->
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", messageContent(message))
                })
            }
        }

        val maxTokens = snapshot.maxTokens.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS
        val reasonBudget = snapshot.reasonBudget.toIntOrNull() ?: 0
        val temperature = (snapshot.temperature * 100).roundToInt() / 100.0

        return buildJsonObject {
            put("messages", payloadMessages)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("top_p", 0.9)
            put("repeat_penalty", 1.1)
            put("stream", true)
            if (!snapshot.thinking) {
                putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
            }
            if (snapshot.thinking && reasonBudget > 0) {
                put("reasoning_budget_tokens", reasonBudget)
            }
        }.toString()
    }

    /** 续写模式 payload（对齐 web doRaw）：prompt=输入原文，n_predict=maxTokens */
    private fun buildRawPayload(snapshot: AppState, text: String): String {
        val maxTokens = snapshot.maxTokens.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS
        val temperature = (snapshot.temperature * 100).roundToInt() / 100.0
        return buildJsonObject {
            put("prompt", text)
            put("n_predict", maxTokens)
            put("temperature", temperature)
            put("top_p", 0.9)
            put("repeat_penalty", 1.1)
            put("stream", true)
        }.toString()
    }

    private fun messageContent(message: Message): JsonElement {
        if (message.role != Roles.USER || message.imageIds.isEmpty()) {
            return JsonPrimitive(message.content)
        }
        return buildJsonArray {
            if (message.content.isNotEmpty()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", message.content)
                })
            }
            message.imageIds.forEach { id ->
                val bytes = runCatching { sessionStore.loadImage(id) }.getOrNull() ?: return@forEach
                add(buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
                    }
                })
            }
        }
    }

    // ---------- 会话 ----------

    private fun refreshSessions() {
        val metas = sessionStore.list().mapIndexed { index, session ->
            SessionMeta(
                id = session.id,
                no = String.format(Locale.US, "%03d", index + 1),
                title = session.title.ifBlank { "新对话" },
                meta = formatSessionMeta(session.updatedAt, session.messages.size),
            )
        }
        _state.update { s ->
            s.copy(sessions = metas, selectedSession = metas.indexOfFirst { it.id == s.currentSessionId })
        }
    }

    private fun persistCurrentSession() {
        synchronized(persistLock) {
            if (history.isEmpty()) return
            val existingId = _state.value.currentSessionId
            val id = existingId ?: sessionStore.newId()
            if (existingId == null) _state.update { it.copy(currentSessionId = id) }
            saveSession(id, history.toList())
            refreshSessions()
        }
    }

    private fun saveSession(id: String, messages: List<Message>) {
        if (id in deletedSessionIds) return
        val now = System.currentTimeMillis()
        val existing = sessionStore.load(id)
        sessionStore.save(
            Session(
                id = id,
                title = existing?.title?.takeIf { it.isNotBlank() } ?: titleFromMessages(messages),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                messages = messages,
            ),
        )
    }

    private fun titleFromMessages(messages: List<Message>): String {
        val first = messages.firstOrNull { it.role == Roles.USER && it.content.isNotBlank() }
        return first?.content?.replace(Regex("\\s+"), " ")?.trim()?.take(TITLE_MAX_CHARS).orEmpty()
    }

    fun newChat() {
        if (_state.value.streaming) stop()
        synchronized(persistLock) { history.clear() }
        nextMessageId = 1
        _state.update {
            it.copy(
                currentSessionId = null,
                selectedSession = -1,
                messages = emptyList(),
                draft = "",
                pendingImages = emptyList(),
                errorText = null,
                notice = null,
                drawerOpen = false,
            )
        }
    }

    fun clearConversation() {
        val id = _state.value.currentSessionId
        if (_state.value.streaming) stop()
        if (id != null) {
            deletedSessionIds += id
            sessionStore.delete(id)
        }
        synchronized(persistLock) { history.clear() }
        nextMessageId = 1
        _state.update {
            it.copy(
                currentSessionId = null,
                selectedSession = -1,
                messages = emptyList(),
                draft = "",
                pendingImages = emptyList(),
                errorText = null,
            )
        }
        refreshSessions()
    }

    fun selectSession(index: Int) {
        val current = _state.value
        val meta = current.sessions.getOrNull(index) ?: return
        if (meta.id == current.currentSessionId) {
            _state.update { it.copy(drawerOpen = false) }
            return
        }
        val session = sessionStore.load(meta.id) ?: return
        synchronized(persistLock) {
            history.clear()
            history.addAll(session.messages)
        }
        nextMessageId = session.messages.size + 1
        _state.update { s ->
            s.copy(
                currentSessionId = session.id,
                selectedSession = index,
                messages = session.messages.mapIndexed { i, m -> m.toUi(i + 1) },
                draft = "",
                pendingImages = emptyList(),
                errorText = null,
                notice = null,
                drawerOpen = false,
            )
        }
    }

    fun deleteSession(index: Int) {
        val meta = _state.value.sessions.getOrNull(index) ?: return
        deletedSessionIds += meta.id
        if (_state.value.currentSessionId == meta.id) {
            if (_state.value.streaming) stop()
            synchronized(persistLock) { history.clear() }
            nextMessageId = 1
        }
        sessionStore.delete(meta.id)
        _state.update { s ->
            if (s.currentSessionId == meta.id) {
                s.copy(
                    currentSessionId = null,
                    selectedSession = -1,
                    messages = emptyList(),
                    draft = "",
                    pendingImages = emptyList(),
                    errorText = null,
                )
            } else {
                s
            }
        }
        refreshSessions()
    }

    // ---------- 图片 ----------

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (_state.value.mode == ChatMode.Raw) {
            showNotice("续写模式不支持图片，请切回对话模式")
            return
        }
        val slots = MAX_IMAGES - _state.value.pendingImages.size
        if (slots <= 0) {
            showNotice("单条最多 4 张图片")
            return
        }
        val accepted = uris.take(slots)
        val targetSessionId = _state.value.currentSessionId
        val app = getApplication<Application>()
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                accepted.map { uri -> ImagePipeline.compress(app, uri) }
            }
            val files = results.mapNotNull { result ->
                if (result is ImageResult.Success) savePendingImage(result.bytes) else null
            }
            val failures = results.filterIsInstance<ImageResult.Failure>()
            if (uris.size > accepted.size) {
                showNotice("单条最多 4 张图片")
            } else if (failures.isNotEmpty()) {
                val detail = failures
                    .groupingBy { it.reason.label }
                    .eachCount()
                    .entries
                    .joinToString(" / ") { "${it.key}×${it.value}" }
                showNotice("部分图片处理失败：$detail")
            } else if (files.size < accepted.size) {
                showNotice("图片保存失败，请重试")
            }
            if (files.isNotEmpty()) {
                _state.update { s ->
                    if (s.currentSessionId != targetSessionId) {
                        s
                    } else {
                        val room = (MAX_IMAGES - s.pendingImages.size).coerceAtLeast(0)
                        s.copy(pendingImages = s.pendingImages + files.take(room))
                    }
                }
                val kept = _state.value.pendingImages.toSet()
                files.filterNot { it in kept }.forEach { it.delete() }
            }
        }
    }

    private fun savePendingImage(bytes: ByteArray): File? {
        val dir = File(getApplication<Application>().cacheDir, "pending").apply { mkdirs() }
        val file = File(dir, "pending_${sessionStore.newId()}.jpg")
        return runCatching {
            file.writeBytes(bytes)
            file
        }.getOrNull()
    }

    fun removePendingImage(file: File) {
        file.delete()
        _state.update { it.copy(pendingImages = it.pendingImages - file) }
    }

    fun imageFileFor(id: String): File? = sessionStore.imageFile(id).takeIf { it.exists() }

    // ---------- 表情包 ----------

    /** 指定情绪目录内随机一张图片；目录缺失或为空返回 null。 */
    fun emotionFileFor(word: String): File? =
        runCatching { EmotionStore.randomFile(appContext, word) }.getOrNull()

    /** 打开设置页时调用：异步建目录并刷新计数（不阻塞 UI）。 */
    fun refreshEmotions() {
        viewModelScope.launch {
            val (counts, dir) = withContext(Dispatchers.IO) {
                EmotionStore.ensureDirs(appContext)
                EmotionStore.counts(appContext) to EmotionStore.dirPath(appContext)
            }
            _state.update {
                it.copy(emotionCounts = counts, emotionDir = dir, emotionStatus = null)
            }
        }
    }

    /** 导入图片到指定情绪目录（字节原样拷贝）；导入中忽略重复触发。 */
    fun importEmotions(word: String, uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.emotionImporting) return
        _state.update { it.copy(emotionImporting = true, emotionStatus = null) }
        viewModelScope.launch {
            val (success, failed) = withContext(Dispatchers.IO) {
                EmotionStore.importImages(appContext, word, uris)
            }
            val counts = withContext(Dispatchers.IO) { EmotionStore.counts(appContext) }
            val status = when {
                failed == 0 -> "导入成功 $success 张"
                success == 0 -> "导入失败 $failed 张"
                else -> "导入成功 $success 张 / 失败 $failed 张"
            }
            _state.update {
                it.copy(emotionCounts = counts, emotionImporting = false, emotionStatus = status)
            }
        }
    }

    /** 清空指定情绪目录内全部图片。 */
    fun clearEmotions(word: String) {
        if (_state.value.emotionImporting) return
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { EmotionStore.clear(appContext, word) }
            val counts = withContext(Dispatchers.IO) { EmotionStore.counts(appContext) }
            _state.update {
                it.copy(emotionCounts = counts, emotionStatus = "已清空 $removed 张")
            }
        }
    }

    // ---------- 消息操作 ----------

    fun copyMessage(messageId: Int) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        val text = plainText(splitEmo(message.text, streaming = false).first)
        if (text.isBlank()) {
            showNotice("没有可复制的内容")
            return
        }
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Crox", text))
        showNotice("已复制")
    }

    /** 重答：删除该 AI 消息及其后所有消息，以截断后历史重新流式生成。 */
    fun regenerate(aiMessageId: Int) {
        val snapshot = _state.value
        if (snapshot.streaming || snapshot.switchState is SwitchState.Waking) return
        val uiIndex = snapshot.messages.indexOfFirst { it.id == aiMessageId && it.role == MsgRole.Ai }
        if (uiIndex < 0) return
        val sessionId = snapshot.currentSessionId ?: return

        val aiOrdinal = snapshot.messages.take(uiIndex + 1).count { it.role == MsgRole.Ai } - 1
        val historyIndex = historyIndexOf(Roles.ASSISTANT, aiOrdinal)
        if (historyIndex < 0) return
        val truncated = history.take(historyIndex)
        if (truncated.isEmpty()) return

        synchronized(persistLock) {
            history.clear()
            history.addAll(truncated)
        }
        saveSession(sessionId, truncated)
        _state.update { s -> s.copy(messages = s.messages.take(uiIndex), errorText = null) }
        refreshSessions()
        launchAssistantStream(ChatMode.Chat, rawPrompt = null)
    }

    /** 修改 user 消息：截断该消息及之后内容，替换文本（保留图片）后重新发送。 */
    fun editUserMessage(userMessageId: Int, newText: String) {
        val text = newText.trim()
        if (text.isEmpty()) return
        val snapshot = _state.value
        if (snapshot.streaming || snapshot.switchState is SwitchState.Waking) return
        val uiIndex = snapshot.messages.indexOfFirst { it.id == userMessageId && it.role == MsgRole.User }
        if (uiIndex < 0) return
        val sessionId = snapshot.currentSessionId ?: return

        val userOrdinal = snapshot.messages.take(uiIndex + 1).count { it.role == MsgRole.User } - 1
        val historyIndex = historyIndexOf(Roles.USER, userOrdinal)
        if (historyIndex < 0) return
        val original = history[historyIndex]
        val edited = Message(
            role = Roles.USER,
            content = text,
            ts = System.currentTimeMillis(),
            imageIds = original.imageIds,
        )

        synchronized(persistLock) {
            val kept = history.take(historyIndex)
            history.clear()
            history.addAll(kept)
            history.add(edited)
        }
        saveSession(sessionId, history.toList())

        val userUi = edited.toUi(nextMessageId++)
        _state.update { s -> s.copy(messages = s.messages.take(uiIndex) + userUi, errorText = null) }
        refreshSessions()
        launchAssistantStream(snapshot.mode, rawPrompt = text.takeIf { snapshot.mode == ChatMode.Raw })
    }

    /** UI 消息序位 → history 中同角色消息下标（当前会话内 1:1 对应）。 */
    private fun historyIndexOf(role: String, ordinal: Int): Int {
        if (ordinal < 0) return -1
        var count = 0
        history.forEachIndexed { index, message ->
            if (message.role == role) {
                if (count == ordinal) return index
                count++
            }
        }
        return -1
    }

    // ---------- UI 杂项 ----------

    fun openDrawer() {
        _state.update { it.copy(drawerOpen = true, drawerPage = DrawerPage.History) }
    }

    fun closeDrawer() {
        _state.update { it.copy(drawerOpen = false) }
    }

    fun openSettings() {
        _state.update { it.copy(drawerOpen = true, drawerPage = DrawerPage.Settings) }
    }

    fun backFromSettings() {
        _state.update { it.copy(drawerPage = DrawerPage.History) }
    }

    fun switchMode() {
        val next = if (_state.value.mode == ChatMode.Chat) ChatMode.Raw else ChatMode.Chat
        settingsStore.mode = next
        _state.update { it.copy(mode = next) }
    }

    fun toggleThink() {
        val next = !_state.value.thinking
        settingsStore.thinking = next
        _state.update { it.copy(thinking = next) }
    }

    fun setTheme(mode: ThemeMode) {
        settingsStore.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun toggleReasoning(id: Int) {
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) it.copy(reasoningExpanded = !it.reasoningExpanded) else it })
        }
    }

    fun useSuggestion(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun onTokenChange(value: String) {
        _state.update { it.copy(token = value, tokenSaved = false) }
    }

    fun saveToken() {
        val token = _state.value.token.trim()
        settingsStore.token = token
        _state.update { it.copy(token = token, tokenSaved = true) }
    }

    fun onReasonBudgetChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(6)
        settingsStore.reasonBudget = filtered
        _state.update { it.copy(reasonBudget = filtered) }
    }

    fun onTemperatureChange(value: Float) {
        settingsStore.temperature = value
        _state.update { it.copy(temperature = value) }
    }

    fun onMaxTokensChange(value: String) {
        val filtered = value.filter { it.isDigit() }.take(7)
        settingsStore.maxTokens = filtered
        _state.update { it.copy(maxTokens = filtered) }
    }

    fun onSystemPromptChange(value: String) {
        settingsStore.systemPrompt = value
        _state.update { it.copy(systemPrompt = value) }
    }

    fun showNotice(text: String) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MILLIS)
            _state.update { it.copy(notice = null) }
        }
    }

    override fun onCleared() {
        activeCall?.cancel()
        activationJob?.cancel()
        noticeJob?.cancel()
    }

    // ---------- 工具 ----------

    private fun Message.toUi(id: Int): UiMessage = UiMessage(
        id = id,
        role = if (role == Roles.USER) MsgRole.User else MsgRole.Ai,
        text = content,
        time = timeOf(ts),
        reasoning = reasoning.takeIf { it.isNotEmpty() },
        reasoningChars = reasoning.length,
        imageIds = imageIds,
    )

    private fun timeOf(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(if (ts > 0) ts else System.currentTimeMillis()))

    private fun formatSessionMeta(updatedAt: Long, count: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val startOfYesterday = startOfToday - 24L * 60 * 60 * 1000
        val label = when {
            updatedAt >= startOfToday -> "NOW"
            updatedAt >= startOfYesterday -> "YDAY"
            else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(updatedAt))
        }
        return "$label · $count MSG"
    }

    private companion object {
        const val MAX_IMAGES = 4
        const val HISTORY_BUDGET = 24000
        const val DEFAULT_MAX_TOKENS = 16384
        const val TITLE_MAX_CHARS = 24
        const val HEALTH_POLL_INTERVAL_MS = 3000L
        const val HEALTH_POLL_TIMEOUT_MS = 150_000L
        const val NOTICE_MILLIS = 3200L
        const val STREAM_UI_THROTTLE_MS = 100L
        const val STREAM_UI_DELTA_BURST = 16
    }
}
