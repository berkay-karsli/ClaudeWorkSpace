package com.reef.engine

import kotlin.random.Random

/**
 * Bots look one choice ahead: they try every option on a copy of the game, score the result from
 * their own point of view, and take the best. Choices that roll dice are tried several times and
 * averaged. Each faction has its own sense of a good position in [FactionRules.value].
 */
class Bot(private val random: Random = Random.Default, private val samples: Int = 4) {

    fun choose(g: GameState, d: Decision): Option {
        val options = usable(g, d)
        if (options.size == 1) return options[0]
        var best = options[0]
        var bestValue = Double.NEGATIVE_INFINITY
        for (o in options) {
            val tries = if (o.random) samples else 1
            var total = 0.0
            repeat(tries) { i ->
                val copy = g.deepCopy()
                if (o.random) copy.rng = g.rng + 7919L * (i + 1)
                Game.apply(copy, o)
                defendPlainly(copy, d.player)
                payUp(copy, d.player)
                endDay(copy, d.player)
                total += evaluate(copy, d.player)
            }
            // A little noise breaks ties, so bots don't always make the same opening. Swapping
            // orders must actually help, or dozens of equal swaps would crowd out doing nothing.
            val value = total / tries + random.nextDouble() * 0.01 - (if (o is Rewire) 0.05 else 0.0)
            if (value > bestValue) {
                best = o
                bestValue = value
            }
        }
        return best
    }

    /**
     * Ending the Day brings the Dusk (scoring, a card), so an action is judged as "do this, then
     * end the Day". Otherwise ending the Day would always look better than any single action.
     */
    private fun endDay(g: GameState, p: Int) {
        if (g.phase != Phase.DAY || g.current != p || g.pending.isNotEmpty()) return
        val d = Game.decision(g) ?: return
        if (EndDay in d.options) Game.apply(g, EndDay)
    }

    /** An attack is judged by how it turns out, assuming the defender fights without an ambush. */
    private fun defendPlainly(g: GameState, p: Int) {
        while (true) {
            val pending = g.pending.lastOrNull() as? DefendPending ?: return
            if (pending.player == p) return
            Game.apply(g, NoAmbush)
        }
    }

    /** A shell bought is only worth judging once it is paid for: pay with the least useful cards. */
    private fun payUp(g: GameState, p: Int) {
        while (true) {
            val pending = g.pending.lastOrNull() as? PayPending ?: return
            if (pending.player != p) return
            val card = g.players[p].hand.minBy { Cards[it].vp + (if (Cards[it].kind == CardKind.AMBUSH) 3 else 0) + (if (Cards[it].suit == Suit.MOON) 2 else 0) }
            Game.apply(g, PayCard(card))
        }
    }

    /**
     * Bots only play Dominance when they already meet it with a margin a rival can't easily
     * break before their next Dawn, and while giving up VP still costs them little.
     */
    private fun usable(g: GameState, d: Decision): List<Option> {
        val p = d.player
        val ready = d.options.filterIsInstance<PlayDominance>().firstOrNull { safeDominance(g, p, it.cardId) }
        if (ready != null && g.players[p].vp <= 18) return listOf(ready)
        return cheapestCards(d.options.filter { it !is PlayDominance })
    }

    /**
     * When a card is only paid (discarded, tucked under an arm, put in the Till), any card of the
     * same suit does the same thing: keep only the option that pays with the least useful one.
     */
    private fun cheapestCards(options: List<Option>): List<Option> {
        val best = linkedMapOf<List<Any?>, Option>()
        for (o in options) {
            val c = o.card
            val key: List<Any?> = if (c == null || o is Craft) listOf(o) else listOf(o::class, o.kind, o.reefs, o.target, o.variant, o.count, Cards[c].suit)
            val old = best[key]
            if (old == null || keep(o.card!!) < keep(old.card!!)) best[key] = o
        }
        return best.values.toList()
    }

    /** How much a card is worth keeping in hand. */
    private fun keep(card: Int): Int {
        val c = Cards[card]
        return c.vp + (if (c.kind == CardKind.AMBUSH) 3 else 0) + (if (c.kind == CardKind.DOMINANCE) 1 else 0) +
            (if (c.effect != GearEffect.NONE) 1 else 0) + (if (c.suit == Suit.MOON) 2 else 0)
    }

    private fun safeDominance(g: GameState, p: Int, card: Int): Boolean {
        val needed = dominanceReefs(g, p, card) ?: return false
        return needed.all { reef -> margin(g, p, reef) >= 4 }
    }

    companion object {
        fun evaluate(g: GameState, p: Int): Double {
            g.winner?.let { return if (it == p) 1e6 else -1e6 }
            val mine = total(g, p, self = true)
            val rival = g.players.indices.filter { it != p }.maxOfOrNull { total(g, it, self = false) } ?: 0.0
            return mine - 0.8 * rival
        }

        /**
         * How good [p]'s position is. [self] adds features that only make sense for the player
         * choosing right now, such as sharks that still have to move this turn. Rivals are judged
         * only on lasting things, so states at different points of the turn compare fairly.
         */
        private fun total(g: GameState, p: Int, self: Boolean): Double {
            val pl = g.players[p]
            var v = pl.vp * 10.0
            pl.dominance?.let { card ->
                // Playing Dominance: what matters now is holding the reefs it needs.
                v += if (Game.dominanceMet(g, p, card)) 250.0 else 60.0
                val suit = Cards[card].suit
                val reefs = if (suit == Suit.MOON) Board.oppositeGates.flatMap { listOf(it.first, it.second) } else g.reefs.indices.filter { g.suitOf(it) == suit }
                v += reefs.sumOf { reef -> if (Game.ruledBy(g, reef, p)) 25.0 + minOf(margin(g, p, reef), 6) * 3.0 else 0.0 }
            }
            v += Game.rules(pl.faction).value(g, p, self)
            // Shells from the Hermit Crabs guard pieces that are under threat.
            g.state<CrabsState>(FactionId.CRABS)?.let { s ->
                for ((reef, m) in s.shells) {
                    val n = m[pl.faction] ?: 0
                    if (n > 0) v += n * 1.0 + minOf(n, Eval.threat(g, p, reef)) * 2.5
                }
            }
            return v
        }

        /** How far [p]'s presence in [reef] is ahead of the strongest rival's. */
        fun margin(g: GameState, p: Int, reef: Int): Int =
            Game.presence(g, p, reef) - (g.players.indices.filter { it != p }.maxOfOrNull { Game.presence(g, it, reef) } ?: 0)

        /** The reefs [p] rules that satisfy Dominance [card], or null if it isn't met. */
        fun dominanceReefs(g: GameState, p: Int, card: Int): List<Int>? {
            val suit = Cards[card].suit
            return if (suit == Suit.MOON) {
                Board.oppositeGates.firstOrNull { (a, b) -> Game.ruledBy(g, a, p) && Game.ruledBy(g, b, p) }?.toList()
            } else {
                g.reefs.indices.filter { g.suitOf(it) == suit && Game.ruledBy(g, it, p) }.sortedByDescending { margin(g, p, it) }.take(3).takeIf { it.size == 3 }
            }
        }
    }
}
