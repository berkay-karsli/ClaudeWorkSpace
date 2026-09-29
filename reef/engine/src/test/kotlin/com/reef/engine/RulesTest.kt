package com.reef.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val sharks = FactionId.SHARKS
    private val coral = FactionId.CORAL

    /** Sharks set up at Gull Rock, Coral in Hollow; returns the game at the Sharks' first Dawn. */
    private fun started(seed: Long = 1): GameState {
        val g = Game.newGame(listOf(Seat(sharks, false), Seat(coral, false)), seed)
        Game.apply(g, PlaceSetup(0))
        Game.apply(g, PlaceSetup(5))
        return g
    }

    private fun GameState.sharkState() = players[player(FactionId.SHARKS)].fs as SharksState

    @Test
    fun boardMatchesTheDesign() {
        assertEquals(12, Board.reefs.size)
        assertEquals(19, Board.channels.size)
        assertEquals(listOf(0, 3, 8, 11), Board.gates)
        for (s in listOf(Suit.KELP, Suit.SPONGE, Suit.PEARL)) assertEquals(4, Board.reefs.count { it.suit == s })
        for (r in Board.reefs) for (n in Board.neighbors(r.id)) assertTrue(r.id in Board.neighbors(n))
        for ((a, b) in Board.currents) assertTrue(b in Board.neighbors(a))
        for (r in Board.reefs) for (o in Board.reefs) assertTrue(Board.distance(r.id, o.id) < 12)
    }

    @Test
    fun deckHas54CardsWithOneDominancePerSuit() {
        assertEquals(54, Cards.all.size)
        assertEquals(4, Cards.all.count { it.kind == CardKind.DOMINANCE })
        assertEquals(Suit.entries.toSet(), Cards.all.filter { it.kind == CardKind.DOMINANCE }.map { it.suit }.toSet())
        assertEquals(Cards.all.indices.toList(), Cards.all.map { it.id })
    }

    @Test
    fun platesComeFromTheDesignAndHaveThreeRulesEach() {
        for (f in FactionId.entries) {
            val plate = Plates.of(f)
            assertEquals(f.display, plate.name)
            assertEquals(3, plate.rules.size)
            val verbs = plate.verbs
            assertEquals("${f.display}: only buildings craft", verbs["Build"], verbs["Craft"])
            assertEquals("${f.display}: craft rule", verbs["Craft"], Game.rules(f).canCraft)
        }
        assertTrue(Plates.shared.isNotEmpty())
    }

    @Test
    fun setupPlacesTheRightPieces() {
        val g = started()
        assertEquals(3, g.reefs[0].warriors(sharks))
        assertEquals(2, g.reefs[5].buildingsOf(coral))
        assertEquals(3, g.reefs[5].warriors(coral))
        for (n in Board.neighbors(5)) assertEquals(1, g.reefs[n].warriors(coral))
        assertEquals(Phase.DAWN, g.phase)
        assertEquals(Board.gates.map { Arrive(it, 1, "shark") }, Game.decision(g)!!.options)
    }

    @Test
    fun sharksCountDoubleForRule() {
        val g = started()
        // Gull Rock: 3 sharks (6) against Coral's 1 polyp from spawning next door.
        assertEquals(g.player(sharks), Game.ruler(g, 0))
        g.reefs[5].addWarriors(sharks, 2)
        // Hollow: 2 sharks = 4 against 3 polyps + 2 coral = 5.
        assertEquals(g.player(coral), Game.ruler(g, 5))
        g.reefs[5].addWarriors(sharks, 1)
        assertEquals(g.player(sharks), Game.ruler(g, 5))
    }

    @Test
    fun sharksThatDontMoveDrown() {
        val g = started()
        Game.apply(g, Arrive(3, 1, "shark"))
        val moveOne = Game.decision(g)!!.options.filterIsInstance<Hunt>().first { it.from == 0 && it.n == 2 && it.to == 1 && it.prey == null }
        Game.apply(g, moveOne)
        // End the Day: 1 shark left at Gull Rock and the new one at Shipwreck never moved.
        Game.apply(g, EndDay)
        assertEquals(0, g.reefs[0].warriors(sharks))
        assertEquals(0, g.reefs[3].warriors(sharks))
        assertEquals(2, g.reefs[1].warriors(sharks))
        assertEquals(8, g.sharkState().supply)
    }

    @Test
    fun battleCapsHitsAndBloodScoresAtDusk() {
        val g = started(seed = 3)
        Game.apply(g, Arrive(0, 1, "shark"))
        // Four sharks hunt into Garden, where Coral has one polyp.
        val c = g.player(coral)
        g.players[c].hand.removeAll { Cards[it].kind == CardKind.AMBUSH }
        Game.apply(g, Hunt(0, 4, 4, scent = false, prey = coral))
        assertEquals(0, g.reefs[4].warriors(coral))
        assertTrue("an attack that removes a warrior leaves Blood", g.reefs[4].has(PieceType.BLOOD))
        val vpBefore = g.players[g.player(sharks)].vp
        Game.apply(g, EndDay)
        assertEquals(vpBefore + SharksRules.BLOOD_VP, g.players[g.player(sharks)].vp)
        assertFalse(g.reefs[4].has(PieceType.BLOOD))
        assertEquals(GameState.BLOOD_TOKENS, g.blood)
    }

    @Test
    fun defenselessDefenderTakesAnExtraHitAndBuildingsScore() {
        val g = started(seed = 5)
        val c = g.player(coral)
        g.players[c].hand.removeAll { Cards[it].kind == CardKind.AMBUSH }
        // Hollow: remove the polyps, leaving 2 undefended coral. One shark waits next door in Flats.
        g.reefs[5].addWarriors(coral, -3)
        (g.players[c].fs as CoralState).polyps += 3
        g.reefs[1].addWarriors(sharks, 1)
        g.sharkState().supply -= 1
        Game.apply(g, Arrive(0, 1, "shark"))
        val vp = g.players[g.player(sharks)].vp
        Game.apply(g, Hunt(1, 5, 1, scent = false, prey = coral))
        val destroyed = 2 - g.reefs[5].buildingsOf(coral)
        // At least the defenseless hit lands, and each coral destroyed scores 1 VP.
        assertTrue(destroyed >= 1)
        assertEquals(vp + destroyed, g.players[g.player(sharks)].vp)
        assertEquals(15 - g.reefs.sumOf { it.buildingsOf(coral) }, (g.players[c].fs as CoralState).coral)
    }

    @Test
    fun livingReefLetsCoralHitBack() {
        val g = started()
        assertEquals(2, CoralRules.defenseCapBonus(g, g.player(coral), 5))
    }

    @Test
    fun ambushGoesToTheDefenderFirst() {
        val g = started()
        val c = g.player(coral)
        val ambush = Cards.all.first { it.kind == CardKind.AMBUSH && it.suit == Suit.MOON }.id
        g.drawPile.remove(ambush); g.discard.remove(ambush); g.players.forEach { it.hand.remove(ambush) }
        g.players[c].hand.add(ambush)
        Game.apply(g, Arrive(0, 1, "shark"))
        Game.apply(g, Hunt(0, 4, 4, scent = false, prey = coral))
        val d = Game.decision(g)!!
        assertEquals(c, d.player)
        assertTrue(PlayAmbush(ambush) in d.options)
        Game.apply(g, PlayAmbush(ambush))
        assertEquals(2, g.reefs[4].warriors(sharks))
        assertTrue(g.pending.isEmpty())
    }

    @Test
    fun coralScoresOneOneOneTwoTwoTwo() {
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 3), (3..9).map { CoralRules.growVp(it) })
    }

    @Test
    fun spawnPutsPolypsNextToMatchingCoral() {
        val g = started()
        Game.apply(g, Arrive(0, 1, "shark"))
        // Pass the Sharks' turn by moving every shark, then end the Day.
        Game.apply(g, Hunt(0, 1, 4, scent = false, prey = null))
        Game.apply(g, EndDay)
        while (g.phase == Phase.DUSK) Game.apply(g, Game.decision(g)!!.options.first())
        val c = g.player(coral)
        assertEquals(c, g.current)
        val sponge = Cards.all.first { it.suit == Suit.SPONGE && it.kind == CardKind.GEAR }.id
        g.drawPile.remove(sponge); g.discard.remove(sponge); g.players.forEach { it.hand.remove(sponge) }
        g.players[c].hand.add(sponge)
        val before = Board.neighbors(5).associateWith { g.reefs[it].warriors(coral) }
        Game.apply(g, Spawn(sponge))
        for ((n, was) in before) assertEquals(was + 1, g.reefs[n].warriors(coral))
    }

    @Test
    fun coralWithNoCoralLeftCanStillSpawn() {
        val g = started()
        val c = g.player(coral)
        val cs = g.players[c].fs as CoralState
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            cs.polyps += rs.warriors(coral); rs.warriors.remove(coral)
            cs.coral += rs.buildingsOf(coral); rs.pieces.removeAll { it.owner == coral }
        }
        g.current = c
        g.phase = Phase.DAY
        g.turn = Turn(actionsLeft = 3)
        val kelp = Cards.all.first { it.suit == Suit.KELP && it.kind == CardKind.GEAR }.id
        g.drawPile.remove(kelp); g.discard.remove(kelp); g.players.forEach { it.hand.remove(kelp) }
        g.players[c].hand.add(kelp)
        val drift = Game.decision(g)!!.options.filterIsInstance<Spawn>().filter { it.cardId == kelp }
        assertEquals(Board.reefs.filter { it.suit == Suit.KELP }.map { it.id }, drift.map { it.into })
        Game.apply(g, drift.first())
        assertEquals(2, g.reefs[drift.first().into!!].warriors(coral))
    }

    @Test
    fun saveAndLoadGiveTheSameGame() {
        val g = started()
        val copy = GameState.fromJson(g.toJson())
        assertEquals(g.toJson(), copy.toJson())
        assertEquals(Game.decision(g)!!.options, Game.decision(copy)!!.options)
    }
}
