package com.nairocy.crox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nairocy.crox.MsgRole
import com.nairocy.crox.Terminal
import com.nairocy.crox.UiMessage
import com.nairocy.crox.data.splitEmo
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter

@Composable
fun MessageList(
    messages: List<UiMessage>,
    terminal: Terminal,
    errorText: String?,
    imageFileFor: (String) -> File?,
    emotionFileFor: (String) -> File?,
    onToggleReasoning: (Int) -> Unit,
    onCopy: (Int) -> Unit,
    onRegenerate: (Int) -> Unit,
    onEditUser: (Int, String) -> Unit,
) {
    val listState = rememberLazyListState()
    var editing by remember { mutableStateOf<UiMessage?>(null) }

    // 跟随意图：用户上翻离开底部则暂停跟随，滚回底部恢复（键盘弹起等布局变化不改变意图）
    var userFollows by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()
                userFollows = lastVisible == null || lastVisible.index >= info.totalItemsCount - 1
            }
    }

    // 新消息 / 切换会话 / 系统错误行：总是滚到底（用户操作的结果）
    LaunchedEffect(messages.firstOrNull()?.id, messages.size, errorText != null) {
        if (messages.isNotEmpty()) {
            userFollows = true
            val target = if (errorText != null) messages.size else messages.lastIndex
            listState.animateScrollToItem(target)
        }
    }

    // 流式增长：贴底时实时跟随（瞬时滚动，不打断手势）
    val lastMsg = messages.lastOrNull()
    LaunchedEffect(lastMsg?.text?.length, lastMsg?.reasoningChars) {
        if (userFollows && !listState.isScrollInProgress &&
            lastMsg?.streaming == true && messages.isNotEmpty()
        ) {
            val target = if (errorText != null) messages.size else messages.lastIndex
            listState.scrollToItem(target)
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(messages, key = { it.id }) { message ->
            MessageEntrance(message.id) {
                when (message.role) {
                    MsgRole.User -> UserRow(
                        message = message,
                        imageFileFor = imageFileFor,
                        onCopy = { onCopy(message.id) },
                        onEdit = { editing = message },
                    )
                    MsgRole.Ai -> AiRow(
                        message = message,
                        terminal = terminal,
                        emotionFileFor = emotionFileFor,
                        onToggleReasoning = onToggleReasoning,
                        onCopy = { onCopy(message.id) },
                        onRegenerate = { onRegenerate(message.id) },
                    )
                }
            }
        }
        if (errorText != null) {
            item(key = "sys-err") {
                MessageEntrance("sys-err") { SysErrorRow(errorText) }
            }
        }
    }

    val editingMessage = editing
    if (editingMessage != null) {
        EditMessageDialog(
            initial = editingMessage.text,
            onDismiss = { editing = null },
            onSave = { newText ->
                editing = null
                onEditUser(editingMessage.id, newText)
            },
        )
    }
}

/** 动效 #3：新消息 fade + translateY(8→0) 220ms；同一消息（流式更新）不重复触发。 */
@Composable
private fun MessageEntrance(key: Any, content: @Composable () -> Unit) {
    var appeared by rememberSaveable(key) { mutableStateOf(false) }
    LaunchedEffect(key) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(220),
        label = "messageEnter",
    )
    val offsetPx = with(LocalDensity.current) { 8.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * offsetPx
            },
    ) {
        content()
    }
}

