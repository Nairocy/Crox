package com.nairocy.crox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nairocy.crox.SwitchState
import com.nairocy.crox.Terminal

@Composable
fun HomeEmpty(terminal: Terminal, switchState: SwitchState, onSuggestion: (String) -> Unit) {
    val c = LocalCroxColors.current
    val model = if (terminal == Terminal.T1) "Crox T2（4B-q4_k_m）" else "Crox P2（4B-q4_k_m）"
    val suggestions = if (terminal == Terminal.T1) {
        listOf(
            "设计一个书架应用的数据库 schema",
            "解释 CAP 定理，带一个生活例子",
            "写一周训练计划，注意恢复日安排",
        )
    } else {
        listOf(
            "介绍一下你自己",
            "讲个冷笑话",
            "今天有点累，聊聊天",
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.heroBg)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 22.dp),
        ) {
            Text(terminal.hero, style = CroxType.heroTitle, color = c.heroFg)
            Spacer(Modifier.height(9.dp))
            Text(terminal.sub, style = CroxType.heroSub, color = c.heroSub)
        }
        KvRow("MODEL", model, mono = true, top = false)
        KvRow("CONTEXT", "16K tokens", mono = true, top = true)
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .topLine(c.ink),
        ) {
            Box(
                Modifier
                    .width(118.dp)
                    .fillMaxHeight()
                    .rightLine(c.ink)
                    .padding(horizontal = 20.dp, vertical = 9.dp),
            ) {
                Text("STATE", style = CroxType.kvKey, color = c.gray)
            }
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (symbol, label, symbolColor) = when (switchState) {
                    is SwitchState.Ready -> Triple("●", "就绪", c.red)
                    is SwitchState.Waking -> Triple("◐", "唤醒中", c.ink)
                    is SwitchState.Error -> Triple("○", "离线", c.gray)
                }
                Text(symbol, style = CroxType.kvVal, color = symbolColor)
                Spacer(Modifier.width(7.dp))
                Text(label, style = CroxType.kvVal, color = c.ink)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink))
        Text(
            "SUGGESTED / 建议指令",
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            style = CroxType.sect,
            color = c.gray,
        )
        suggestions.forEachIndexed { index, text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .topLine(c.hair)
                    .sqClickable { onSuggestion(text) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    "0${index + 1}",
                    modifier = Modifier.width(20.dp),
                    style = CroxType.sessionNo,
                    color = c.red,
                )
                Spacer(Modifier.width(12.dp))
                Text(text, style = CroxType.cmd, color = c.ink)
            }
        }
    }
}

@Composable
private fun KvRow(key: String, value: String, mono: Boolean, top: Boolean) {
    val c = LocalCroxColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (top) Modifier.topLine(c.ink) else Modifier),
    ) {
        Box(
            Modifier
                .width(118.dp)
                .fillMaxHeight()
                .rightLine(c.ink)
                .padding(horizontal = 20.dp, vertical = 9.dp),
        ) {
            Text(key, style = CroxType.kvKey, color = c.gray)
        }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(
                value,
                style = if (mono) CroxType.kvVal else CroxType.cmd,
                color = c.ink,
            )
        }
    }
}
