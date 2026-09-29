package com.reef.engine

object SnakeRules : FactionRules {
    override val id = FactionId.SNAKE
    override val actionsPerDay = 2
    override val canDig = true
    override val setupHint = "the Head and 3 segments in one reef that isn't a gate."
    const val PIECES = 15
    const val SETUP = 4
    const val SMALL = 4
    const val LONG = 9
    const val MOLT = 2

    override fun warriorNoun(n: Int) = if (n == 1) "piece" else "pieces"
    override fun newState(): FactionState = SnakeState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as SnakeState

    fun head(g: GameState): Int? = g.state<SnakeState>(id)?.body?.firstOrNull()

    override fun dawnHint(g: GameState, p: Int) =
        if (st(g, p).body.isEmpty()) "a new Head arrives at a gate of your choice." else "a snake with fewer than $SMALL pieces adds a segment."
    override fun duskHint(g: GameState, p: Int) = "score 1 VP for every 2 reefs your snake is in."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { !it.gate }.map { PlaceSetup(it.id) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        repeat(SETUP) { st(g, p).body += reef }
        sync(g, p)
        g.players[p].setupDone = true
        Game.log(g, "Sea Snake: the snake coils up in ${Board.name(reef)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        if (s.body.isNotEmpty() && s.body.size < SMALL) grow(g, p, 1)
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> =
        if (st(g, p).body.isEmpty()) Board.gates.map { Arrive(it, 1, "new Head") } else emptyList()

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        st(g, p).body += a.reef
        sync(g, p)
        Game.log(g, "Sea Snake: a new Head arrives at ${Board.name(a.reef)}.")
        grow(g, p, 1)
    }

    override fun bonusActions(g: GameState, p: Int) = if (st(g, p).body.size >= LONG) 1 else 0

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val head = s.body.firstOrNull() ?: return emptyList()
        val out = mutableListOf<Option>()
        out += Game.neighbors(g, head, id).map { Slither(head, it) }
        out += Game.battleTargets(g, p, head).map { Bite(head, it) }
        if (s.body.size < PIECES) out += g.players[p].hand.distinct().map { Molt(it) }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Slither -> {
                val old = s.body.toList()
                // A coil: the Head slithers into a reef where the rest of the body already is.
                val coil = old.drop(1).contains(o.to)
                // The Head moves; each segment moves to where the piece ahead of it was.
                for (i in s.body.indices) s.body[i] = if (i == 0) o.to else old[i - 1]
                sync(g, p)
                Game.log(g, "Sea Snake: slither from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                val arrivals = linkedMapOf<Pair<Int, Int>, Int>()
                arrivals[o.from to o.to] = 1
                for (i in 1 until old.size) if (old[i] != old[i - 1]) arrivals.merge(old[i] to old[i - 1], 1, Int::plus)
                for ((move, n) in arrivals) {
                    if (s.body.isEmpty() || g.winner != null) break
                    Game.afterArrive(g, p, move.first, move.second, n)
                }
                if (coil && s.body.firstOrNull() == o.to) squeeze(g, p, o.to)
            }
            is Molt -> {
                Game.discardFromHand(g, p, o.cardId)
                Game.log(g, "Sea Snake: molt, shedding the old skin.")
                grow(g, p, MOLT)
            }
            is Bite -> Game.startBattle(g, p, o.reef, o.prey)
            else -> error("Sea Snake can't ${o.describe()}")
        }
    }

    /** A coil: every other faction's warriors in [reef] take 1 hit. */
    private fun squeeze(g: GameState, p: Int, reef: Int) {
        val prey = g.players.indices.filter { q -> q != p && g.reefs[reef].warriors(g.players[q].faction) > 0 && !Game.rules(g, q).immune(g, q, reef) }
        if (prey.isEmpty()) return
        Game.log(g, "Sea Snake: coil around everything in ${Board.name(reef)}!")
        for (q in prey) {
            if (g.winner != null) return
            Game.hit(g, p, q, reef, 1, Source.COIL, AttackCtx())
        }
    }

    /** Shed the tail: each segment taken off the end pays any suit. The Head can't be shed. */
    override fun craftUnits(g: GameState, p: Int): List<CraftUnit> =
        List(maxOf(0, st(g, p).body.size - 1)) { CraftUnit("tail", Suit.MOON, spend = true) }

    override fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {
        val s = st(g, p)
        val tail = s.body.removeAt(s.body.lastIndex)
        sync(g, p)
        RemorasRules.dropOff(g, id, tail)
        Game.log(g, "Sea Snake: shed the tail in ${Board.name(tail)} (${s.body.size} pieces).")
    }

    override fun pushable(g: GameState, p: Int, reef: Int) = false

    /** Adds up to [n] segments at the tail. */
    fun grow(g: GameState, p: Int, n: Int) {
        val s = st(g, p)
        if (s.body.isEmpty()) return
        val k = minOf(n, PIECES - s.body.size)
        if (k <= 0) return
        val tail = s.body.last()
        repeat(k) { s.body += tail }
        sync(g, p)
        Game.log(g, "Sea Snake: grow $k segment${if (k == 1) "" else "s"} (${s.body.size} pieces).")
    }

    override fun duskAuto(g: GameState, p: Int) {
        val reefs = st(g, p).body.distinct().size
        Game.scoreVp(g, p, (reefs + 1) / 2, "stretching across $reefs reefs")
    }

    /** Each hit takes the piece nearest the tail in [reef]. A cut that breaks the line loses everything behind it. */
    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        val emptied = mutableSetOf(reef)
        repeat(n) {
            val i = s.body.lastIndexOf(reef)
            if (i < 0) return@repeat
            s.body.removeAt(i)
            if (i in 1 until s.body.size) {
                val ahead = s.body[i - 1]
                val behind = s.body[i]
                if (ahead != behind && behind !in Board.neighbors(ahead)) {
                    val lost = s.body.subList(i, s.body.size)
                    emptied += lost
                    Game.log(g, "Sea Snake: the body is cut in two and ${lost.size} segment${if (lost.size == 1) " is" else "s are"} lost.")
                    lost.clear()
                }
            }
        }
        sync(g, p)
        for (r in emptied) RemorasRules.dropOff(g, id, r)
    }

    /** Warrior counts on the map follow the body. */
    fun sync(g: GameState, p: Int) {
        val s = st(g, p)
        for (reef in g.reefs.indices) g.reefs[reef].setWarriors(id, s.body.count { it == reef })
        s.segments = if (s.body.isEmpty()) PIECES - 1 else PIECES - s.body.size
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return if (s.body.isEmpty()) "The snake is gone. A new Head arrives at Dawn." else "${s.body.size} pieces long · ${s.segments} segments left to grow"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val body = st(g, p).body
        if (body.isEmpty()) return Eval.hand(g, p)
        var v = body.size * 4.0 + body.distinct().size * 4.0
        if (body.size >= LONG) v += 6.0
        val head = body[0]
        val coiled = g.reefs[head].warriors(id)
        // Prey at the Head is dinner; prey stronger than the coil is danger.
        for (q in g.players.indices) {
            if (q == p) continue
            val prey = g.reefs[head].warriors(g.players[q].faction)
            if (prey > 0) v += minOf(prey, coiled) * 1.2
        }
        v -= Eval.threat(g, p, head).coerceAtMost(6) * 0.5
        return v + Eval.hand(g, p)
    }
}
