package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.engine.Board
import com.reef.engine.Bot
import com.reef.engine.Cards
import com.reef.engine.Decision
import com.reef.engine.FactionId
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.Option
import com.reef.engine.OptionPicker
import com.reef.engine.OptionPicker.Pick
import com.reef.engine.OptionPicker.Step
import com.reef.engine.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Where the game in progress is kept between sessions. */
interface SaveStore {
    fun load(): String?
    fun save(json: String)
    fun clear()
}

/** Holds the running game. The state object is changed in place; [version] tells Compose to redraw. */
class GameController(val game: GameState, private val store: SaveStore) {
    var version by mutableIntStateOf(0)
        private set
    var pick by mutableStateOf(Pick())

    /** The person whose hand is on screen. In pass-and-play it changes only after the cover screen. */
    var viewer by mutableIntStateOf(game.players.indexOfFirst { it.human }.coerceAtLeast(0))

    val humans: Int get() = game.players.count { it.human }

    fun decision(): Decision? = Game.decision(game)

    fun apply(o: Option) {
        Game.apply(game, o)
        pick = Pick()
        version++
        store.save(game.toJson())
    }
}

@Composable
fun GameScreen(controller: GameController, onExit: () -> Unit, onRematch: () -> Unit) {
    val version = controller.version
    val g = controller.game
    val d = remember(version) { controller.decision() }
    val bot = remember { Bot() }
    var showLog by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    // Bots take their turn after a short pause so people can follow what happened.
    LaunchedEffect(version) {
        val decision = d ?: return@LaunchedEffect
        if (g.players[decision.player].human) return@LaunchedEffect
        delay(if (decision.options.size == 1) 250 else 650)
        val snapshot = g.deepCopy()
        val choice = withContext(Dispatchers.Default) { bot.choose(snapshot, Game.decision(snapshot)!!) }
        controller.apply(choice)
    }

    val humanTurn = d != null && g.players[d.player].human
    val needsHandoff = humanTurn && controller.humans > 1 && d!!.player != controller.viewer
    val myDecision = humanTurn && !needsHandoff
    val step = if (myDecision) OptionPicker.next(d!!.options, controller.pick) else null
    val matching = if (myDecision) OptionPicker.matching(d!!.options, OptionPicker.withKind(d.options, controller.pick)) else emptyList()

    Box(Modifier.fillMaxSize().background(Reef.night)) {
        Row(Modifier.fillMaxSize()) {
            ReefMap(
                g = g,
                version = version,
                highlights = (step as? Step.ChooseReef)?.reefs ?: emptySet(),
                chosen = controller.pick.reefs,
                onReefTap = { reef -> controller.pick = OptionPicker.withKind(d!!.options, controller.pick).let { it.copy(reefs = it.reefs + reef) } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Column(
                Modifier.width(330.dp).fillMaxHeight().background(Reef.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Scoreboard(g)
                Line()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        d == null -> Text(g.winText, color = Reef.ink, fontWeight = FontWeight.SemiBold)
                        !humanTurn -> Text("${g.players[d.player].faction.display} (bot) are thinking…", color = Reef.muted)
                        needsHandoff -> Text("Waiting for ${g.players[d.player].faction.display}.", color = Reef.muted)
                        else -> {
                            Text(d.prompt, color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            StepControls(controller, d, step!!, matching)
                        }
                    }
                    LastEvents(g)
                }
                Hand(controller, (step as? Step.ChooseCard)?.cards ?: emptySet(), d)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("Rules") { showRules = true }
                    SmallButton("Log") { showLog = true }
                    SmallButton("Menu") { onExit() }
                }
            }
        }
        if (needsHandoff) Handoff(g.players[d!!.player].faction) { controller.viewer = d.player }
        if (g.phase == Phase.OVER) GameOver(g, onExit, onRematch)
        if (showLog) LogDialog(g) { showLog = false }
        if (showRules) RulesDialog(g.players.map { it.faction }) { showRules = false }
    }
}

@Composable
private fun Scoreboard(g: GameState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        g.players.forEachIndexed { i, pl ->
            val turn = g.phase != Phase.OVER && g.current == i
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Reef.faction(pl.faction), CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pl.faction.display + (if (pl.human) "" else " (bot)") + (if (turn) "  ◀ turn" else ""),
                        color = Reef.ink, fontWeight = if (turn) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp,
                    )
                    val extra = buildList {
                        add(Game.rules(pl.faction).supplySummary(g, i))
                        pl.dominance?.let { add("Dominance: ${Cards[it].suit.label}") }
                        if (pl.gear.isNotEmpty()) add("Gear: " + pl.gear.joinToString { Cards[it].name })
                    }
                    Text(extra.joinToString(" · "), color = Reef.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("${pl.vp}", color = Reef.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(" VP", color = Reef.muted, fontSize = 11.sp)
            }
        }
    }
}

