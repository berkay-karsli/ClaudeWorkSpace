package com.woodland.engine

import kotlin.random.Random

/** Picks the option with the best heuristic score, with a little noise so games vary. */
object Ai {
    private const val SIMULATIONS = 2

    fun pick(game: Game, rng: Random): Int {
        val d = game.pending ?: error("No decision")
        if (d.tag == "decree") return lookahead(game, d, rng)
        return pick(d, rng)
    }

    fun pick(d: Decision, rng: Random): Int =
        d.options.indices.maxBy { d.options[it].ai + rng.nextDouble() * 1.5 }

    /**
     * Plays the rest of the Eyrie's turn for each option (with fresh dice) and prefers the ones
     * that carry out the whole Decree and grow the roosts.
     */
    private fun lookahead(game: Game, d: Decision, rng: Random): Int {
        val base = game.answers.toList()
        return d.options.indices.maxBy { i ->
            var total = 0.0
            repeat(SIMULATIONS) {
                val sim = Game.replay(game.config, game.seed, base + i, reseed = rng.nextLong())
                total += playOutTurn(sim, d.faction, rng)
            }
            total / SIMULATIONS + 0.3 * d.options[i].ai + rng.nextDouble()
        }
    }

    private fun playOutTurn(sim: Game, f: Faction, rng: Random): Double {
        val turn = sim.round
        val logStart = sim.log.size
        while (!sim.finished && sim.current == f && sim.round == turn && sim.phase != "Evening") {
            sim.answer(pick(sim.pending!!, rng))
        }
        if (sim.finished) return if (sim.winner == f) 100.0 else -100.0
        val turmoil = sim.log.subList(logStart, sim.log.size).any { it.contains("TURMOIL") }
        val p = sim.player(f)
        return (if (turmoil) -12.0 else 0.0) + 3.0 * sim.count(BuildingType.ROOST) + p.vp +
            0.3 * sim.warriorsOnMap(f)
    }
}
