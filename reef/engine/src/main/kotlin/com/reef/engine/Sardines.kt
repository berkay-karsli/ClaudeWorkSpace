package com.reef.engine

object SardinesRules : FactionRules {
    override val id = FactionId.SARDINES
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "place 5 sardines at any gate."
    const val SETUP = 5
    const val RUN = 3

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

    override fun dawnHint(g: GameState, p: Int): String {
        val gate = st(g, p).runGate ?: return "$RUN sardines arrive at a gate you choose."
        return "discard ${g.suitOf(gate).label} cards (or Moon) for 1 more sardine each."
    }

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
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.runDone) return emptyList()
        val gate = s.runGate ?: return Board.gates.map { RunGate(it) }
        return Game.cardsOfSuit(g, p, g.suitOf(gate)).map<Int, Option> { RunCard(it) } + Done("Start the Day")
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is RunGate -> {
                s.runGate = o.gate
                val n = place(g, p, o.gate, o.gate, RUN)
                Game.log(g, "Sardines: the run arrives, $n sardines at ${Board.name(o.gate)}.")
            }
            is RunCard -> {
                Game.discardFromHand(g, p, o.cardId)
                place(g, p, s.runGate!!, s.runGate!!, 1)
                Game.log(g, "Sardines: discard ${Cards[o.cardId].name}, 1 more sardine joins the run.")
            }
            else -> s.runDone = true
        }
        if (s.supply == 0) s.runDone = true
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
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val m = o as Move
        Game.log(g, "Sardines: a school of ${m.n} swims from ${Board.name(m.from)} to ${Board.name(m.to)}.")
        Game.moveWarriors(g, p, m.from, m.to, m.n)
    }

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
