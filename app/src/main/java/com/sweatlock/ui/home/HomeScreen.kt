package com.sweatlock.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweatlock.blocking.Perms
import com.sweatlock.ui.MainViewModel
import com.sweatlock.ui.rememberPermTick
import com.sweatlock.ui.theme.*
import com.sweatlock.R

@Composable
fun HomeScreen(onPractice: () -> Unit, onOpenApps: () -> Unit, vm: MainViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val st by vm.stats.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val tick = rememberPermTick()
    val serviceOn = remember(tick) { Perms.accessibility(ctx) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)) {
                Image(painterResource(R.drawable.logo), null, Modifier.fillMaxSize().padding(8.dp))
            }
            Column {
                Text("SweatLock", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Earn your scroll.", color = Muted, fontSize = 17.sp)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Blocking", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(if (s.enabled) "${s.blocked.size} apps locked" else "Paused", color = Muted)
                }
                Switch(
                    checked = s.enabled, onCheckedChange = vm::setEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Good, checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0x40FFFFFF), uncheckedThumbColor = Color.White,
                        uncheckedBorderColor = Color.Transparent, checkedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // Edge case: service killed / disabled (battery optimisation, user toggled it off)
        if (!serviceOn) {
            GlassCard(Modifier.fillMaxWidth().clickable { runCatching { ctx.startActivity(Perms.accessibilityIntent()) } }) {
                Column(Modifier.padding(20.dp)) {
                    Text("\u26A0\uFE0F Blocker is off", fontWeight = FontWeight.SemiBold, color = Warn, fontSize = 17.sp)
                    Text("Apps won't be locked. Tap to turn the SweatLock accessibility service back on.", color = Muted, fontSize = 14.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Today", "${st.today}", "reps", Modifier.weight(1f))
            StatCard("Streak", "${st.streak}", if (st.streak == 1) "day" else "days", Modifier.weight(1f))
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your rule", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "${s.exercise.emoji} ${s.reps} ${s.exercise.unit} \u2192 ${s.durationMin} min of access",
                    color = Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Choose apps", onOpenApps)
                    GlassButton("Practice", onPractice, primary = true)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Muted, fontSize = 14.sp)
            Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(unit, color = Muted, fontSize = 14.sp)
        }
    }
}
