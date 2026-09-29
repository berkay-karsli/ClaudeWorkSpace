package com.reef.engine

object StarfishRules : FactionRules {
    override val id = FactionId.STARFISH
    override val actionsPerDay = 2
    override val canDig = true
    override val setupHint = "3 starfish in each of two neighboring reefs that aren't gates."
    const val SETUP = 3
    const val SPAWN = 2
    const val REGROW = 2
    const val DEVOUR_AT = 3

    override fun warriorNoun(n: Int) = "starfish"
    override fun newState(): FactionState = StarfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as StarfishState

    override fun dawnHint(g: GameState, p: Int) = "discard up to 2 cards; each spawns 2 starfish in a reef of its suit."
    override fun duskHint(g: GameState, p: Int) = "score 1 VP for every 2 reefs with your Rubble."

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

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        out += Game.standardMoves(g, p)
        // Drawn to food: into a reef with an enemy building, ignoring rule.
        for (from in g.reefs.indices) {
            val n = g.reefs[from].warriors(id)
            if (n == 0) continue
            for (to in Game.neighbors(g, from, id)) {
                if (g.reefs[to].pieces.none { it.type.building && it.owner != id }) continue
                for (k in n downTo 1) {
                    val m = Move(from, to, k)
                    if (m !in out) out += m
                }
            }
        }
        out += Game.battleOptions(g, p)
        out += devours(g, p)
        return out
    }

    private fun devours(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        val s = st(g, p)
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            if (rs.warriors(id) < DEVOUR_AT) continue
            for (piece in rs.pieces.filter { it.type.building && it.owner != id }.distinctBy { it.owner to it.type }) {
                val q = g.player(piece.owner!!)
                if (!Game.rules(piece.owner).immune(g, q, reef)) out += Devour(reef, piece.owner, piece.type)
            }
            if (s.rubble > 0 && Game.freeSlots(g, reef) > 0 && rs.count(PieceType.RUBBLE, id) == 0) out += Devour(reef)
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        when (o) {
            is Move -> {
                Game.log(g, "Starfish: crawl ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            is Devour -> {
                val rs = g.reefs[o.reef]
                if (o.owner == null) {
                    rs.pieces.add(Piece(id, PieceType.RUBBLE))
                    st(g, p).rubble--
                    Game.log(g, "Starfish: strip ${Board.name(o.reef)} to Rubble.")
                } else {
                    Game.log(g, "Starfish: devour ${Game.possessive(o.owner.display)} ${o.type!!.label} in ${Board.name(o.reef)}.")
                    Game.removePiece(g, o.reef, rs.pieces.first { it.owner == o.owner && it.type == o.type })
                    Game.scoreVp(g, p, 1, "devouring a ${o.type.label}")
                }
            }
            else -> error("Starfish can't ${o.describe()}")
        }
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
        val stripped = g.reefs.count { it.count(PieceType.RUBBLE, id) > 0 }
        Game.scoreVp(g, p, (stripped + 1) / 2, "$stripped reef${if (stripped == 1) "" else "s"} of Rubble")
    }

    /** Your Rubble pays its reef's suit. */
    override fun craftUnits(g: GameState, p: Int) = g.reefs.indices.flatMap { reef ->
        List(g.reefs[reef].count(PieceType.RUBBLE, id)) { CraftUnit("reef:$reef", g.suitOf(reef)) }
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
            if (rs.count(PieceType.RUBBLE, id) > 0) v += 7.0
            else if (n >= DEVOUR_AT && Game.freeSlots(g, reef) > 0) v += 3.0
            else if (n in 1 until DEVOUR_AT && Game.freeSlots(g, reef) > 0) v += n * 1.0
            // Food: enemy buildings within reach.
            val food = rs.pieces.count { it.type.building && it.owner != id }
            if (food > 0 && n > 0) v += food * (if (n >= DEVOUR_AT) 4.0 else 1.5)
        }
        return v + Eval.hand(g, p, 1.8)
    }
}
