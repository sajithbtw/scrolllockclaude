package com.sweatlock.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweatlock.ui.MainViewModel
import com.sweatlock.ui.theme.*
import java.time.LocalDate

@Composable
fun StatsScreen(vm: MainViewModel = hiltViewModel()) {
    val st by vm.stats.collectAsStateWithLifecycle()
    val max = (st.week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Stats", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("Reps today", color = Muted)
                Text("${st.today}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Text("\uD83D\uDD25 Streak", color = Muted)
                    Text("${st.streak}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(if (st.streak == 1) "day" else "days", color = Muted, fontSize = 14.sp)
                }
            }
            GlassCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Text("All time", color = Muted)
                    Text("${st.total}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("reps", color = Muted, fontSize = 14.sp)
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Last 7 days", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().height(150.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom
                ) {
                    st.week.forEachIndexed { i, (day, reps) ->
                        val isToday = i == st.week.lastIndex
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("$reps", fontSize = 11.sp, color = Muted)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.width(26.dp).height((90f * reps / max).coerceAtLeast(4f).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isToday) Accent else Accent.copy(alpha = 0.35f))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("MTWTFSS"[LocalDate.ofEpochDay(day).dayOfWeek.value - 1].toString(), fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }
}
