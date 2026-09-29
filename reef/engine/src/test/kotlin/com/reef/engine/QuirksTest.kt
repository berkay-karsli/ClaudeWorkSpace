package com.reef.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test per rule added in draft 4: quirks, new ways to recruit and battle, and crafting for everyone. */
class QuirksTest {

    /** A game with every faction set up at its first choice, at the first player's Dawn. */
    private fun game(vararg f: FactionId, seed: Long = 1): GameState {
        val g = Game.newGame(f.map { Seat(it, false) }, seed)
        while (g.phase == Phase.SETUP) Game.apply(g, Game.decision(g)!!.options.first())
        g.players.forEach { pl -> pl.hand.removeAll { Cards[it].kind == CardKind.AMBUSH } }
        return g
    }

    /** Takes the first gear card of [suit] from the deck and puts it in [p]'s hand. */
    private fun GameState.give(p: Int, suit: Suit): Int {
        val c = Cards.all.first { it.suit == suit && it.kind == CardKind.GEAR && it.cost.size == 1 && (it.id in drawPile || it.id in discard) }.id
        drawPile.remove(c); discard.remove(c)
        players[p].hand.add(c)
        return c
    }

    private fun options(g: GameState) = Game.decision(g)!!.options

    private fun GameState.blood(reef: Int) {
        reefs[reef].pieces.add(Piece(null, PieceType.BLOOD))
        blood--
    }

    @Test
    fun sharksFrenzyInsteadOfFeedingAndTheNewSharksDontDrown() {
        val g = game(FactionId.SHARKS, FactionId.CORAL)
        g.blood(0)
        Game.apply(g, Arrive(0, 1, "shark"))
        Game.apply(g, EndDay)
        assertEquals(listOf(FeedBlood(0), Frenzy(0, SharksRules.FRENZY)), options(g))
        val vp = g.players[0].vp
        Game.apply(g, Frenzy(0, SharksRules.FRENZY))
        // The 4 sharks that never moved drown; the 2 frenzied ones count as having moved.
        assertEquals(2, g.reefs[0].warriors(FactionId.SHARKS))
        assertEquals(vp, g.players[0].vp)
        assertFalse(g.reefs[0].has(PieceType.BLOOD))
    }

    @Test
    fun sharksDiveThroughTheOpenOceanFromGateToGate() {
        val g = game(FactionId.SHARKS, FactionId.CORAL)
        Game.apply(g, Arrive(0, 1, "shark"))
        val hunts = options(g).filterIsInstance<Hunt>().filter { it.from == 0 }.map { it.to }.toSet()
        assertTrue("every other gate is one move away", Board.gates.filter { it != 0 }.all { it in hunts })
        assertFalse(11 in Board.neighbors(0))
    }

    @Test
    fun sharksCraftWithBloodAndEatIt() {
        val g = game(FactionId.SHARKS, FactionId.CORAL)
        Game.apply(g, Arrive(0, 1, "shark"))
        g.blood(0)
        val card = g.give(0, g.suitOf(0))
        assertTrue(Craft(card) in options(g))
        Game.apply(g, Craft(card))
        assertTrue(card in g.players[0].gear)
        assertFalse("the Blood is eaten", g.reefs[0].has(PieceType.BLOOD))
        assertEquals(GameState.BLOOD_TOKENS, g.blood)
    }

    @Test
    fun sardineMobsDealOneHitPerThreeFish() {
        val g = game(FactionId.SARDINES, FactionId.CORAL)
        g.reefs[0].addWarriors(FactionId.SARDINES, 2)
        assertEquals(7, g.reefs[0].warriors(FactionId.SARDINES))
        assertEquals(2, SardinesRules.attackCap(g, 0, 0))
    }

