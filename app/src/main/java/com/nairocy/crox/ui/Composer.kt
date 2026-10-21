package com.nairocy.crox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nairocy.crox.AppState
import java.io.File

@Composable
fun Composer(
    state: AppState,
    onImage: () -> Unit,
    onRemoveImage: (File) -> Unit,
    onToggleThink: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = LocalCroxColors.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPad = maxOf(navBottom, imeBottom)
    Column(Modifier.fillMaxWidth().background(c.bg).padding(bottom = bottomPad)) {
        if (state.pendingImages.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pendingImages.forEach { file ->
                    Box(Modifier.size(64.dp)) {
                        AsyncThumb(
                            file = file,
                            modifier = Modifier.fillMaxSize(),
                            decodePx = 160,
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .background(c.ink)
                                .sqClickable { onRemoveImage(file) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                IcoClose,
                                contentDescription = "移除图片",
                                modifier = Modifier.size(9.dp),
                                tint = c.bg,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.ink))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(c.bg)
                    .border(1.dp, c.ink)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onImage,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IcoImage, contentDescription = "选择图片", modifier = Modifier.size(20.dp), tint = c.ink)
            }
            val thinkBg by animateColorAsState(
                if (state.thinking) c.ink else c.bg,
                tween(150),
                label = "thinkBg",
            )
            val thinkFg by animateColorAsState(
                if (state.thinking) c.bg else c.ink,
                tween(150),
                label = "thinkFg",
            )
            Box(
                Modifier
                    .height(48.dp)
                    .background(thinkBg)
                    .border(1.dp, c.ink)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleThink,
                    )
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("思考", style = CroxType.btn, color = thinkFg)
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(c.bg)
                    .border(1.dp, c.ink)
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.draft.isEmpty()) {
                    Text(
                        "输入指令……",
                        style = CroxType.input,
                        color = c.placeholder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    textStyle = CroxType.input.copy(color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            Box(
                Modifier
                    .width(54.dp)
                    .height(48.dp)
                    .background(c.red)
                    .border(1.dp, c.red)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (state.streaming) onStop() else onSend() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.streaming,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "sendStop",
                ) { streaming ->
                    if (streaming) {
                        Icon(IcoStop, contentDescription = "停止", modifier = Modifier.size(20.dp), tint = Color.White)
                    } else {
                        Icon(IcoSend, contentDescription = "发送", modifier = Modifier.size(20.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}
