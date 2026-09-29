package com.reef.engine

object StarfishRules : FactionRules {
    override val id = FactionId.STARFISH
    override val actionsPerDay = 2
    override val canDig = true
    override val setupHint = "3 starfish in each of two neighboring reefs that aren't gates."
    const val SETUP = 3
    const val SPAWN = 2
    const val REGROW = 2
    const val STRIP_AT = 3

    override fun warriorNoun(n: Int) = "starfish"
    override fun newState(): FactionState = StarfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as StarfishState

    override fun dawnHint(g: GameState, p: Int) = "discard up to 2 cards; each spawns 2 starfish in a reef of its suit."
    override fun duskHint(g: GameState, p: Int) = "strip reefs to Rubble, then score."

    override fun setupOptions(g: GameState, p: Int): List<Option> {
        if (g.players[p].setupDone) return emptyList()
        val first = st(g, p).setupFirst
        val inner = Board.reefs.filter { !it.gate }.map { it.id }
        return if (first == null) inner.filter { r -> Board.neighbors(r).any { it in inner } }.map { PlaceSetup(it) }
        else Board.neighbors(first).filter { it in inner }.map { PlaceSetup(it) }
    }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        place(g, p, reef, SETUP)
        val s = st(g, p)
        if (s.setupFirst == null) s.setupFirst = reef else g.players[p].setupDone = true
        Game.log(g, "Starfish: $SETUP starfish crawl into ${Board.name(reef)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        st(g, p).spawnDone = false
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.spawnDone || g.turn.used("spawn") >= 2 || s.supply == 0) return emptyList()
        val anyOnMap = Eval.onMap(g, id) > 0
        val out = mutableListOf<Option>()
        for (c in g.players[p].hand) {
            val suit = Cards[c].suit
            for (reef in g.reefs.indices) {
                val suitOk = suit == Suit.MOON || g.suitOf(reef) == suit
                if (suitOk && (!anyOnMap || g.reefs[reef].warriors(id) > 0)) out += StarSpawn(c, reef)
            }
        }
        return if (out.isEmpty()) out else out + Done("Start the Day")
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is StarSpawn -> {
                Game.discardFromHand(g, p, o.cardId)
                val n = place(g, p, o.reef, SPAWN)
                g.turn.use("spawn")
                Game.log(g, "Starfish: $n starfish spawn in ${Board.name(o.reef)}.")
            }
            else -> s.spawnDone = true
        }
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> = Game.standardMoves(g, p)

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val m = o as Move
        Game.log(g, "Starfish: crawl ${m.n} from ${Board.name(m.from)} to ${Board.name(m.to)}.")
        Game.moveWarriors(g, p, m.from, m.to, m.n)
    }

    /** After an attack removes starfish, 2 regrow in the neighboring reef with the most starfish. */
    fun regrow(g: GameState, p: Int, reef: Int) {
        val options = Game.neighbors(g, reef, id)
        if (options.isEmpty()) return
        val best = options.sortedWith(compareByDescending<Int> { g.reefs[it].warriors(id) }.thenBy { Eval.enemyPresence(g, p, it) }.thenBy { it }).first()
        val n = place(g, p, best, REGROW)
        if (n > 0) Game.log(g, "Starfish: $n starfish regrow in ${Board.name(best)}.")
    }

    override fun duskAuto(g: GameState, p: Int) {
        val s = st(g, p)
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            if (s.rubble == 0) break
            if (rs.warriors(id) >= STRIP_AT && Game.freeSlots(g, reef) > 0 && rs.count(PieceType.RUBBLE, id) == 0) {
                rs.pieces.add(Piece(id, PieceType.RUBBLE))
                s.rubble--
                Game.log(g, "Starfish: strip ${Board.name(reef)} to Rubble.")
            }
        }
        val stripped = g.reefs.count { it.count(PieceType.RUBBLE, id) > 0 }
        Game.scoreVp(g, p, (stripped + 1) / 2, "$stripped reef${if (stripped == 1) "" else "s"} of Rubble")
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).supply += n
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.RUBBLE) st(g, p).rubble++
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
        return "${s.supply} starfish and ${s.rubble} Rubble left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        var v = 0.0
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val n = rs.warriors(id)
            v += n * 1.8
            if (rs.count(PieceType.RUBBLE, id) > 0) v += 9.0
            else if (n >= STRIP_AT && Game.freeSlots(g, reef) > 0) v += 6.0
            else if (n in 1 until STRIP_AT && Game.freeSlots(g, reef) > 0) v += n * 1.0
        }
        return v + Eval.hand(g, p, 1.8)
    }
}
