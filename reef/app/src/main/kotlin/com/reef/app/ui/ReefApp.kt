package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.engine.FactionId
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.Phase
import com.reef.engine.Plates
import com.reef.engine.Seat

private sealed class Screen {
    data object Home : Screen()
    data object Setup : Screen()
    data object HowToPlay : Screen()
    class Play(val controller: GameController) : Screen()
}

/**
 * The whole app. [backHandler] lets the platform route its Back button here (Android's
 * BackHandler), keeping this file free of platform code.
 */
@Composable
fun ReefApp(store: SaveStore, backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var lastSeats by remember { mutableStateOf<List<Seat>?>(null) }

    fun savedGame(): GameState? = store.load()?.let { runCatching { GameState.fromJson(it) }.getOrNull() }?.takeIf { it.phase != Phase.OVER }

    fun start(seats: List<Seat>) {
        lastSeats = seats
        val g = Game.newGame(seats, System.nanoTime())
        store.save(g.toJson())
        screen = Screen.Play(GameController(g, store))
    }

    backHandler(screen != Screen.Home) { screen = Screen.Home }

    Box(Modifier.fillMaxSize().background(Reef.night).windowInsetsPadding(WindowInsets.safeDrawing)) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                saved = savedGame(),
                onContinue = { g -> screen = Screen.Play(GameController(g, store)) },
                onNew = { screen = Screen.Setup },
                onHowToPlay = { screen = Screen.HowToPlay },
            )
            Screen.Setup -> SetupScreen(onStart = { start(it) }, onBack = { screen = Screen.Home })
            Screen.HowToPlay -> RulesDialog(FactionId.entries) { screen = Screen.Home }
            is Screen.Play -> GameScreen(
                controller = s.controller,
                onExit = { screen = Screen.Home },
                onRematch = {
                    val seats = lastSeats ?: s.controller.game.players.map { Seat(it.faction, it.human) }
                    start(seats)
                },
            )
        }
    }
}

@Composable
internal fun HomeScreen(saved: GameState?, onContinue: (GameState) -> Unit, onNew: () -> Unit, onHowToPlay: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1F7C92), Color(0xFF0A3347), Color(0xFF051B26)))),
        contentAlignment = Alignment.Center,
    ) {
        // A ring of all fourteen factions around the title.
        Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FactionId.entries.take(7)) Portrait(f, 44.dp)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (f in FactionId.entries.drop(7)) Portrait(f, 44.dp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Reef", color = Reef.ink, fontSize = 60.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light)
            Text("Fourteen creatures. One reef. Nobody plays by the same rules.", color = Reef.ink.copy(alpha = 0.8f))
            if (saved != null) {
                Button(onClick = { onContinue(saved) }, colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night)) {
                    Text("Continue: round ${saved.round}, " + saved.players.joinToString(" vs ") { "${it.faction.display} ${it.vp}" })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (saved == null) {
                    Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night)) { Text("New game") }
                } else {
                    OutlinedButton(onClick = onNew) { Text("New game", color = Reef.ink) }
                }
                OutlinedButton(onClick = onHowToPlay) { Text("How to play", color = Reef.ink) }
            }
        }
    }
}

/** The lowest total reach each table size needs, so somebody can always check a runaway leader. */
private val REACH_NEEDED = mapOf(2 to 12, 3 to 15, 4 to 18)

