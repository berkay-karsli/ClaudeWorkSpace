package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.app.ui.art.Art
import com.reef.app.ui.art.Icons
import com.reef.app.ui.art.Portraits
import com.reef.engine.BattleReport
import com.reef.engine.Board
import com.reef.engine.Bot
import com.reef.engine.Cards
import com.reef.engine.Decision
import com.reef.engine.EndDay
import com.reef.engine.FactionId
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.Option
import com.reef.engine.OptionPicker
import com.reef.engine.OptionPicker.Pick
import com.reef.engine.OptionPicker.Step
import com.reef.engine.Phase
import com.reef.engine.Suit
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

    /** The last battle the players have closed in the battle popup. */
    var seenBattle by mutableIntStateOf(game.lastBattle?.seq ?: -1)

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
    var board by remember { mutableStateOf<FactionId?>(null) }
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
    val pick = if (myDecision) OptionPicker.withKind(d!!.options, controller.pick) else Pick()
    val step = if (myDecision) OptionPicker.next(d!!.options, controller.pick) else null
    val matching = if (myDecision) OptionPicker.matching(d!!.options, pick) else emptyList()
    val marks = if (step != null) marksFor(pick, step) else emptyList()

    Box(Modifier.fillMaxSize().background(Reef.night)) {
        Row(Modifier.fillMaxSize()) {
            ReefMap(
                g = g,
                version = version,
                highlights = (step as? Step.ChooseReef)?.reefs ?: emptySet(),
                chosen = pick.reefs,
                marks = marks,
                onReefTap = { reef -> controller.pick = pick.copy(reefs = pick.reefs + reef) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // A landscape phone is short: the scoreboard stays on top, and everything below scrolls,
            // with the current choice first so it is always in view.
            Column(
                Modifier.width(340.dp).fillMaxHeight().background(Reef.surface).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Scoreboard(g, version, Modifier.weight(1f)) { board = it }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SmallButton("Rules") { showRules = true }
                        SmallButton("Log") { showLog = true }
                        SmallButton("Menu") { onExit() }
                    }
                }
                Line()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        d == null -> Text(g.winText, color = Reef.ink, fontWeight = FontWeight.SemiBold)
                        !humanTurn -> Waiting(g.players[d.player].faction, "is thinking…")
                        needsHandoff -> Waiting(g.players[d.player].faction, "plays next")
                        else -> {
                            Prompt(g, d)
                            Supply(g, d.player, version)
                            FactionStatus(g, d.player, version)
                            StepControls(controller, d, step!!, pick, matching)
                        }
                    }
                    Hand(controller, (step as? Step.ChooseCard)?.cards ?: emptySet(), d, version)
                    LastEvents(g, version)
                }
            }
        }
        if (needsHandoff) Handoff(g.players[d!!.player].faction) { controller.viewer = d.player }
        val battle = g.lastBattle
        if (battle != null && battle.seq != controller.seenBattle && !needsHandoff && g.phase != Phase.OVER) {
            BattlePopup(battle) { controller.seenBattle = battle.seq }
        }
        if (g.phase == Phase.OVER) GameOver(g, onExit, onRematch)
        if (showLog) LogDialog(g, version) { showLog = false }
        if (showRules) RulesDialog(g.players.map { it.faction }) { showRules = false }
        board?.let { f -> RulesDialog(listOf(f) + g.players.map { it.faction }.filter { it != f }) { board = null } }
    }
}

/** Arrows and rings on the map that show what the choice so far would do. */
private fun marksFor(pick: Pick, step: Step): List<MapMark> {
    val kind = pick.kind ?: return emptyList()
    val icon = Art.action(kind)
    return when (step) {
        is Step.Confirm -> {
            val o = step.option
            val ic = if (o.target != null && o.random) Icons.battle else icon
            when {
                o.reefs.size >= 2 -> listOf(MapMark(o.reefs[0], o.reefs[1], ic, strong = true, label = o.count?.toString()))
                o.reefs.size == 1 -> listOf(MapMark(null, o.reefs[0], ic, strong = true, label = o.count?.toString()))
                else -> emptyList()
            }
        }
        is Step.ChooseReef -> if (step.index == 1) {
            step.reefs.map { MapMark(pick.reefs[0], it, if (step.reefs.size <= 4) icon else null, strong = false) }
        } else {
            step.reefs.map { MapMark(null, it, icon, strong = false) }
        }
        else -> when {
            pick.reefs.size >= 2 -> listOf(MapMark(pick.reefs[0], pick.reefs[1], icon, strong = true))
            pick.reefs.size == 1 -> listOf(MapMark(null, pick.reefs[0], icon, strong = true))
            else -> emptyList()
        }
    }
}

@Composable
private fun Waiting(f: FactionId, what: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Portrait(f, 34.dp)
        Spacer(Modifier.width(8.dp))
        Text("${f.display} $what", color = Reef.muted, fontSize = 14.sp)
    }
}

