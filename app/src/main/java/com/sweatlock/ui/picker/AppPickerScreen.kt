package com.sweatlock.ui.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweatlock.ui.AppsViewModel
import com.sweatlock.ui.theme.*

@Composable
fun AppPickerScreen(vm: AppsViewModel = hiltViewModel()) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Blocked apps", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("${blocked.size} selected", color = Muted)

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search apps", color = Muted) }, singleLine = true,
            shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedContainerColor = Color.White.copy(alpha = 0.16f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.30f), focusedBorderColor = Accent
            )
        )

        GlassCard(Modifier.fillMaxWidth().weight(1f)) {
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading apps\u2026", color = Muted) }
            } else {
                val filtered = list.filter { it.label.contains(query, ignoreCase = true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggle(app.pkg) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Image(app.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                            Text(app.label, Modifier.weight(1f), color = Ink, fontSize = 17.sp)
                            Checkbox(
                                checked = app.pkg in blocked, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = Color.White.copy(alpha = 0.5f), checkmarkColor = Color.White)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
