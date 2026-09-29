package com.reef.engine

/**
 * Anglers live in the Trench, off the map. They only come onto the map for a snap, and the
 * survivors sink back as soon as it is over.
 */
object AnglersRules : FactionRules {
    override val id = FactionId.ANGLERS
    override val setupHint = "3 anglers in the Trench and 2 lures to hang at your first Dawn."
    const val TREASURE = "Treasure"
    const val SHELTER = "Shelter"
    const val GLORY = "Glory"
    val offers = listOf(TREASURE, SHELTER, GLORY)
    const val MAX_SNAP = 3
    const val SNAPS_PER_DUSK = 2
    const val SPAWN = 2
    const val SPAWNS_PER_TURN = 2

    override fun warriorNoun(n: Int) = if (n == 1) "angler" else "anglers"
    override fun newState(): FactionState = AnglersState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as AnglersState

    override fun dawnHint(g: GameState, p: Int) = "hang or move your lures and pick what each one offers."
    override fun duskHint(g: GameState, p: Int) = "snap at up to 2 lure or rim reefs with enemy warriors."

    /** Rim reefs and their neighbors. */
    val lureReefs: List<Int> = Board.reefs.filter { r -> r.rim || Board.neighbors(r.id).any { Board.reefs[it].rim } }.map { it.id }

    fun lureAt(g: GameState, reef: Int): Piece? = g.reefs[reef].pieces.firstOrNull { it.type == PieceType.LURE && it.owner == id }
    fun luresOnMap(g: GameState): Int = g.reefs.sumOf { it.count(PieceType.LURE, id) }

    /** Lures you may have out: 2, then 3 after 4 warriors eaten, 4 after 8. */
    fun unlocked(s: AnglersState): Int = 2 + (if (s.eaten >= 4) 1 else 0) + (if (s.eaten >= 8) 1 else 0)

    override fun onNewGame(g: GameState, p: Int) {
        g.players[p].setupDone = true
    }

    override fun setupOptions(g: GameState, p: Int): List<Option> = emptyList()
    override fun applySetup(g: GameState, p: Int, o: Option) {}

    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.movedLures.clear()
        s.luresDone = false
        s.snapped.clear()
        s.snapsDone = false
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.luresDone) return emptyList()
        val out = mutableListOf<Option>()
        val free = lureReefs.filter { lureAt(g, it) == null }
        if (s.lures > 0 && luresOnMap(g) < unlocked(s)) for (r in free) for (offer in offers) out += PlaceLure(r, offer)
        for (from in lureReefs) {
            val lure = lureAt(g, from) ?: continue
            if (from in s.movedLures) continue
            for (to in free + from) for (offer in offers) if (to != from || offer != lure.variant) out += MoveLure(from, to, offer)
        }
        return if (out.isEmpty()) out else out + Done("Start the Day")
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is PlaceLure -> {
                s.lures--
                g.reefs[o.reef].pieces.add(Piece(id, PieceType.LURE, variant = o.offer))
                s.movedLures += o.reef
                Game.log(g, "Anglerfish: hang a ${o.offer} lure over ${Board.name(o.reef)}.")
            }
            is MoveLure -> {
                g.reefs[o.from].pieces.remove(lureAt(g, o.from))
                g.reefs[o.to].pieces.add(Piece(id, PieceType.LURE, variant = o.offer))
                s.movedLures += o.to
                Game.log(g, if (o.from == o.to) "Anglerfish: the lure over ${Board.name(o.to)} now offers ${o.offer}." else "Anglerfish: move a lure to ${Board.name(o.to)}, offering ${o.offer}.")
            }
            else -> s.luresDone = true
        }
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (g.turn.used("spawn") >= SPAWNS_PER_TURN || s.supply == 0) return emptyList()
        return g.players[p].hand.map { SpawnAnglers(it) }
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        val c = (o as SpawnAnglers).cardId
        g.turn.use("spawn")
        Game.discardFromHand(g, p, c)
        val n = minOf(SPAWN, s.supply)
        s.supply -= n
        s.trench += n
        Game.log(g, "Anglerfish: discard ${Cards[c].name}, $n ${warriorNoun(n)} join the Trench (${s.trench}).")
    }

    /** Reefs where a snap can rise: lure reefs and rim reefs. */
    fun snapReefs(g: GameState): List<Int> = g.reefs.indices.filter { lureAt(g, it) != null || Board.reefs[it].rim }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.snapsDone || s.trench == 0 || s.snapped.size >= SNAPS_PER_DUSK) return emptyList()
        val out = mutableListOf<Option>()
        for (reef in snapReefs(g)) {
            if (reef in s.snapped) continue
            for (prey in Game.battleTargets(g, p, reef)) {
                if (g.reefs[reef].warriors(prey) == 0) continue
                for (n in minOf(MAX_SNAP, s.trench) downTo 1) out += Snap(reef, prey, n)
            }
        }
        return if (out.isEmpty()) out else out + Done("Stop snapping")
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Snap -> {
                s.snapped += o.reef
                s.trench -= o.n
                Game.log(g, "Anglerfish: ${o.n} ${warriorNoun(o.n)} rise from the Trench at ${Board.name(o.reef)}.")
                Game.startBattle(g, p, o.reef, o.prey, snap = o.n)
            }
            else -> s.snapsDone = true
        }
    }

    /** After a snap, surviving anglers sink back into the Trench. */
    fun sinkBack(g: GameState, reef: Int) {
        val s = g.state<AnglersState>(id) ?: return
        val n = g.reefs[reef].warriors(id)
        if (n == 0) return
        g.reefs[reef].addWarriors(id, -n)
        s.trench += n
        RemorasRules.dropOff(g, id, reef)
    }

    /** Snaps score 1 VP per enemy warrior they remove, and count toward unlocking lures. */
    fun eat(g: GameState, p: Int, n: Int) {
        val s = st(g, p)
        val before = unlocked(s)
        s.eaten += n
        Game.scoreVp(g, p, n, "eating $n ${if (n == 1) "warrior" else "warriors"}")
        if (unlocked(s) > before) Game.log(g, "Anglerfish: a new lure unlocks (${unlocked(s)} in all).")
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).supply += n
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.LURE) st(g, p).lures++
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return "${s.trench} in the Trench · ${luresOnMap(g)} of ${unlocked(s)} lures out · ${s.eaten} eaten"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = s.trench * 3.0 + s.supply * 0.3 + s.eaten * 0.5
        for (reef in snapReefs(g)) {
            val prey = g.players.indices.filter { it != p }.sumOf { g.reefs[reef].warriors(g.players[it].faction) }
            v += minOf(prey, 4) * 1.2
            lureAt(g, reef)?.let { v += 3.0 }
        }
        return v + Eval.hand(g, p, 1.8)
    }
}
