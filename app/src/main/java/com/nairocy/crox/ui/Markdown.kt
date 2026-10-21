package com.nairocy.crox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 轻量自研 Markdown（对齐 web `mdRender()` 的行为子集，不含新依赖）。
 * 结构：`parseMarkdown(text): List<MdBlock>`（纯函数）＋ Compose 渲染层。
 */
sealed interface MdBlock {
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Code(val code: String) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<MdListItem>) : MdBlock
    data class Quote(val inlines: List<MdInline>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Hr : MdBlock
}

data class MdListItem(val depth: Int, val marker: String?, val inlines: List<MdInline>)

data class MdInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

private val FENCE = Regex("^[ \\t]*```(.*)$")
private val HEADING = Regex("^(#{1,6})[ \\t]+(.+)$")
private val LIST_ITEM = Regex("^([ \\t]*)([-*+]|\\d+\\.)([ \\t]+)(.*)$")
private val QUOTE = Regex("^[ \\t]*>[ \\t]?(.*)$")
private val HR = Regex("^[ \\t]*(-{3,}|\\*{3,}|_{3,})[ \\t]*$")
private val TABLE_SEPARATOR = Regex("^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$")

fun parseMarkdown(source: String): List<MdBlock> {
    if (source.isEmpty()) return emptyList()
    val text = source.replace("\r\n", "\n").replace('\r', '\n')
    val lines = text.split('\n')
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (FENCE.find(line) != null) {
            val code = StringBuilder()
            i++
            while (i < lines.size && FENCE.find(lines[i]) == null) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            if (i < lines.size) i++
            blocks += MdBlock.Code(code.toString())
            continue
        }

        if (line.isBlank()) {
            i++
            continue
        }

        if (isTableRow(line)) {
            var end = i
            while (end < lines.size && isTableRow(lines[end])) end++
            if (end - i >= 2 && TABLE_SEPARATOR.matches(lines[i + 1])) {
                val rows = (i until end).map { tableCells(lines[it]) }
                blocks += MdBlock.Table(header = rows.first(), rows = rows.drop(2))
                i = end
                continue
            }
        }

        if (HR.matches(line)) {
            blocks += MdBlock.Hr
            i++
            continue
        }

        val heading = HEADING.find(line)
        if (heading != null) {
            blocks += MdBlock.Heading(heading.groupValues[1].length, parseInline(heading.groupValues[2]))
            i++
            continue
        }

        val listFirst = LIST_ITEM.find(line)
        if (listFirst != null) {
            val ordered = listFirst.groupValues[2].first().isDigit()
            val items = mutableListOf<MdListItem>()
            while (i < lines.size) {
                val match = LIST_ITEM.find(lines[i]) ?: break
                val indent = match.groupValues[1]
                val depth = ((indent.count { it == ' ' } + indent.count { it == '\t' } * 2) / 2).coerceIn(0, 3)
                val marker = match.groupValues[2]
                items += MdListItem(
                    depth = depth,
                    marker = if (ordered && marker.endsWith('.')) marker else null,
                    inlines = parseInline(match.groupValues[4]),
                )
                i++
            }
            blocks += MdBlock.ListBlock(ordered, items)
            continue
        }

        if (QUOTE.matches(line)) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size) {
                val match = QUOTE.find(lines[i]) ?: break
                quoteLines += match.groupValues[1]
                i++
            }
            blocks += MdBlock.Quote(parseInline(quoteLines.joinToString("\n")))
            continue
        }

        val paragraph = mutableListOf<String>()
        while (i < lines.size) {
            val current = lines[i]
            if (current.isBlank()) break
            if (FENCE.find(current) != null || HR.matches(current) || HEADING.find(current) != null) break
            if (LIST_ITEM.find(current) != null || QUOTE.matches(current)) break
            if (isTableRow(current) && i + 1 < lines.size && isTableRow(lines[i + 1]) && TABLE_SEPARATOR.matches(lines[i + 1])) break
            paragraph += current
            i++
        }
        blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString("\n")))
    }
    return blocks
}

