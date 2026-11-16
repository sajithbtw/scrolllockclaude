package com.sweatlock.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sweatlock.ui.theme.SweatLockTheme
import com.sweatlock.ui.workout.WorkoutScreen
import dagger.hilt.android.AndroidEntryPoint

/** Full-screen "do your task" screen shown over a blocked app. */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {
    private var pkg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG)

        // Back must not return to the blocked app; send the user home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            SweatLockTheme {
                val p = pkg
                val label = remember(p) {
                    p?.let {
                        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }
                            .getOrNull()
                    } ?: "this app"
                }
                WorkoutScreen(
                    lockedPackage = p,
                    lockedLabel = label,
                    onUnlocked = {
                        p?.let { packageManager.getLaunchIntentForPackage(it)?.let(::startActivity) }
                        finish()
                    },
                    onClose = ::goHome
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG) // a different blocked app triggered the lock
    }

    /** Leaving the lock screen (home, recents) closes it; the service re-opens it when needed. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object { const val EXTRA_PKG = "pkg" }
}
