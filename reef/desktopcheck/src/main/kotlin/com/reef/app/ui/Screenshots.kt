package com.reef.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.reef.engine.Bot
import com.reef.engine.FactionId
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
    val scene = ImageComposeScene(width = (800 * d).toInt(), height = (370 * d).toInt(), density = Density(d)) { ReefTheme { content() } }
    scene.render(0)
    val image = scene.render(500_000_000L)
    File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("wrote $name.png")
}

/**
 * Plays bot games (trying a few seeds) until [stop] holds, then hands every seat to a person so
 * the panel shows their controls. Null if no game reached that situation.
 */
private fun playUntil(seed: Long, stop: (GameState) -> Boolean): GameState? {
    for (s in seed until seed + 10) {
        val g = Game.newGame(listOf(Seat(FactionId.SHARKS, false), Seat(FactionId.CORAL, false)), s)
        val bot = Bot(Random(s), samples = 1)
        while (g.phase != Phase.OVER && !stop(g)) Game.apply(g, bot.choose(g, Game.decision(g)!!))
        if (stop(g)) return GameState.fromJson(g.toJson().replace("\"human\":false", "\"human\":true"))
    }
    println("no game reached the situation for seed $seed")
    return null
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screens").apply { mkdirs() }

    shot(out, "1-home") { HomeScreen(saved = playUntil(3) { it.round >= 3 }, onContinue = {}, onNew = {}, onHowToPlay = {}) }
    shot(out, "2-setup") { SetupScreen(onStart = {}, onBack = {}) }

    val setup = Game.newGame(listOf(Seat(FactionId.SHARKS, true), Seat(FactionId.CORAL, false)), 42)
    shot(out, "3-sharks-set-up") { GameScreen(GameController(setup, MemoryStore), {}, {}) }

    val sharksDay = playUntil(7) { it.round >= 4 && it.phase == Phase.DAY && it.current == it.player(FactionId.SHARKS) && it.battle == null }
    if (sharksDay != null) {
        shot(out, "4-sharks-day") { GameScreen(GameController(sharksDay, MemoryStore), {}, {}) }

        val hunting = GameController(sharksDay.deepCopy(), MemoryStore)
        val hunt = Game.decision(hunting.game)!!.options.first { it.kind == "Hunt" }
        hunting.pick = OptionPicker.Pick(kind = "Hunt", reefs = listOf(hunt.reefs[0]))
        shot(out, "5-sharks-hunt-destination") { GameScreen(hunting, {}, {}) }

        val handoff = GameController(sharksDay.deepCopy(), MemoryStore)
        handoff.viewer = handoff.game.player(FactionId.CORAL)
        shot(out, "7-pass-the-phone") { GameScreen(handoff, {}, {}) }
    }

    val coralDay = playUntil(11) {
        it.round >= 3 && it.phase == Phase.DAY && it.battle == null && it.current == it.player(FactionId.CORAL) &&
            Game.decision(it)!!.options.any { o -> o.kind == "Grow" }
    }
    if (coralDay != null) {
        val growing = GameController(coralDay, MemoryStore)
        growing.viewer = coralDay.player(FactionId.CORAL)
        val grow = Game.decision(coralDay)!!.options.first { it.kind == "Grow" }
        growing.pick = OptionPicker.Pick(kind = "Grow", reefs = grow.reefs)
        shot(out, "6-coral-grow-card") { GameScreen(growing, {}, {}) }
    }

    val over = playUntil(5) { it.phase == Phase.OVER }
    if (over != null) shot(out, "8-game-over") { GameScreen(GameController(over, MemoryStore), {}, {}) }

    shot(out, "9-rules") { RulesDialog(listOf(FactionId.SHARKS, FactionId.CORAL)) {} }
}
