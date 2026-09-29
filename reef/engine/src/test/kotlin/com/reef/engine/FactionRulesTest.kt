package com.reef.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test per signature rule of the factions beyond Sharks and Coral. */
class FactionRulesTest {

    /** A game with every faction set up at its first choice, at the first player's Dawn. */
    private fun game(vararg f: FactionId, seed: Long = 1): GameState {
        val g = Game.newGame(f.map { Seat(it, false) }, seed)
        while (g.phase == Phase.SETUP) Game.apply(g, Game.decision(g)!!.options.first())
        return g
    }

    private fun GameState.clearAmbushes() = players.forEach { pl -> pl.hand.removeAll { Cards[it].kind == CardKind.AMBUSH } }

    /** Takes a card of [suit] out of wherever it is and puts it in [p]'s hand. */
    private fun GameState.give(p: Int, suit: Suit): Int {
        val c = Cards.all.first { it.suit == suit && it.kind == CardKind.GEAR && (it.id in drawPile || it.id in discard) }.id
        drawPile.remove(c); discard.remove(c)
        players[p].hand.add(c)
        return c
    }

    @Test
    fun snakeSlithersAsOneLineAndACutLosesTheTail() {
        val g = game(FactionId.SNAKE, FactionId.CORAL)
        val p = g.player(FactionId.SNAKE)
        val s = g.players[p].fs as SnakeState
        s.body.clear(); s.body.addAll(listOf(6, 5, 4, 4))
        SnakeRules.sync(g, p)
        SnakeRules.applyDay(g, p, Slither(6, 7))
        assertEquals(listOf(7, 6, 5, 4), s.body)
        assertEquals(1, g.reefs[7].warriors(FactionId.SNAKE))
        // A hit in the Arch takes the segment there; Meadow and Hollow aren't neighbors, so the tail is lost.
        SnakeRules.removeWarriors(g, p, 6, 1, 0)
        assertEquals(listOf(7), s.body)
        assertEquals(0, g.reefs[4].warriors(FactionId.SNAKE))
        assertEquals(SnakeRules.PIECES - 1, s.segments)
    }

    @Test
    fun snakeGrowsWhenSmallAndWhenItEats() {
        val g = game(FactionId.SNAKE, FactionId.CORAL)
        val p = g.player(FactionId.SNAKE)
        val s = g.players[p].fs as SnakeState
        s.body.clear(); s.body.addAll(listOf(5, 5))
        SnakeRules.sync(g, p)
        SnakeRules.beginTurn(g, p)
        assertEquals(3, s.body.size)
        SnakeRules.grow(g, p, 2)
        assertEquals(5, s.body.size)
    }

    @Test
    fun remorasRideTheirHostAndDropOffWhenItIsGone() {
        val g = game(FactionId.REMORAS, FactionId.SHARKS, FactionId.CORAL)
        val r = g.player(FactionId.REMORAS)
        val sh = g.player(FactionId.SHARKS)
        val s = g.players[r].fs as RemorasState
        assertEquals(3, g.reefs[0].warriors(FactionId.REMORAS))
        assertEquals(3, g.reefs[0].warriors(FactionId.SHARKS))
        RemorasRules.applyDay(g, r, Attach(0, FactionId.SHARKS, 2))
        assertEquals(1, g.reefs[0].warriors(FactionId.REMORAS))
        Game.moveWarriors(g, sh, 0, 4, 3)
        assertEquals(2, s.attached[4]?.get(FactionId.SHARKS))
        Game.hit(g, g.player(FactionId.CORAL), sh, 4, 9, Source.BATTLE, AttackCtx())
        assertEquals(0, g.reefs[4].warriors(FactionId.SHARKS))
        assertEquals(2, g.reefs[4].warriors(FactionId.REMORAS))
        assertTrue(s.attached.isEmpty())
    }

    @Test
    fun remorasEatScrapsOncePerAttack() {
        val g = game(FactionId.REMORAS, FactionId.SHARKS, FactionId.CORAL)
        val r = g.player(FactionId.REMORAS)
        val sh = g.player(FactionId.SHARKS)
        RemorasRules.applyDay(g, r, Attach(0, FactionId.SHARKS, 1))
        val coral = g.player(FactionId.CORAL)
        g.reefs[0].addWarriors(FactionId.CORAL, 2)
        (g.players[coral].fs as CoralState).polyps -= 2
        val ctx = AttackCtx()
        Game.hit(g, sh, coral, 0, 1, Source.BATTLE, ctx)
        Game.hit(g, sh, coral, 0, 1, Source.BATTLE, ctx)
        assertEquals(1, g.players[r].vp)
    }

