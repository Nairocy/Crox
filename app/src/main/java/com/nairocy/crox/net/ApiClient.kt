package com.nairocy.crox.net

import com.nairocy.crox.Terminal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

val Terminal.apiName: String
    get() = if (this == Terminal.T1) "t2" else "persona"

val Terminal.apiPrefix: String
    get() = if (this == Terminal.T1) "/api" else "/persona/api"

interface ChatListener {
    fun onDelta(content: String, reasoning: String)
    fun onTimings(predictedPerSecond: Double)
    fun onDone()
    fun onStopped()
    fun onError(message: String)
}

object ApiClient {

    private const val BASE = "https://ai.nairocy.com"
    private const val ENSURED_TIMEOUT_S = 20L
    private val jsonMedia = "application/json".toMediaType()

    val json: Json = Json {
        ignoreUnknownKeys = true
    }

    /** connect 15s / read 0 (SSE must not time out) / write 60s */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** POST /switch/ensure（无鉴权，失败重试 1 次） */
    fun ensure(terminal: Terminal): Boolean {
        val body = buildJsonObject { put("model", terminal.apiName) }
            .toString()
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("$BASE/switch/ensure")
            .post(body)
            .build()
        repeat(2) {
            val ok = runCatching {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** GET /api/health（T1）｜/persona/api/health（persona），无鉴权 */
    fun health(terminal: Terminal): Boolean = runCatching {
        val request = Request.Builder()
            .url("$BASE${terminal.apiPrefix}/health")
            .get()
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(ENSURED_TIMEOUT_S, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) return@use false
            val status = runCatching {
                val root = json.parseToJsonElement(response.body.string()) as? JsonObject
                root?.get("status") as? JsonPrimitive
            }.getOrNull()
            status?.contentOrNull == "ok"
        }
    }.getOrDefault(false)

    /**
     * POST /api/v1/chat/completions ｜ /persona/api/v1/chat/completions
     * 流式 SSE；返回的 Call 可 cancel（停止按钮）。
     */
    fun chat(
        terminal: Terminal,
        token: String,
        payload: String,
        listener: ChatListener,
    ): Call {
        val request = Request.Builder()
            .url("$BASE${terminal.apiPrefix}/v1/chat/completions")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) listener.onStopped() else listener.onError(describeIo(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        listener.onError(httpErrorMessage(resp.code))
                        return
                    }
                    val parser = SseParser()
                    val buffer = Buffer()
                    try {
                        val source = resp.body.source()
                        readLoop@ while (!parser.isTerminated) {
                            val read = source.read(buffer, 8192)
                            if (read == -1L) break
                            for (event in parser.feed(buffer.readByteArray())) {
                                dispatch(event, listener)
                            }
                            if (parser.isTerminated) break@readLoop
                        }
                        if (call.isCanceled()) {
                            listener.onStopped()
                            return
                        }
                        for (event in parser.finish()) {
                            dispatch(event, listener)
                        }
                        if (parser.isTerminated) {
                            listener.onDone()
                        } else {
                            listener.onError("连接中断：流式响应提前结束")
                        }
                    } catch (e: IOException) {
                        if (call.isCanceled()) listener.onStopped() else listener.onError(describeIo(e))
                    }
                }
            }
        })
        return call
    }

    /**
     * POST /api/completion ｜ /persona/api/completion（续写模式）
     * 流式 SSE：delta 在事件顶层 "content"；无 [DONE]，连接正常关闭即完成。
     */
    fun complete(
        terminal: Terminal,
        token: String,
        payload: String,
        listener: ChatListener,
    ): Call {
        val request = Request.Builder()
            .url("$BASE${terminal.apiPrefix}/completion")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMedia))
            .build()
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) listener.onStopped() else listener.onError(describeIo(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        listener.onError(httpErrorMessage(resp.code))
                        return
                    }
                    val parser = SseParser()
                    val buffer = Buffer()
                    var eof = false
                    try {
                        val source = resp.body.source()
                        readLoop@ while (!parser.isTerminated) {
                            val read = source.read(buffer, 8192)
                            if (read == -1L) {
                                eof = true
                                break
                            }
                            for (event in parser.feed(buffer.readByteArray())) {
                                dispatchCompletion(event, listener)
                            }
                            if (parser.isTerminated) break@readLoop
                        }
                        if (call.isCanceled()) {
                            listener.onStopped()
                            return
                        }
                        for (event in parser.finish()) {
                            dispatchCompletion(event, listener)
                        }
                        if (parser.isTerminated || eof) {
                            listener.onDone()
                        } else {
                            listener.onError("连接中断：流式响应提前结束")
                        }
                    } catch (e: IOException) {
                        if (call.isCanceled()) listener.onStopped() else listener.onError(describeIo(e))
                    }
                }
            }
        })
        return call
    }

    private fun dispatchCompletion(event: String, listener: ChatListener) {
        if (event.isBlank() || event == "[DONE]") return
        val root = runCatching { json.parseToJsonElement(event) }.getOrNull() as? JsonObject ?: return
        val content = (root["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (content.isNotEmpty()) listener.onDelta(content, "")
        val tps = ((root["timings"] as? JsonObject)?.get("predicted_per_second") as? JsonPrimitive)?.doubleOrNull
        if (tps != null) listener.onTimings(tps)
    }

    private fun dispatch(event: String, listener: ChatListener) {
        if (event.isBlank() || event == "[DONE]") return
        val root = runCatching { json.parseToJsonElement(event) }.getOrNull() as? JsonObject ?: return
        val delta = ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("delta") as? JsonObject
        val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val reasoning = (delta?.get("reasoning_content") as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (content.isNotEmpty() || reasoning.isNotEmpty()) listener.onDelta(content, reasoning)
        val tps = ((root["timings"] as? JsonObject)?.get("predicted_per_second") as? JsonPrimitive)?.doubleOrNull
        if (tps != null) listener.onTimings(tps)
    }

    fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "401 未授权：token 不正确"
        502 -> "502 后端离线（隧道或本机服务未启动）"
        else -> "$code 请求失败"
    }

    private fun describeIo(e: IOException): String {
        val detail = e.message?.take(80)?.takeIf { it.isNotBlank() }
        return if (detail == null) "网络错误" else "网络错误：$detail"
    }
}