    @Test
    fun aWallOfFishPushesAWeakerFactionAside() {
        val g = game(FactionId.SARDINES, FactionId.LIONFISH)
        val l = g.player(FactionId.LIONFISH)
        g.reefs[5].addWarriors(FactionId.LIONFISH, 2)
        (g.players[l].fs as LionfishState).supply -= 2
        Game.apply(g, RunGate(0))
        val school = g.reefs[0].warriors(FactionId.SARDINES)
        assertTrue(school >= SardinesRules.WALL)
        Game.apply(g, Move(0, 5, school))
        val push = Push(5, FactionId.LIONFISH, 4)
        assertTrue(push in options(g))
        Game.apply(g, push)
        assertEquals(0, g.reefs[5].warriors(FactionId.LIONFISH))
        assertEquals(2, g.reefs[4].warriors(FactionId.LIONFISH))
    }

    @Test
    fun stuffedLionfishCantMoveOrGorgeAgain() {
        val g = game(FactionId.LIONFISH, FactionId.CORAL)
        assertEquals(1, g.reefs[0].warriors(FactionId.CORAL))
        Game.apply(g, Gorge(0, FactionId.CORAL))
        assertEquals(0, g.reefs[0].warriors(FactionId.CORAL))
        assertEquals(1, g.players[0].vp)
        val o = options(g)
        assertTrue(o.none { it is Move && it.from == 0 })
        assertTrue(o.none { it is Gorge && it.reef == 0 })
    }

    @Test
    fun lionfishChooseWhereTheYoungSettle() {
        val g = game(FactionId.LIONFISH, FactionId.CORAL)
        g.reefs[6].addWarriors(FactionId.LIONFISH, 3)
        (g.players[0].fs as LionfishState).supply -= 3
        Game.apply(g, EndDay)
        val breeds = options(g).filterIsInstance<Breed>().filter { it.from == 6 }
        assertEquals(setOf(6) + Board.neighbors(6), breeds.map { it.to }.toSet())
        Game.apply(g, Breed(6, 7))
        assertEquals(1, g.reefs[7].warriors(FactionId.LIONFISH))
    }

    @Test
    fun starfishDevourBuildingsAndLayRubble() {
        val g = game(FactionId.STARFISH, FactionId.CORAL)
        Game.apply(g, Done("Start the Day"))
        val coralReef = g.reefs.indices.first { g.reefs[it].buildingsOf(FactionId.CORAL) == 2 }
        g.reefs[coralReef].addWarriors(FactionId.STARFISH, 3)
        (g.players[0].fs as StarfishState).supply -= 3
        Game.apply(g, Devour(coralReef, FactionId.CORAL, PieceType.CORAL))
        assertEquals(1, g.reefs[coralReef].buildingsOf(FactionId.CORAL))
        assertEquals(1, g.players[0].vp)
        Game.apply(g, Devour(coralReef))
        assertEquals(1, g.reefs[coralReef].count(PieceType.RUBBLE, FactionId.STARFISH))
    }

    @Test
    fun bleachedCoralIsGoneAndItsTurnCantGrow() {
        val g = game(FactionId.CORAL, FactionId.SHARKS)
        val reef = g.reefs.indices.first { g.reefs[it].buildingsOf(FactionId.CORAL) > 0 }
        val hand = g.players[0].hand.size
        Game.apply(g, Bleach(reef))
        assertEquals(hand + 2, g.players[0].hand.size)
        val s = g.players[0].fs as CoralState
        assertEquals(1, s.bleached)
        // 2 coral were set up; the bleached one leaves the game instead of going back to the supply.
        assertEquals(15 - 2, s.coral)
        assertTrue(options(g).none { it is Grow || it is Bleach })
    }

    @Test
    fun coralNurseryScoresReefsWithVisitors() {
        val g = game(FactionId.CORAL, FactionId.SHARKS)
        val reef = g.reefs.indices.first { g.reefs[it].buildingsOf(FactionId.CORAL) > 0 }
        assertEquals(0, CoralRules.nursery(g))
        g.reefs[reef].addWarriors(FactionId.SHARKS, 1)
        assertEquals(1, CoralRules.nursery(g))
    }