@Composable
private fun Prompt(g: GameState, d: Decision) {
    val f = g.players[d.player].faction
    val phaseIcon = when {
        g.pending.isNotEmpty() -> Icons.battle
        g.phase == Phase.DAWN || g.phase == Phase.SETUP -> Icons.dawn
        g.phase == Phase.DAY -> Icons.day
        else -> Icons.endday
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Portrait(f, 34.dp)
        Spacer(Modifier.width(6.dp))
        ArtImage(phaseIcon, 26.dp)
        Spacer(Modifier.width(6.dp))
        Text(d.prompt.substringAfter(", "), color = Reef.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp)
    }
}

private fun reefHint(kind: String?, index: Int, twoReefs: Boolean): String = when {
    kind == "Set up" -> "Tap a glowing reef to set up there."
    kind == "Current" -> if (index == 0) "Tap the reef the current flows from." else "Tap the reef it flows to."
    kind == "Sandbar" || kind == "Dig out" -> if (index == 0) "Tap one end of the channel." else "Tap the other end."
    kind == "Move lure" -> if (index == 0) "Tap the lure to move." else "Tap where to hang it (or the same reef to change its offer)."
    !twoReefs -> "Tap a glowing reef."
    index == 0 -> "Tap the reef to go from."
    else -> "Tap where they go. The arrows show every way."
}