private val reefHints = mapOf(
    "Set up" to listOf("Tap where to set up."),
    "Arrive" to listOf("Tap a gate for the new shark."),
    "Hunt" to listOf("Tap the reef the sharks leave from.", "Tap where they go."),
    "Move" to listOf("Tap the reef to move from.", "Tap where they go."),
    "Battle" to listOf("Tap the reef to battle in."),
    "Grow" to listOf("Tap a reef to grow coral in."),
    "Spawn" to listOf("Tap the reef the polyps drift into."),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepControls(controller: GameController, d: Decision, step: Step, matching: List<Option>) {
    val pick = OptionPicker.withKind(d.options, controller.pick)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (step) {
            is Step.ChooseKind -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (k in step.kinds) Chip(k, primary = true) { controller.pick = Pick(kind = k) }
            }
            is Step.ChooseReef -> {
                Text(reefHints[pick.kind]?.getOrNull(step.index) ?: "Tap a highlighted reef.", color = Reef.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in step.reefs.sortedBy { Board.name(it) }) Chip(Board.name(r)) { controller.pick = pick.copy(reefs = pick.reefs + r) }
                }
            }
            is Step.ChooseCard -> Text("Choose a card from your hand below.", color = Reef.muted, fontSize = 13.sp)
            is Step.ChooseTarget -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in step.targets) {
                    Chip(if (t == null) "No battle" else "Battle ${t.display}", primary = t != null) {
                        controller.pick = pick.copy(target = t, targetChosen = true)
                    }
                }
            }
            is Step.ChooseCount -> {
                Text("How many?", color = Reef.muted, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in step.counts) Chip("$n") { controller.pick = pick.copy(count = n) }
                }
            }
            is Step.Confirm -> Button(
                onClick = { controller.apply(step.option) },
                colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(step.option.describe(), textAlign = TextAlign.Center) }
        }
        if (controller.pick != Pick() && !(step is Step.ChooseKind)) {
            TextButton(onClick = { controller.pick = Pick() }) { Text("Back", color = Reef.muted) }
        } else if (matching.size == 1 && step !is Step.Confirm && step !is Step.ChooseKind) {
            Text(matching.first().describe(), color = Reef.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LastEvents(g: GameState) {
    val lines = g.log.takeLast(6).reversed()
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("LATEST", color = Reef.muted, fontSize = 10.sp, letterSpacing = 1.sp)
        for (l in lines) Text(l, color = Reef.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Hand(controller: GameController, selectable: Set<Int>, d: Decision?) {
    val g = controller.game
    val viewer = controller.viewer
    if (g.players.none { it.human }) return
    val hand = g.players[viewer].hand
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${g.players[viewer].faction.display.uppercase()} HAND · ${hand.size}", color = Reef.muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in hand) {
                val card = Cards[c]
                val canPick = c in selectable
                Column(
                    Modifier
                        .width(118.dp)
                        .background(Reef.raised, RoundedCornerShape(8.dp))
                        .border(if (canPick) 2.dp else 1.dp, if (canPick) Reef.accent else Reef.line, RoundedCornerShape(8.dp))
                        .clickable(enabled = canPick && d != null) { controller.pick = OptionPicker.withKind(d!!.options, controller.pick).copy(card = c) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Reef.suit(card.suit), RoundedCornerShape(2.dp)))
                    Text(card.name, color = Reef.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.describe(), color = Reef.muted, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Handoff(f: FactionId, onReady: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Reef.night).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(28.dp).background(Reef.faction(f), CircleShape))
            Text("Pass the phone to ${f.display}", color = Reef.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Everyone else, look away: the next screen shows ${Game.possessive(f.display)} hand.", color = Reef.muted)
            Button(onClick = onReady, colors = ButtonDefaults.buttonColors(containerColor = Reef.faction(f), contentColor = Reef.night)) {
                Text("I'm ${f.display}. Show my turn")
            }
        }
    }
}

@Composable
private fun GameOver(g: GameState, onExit: () -> Unit, onRematch: () -> Unit) {
    val winner = g.winner?.let { g.players[it] }
    Box(Modifier.fillMaxSize().background(Color(0xE606171D)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Reef.surface, RoundedCornerShape(16.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (winner != null) Box(Modifier.size(28.dp).background(Reef.faction(winner.faction), CircleShape))
            Text(if (winner != null) "${winner.faction.display} win" else "Game over", color = Reef.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(g.winText, color = Reef.muted)
            Text(g.players.joinToString("   ") { "${it.faction.display} ${it.vp} VP" } + "   ·   ${g.round} rounds", color = Reef.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onExit) { Text("Home") }
                Button(onClick = onRematch, colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night)) { Text("Play again") }
            }
        }
    }
}

@Composable
fun Chip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (primary) Reef.raised else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (primary) Reef.current else Reef.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = Reef.ink, fontSize = 14.sp) }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.border(1.dp, Reef.line, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = Reef.muted, fontSize = 13.sp) }
}

@Composable
fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Reef.line))
}