    @Test
    fun theLastJellyfishLeavesACystThatGrowsBackIntoTwo() {
        val g = game(FactionId.JELLYFISH, FactionId.SHARKS)
        val j = g.player(FactionId.JELLYFISH)
        val reef = g.reefs.indices.first { g.reefs[it].warriors(FactionId.JELLYFISH) > 0 }
        Game.hit(g, g.player(FactionId.SHARKS), j, reef, 3, Source.BATTLE, AttackCtx())
        assertEquals(0, g.reefs[reef].warriors(FactionId.JELLYFISH))
        assertNotNull(JellyfishRules.cystAt(g, reef))
        JellyfishRules.beginTurn(g, j)
        assertEquals(2, g.reefs[reef].warriors(FactionId.JELLYFISH))
        assertEquals(null, JellyfishRules.cystAt(g, reef))
    }

    @Test
    fun cocoonedParrotfishAreSafeButPinned() {
        val g = game(FactionId.PARROTFISH, FactionId.SHARKS)
        val p = g.player(FactionId.PARROTFISH)
        g.reefs[0].addWarriors(FactionId.PARROTFISH, -4)
        g.reefs[5].addWarriors(FactionId.PARROTFISH, 4)
        g.reefs[5].pieces.add(Piece(FactionId.PARROTFISH, PieceType.COCOON))
        (g.players[p].fs as ParrotfishState).cocoon--
        g.reefs[5].addWarriors(FactionId.SHARKS, 1)
        assertTrue(ParrotfishRules.immune(g, p, 5))
        assertFalse(FactionId.PARROTFISH in Game.battleTargets(g, g.player(FactionId.SHARKS), 5))
        Game.apply(g, options(g).first())
        assertTrue(options(g).none { it is Move && it.from == 5 })
        // The cocoon opens at the next Dusk.
        ParrotfishRules.onDuskStart(g, p)
        assertFalse(ParrotfishRules.immune(g, p, 5))
    }

    @Test
    fun parrotfishPayAnySuitWithSand() {
        val g = game(FactionId.PARROTFISH, FactionId.SHARKS)
        val p = g.player(FactionId.PARROTFISH)
        val s = g.players[p].fs as ParrotfishState
        s.sand = ParrotfishRules.SAND_PER_SUIT
        val unit = Game.craftPayment(g, p, listOf(Suit.KELP))!!.single()
        assertEquals(Suit.MOON, unit.suit)
        ParrotfishRules.spendCraftUnit(g, p, unit)
        assertEquals(0, s.sand)
    }

    @Test
    fun turtlesRideTheGyreAsOneMove() {
        val g = game(FactionId.TURTLES, FactionId.SHARKS)
        assertEquals(listOf(6, 10, 9), TurtlesRules.currentRide(g, 5))
    }

    @Test
    fun hatchlingsDashPastEnemiesAndGetEaten() {
        val g = game(FactionId.TURTLES, FactionId.SHARKS)
        val t = g.player(FactionId.TURTLES)
        val sh = g.player(FactionId.SHARKS)
        g.reefs[7].pieces.add(Piece(FactionId.TURTLES, PieceType.EGG))
        (g.players[t].fs as TurtlesState).eggs--
        g.reefs[7].addWarriors(FactionId.SHARKS, 1)
        val turtleVp = g.players[t].vp
        val sharkVp = g.players[sh].vp
        TurtlesRules.beginTurn(g, t)
        assertEquals(turtleVp, g.players[t].vp)
        assertEquals(sharkVp + 1, g.players[sh].vp)
        assertEquals(0, g.reefs[7].count(PieceType.EGG))
    }

