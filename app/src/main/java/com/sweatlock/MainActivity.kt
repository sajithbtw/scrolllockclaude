package com.sweatlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.sweatlock.blocking.Perms
import com.sweatlock.ui.MainScaffold
import com.sweatlock.ui.onboarding.OnboardingScreen
import com.sweatlock.ui.theme.SweatLockTheme
import com.sweatlock.ui.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            SweatLockTheme {
                val ctx = LocalContext.current
                // Skip onboarding when the two required permissions are already granted.
                var onboarded by rememberSaveable { mutableStateOf(Perms.camera(ctx) && Perms.accessibility(ctx)) }
                var practice by rememberSaveable { mutableStateOf(false) }
                var tab by rememberSaveable { mutableIntStateOf(0) }

                when {
                    !onboarded -> OnboardingScreen(onDone = { onboarded = true })
                    practice -> {
                        BackHandler { practice = false }
                        WorkoutScreen(onClose = { practice = false })
                    }
                    else -> MainScaffold(
                        tab = tab, onTab = { tab = it },
                        onPractice = { practice = true },
                        onPermissions = { onboarded = false }
                    )
                }
            }
        }
    }
}