@Composable
private fun UserRow(
    message: UiMessage,
    imageFileFor: (String) -> File?,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = LocalCroxColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    Column(Modifier.fillMaxWidth().background(c.bg).bottomLine(c.ink)) {
        HeadRowRight(tag = "[USER]", tagColor = c.red, meta = message.time)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        ) {
            val blockMax = maxWidth * 0.84f
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { anchorSize = it },
            ) {
                Column(
                    Modifier
                        .widthIn(max = blockMax)
                        .background(c.userRowBg)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { menuOpen = true },
                        )
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    if (message.imageIds.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            message.imageIds.forEachIndexed { index, id ->
                                AsyncThumb(
                                    file = imageFileFor(id),
                                    modifier = Modifier.width(150.dp).height(104.dp),
                                    label = String.format(Locale.US, "IMG-%02d", index + 1),
                                    decodePx = 512,
                                )
                            }
                        }
                        if (message.text.isNotEmpty()) Spacer(Modifier.height(9.dp))
                    }
                    if (message.text.isNotEmpty()) {
                        MarkdownBody(message.text, streaming = false)
                    }
                }
                if (menuOpen) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, anchorSize.height + with(LocalDensity.current) { 6.dp.roundToPx() }),
                        onDismissRequest = { menuOpen = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        MessageContextMenu(
                            onCopy = {
                                menuOpen = false
                                onCopy()
                            },
                            onEdit = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** user 消息长按菜单（直角、贴消息、点击外部关闭）。 */
@Composable
private fun MessageContextMenu(onCopy: () -> Unit, onEdit: () -> Unit) {
    val c = LocalCroxColors.current
    Column(
        Modifier
            .width(132.dp)
            .background(c.bg)
            .border(1.dp, c.ink),
    ) {
        ContextMenuItem("复制", onCopy)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
        ContextMenuItem("修改", onEdit)
    }
}

@Composable
private fun ContextMenuItem(text: String, onClick: () -> Unit) {
    val c = LocalCroxColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .sqClickable(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = CroxType.settingInput, color = c.ink)
    }
}

/** 修改消息对话框：直角、预填当前文本、自动聚焦弹键盘。 */
@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val c = LocalCroxColors.current
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Dialog(
        onDismissRequest = {
            keyboard?.hide()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(c.bg)
                .border(1.dp, c.ink),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bottomLine(c.ink)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("修改消息", style = CroxType.drawerTitle, color = c.ink)
            }
            SqInput(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                singleLine = false,
                minHeight = 88.dp,
                focusRequester = focusRequester,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .topLine(c.ink)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SqButton(
                    text = "取消",
                    onClick = {
                        keyboard?.hide()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    style = SqButtonStyle.Ghost,
                )
                SqButton(
                    text = "保存",
                    onClick = {
                        if (text.isNotBlank()) {
                            keyboard?.hide()
                            onSave(text)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = SqButtonStyle.Red,
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}

@Composable
private fun AiRow(
    message: UiMessage,
    terminal: Terminal,
    emotionFileFor: (String) -> File?,
    onToggleReasoning: (Int) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val c = LocalCroxColors.current
    val tag = if (terminal == Terminal.T1) "[CROX · T2]" else "[CROX · P2]"
    val (shown, word) = splitEmo(message.text, message.streaming)
    // 随机一次、scroll 往返不闪：id 跨会话复用故拼接身份键；rememberSaveable 防滚出屏幕销毁后重随机
    val stickerKey = "emo-${message.id}-${message.time}-${message.text.hashCode()}-$word"
    val stickerPath = if (!message.streaming && word != null) {
        rememberSaveable(stickerKey) { emotionFileFor(word)?.absolutePath.orEmpty() }
    } else {
        null
    }
    // 冗余校验：路径须属于当前情绪目录（防 LazyColumn item key 复用还原出别条消息的贴纸）
    val stickerFile = stickerPath
        ?.takeIf { it.isNotEmpty() }
        ?.let(::File)
        ?.takeIf { it.parentFile?.name == word }
    val meta = buildString {
        if (message.throughput != null) {
            append(message.throughput)
            append(" · ")
        }
        append(message.time)
    }
    Column(Modifier.fillMaxWidth().background(c.bg).bottomLine(c.ink)) {
        HeadRow(tag = tag, tagColor = c.ink, meta = meta)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (message.reasoning != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .sqClickable { onToggleReasoning(message.id) }
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        IcoReasoning,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = c.red,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "REASONING · ${message.reasoningChars} 字",
                        style = CroxType.rowLabel,
                        color = c.red,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (message.reasoningExpanded) "[ 收起 ]" else "[ 展开 ]",
                        style = CroxType.reasoningToggle,
                        color = c.red,
                    )
                    Spacer(Modifier.width(5.dp))
                    val rotation by animateFloatAsState(
                        targetValue = if (message.reasoningExpanded) 180f else 0f,
                        animationSpec = tween(150),
                        label = "reasonChevron",
                    )
                    Icon(
                        IcoChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp).rotate(rotation),
                        tint = c.red,
                    )
                }
                AnimatedVisibility(
                    visible = message.reasoningExpanded,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(top = 10.dp, bottom = 12.dp),
                    ) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(c.red))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            Text(message.reasoning, style = CroxType.reasoning, color = c.gray)
                        }
                    }
                }
            }
            MarkdownBody(shown, streaming = message.streaming)
            if (stickerFile != null) {
                Spacer(Modifier.height(8.dp))
                EmotionSticker(stickerFile, Modifier.padding(vertical = 4.dp))
            }
            if (!message.streaming && message.text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionIcon(IcoCopy, "复制", onCopy)
                    ActionIcon(IcoRegen, "重答", onRegenerate)
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalCroxColors.current
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(40.dp)
            .sqClickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .size(17.dp),
            tint = c.ink.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SysErrorRow(text: String) {
    val c = LocalCroxColors.current
    Column(Modifier.fillMaxWidth().background(c.bg).bottomLine(c.ink)) {
        HeadRow(tag = "[SYS · ERR]", tagColor = c.red, meta = "网络")
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.red)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).background(c.red))
                Spacer(Modifier.width(9.dp))
                Text(text, style = CroxType.msgBody, color = c.red)
            }
        }
    }
}

@Composable
private fun HeadRow(tag: String, tagColor: Color, meta: String) {
    val c = LocalCroxColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .bottomLine(c.hair)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tag, style = CroxType.rowLabel, color = tagColor)
        Spacer(Modifier.weight(1f))
        Text(meta, style = CroxType.meta, color = c.gray)
    }
}

@Composable
private fun HeadRowRight(tag: String, tagColor: Color, meta: String) {
    val c = LocalCroxColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .bottomLine(c.hair)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(tag, style = CroxType.rowLabel, color = tagColor)
        Spacer(Modifier.width(10.dp))
        Text(meta, style = CroxType.meta, color = c.gray)
    }
}
