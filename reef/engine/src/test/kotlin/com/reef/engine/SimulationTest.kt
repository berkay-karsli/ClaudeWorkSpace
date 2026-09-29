package com.reef.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Bot-vs-bot games: every game must finish, and no piece or card may ever appear or vanish. */
class SimulationTest {

    private fun checkInvariants(g: GameState) {
        val cards = g.drawPile + g.discard + g.players.flatMap { it.hand + it.gear + listOfNotNull(it.dominance) }
        assertEquals("cards", Cards.all.indices.toList(), cards.sorted())
        assertEquals("blood", GameState.BLOOD_TOKENS, g.blood + g.reefs.sumOf { r -> r.pieces.count { it.type == PieceType.BLOOD } })
        for (p in g.players) {
            val f = p.faction
            val warriors = g.reefs.sumOf { it.warriors(f) }
            when (val s = p.fs) {
                is SharksState -> assertEquals("sharks", 10, s.supply + warriors)
                is CoralState -> {
                    assertEquals("polyps", 20, s.polyps + warriors)
                    assertEquals("coral", 15, s.coral + g.reefs.sumOf { it.buildingsOf(f) })
                }
            }
        }
        for (i in g.reefs.indices) assertTrue("slots in ${Board.name(i)}", Game.freeSlots(g, i) >= 0)
        if (g.phase != Phase.OVER) assertTrue(Game.decision(g)!!.options.isNotEmpty())
    }

    private fun play(seed: Long, seats: List<Seat>): GameState {
        val g = Game.newGame(seats, seed)
        val bot = Bot(Random(seed), samples = 2)
        var steps = 0
        while (g.phase != Phase.OVER) {
            val d = Game.decision(g)!!
            Game.apply(g, bot.choose(g, d))
            checkInvariants(g)
            steps++
            if (g.round > 60 || steps >= 5000) {
                val summary = g.players.joinToString { "${it.faction.display} ${it.vp} VP" }
                val board = g.reefs.indices.filter { g.reefs[it].warriors.isNotEmpty() || g.reefs[it].pieces.isNotEmpty() }
                    .joinToString("\n") { r -> "  ${Board.name(r)}: ${g.reefs[r].warriors} ${g.reefs[r].pieces.map { it.type }}" }
                val hands = g.players.joinToString("\n") { pl -> "  ${pl.faction.display} hand: ${pl.hand.map { Cards[it].name }} supply: ${Game.rules(pl.faction).supplySummary(g, g.player(pl.faction))}" }
                throw AssertionError("game $seed runs too long (round ${g.round}): $summary\n$board\n$hands\n" + g.log.takeLast(12).joinToString("\n"))
            }
        }
        return g
    }

    @Test
    fun botGamesFinishAndKeepEveryPiece() {
        val wins = mutableMapOf<FactionId, Int>()
        val rounds = mutableListOf<Int>()
        val vpSources = mutableMapOf<String, Int>()
        val games = 30
        for (i in 0 until games) {
            val seats = listOf(Seat(FactionId.SHARKS, false), Seat(FactionId.CORAL, false)).let { if (i % 2 == 0) it else it.reversed() }
            val g = play(1000L + i, seats)
            val w = g.players[g.winner!!].faction
            wins[w] = (wins[w] ?: 0) + 1
            rounds += g.round
            for (line in g.log) {
                val m = Regex("""^(\w+): \+(\d+) VP for (\w+)""").find(line) ?: continue
                val key = "${m.groupValues[1]} ${m.groupValues[3]}"
                vpSources[key] = (vpSources[key] ?: 0) + m.groupValues[2].toInt()
            }
            println("  game ${1000L + i}: ${w.display} win in round ${g.round}, " + g.players.joinToString { "${it.faction.display} ${it.vp}" } + " (${g.winText})")
        }
        println("Sharks vs Coral, $games bot games: wins $wins, rounds ${rounds.sorted()}")
        println("VP by source: " + vpSources.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
    }
}
