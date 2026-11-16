package com.sweatlock.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.sweatlock.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sweatlock.blocking.Perms
import com.sweatlock.ui.rememberPermTick
import com.sweatlock.ui.theme.*

private data class PermItem(
    val emoji: String, val title: String, val why: String,
    val granted: Boolean, val required: Boolean,
    val action: () -> Unit, val tip: String? = null
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberPermTick()
    var extra by remember { mutableIntStateOf(0) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { extra++ }

    fun open(intent: android.content.Intent) {
        runCatching { ctx.startActivity(intent) }
    }

    val items = remember(tick, extra) {
        listOf(
            PermItem("\uD83D\uDCF7", "Camera", "Counts your reps on-device. Nothing is recorded or uploaded.",
                Perms.camera(ctx), true, { camLauncher.launch(Manifest.permission.CAMERA) },
                "If nothing happens, enable Camera in the app's settings."),
            PermItem("\u267F", "Accessibility service", "Lets SweatLock see which app is in front so it can lock the ones you chose. It never reads your screen.",
                Perms.accessibility(ctx), true, { open(Perms.accessibilityIntent()) },
                "Toggle greyed out? Open App info \u2192 \u22EE menu \u2192 Allow restricted settings, then try again."),
            PermItem("\uD83E\uDE9F", "Display over other apps", "Lets the lock screen appear instantly on top of a blocked app. This is what makes the lock reliable.",
                Perms.overlay(ctx), true, { open(Perms.overlayIntent(ctx)) },
                "On vivo / iQOO also enable 'Display pop-ups while running in the background' in App info \u2192 Permissions."),
            PermItem("\uD83D\uDCCA", "Usage access", "Backup way to notice the open app if the accessibility service misses an event.",
                Perms.usageAccess(ctx), false, { open(Perms.usageIntent()) }),
            PermItem("\uD83D\uDD0B", "Battery: unrestricted", "Stops your phone from killing the blocker in the background.",
                Perms.batteryUnrestricted(ctx), false, { open(Perms.batteryIntent()) },
                "Choose SweatLock \u2192 Don't optimize.")
        )
    }
    val ready = items.filter { it.required }.all { it.granted }

    GlassBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(88.dp))
            Text("Set up SweatLock", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("A few permissions make the lock work. Each one is explained below.", color = Muted)

            items.forEach { p ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(p.emoji, fontSize = 24.sp)
                            Text(p.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                            Text(if (p.required) "Required" else "Recommended", fontSize = 12.sp, color = Muted)
                        }
                        Text(p.why, color = Muted, fontSize = 14.sp)
                        if (!p.granted && p.tip != null) Text(p.tip, color = Warn, fontSize = 12.sp)
                        if (p.granted) Text("Granted \u2713", color = Good, fontWeight = FontWeight.SemiBold)
                        else GlassButton("Allow", p.action, primary = true)
                    }
                }
            }

            GlassButton("Continue", onDone, Modifier.fillMaxWidth(), primary = true, enabled = ready)
            GlassButton("Skip for now", onDone, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}
