package com.sweatlock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0A0F2E)
val Accent = Color(0xFF0A84FF)      // iOS blue
val Good = Color(0xFF30D158)        // iOS green
val Warn = Color(0xFFFF9F0A)        // iOS orange
val Ink = Color.White
val Muted = Color(0xB3FFFFFF)       // white @ 70%

@Composable
fun SweatLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Bg,
            onSurface = Color.White, onBackground = Color.White
        ),
        content = content
    )
}