    @Test
    fun theShellShopSellsAtTheStartOfAnotherFactionsDay() {
        val g = game(FactionId.SHARKS, FactionId.CRABS)
        val sh = g.player(FactionId.SHARKS)
        val cr = g.player(FactionId.CRABS)
        val crabs = g.players[cr].fs as CrabsState
        Game.apply(g, Game.decision(g)!!.options.first())
        val shop = Game.decision(g)!!
        assertEquals(sh, shop.player)
        val buy = shop.options.filterIsInstance<BuyShell>().first { it.reef == 0 }
        Game.apply(g, buy)
        val card = g.players[sh].hand.first()
        Game.apply(g, PayCard(card))
        assertTrue(card in crabs.till)
        assertEquals(1, crabs.shells[0]?.get(FactionId.SHARKS))
        assertEquals(0, g.players[cr].vp)
        // The shell ignores the next hit on the sharks there, then goes back to the pool.
        val before = g.reefs[0].warriors(FactionId.SHARKS)
        Game.hit(g, cr, sh, 0, 1, Source.BATTLE, AttackCtx())
        assertEquals(before, g.reefs[0].warriors(FactionId.SHARKS))
        assertEquals(CrabsRules.SHELLS, crabs.pool)
    }

    @Test
    fun rentedShellsComeBackAtTheCrabsDawn() {
        val g = game(FactionId.SHARKS, FactionId.CRABS)
        val cr = g.player(FactionId.CRABS)
        val crabs = g.players[cr].fs as CrabsState
        CrabsRules.sell(g, g.player(FactionId.SHARKS), 0)
        assertEquals(CrabsRules.SHELLS - 1, crabs.pool)
        CrabsRules.beginTurn(g, cr)
        assertEquals(CrabsRules.SHELLS, crabs.pool)
        assertTrue(crabs.shells.isEmpty())
    }

    @Test
    fun anglersSnapBiteFirstScoreWhatTheyEatAndSinkBack() {
        val g = game(FactionId.ANGLERS, FactionId.CORAL)
        g.clearAmbushes()
        val a = g.player(FactionId.ANGLERS)
        val c = g.player(FactionId.CORAL)
        val s = g.players[a].fs as AnglersState
        g.reefs[10].addWarriors(FactionId.CORAL, 2)
        (g.players[c].fs as CoralState).polyps -= 2
        g.current = a
        g.phase = Phase.DUSK
        g.turn = Turn()
        val snap = Game.decision(g)!!.options.filterIsInstance<Snap>().first { it.reef == 10 && it.n == 3 }
        Game.apply(g, snap)
        val eaten = 2 - g.reefs[10].warriors(FactionId.CORAL)
        assertTrue("the first bite always lands", eaten >= 1)
        assertEquals(eaten, g.players[a].vp)
        assertEquals(eaten, s.eaten)
        assertEquals(0, g.reefs[10].warriors(FactionId.ANGLERS))
        assertEquals(10, s.trench + s.supply)
    }

    @Test
    fun anOctopusArmThatCantObeyRecoils() {
        val g = game(FactionId.OCTOPUS, FactionId.CORAL)
        val o = g.player(FactionId.OCTOPUS)
        val s = g.players[o].fs as OctopusState
        // Arm 1 sits alone in the Meadow with an order to steal: there is nothing to steal.
        s.arms[0] = 7
        OctopusRules.sync(g, o)
        val pearl = g.give(o, Suit.PEARL)
        val kelp = g.give(o, Suit.KELP)
        g.players[o].hand.removeAll(listOf(pearl, kelp))
        s.orders[0] = pearl
        s.orders[3] = kelp
        g.players[o].vp = 5
        g.current = o
        g.phase = Phase.DAY
        g.turn = Turn()
        assertEquals(listOf(Recoil(0, 2)), Game.decision(g)!!.options)
        Game.apply(g, Recoil(0, 2))
        assertTrue(s.orders.all { it == null })
        assertEquals(3, g.players[o].vp)
        assertTrue(pearl in g.discard && kelp in g.discard)
    }

    @Test
    fun losingTheMantleScattersTheGarden() {
        val g = game(FactionId.OCTOPUS, FactionId.CUTTLEFISH)
        val o = g.player(FactionId.OCTOPUS)
        val cu = g.player(FactionId.CUTTLEFISH)
        val s = g.players[o].fs as OctopusState
        val pigments = g.players[cu].fs as CuttlefishState
        val reef = s.mantle
        g.reefs[reef].pieces.add(Piece(FactionId.CUTTLEFISH, PieceType.PIGMENT, suit = Suit.KELP))
        pigments.pigments[Suit.KELP] = 1
        s.orders[0] = g.give(o, Suit.PEARL).also { g.players[o].hand.remove(it) }
        OctopusRules.applyDay(g, o, ArmSteal(0, reef, FactionId.CUTTLEFISH, PieceType.PIGMENT))
        assertEquals(1, s.garden.size)
        assertEquals(1, g.players[o].vp)
        OctopusRules.removeWarriors(g, o, reef, 9, 0)
        assertEquals(-1, s.mantle)
        assertTrue(s.garden.isEmpty())
        assertEquals(2, pigments.pigments[Suit.KELP])
        // At the next Dawn the Mantle comes back at a gate, since no arm is left.
        assertEquals(Board.gates.map { MantleMove(it) }, OctopusRules.dawnOptions(g, o))
    }

