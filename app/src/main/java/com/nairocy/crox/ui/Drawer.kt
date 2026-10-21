package com.nairocy.crox.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nairocy.crox.AppState
import com.nairocy.crox.AppViewModel
import com.nairocy.crox.DrawerPage
import com.nairocy.crox.SessionMeta
import com.nairocy.crox.ThemeMode
import com.nairocy.crox.data.EmotionStore
import java.util.Locale

@Composable
fun DrawerOverlay(state: AppState, vm: AppViewModel) {
    val c = LocalCroxColors.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = vm::closeDrawer,
                    ),
            )
        }
        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInHorizontally(tween(240)) { -it },
            exit = slideOutHorizontally(tween(220)) { -it },
        ) {
            val fraction by animateFloatAsState(
                targetValue = if (state.drawerPage == DrawerPage.Settings) 0.94f else 0.88f,
                animationSpec = tween(260),
                label = "drawerWidth",
            )
            Column(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(c.bg)
                    .rightLine(c.ink, 2.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                AnimatedContent(
                    targetState = state.drawerPage,
                    transitionSpec = {
                        if (targetState == DrawerPage.Settings) {
                            (slideInHorizontally(tween(260)) { it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(260)) { -it } + fadeOut(tween(260)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(260)))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "drawerPage",
                ) { page ->
                    when (page) {
                        DrawerPage.History -> HistoryPage(state, vm)
                        DrawerPage.Settings -> SettingsPage(state, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(state: AppState, vm: AppViewModel) {
    val c = LocalCroxColors.current
    Column(Modifier.fillMaxSize()) {
        DrawerHead(
            title = "SESSIONS · 会话",
            showBack = false,
            onBack = {},
            onClose = vm::closeDrawer,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            state.sessions.forEachIndexed { index, session ->
                SessionRow(
                    session = session,
                    selected = index == state.selectedSession,
                    onClick = { vm.selectSession(index) },
                    onDelete = { vm.deleteSession(index) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .topLine(c.ink, 2.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SqButton(
                text = "新建会话",
                onClick = vm::newChat,
                modifier = Modifier.weight(1f),
                style = SqButtonStyle.Red,
                icon = IcoPlus,
                iconSize = 11.dp,
            )
            SqButton(
                text = "设置",
                onClick = vm::openSettings,
                style = SqButtonStyle.Ghost,
                icon = IcoGear,
                iconSize = 13.dp,
            )
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionMeta,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalCroxColors.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).bottomLine(c.ink)) {
        Row(
            Modifier
                .weight(1f)
                .sqClickable(onClick)
                .padding(start = 16.dp, top = 11.dp, end = 8.dp, bottom = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                session.no,
                modifier = Modifier.padding(top = 2.dp),
                style = CroxType.sessionNo,
                color = c.red,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = CroxType.sessionTitle,
                    color = if (selected) c.red else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    session.meta,
                    style = CroxType.sessionMeta,
                    color = c.gray,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .width(44.dp)
                .fillMaxHeight()
                .leftLine(c.hair)
                .sqClickable(onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IcoClose, contentDescription = "删除", modifier = Modifier.size(12.dp), tint = c.gray)
        }
    }
}

@Composable
private fun SettingsPage(state: AppState, vm: AppViewModel) {
    val c = LocalCroxColors.current
    Column(Modifier.fillMaxSize()) {
        DrawerHead(
            title = "SETTINGS · 设置",
            showBack = true,
            onBack = vm::backFromSettings,
            onClose = vm::closeDrawer,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingSection {
                RowLabel("访问 TOKEN")
                Spacer(Modifier.height(10.dp))
                SqInput(
                    value = state.token,
                    onValueChange = vm::onTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "············",
                    mono = true,
                    password = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SqButton("保存", onClick = vm::saveToken, style = SqButtonStyle.Ink)
                    if (state.tokenSaved) {
                        Text(
                            "已保存",
                            style = TextStyle(fontSize = 10.sp, letterSpacing = 0.1.em),
                            color = c.red,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("与服务端一致；仅存本机。", style = CroxType.hint, color = c.gray)
            }
            SettingSection {
                RowLabel("主题 THEME")
                Spacer(Modifier.height(10.dp))
                SqSeg(
                    options = ThemeMode.entries.map { it.label },
                    selected = state.themeMode.ordinal,
                    onSelect = { vm.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingSection {
                RowLabel("思考预算 REASONING")
                Spacer(Modifier.height(10.dp))
                SqInput(
                    value = state.reasonBudget,
                    onValueChange = vm::onReasonBudgetChange,
                    modifier = Modifier.fillMaxWidth(),
                    mono = true,
                )
                Spacer(Modifier.height(8.dp))
                Text("0 = 不限。达到预算后模型被催着收尾、进正文。", style = CroxType.hint, color = c.gray)
            }
            SettingSection {
                RowLabel("温度 TEMPERATURE")
                Spacer(Modifier.height(6.dp))
                SqSlider(
                    value = state.temperature,
                    range = 0.1f..1.5f,
                    onValueChange = vm::onTemperatureChange,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    String.format(Locale.US, "%.2f", state.temperature),
                    style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.08.em),
                    color = c.gray,
                )
            }
            SettingSection {
                RowLabel("最大生成 MAX TOKENS")
                Spacer(Modifier.height(10.dp))
                SqInput(
                    value = state.maxTokens,
                    onValueChange = vm::onMaxTokensChange,
                    modifier = Modifier.fillMaxWidth(),
                    mono = true,
                )
            }
            SettingSection {
                RowLabel("系统提示 SYSTEM")
                Spacer(Modifier.height(10.dp))
                SqInput(
                    value = state.systemPrompt,
                    onValueChange = vm::onSystemPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "（可选）给模型的固定指令",
                    singleLine = false,
                    minHeight = 44.dp,
                )
            }
            SettingSection {
                EmotionSection(state, vm)
            }
            SettingSection(last = true) {
                SqButton(
                    text = "清空当前对话",
                    onClick = vm::clearConversation,
                    style = SqButtonStyle.Ghost,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .topLine(c.ink, 2.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NAIROCY · CROX", style = CroxType.version, color = c.gray)
            Text(
                "V1.3",
                style = CroxType.version.copy(fontWeight = FontWeight.Bold),
                color = c.red,
            )
        }
    }
}

@Composable
private fun SettingSection(last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalCroxColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (last) Modifier else Modifier.bottomLine(c.ink))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** 设置页「表情包」分区内容（须放在 SettingSection 的 ColumnScope 内）。 */
@Composable
private fun EmotionSection(state: AppState, vm: AppViewModel) {
    val c = LocalCroxColors.current
    var confirming by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshEmotions() }
    RowLabel("表情包 EMOJI")
    Spacer(Modifier.height(8.dp))
    Text(
        "模型回复以 <emo> 标签结尾时，随机展示对应情绪的图片。",
        style = CroxType.hint,
        color = c.gray,
    )
    Spacer(Modifier.height(6.dp))
    EmotionStore.WORDS.forEach { word ->
        EmotionRow(
            word = word,
            count = state.emotionCounts[word] ?: 0,
            importing = state.emotionImporting,
            confirming = confirming == word,
            onPick = { uris ->
                confirming = null
                vm.importEmotions(word, uris)
            },
            onArmClear = { confirming = word },
            onConfirmClear = {
                confirming = null
                vm.clearEmotions(word)
            },
        )
    }
    state.emotionStatus?.let { status ->
        Spacer(Modifier.height(6.dp))
        Text(status, style = CroxType.hint, color = c.red)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "目录：${state.emotionDir.ifEmpty { "…" }}",
        style = CroxType.hint,
        color = c.gray,
    )
}

@Composable
private fun EmotionRow(
    word: String,
    count: Int,
    importing: Boolean,
    confirming: Boolean,
    onPick: (List<Uri>) -> Unit,
    onArmClear: () -> Unit,
    onConfirmClear: () -> Unit,
) {
    val c = LocalCroxColors.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> onPick(uris) },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .bottomLine(c.hair)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                word.uppercase(Locale.US),
                style = CroxType.sessionMeta,
                color = c.ink,
                maxLines = 1,
            )
            Text(
                " · ${EmotionStore.label(word).orEmpty()}",
                style = CroxType.settingInput,
                color = c.ink,
                maxLines = 1,
            )
        }
        Text("$count 张", style = CroxType.meta, color = c.gray)
        Spacer(Modifier.width(8.dp))
        SqButton(
            text = if (importing) "导入中" else "导入",
            onClick = { picker.launch(arrayOf("image/*")) },
            style = SqButtonStyle.Ghost,
        )
        Spacer(Modifier.width(6.dp))
        if (confirming) {
            SqButton(
                text = "确认",
                onClick = onConfirmClear,
                style = SqButtonStyle.Red,
            )
        } else {
            SqButton(
                text = "清空",
                onClick = onArmClear,
                style = SqButtonStyle.Ghost,
            )
        }
    }
}

@Composable
private fun DrawerHead(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalCroxColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .bottomLine(c.ink, 2.dp)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBack) {
            HeadIcon(IcoBack, 14.dp, onBack, "返回")
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = CroxType.drawerTitle,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HeadIcon(IcoClose, 13.dp, onClose, "关闭")
    }
}

@Composable
private fun HeadIcon(icon: ImageVector, iconSize: Dp, onClick: () -> Unit, desc: String) {
    val c = LocalCroxColors.current
    Box(
        Modifier
            .size(34.dp)
            .border(1.dp, c.ink)
            .sqClickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(iconSize), tint = c.ink)
    }
}
