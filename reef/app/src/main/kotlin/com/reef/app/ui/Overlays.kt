package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.app.ui.art.Icons
import com.reef.engine.FactionId
import com.reef.engine.GameState
import com.reef.engine.Plates

/** A full-screen sheet with a title bar and a Close button. */
@Composable
private fun Sheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Reef.night).clickable(onClick = {})) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun LogDialog(g: GameState, version: Int, onClose: () -> Unit) {
    // Keyed on the version: the game state changes in place, so this must rebuild after every move.
    key(version) {
        Sheet("What happened", onClose) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (line in g.log.reversed()) {
                    val round = line.startsWith("Round ")
                    val f = FactionId.entries.firstOrNull { line.startsWith(it.display + ":") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (f != null) {
                            Portrait(f, 18.dp, ring = 1.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(line, color = if (round) Reef.ink else Reef.muted, fontWeight = if (round) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RulesDialog(factions: List<FactionId>, onClose: () -> Unit) {
    var tab by remember { mutableStateOf<FactionId?>(factions.firstOrNull()) }
    Sheet("Rules", onClose) {
        ChipRow {
            for (f in factions) Chip(f.display, primary = tab == f, faction = f) { tab = f }
            Chip("Shared rules", primary = tab == null, image = Icons.card) { tab = null }
        }
        val f = tab
        if (f == null) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { SharedRules() }
        } else {
            FactionBoard(f, Modifier.weight(1f))
        }
    }
}

/** The rules every faction shares, each beside a picture, two to a row. */
@Composable
fun SharedRules() {
    for (row in Plates.shared.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in row) {
                Row(
                    Modifier.weight(1f).background(Reef.raised, RoundedCornerShape(12.dp)).border(1.dp, Reef.line, RoundedCornerShape(12.dp)).padding(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(40.dp).background(Color(0xFF0E3042), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        ArtImage(sharedIcon(r.title), 34.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(r.title, color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(stripTags(r.text), color = Reef.muted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun sharedIcon(title: String) = when (title) {
    "Twelve reefs" -> com.reef.app.ui.art.Suits.kelp
    "Edges of the map" -> com.reef.app.ui.art.Scenery.hole
    "A turn" -> Icons.day
    "Pieces" -> com.reef.app.ui.art.Tokens.coral
    "Rule and moving" -> Icons.move
    "Currents" -> Icons.current
    "Attacks and hits", "Battle" -> Icons.battle
    "Cards" -> Icons.card
    "Gear" -> Icons.craft
    "Winning" -> Icons.dominance
    else -> Icons.done
}

/**
 * A faction's board: who they are on the left; on the right, their three rules each with a
 * picture, the shape of their turn from Dawn to Dusk, and how they score. The words come from
 * the design guide.
 */
@Composable
fun FactionBoard(f: FactionId, modifier: Modifier = Modifier) {
    val plate = Plates.of(f)
    val color = Reef.faction(f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.width(230.dp).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)), RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Portrait(f, 96.dp, ring = 3.dp)
            Text(plate.name, color = Reef.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(plate.role, color = color, fontSize = 13.sp)
            Text("complexity " + "●".repeat(plate.complexity) + "○".repeat(4 - plate.complexity) + " · reach ${plate.reach}", color = Reef.muted, fontSize = 11.sp)
            Text("“${plate.tagline}”", color = Reef.ink, fontStyle = FontStyle.Italic, fontSize = 14.sp)
            Text(plate.idea, color = Reef.muted, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Three rules")
            for (r in plate.rules) {
                Row(
                    Modifier.fillMaxWidth().background(Reef.raised, RoundedCornerShape(12.dp)).border(1.dp, Reef.line, RoundedCornerShape(12.dp)).padding(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(44.dp).background(Color(0xFF0E3042), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        ArtImage(Guide.ruleIcon(f, r.title), 38.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(r.title, color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(stripTags(r.text), color = Reef.muted, fontSize = 12.5.sp, lineHeight = 16.sp)
                    }
                }
            }
            SectionTitle("A turn")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in plate.turn) {
                    val icon = when (t.phase) {
                        "Dawn" -> Icons.dawn
                        "Day" -> Icons.day
                        else -> Icons.endday
                    }
                    Column(
                        Modifier.weight(1f).background(Reef.surface, RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArtImage(icon, 26.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(t.phase, color = Reef.current, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(t.text, color = Reef.muted, fontSize = 12.sp, lineHeight = 15.sp)
                    }
                }
            }
            SectionTitle("Scoring")
            for (s in plate.scores) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VpBadge("VP")
                    Spacer(Modifier.width(8.dp))
                    Text(s, color = Reef.ink, fontSize = 13.sp)
                }
            }
            SectionTitle("Pieces and setup")
            Text("${plate.pieces} Setup: ${plate.setup}", color = Reef.muted, fontSize = 13.sp)
        }
    }
}

/** The design text marks lure names in italics; the app shows them plain. */
private fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), "")