    @Test
    fun paintChangesTheSuitAndCamouflageCostsACard() {
        val g = game(FactionId.CUTTLEFISH, FactionId.SHARKS)
        g.clearAmbushes()
        val cu = g.player(FactionId.CUTTLEFISH)
        val sh = g.player(FactionId.SHARKS)
        assertEquals(Suit.SPONGE, g.suitOf(1))
        val kelp = g.give(cu, Suit.KELP)
        CuttlefishRules.applyDay(g, cu, Paint(1, kelp, Suit.KELP))
        assertEquals(Suit.KELP, g.suitOf(1))
        assertTrue(CuttlefishRules.camouflaged(g, 1))
        g.reefs[1].addWarriors(FactionId.SHARKS, 1)
        (g.players[sh].fs as SharksState).supply -= 1
        g.players[sh].hand.clear()
        assertFalse(FactionId.CUTTLEFISH in Game.battleTargets(g, sh, 1))
        val card = g.give(sh, Suit.KELP)
        assertTrue(FactionId.CUTTLEFISH in Game.battleTargets(g, sh, 1))
        Game.startBattle(g, sh, 1, FactionId.CUTTLEFISH)
        assertTrue("the attacker discards a card of the painted suit", card in g.discard)
    }

    @Test
    fun jellyfishStingArrivals() {
        val g = game(FactionId.JELLYFISH, FactionId.SHARKS)
        val j = g.player(FactionId.JELLYFISH)
        val sh = g.player(FactionId.SHARKS)
        assertTrue(g.reefs[5].warriors(FactionId.JELLYFISH) >= 2)
        g.reefs[4].addWarriors(FactionId.SHARKS, 1)
        (g.players[sh].fs as SharksState).supply -= 1
        Game.moveWarriors(g, sh, 4, 5, 1)
        assertEquals(0, g.reefs[5].warriors(FactionId.SHARKS))
        assertEquals(1, g.players[j].vp)
    }

    @Test
    fun aSandbarCutsAChannelForEveryoneButTheParrotfish() {
        val g = game(FactionId.PARROTFISH, FactionId.CORAL)
        g.markers += ChannelMarker(0, 1, MarkerType.SANDBAR, FactionId.PARROTFISH)
        assertFalse(1 in Game.neighbors(g, 0, FactionId.CORAL))
        assertTrue(1 in Game.neighbors(g, 0, FactionId.PARROTFISH))
    }

    @Test
    fun aTurtleShellIgnoresTheFirstHitOfAnAttack() {
        val g = game(FactionId.TURTLES, FactionId.SHARKS)
        val t = g.player(FactionId.TURTLES)
        val s = g.players[t].fs as TurtlesState
        val reef = s.turtles.first().reef
        Game.hit(g, g.player(FactionId.SHARKS), t, reef, 2, Source.BATTLE, AttackCtx())
        assertEquals(3, s.turtles.size)
        assertEquals(3, g.reefs[reef].warriors(FactionId.TURTLES))
    }

    @Test
    fun turtleEggsScoreWhenLaidAndWhenTheyHatch() {
        val g = game(FactionId.TURTLES, FactionId.SHARKS)
        val t = g.player(FactionId.TURTLES)
        val s = g.players[t].fs as TurtlesState
        val turtle = s.turtles.first()
        // No enemy on the beach, so every hatchling makes it to the sea.
        val sharksHere = g.reefs[turtle.reef].warriors(FactionId.SHARKS)
        g.reefs[turtle.reef].addWarriors(FactionId.SHARKS, -sharksHere)
        g.reefs[Board.neighbors(turtle.reef).first()].addWarriors(FactionId.SHARKS, sharksHere)
        turtle.food += Suit.KELP
        turtle.food += Suit.PEARL
        TurtlesRules.applyDay(g, t, Lay(turtle.id, turtle.reef, 3))
        assertEquals(3, g.players[t].vp)
        TurtlesRules.beginTurn(g, t)
        assertEquals(6, g.players[t].vp)
        assertEquals(10, s.eggs)
    }

    @Test
    fun starfishRegrowNextDoorWhenAttacked() {
        val g = game(FactionId.STARFISH, FactionId.SHARKS)
        val st = g.player(FactionId.STARFISH)
        val reefs = g.reefs.indices.filter { g.reefs[it].warriors(FactionId.STARFISH) > 0 }
        assertEquals(2, reefs.size)
        val (a, b) = reefs
        Game.hit(g, g.player(FactionId.SHARKS), st, a, 2, Source.BATTLE, AttackCtx())
        assertEquals(1, g.reefs[a].warriors(FactionId.STARFISH))
        assertEquals(3 + StarfishRules.REGROW, g.reefs[b].warriors(FactionId.STARFISH))
    }

    @Test
    fun sardineSchoolsScoreByTheirSize() {
        assertEquals(listOf(0, 1, 1, 1, 2, 2, 2, 4, 4, 4, 6, 6), (0..11).map { SardinesRules.exitVp(it) })
    }
}
