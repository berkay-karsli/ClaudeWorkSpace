package com.reef.engine

/**
 * Remoras on the map are free (counted as warriors in the reef) or attached (kept in
 * [RemorasState.attached], out of reach of hits).
 */
object RemorasRules : FactionRules {
    override val id = FactionId.REMORAS
    override val ruleWeight = 0
    override val actionsPerDay = 3
    override val setupHint = "place 3 remoras at any gate."
    const val SETUP = 3
    const val HITCH = 2
    const val PILE_ON = 2

    override fun warriorNoun(n: Int) = if (n == 1) "remora" else "remoras"
    override fun newState(): FactionState = RemorasState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as RemorasState

    override fun dawnHint(g: GameState, p: Int) = "1 remora arrives at a gate of your choice."
    override fun duskHint(g: GameState, p: Int) = "score 1 VP per faction that 2 or more of your remoras ride."

    fun attached(g: GameState, reef: Int, host: FactionId): Int = g.state<RemorasState>(id)?.attached?.get(reef)?.get(host) ?: 0
    fun attachedTotal(s: RemorasState): Int = s.attached.values.sumOf { it.values.sum() }
    fun hosts(s: RemorasState): Set<FactionId> = s.attached.values.flatMap { m -> m.filterValues { it > 0 }.keys }.toSet()

