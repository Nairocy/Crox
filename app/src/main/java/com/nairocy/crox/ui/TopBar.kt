package com.nairocy.crox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nairocy.crox.AppState
import com.nairocy.crox.Terminal

@Composable
fun TopBar(
    state: AppState,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSwitchTerminal: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val c = LocalCroxColors.current
    Column(Modifier.fillMaxWidth().background(c.topbarBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier
                    .height(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenDrawer,
                    )
                    .padding(start = 2.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).background(c.red))
                Spacer(Modifier.width(8.dp))
                Text(
                    "CROX",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em),
                    color = c.topbarFg,
                )
            }
            Text(
                state.title,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                style = CroxType.topTitle,
                color = c.topbarFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TopBarChip(state.terminal.label, onSwitchTerminal)
            TopBarChip(state.mode.label, onSwitchMode)
            Box(
                Modifier
                    .size(36.dp)
                    .border(1.dp, c.topbarBorder)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNewChat,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IcoPlus, contentDescription = "新对话", modifier = Modifier.size(14.dp), tint = c.topbarFg)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.ink))
    }
}

@Composable
private fun TopBarChip(label: String, onClick: () -> Unit) {
    val c = LocalCroxColors.current
    Row(
        Modifier
            .height(36.dp)
            .border(1.dp, c.topbarBorder)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em),
            color = c.topbarFg,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            IcoSwap,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = c.topbarFg.copy(alpha = 0.75f),
        )
    }
}
