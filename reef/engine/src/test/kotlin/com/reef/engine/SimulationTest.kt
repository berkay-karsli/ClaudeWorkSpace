package com.reef.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Bot-vs-bot games across many lineups: every game must finish, no decision may ever be empty
 * (no softlocks), and no piece or card may ever appear or vanish.
 */
class SimulationTest {

    companion object {
        /** Every piece and card, counted wherever it can be. */
        fun checkInvariants(g: GameState) {
            val octo = g.state<OctopusState>(FactionId.OCTOPUS)
            val crabs = g.state<CrabsState>(FactionId.CRABS)
            val cards = g.drawPile + g.discard + g.players.flatMap { it.hand + it.gear + listOfNotNull(it.dominance) } +
                octo?.orders.orEmpty().filterNotNull() + octo?.garden.orEmpty().mapNotNull { it.card } + crabs?.till.orEmpty()
            assertEquals("cards", Cards.all.indices.toList(), cards.sorted())
            assertEquals("blood", GameState.BLOOD_TOKENS, g.blood + g.reefs.sumOf { r -> r.pieces.count { it.type == PieceType.BLOOD } })
            fun onMap(f: FactionId, t: PieceType) = g.reefs.sumOf { it.count(t, f) }
            fun inGarden(f: FactionId, t: PieceType) = octo?.garden.orEmpty().count { it.owner == f && it.type == t }
            for (p in g.players) {
                val f = p.faction
                val warriors = g.reefs.sumOf { it.warriors(f) }
                when (val s = p.fs) {
                    is SharksState -> assertEquals("sharks", 10, s.supply + warriors)
                    is CoralState -> {
                        assertEquals("polyps", 20, s.polyps + warriors)
                        assertEquals("coral", 15, s.coral + onMap(f, PieceType.CORAL))
                    }
                    is SardinesState -> {
                        assertEquals("sardines", 30, s.supply + warriors)
                        for (r in g.reefs.indices) assertEquals("sardine origins in ${Board.name(r)}", g.reefs[r].warriors(f), s.origins[r]?.values?.sum() ?: 0)
                    }
                    is LionfishState -> assertEquals("lionfish", 24, s.supply + warriors)
                    is StarfishState -> {
                        assertEquals("starfish", 20, s.supply + warriors)
                        assertEquals("rubble", 6, s.rubble + onMap(f, PieceType.RUBBLE) + inGarden(f, PieceType.RUBBLE))
                    }
                    is JellyfishState -> {
                        assertEquals("jellyfish", 24, s.supply + warriors)
                        assertEquals("current arrows", 4, s.arrows + g.markers.count { it.type == MarkerType.CURRENT })
                    }
                    is ParrotfishState -> {
                        assertEquals("parrotfish", 16, s.supply + warriors)
                        assertEquals("sandbars", 8, s.sandbars + g.markers.count { it.type == MarkerType.SANDBAR })
                    }
                    is TurtlesState -> {
                        assertEquals("turtles on the map", s.turtles.size, warriors)
                        for (r in g.reefs.indices) assertEquals("turtles in ${Board.name(r)}", s.turtles.count { it.reef == r }, g.reefs[r].warriors(f))
                        assertEquals("eggs", 10, s.eggs + onMap(f, PieceType.EGG) + inGarden(f, PieceType.EGG))
                        assertEquals("nests", 3, s.nests + onMap(f, PieceType.NEST))
                    }
                    is SnakeState -> {
                        assertEquals("snake body", s.body.size, warriors)
                        assertTrue("snake length", s.body.size <= SnakeRules.PIECES)
                        for (i in 1 until s.body.size) {
                            val a = s.body[i - 1]
                            val b = s.body[i]
                            assertTrue("snake body is one line", a == b || b in Board.neighbors(a))
                        }
                    }
                    is RemorasState -> {
                        assertEquals("remoras", 10, s.supply + warriors + RemorasRules.attachedTotal(s))
                        for ((reef, m) in s.attached) for ((host, n) in m) {
                            assertTrue("remoras ride nothing in ${Board.name(reef)}", n > 0 && g.reefs[reef].warriors(host) > 0 || g.pending.isNotEmpty())
                        }
                    }
                    is CrabsState -> {
                        assertEquals("crabs", 12, s.supply + warriors)
                        assertEquals("markets", 4, s.markets + onMap(f, PieceType.MARKET))
                        assertEquals("shells", CrabsRules.SHELLS, s.pool + s.shells.values.sumOf { it.values.sum() })
                    }
                    is AnglersState -> {
                        assertEquals("anglers", 10, s.supply + s.trench + warriors)
                        if (g.pending.isEmpty()) assertEquals("anglers on the map outside a snap", 0, warriors)
                        assertEquals("lures", 4, s.lures + onMap(f, PieceType.LURE) + inGarden(f, PieceType.LURE))
                    }
                    is OctopusState -> {
                        for (r in g.reefs.indices) {
                            assertEquals("octopus in ${Board.name(r)}", s.arms.count { it == r } + (if (s.mantle == r) 1 else 0), g.reefs[r].warriors(f))
                        }
                    }
                    is CuttlefishState -> {
                        assertEquals("cuttlefish", 12, s.supply + warriors)
                        assertEquals("pigments", 6, s.pigments.values.sum() + onMap(f, PieceType.PIGMENT) + inGarden(f, PieceType.PIGMENT))
                    }
                }
            }
            for (i in g.reefs.indices) assertTrue("slots in ${Board.name(i)}", Game.freeSlots(g, i) >= 0)
            if (g.phase != Phase.OVER) assertTrue("an empty decision is a softlock", Game.decision(g)!!.options.isNotEmpty())
        }

        /** Lineups of 2 to 4 factions that together play every faction several times. */
        fun lineups(count: Int, seed: Int): List<List<FactionId>> {
            val rnd = Random(seed)
            val out = mutableListOf<List<FactionId>>()
            var pool = mutableListOf<FactionId>()
            while (out.size < count) {
                val size = 2 + out.size % 3
                val lineup = mutableListOf<FactionId>()
                while (lineup.size < size) {
                    if (pool.isEmpty()) pool = FactionId.entries.shuffled(rnd).toMutableList()
                    val f = pool.removeAt(0)
                    if (f !in lineup) lineup += f
                }
                if (FactionId.REMORAS in lineup && lineup.size < 3) {
                    lineup += FactionId.entries.filter { it !in lineup }.random(rnd)
                }
                out += lineup
            }
            return out
        }

        fun play(seed: Long, factions: List<FactionId>, maxRounds: Int = 45): GameState {
            val g = Game.newGame(factions.map { Seat(it, false) }, seed)
            val bot = Bot(Random(seed), samples = 2)
            var steps = 0
            while (g.phase != Phase.OVER) {
                val d = Game.decision(g)!!
                Game.apply(g, bot.choose(g, d))
                checkInvariants(g)
                steps++
                if (g.round > maxRounds || steps >= 12000) {
                    val summary = g.players.joinToString { "${it.faction.display} ${it.vp} VP" }
                    val board = g.reefs.indices.filter { g.reefs[it].warriors.isNotEmpty() || g.reefs[it].pieces.isNotEmpty() }
                        .joinToString("\n") { r -> "  ${Board.name(r)}: ${g.reefs[r].warriors} ${g.reefs[r].pieces.map { it.type }}" }
                    val hands = g.players.joinToString("\n") { pl -> "  ${pl.faction.display} hand: ${pl.hand.map { Cards[it].name }} · ${Game.rules(pl.faction).supplySummary(g, g.player(pl.faction))}" }
                    throw AssertionError("game $seed ($summary) runs too long (round ${g.round})\n$board\n$hands\n" + g.log.takeLast(16).joinToString("\n"))
                }
            }
            return g
        }
    }

