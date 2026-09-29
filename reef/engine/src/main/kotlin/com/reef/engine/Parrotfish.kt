package com.reef.engine

object ParrotfishRules : FactionRules {
    override val id = FactionId.PARROTFISH
    override val actionsPerDay = 3
    override val setupHint = "place 4 parrotfish at any gate."
    const val SETUP = 4
    const val ARRIVALS = 2
    const val SANDBAR_COST = 2
    const val ISLAND_COST = 5
    const val MAX_ISLANDS = 3

    override fun warriorNoun(n: Int) = "parrotfish"
    override fun newState(): FactionState = ParrotfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as ParrotfishState

    override fun dawnHint(g: GameState, p: Int) = "2 parrotfish arrive in a reef you rule (or at a gate if you rule none)."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val gate = (o as PlaceSetup).reef
        place(g, p, gate, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Parrotfish: $SETUP parrotfish arrive at ${Board.name(gate)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        st(g, p).arrivals = 0
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.arrivals > 0 || s.supply == 0) return emptyList()
        val ruled = g.reefs.indices.filter { Game.ruledBy(g, it, p) }
        val n = minOf(ARRIVALS, s.supply)
        return (ruled.ifEmpty { Board.gates }).map { Arrive(it, n, "parrotfish") }
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        st(g, p).arrivals = place(g, p, a.reef, a.n).coerceAtLeast(1)
        Game.log(g, "Parrotfish: ${a.n} parrotfish arrive in ${Board.name(a.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        out += Game.standardMoves(g, p)
        out += Game.battleOptions(g, p)
        val ruled = g.reefs.indices.filter { Game.ruledBy(g, it, p) }
        for (reef in ruled) {
            val edible = g.reefs[reef].pieces.filter { it.owner != null && it.owner != id }.map { it.owner!! to it.type }.distinct()
            if (edible.isNotEmpty()) edible.forEach { (owner, type) -> out += GrazeEat(reef, owner, type) }
            else if (g.turn.used("chew:$reef") == 0) out += GrazeChew(reef)
        }
        if (s.sandbars > 0 && s.sand >= SANDBAR_COST) {
            val channels = ruled.flatMap { r -> Board.neighbors(r).map { minOf(r, it) to maxOf(r, it) } }.distinct()
            for ((a, b) in channels) if (g.markers.none { it.joins(a, b) }) out += PlaceSandbar(a, b)
        }
        if (s.islands.size < MAX_ISLANDS && s.sand >= ISLAND_COST) {
            for (reef in ruled) {
                val bars = g.markers.count { it.type == MarkerType.SANDBAR && (it.a == reef || it.b == reef) }
                if (reef !in s.islands && bars >= 2) out += RaiseIsland(reef)
            }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Move -> {
                Game.log(g, "Parrotfish: move ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            is GrazeEat -> {
                val rs = g.reefs[o.reef]
                val piece = rs.pieces.first { it.owner == o.owner && it.type == o.type }
                rs.pieces.remove(piece)
                Game.rules(o.owner).pieceRemoved(g, g.player(o.owner), piece)
                s.sand += 3
                Game.log(g, "Parrotfish: graze ${Game.possessive(o.owner.display)} ${o.type.label} in ${Board.name(o.reef)}, +3 Sand.")
                Game.scoreVp(g, p, 1, "grazing a ${o.type.label}")
            }
            is GrazeChew -> {
                g.turn.use("chew:${o.reef}")
                s.sand += 2
                Game.log(g, "Parrotfish: chew the bare reef in ${Board.name(o.reef)}, +2 Sand.")
            }
            is PlaceSandbar -> {
                s.sand -= SANDBAR_COST
                s.sandbars--
                g.markers += ChannelMarker(o.a, o.b, MarkerType.SANDBAR, id, seq = ++g.markerSeq)
                Game.log(g, "Parrotfish: a sandbar closes the channel between ${Board.name(o.a)} and ${Board.name(o.b)}.")
                Game.scoreVp(g, p, 1, "a sandbar")
            }
            is RaiseIsland -> {
                s.sand -= ISLAND_COST
                s.islands += o.reef
                Game.log(g, "Parrotfish: ${Board.name(o.reef)} rises out of the sea as an island.")
                Game.scoreVp(g, p, 2, "raising an island")
            }
            else -> error("Parrotfish can't ${o.describe()}")
        }
    }

    override fun duskHint(g: GameState, p: Int) = "score 1 VP per island."

    override fun duskAuto(g: GameState, p: Int) {
        val n = st(g, p).islands.size
        Game.scoreVp(g, p, n, "$n island${if (n == 1) "" else "s"}")
    }

    override fun immune(g: GameState, p: Int, reef: Int) = reef in st(g, p).islands

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).supply += n
    }

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
        return "${s.sand} Sand · ${s.supply} parrotfish, ${s.sandbars} sandbars and ${MAX_ISLANDS - s.islands.size} islands left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = Eval.onMap(g, id) * 2.0 + s.sand * 1.4 + s.islands.size * 14.0
        v += g.markers.count { it.type == MarkerType.SANDBAR } * 2.0
        for (reef in g.reefs.indices) {
            if (Game.ruledBy(g, reef, p)) {
                v += 1.5
                v += g.reefs[reef].pieces.count { it.owner != null && it.owner != id } * 1.5
            }
        }
        return v + Eval.hand(g, p)
    }
}
