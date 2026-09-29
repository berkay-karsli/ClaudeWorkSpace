package com.reef.engine

import kotlin.math.max
import kotlin.math.min

/** A seat at the table: which faction, and whether a person or a bot plays it. */
data class Seat(val faction: FactionId, val human: Boolean)

/**
 * The shared rules. A game is a [GameState] plus two calls: [decision] says who must choose and
 * from what, and [apply] carries out a choice and runs the game forward to the next decision.
 */
object Game {
    const val WIN_VP = 30
    const val HAND_LIMIT = 5
    const val STARTING_HAND = 3
    const val DOMINANCE_MIN_VP = 10

    fun rules(f: FactionId): FactionRules = when (f) {
        FactionId.SHARKS -> SharksRules
        FactionId.CORAL -> CoralRules
    }

    fun rules(g: GameState, p: Int): FactionRules = rules(g.players[p].faction)

    fun newGame(seats: List<Seat>, seed: Long): GameState {
        require(seats.map { it.faction }.distinct().size == seats.size) { "Each faction can only be played once" }
        val g = GameState(
            players = seats.map { PlayerState(it.faction, it.human, rules(it.faction).newState()) }.toMutableList(),
            reefs = MutableList(Board.reefs.size) { ReefState() },
            drawPile = Cards.all.map { it.id }.toMutableList(),
            discard = mutableListOf(),
            rng = seed,
        )
        shuffle(g, g.drawPile)
        for (p in g.players.indices) draw(g, p, STARTING_HAND)
        log(g, "A new game: " + g.players.joinToString(" vs ") { it.faction.display } + ".")
        advance(g)
        return g
    }

    // ---- Decisions -------------------------------------------------------------------------

    fun decision(g: GameState): Decision? {
        val p = g.current
        val name = g.players[p].faction.display
        val r = rules(g, p)
        return when (g.phase) {
            Phase.SETUP -> Decision(p, "$name, set up: ${r.setupHint}", r.setupOptions(g, p))
            Phase.DAWN -> Decision(p, "$name, Dawn: ${r.dawnHint}", r.dawnOptions(g, p))
            Phase.DAY -> {
                val b = g.battle
                if (b != null) {
                    Decision(
                        b.defender,
                        "${g.players[b.defender].faction.display}: ${g.players[b.attacker].faction.display} attack you in ${Board.name(b.reef)}. Ambush first?",
                        ambushCards(g, b.defender, b.reef).map { PlayAmbush(it) } + NoAmbush,
                    )
                } else {
                    val actions = if (r.actionsPerDay != null) ", ${g.turn.actionsLeft} action${if (g.turn.actionsLeft == 1) "" else "s"} left" else ""
                    Decision(p, "$name, Day$actions", dayOptions(g, p))
                }
            }
            Phase.DUSK -> Decision(p, "$name, Dusk: discard down to $HAND_LIMIT cards", g.players[p].hand.map { Discard(it) })
            Phase.OVER -> null
        }
    }

    fun apply(g: GameState, o: Option) {
        val d = decision(g) ?: error("The game is over")
        require(o in d.options) { "Not a legal choice now: ${o.describe()}" }
        val p = d.player
        when (g.phase) {
            Phase.SETUP -> rules(g, p).applySetup(g, p, o)
            Phase.DAWN -> rules(g, p).applyDawn(g, p, o)
            Phase.DAY -> applyDay(g, p, o)
            Phase.DUSK -> {
                val c = (o as Discard).cardId
                discardFromHand(g, p, c)
                log(g, "${g.players[p].faction.display}: discard ${Cards[c].name}.")
            }
            Phase.OVER -> {}
        }
        advance(g)
    }

    private fun dayOptions(g: GameState, p: Int): List<Option> {
        val r = rules(g, p)
        val pl = g.players[p]
        val out = mutableListOf<Option>()
        if (r.actionsPerDay == null || g.turn.actionsLeft > 0) out += r.dayOptions(g, p)
        if (r.canCraft) out += pl.hand.filter { Cards[it].kind == CardKind.GEAR && craftPayment(g, p, Cards[it].cost) != null }.map { Craft(it) }
        if (r.actionsPerDay != null && !g.turn.surgeUsed) out += pl.hand.map { Surge(it) }
        if (pl.dominance == null && pl.vp >= DOMINANCE_MIN_VP) out += pl.hand.filter { Cards[it].kind == CardKind.DOMINANCE }.map { PlayDominance(it) }
        out += EndDay
        return out
    }