    @Test
    fun botGamesAcrossLineupsFinishAndKeepEveryPiece() {
        val games = System.getProperty("reef.games")?.toInt() ?: 42
        val wins = mutableMapOf<FactionId, Int>()
        val played = mutableMapOf<FactionId, Int>()
        val rounds = mutableListOf<Int>()
        val vpSources = mutableMapOf<String, Int>()
        val rates = mutableMapOf<FactionId, MutableList<Double>>()
        val start = System.currentTimeMillis()
        for ((i, lineup) in lineups(games, 7).withIndex()) {
            val seed = 2000L + i
            val g = play(seed, lineup)
            val w = g.players[g.winner!!].faction
            wins[w] = (wins[w] ?: 0) + 1
            for (f in lineup) played[f] = (played[f] ?: 0) + 1
            for (pl in g.players) rates.getOrPut(pl.faction) { mutableListOf() } += pl.vp.toDouble() / g.round
            rounds += g.round
            for (line in g.log) {
                val m = Regex("""^([\w ]+): \+(\d+) VP for (?:the |a |an |\d+ )?(\w+)""").find(line) ?: continue
                val key = "${m.groupValues[1]} ${m.groupValues[3]}"
                vpSources[key] = (vpSources[key] ?: 0) + m.groupValues[2].toInt()
            }
            println("  game $seed: ${w.display} win in round ${g.round}: " + g.players.joinToString { "${it.faction.display} ${it.vp}" } + " (${g.winText})")
        }
        println("$games games in ${(System.currentTimeMillis() - start) / 1000}s, rounds ${rounds.sorted()}")
        for (f in FactionId.entries) {
            val r = rates[f].orEmpty()
            println("  ${f.display}: won ${wins[f] ?: 0} of ${played[f] ?: 0}, VP per round %.2f".format(r.average()))
        }
        println("VP by source: " + vpSources.entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
        for (f in FactionId.entries) assertTrue("${f.display} never played", (played[f] ?: 0) > 0)
    }
}
