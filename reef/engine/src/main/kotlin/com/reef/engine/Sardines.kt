package com.reef.engine

object SardinesRules : FactionRules {
    override val id = FactionId.SARDINES
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "place 5 sardines at any gate."
    const val SETUP = 5
    const val RUN = 3
    const val RALLY = 2
    const val WALL = 6
    const val MOB = 6
    const val CRAFT_SCHOOL = 4

    override fun warriorNoun(n: Int) = if (n == 1) "sardine" else "sardines"
    override fun newState(): FactionState = SardinesState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as SardinesState

    /** Points for a school leaving the map: 1-3 fish score 1, 4-6 score 2, 7-9 score 4, 10+ score 6. */
    fun exitVp(n: Int) = when {
        n >= 10 -> 6
        n >= 7 -> 4
        n >= 4 -> 2
        n >= 1 -> 1
        else -> 0
    }

    override fun dawnHint(g: GameState, p: Int) = "$RUN sardines arrive at a gate you choose."

    override fun duskHint(g: GameState, p: Int) = "schools on a gate they didn't come in by may leave and score."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val gate = (o as PlaceSetup).reef
        place(g, p, gate, gate, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Sardines: $SETUP sardines come in at ${Board.name(gate)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.runGate = null
        s.runDone = s.supply == 0
        s.exitDone = false
        s.pushAt = null
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.runDone) return emptyList()
        return Board.gates.map { RunGate(it) }
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is RunGate -> {
                s.runGate = o.gate
                s.runDone = true
                val n = place(g, p, o.gate, o.gate, RUN)
                Game.log(g, "Sardines: the run arrives, $n sardines at ${Board.name(o.gate)}.")
            }
            else -> error("Sardines can't ${o.describe()} at Dawn")
        }
    }

    /** A move takes the whole school up to two channels, ignoring rule. */
    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        for (from in g.reefs.indices) {
            val n = g.reefs[from].warriors(id)
            if (n == 0) continue
            val one = Game.neighbors(g, from, id)
            val reach = (one + one.flatMap { Game.neighbors(g, it, id) }).toSet() - from
            for (to in reach.sorted()) out += Move(from, to, n)
            if (n >= MOB) out += Game.battleTargets(g, p, from).map { Battle(from, it) }
        }
        if (st(g, p).supply > 0) {
            for (c in g.players[p].hand) for (reef in g.reefs.indices) {
                if (g.reefs[reef].warriors(id) > 0 && (Cards[c].suit == Suit.MOON || g.suitOf(reef) == Cards[c].suit)) out += Rally(c, reef)
            }
        }
        return out
    }

    /** Right after a school of 6 or more arrives, it may push a faction with fewer warriors there. */
    override fun freeDayOptions(g: GameState, p: Int): List<Option> {
        val reef = st(g, p).pushAt ?: return emptyList()
        val mine = g.reefs[reef].warriors(id)
        if (mine < WALL) return emptyList()
        return Game.pushTargets(g, p, reef).filter { (q, _) ->
            val f = g.players[q].faction
            g.reefs[reef].warriors(f) * Game.rules(f).hitWeight < mine
        }.flatMap { (q, dests) -> dests.map { Push(reef, g.players[q].faction, it) } }
    }

    /** A mob deals at most 1 hit for every 3 fish. */
    override fun attackCap(g: GameState, p: Int, reef: Int) = g.reefs[reef].warriors(id) / 3

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        s.pushAt = null
        when (o) {
            is Move -> {
                Game.log(g, "Sardines: a school of ${o.n} swims from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
                if (g.reefs[o.to].warriors(id) >= WALL) s.pushAt = o.to
            }
            is Battle -> {
                Game.log(g, "Sardines: the school of ${g.reefs[o.reef].warriors(id)} mobs.")
                Game.startBattle(g, p, o.reef, o.defender)
            }
            is Rally -> {
                Game.discardFromHand(g, p, o.cardId)
                val here = s.origins[o.reef].orEmpty()
                val gate = here.maxByOrNull { it.value }?.key ?: o.reef
                val n = place(g, p, o.reef, gate, RALLY)
                Game.log(g, "Sardines: $n more sardines rally to the school in ${Board.name(o.reef)}.")
            }
            is Push -> Game.push(g, p, g.player(o.victim), o.reef, o.to, g.reefs[o.reef].warriors(o.victim))
            else -> error("Sardines can't ${o.describe()}")
        }
    }

    override fun onDuskStart(g: GameState, p: Int) {
        st(g, p).pushAt = null
    }

    /** Each reef with 4 or more sardines pays its suit. */
    override fun craftUnits(g: GameState, p: Int) = reefUnits(g, g.reefs.indices.filter { g.reefs[it].warriors(id) >= CRAFT_SCHOOL })

    override fun onMoved(g: GameState, p: Int, from: Int, to: Int, n: Int) {
        val s = st(g, p)
        val src = s.origins.getOrPut(from) { mutableMapOf() }
        val dst = s.origins.getOrPut(to) { mutableMapOf() }
        var left = n
        for (gate in src.keys.sortedByDescending { src.getValue(it) }) {
            val k = minOf(left, src.getValue(gate))
            src[gate] = src.getValue(gate) - k
            dst[gate] = (dst[gate] ?: 0) + k
            left -= k
            if (left == 0) break
        }
        tidy(s)
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.exitDone) return emptyList()
        val exits = Board.gates.mapNotNull { gate -> eligible(s, gate).takeIf { it > 0 }?.let { Exit(gate, it) } }
        return if (exits.isEmpty()) emptyList() else exits + Done("Stay on the map")
    }

    private fun eligible(s: SardinesState, gate: Int): Int = s.origins[gate]?.filterKeys { it != gate }?.values?.sum() ?: 0

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Exit -> {
                val here = s.origins.getValue(o.gate)
                for (gate in here.keys.toList()) if (gate != o.gate) here.remove(gate)
                tidy(s)
                g.reefs[o.gate].addWarriors(id, -o.n)
                s.supply += o.n
                RemorasRules.dropOff(g, id, o.gate)
                Game.log(g, "Sardines: ${o.n} sardines leave the map at ${Board.name(o.gate)}.")
                Game.scoreVp(g, p, exitVp(o.n), "a school of ${o.n} leaving")
            }
            else -> s.exitDone = true
        }
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        g.reefs[reef].addWarriors(id, -n)
        s.supply += n
        val here = s.origins.getOrPut(reef) { mutableMapOf() }
        var left = n
        for (gate in here.keys.sortedByDescending { here.getValue(it) }) {
            val k = minOf(left, here.getValue(gate))
            here[gate] = here.getValue(gate) - k
            left -= k
            if (left == 0) break
        }
        tidy(s)
    }

    private fun place(g: GameState, p: Int, reef: Int, gate: Int, n: Int): Int {
        val s = st(g, p)
        val k = minOf(n, s.supply)
        if (k <= 0) return 0
        g.reefs[reef].addWarriors(id, k)
        s.supply -= k
        val here = s.origins.getOrPut(reef) { mutableMapOf() }
        here[gate] = (here[gate] ?: 0) + k
        return k
    }

    private fun tidy(s: SardinesState) {
        for (m in s.origins.values) m.entries.removeIf { it.value <= 0 }
        s.origins.entries.removeIf { it.value.isEmpty() }
    }

    override fun supplySummary(g: GameState, p: Int) = "${st(g, p).supply} sardines in the open ocean"

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = Eval.onMap(g, id) * 0.5
        for ((reef, origins) in s.origins) {
            // A school is worth what it will score when it leaves, less the further it has to go.
            var best = 0.0
            for (exit in Board.gates) {
                val n = origins.filterKeys { it != exit }.values.sum()
                if (n == 0) continue
                val d = Game.distance(g, reef, exit, id)
                val discount = when {
                    d == 0 -> 0.7
                    d <= 2 -> 0.5
                    d <= 4 -> 0.3
                    else -> 0.15
                }
                best = maxOf(best, exitVp(n) * 10.0 * discount)
            }
            v += best
            v -= minOf(Eval.threat(g, p, reef), origins.values.sum()) * 0.6
        }
        return v + Eval.hand(g, p, 1.8)
    }
}
