package com.reef.engine

object JellyfishRules : FactionRules {
    override val id = FactionId.JELLYFISH
    override val setupHint = "3 jellyfish in each of two reefs on the Gyre."
    const val SETUP = 3
    const val SWARM = 3
    const val BIG_SWARM = 6
    const val FROM_NOTHING = 3
    const val CURRENTS_PER_TURN = 2
    const val BLOOMS_PER_TURN = 2
    const val CYST_HATCH = 2
    val gyre = listOf(5, 6, 10, 9)

    override fun warriorNoun(n: Int) = if (n == 1) "jellyfish" else "jellyfish"
    override fun newState(): FactionState = JellyfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as JellyfishState

    override fun duskHint(g: GameState, p: Int) = "choose which current carries each group of jellyfish."

    fun cystAt(g: GameState, reef: Int): Piece? = g.reefs[reef].pieces.firstOrNull { it.type == PieceType.CYST && it.owner == id }

    override fun setupOptions(g: GameState, p: Int): List<Option> {
        if (g.players[p].setupDone) return emptyList()
        val first = st(g, p).setupFirst
        return gyre.filter { it != first }.map { PlaceSetup(it) }
    }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        place(g, p, reef, SETUP)
        val s = st(g, p)
        if (s.setupFirst == null) s.setupFirst = reef else g.players[p].setupDone = true
        Game.log(g, "Jellyfish: $SETUP jellyfish drift into ${Board.name(reef)}.")
    }

    /** At Dawn every cyst becomes 2 jellyfish. */
    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.drift.clear()
        s.driftPlanned = false
        for (reef in g.reefs.indices) {
            val cyst = cystAt(g, reef) ?: continue
            g.reefs[reef].pieces.remove(cyst)
            s.cysts++
            val n = place(g, p, reef, CYST_HATCH)
            Game.log(g, "Jellyfish: the cyst in ${Board.name(reef)} grows back into $n jellyfish.")
        }
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        if (g.turn.used("current") < CURRENTS_PER_TURN) {
            for ((a, b) in Board.channels) {
                if (Board.isCurrent(a, b) || Board.isCurrent(b, a)) continue
                val marker = g.markers.firstOrNull { it.joins(a, b) }
                if (marker != null && !(marker.type == MarkerType.CURRENT && marker.owner == id)) continue
                if (marker?.from != a) out += SetCurrent(a, b)
                if (marker?.from != b) out += SetCurrent(b, a)
            }
        }
        if (g.turn.used("bloom") < BLOOMS_PER_TURN && st(g, p).supply > 0) {
            val anyOnMap = Eval.onMap(g, id) > 0
            for (c in g.players[p].hand) {
                val suit = Cards[c].suit
                val reefs = g.reefs.indices.filter { suit == Suit.MOON || g.suitOf(it) == suit }
                if (anyOnMap) {
                    if (reefs.any { g.reefs[it].warriors(id) > 0 }) out += Bloom(c)
                } else {
                    for (r in reefs) out += Bloom(c, r)
                }
            }
        }
        if (g.turn.used("pulse") == 0) {
            for (reef in g.reefs.indices) if (g.reefs[reef].warriors(id) >= SWARM) out += Game.battleTargets(g, p, reef).map { Battle(reef, it) }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Battle -> {
                g.turn.use("pulse")
                Game.log(g, "Jellyfish: the swarm in ${Board.name(o.reef)} pulses.")
                Game.startBattle(g, p, o.reef, o.defender)
            }
            is SetCurrent -> {
                g.turn.use("current")
                val existing = g.markers.firstOrNull { it.joins(o.from, o.to) }
                if (existing != null) {
                    g.markers.remove(existing)
                } else if (s.arrows > 0) {
                    s.arrows--
                } else {
                    // All four arrows are out: the oldest one moves here.
                    g.markers.remove(g.markers.filter { it.type == MarkerType.CURRENT && it.owner == id }.minBy { it.seq })
                }
                g.markers += ChannelMarker(o.from, o.to, MarkerType.CURRENT, id, from = o.from, seq = ++g.markerSeq)
                Game.log(g, "Jellyfish: a current now flows from ${Board.name(o.from)} to ${Board.name(o.to)}.")
            }
            is Bloom -> {
                g.turn.use("bloom")
                Game.discardFromHand(g, p, o.cardId)
                if (o.into != null) {
                    val n = place(g, p, o.into, FROM_NOTHING)
                    Game.log(g, "Jellyfish: $n jellyfish bloom from nothing in ${Board.name(o.into)}.")
                } else {
                    val suit = Cards[o.cardId].suit
                    val reefs = g.reefs.indices.filter { g.reefs[it].warriors(id) > 0 && (suit == Suit.MOON || g.suitOf(it) == suit) }
                    var n = 0
                    for (r in reefs) n += place(g, p, r, 1)
                    Game.log(g, "Jellyfish: bloom, $n new jellyfish.")
                }
            }
            else -> error("Jellyfish can't ${o.describe()}")
        }
    }

    /** Where jellyfish in [reef] can drift: along any current leading out of it. */
    fun outlets(g: GameState, reef: Int): List<Int> = Game.neighbors(g, reef, id).filter { Game.isCurrent(g, reef, it) }

    override fun onDuskStart(g: GameState, p: Int) {
        val s = st(g, p)
        s.drift.clear()
        for (reef in g.reefs.indices) {
            if (g.reefs[reef].warriors(id) == 0) continue
            val out = outlets(g, reef)
            if (out.size == 1) s.drift[reef] = out[0] else if (out.size > 1) s.drift[reef] = -1
        }
        s.driftPlanned = true
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val open = s.drift.entries.firstOrNull { it.value == -1 } ?: return emptyList()
        return outlets(g, open.key).map { Drift(open.key, it) }
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val d = o as Drift
        st(g, p).drift[d.from] = d.to
    }

    override fun duskAuto(g: GameState, p: Int) {
        val s = st(g, p)
        // Every group drifts at once, so no jellyfish drifts twice.
        val moves = s.drift.filter { it.value >= 0 }.map { (from, to) -> Triple(from, to, g.reefs[from].warriors(id)) }.filter { it.third > 0 }
        for ((from, _, n) in moves) g.reefs[from].addWarriors(id, -n)
        for ((_, to, n) in moves) g.reefs[to].addWarriors(id, n)
        for ((from, to, n) in moves) {
            Game.log(g, "Jellyfish: $n drift from ${Board.name(from)} to ${Board.name(to)}.")
            Game.afterArrive(g, p, from, to, n)
        }
        s.drift.clear()
        val swarms = g.reefs.count { it.warriors(id) >= SWARM }
        val big = g.reefs.count { it.warriors(id) >= BIG_SWARM }
        Game.scoreVp(g, p, swarms + big, "$swarms swarm${if (swarms == 1) "" else "s"}" + (if (big > 0) ", $big of them huge" else ""))
    }

    /** When the last jellyfish in a reef is removed, a cyst stays behind. */
    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        g.reefs[reef].addWarriors(id, -n)
        s.supply += n
        if (g.reefs[reef].warriors(id) == 0 && s.cysts > 0 && cystAt(g, reef) == null) {
            g.reefs[reef].pieces.add(Piece(id, PieceType.CYST))
            s.cysts--
            Game.log(g, "Jellyfish: the last jellyfish in ${Board.name(reef)} shrinks into a cyst.")
        }
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.CYST) st(g, p).cysts++
    }

    /** Each reef with 3 or more jellyfish pays its suit. */
    override fun craftUnits(g: GameState, p: Int) = reefUnits(g, g.reefs.indices.filter { g.reefs[it].warriors(id) >= SWARM })

    private fun place(g: GameState, p: Int, reef: Int, n: Int): Int {
        val k = minOf(n, st(g, p).supply)
        if (k > 0) {
            g.reefs[reef].addWarriors(id, k)
            st(g, p).supply -= k
        }
        return k
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return "${s.supply} jellyfish, ${s.arrows} current arrows and ${s.cysts} cysts left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        var v = 0.0
        for (reef in g.reefs.indices) {
            val n = g.reefs[reef].warriors(id)
            if (n == 0) continue
            v += n * 1.2
            if (n >= SWARM) v += 6.0
            if (n >= 2) v += Eval.enemyPresence(g, p, reef).coerceAtMost(4) * 0.6
        }
        v += g.reefs.indices.count { cystAt(g, it) != null } * 3.0
        return v + Eval.hand(g, p, 1.6)
    }
}
