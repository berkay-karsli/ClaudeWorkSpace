package com.reef.engine

object TurtlesRules : FactionRules {
    override val id = FactionId.TURTLES
    override val actionsPerDay = 3
    override val canCraft = true
    override val setupHint = "1 nest and all 4 turtles in one shore reef."
    const val TURTLES = 4

    override fun warriorNoun(n: Int) = if (n == 1) "turtle" else "turtles"
    override fun newState(): FactionState = TurtlesState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as TurtlesState

    fun foodText(t: Turtle) = if (t.food.isEmpty()) "no food" else t.food.sortedBy { it.ordinal }.joinToString("+") { it.label }

    override fun dawnHint(g: GameState, p: Int) = "with no turtles left, one returns to a shore reef of your choice."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { it.shore && Game.freeSlots(g, it.id) > 0 }.map { PlaceSetup(it.id) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        val s = st(g, p)
        g.reefs[reef].pieces.add(Piece(id, PieceType.NEST))
        s.nests--
        repeat(TURTLES) { s.turtles += Turtle(s.nextId++, reef) }
        sync(g, p)
        g.players[p].setupDone = true
        Game.log(g, "Sea Turtles: four turtles haul out at their nest in ${Board.name(reef)}.")
    }

    /** At Dawn every egg hatches and scores; hatchlings become new turtles while there are fewer than 4. */
    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        var turtles = 0
        var points = 0
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val eggs = rs.count(PieceType.EGG, id)
            repeat(eggs) {
                rs.pieces.remove(Piece(id, PieceType.EGG))
                s.eggs++
                points++
                if (s.turtles.size < TURTLES) {
                    s.turtles += Turtle(s.nextId++, reef)
                    turtles++
                }
            }
        }
        sync(g, p)
        if (turtles > 0) Game.log(g, "Sea Turtles: $turtles hatchling${if (turtles == 1) " grows" else "s grow"} into a new turtle.")
        if (points > 0) Game.scoreVp(g, p, points, "$points egg${if (points == 1) "" else "s"} hatching")
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> =
        if (st(g, p).turtles.isEmpty() && g.turn.used("return") == 0) Board.reefs.filter { it.shore }.map { Arrive(it.id, 1, "turtle") } else emptyList()

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        g.turn.use("return")
        s.turtles += Turtle(s.nextId++, (o as Arrive).reef)
        sync(g, p)
        Game.log(g, "Sea Turtles: an old turtle returns to ${Board.name(o.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        for (t in s.turtles) {
            for (to in Game.neighbors(g, t.reef, id)) out += TurtleMove(t.id, t.reef, to, foodText(t))
            val suit = g.suitOf(t.reef)
            if (!Board.reefs[t.reef].shore && suit !in t.food) out += Feed(t.id, t.reef, suit)
            if (t.food.isNotEmpty() && s.eggs > 0 && g.reefs[t.reef].count(PieceType.NEST, id) > 0) out += Lay(t.id, t.reef, minOf(t.food.size + 1, s.eggs))
        }
        if (s.nests > 0) {
            for (reef in s.turtles.map { it.reef }.distinct()) {
                if (Board.reefs[reef].shore && Game.freeSlots(g, reef) > 0) out += BuildNest(reef)
            }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is TurtleMove -> {
                val t = s.turtles.first { it.id == o.id }
                t.reef = o.to
                sync(g, p)
                Game.log(g, "Sea Turtles: turtle ${o.id + 1} swims from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.afterArrive(g, p, o.from, o.to, 1)
            }
            is Feed -> {
                s.turtles.first { it.id == o.id }.food += o.suit
                Game.log(g, "Sea Turtles: turtle ${o.id + 1} feeds in ${Board.name(o.reef)} (${o.suit.label}).")
            }
            is Lay -> {
                val t = s.turtles.first { it.id == o.id }
                repeat(o.eggs) { g.reefs[o.reef].pieces.add(Piece(id, PieceType.EGG)) }
                s.eggs -= o.eggs
                t.food.clear()
                Game.log(g, "Sea Turtles: turtle ${o.id + 1} lays ${o.eggs} egg${if (o.eggs == 1) "" else "s"} in ${Board.name(o.reef)}.")
                Game.scoreVp(g, p, o.eggs, "laying ${o.eggs} egg${if (o.eggs == 1) "" else "s"}")
            }
            is BuildNest -> {
                g.reefs[o.reef].pieces.add(Piece(id, PieceType.NEST))
                s.nests--
                s.nestsBuilt++
                Game.log(g, "Sea Turtles: build a nest in ${Board.name(o.reef)}.")
                Game.scoreVp(g, p, s.nestsBuilt, "a new nest")
            }
            else -> error("Sea Turtles can't ${o.describe()}")
        }
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        repeat(n) {
            val t = s.turtles.filter { it.reef == reef }.minByOrNull { it.food.size } ?: return@repeat
            s.turtles.remove(t)
        }
        sync(g, p)
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        val s = st(g, p)
        when (piece.type) {
            PieceType.EGG -> s.eggs++
            PieceType.NEST -> s.nests++
            else -> {}
        }
    }

    /** Warrior counts on the map follow the turtle list. */
    fun sync(g: GameState, p: Int) {
        val s = st(g, p)
        for (reef in g.reefs.indices) g.reefs[reef].setWarriors(id, s.turtles.count { it.reef == reef })
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return "${s.turtles.size} turtles · ${s.eggs} eggs and ${s.nests} nests left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = s.turtles.size * 10.0
        val nests = g.reefs.indices.filter { g.reefs[it].count(PieceType.NEST, id) > 0 }
        v += nests.size * 4.0
        for (t in s.turtles) {
            v += t.food.size * 3.5
            if (t.food.isNotEmpty() && nests.isNotEmpty()) v -= nests.minOf { Game.distance(g, t.reef, it, id).coerceAtMost(6) } * t.food.size * 0.4
            if (t.food.size < 3 && !Board.reefs[t.reef].shore && g.suitOf(t.reef) !in t.food) v += 1.0
        }
        for (reef in g.reefs.indices) v += g.reefs[reef].count(PieceType.EGG, id) * (6.0 - minOf(Eval.threat(g, p, reef), 4))
        return v + Eval.hand(g, p)
    }
}
