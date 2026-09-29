package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.engine.FactionId
import com.reef.engine.GameState
import com.reef.engine.Plates

/** A full-screen sheet with a title bar and a Close button. */
@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Reef.night).clickable(onClick = {})) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Reef.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = Reef.current) }
            }
            Line()
            content()
        }
    }
}

@Composable
fun LogDialog(g: GameState, onClose: () -> Unit) {
    Sheet("What happened", onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (line in g.log.reversed()) {
                val round = line.startsWith("Round ")
                Text(line, color = if (round) Reef.ink else Reef.muted, fontWeight = if (round) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesDialog(factions: List<FactionId>, onClose: () -> Unit) {
    var tab by remember { mutableStateOf<FactionId?>(factions.firstOrNull()) }
    Sheet("Rules", onClose) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in factions) Chip(f.display, primary = tab == f) { tab = f }
            Chip("Shared rules", primary = tab == null) { tab = null }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val f = tab
            if (f == null) SharedRules() else PlateView(f)
        }
    }
}

@Composable
fun SharedRules() {
    for (r in Plates.shared) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.title, color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(r.text, color = Reef.muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun PlateView(f: FactionId) {
    val plate = Plates.of(f)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${plate.name} · ${plate.role}", color = Reef.faction(f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("“${plate.tagline}”", color = Reef.ink, fontStyle = FontStyle.Italic, fontSize = 16.sp)
        Text(plate.idea, color = Reef.ink, fontSize = 14.sp)
        Section("Its three rules")
        for (r in plate.rules) {
            Text(r.title, color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(r.text, color = Reef.muted, fontSize = 14.sp)
        }
        Section("A turn")
        for (t in plate.turn) {
            Row {
                Text(t.phase, color = Reef.current, fontSize = 13.sp, modifier = Modifier.width(52.dp))
                Text(t.text, color = Reef.muted, fontSize = 14.sp)
            }
        }
        Section("Scores")
        for (s in plate.scores) Text(s, color = Reef.muted, fontSize = 14.sp)
        Section("Pieces")
        Text("${plate.pieces} Setup: ${plate.setup}", color = Reef.muted, fontSize = 14.sp)
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = Reef.muted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
}