    @Test
    fun aSnakeCoilingOntoItselfSqueezesEveryoneThere() {
        val g = game(FactionId.SNAKE, FactionId.LIONFISH)
        val p = g.player(FactionId.SNAKE)
        val s = g.players[p].fs as SnakeState
        s.body.clear(); s.body.addAll(listOf(6, 5, 5, 5))
        SnakeRules.sync(g, p)
        g.reefs[5].addWarriors(FactionId.LIONFISH, 2)
        (g.players[g.player(FactionId.LIONFISH)].fs as LionfishState).supply -= 2
        SnakeRules.applyDay(g, p, Slither(6, 5))
        assertEquals(1, g.reefs[5].warriors(FactionId.LIONFISH))
        assertEquals("the coil grows the snake", 5, s.body.size)
    }

    @Test
    fun aMoltAddsTwoSegmentsAndTheTailPaysAnySuit() {
        val g = game(FactionId.SNAKE, FactionId.CORAL)
        val p = g.player(FactionId.SNAKE)
        val s = g.players[p].fs as SnakeState
        val card = g.players[p].hand.first()
        SnakeRules.applyDay(g, p, Molt(card))
        assertEquals(6, s.body.size)
        assertEquals(5, SnakeRules.craftUnits(g, p).size)
        SnakeRules.spendCraftUnit(g, p, SnakeRules.craftUnits(g, p).first())
        assertEquals(5, s.body.size)
    }

    @Test
    fun cleaningFeedsBothAndRidersPileIntoTheirHostsBattles() {
        val g = game(FactionId.REMORAS, FactionId.SHARKS, FactionId.CORAL)
        val r = g.player(FactionId.REMORAS)
        val sh = g.player(FactionId.SHARKS)
        val s = g.players[r].fs as RemorasState
        g.reefs[0].addWarriors(FactionId.REMORAS, -2)
        s.attached[0] = mutableMapOf(FactionId.SHARKS to 2)
        Game.apply(g, options(g).first())
        assertTrue(Clean(0, FactionId.SHARKS) in options(g))
        val hands = g.players[r].hand.size to g.players[sh].hand.size
        Game.apply(g, Clean(0, FactionId.SHARKS))
        assertEquals(hands.first + 1, g.players[r].hand.size)
        assertEquals(hands.second + 1, g.players[sh].hand.size)
        assertTrue("once per turn", options(g).none { it is Clean })
        assertEquals(2, RemorasRules.pileOn(g, FactionId.SHARKS, 0))
    }

    @Test
    fun crabsMoveIntoAWreckedHome() {
        val g = game(FactionId.CRABS, FactionId.CORAL)
        val c = g.player(FactionId.CRABS)
        val reef = g.reefs.indices.first { g.reefs[it].buildingsOf(FactionId.CORAL) == 2 }
        g.reefs[reef].addWarriors(FactionId.CRABS, 1)
        (g.players[c].fs as CrabsState).supply--
        Game.removePiece(g, reef, g.reefs[reef].pieces.first { it.type == PieceType.CORAL })
        assertEquals(1, g.reefs[reef].count(PieceType.MARKET, FactionId.CRABS))
    }

    @Test
    fun aHookedFactionCanBeSnappedAnywhere() {
        val g = game(FactionId.ANGLERS, FactionId.SHARKS)
        val a = g.player(FactionId.ANGLERS)
        assertTrue(0 !in AnglersRules.snapReefs(g))
        assertTrue(AnglersRules.duskOptions(g, a).none { it is Snap && it.reef == 0 })
        AnglersRules.hook(g, FactionId.SHARKS)
        assertTrue(AnglersRules.duskOptions(g, a).any { it is Snap && it.reef == 0 })
        AnglersRules.duskAuto(g, a)
        assertTrue((g.players[a].fs as AnglersState).hooked.isEmpty())
    }

