package com.nairocy.crox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nairocy.crox.ThemeMode

@Immutable
data class CroxColors(
    val bg: Color,
    val ink: Color,
    val red: Color,
    val gray: Color,
    val hair: Color,
    val soft: Color,
    val topbarBg: Color,
    val topbarFg: Color,
    val topbarBorder: Color,
    val heroBg: Color,
    val heroFg: Color,
    val heroSub: Color,
    val codeBg: Color,
    val codeFg: Color,
    val userRowBg: Color,
    val scrim: Color,
    val placeholder: Color,
    val thumbBg: Color,
    val thumbLine: Color,
    val inlineCodeBg: Color,
)

val LightCroxColors = CroxColors(
    bg = Color(0xFFFFFFFF),
    ink = Color(0xFF0B0B0B),
    red = Color(0xFFE30613),
    gray = Color(0xFF6F6F6F),
    hair = Color(0xFFD8D8D8),
    soft = Color(0xFFF6F6F6),
    topbarBg = Color(0xFF0B0B0B),
    topbarFg = Color(0xFFFFFFFF),
    topbarBorder = Color.White.copy(alpha = 0.55f),
    heroBg = Color(0xFF0B0B0B),
    heroFg = Color(0xFFFFFFFF),
    heroSub = Color.White.copy(alpha = 0.62f),
    codeBg = Color(0xFF0B0B0B),
    codeFg = Color(0xFFFFFFFF),
    userRowBg = Color(0xFFF6F6F6),
    scrim = Color(0xFF0B0B0B).copy(alpha = 0.32f),
    placeholder = Color(0xFF9A9A9A),
    thumbBg = Color(0xFFEDEDED),
    thumbLine = Color(0xFFBCBCBC),
    inlineCodeBg = Color(0xFFEDEDED),
)

val DarkCroxColors = CroxColors(
    bg = Color(0xFF0B0B0B),
    ink = Color(0xFFF2F2F2),
    red = Color(0xFFFF3B3B),
    gray = Color(0xFF9A9A9A),
    hair = Color(0xFF2E2E2E),
    soft = Color(0xFF1A1A1A),
    topbarBg = Color(0xFFF2F2F2),
    topbarFg = Color(0xFF0B0B0B),
    topbarBorder = Color(0xFF0B0B0B).copy(alpha = 0.55f),
    heroBg = Color(0xFFF2F2F2),
    heroFg = Color(0xFF0B0B0B),
    heroSub = Color(0xFF0B0B0B).copy(alpha = 0.62f),
    codeBg = Color(0xFFF2F2F2),
    codeFg = Color(0xFF0B0B0B),
    userRowBg = Color(0xFF1A1A1A),
    scrim = Color.Black.copy(alpha = 0.45f),
    placeholder = Color(0xFF6F6F6F),
    thumbBg = Color(0xFF1A1A1A),
    thumbLine = Color(0xFF3A3A3A),
    inlineCodeBg = Color(0xFF262626),
)

val LocalCroxColors = staticCompositionLocalOf { LightCroxColors }

object CroxType {
    val heroTitle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.5.sp)
    val heroSub = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.34.em, lineHeight = 13.sp)
    val msgBody = TextStyle(fontSize = 14.5.sp, lineHeight = 24.65.sp)
    val rowLabel = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.em, lineHeight = 14.sp)
    val meta = TextStyle(fontSize = 10.5.sp, letterSpacing = 0.08.em, lineHeight = 14.sp)
    val topTitle = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val btn = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em, lineHeight = 15.sp)
    val reasoning = TextStyle(fontSize = 12.sp, lineHeight = 21.sp)
    val reasoningToggle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)
    val code = TextStyle(fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 19.5.sp)
    val kvKey = TextStyle(fontSize = 9.5.sp, letterSpacing = 0.22.em, lineHeight = 13.sp)
    val kvVal = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp)
    val sect = TextStyle(fontSize = 9.5.sp, letterSpacing = 0.28.em, lineHeight = 13.sp)
    val cmd = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val input = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp)
    val settingLabel = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.26.em, lineHeight = 13.sp)
    val hint = TextStyle(fontSize = 10.5.sp, lineHeight = 17.sp)
    val settingInput = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp)
    val drawerTitle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.em, lineHeight = 14.sp)
    val sessionTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    val sessionMeta = TextStyle(fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.14.em, lineHeight = 13.sp)
    val sessionNo = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, lineHeight = 14.sp)
    val version = TextStyle(fontSize = 9.5.sp, letterSpacing = 0.14.em, lineHeight = 13.sp)
}

@Composable
fun isCroxDark(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> isSystemInDarkTheme()
}

@Composable
fun CroxTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isCroxDark(themeMode)
    val crox = if (dark) DarkCroxColors else LightCroxColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = crox.red,
            onPrimary = Color.White,
            background = crox.bg,
            onBackground = crox.ink,
            surface = crox.bg,
            onSurface = crox.ink,
            surfaceVariant = crox.soft,
            onSurfaceVariant = crox.ink,
            outline = crox.ink,
            error = crox.red,
        )
    } else {
        lightColorScheme(
            primary = crox.red,
            onPrimary = Color.White,
            background = crox.bg,
            onBackground = crox.ink,
            surface = crox.bg,
            onSurface = crox.ink,
            surfaceVariant = crox.soft,
            onSurfaceVariant = crox.ink,
            outline = crox.ink,
            error = crox.red,
        )
    }
    CompositionLocalProvider(LocalCroxColors provides crox) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
