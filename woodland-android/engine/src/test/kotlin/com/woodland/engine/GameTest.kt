package com.woodland.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameTest {
    private val setups = listOf(
        listOf(Faction.CATS, Faction.BIRDS),
        listOf(Faction.CATS, Faction.ALLIANCE),
        listOf(Faction.BIRDS, Faction.ALLIANCE),
        listOf(Faction.CATS, Faction.BIRDS, Faction.ALLIANCE),
        listOf(Faction.CATS, Faction.VAGABOND),
        listOf(Faction.BIRDS, Faction.ALLIANCE, Faction.VAGABOND),
        listOf(Faction.CATS, Faction.BIRDS, Faction.ALLIANCE, Faction.VAGABOND),
    )

    private fun play(factions: List<Faction>, seed: Long): Game {
        val game = Game(GameConfig(factions.map { Seat(it, human = false) }), seed)
        val ai = Random(seed * 31 + 7)
        game.start()
        var steps = 0
        while (!game.finished) {
            game.answer(Ai.pick(game, ai))
            checkInvariants(game)
            steps++
            check(steps < 50_000) { "game did not end" }
        }
        return game
    }

    private fun checkInvariants(game: Game) {
        for (f in game.order) {
            assertTrue("$f warrior supply", game.warriorSupply(f) >= 0)
            assertTrue(game.player(f).vp >= 0)
        }
        for (cs in game.board) {
            assertTrue("slots in ${cs.id}", cs.buildings.size <= cs.def.slots)
            assertTrue(cs.warriors.all { it >= 0 })
            assertTrue(cs.wood >= 0)
        }
        assertTrue(game.woodOnMap() <= Game.MAX_WOOD)
        assertTrue(game.sympathyOnMap() <= Game.MAX_SYMPATHY)
        assertTrue(game.count(BuildingType.ROOST) <= BuildingType.ROOST.max)
        assertTrue(game.baseSuits().size == game.baseSuits().toSet().size)
        val cards = game.deckSize + game.discardPile.size + game.availableDominance.size + game.players.values.sumOf { p ->
            p.hand.size + p.effects.size + p.supporters.size + (if (p.dominance != null) 1 else 0) +
                p.decree.sumOf { col -> col.count { it.kind != CardKind.VIZIER } }
        }
        assertTrue(game.itemSupply.values.all { it >= 0 })
        if (game.has(Faction.VAGABOND)) {
            assertTrue((game.vbClearing >= 0) != (game.vbForest >= 0) || game.phase == "Setup")
        }
        assertEquals("cards are conserved", Deck.build().size, cards)
    }

    @Test
    fun aiGamesFinish() {
        val wins = mutableMapOf<Faction, Int>()
        val rounds = mutableListOf<Int>()
        for ((i, f) in setups.withIndex()) {
            for (seed in 1L..30L) {
                val game = play(f, seed * 100 + i)
                assertNotNull(game.winner)
                wins.merge(game.winner!!, 1, Int::plus)
                rounds += game.round
            }
        }
        println("wins=$wins avgRound=${rounds.average()} maxRound=${rounds.max()}")
        check(rounds.max() < Game.MAX_ROUNDS) { "a game stalled" }
    }

    @Test
    fun replayRebuildsTheSameGame() {
        val config = GameConfig(listOf(Seat(Faction.CATS, true), Seat(Faction.BIRDS, false), Seat(Faction.ALLIANCE, false), Seat(Faction.VAGABOND, false)))
        val game = Game(config, 42)
        val ai = Random(1)
        game.start()
        repeat(300) { if (!game.finished) game.answer(Ai.pick(game, ai)) }
        val copy = Game.replay(GameConfig.decode(config.encode()), 42, game.answers.toList())
        assertEquals(game.log, copy.log)
        assertEquals(game.pending?.prompt, copy.pending?.prompt)
        for (f in game.order) assertEquals(game.player(f).vp, copy.player(f).vp)
    }
}
