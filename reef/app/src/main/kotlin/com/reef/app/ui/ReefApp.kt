package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Reef", color = Reef.ink, fontSize = 64.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light)
            Text("An asymmetric strategy game on a coral reef.", color = Reef.muted)
            Spacer(Modifier.height(8.dp))
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

/** Phase 1 has two factions. Each can be played by a person or a bot, and either can go first. */
@Composable
internal fun SetupScreen(onStart: (List<Seat>) -> Unit, onBack: () -> Unit) {
    val factions = remember { mutableStateListOf(FactionId.SHARKS, FactionId.CORAL) }
    val human = remember { mutableStateListOf(true, false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The buttons sit beside the title so a short landscape screen never hides them.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New game", color = Reef.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (human.all { it }) "Pass-and-play: a cover screen hides each hand while the phone changes hands."
                    else if (human.none { it }) "Bots only: watch the two factions play each other."
                    else "You against a bot. The faction on the left goes first.",
                    color = Reef.muted, fontSize = 13.sp,
                )
            }
            OutlinedButton(onClick = onBack) { Text("Back", color = Reef.ink) }
            OutlinedButton(onClick = {
                factions.reverse()
                human.reverse()
            }) { Text("Swap order", color = Reef.ink) }
            Button(
                onClick = { onStart(factions.indices.map { Seat(factions[it], human[it]) }) },
                colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night),
            ) { Text("Start") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            factions.forEachIndexed { i, f ->
                val plate = Plates.of(f)
                Column(
                    Modifier.weight(1f).background(Reef.surface, RoundedCornerShape(12.dp)).border(1.dp, Reef.line, RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(Reef.faction(f), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(plate.name, color = Reef.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(if (i == 0) "goes first" else "goes second", color = Reef.muted, fontSize = 12.sp)
                    }
                    Text("“${plate.tagline}”", color = Reef.ink, fontStyle = FontStyle.Italic)
                    Text(plate.idea, color = Reef.muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Person", primary = human[i]) { human[i] = true }
                        Chip("Bot", primary = !human[i]) { human[i] = false }
                    }
                }
            }
        }
    }
}
