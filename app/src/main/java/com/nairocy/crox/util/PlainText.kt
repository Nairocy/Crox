package com.nairocy.crox.util

/**
 * Markdown 原文 → 纯文本（复制与朗读共用）。
 * 规则（对齐任务书 §2.1）：
 * - `**粗体**`/`*斜体*`/`~~删除线~~` 去标记；行内代码去反引号
 * - 围栏代码块去围栏行、保留代码文本
 * - `#` 标题、`>` 引用去标记；`---` hr 删行
 * - `[文字](url)` 只留文字；图片语法只留 alt
 * - 表格行去竖线、以「，」连接
 * - 列表标记（- * + 1.）去掉，保留文本
 * - 折叠多余空行
 */
fun plainText(markdown: String): String {
    if (markdown.isEmpty()) return ""
    val text = markdown.replace("\r\n", "\n").replace('\r', '\n')
    val out = StringBuilder()
    var inFence = false
    for (raw in text.split('\n')) {
        if (FENCE_LINE.matches(raw)) {
            inFence = !inFence
            continue
        }
        if (inFence) {
            appendLine(out, raw)
            continue
        }
        if (raw.isBlank()) {
            appendLine(out, "")
            continue
        }
        if (HR_LINE.matches(raw)) continue

        val heading = HEADING_LINE.matchEntire(raw)
        if (heading != null) {
            appendLine(out, inlineText(heading.groupValues[1]))
            continue
        }
        val quote = QUOTE_LINE.matchEntire(raw)
        if (quote != null) {
            appendLine(out, inlineText(quote.groupValues[1]))
            continue
        }
        val list = LIST_LINE.matchEntire(raw)
        if (list != null) {
            appendLine(out, inlineText(list.groupValues[1]))
            continue
        }
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("|") && trimmed.endsWith("|") &&
            trimmed.count { it == '|' } >= 2
        ) {
            if (TABLE_SEPARATOR.matches(trimmed)) continue
            val cells = trimmed.removePrefix("|").removeSuffix("|")
                .split('|')
                .map { inlineText(it.trim()) }
                .filter { it.isNotEmpty() }
            appendLine(out, cells.joinToString("，"))
            continue
        }
        appendLine(out, inlineText(raw))
    }
    return out.toString().replace(MULTI_BLANK, "\n\n").trim()
}

private fun appendLine(out: StringBuilder, line: String) {
    if (out.isNotEmpty()) out.append('\n')
    out.append(line)
}

private fun inlineText(source: String): String {
    var s = source
    s = IMAGE_LINK.replace(s) { it.groupValues[1] }
    s = LINK.replace(s) { it.groupValues[1] }
    s = BOLD.replace(s) { it.groupValues[1] }
    s = ITALIC.replace(s) { it.groupValues[1] }
    s = STRIKE.replace(s) { it.groupValues[1] }
    s = BACKTICK.replace(s) { it.groupValues[1] }
    return s
}

private val FENCE_LINE = Regex("^[ \\t]*```.*$")
private val HR_LINE = Regex("^[ \\t]*(-{3,}|\\*{3,}|_{3,})[ \\t]*$")
private val HEADING_LINE = Regex("^[ \\t]*#{1,6}[ \\t]+(.*)$")
private val QUOTE_LINE = Regex("^[ \\t]*>[ \\t]?(.*)$")
private val LIST_LINE = Regex("^[ \\t]*(?:[-*+]|\\d+[.)])[ \\t]+(.*)$")
private val TABLE_SEPARATOR = Regex("^\\|?(\\s*:?-+:?\\s*\\|)+$")
private val MULTI_BLANK = Regex("\n{3,}")

private val IMAGE_LINK = Regex("!\\[([^\\]]*)\\]\\([^)\\s]+\\)")
private val LINK = Regex("\\[([^\\]]+)\\]\\([^)\\s]+\\)")
private val BOLD = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val ITALIC = Regex("\\*([^*\\n]+)\\*")
private val STRIKE = Regex("~~([^~\\n]+)~~")
private val BACKTICK = Regex("`+([^`]*)`+")