    @Test
    fun aLureSpentOnCraftingGoesDark() {
        val g = game(FactionId.ANGLERS, FactionId.SHARKS)
        val a = g.player(FactionId.ANGLERS)
        g.reefs[5].pieces.add(Piece(FactionId.ANGLERS, PieceType.LURE, variant = AnglersRules.TREASURE))
        (g.players[a].fs as AnglersState).lures--
        assertTrue(5 in AnglersRules.snapReefs(g))
        val unit = AnglersRules.craftUnits(g, a).single()
        AnglersRules.spendCraftUnit(g, a, unit)
        assertEquals(AnglersRules.DARK, AnglersRules.lureAt(g, 5)!!.variant)
        assertTrue(5 !in AnglersRules.snapReefs(g))
        assertTrue(AnglersRules.craftUnits(g, a).isEmpty())
    }

    @Test
    fun theOctopusInksAndJetsAwayFromABattle() {
        val g = game(FactionId.SHARKS, FactionId.OCTOPUS)
        val o = g.player(FactionId.OCTOPUS)
        val den = g.reefs.indices.first { g.reefs[it].warriors(FactionId.OCTOPUS) > 0 }
        assertTrue(den in Board.neighbors(0))
        Game.apply(g, Arrive(0, 1, "shark"))
        Game.apply(g, Hunt(0, den, 4, scent = false, prey = FactionId.OCTOPUS))
        val ink = options(g).filterIsInstance<InkCloud>().first()
        Game.apply(g, ink)
        assertEquals(0, g.reefs[den].warriors(FactionId.OCTOPUS))
        assertEquals(9, g.reefs[ink.to].warriors(FactionId.OCTOPUS))
        assertEquals(4, g.reefs[den].warriors(FactionId.SHARKS))
        assertFalse(ink.cardId in g.players[o].hand)
    }

    @Test
    fun theOctopusCanSwapTwoOrdersAtDawn() {
        val g = game(FactionId.OCTOPUS, FactionId.SHARKS)
        val s = g.players[0].fs as OctopusState
        val kelp = g.give(0, Suit.KELP)
        g.players[0].hand.remove(kelp)
        s.orders[0] = kelp
        assertTrue(Rewire(0, 1) in options(g))
        Game.apply(g, Rewire(0, 1))
        assertEquals(null, s.orders[0])
        assertEquals(kelp, s.orders[1])
        assertTrue(options(g).none { it is Rewire })
    }

    @Test
    fun cuttlefishScoreEveryPatternTheyRule() {
        val g = game(FactionId.CUTTLEFISH, FactionId.SHARKS)
        val c = g.player(FactionId.CUTTLEFISH)
        val s = g.players[c].fs as CuttlefishState
        for (r in g.reefs) r.setWarriors(FactionId.CUTTLEFISH, 0)
        s.supply = 12
        for (reef in listOf(2, 6, 7, 9, 10, 11)) {
            g.reefs[reef].addWarriors(FactionId.CUTTLEFISH, 1)
            s.supply--
        }
        s.galleryDeck.addAll(s.gallery)
        s.galleryDeck.removeAll(listOf(0, 5, 6))
        s.gallery.clear(); s.gallery.addAll(listOf(0, 5, 6))
        assertEquals(listOf("Kelp Forest", "The Rim"), CuttlefishRules.metPatterns(g).map { it.name })
        CuttlefishRules.duskAuto(g, c)
        assertEquals(Gallery[0].vp + Gallery[6].vp, g.players[c].vp)
        assertEquals("both scored cards are replaced", 5, s.gallery[1])
        assertTrue(0 !in s.gallery && 6 !in s.gallery)
    }

