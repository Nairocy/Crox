package com.nairocy.crox.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nairocy.crox.AppState
import com.nairocy.crox.AppViewModel
import com.nairocy.crox.ChatMode
import com.nairocy.crox.DrawerPage
import com.nairocy.crox.SwitchState

private const val MAX_PICK_IMAGES = 4

@Composable
fun MainScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val c = LocalCroxColors.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 抽屉打开/在其内换页时收起软键盘（避免“抽屉+键盘”同屏）
    LaunchedEffect(state.drawerOpen, state.drawerPage) {
        if (state.drawerOpen) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_IMAGES),
    ) { uris ->
        vm.onImagesPicked(uris)
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onOpenDrawer = vm::openDrawer,
                onNewChat = vm::newChat,
                onSwitchTerminal = vm::switchTerminal,
                onSwitchMode = vm::switchMode,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = state.messages.isEmpty(),
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                    label = "content",
                ) { empty ->
                    if (empty) {
                        HomeEmpty(state.terminal, state.switchState, vm::useSuggestion)
                    } else {
                        MessageList(
                            messages = state.messages,
                            terminal = state.terminal,
                            errorText = state.errorText,
                            imageFileFor = vm::imageFileFor,
                            emotionFileFor = vm::emotionFileFor,
                            onToggleReasoning = vm::toggleReasoning,
                            onCopy = vm::copyMessage,
                            onRegenerate = vm::regenerate,
                            onEditUser = vm::editUserMessage,
                        )
                    }
                }
            }
            StatusLine(state)
            Composer(
                state = state,
                onImage = {
                    if (state.mode == ChatMode.Raw) {
                        vm.showNotice("续写模式不支持图片，请切回对话模式")
                    } else {
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                },
                onRemoveImage = vm::removePendingImage,
                onToggleThink = vm::toggleThink,
                onDraftChange = vm::onDraftChange,
                onSend = vm::send,
                onStop = vm::stop,
            )
        }
        DrawerOverlay(state, vm)
    }
    BackHandler(enabled = state.drawerOpen) {
        if (state.drawerPage == DrawerPage.Settings) {
            vm.backFromSettings()
        } else {
            vm.closeDrawer()
        }
    }
}

@Composable
private fun StatusLine(state: AppState) {
    val c = LocalCroxColors.current
    var text: String? = null
    var isError = false
    when (val sw = state.switchState) {
        is SwitchState.Waking -> {
            text = "◐ 正在唤醒 Crox ${state.terminal.label}…"
        }
        is SwitchState.Error -> {
            text = "○ ${sw.message}"
            isError = true
        }
        is SwitchState.Ready -> {
            when {
                state.errorText != null -> {
                    text = "■ ${state.errorText}"
                    isError = true
                }
                state.notice != null -> {
                    text = state.notice
                }
            }
        }
    }
    if (text != null) {
        Text(
            text,
            modifier = Modifier
                .fillMaxWidth()
                .background(c.bg)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            style = CroxType.meta,
            color = if (isError) c.red else c.gray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
