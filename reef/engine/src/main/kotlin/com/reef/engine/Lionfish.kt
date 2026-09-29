package com.reef.engine

object LionfishRules : FactionRules {
    override val id = FactionId.LIONFISH
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "place 2 lionfish at any gate."
    const val SETUP = 2
    const val RELEASE = 2
    const val HOLD = 6
    const val CRAFT_AT = 3
    const val BREED_AT = 3

    override fun warriorNoun(n: Int) = "lionfish"
    override fun newState(): FactionState = LionfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as LionfishState

    override fun dawnHint(g: GameState, p: Int) = "with fewer than 2 lionfish on the map, 2 more arrive at a gate you choose."

    /** Reefs where 3 or more of your lionfish are the only warriors. */
    fun alone(g: GameState): List<Int> =
        g.reefs.indices.filter { reef -> g.reefs[reef].warriors(id) >= CRAFT_AT && g.reefs[reef].warriors.keys.all { it == id } }

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val gate = (o as PlaceSetup).reef
        place(g, p, gate, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Lionfish: $SETUP lionfish slip in at ${Board.name(gate)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.stuffed.clear()
        s.breeding.clear()
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val n = minOf(RELEASE, st(g, p).supply)
        return if (g.turn.used("release") == 0 && Eval.onMap(g, id) < 2 && n > 0) Board.gates.map { Arrive(it, n, "lionfish") } else emptyList()
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        g.turn.use("release")
        place(g, p, a.reef, a.n)
        Game.log(g, "Lionfish: ${a.n} more lionfish are released at ${Board.name(a.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        // Stuffed lionfish can't move.
        out += Game.standardMoves(g, p).filter { (it as Move).from !in s.stuffed }
        out += Game.battleOptions(g, p)
        out += gorges(g, p)
        if (s.supply > 0 && g.turn.used("release") == 0) for (c in g.players[p].hand.distinct()) for (gate in Board.gates) out += Release(c, gate)
        return out
    }

    /** Gorge on any faction whose warriors the lionfish outnumber in a reef. */
    private fun gorges(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        for (reef in g.reefs.indices) {
            val mine = g.reefs[reef].warriors(id)
            if (mine == 0) continue
            // Stuffed lionfish can't gorge again this turn.
            if (reef in st(g, p).stuffed) continue
            for (q in g.players.indices) {
                if (q == p) continue
                val f = g.players[q].faction
                val rs = g.reefs[reef]
                if (rs.warriors(f) == 0) continue
                if (Game.rules(f).immune(g, q, reef)) continue
                if (mine > rs.warriors(f) * Game.rules(f).hitWeight) out += Gorge(reef, f)
            }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        when (o) {
            is Move -> {
                Game.log(g, "Lionfish: move ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            is Gorge -> {
                Game.log(g, "Lionfish: gorge on ${o.prey.display} in ${Board.name(o.reef)}. They are too stuffed to move.")
                st(g, p).stuffed += o.reef
                Game.hit(g, p, g.player(o.prey), o.reef, 1, Source.GORGE, AttackCtx())
            }
            is Release -> {
                g.turn.use("release")
                Game.discardFromHand(g, p, o.cardId)
                val n = place(g, p, o.gate, RELEASE)
                Game.log(g, "Lionfish: $n more lionfish are released at ${Board.name(o.gate)}.")
            }
            else -> error("Lionfish can't ${o.describe()}")
        }
    }

    override fun duskHint(g: GameState, p: Int) = "each reef with 3 or more lionfish breeds 1, there or next door. Then score reefs you hold alone."

    /** Every reef with 2 or more lionfish breeds once, in the order they are placed. */
    override fun onDuskStart(g: GameState, p: Int) {
        val s = st(g, p)
        s.breeding.clear()
        s.breeding += g.reefs.indices.filter { g.reefs[it].warriors(id) >= BREED_AT }
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.supply == 0) return emptyList()
        val from = s.breeding.firstOrNull() ?: return emptyList()
        return listOf(Breed(from, from)) + Game.neighbors(g, from, id).map { Breed(from, it) }
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val b = o as Breed
        st(g, p).breeding.remove(b.from)
        place(g, p, b.to, 1)
        Game.log(g, if (b.from == b.to) "Lionfish: a young lionfish is born in ${Board.name(b.to)}." else "Lionfish: a young lionfish from ${Board.name(b.from)} settles in ${Board.name(b.to)}.")
        // Young lionfish that land among enemies are warriors like any other: they can be stung.
        if (b.from != b.to) Game.afterArrive(g, p, b.from, b.to, 1)
    }

    override fun duskAuto(g: GameState, p: Int) {
        st(g, p).breeding.clear()
        val alone = g.reefs.indices.count { reef -> g.reefs[reef].warriors(id) >= HOLD && g.players.none { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 } }
        Game.scoreVp(g, p, alone, "$alone reef${if (alone == 1) "" else "s"} eaten empty")
    }

    /** Each reef where 3 or more lionfish are the only warriors pays its suit. */
    override fun craftUnits(g: GameState, p: Int) = reefUnits(g, alone(g))

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

    override fun supplySummary(g: GameState, p: Int) = "${st(g, p).supply} lionfish waiting to invade"

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        var v = 0.0
        for (reef in g.reefs.indices) {
            val n = g.reefs[reef].warriors(id)
            if (n == 0) continue
            v += n * 3.0
            if (n >= BREED_AT) v += 2.5
            val others = g.players.indices.filter { it != p }
            val rivals = others.sumOf { g.reefs[reef].warriors(g.players[it].faction) }
            if (rivals == 0) v += if (n >= HOLD) 7.0 else n * 1.2
            // Prey they already outnumber.
            v += others.sumOf { q ->
                val theirs = g.reefs[reef].warriors(g.players[q].faction)
                if (theirs > 0 && n > theirs * Game.rules(g.players[q].faction).hitWeight) 2.0 else 0.0
            }
        }
        return v + Eval.hand(g, p)
    }
}
