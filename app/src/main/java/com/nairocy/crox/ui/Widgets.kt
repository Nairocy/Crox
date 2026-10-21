package com.nairocy.crox.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nairocy.crox.data.ImagePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

fun Modifier.topLine(color: Color, stroke: Dp = 1.dp): Modifier = drawBehind {
    val w = stroke.toPx()
    drawLine(color, Offset(0f, w / 2f), Offset(size.width, w / 2f), w)
}

fun Modifier.bottomLine(color: Color, stroke: Dp = 1.dp): Modifier = drawBehind {
    val w = stroke.toPx()
    val y = size.height - w / 2f
    drawLine(color, Offset(0f, y), Offset(size.width, y), w)
}

fun Modifier.leftLine(color: Color, stroke: Dp = 1.dp): Modifier = drawBehind {
    val w = stroke.toPx()
    drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, size.height), w)
}

fun Modifier.rightLine(color: Color, stroke: Dp = 1.dp): Modifier = drawBehind {
    val w = stroke.toPx()
    val x = size.width - w / 2f
    drawLine(color, Offset(x, 0f), Offset(x, size.height), w)
}

@Composable
fun Modifier.sqClickable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun RowLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalCroxColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 2.dp).background(c.red))
        Spacer(Modifier.width(9.dp))
        Text(text, style = CroxType.settingLabel, color = c.ink)
    }
}

enum class SqButtonStyle {
    Ink,
    Red,
    Ghost,
}

@Composable
fun SqButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SqButtonStyle = SqButtonStyle.Ink,
    icon: ImageVector? = null,
    iconSize: Dp = 13.dp,
) {
    val c = LocalCroxColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val base = when (style) {
        SqButtonStyle.Ink -> c.ink
        SqButtonStyle.Red -> c.red
        SqButtonStyle.Ghost -> c.bg
    }
    val fg = when (style) {
        SqButtonStyle.Ink -> c.bg
        SqButtonStyle.Red -> Color.White
        SqButtonStyle.Ghost -> c.ink
    }
    val pressedBg = if (style == SqButtonStyle.Red) c.ink else c.red
    val bg by animateColorAsState(if (pressed) pressedBg else base, tween(120), label = "sqButtonBg")
    val borderColor by animateColorAsState(if (pressed) pressedBg else base, tween(120), label = "sqButtonBorder")
    val textColor = if (pressed && style != SqButtonStyle.Red) Color.White else fg
    Row(
        modifier
            .height(40.dp)
            .background(bg)
            .border(1.dp, borderColor)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = textColor)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = CroxType.btn, color = textColor)
    }
}

@Composable
fun SqSeg(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCroxColors.current
    Row(modifier.border(1.dp, c.ink)) {
        options.forEachIndexed { index, label ->
            if (index > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.ink))
            }
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) c.ink else c.bg)
                    .sqClickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = CroxType.btn, color = if (on) c.bg else c.ink)
            }
        }
    }
}

@Composable
fun SqSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCroxColors.current
    val density = LocalDensity.current
    val thumb = 16.dp
    val thumbPx = with(density) { thumb.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun valueAt(x: Float): Float {
        if (widthPx <= thumbPx) return range.start
        val fraction = ((x - thumbPx / 2f) / (widthPx - thumbPx)).coerceIn(0f, 1f)
        val raw = range.start + fraction * (range.endInclusive - range.start)
        return (raw * 20f).roundToInt() / 20f
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(range) {
                detectTapGestures { offset -> onValueChange(valueAt(offset.x)) }
            }
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ -> onValueChange(valueAt(change.position.x)) }
            },
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(c.ink),
        )
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                .size(thumb)
                .background(c.ink),
        )
    }
}

@Composable
fun SqInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Dp = 18.dp,
    textStyle: TextStyle? = null,
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val c = LocalCroxColors.current
    var style = textStyle ?: CroxType.settingInput
    if (mono) style = style.copy(fontFamily = FontFamily.Monospace)
    style = style.copy(color = c.ink)
    Box(
        modifier
            .background(c.bg)
            .border(1.dp, c.ink)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(
                placeholder,
                style = style.copy(color = c.placeholder),
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(c.ink),
            singleLine = singleLine,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

/** 本地文件异步解码缩略图；解码完成 150ms fade-in。 */
@Composable
fun AsyncThumb(
    file: File?,
    modifier: Modifier = Modifier,
    label: String? = null,
    decodePx: Int = 256,
) {
    val c = LocalCroxColors.current
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { file?.let { ImagePipeline.decodeThumb(it, decodePx) } }
    }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(150),
        label = "thumbFade",
    )
    Box(modifier.border(1.dp, c.ink).background(c.thumbBg).clipToBounds()) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(alpha),
                contentScale = ContentScale.Crop,
            )
        }
        if (label != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .background(c.ink)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    label,
                    style = TextStyle(fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em),
                    color = c.bg,
                )
            }
        }
    }
}
