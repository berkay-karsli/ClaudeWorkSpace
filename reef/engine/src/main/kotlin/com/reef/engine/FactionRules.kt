package com.reef.engine

/**
 * Everything that makes one faction different. The shared rules in [Game] call these hooks; each
 * faction implements exactly what its plate in the design guide says, and nothing else.
 */
interface FactionRules {
    val id: FactionId

    /** How many warriors each warrior piece counts as for rule. */
    val ruleWeight: Int get() = 1

    /** How many hits each warrior piece can deal in a battle. */
    val hitWeight: Int get() = 1


    /** Day actions per turn, or null when the faction's Day isn't counted in actions. */
    val actionsPerDay: Int? get() = null

    /** Whether the faction can spend a move to dig out a sandbar. */
    val canDig: Boolean get() = false

    val setupHint: String
    fun dawnHint(g: GameState, p: Int): String = "nothing to do."
    fun duskHint(g: GameState, p: Int): String = "nothing to do."

    fun warriorNoun(n: Int): String

    fun newState(): FactionState
    fun onNewGame(g: GameState, p: Int) {}
    fun setupOptions(g: GameState, p: Int): List<Option>
    fun applySetup(g: GameState, p: Int, o: Option)
    fun beginTurn(g: GameState, p: Int) {}
    fun dawnOptions(g: GameState, p: Int): List<Option> = emptyList()
    fun applyDawn(g: GameState, p: Int, o: Option) {}

    /** The faction's own Day actions. Shared ones (crafting, extra action, Dominance, End Day) are added by [Game]. */
    fun dayOptions(g: GameState, p: Int): List<Option>

    /** Day options that never use an action. */
    fun freeDayOptions(g: GameState, p: Int): List<Option> = emptyList()
    fun applyDay(g: GameState, p: Int, o: Option)
    fun canEndDay(g: GameState, p: Int): Boolean = true
    fun bonusActions(g: GameState, p: Int): Int = 0

    /** Whether the faction can take an action that costs one right now. */
    fun canAct(g: GameState, p: Int): Boolean = actionsPerDay == null || g.turn.actionsLeft > 0

    /** Pays for one action. */
    fun payAction(g: GameState, p: Int) {
        if (actionsPerDay != null) g.turn.actionsLeft--
    }

    /** Called once when the faction's Day ends and its Dusk begins. */
    fun onDuskStart(g: GameState, p: Int) {}

    /** Dusk choices, made before the automatic Dusk steps. */
    fun duskOptions(g: GameState, p: Int): List<Option> = emptyList()
    fun applyDusk(g: GameState, p: Int, o: Option) {}
    fun duskAuto(g: GameState, p: Int) {}

    /** Cards drawn at Dusk on top of the shared one. */
    fun extraDraws(g: GameState, p: Int): Int = 0

    /** Extra hits this faction can deal when defending in [reef], beyond its warriors. */
    fun defenseCapBonus(g: GameState, p: Int, reef: Int): Int = 0

    /** Whether this faction's pieces in [reef] can't be attacked at all. */
    fun immune(g: GameState, p: Int, reef: Int): Boolean = false

    /**
     * The faction's crafting pieces on the map, whether or not they have paid this turn. By
     * default each building pays its reef's suit.
     */
    fun craftUnits(g: GameState, p: Int): List<CraftUnit> = buildingUnits(g, p)

    /** Uses up a crafting piece that is spent when it pays. */
    fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {}

    /** The most hits this faction's warriors in [reef] can deal when it attacks. */
    fun attackCap(g: GameState, p: Int, reef: Int): Int = hitWeight * g.reefs[reef].warriors(id)

    /** Whether another faction can push this faction's warriors out of [reef]. */
    fun pushable(g: GameState, p: Int, reef: Int): Boolean = !immune(g, p, reef)

    /** Moves [n] of this faction's warriors from [from] to [to] because another faction pushed them. */
    fun displace(g: GameState, p: Int, from: Int, to: Int, n: Int) = Game.moveWarriors(g, p, from, to, n)

    /** Removes [n] of this faction's warriors from [reef] and returns them to the supply. */
    fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int)

    /** Tracking after [Game.moveWarriors] moved this faction's warriors. */
    fun onMoved(g: GameState, p: Int, from: Int, to: Int, n: Int) {}

    /** Called after one of this faction's buildings or tokens was removed from the map. */
    fun pieceRemoved(g: GameState, p: Int, piece: Piece) {}

    /** One line for the faction board, such as "7 sharks in the open ocean". */
    fun supplySummary(g: GameState, p: Int): String

    /** How good [p]'s position is, for bots. [self] adds features that only matter to the player choosing now. */
    fun value(g: GameState, p: Int, self: Boolean): Double
}

