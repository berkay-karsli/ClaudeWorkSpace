package com.reef.engine

object CrabsRules : FactionRules {
    override val id = FactionId.CRABS
    override val setupHint = "1 market and 4 crabs in any reef."
    const val SETUP = 4
    const val RECRUIT = 3
    const val SHELLS = 8

    override fun warriorNoun(n: Int) = if (n == 1) "crab" else "crabs"
    override fun newState(): FactionState = CrabsState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as CrabsState

    override fun dawnHint(g: GameState, p: Int) = "put cards from your hand into your Till. Each Day action costs one Till card."
    override fun duskHint(g: GameState, p: Int) = "set your shell price for the next round, then score 1 VP per market."

    fun price(g: GameState): Int = g.state<CrabsState>(id)?.price ?: 1

    fun shellsOn(s: CrabsState, reef: Int, f: FactionId): Int = s.shells[reef]?.get(f) ?: 0

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { Game.freeSlots(g, it.id) > 0 }.map { PlaceSetup(it.id) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        val s = st(g, p)
        g.reefs[reef].pieces.add(Piece(id, PieceType.MARKET))
        s.markets--
        place(g, p, reef, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Hermit Crabs: open shop in ${Board.name(reef)}.")
    }

    /** Shells are rented: at your Dawn every shell comes back to the pool. */
    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.tillDone = false
        s.priceSet = false
        val back = s.shells.values.sumOf { it.values.sum() }
        if (back > 0) {
            s.shells.clear()
            s.pool += back
            Game.log(g, "Hermit Crabs: $back shell${if (back == 1) " comes" else "s come"} back to the pool.")
        }
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        if (st(g, p).tillDone || g.players[p].hand.isEmpty()) return emptyList()
        return g.players[p].hand.map { AddToTill(it) } + Done("Start the Day")
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is AddToTill -> {
                g.players[p].hand.remove(o.cardId)
                s.till += o.cardId
                Game.log(g, "Hermit Crabs: put ${Cards[o.cardId].name} in the Till (${s.till.size}).")
            }
            else -> s.tillDone = true
        }
    }

    override fun canAct(g: GameState, p: Int) = st(g, p).till.isNotEmpty()

    /** Spends the Till card least likely to pay for a market. */
    override fun payAction(g: GameState, p: Int) {
        val s = st(g, p)
        val wanted = marketSuits(g, p)
        val card = s.till.minBy { c ->
            val suit = Cards[c].suit
            (if (suit == Suit.MOON) 10 else 0) + (if (suit in wanted) 5 else 0) + Cards[c].vp
        }
        s.till.remove(card)
        g.discard += card
    }

    private fun marketSuits(g: GameState, p: Int): Set<Suit> =
        if (st(g, p).markets == 0) emptySet()
        else g.reefs.indices.filter { Game.ruledBy(g, it, p) && Game.freeSlots(g, it) > 0 }.map { g.suitOf(it) }.toSet()

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        out += Game.standardMoves(g, p)
        out += Game.battleOptions(g, p)
        if (s.supply > 0) out += recruitReefs(g).map { Recruit(it) }
        return out
    }

    /** The vacancy chain: when another faction's building goes from a reef with crabs, a market moves in. */
    fun vacancy(g: GameState, reef: Int, removed: Piece) {
        val s = g.state<CrabsState>(id) ?: return
        if (removed.owner == id || g.reefs[reef].warriors(id) == 0 || s.markets == 0 || Game.freeSlots(g, reef) <= 0) return
        g.reefs[reef].pieces.add(Piece(id, PieceType.MARKET))
        s.markets--
        Game.log(g, "Hermit Crabs: a vacancy in ${Board.name(reef)}! The crabs move a market in.")
    }

    fun recruitReefs(g: GameState): List<Int> {
        val markets = g.reefs.indices.filter { g.reefs[it].count(PieceType.MARKET, id) > 0 }
        return markets.ifEmpty { Board.gates }
    }

    override fun freeDayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        if (s.markets > 0) {
            for (reef in g.reefs.indices) {
                if (!Game.ruledBy(g, reef, p) || Game.freeSlots(g, reef) <= 0) continue
                for (c in s.till.distinct()) if (Cards[c].suit == Suit.MOON || Cards[c].suit == g.suitOf(reef)) out += BuildMarket(reef, c)
            }
        }
        if (s.pool > 0) {
            for (reef in g.reefs.indices) if (g.reefs[reef].warriors(id) > 0) out += WearShell(reef)
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Move -> {
                Game.log(g, "Hermit Crabs: move ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            is Recruit -> {
                val n = place(g, p, o.reef, RECRUIT)
                Game.log(g, "Hermit Crabs: $n ${warriorNoun(n)} join at ${Board.name(o.reef)}.")
            }
            is BuildMarket -> {
                s.till.remove(o.cardId)
                g.discard += o.cardId
                g.reefs[o.reef].pieces.add(Piece(id, PieceType.MARKET))
                s.markets--
                s.marketsBuilt++
                Game.log(g, "Hermit Crabs: build a market in ${Board.name(o.reef)}.")
            }
            is WearShell -> {
                s.pool--
                addShell(s, o.reef, id)
                Game.log(g, "Hermit Crabs: the crabs in ${Board.name(o.reef)} put on a shell.")
            }
            else -> error("Hermit Crabs can't ${o.describe()}")
        }
    }

    private fun addShell(s: CrabsState, reef: Int, f: FactionId) {
        val m = s.shells.getOrPut(reef) { mutableMapOf() }
        m[f] = (m[f] ?: 0) + 1
    }

    // ---- The shell shop, used by the shared rules ----------------------------------------------

    /** Shells [p] could buy right now: one option per reef where [p] has pieces. */
    fun shopOptions(g: GameState, p: Int): List<Option> {
        val s = g.state<CrabsState>(id) ?: return emptyList()
        val f = g.players[p].faction
        if (f == id || s.pool == 0 || g.players[p].hand.size < s.price) return emptyList()
        return g.reefs.indices.filter { g.reefs[it].warriors(f) > 0 || g.reefs[it].pieces.any { pc -> pc.owner == f } }.map { BuyShell(it) }
    }

    /** A card paid for a shell goes into the Till. */
    fun receive(g: GameState, card: Int) {
        g.state<CrabsState>(id)!!.till += card
    }

    fun sell(g: GameState, buyer: Int, reef: Int) {
        val s = g.state<CrabsState>(id)!!
        s.pool--
        addShell(s, reef, g.players[buyer].faction)
        Game.log(g, "${g.players[buyer].faction.display}: buy a shell for ${Board.name(reef)}.")
    }

    /** Shells on [f]'s pieces in [reef] ignore hits, one hit each. Returns the hits left. */
    fun absorb(g: GameState, f: FactionId, reef: Int, hits: Int): Int {
        val s = g.state<CrabsState>(id) ?: return hits
        val m = s.shells[reef] ?: return hits
        val have = m[f] ?: 0
        val used = minOf(have, hits)
        if (used == 0) return hits
        if (have == used) m.remove(f) else m[f] = have - used
        if (m.isEmpty()) s.shells.remove(reef)
        s.pool += used
        Game.log(g, "${f.display}: ${if (used == 1) "a shell ignores 1 hit" else "shells ignore $used hits"} in ${Board.name(reef)}.")
        return hits - used
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        s.price = (o as SetPrice).price
        s.priceSet = true
        Game.log(g, "Hermit Crabs: shells now cost ${s.price} card${if (s.price == 1) "" else "s"}.")
    }

    override fun duskAuto(g: GameState, p: Int) {
        val n = g.reefs.sumOf { it.count(PieceType.MARKET, id) }
        Game.scoreVp(g, p, n, "$n market${if (n == 1) "" else "s"}")
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> =
        if (st(g, p).priceSet) emptyList() else (1..3).map { SetPrice(it) }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).supply += n
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.MARKET) st(g, p).markets++
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
        return "${s.till.size} in the Till · ${s.pool} shells in the pool at ${s.price} · ${s.supply} crabs and ${s.markets} markets left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = s.till.size * 2.6 + Eval.onMap(g, id) * 2.0
        for (reef in g.reefs.indices) {
            val markets = g.reefs[reef].count(PieceType.MARKET, id)
            v += markets * 6.0
            if (markets > 0) v -= minOf(Eval.threat(g, p, reef), markets * 2) * 0.8
            if (Game.ruledBy(g, reef, p) && Game.freeSlots(g, reef) > 0 && s.markets > 0) v += 1.5
        }
        // Shells in the pool are sales waiting to happen; a price that the others can pay sells more.
        val buyers = g.players.indices.count { it != p && g.players[it].hand.size >= s.price }
        v += minOf(s.pool, buyers * 2) * (0.6 + s.price * 0.5)
        return v + Eval.hand(g, p, 1.2)
    }
}
