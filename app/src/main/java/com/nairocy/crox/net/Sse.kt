package com.nairocy.crox.net

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Incremental SSE parser: UTF-8 streaming decode + `\n\n` event boundaries.
 * Handles CRLF/CR line endings, tolerates EOF without trailing newline,
 * understands `data:` prefixed lines and terminates on `[DONE]`.
 */
class SseParser {

    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var pendingBytes: ByteArray = ByteArray(0)
    private val text = StringBuilder()
    private var pendingCr = false

    var isTerminated: Boolean = false
        private set


    /** Feed a raw chunk; returns complete event payloads found so far. */
    fun feed(chunk: ByteArray): List<String> {
        if (isTerminated || chunk.isEmpty()) return emptyList()
        pendingBytes = if (pendingBytes.isEmpty()) chunk else pendingBytes + chunk
        decode(inputDone = false)
        return drain()
    }

    /** Flush at stream end (may emit one last event without trailing blank line). */
    fun finish(): List<String> {
        if (isTerminated) return emptyList()
        if (pendingCr) {
            pendingCr = false
            text.append('\n')
        }
        decode(inputDone = true)
        val events = drain()
        if (!isTerminated && text.isNotEmpty()) {
            val tail = text.toString().trimEnd('\n')
            text.setLength(0)
            if (tail.isNotEmpty()) parseEvent(tail)?.let { events += it }
        }
        return events
    }

    private fun decode(inputDone: Boolean) {
        val input = ByteBuffer.wrap(pendingBytes)
        val output = CharBuffer.allocate(pendingBytes.size + 1)
        decoder.decode(input, output, inputDone)
        output.flip()
        appendDecoded(output.toString())
        pendingBytes = ByteArray(input.remaining()).also { input.get(it) }
        if (inputDone) {
            val flushOut = CharBuffer.allocate(8)
            decoder.flush(flushOut)
            flushOut.flip()
            appendDecoded(flushOut.toString())
        }
    }

    private fun appendDecoded(s: String) {
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (pendingCr) {
                pendingCr = false
                text.append('\n')
                if (ch == '\n') {
                    i++
                    continue
                }
            }
            if (ch == '\r') {
                pendingCr = true
            } else {
                text.append(ch)
            }
            i++
        }
    }

    private fun drain(): MutableList<String> {
        val events = mutableListOf<String>()
        while (!isTerminated) {
            val idx = text.indexOf("\n\n")
            if (idx < 0) break
            val block = text.substring(0, idx)
            text.delete(0, idx + 2)
            when (val payload = parseEvent(block)) {
                null -> Unit
                "[DONE]" -> isTerminated = true
                else -> events += payload
            }
        }
        return events
    }

    private fun parseEvent(block: String): String? {
        val data = StringBuilder()
        var hasData = false
        for (line in block.split('\n')) {
            if (!line.startsWith("data:")) continue
            hasData = true
            var value = line.substring(5)
            if (value.startsWith(" ")) value = value.substring(1)
            if (data.isNotEmpty()) data.append('\n')
            data.append(value)
        }
        return if (hasData) data.toString() else null
    }
}