/**
 * One crafting piece. It pays [suit] once per turn ([Suit.MOON]: any suit). Pieces with the same
 * [key] are alike. A [spend] piece is used up when it pays.
 */
data class CraftUnit(val key: String, val suit: Suit, val spend: Boolean = false)

/** Each of [p]'s buildings pays its reef's suit. */
fun buildingUnits(g: GameState, p: Int): List<CraftUnit> {
    val f = g.players[p].faction
    return g.reefs.indices.flatMap { reef -> List(g.reefs[reef].buildingsOf(f)) { CraftUnit("reef:$reef", g.suitOf(reef)) } }
}

/** One crafting piece for each reef in [reefs], paying that reef's suit. */
fun reefUnits(g: GameState, reefs: List<Int>): List<CraftUnit> = reefs.map { CraftUnit("reef:$it", g.suitOf(it)) }

/** Shared bot helpers. */
object Eval {
    fun enemyPresence(g: GameState, p: Int, reef: Int): Int = g.players.indices.filter { it != p }.sumOf { Game.presence(g, it, reef) }

    fun onMap(g: GameState, f: FactionId): Int = g.reefs.sumOf { it.warriors(f) }

    /** Rough value of a hand: cards are actions and options for everyone. */
    fun hand(g: GameState, p: Int, perCard: Double = 1.5): Double = g.players[p].hand.sumOf {
        when (Cards[it].kind) {
            CardKind.AMBUSH -> perCard + 1.0
            CardKind.DOMINANCE -> 0.5
            CardKind.GEAR -> perCard + Cards[it].vp * 0.3
        }
    }

    /** Reefs where enemy warriors could reach [reef] in one move. */
    fun threat(g: GameState, p: Int, reef: Int): Int = g.players.indices.filter { it != p }.sumOf { q ->
        val f = g.players[q].faction
        g.reefs[reef].warriors(f) * Game.rules(f).hitWeight + Board.neighbors(reef).sumOf { g.reefs[it].warriors(f) } / 2
    }
}

object SharksRules : FactionRules {
    override val id = FactionId.SHARKS
    override val ruleWeight = 2
    override val hitWeight = 2
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "place 3 sharks at any gate."
    const val SETUP_SHARKS = 3
    const val BLOOD_VP = 2
    const val FRENZY = 2

    override fun dawnHint(g: GameState, p: Int) = "${arrivals(g, p)} shark${if (arrivals(g, p) == 1) "" else "s"} arrive at a gate of your choice."
    override fun duskHint(g: GameState, p: Int) = "each Blood with your sharks: feed on it for $BLOOD_VP VP, or frenzy and $FRENZY sharks arrive there."
    override fun warriorNoun(n: Int) = if (n == 1) "shark" else "sharks"
    override fun newState(): FactionState = SharksState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as SharksState

    /** Sharks arriving this Dawn: 1, or 2 with none on the map. */
    fun arrivals(g: GameState, p: Int): Int = minOf(if (Eval.onMap(g, id) == 0) 2 else 1, st(g, p).supply)

