package com.sweatlock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sweatlock.ui.home.HomeScreen
import com.sweatlock.ui.picker.AppPickerScreen
import com.sweatlock.ui.settings.SettingsScreen
import com.sweatlock.ui.stats.StatsScreen
import com.sweatlock.ui.theme.GlassBackground
import com.sweatlock.ui.theme.GlassTabBar

@Composable
fun MainScaffold(tab: Int, onTab: (Int) -> Unit, onPractice: () -> Unit, onPermissions: () -> Unit) {
    GlassBackground {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom = 112.dp)) {
                when (tab) {
                    0 -> HomeScreen(onPractice = onPractice, onOpenApps = { onTab(1) })
                    1 -> AppPickerScreen()
                    2 -> StatsScreen()
                    else -> SettingsScreen(onPermissions)
                }
            }
            GlassTabBar(tab, onTab, Modifier.align(Alignment.BottomCenter))
        }
    }
}
