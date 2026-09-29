package com.reef.engine

object LionfishRules : FactionRules {
    override val id = FactionId.LIONFISH
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "place 2 lionfish at any gate."
    const val SETUP = 2
    const val RELEASE = 2
    const val HOLD = 4

    override fun warriorNoun(n: Int) = "lionfish"
    override fun newState(): FactionState = LionfishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as LionfishState

    override fun dawnHint(g: GameState, p: Int) = "with fewer than 2 lionfish on the map, 2 more arrive at a gate you choose."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val gate = (o as PlaceSetup).reef
        place(g, p, gate, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Lionfish: $SETUP lionfish slip in at ${Board.name(gate)}.")
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

    override fun dayOptions(g: GameState, p: Int): List<Option> = Game.standardMoves(g, p) + gorges(g, p)

    /** Gorge on any faction whose warriors the lionfish outnumber in a reef. */
    private fun gorges(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        for (reef in g.reefs.indices) {
            val mine = g.reefs[reef].warriors(id)
            if (mine == 0) continue
            for (q in g.players.indices) {
                if (q == p) continue
                val f = g.players[q].faction
                val theirs = g.reefs[reef].warriors(f)
                if (theirs == 0 || Game.rules(f).immune(g, q, reef)) continue
                if (mine > theirs * Game.rules(f).hitWeight) out += Gorge(reef, f)
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
            is Gorge -> {
                Game.log(g, "Lionfish: gorge on ${o.prey.display} in ${Board.name(o.reef)}.")
                Game.hit(g, p, g.player(o.prey), o.reef, 1, Source.GORGE, AttackCtx())
            }
            else -> error("Lionfish can't ${o.describe()}")
        }
    }

    override fun duskHint(g: GameState, p: Int) = "every reef with 2 or more lionfish breeds 1 more, then score reefs you hold alone."

    override fun duskAuto(g: GameState, p: Int) {
        var born = 0
        for (reef in g.reefs.indices) if (g.reefs[reef].warriors(id) >= 2) born += place(g, p, reef, 1)
        if (born > 0) Game.log(g, "Lionfish: $born new lionfish are born.")
        val alone = g.reefs.indices.count { reef -> g.reefs[reef].warriors(id) >= HOLD && g.players.none { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 } }
        Game.scoreVp(g, p, alone, "$alone reef${if (alone == 1) "" else "s"} eaten empty")
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

    override fun supplySummary(g: GameState, p: Int) = "${st(g, p).supply} lionfish waiting to invade"

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        var v = 0.0
        for (reef in g.reefs.indices) {
            val n = g.reefs[reef].warriors(id)
            if (n == 0) continue
            v += n * 3.0
            if (n >= 2) v += 2.5
            // Prey they already outnumber.
            v += g.players.indices.filter { it != p }.sumOf { q ->
                val theirs = g.reefs[reef].warriors(g.players[q].faction)
                if (theirs > 0 && n > theirs * Game.rules(g.players[q].faction).hitWeight) 2.0 else 0.0
            }
        }
        return v + Eval.hand(g, p)
    }
}