    /** Reefs with Blood and your sharks: meals, frenzies or crafting. */
    fun bloodReefs(g: GameState): List<Int> = g.reefs.indices.filter { g.reefs[it].has(PieceType.BLOOD) && g.reefs[it].warriors(id) > 0 }

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.gates.map { PlaceSetup(it) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        g.reefs[reef].addWarriors(id, SETUP_SHARKS)
        st(g, p).supply -= SETUP_SHARKS
        g.players[p].setupDone = true
        Game.log(g, "Sharks: $SETUP_SHARKS sharks gather at ${Board.name(reef)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        st(g, p).moved.clear()
        st(g, p).arrived = false
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val n = arrivals(g, p)
        return if (!st(g, p).arrived && n > 0) Board.gates.map { Arrive(it, n, warriorNoun(n)) } else emptyList()
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        val s = st(g, p)
        g.reefs[a.reef].addWarriors(id, a.n)
        s.supply -= a.n
        s.arrived = true
        Game.log(g, "Sharks: ${a.n} ${warriorNoun(a.n)} arrive at ${Board.name(a.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        for (from in g.reefs.indices) {
            val n = g.reefs[from].warriors(id)
            if (n == 0) continue
            val targets = linkedMapOf<Int, Boolean>()
            for (to in Game.neighbors(g, from, id)) {
                if (Game.ruledBy(g, from, p) || Game.ruledBy(g, to, p) || Game.isCurrent(g, from, to)) targets[to] = false
            }
            for (to in g.reefs.indices) {
                if (to != from && to !in targets && g.reefs[to].has(PieceType.BLOOD) && Game.distance(g, from, to, id) <= 2) targets[to] = true
            }
            // The open ocean: from a gate, any other gate is one move away.
            if (Board.reefs[from].gate) for (to in Board.gates) if (to != from && to !in targets) targets[to] = false
            for ((to, scent) in targets) {
                val prey = Game.battleTargets(g, p, to)
                for (k in n downTo 1) {
                    out += Hunt(from, to, k, scent, null)
                    for (f in prey) out += Hunt(from, to, k, scent, f)
                }
            }
        }
        return out
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val m = o as Hunt
        val how = when {
            m.scent -> ", following Blood."
            Board.reefs[m.from].gate && Board.reefs[m.to].gate && m.to !in Board.neighbors(m.from) -> " through the open ocean."
            else -> "."
        }
        Game.log(g, "Sharks: move ${m.n} ${warriorNoun(m.n)} from ${Board.name(m.from)} to ${Board.name(m.to)}$how")
        Game.moveWarriors(g, p, m.from, m.to, m.n)
        if (m.prey != null && g.reefs[m.to].warriors(id) > 0 && m.prey in Game.battleTargets(g, p, m.to)) Game.startBattle(g, p, m.to, m.prey)
    }

    override fun onMoved(g: GameState, p: Int, from: Int, to: Int, n: Int) {
        val s = st(g, p)
        val movedHere = s.moved[from] ?: 0
        val unmovedBefore = g.reefs[from].warriors(id) + n - movedHere
        // Unmoved sharks go first, so as few as possible are left to drown.
        val takeMoved = n - minOf(n, unmovedBefore)
        setMoved(s, from, movedHere - takeMoved)
        setMoved(s, to, (s.moved[to] ?: 0) + n)
    }

    override fun duskOptions(g: GameState, p: Int): List<Option> {
        val reef = bloodReefs(g).firstOrNull() ?: return emptyList()
        val n = minOf(FRENZY, st(g, p).supply)
        return listOf(FeedBlood(reef)) + (if (n > 0) listOf(Frenzy(reef, n)) else emptyList())
    }

    override fun applyDusk(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        val reef = o.reefs.first()
        g.reefs[reef].pieces.remove(Piece(null, PieceType.BLOOD))
        g.blood++
        when (o) {
            is FeedBlood -> {
                Game.log(g, "Sharks: feed on the Blood in ${Board.name(reef)}.")
                Game.scoreVp(g, p, BLOOD_VP, "eating Blood in ${Board.name(reef)}")
            }
            is Frenzy -> {
                g.reefs[reef].addWarriors(id, o.n)
                s.supply -= o.n
                // Frenzied sharks are already swimming hard: they count as having moved.
                setMoved(s, reef, (s.moved[reef] ?: 0) + o.n)
                Game.log(g, "Sharks: frenzy in ${Board.name(reef)}! ${o.n} more ${warriorNoun(o.n)} arrive.")
            }
            else -> error("Sharks can't ${o.describe()} at Dusk")
        }
    }

    override fun duskAuto(g: GameState, p: Int) {
        val s = st(g, p)
        for (reef in g.reefs.indices) {
            val here = g.reefs[reef].warriors(id)
            val drowned = here - (s.moved[reef] ?: 0)
            if (drowned > 0) {
                g.reefs[reef].addWarriors(id, -drowned)
                s.supply += drowned
                RemorasRules.dropOff(g, id, reef)
                Game.log(g, "Sharks: $drowned ${warriorNoun(drowned)} stayed still in ${Board.name(reef)} and drowned.")
            }
        }
        s.moved.clear()
    }

    /** Blood in a reef with your sharks pays that reef's suit, and is eaten. */
    override fun craftUnits(g: GameState, p: Int): List<CraftUnit> = bloodReefs(g).map { CraftUnit("blood:$it", g.suitOf(it), spend = true) }

    override fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {
        val reef = unit.key.removePrefix("blood:").toInt()
        g.reefs[reef].pieces.remove(Piece(null, PieceType.BLOOD))
        g.blood++
        Game.log(g, "Sharks: eat the Blood in ${Board.name(reef)} to craft.")
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        val before = g.reefs[reef].warriors(id)
        g.reefs[reef].addWarriors(id, -n)
        s.supply += n
        // Arrivals hit by a sting are moved sharks; otherwise the unmoved ones go first.
        val moved = s.moved[reef] ?: 0
        val lostMoved = if (arrivals > 0) minOf(n, moved) else maxOf(0, n - (before - moved))
        setMoved(s, reef, moved - lostMoved)
    }

    override fun supplySummary(g: GameState, p: Int) = "${st(g, p).supply} sharks in the open ocean"

    private fun setMoved(s: SharksState, reef: Int, n: Int) {
        if (n <= 0) s.moved.remove(reef) else s.moved[reef] = n
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = 0.0
        val onMap = Eval.onMap(g, id)
        // A small pack is worth a lot more per shark than a big one.
        v += minOf(onMap, 6) * 9.0 + maxOf(0, onMap - 6) * 5.0 + s.supply * 1.0
        if (self && g.current == p && g.phase == Phase.DAY) v -= (onMap - s.moved.values.sum()) * 8.0
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val here = rs.warriors(id)
            if (rs.has(PieceType.BLOOD)) {
                v += when {
                    here > 0 -> 12.0
                    g.reefs.indices.any { g.reefs[it].warriors(id) > 0 && Board.distance(it, reef) <= 2 } -> 4.0
                    else -> 0.0
                }
            }
            val reach = g.reefs.indices.any { g.reefs[it].warriors(id) > 0 && Board.distance(it, reef) <= 1 }
            if (reach) v += g.players.indices.filter { it != p }.sumOf { rs.pieces.count { pc -> pc.owner == g.players[it].faction } } * 0.8
        }
        return v + Eval.hand(g, p)
    }
}

