package com.nairocy.crox

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nairocy.crox.ui.CroxTheme
import com.nairocy.crox.ui.MainScreen
import com.nairocy.crox.ui.isCroxDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val state by vm.state.collectAsState()
            val dark = isCroxDark(state.themeMode)
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CroxTheme(themeMode = state.themeMode) {
                MainScreen(vm)
            }
        }
    }
}
