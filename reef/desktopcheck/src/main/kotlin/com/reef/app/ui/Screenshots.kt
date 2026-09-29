package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.reef.engine.Bot
import com.reef.engine.Bleach
import com.reef.engine.FactionId
import com.reef.engine.FeedBlood
import com.reef.engine.InkCloud
import com.reef.engine.Option
import com.reef.engine.Push
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.OptionPicker
import com.reef.engine.Phase
import com.reef.engine.Seat
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.random.Random

/** Renders the app's screens to PNGs at a landscape phone size, without a device. */
private object MemoryStore : SaveStore {
    var text: String? = null
    override fun load() = text
    override fun save(json: String) { text = json }
    override fun clear() { text = null }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun shot(out: File, name: String, content: @Composable () -> Unit) {
    val d = 2f
    val scene = ImageComposeScene(width = (800 * d).toInt(), height = (370 * d).toInt(), density = Density(d)) {
        // The same dark ground ReefApp puts behind every screen.
        ReefTheme { Box(Modifier.fillMaxSize().background(Reef.night)) { content() } }
    }
    scene.render(0)
    val image = scene.render(500_000_000L)
    File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote $name.png")
}

/**
 * Renders [controller]'s game, runs [change] on the live screen, and renders again: the second
 * picture must show the new state (scores, hand, latest events), not the first one's.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun shotAfter(out: File, name: String, controller: GameController, change: () -> Unit) {
    val d = 2f
    val scene = ImageComposeScene(width = (800 * d).toInt(), height = (370 * d).toInt(), density = Density(d)) {
        ReefTheme { Box(Modifier.fillMaxSize().background(Reef.night)) { GameScreen(controller, {}, {}) } }
    }
    scene.render(0)
    change()
    scene.render(1_000_000_000L)
    val image = scene.render(2_000_000_000L)
    File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote $name.png")
}

/** Every seat played by a person, so the panel shows their controls. */
private fun asHumans(g: GameState) = GameState.fromJson(g.toJson().replace("\"human\":false", "\"human\":true"))

/** Plays bot games of [lineup] (trying a few seeds) until [stop] holds. Null if none got there. */
private fun playUntil(lineup: List<FactionId>, seed: Long, stop: (GameState) -> Boolean): GameState? {
    for (s in seed until seed + 12) {
        val g = Game.newGame(lineup.map { Seat(it, false) }, s)
        val bot = Bot(Random(s), samples = 1)
        while (g.phase != Phase.OVER && !stop(g) && g.round < 40) Game.apply(g, bot.choose(g, Game.decision(g)!!))
        if (stop(g)) return asHumans(g)
    }
    println("no ${lineup.joinToString { it.display }} game reached the situation for seed $seed")
    return null
}

/** A human-seat controller whose screen shows [f]'s hand. */
private fun controllerFor(g: GameState, f: FactionId, pick: OptionPicker.Pick = OptionPicker.Pick()) =
    GameController(g, MemoryStore).also {
        it.viewer = g.player(f)
        it.seenBattle = g.lastBattle?.seq ?: -1
        it.pick = pick
    }

/**
 * For [f] in a game of [lineup]: its Day with the action cards, then the first two-reef action
 * part-chosen (every destination arrow) and fully chosen (the preview of exactly what happens).
 */
private fun factionDay(out: File, n: Int, lineup: List<FactionId>, f: FactionId, seed: Long) {
    val g = playUntil(lineup, seed) {
        it.round >= 3 && it.phase == Phase.DAY && it.pending.isEmpty() && it.current == it.player(f) &&
            OptionPicker.kinds(Game.decision(it)!!.options).size >= 3
    } ?: return
    val key = f.key
    shot(out, "%02d-$key-day".format(n)) { GameScreen(controllerFor(g, f), {}, {}) }
    val options = Game.decision(g)!!.options
    val twoReef = options.firstOrNull { it.reefs.size >= 2 } ?: options.firstOrNull { it.reefs.isNotEmpty() } ?: return
    shot(out, "%02d-$key-choose".format(n)) {
        GameScreen(controllerFor(g.deepCopy(), f, OptionPicker.Pick(kind = twoReef.kind, reefs = twoReef.reefs.take(1))), {}, {})
    }
    shot(out, "%02d-$key-preview".format(n)) {
        GameScreen(
            controllerFor(
                g.deepCopy(), f,
                OptionPicker.Pick(kind = twoReef.kind, reefs = twoReef.reefs, card = twoReef.card, target = twoReef.target, targetChosen = true, variant = twoReef.variant, count = twoReef.count),
            ),
            {}, {},
        )
    }
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screens").apply { mkdirs() }

    val lineups = listOf(
        listOf(FactionId.SHARKS, FactionId.CORAL, FactionId.JELLYFISH, FactionId.PARROTFISH),
        listOf(FactionId.SNAKE, FactionId.OCTOPUS, FactionId.REMORAS, FactionId.CRABS),
        listOf(FactionId.TURTLES, FactionId.ANGLERS, FactionId.CUTTLEFISH, FactionId.LIONFISH),
        listOf(FactionId.SARDINES, FactionId.STARFISH, FactionId.SHARKS, FactionId.OCTOPUS),
    )

    val saved = playUntil(lineups[0], 3) { it.round >= 3 }
    shot(out, "00-home") { HomeScreen(saved = saved, onContinue = {}, onNew = {}, onHowToPlay = {}) }
    shot(out, "01-setup") { SetupScreen(onStart = {}, onBack = {}, initial = lineups[1]) }
    shot(out, "02-setup-low-reach") { SetupScreen(onStart = {}, onBack = {}, initial = listOf(FactionId.TURTLES, FactionId.CORAL)) }
    for ((i, f) in FactionId.entries.withIndex()) {
        shot(out, "03-board-%02d-${f.key}".format(i + 1)) { RulesDialog(listOf(f)) {} }
    }

    var n = 10
    val seen = mutableSetOf<FactionId>()
    for ((li, lineup) in lineups.withIndex()) {
        for (f in lineup) {
            if (!seen.add(f)) continue
            factionDay(out, n++, lineup, f, 20L + li * 7)
        }
    }

    // A battle, as the popup shows it.
    val battle = playUntil(lineups[0], 40) { it.lastBattle != null && it.lastBattle!!.dice.isNotEmpty() && it.pending.isEmpty() }
    if (battle != null) {
        val c = controllerFor(battle, battle.players[battle.current].faction)
        c.seenBattle = -1
        shot(out, "40-battle") { GameScreen(c, {}, {}) }
    }

    // Passing the phone between two people.
    val handoff = playUntil(lineups[2], 50) { it.round >= 2 && it.phase == Phase.DAY }
    if (handoff != null) {
        val c = controllerFor(handoff, handoff.players[(handoff.current + 1) % handoff.players.size].faction)
        shot(out, "41-pass-the-phone") { GameScreen(c, {}, {}) }
    }

    val over = playUntil(lineups[1], 60) { it.phase == Phase.OVER }
    if (over != null) shot(out, "42-game-over") { GameScreen(GameController(over, MemoryStore), {}, {}) }

    shot(out, "43-shared-rules") { RulesDialog(emptyList()) {} }

    // New choices in draft 4, each as the player sees it.
    fun offered(g: GameState, test: (Option) -> Boolean) = g.phase != Phase.OVER && Game.decision(g)!!.options.any(test)
    fun choiceShot(name: String, lineup: List<FactionId>, seed: Long, test: (Option) -> Boolean) {
        val g = playUntil(lineup, seed) { offered(it, test) } ?: return
        shot(out, name) { GameScreen(controllerFor(g, g.players[Game.decision(g)!!.player].faction), {}, {}) }
    }
    choiceShot("45-sharks-feed-or-frenzy", lineups[0], 80) { it is FeedBlood }
    choiceShot("46-octopus-ink", lineups[3], 81) { it is InkCloud }
    choiceShot("47-sardines-wall-of-fish", lineups[3], 82) { it is Push }
    choiceShot("48-coral-bleach", lineups[0], 83) { it is Bleach }

    // Scores change during play: moves made on a live screen must show up in the scoreboard.
    val live = playUntil(lineups[0], 70) { it.round >= 2 && it.phase == Phase.DAWN } ?: return
    val c = controllerFor(live, live.players[live.current].faction)
    val before = live.players.map { it.vp }
    shotAfter(out, "44-scores-update-live", c) {
        val bot = Bot(Random(70), samples = 1)
        var steps = 0
        while (live.players.map { it.vp } == before && live.phase != Phase.OVER && steps++ < 400) c.apply(bot.choose(live, Game.decision(live)!!))
        c.seenBattle = live.lastBattle?.seq ?: -1
        c.viewer = live.current
        println("scores before ${before}, after ${live.players.map { it.vp }}")
    }
}