object CoralRules : FactionRules {
    override val id = FactionId.CORAL
    override val actionsPerDay = 3
    override val setupHint = "2 coral and 3 polyps in a reef that isn't a gate, and 1 polyp in each neighboring reef."
    const val SETUP_CORAL = 2
    const val SETUP_POLYPS = 3
    const val DRIFT_POLYPS = 2

    override fun warriorNoun(n: Int) = if (n == 1) "polyp" else "polyps"
    override fun duskHint(g: GameState, p: Int) = "score the Nursery: 1 VP per reef with your coral where another faction has warriors."
    override fun newState(): FactionState = CoralState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as CoralState

    fun coralOnMap(g: GameState): Int = g.reefs.sumOf { it.buildingsOf(id) }

    /** Each coral scores when grown: 1 for each 4 coral on the map, at least 1. */
    fun growVp(coralOnMapAfter: Int): Int = maxOf(1, coralOnMapAfter / 4)

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { !it.gate && Game.freeSlots(g, it.id) >= SETUP_CORAL }.map { PlaceSetup(it.id) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        val s = st(g, p)
        repeat(SETUP_CORAL) { g.reefs[reef].pieces.add(Piece(id, PieceType.CORAL)) }
        s.coral -= SETUP_CORAL
        placePolyps(g, s, reef, SETUP_POLYPS)
        for (n in Board.neighbors(reef)) placePolyps(g, s, n, 1)
        g.players[p].setupDone = true
        Game.log(g, "Coral: takes root in ${Board.name(reef)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        st(g, p).spawned = false
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val hand = g.players[p].hand
        val out = mutableListOf<Option>()
        // A turn that bleaches can't grow.
        if (s.coral > 0 && g.turn.used("bleach") == 0) {
            for (reef in g.reefs.indices) {
                if (Game.freeSlots(g, reef) <= 0 || !Game.ruledBy(g, reef, p)) continue
                for (c in hand) if (Cards[c].suit == Suit.MOON || Cards[c].suit == g.suitOf(reef)) out += Grow(reef, c)
            }
        }
        if (!s.spawned && s.polyps > 0) {
            if (coralOnMap(g) == 0) {
                // No coral left: larvae drift in to one reef of the card's suit.
                for (c in hand) for (reef in g.reefs.indices) {
                    if (Cards[c].suit == Suit.MOON || g.suitOf(reef) == Cards[c].suit) out += Spawn(c, reef)
                }
            } else {
                for (c in hand) if (spawnSources(g, Cards[c].suit).isNotEmpty()) out += Spawn(c)
            }
        }
        out += Game.battleOptions(g, p)
        return out
    }

    /** Bleaching: once a turn, a coral becomes 2 cards. */
    override fun freeDayOptions(g: GameState, p: Int): List<Option> =
        if (g.turn.used("bleach") > 0 || g.turn.used("grow") > 0) emptyList() else g.reefs.indices.filter { g.reefs[it].buildingsOf(id) > 0 }.map { Bleach(it) }

    /** Reefs with your coral where another faction has warriors. */
    fun nursery(g: GameState): Int = g.reefs.count { rs -> rs.buildingsOf(id) > 0 && rs.warriors.keys.any { it != id } }

    private fun spawnSources(g: GameState, suit: Suit): List<Int> =
        g.reefs.indices.filter { g.reefs[it].buildingsOf(id) > 0 && (suit == Suit.MOON || g.suitOf(it) == suit) }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Grow -> {
                g.turn.use("grow")
                Game.discardFromHand(g, p, o.cardId)
                g.reefs[o.reef].pieces.add(Piece(id, PieceType.CORAL))
                s.coral--
                Game.log(g, "Coral: grow coral in ${Board.name(o.reef)}.")
                Game.scoreVp(g, p, growVp(coralOnMap(g)), "growing coral")
            }
            is Spawn -> if (o.into != null) {
                Game.discardFromHand(g, p, o.cardId)
                s.spawned = true
                val placed = placePolyps(g, s, o.into, DRIFT_POLYPS)
                Game.log(g, "Coral: with no coral left, $placed ${warriorNoun(placed)} drift into ${Board.name(o.into)}.")
            } else {
                val sources = spawnSources(g, Cards[o.cardId].suit)
                Game.discardFromHand(g, p, o.cardId)
                s.spawned = true
                var placed = 0
                for (src in sources) for (n in Game.neighbors(g, src, id)) placed += placePolyps(g, s, n, 1)
                Game.log(g, "Coral: spawn from ${sources.joinToString { Board.name(it) }}, $placed new ${warriorNoun(placed)}.")
            }
            is Battle -> Game.startBattle(g, p, o.reef, o.defender)
            is Bleach -> {
                g.turn.use("bleach")
                Game.log(g, "Coral: a coral in ${Board.name(o.reef)} bleaches white.")
                Game.removePiece(g, o.reef, g.reefs[o.reef].pieces.first { it.owner == id && it.type == PieceType.CORAL })
                // Bleached coral is dead: it leaves the game instead of going back to the supply.
                val s = st(g, p)
                s.coral--
                s.bleached++
                Game.draw(g, p, 2)
            }
            else -> error("Coral can't ${o.describe()}")
        }
    }

    override fun duskAuto(g: GameState, p: Int) {
        val n = nursery(g)
        Game.scoreVp(g, p, n, "sheltering visitors in $n reef${if (n == 1) "" else "s"}")
    }

    override fun pushable(g: GameState, p: Int, reef: Int) = false

    /** Each reef with your coral pays its suit, however many coral it has. */
    override fun craftUnits(g: GameState, p: Int) = reefUnits(g, g.reefs.indices.filter { g.reefs[it].buildingsOf(id) > 0 })

    override fun extraDraws(g: GameState, p: Int) = coralOnMap(g) / 5

    override fun defenseCapBonus(g: GameState, p: Int, reef: Int) = g.reefs[reef].buildingsOf(id)

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).polyps += n
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.CORAL) st(g, p).coral++
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        return "${s.polyps} polyps and ${s.coral} coral left to place" + (if (s.bleached > 0) " · ${s.bleached} bleached" else "")
    }

    private fun placePolyps(g: GameState, s: CoralState, reef: Int, n: Int): Int {
        val k = minOf(n, s.polyps)
        if (k > 0) {
            g.reefs[reef].addWarriors(id, k)
            s.polyps -= k
        }
        return k
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        var v = 0.0
        val growable = mutableSetOf<Suit>()
        val spawnable = mutableSetOf<Suit>()
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val coral = rs.buildingsOf(id)
            v += coral * 5.0 + rs.warriors(id) * 1.5
            if (coral > 0) spawnable += g.suitOf(reef)
            // Visitors pay every Dusk.
            if (coral > 0 && rs.warriors.keys.any { it != id }) v += 6.0
            if (Game.ruledBy(g, reef, p)) {
                v += 2.0
                if (Game.freeSlots(g, reef) > 0) {
                    v += 2.0
                    growable += g.suitOf(reef)
                }
            }
            if (coral > 0) v -= minOf(Eval.threat(g, p, reef), coral) * 1.5
        }
        if (spawnable.isEmpty()) spawnable += Suit.entries
        for (c in g.players[p].hand) {
            val card = Cards[c]
            v += when {
                card.kind == CardKind.DOMINANCE -> 0.5
                card.suit == Suit.MOON -> 3.5
                card.suit in growable -> 3.0
                card.suit in spawnable -> 2.5
                else -> 1.5
            }
            if (card.kind == CardKind.GEAR) v += card.vp * 0.5
        }
        return v
    }
}