    @Test
    fun cuttlefishHatchAndHypnotize() {
        val g = game(FactionId.CUTTLEFISH, FactionId.SHARKS)
        val c = g.player(FactionId.CUTTLEFISH)
        Game.apply(g, options(g).first())
        // The second setup reef: 3 cuttlefish and nobody else.
        val reef = 1
        val card = g.give(c, Suit.MOON)
        val before = g.reefs[reef].warriors(FactionId.CUTTLEFISH)
        assertTrue(Hatch(card, reef) in options(g))
        CuttlefishRules.applyDay(g, c, Hatch(card, reef))
        assertEquals(before + 2, g.reefs[reef].warriors(FactionId.CUTTLEFISH))
        g.reefs[reef].addWarriors(FactionId.SHARKS, 2)
        val hyp = CuttlefishRules.dayOptions(g, c).filterIsInstance<Hypnotize>().first { it.from == reef && it.victim == FactionId.SHARKS && it.n == 2 }
        val there = g.reefs[hyp.to].warriors(FactionId.SHARKS)
        CuttlefishRules.applyDay(g, c, hyp)
        assertEquals(0, g.reefs[reef].warriors(FactionId.SHARKS))
        assertEquals(there + 2, g.reefs[hyp.to].warriors(FactionId.SHARKS))
    }

    @Test
    fun rootedCoralAndTheSnakesBodyCantBePushed() {
        val g = game(FactionId.CORAL, FactionId.SNAKE, FactionId.SARDINES)
        assertFalse(CoralRules.pushable(g, g.player(FactionId.CORAL), 0))
        assertFalse(SnakeRules.pushable(g, g.player(FactionId.SNAKE), 0))
    }

    /** Every faction has crafting pieces: here, one simple way to get some for each. */
    @Test
    fun everyFactionCrafts() {
        val ready: Map<FactionId, (GameState, Int) -> Unit> = mapOf(
            FactionId.SHARKS to { g, _ -> g.blood(g.reefs.indices.first { g.reefs[it].warriors(FactionId.SHARKS) > 0 }) },
            FactionId.SARDINES to { _, _ -> },
            FactionId.LIONFISH to { g, p -> g.reefs[7].addWarriors(FactionId.LIONFISH, 3); (g.players[p].fs as LionfishState).supply -= 3 },
            FactionId.STARFISH to { g, p -> g.reefs[5].pieces.add(Piece(FactionId.STARFISH, PieceType.RUBBLE)); (g.players[p].fs as StarfishState).rubble-- },
            FactionId.CORAL to { _, _ -> },
            FactionId.JELLYFISH to { _, _ -> },
            FactionId.PARROTFISH to { g, p -> (g.players[p].fs as ParrotfishState).sand = 3 },
            FactionId.TURTLES to { _, _ -> },
            FactionId.SNAKE to { _, _ -> },
            FactionId.REMORAS to { g, p -> (g.players[p].fs as RemorasState).apply { supply -= 3; attached[7] = mutableMapOf(FactionId.CORAL to 3) } },
            FactionId.CRABS to { _, _ -> },
            FactionId.ANGLERS to { g, p -> g.reefs[5].pieces.add(Piece(FactionId.ANGLERS, PieceType.LURE, variant = AnglersRules.GLORY)); (g.players[p].fs as AnglersState).lures-- },
            FactionId.OCTOPUS to { g, p -> (g.players[p].fs as OctopusState).garden += GardenItem("token", FactionId.CORAL, PieceType.CORAL) },
            FactionId.CUTTLEFISH to { g, p ->
                val reef = g.reefs.indices.first { g.reefs[it].warriors(FactionId.CUTTLEFISH) > 0 }
                g.reefs[reef].pieces.add(Piece(FactionId.CUTTLEFISH, PieceType.PIGMENT, suit = Suit.KELP))
                (g.players[p].fs as CuttlefishState).pigments[Suit.KELP] = 1
            },
        )
        for (f in FactionId.entries) {
            val lineup = listOf(f) + listOf(FactionId.CORAL, FactionId.SHARKS, FactionId.LIONFISH).filter { it != f }.take(2)
            val g = game(*lineup.toTypedArray())
            val p = g.player(f)
            ready.getValue(f)(g, p)
            assertTrue("${f.display} have crafting pieces", Game.craftUnits(g, p).isNotEmpty())
            assertNotNull("${f.display} can pay for a gear card", Game.craftPayment(g, p, listOf(Suit.MOON)))
        }
    }
}