    private fun applyDay(g: GameState, p: Int, o: Option) {
        val pl = g.players[p]
        when (o) {
            is PlayAmbush, NoAmbush -> {
                val b = g.battle!!
                g.battle = null
                resolveBattle(g, b.attacker, b.defender, b.reef, (o as? PlayAmbush)?.cardId)
            }
            is Craft -> {
                val card = Cards[o.cardId]
                for (reef in craftPayment(g, p, card.cost)!!) g.turn.crafted[reef] = (g.turn.crafted[reef] ?: 0) + 1
                pl.hand.remove(o.cardId)
                pl.gear.add(o.cardId)
                log(g, "${pl.faction.display}: craft ${card.name}.")
                scoreVp(g, p, card.vp, "crafting ${card.name}")
            }
            is Surge -> {
                discardFromHand(g, p, o.cardId)
                g.turn.surgeUsed = true
                g.turn.actionsLeft++
                log(g, "${pl.faction.display}: discard ${Cards[o.cardId].name} for an extra action.")
            }
            is PlayDominance -> {
                pl.hand.remove(o.cardId)
                pl.dominance = o.cardId
                log(g, "${pl.faction.display}: play ${Cards[o.cardId].name}. ${Cards[o.cardId].describe()} No more VP from now on.")
            }
            EndDay -> endDay(g, p)
            is Battle -> {
                g.turn.actionsLeft--
                startBattle(g, p, o.reef, o.defender)
            }
            else -> {
                if (o.costsAction) g.turn.actionsLeft--
                rules(g, p).applyDay(g, p, o)
            }
        }
    }

    // ---- Turn flow -------------------------------------------------------------------------

    fun advance(g: GameState) {
        while (true) {
            if (g.winner != null) {
                g.phase = Phase.OVER
                g.battle = null
                return
            }
            val p = g.current
            when (g.phase) {
                Phase.SETUP -> {
                    if (!g.players[p].setupDone) return
                    val next = g.players.indexOfFirst { !it.setupDone }
                    if (next >= 0) {
                        g.current = next
                    } else {
                        g.current = 0
                        startTurn(g)
                    }
                }
                Phase.DAWN -> {
                    if (rules(g, p).dawnOptions(g, p).isNotEmpty()) return
                    g.phase = Phase.DAY
                    val fixed = rules(g, p).actionsPerDay
                    g.turn.actionsLeft = if (fixed == null) 0 else fixed + gearCount(g, p, GearEffect.EXTRA_ACTION)
                }
                Phase.DAY -> {
                    if (g.battle != null) return
                    val options = dayOptions(g, p)
                    if (options.size == 1 && options[0] == EndDay) {
                        endDay(g, p)
                        continue
                    }
                    return
                }
                Phase.DUSK -> {
                    if (g.players[p].hand.size > HAND_LIMIT) return
                    g.current = (p + 1) % g.players.size
                    if (g.current == 0) g.round++
                    startTurn(g)
                }
                Phase.OVER -> return
            }
        }
    }

    private fun startTurn(g: GameState) {
        val p = g.current
        val pl = g.players[p]
        g.turn = Turn()
        g.phase = Phase.DAWN
        log(g, "Round ${g.round}, ${pl.faction.display} to play.")
        pl.dominance?.let { card ->
            if (dominanceMet(g, p, card)) win(g, p, "${pl.faction.display} win by Dominance: ${Cards[card].describe().substringAfter(": ")}")
        }
        rules(g, p).beginTurn(g, p)
    }

    private fun endDay(g: GameState, p: Int) {
        val r = rules(g, p)
        r.dusk(g, p)
        if (g.winner != null) return
        val n = 1 + r.extraDraws(g, p) + gearCount(g, p, GearEffect.EXTRA_DRAW)
        draw(g, p, n)
        log(g, "${g.players[p].faction.display}: draw $n card${if (n == 1) "" else "s"}.")
        g.phase = Phase.DUSK
    }

    // ---- Rule ------------------------------------------------------------------------------

    fun presence(g: GameState, p: Int, reef: Int): Int {
        val f = g.players[p].faction
        val rs = g.reefs[reef]
        return rules(f).warriorWeight * rs.warriors(f) + rs.buildingsOf(f)
    }

