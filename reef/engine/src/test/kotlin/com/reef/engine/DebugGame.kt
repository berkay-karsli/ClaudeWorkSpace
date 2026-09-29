package com.reef.engine

import kotlin.random.Random

/** Replays one bot game and prints its log: `debugGame -Pseed=N -Plineup=sharks,coral`. Not a test. */
object DebugGame {
    @JvmStatic
    fun main(args: Array<String>) {
        val seed = args.getOrNull(0)?.toLong() ?: 2000L
        val lineup = args.getOrNull(1)?.split(",")?.map { k -> FactionId.entries.first { it.key == k.trim() } }
            ?: SimulationTest.lineups(1, 7).first()
        val g = Game.newGame(lineup.map { Seat(it, false) }, seed)
        val bot = Bot(Random(seed), samples = 2)
        while (g.phase != Phase.OVER && g.round <= 45) Game.apply(g, bot.choose(g, Game.decision(g)!!))
        println(g.log.joinToString("\n"))
    }
}
