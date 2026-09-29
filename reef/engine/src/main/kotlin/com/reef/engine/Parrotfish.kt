package com.reef.engine

object ParrotfishRules : FactionRules {
    override val id = FactionId.PARROTFISH
    override val actionsPerDay = 3
    override val setupHint = "place 4 parrotfish at any gate."
    const val SETUP = 4
    const val ARRIVALS = 2
    const val SANDBAR_COST = 2
    const val ISLAND_COST = 4
    const val MAX_ISLANDS = 3
    const val ISLAND_VP = 3
    const val ISLAND_DUSK_VP = 1
    const val SAND_PER_SUIT = 3

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
        val cocooned = cocoonAt(g)
        out += Game.standardMoves(g, p).filter { (it as Move).from != cocooned }
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
                Game.removePiece(g, o.reef, rs.pieces.first { it.owner == o.owner && it.type == o.type })
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
                Game.scoreVp(g, p, ISLAND_VP, "raising an island")
            }
            else -> error("Parrotfish can't ${o.describe()}")
        }
    }

    override fun duskHint(g: GameState, p: Int) = "your old cocoon opens. You may wrap the parrotfish in one reef you rule in a new one."

    /** The reef with your cocoon, if any. */
    fun cocoonAt(g: GameState): Int? = g.reefs.indices.firstOrNull { reef -> g.reefs[reef].pieces.any { it.type == PieceType.COCOON && it.owner == id } }

    override fun onDuskStart(g: GameState, p: Int) {
        val s = st(g, p)
        s.cocoonDone = false
        cocoonAt(g)?.let { reef ->
            g.reefs[reef].pieces.removeAll { it.type == PieceType.COCOON && it.owner == id }
            s.cocoon++
            Game.log(g, "Parrotfish: the cocoon in ${Board.name(reef)} opens.")
        }
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.cocoonDone || s.cocoon == 0) return emptyList()
        val reefs = g.reefs.indices.filter { g.reefs[it].warriors(id) > 0 && Game.ruledBy(g, it, p) }
        return if (reefs.isEmpty()) emptyList() else reefs.map { Cocoon(it) } + Done("No cocoon")
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        s.cocoonDone = true
        if (o is Cocoon) {
            g.reefs[o.reef].pieces.add(Piece(id, PieceType.COCOON))
            s.cocoon--
            Game.log(g, "Parrotfish: the parrotfish in ${Board.name(o.reef)} wrap themselves in slime for the night.")
        }
    }

    override fun duskAuto(g: GameState, p: Int) {
        val n = st(g, p).islands.size
        Game.scoreVp(g, p, n * ISLAND_DUSK_VP, "$n island${if (n == 1) "" else "s"}")
    }

    override fun immune(g: GameState, p: Int, reef: Int) = reef in st(g, p).islands || cocoonAt(g) == reef

    /** Islands pay their suit, and every 2 Sand pays any suit. */
    override fun craftUnits(g: GameState, p: Int): List<CraftUnit> {
        val s = st(g, p)
        return reefUnits(g, s.islands.sorted()) + List(s.sand / SAND_PER_SUIT) { CraftUnit("sand", Suit.MOON, spend = true) }
    }

    override fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {
        st(g, p).sand -= SAND_PER_SUIT
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.COCOON) st(g, p).cocoon++
    }

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