private fun isTableRow(line: String): Boolean = line.trim().contains('|')

private fun tableCells(line: String): List<String> {
    val body = line.trim().removePrefix("|").removeSuffix("|")
    return body.split('|').map { it.trim() }
}

private fun parseInline(text: String): List<MdInline> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<MdInline>()
    val plain = StringBuilder()
    var i = 0

    fun flush() {
        if (plain.isNotEmpty()) {
            out += MdInline(plain.toString())
            plain.setLength(0)
        }
    }

    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                    flush()
                    out += MdInline(text.substring(i + 1, end), code = true)
                    i = end + 1
                } else {
                    plain.append(ch)
                    i++
                }
            }

            ch == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                val content = if (end > i + 1) text.substring(i + 2, end) else null
                if (content != null && content.isNotEmpty() && !content.contains('*') && !content.contains('\n')) {
                    flush()
                    out += parseInline(content).map { it.copy(bold = true) }
                    i = end + 2
                } else {
                    plain.append(ch)
                    i++
                }
            }

            ch == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                val end = text.indexOf('*', i + 1)
                val content = if (end > i + 1) text.substring(i + 1, end) else null
                if (content != null && content.isNotEmpty() && !content.contains('*') && !content.contains('\n')) {
                    flush()
                    out += parseInline(content).map { it.copy(italic = true) }
                    i = end + 1
                } else {
                    plain.append(ch)
                    i++
                }
            }

            ch == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val closeParen = if (
                    closeBracket > i + 1 &&
                    closeBracket + 1 < text.length &&
                    text[closeBracket + 1] == '('
                ) {
                    text.indexOf(')', closeBracket + 2)
                } else {
                    -1
                }
                if (closeParen > closeBracket + 1) {
                    val label = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    if (
                        (url.startsWith("https://") || url.startsWith("http://")) &&
                        !label.contains('\n') && !url.contains('\n')
                    ) {
                        flush()
                        out += MdInline(label, link = url)
                        i = closeParen + 1
                    } else {
                        plain.append(ch)
                        i++
                    }
                } else {
                    plain.append(ch)
                    i++
                }
            }

            ch == '\n' -> {
                flush()
                out += MdInline("\n")
                i++
            }

            else -> {
                plain.append(ch)
                i++
            }
        }
    }
    flush()
    return out
}

/** AI（与 user）正文渲染入口：块级结构 + 行内 AnnotatedString。 */
@Composable
fun MarkdownBody(text: String, streaming: Boolean = false) {
    val c = LocalCroxColors.current
    val blocks = remember(text) { parseMarkdown(text) }
    Column(Modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(blockGap(block)))
            MdBlockView(
                block = block,
                c = c,
                trailingCursor = streaming && index == blocks.lastIndex && block is MdBlock.Paragraph,
            )
        }
        if (streaming && (blocks.isEmpty() || blocks.last() !is MdBlock.Paragraph)) {
            Spacer(Modifier.height(4.dp))
            BlinkingCursor(c)
        }
    }
}