    /** The player who rules [reef], or null if nobody does (empty, or a tie for most). */
    fun ruler(g: GameState, reef: Int): Int? {
        var best = -1
        var bestValue = 0
        var tied = false
        for (p in g.players.indices) {
            val v = presence(g, p, reef)
            if (v > bestValue) {
                best = p; bestValue = v; tied = false
            } else if (v == bestValue && v > 0) {
                tied = true
            }
        }
        return if (best >= 0 && !tied) best else null
    }

    fun ruledBy(g: GameState, reef: Int, p: Int): Boolean = ruler(g, reef) == p

    fun freeSlots(g: GameState, reef: Int): Int = Board.reefs[reef].slots - g.reefs[reef].buildings().size

    // ---- Attacks ---------------------------------------------------------------------------

    /** Battle options for [p]: any reef where it has warriors and another faction has pieces. */
    fun battleOptions(g: GameState, p: Int): List<Option> {
        val f = g.players[p].faction
        val out = mutableListOf<Option>()
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            if (rs.warriors(f) == 0) continue
            for (q in g.players.indices) {
                if (q == p) continue
                val fq = g.players[q].faction
                if (rs.warriors(fq) > 0 || rs.pieces.any { it.owner == fq }) out += Battle(reef, fq)
            }
        }
        return out
    }

    fun ambushCards(g: GameState, p: Int, reef: Int): List<Int> =
        g.players[p].hand.filter { val c = Cards[it]; c.kind == CardKind.AMBUSH && (c.suit == Suit.MOON || c.suit == g.suitOf(reef)) }

    fun startBattle(g: GameState, a: Int, reef: Int, defender: FactionId) {
        val d = g.player(defender)
        log(g, "${g.players[a].faction.display}: battle ${defender.display} in ${Board.name(reef)}.")
        if (ambushCards(g, d, reef).isNotEmpty()) {
            g.battle = PendingBattle(a, d, reef)
            return
        }
        resolveBattle(g, a, d, reef, null)
    }

    private fun resolveBattle(g: GameState, a: Int, d: Int, reef: Int, ambush: Int?) {
        val fa = g.players[a].faction
        val fd = g.players[d].faction
        val rs = g.reefs[reef]
        if (ambush != null) {
            discardFromHand(g, d, ambush)
            log(g, "${fd.display}: ambush with ${Cards[ambush].name}.")
            removeHits(g, a, reef, 2, by = d)
            if (g.winner != null) return
            if (rs.warriors(fa) == 0) {
                log(g, "The ambush stops the attack before it starts.")
                return
            }
        }
        val r1 = g.nextInt(4)
        val r2 = g.nextInt(4)
        val attackerCap = rules(fa).warriorWeight * rs.warriors(fa)
        val defenderWarriors = rs.warriors(fd)
        val defenderCap = rules(fd).warriorWeight * defenderWarriors + rules(fd).defenseCapBonus(g, d, reef)
        var attackerHits = min(max(r1, r2), attackerCap)
        val defenderHits = min(min(r1, r2), defenderCap)
        if (defenderWarriors == 0) attackerHits += 1
        attackerHits += gearCount(g, a, GearEffect.BATTLE_HIT)
        attackerHits = max(0, attackerHits - gearCount(g, d, GearEffect.DEFENSE))
        log(g, "Dice: $r1 and $r2. Hits: ${fa.display} $attackerHits, ${fd.display} $defenderHits.")
        removeHits(g, d, reef, attackerHits, by = a)
        removeHits(g, a, reef, defenderHits, by = d)
    }

    /**
     * Deals [hits] to [victim]'s pieces in [reef]: warriors first, then tokens, then buildings.
     * Every enemy building or token removed scores 1 VP for [by]. Returns the warriors removed.
     */
    fun removeHits(g: GameState, victim: Int, reef: Int, hits: Int, by: Int): Int {
        if (hits <= 0) return 0
        val f = g.players[victim].faction
        val r = rules(f)
        val rs = g.reefs[reef]
        var left = hits
        val w = min(left, rs.warriors(f))
        if (w > 0) {
            rs.addWarriors(f, -w)
            r.warriorsRemoved(g, victim, reef, w)
            left -= w
            log(g, "${f.display}: lose $w ${r.warriorNoun(w)} in ${Board.name(reef)}.")
        }
        while (left > 0) {
            var idx = rs.pieces.indexOfFirst { it.owner == f && !it.type.building }
            if (idx < 0) idx = rs.pieces.indexOfFirst { it.owner == f && it.type.building }
            if (idx < 0) break
            val piece = rs.pieces.removeAt(idx)
            r.pieceRemoved(g, victim, piece)
            left--
            log(g, "${g.players[by].faction.display}: destroy ${possessive(f.display)} ${piece.type.label} in ${Board.name(reef)}.")
            scoreVp(g, by, 1, "destroying ${piece.type.label}")
        }
        if (w > 0) placeBlood(g, reef)
        return w
    }

    /** While sharks are in the game, any attack that removes a warrior leaves Blood, one per reef. */
    private fun placeBlood(g: GameState, reef: Int) {
        val rs = g.reefs[reef]
        if (!g.inGame(FactionId.SHARKS) || g.blood == 0 || rs.has(PieceType.BLOOD)) return
        rs.pieces.add(Piece(null, PieceType.BLOOD))
        g.blood--
        log(g, "Blood in the water at ${Board.name(reef)}.")
    }

    // ---- Cards, crafting, scoring ----------------------------------------------------------

    fun draw(g: GameState, p: Int, n: Int) {
        repeat(n) {
            if (g.drawPile.isEmpty()) {
                if (g.discard.isEmpty()) return
                g.drawPile.addAll(g.discard)
                g.discard.clear()
                shuffle(g, g.drawPile)
            }
            g.players[p].hand.add(g.drawPile.removeAt(g.drawPile.lastIndex))
        }
    }

    fun discardFromHand(g: GameState, p: Int, card: Int) {
        check(g.players[p].hand.remove(card)) { "Card $card is not in hand" }
        g.discard.add(card)
    }

    fun gearCount(g: GameState, p: Int, effect: GearEffect): Int = g.players[p].gear.count { Cards[it].effect == effect }

    /**
     * Which reefs' buildings would pay [cost], or null if it can't be paid. Each building crafts
     * once per turn with its reef's suit; [Suit.MOON] in a cost accepts any suit.
     */
    fun craftPayment(g: GameState, p: Int, cost: List<Suit>): List<Int>? {
        val f = g.players[p].faction
        val units = mutableListOf<Pair<Int, Suit>>()
        for (reef in g.reefs.indices) {
            val free = g.reefs[reef].buildingsOf(f) - (g.turn.crafted[reef] ?: 0)
            repeat(max(0, free)) { units += reef to g.suitOf(reef) }
        }
        val used = mutableListOf<Int>()
        for (suit in cost.sortedBy { it == Suit.MOON }) {
            val i = units.indexOfFirst { suit == Suit.MOON || it.second == suit }
            if (i < 0) return null
            used += units.removeAt(i).first
        }
        return used
    }

    fun scoreVp(g: GameState, p: Int, n: Int, why: String) {
        if (n <= 0 || g.winner != null) return
        val pl = g.players[p]
        if (pl.dominance != null) {
            log(g, "${pl.faction.display}: no VP for $why while playing Dominance.")
            return
        }
        pl.vp += n
        log(g, "${pl.faction.display}: +$n VP for $why (${pl.vp} VP).")
        if (pl.vp >= WIN_VP) win(g, p, "${pl.faction.display} reach $WIN_VP VP.")
    }

    fun dominanceMet(g: GameState, p: Int, card: Int): Boolean {
        val suit = Cards[card].suit
        return if (suit == Suit.MOON) {
            Board.oppositeGates.any { (a, b) -> ruledBy(g, a, p) && ruledBy(g, b, p) }
        } else {
            g.reefs.indices.count { g.suitOf(it) == suit && ruledBy(g, it, p) } >= 3
        }
    }

    private fun win(g: GameState, p: Int, text: String) {
        if (g.winner != null) return
        g.winner = p
        g.winText = text
        log(g, text)
    }

    fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

    fun log(g: GameState, line: String) {
        g.log.add(line)
        if (g.log.size > 400) g.log.subList(0, g.log.size - 400).clear()
    }

    private fun shuffle(g: GameState, list: MutableList<Int>) {
        for (i in list.lastIndex downTo 1) {
            val j = g.nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }
}