@Composable
private fun StepControls(controller: GameController, d: Decision, step: Step, pick: Pick, matching: List<Option>) {
    val f = controller.game.players[d.player].faction
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (step is Step.ChooseKind) {
            // Every action as a card, with the ones that end a step last.
            val kinds = step.kinds.sortedBy { if (it == EndDay.kind || it == "Done") 1 else 0 }
            for (row in kinds.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (k in row) {
                        ActionCard(f, k, d.options.count { it.kind == k }, selected = false, modifier = Modifier.weight(1f), compact = true) { controller.pick = Pick(kind = k) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            return@Column
        }
        ActionCard(f, pick.kind ?: "", matching.size, selected = true, modifier = Modifier.fillMaxWidth())
        when (step) {
            is Step.ChooseReef -> {
                Instruction(reefHint(pick.kind, step.index, matching.any { it.reefs.size > 1 }))
                ChipRow {
                    for (r in step.reefs.sortedBy { Board.name(it) }) {
                        Chip(Board.name(r), image = Art.suit(controller.game.suitOf(r))) { controller.pick = pick.copy(reefs = pick.reefs + r) }
                    }
                }
            }
            is Step.ChooseCard -> Instruction("Choose a card from your hand below.")
            is Step.ChooseTarget -> {
                Instruction("Which faction?")
                ChipRow {
                    for (t in step.targets) {
                        if (t == null) Chip("No battle", image = Icons.move) { controller.pick = pick.copy(target = null, targetChosen = true) }
                        else Chip(t.display, primary = true, faction = t) { controller.pick = pick.copy(target = t, targetChosen = true) }
                    }
                }
            }
            is Step.ChooseVariant -> {
                Instruction("Which one?")
                ChipRow { for (v in step.variants) Chip(v, image = variantImage(v)) { controller.pick = pick.copy(variant = v) } }
            }
            is Step.ChooseCount -> {
                Instruction("How many?")
                ChipRow { for (n in step.counts) Chip("$n", primary = n == step.counts.first()) { controller.pick = pick.copy(count = n) } }
            }
            is Step.Confirm -> Button(
                onClick = { controller.apply(step.option) },
                colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArtImage(Art.action(step.option.kind), 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(step.option.describe(), textAlign = TextAlign.Center)
            }
            is Step.ChooseKind -> {}
        }
        if (OptionPicker.kinds(d.options).size > 1) {
            TextButton(onClick = { controller.pick = Pick() }) { Text("‹ Choose another action", color = Reef.muted) }
        } else if (controller.pick != Pick()) {
            TextButton(onClick = { controller.pick = Pick() }) { Text("‹ Start over", color = Reef.muted) }
        }
    }
}

private fun variantImage(v: String) = when {
    v in listOf("Treasure", "Shelter", "Glory") -> Art.lure(v)
    Suit.entries.any { it.label == v } -> Art.suit(Suit.entries.first { it.label == v })
    v.startsWith("Arm") -> Icons.reach
    v.startsWith("Turtle") -> Portraits.turtle
    else -> null
}

@Composable
private fun Instruction(text: String) {
    Text(text, color = Reef.ink, fontSize = 13.sp, fontStyle = FontStyle.Italic)
}

/** Every faction's portrait and VP in one row. Tap one to open its board. */
@Composable
// The game state changes in place, so everything drawn from it also takes [version]: Compose then
// redraws it after every move instead of skipping it because the state object is the same one.
private fun Scoreboard(g: GameState, @Suppress("UNUSED_PARAMETER") version: Int, modifier: Modifier = Modifier, onOpen: (FactionId) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        g.players.forEachIndexed { i, pl ->
            val turn = g.phase != Phase.OVER && g.current == i
            Box(Modifier.clickable { onOpen(pl.faction) }.padding(bottom = 4.dp, end = 6.dp)) {
                Portrait(pl.faction, 42.dp, ring = if (turn) 3.dp else 1.5.dp)
                VpCoin(pl.vp, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
                if (!pl.human) {
                    Text(
                        "bot", color = Reef.night, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).background(Reef.muted, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

/** What the faction on turn has left, one line under the prompt. */
@Composable
private fun Supply(g: GameState, p: Int, @Suppress("UNUSED_PARAMETER") version: Int) {
    val pl = g.players[p]
    val extra = buildList {
        add(Game.rules(pl.faction).supplySummary(g, p))
        pl.dominance?.let { add("Dominance: ${Cards[it].suit.label}") }
        if (pl.gear.isNotEmpty()) add("Gear: " + pl.gear.joinToString { Cards[it].name })
    }
    Text(extra.joinToString(" · "), color = Reef.muted, fontSize = 11.sp)
}

@Composable
private fun LastEvents(g: GameState, @Suppress("UNUSED_PARAMETER") version: Int) {
    val lines = g.log.takeLast(6).reversed()
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionTitle("Latest")
        for (l in lines) {
            val f = FactionId.entries.firstOrNull { l.startsWith(it.display + ":") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f != null) {
                    Portrait(f, 18.dp, ring = 1.dp)
                    Spacer(Modifier.width(5.dp))
                }
                Text(if (f != null) l.substringAfter(": ") else l, color = Reef.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Hand(controller: GameController, selectable: Set<Int>, d: Decision?, @Suppress("UNUSED_PARAMETER") version: Int) {
    val g = controller.game
    val viewer = controller.viewer
    if (g.players.none { it.human }) return
    val hand = g.players[viewer].hand
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("${g.players[viewer].faction.display} hand · ${hand.size}")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in hand) {
                val canPick = c in selectable
                PlayingCard(
                    c, highlighted = canPick,
                    onClick = if (canPick && d != null) {
                        { controller.pick = OptionPicker.withKind(d.options, controller.pick).copy(card = c) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun Handoff(f: FactionId, onReady: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Reef.night).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Portrait(f, 110.dp, ring = 3.dp)
            Text("Pass the phone to ${f.display}", color = Reef.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Everyone else, look away: the next screen shows ${Game.possessive(f.display)} hand.", color = Reef.muted)
            Button(onClick = onReady, colors = ButtonDefaults.buttonColors(containerColor = Reef.faction(f), contentColor = Reef.night)) {
                Text("I'm ${f.display}. Show my turn")
            }
        }
    }
}

/** How the latest battle went: who fought, the dice, and the hits each side dealt. */
@Composable
internal fun BattlePopup(b: BattleReport, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x9906171D)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(440.dp).background(Reef.surface, RoundedCornerShape(16.dp)).border(1.dp, Reef.line, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Battle in ${Board.name(b.reef)}", color = Reef.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Side(b.attacker, "attack", b.attackerHits)
                ArtImage(Icons.battle, 54.dp)
                Side(b.defender, "defend", b.defenderHits)
            }
            if (b.dice.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (v in b.dice) Die(v)
                    Text("The attacker deals the higher die, the defender the lower, each capped by warriors there.", color = Reef.muted, fontSize = 11.sp, modifier = Modifier.width(230.dp))
                }
            }
            for (n in b.notes) Text(n, color = Reef.muted, fontSize = 12.sp)
            Text("Tap to close", color = Reef.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Side(f: FactionId, verb: String, hits: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Portrait(f, 62.dp, ring = 2.5.dp)
        Text("${f.display} $verb", color = Reef.ink, fontSize = 12.sp)
        Text("$hits hit${if (hits == 1) "" else "s"}", color = Reef.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** A four-sided die showing 0 to 3. */
@Composable
private fun Die(v: Int) {
    Box(
        Modifier.size(40.dp).background(Color(0xFFF6F0E2), RoundedCornerShape(8.dp)).border(2.dp, Color(0xFF10222B), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text("$v", color = Color(0xFF10222B), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GameOver(g: GameState, onExit: () -> Unit, onRematch: () -> Unit) {
    val winner = g.winner?.let { g.players[it] }
    Box(Modifier.fillMaxSize().background(Color(0xE606171D)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(Reef.surface, RoundedCornerShape(16.dp)).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (winner != null) Portrait(winner.faction, 90.dp, ring = 3.dp)
            if (winner != null) SectionTitle("Victory")
            Text(winner?.faction?.display ?: "Game over", color = Reef.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(g.winText, color = Reef.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (pl in g.players.sortedByDescending { it.vp }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Portrait(pl.faction, 36.dp)
                        Text("${pl.vp} VP", color = Reef.ink, fontSize = 13.sp)
                    }
                }
            }
            Text("${g.round} rounds", color = Reef.muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onExit) { Text("Home") }
                Button(onClick = onRematch, colors = ButtonDefaults.buttonColors(containerColor = Reef.current, contentColor = Reef.night)) { Text("Play again") }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.width(52.dp).height(19.dp).border(1.dp, Reef.line, RoundedCornerShape(6.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Reef.muted, fontSize = 11.sp, lineHeight = 12.sp) }
}
