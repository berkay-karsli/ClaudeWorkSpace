package com.reef.engine

import com.reef.engine.OptionPicker.Pick
import com.reef.engine.OptionPicker.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class OptionPickerTest {

    @Test
    fun huntIsPickedAsFromThenToThenTargetThenCount() {
        val options = listOf(
            Hunt(0, 4, 3, false, null), Hunt(0, 4, 3, false, FactionId.CORAL),
            Hunt(0, 4, 2, false, null), Hunt(0, 4, 2, false, FactionId.CORAL),
            Hunt(0, 1, 3, false, null), Hunt(0, 1, 2, false, null),
            EndDay,
        )
        assertEquals(Step.ChooseKind(listOf("Hunt", "End Day")), OptionPicker.next(options, Pick()))
        var pick = Pick(kind = "Hunt")
        assertEquals(Step.ChooseReef(setOf(0), 0), OptionPicker.next(options, pick))
        pick = pick.copy(reefs = listOf(0))
        assertEquals(Step.ChooseReef(setOf(4, 1), 1), OptionPicker.next(options, pick))
        pick = pick.copy(reefs = listOf(0, 4))
        assertEquals(Step.ChooseTarget(listOf(null, FactionId.CORAL)), OptionPicker.next(options, pick))
        pick = pick.copy(target = FactionId.CORAL, targetChosen = true)
        assertEquals(Step.ChooseCount(listOf(3, 2)), OptionPicker.next(options, pick))
        pick = pick.copy(count = 3)
        assertEquals(Step.Confirm(Hunt(0, 4, 3, false, FactionId.CORAL)), OptionPicker.next(options, pick))
    }

    @Test
    fun aSingleKindIsChosenAutomatically() {
        val options = Board.gates.map { Arrive(it) }
        assertEquals(Step.ChooseReef(Board.gates.toSet(), 0), OptionPicker.next(options, Pick()))
        assertEquals(Step.Confirm(Arrive(3)), OptionPicker.next(options, Pick(reefs = listOf(3))))
    }

    @Test
    fun everyOptionOfRealGamesCanBeReachedByTapping() {
        // Walk every decision of a few bot games: each option must be reachable through the steps.
        repeat(3) { seed ->
            val g = Game.newGame(listOf(Seat(FactionId.SHARKS, false), Seat(FactionId.CORAL, false)), seed.toLong())
            val bot = Bot(Random(seed), samples = 1)
            var decisions = 0
            while (g.phase != Phase.OVER && decisions < 150) {
                val d = Game.decision(g)!!
                for (o in d.options) assertEquals(o, reach(d.options, o))
                Game.apply(g, bot.choose(g, d))
                decisions++
            }
            assertTrue(decisions > 20)
        }
    }

    /** Follows the steps the way a person would to get to [target]. */
    private fun reach(options: List<Option>, target: Option): Option {
        var pick = Pick()
        repeat(12) {
            when (val s = OptionPicker.next(options, pick)) {
                is Step.ChooseKind -> pick = pick.copy(kind = target.kind)
                is Step.ChooseReef -> pick = pick.copy(reefs = pick.reefs + target.reefs[s.index])
                is Step.ChooseCard -> pick = pick.copy(card = target.card)
                is Step.ChooseTarget -> pick = pick.copy(target = target.target, targetChosen = true)
                is Step.ChooseVariant -> pick = pick.copy(variant = target.variant)
                is Step.ChooseCount -> pick = pick.copy(count = target.count)
                is Step.Confirm -> return s.option
            }
        }
        error("Could not reach ${target.describe()}")
    }
}
