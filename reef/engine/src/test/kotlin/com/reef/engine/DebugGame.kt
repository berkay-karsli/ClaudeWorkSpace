package com.reef.engine

import kotlin.random.Random

/** Replays one bot game and prints its log; run with -Dreef.debugSeed=N. Not a test. */
object DebugGame {
    @JvmStatic
    fun main(args: Array<String>) {
        val seed = args.firstOrNull()?.toLong() ?: 1007L
        val seats = listOf(Seat(FactionId.SHARKS, false), Seat(FactionId.CORAL, false)).let { if (seed % 2 == 0L) it else it.reversed() }
        val g = Game.newGame(seats, seed)
        val bot = Bot(Random(seed), samples = 2)
        while (g.phase != Phase.OVER && g.round <= 25) Game.apply(g, bot.choose(g, Game.decision(g)!!))
        println(g.log.joinToString("\n"))
    }
}