@Composable
private fun MdBlockView(block: MdBlock, c: CroxColors, trailingCursor: Boolean) {
    when (block) {
        is MdBlock.Paragraph -> {
            val annotated = annotate(block.inlines, c)
            if (trailingCursor) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(annotated, style = CroxType.msgBody, color = c.ink)
                    Spacer(Modifier.width(3.dp))
                    BlinkingCursor(c)
                }
            } else {
                Text(annotated, style = CroxType.msgBody, color = c.ink)
            }
        }

        is MdBlock.Heading -> Text(
            annotate(block.inlines, c),
            style = headingStyle(block.level),
            color = c.ink,
        )

        is MdBlock.Code -> CodeBlock(block.code)

        is MdBlock.ListBlock -> Column(Modifier.fillMaxWidth()) {
            block.items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = (item.depth * 16).dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .height(24.dp)
                            .width(if (item.marker != null) 28.dp else 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (item.marker != null) {
                            Text(
                                item.marker,
                                style = CroxType.msgBody.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = c.gray,
                                ),
                            )
                        } else {
                            Box(Modifier.size(if (item.depth == 0) 4.dp else 3.dp).background(c.ink))
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(annotate(item.inlines, c), style = CroxType.msgBody, color = c.ink)
                }
            }
        }

        is MdBlock.Quote -> Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(c.red))
            Spacer(Modifier.width(11.dp))
            Text(annotate(block.inlines, c), style = CroxType.msgBody, color = c.gray)
        }

        is MdBlock.Table -> Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, c.hair),
        ) {
            MdTableRow(block.header, header = true, c = c)
            if (block.rows.isNotEmpty()) TableDivider(c)
            block.rows.forEachIndexed { index, row ->
                MdTableRow(row, header = false, c = c)
                if (index < block.rows.lastIndex) TableDivider(c)
            }
        }

        MdBlock.Hr -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
    }
}

@Composable
private fun TableDivider(c: CroxColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
}

@Composable
private fun MdTableRow(cells: List<String>, header: Boolean, c: CroxColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) c.inlineCodeBg else Color.Unspecified),
    ) {
        cells.forEach { cell ->
            Text(
                annotate(parseInline(cell), c),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                style = CroxType.msgBody.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                ),
                color = c.ink,
            )
        }
    }
}

@Composable
fun CodeBlock(code: String) {
    val c = LocalCroxColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.codeBg)
            .clipToBounds(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.red))
            Text(
                code,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                style = CroxType.code,
                color = c.codeFg,
                softWrap = false,
            )
        }
        if (code.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .heightIn(min = 32.dp)
                    .widthIn(min = 32.dp)
                    .sqClickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }
                    .background(c.codeBg)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (copied) {
                    Text(
                        "已复制",
                        style = CroxType.hint,
                        color = c.codeFg,
                        maxLines = 1,
                    )
                } else {
                    Icon(
                        IcoCopy,
                        contentDescription = "复制代码",
                        modifier = Modifier.size(14.dp),
                        tint = c.codeFg,
                    )
                }
            }
        }
    }
}

@Composable
private fun annotate(inlines: List<MdInline>, c: CroxColors): AnnotatedString = buildAnnotatedString {
    inlines.forEach { span ->
        val base = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            background = if (span.code) c.inlineCodeBg else Color.Unspecified,
            fontSize = if (span.code) 13.sp else TextUnit.Unspecified,
        )
        val link = span.link
        if (link != null) {
            val start = length
            withStyle(base + SpanStyle(color = c.red, textDecoration = TextDecoration.Underline)) {
                append(span.text)
            }
            addLink(LinkAnnotation.Url(link), start, length)
        } else {
            withStyle(base) { append(span.text) }
        }
    }
}

@Composable
private fun BlinkingCursor(c: CroxColors) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 using LinearEasing
                1f at 499 using LinearEasing
                0f at 500 using LinearEasing
                0f at 999 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    Box(
        Modifier
            .padding(bottom = 4.dp)
            .size(width = 8.dp, height = 13.dp)
            .alpha(alpha)
            .background(c.red),
    )
}

private fun blockGap(block: MdBlock): Dp = when (block) {
    is MdBlock.Code -> 10.dp
    is MdBlock.Table -> 10.dp
    is MdBlock.Heading -> 10.dp
    MdBlock.Hr -> 12.dp
    is MdBlock.ListBlock -> 4.dp
    is MdBlock.Quote -> 8.dp
    is MdBlock.Paragraph -> 8.dp
}

private fun headingStyle(level: Int): TextStyle {
    val size = when (level) {
        1 -> 19.sp
        2 -> 17.sp
        3 -> 15.5.sp
        4 -> 14.5.sp
        5 -> 14.sp
        else -> 13.5.sp
    }
    return TextStyle(fontSize = size, fontWeight = FontWeight.Bold, lineHeight = size * 1.35f)
}
