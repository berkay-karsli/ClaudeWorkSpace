package com.reef.engine

object TurtlesRules : FactionRules {
    override val id = FactionId.TURTLES
    override val actionsPerDay = 3
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

    /**
     * At Dawn every egg hatches and scores; hatchlings become new turtles while there are fewer
     * than 4. A hatchling in a reef with enemy warriors is eaten instead, and the ruler scores it.
     */
    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        var turtles = 0
        var points = 0
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val eggs = rs.count(PieceType.EGG, id)
            val danger = g.players.any { it.faction != id && rs.warriors(it.faction) > 0 }
            repeat(eggs) {
                rs.pieces.remove(Piece(id, PieceType.EGG))
                s.eggs++
                if (danger) {
                    val ruler = Game.ruler(g, reef)
                    Game.log(g, "Sea Turtles: a hatchling dashes for the sea in ${Board.name(reef)} and is eaten.")
                    if (ruler != null && ruler != p) Game.scoreVp(g, ruler, 1, "eating a hatchling")
                    return@repeat
                }
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
            val near = Game.neighbors(g, t.reef, id)
            for (to in near) out += TurtleMove(t.id, t.reef, to, foodText(t))
            for (to in currentRide(g, t.reef)) if (to !in near) out += TurtleMove(t.id, t.reef, to, foodText(t), ride = true)
            val suit = g.suitOf(t.reef)
            if (!Board.reefs[t.reef].shore && suit !in t.food) out += Feed(t.id, t.reef, suit)
            if (t.food.isNotEmpty() && s.eggs > 0 && g.reefs[t.reef].count(PieceType.NEST, id) > 0) out += Lay(t.id, t.reef, minOf(t.food.size + 2, s.eggs))
        }
        if (s.nests > 0) {
            for (reef in s.turtles.map { it.reef }.distinct()) {
                if (Board.reefs[reef].shore && Game.freeSlots(g, reef) > 0) out += BuildNest(reef)
            }
        }
        out += Game.battleOptions(g, p)
        return out
    }

    /** Every reef a turtle in [from] can reach by riding currents, one after another. */
    fun currentRide(g: GameState, from: Int): List<Int> {
        val seen = linkedSetOf<Int>()
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            for (n in Game.neighbors(g, x, id)) if (Game.isCurrent(g, x, n) && n != from && seen.add(n)) queue.addLast(n)
        }
        return seen.toList()
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is TurtleMove -> {
                val t = s.turtles.first { it.id == o.id }
                t.reef = o.to
                sync(g, p)
                Game.log(g, "Sea Turtles: turtle ${o.id + 1} ${if (o.ride) "rides the current" else "swims"} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
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
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            else -> error("Sea Turtles can't ${o.describe()}")
        }
    }

    /** Nests pay their reef's suit; each Food a turtle carries pays its suit and is eaten. */
    override fun craftUnits(g: GameState, p: Int): List<CraftUnit> =
        buildingUnits(g, p) + st(g, p).turtles.flatMap { t -> t.food.map { CraftUnit("food:${t.id}:${it.name}", it, spend = true) } }

    override fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {
        val (_, tid, suit) = unit.key.split(":")
        st(g, p).turtles.first { it.id == tid.toInt() }.food.remove(Suit.valueOf(suit))
    }

    /** Pushed turtles go the lightest-laden first. */
    override fun displace(g: GameState, p: Int, from: Int, to: Int, n: Int) {
        val s = st(g, p)
        s.turtles.filter { it.reef == from }.sortedBy { it.food.size }.take(n).forEach { it.reef = to }
        sync(g, p)
        Game.afterArrive(g, p, from, to, n)
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
        return "${s.turtles.size} turtle${if (s.turtles.size == 1) "" else "s"} · ${s.eggs} eggs and ${s.nests} nests left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = s.turtles.size * 10.0
        val nests = g.reefs.indices.filter { g.reefs[it].count(PieceType.NEST, id) > 0 }
        v += nests.size * 4.0
        for (t in s.turtles) {
            // Food is eggs waiting to be laid: worth more the closer the turtle is to a nest.
            v += t.food.size * 5.0
            if (t.food.isNotEmpty() && nests.isNotEmpty()) v -= nests.minOf { Game.distance(g, t.reef, it, id).coerceAtMost(6) } * t.food.size * 1.0
            if (t.food.size < 3 && !Board.reefs[t.reef].shore && g.suitOf(t.reef) !in t.food) v += 1.0
        }
        for (reef in g.reefs.indices) {
            val eggs = g.reefs[reef].count(PieceType.EGG, id)
            if (eggs == 0) continue
            val watched = g.players.any { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 }
            v += eggs * (if (watched) 1.0 else 6.0 - minOf(Eval.threat(g, p, reef), 4))
        }
        return v + Eval.hand(g, p)
    }
}