/** Choose 2 to 4 factions, who plays each (a person or a bot), and the order of play. */
@Composable
internal fun SetupScreen(onStart: (List<Seat>) -> Unit, onBack: () -> Unit, initial: List<FactionId> = listOf(FactionId.SHARKS, FactionId.CORAL)) {
    val chosen = remember { mutableStateListOf(*initial.toTypedArray()) }
    val human = remember { mutableStateListOf(*initial.mapIndexed { i, _ -> i == 0 }.toTypedArray()) }
    var info by remember { mutableStateOf<FactionId?>(null) }

    fun toggle(f: FactionId) {
        val i = chosen.indexOf(f)
        if (i >= 0) {
            chosen.removeAt(i); human.removeAt(i)
        } else if (chosen.size < 4) {
            chosen.add(f); human.add(chosen.size == 1)
        }
    }

    val reach = chosen.sumOf { Plates.of(it).reach }
    val needed = REACH_NEEDED[chosen.size]
    val problem = when {
        chosen.size < 2 -> "Choose at least 2 factions."
        FactionId.REMORAS in chosen && chosen.size < 3 -> "Remoras need at least two other factions to ride."
        needed != null && reach < needed -> "Reach $reach of $needed: too few factions attack, so nobody could stop a runaway leader. Add a hunter such as the Sharks, Lionfish or Octopus."
        else -> null
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Choose 2 to 4 factions", color = Reef.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            for (row in FactionId.entries.chunked(7)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (f in row) FactionTile(f, chosen.indexOf(f), Modifier.weight(1f), onInfo = { info = f }) { toggle(f) }
                }
            }
            Text("Tap a faction to add it to the table or take it off. ? opens its board.", color = Reef.muted, fontSize = 12.sp)
        }
        Column(Modifier.width(262.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("The table", color = Reef.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onBack) { Text("Back", color = Reef.ink) }
            }
            chosen.forEachIndexed { i, f ->
                Row(
                    Modifier.fillMaxWidth().background(Reef.surface, RoundedCornerShape(10.dp)).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = Reef.muted, fontSize = 13.sp, modifier = Modifier.width(14.dp))
                    Portrait(f, 34.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(f.display, color = Reef.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Toggle("Person", human[i]) { human[i] = true }
                    Spacer(Modifier.width(4.dp))
                    Toggle("Bot", !human[i]) { human[i] = false }
                    if (i > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text("▲", color = Reef.muted, fontSize = 14.sp, modifier = Modifier.clickable {
                            chosen.add(i - 1, chosen.removeAt(i)); human.add(i - 1, human.removeAt(i))
                        }.padding(4.dp))
                    }
                }
            }
            Text(
                when {
                    human.isEmpty() -> ""
                    human.all { it } -> "Pass-and-play: a cover screen hides each hand while the phone changes hands."
                    human.none { it } -> "Bots only: watch them play each other."
                    else -> "Bots play the others. Number 1 goes first; ▲ moves a faction up."
                },
                color = Reef.muted, fontSize = 12.sp,
            )
            if (chosen.size >= 2) {
                val ok = needed == null || reach >= needed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reach $reach", color = if (ok) Color(0xFF8BE07A) else Reef.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("  needs ${needed ?: "-"} for ${chosen.size} factions", color = Reef.muted, fontSize = 12.sp)
                }
            }
            if (problem != null) Text(problem, color = Reef.accent, fontSize = 12.sp)
            Button(
                onClick = { onStart(chosen.indices.map { Seat(chosen[it], human[it]) }) },
                enabled = problem == null,
                colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start") }
        }
    }
    info?.let { f -> RulesDialog(listOf(f)) { info = null } }
}

@Composable
private fun FactionTile(f: FactionId, seat: Int, modifier: Modifier, onInfo: () -> Unit, onClick: () -> Unit) {
    val plate = Plates.of(f)
    val selected = seat >= 0
    val shape = RoundedCornerShape(12.dp)
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .background(if (selected) Reef.faction(f).copy(alpha = 0.22f) else Reef.surface, shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) Reef.faction(f) else Reef.line, shape)
                .clickable(onClick = onClick)
                .padding(vertical = 5.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Portrait(f, 44.dp)
            Text(plate.name, color = Reef.ink, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
            Text(plate.role, color = Reef.muted, fontSize = 9.sp, maxLines = 1, lineHeight = 10.sp)
            Text("●".repeat(plate.complexity) + "○".repeat(4 - plate.complexity), color = Reef.faction(f), fontSize = 8.sp, lineHeight = 9.sp)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).offset(x = 2.dp, y = 2.dp).size(18.dp).background(Reef.faction(f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${seat + 1}", color = Reef.night, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(18.dp).background(Reef.raised, CircleShape).border(1.dp, Reef.muted, CircleShape).clickable(onClick = onInfo),
            contentAlignment = Alignment.Center,
        ) { Text("?", color = Reef.ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp) }
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(26.dp)
            .background(if (on) Reef.current else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (on) Reef.current else Reef.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) Reef.night else Reef.muted, fontSize = 11.sp) }
}