    /** Hosts ridden by 2 or more remoras in all: these score at Dusk. */
    fun scoringHosts(s: RemorasState): Set<FactionId> =
        s.attached.values.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.sum() >= 2 }.keys

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val gate = (o as PlaceSetup).reef
        place(g, p, gate, SETUP)
        g.players[p].setupDone = true
        Game.log(g, "Remoras: $SETUP remoras wait at ${Board.name(gate)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        st(g, p).arrived = false
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> =
        if (!st(g, p).arrived && st(g, p).supply > 0) Board.gates.map { Arrive(it, 1, "remora") } else emptyList()

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        st(g, p).arrived = true
        place(g, p, a.reef, 1)
        Game.log(g, "Remoras: a remora arrives at ${Board.name(a.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        for (reef in g.reefs.indices) {
            val free = g.reefs[reef].warriors(id)
            if (free > 0) {
                for (to in Game.neighbors(g, reef, id)) for (k in free downTo 1) out += Swim(reef, to, k)
                for (q in g.players.indices) {
                    val host = g.players[q].faction
                    if (host == id || g.reefs[reef].warriors(host) == 0) continue
                    for (k in free downTo 1) out += Attach(reef, host, k)
                }
            }
            s.attached[reef]?.forEach { (host, n) ->
                if (n > 0) {
                    out += LetGo(reef, host)
                    if (g.turn.used("clean") == 0) out += Clean(reef, host)
                }
            }
        }
        if (s.supply > 0) {
            for (c in g.players[p].hand.distinct()) for (reef in g.reefs.indices) {
                if (Cards[c].suit != Suit.MOON && g.suitOf(reef) != Cards[c].suit) continue
                for (q in g.players.indices) {
                    val host = g.players[q].faction
                    if (host != id && g.reefs[reef].warriors(host) > 0) out += Hitch(c, reef, host)
                }
            }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Swim -> {
                Game.log(g, "Remoras: swim ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Attach -> {
                g.reefs[o.reef].addWarriors(id, -o.n)
                val m = s.attached.getOrPut(o.reef) { mutableMapOf() }
                m[o.host] = (m[o.host] ?: 0) + o.n
                Game.log(g, "Remoras: ${o.n} ${warriorNoun(o.n)} stick to the ${o.host.display} in ${Board.name(o.reef)}.")
            }
            is LetGo -> {
                val n = detach(s, o.reef, o.host)
                g.reefs[o.reef].addWarriors(id, n)
                Game.log(g, "Remoras: $n ${warriorNoun(n)} let go of the ${o.host.display} in ${Board.name(o.reef)}.")
            }
            is Clean -> {
                g.turn.use("clean")
                Game.log(g, "Remoras: open a cleaning station for the ${o.host.display} in ${Board.name(o.reef)}. Both draw a card.")
                Game.draw(g, g.player(o.host), 1)
                Game.draw(g, p, 1)
            }
            is Hitch -> {
                Game.discardFromHand(g, p, o.cardId)
                val n = minOf(HITCH, s.supply)
                s.supply -= n
                val m = s.attached.getOrPut(o.reef) { mutableMapOf() }
                m[o.host] = (m[o.host] ?: 0) + n
                Game.log(g, "Remoras: $n new ${warriorNoun(n)} hitch onto the ${o.host.display} in ${Board.name(o.reef)}.")
            }
            else -> error("Remoras can't ${o.describe()}")
        }
    }

    private fun detach(s: RemorasState, reef: Int, host: FactionId): Int {
        val m = s.attached[reef] ?: return 0
        val n = m.remove(host) ?: 0
        if (m.isEmpty()) s.attached.remove(reef)
        return n
    }

    /** When the last of a host's warriors leaves [from], the remoras riding them go too. */
    fun follow(g: GameState, host: FactionId, from: Int, to: Int) {
        val s = g.state<RemorasState>(id) ?: return
        if (host == id || g.reefs[from].warriors(host) > 0) return
        val n = detach(s, from, host)
        if (n == 0) return
        val m = s.attached.getOrPut(to) { mutableMapOf() }
        m[host] = (m[host] ?: 0) + n
        Game.log(g, "Remoras: $n ${warriorNoun(n)} ride along to ${Board.name(to)}.")
    }

    /** When a host's warriors in [reef] are all gone, the remoras riding them drop off and are free. */
    fun dropOff(g: GameState, host: FactionId, reef: Int) {
        val s = g.state<RemorasState>(id) ?: return
        if (host == id || g.reefs[reef].warriors(host) > 0) return
        val n = detach(s, reef, host)
        if (n == 0) return
        g.reefs[reef].addWarriors(id, n)
        Game.log(g, "Remoras: $n ${warriorNoun(n)} drop off in ${Board.name(reef)}.")
    }

    /** Drops off any remoras left without a host, whatever removed it. */
    fun settle(g: GameState) {
        val s = g.state<RemorasState>(id) ?: return
        for (reef in s.attached.keys.toList()) for (host in s.attached[reef]?.keys?.toList().orEmpty()) dropOff(g, host, reef)
    }

    /** Remoras riding [host] in [reef] add 1 hit each to the host's attacks there, up to 2. */
    fun pileOn(g: GameState, host: FactionId, reef: Int): Int = minOf(PILE_ON, attached(g, reef, host))

    /** Each reef where 3 or more remoras ride pays its suit. */
    override fun craftUnits(g: GameState, p: Int) = reefUnits(g, st(g, p).attached.filterValues { m -> m.values.sum() >= 3 }.keys.sorted())

    /** 1 VP when a faction the remoras ride removes pieces of a faction other than the remoras, once per attack. */
    fun scraps(g: GameState, by: Int, victim: Int, ctx: AttackCtx) {
        val s = g.state<RemorasState>(id) ?: return
        val fb = g.players[by].faction
        if (fb == id || g.players[victim].faction == id || fb in ctx.scrapsPaid || fb !in hosts(s)) return
        ctx.scrapsPaid += fb
        Game.scoreVp(g, g.player(id), 1, "scraps from the ${fb.display}")
    }

    override fun duskAuto(g: GameState, p: Int) {
        settle(g)
        val n = scoringHosts(st(g, p)).size
        Game.scoreVp(g, p, n, "riding $n faction${if (n == 1) "" else "s"}")
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

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return "${attachedTotal(s)} riding · ${Eval.onMap(g, id)} free · ${s.supply} waiting"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = Eval.onMap(g, id) * 1.5 + attachedTotal(s) * 3.0
        v += hosts(s).size * 3.0 + scoringHosts(s).size * 7.0
        for ((reef, m) in s.attached) for ((host, n) in m) {
            if (n <= 0) continue
            // Hosts that can fight feed the remoras.
            val q = g.player(host)
            if (q >= 0 && Game.battleTargets(g, q, reef).any { it != id }) v += 2.0
        }
        // Free remoras next to a host they could ride.
        for (reef in g.reefs.indices) {
            if (g.reefs[reef].warriors(id) == 0) continue
            val hostHere = g.players.any { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 }
            if (hostHere) v += 1.5
        }
        return v + Eval.hand(g, p, 1.0)
    }
}
