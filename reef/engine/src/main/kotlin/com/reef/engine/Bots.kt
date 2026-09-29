package com.reef.engine

import kotlin.random.Random

/**
 * Bots look one choice ahead: they try every option on a copy of the game, score the result from
 * their own point of view, and take the best. Choices that roll dice are tried several times and
 * averaged. Each faction has its own sense of a good position in [factionValue].
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
                total += evaluate(copy, d.player)
            }
            // A little noise breaks ties, so bots don't always make the same opening.
            val value = total / tries + random.nextDouble() * 0.01
            if (value > bestValue) {
                best = o
                bestValue = value
            }
        }
        return best
    }

    /**
     * Bots only play Dominance when they already meet it with a margin a rival can't easily
     * break before their next Dawn, and while giving up VP still costs them little.
     */
    private fun usable(g: GameState, d: Decision): List<Option> {
        val p = d.player
        val ready = d.options.filterIsInstance<PlayDominance>().firstOrNull { safeDominance(g, p, it.cardId) }
        if (ready != null && g.players[p].vp <= 18) return listOf(ready)
        return d.options.filter { it !is PlayDominance }
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
            v += when (pl.faction) {
                FactionId.SHARKS -> sharks(g, p, self)
                FactionId.CORAL -> coral(g, p)
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

        private fun sharks(g: GameState, p: Int, self: Boolean): Double {
            val s = g.players[p].fs as SharksState
            val f = FactionId.SHARKS
            var v = 0.0
            val onMap = g.reefs.sumOf { it.warriors(f) }
            v += onMap * 9.0 + s.supply * 2.0
            if (self && g.current == p && g.phase == Phase.DAY) v -= (onMap - s.moved.values.sum()) * 8.0
            for (reef in g.reefs.indices) {
                val rs = g.reefs[reef]
                val here = rs.warriors(f)
                val reach = g.reefs.indices.any { g.reefs[it].warriors(f) > 0 && Board.distance(it, reef) <= 1 }
                if (rs.has(PieceType.BLOOD)) {
                    v += when {
                        here > 0 -> 7.0
                        g.reefs.indices.any { g.reefs[it].warriors(f) > 0 && Board.distance(it, reef) <= 2 } -> 2.5
                        else -> 0.0
                    }
                }
                // Buildings within reach are points waiting to be taken.
                if (reach) v += g.players.indices.filter { it != p }.sumOf { g.reefs[reef].buildingsOf(g.players[it].faction) } * 0.8
            }
            for (c in g.players[p].hand) v += if (Cards[c].kind == CardKind.DOMINANCE) 0.5 else 1.5
            return v
        }

        private fun coral(g: GameState, p: Int): Double {
            val f = FactionId.CORAL
            var v = 0.0
            val growable = mutableSetOf<Suit>()
            val spawnable = mutableSetOf<Suit>()
            for (reef in g.reefs.indices) {
                val rs = g.reefs[reef]
                val coral = rs.buildingsOf(f)
                v += coral * 5.0 + rs.warriors(f) * 1.5
                if (coral > 0) spawnable += g.suitOf(reef)
                if (Game.ruledBy(g, reef, p)) {
                    v += 2.0
                    if (Game.freeSlots(g, reef) > 0) {
                        v += 2.0
                        growable += g.suitOf(reef)
                    }
                }
                // Coral that enemies can reach is coral at risk.
                if (coral > 0) {
                    val threat = g.players.indices.filter { it != p }.sumOf { q ->
                        val fq = g.players[q].faction
                        rs.warriors(fq) + Board.neighbors(reef).sumOf { g.reefs[it].warriors(fq) } / 2
                    }
                    v -= minOf(threat, coral) * 1.5
                }
            }
            if (spawnable.isEmpty()) spawnable += Suit.entries
            for (c in g.players[p].hand) {
                val card = Cards[c]
                v += when {
                    card.kind == CardKind.DOMINANCE -> 0.5
                    card.suit == Suit.MOON -> 3.5
                    card.suit in growable -> 3.0
                    card.suit in spawnable -> 2.5
                    else -> 1.5
                }
                if (card.kind == CardKind.GEAR) v += card.vp * 0.5
            }
            return v
        }
    }
}
