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

    /** Only factions with buildings craft. */
    val canCraft: Boolean get() = false

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
    const val MAX_ARRIVALS = 3
    const val BLOOD_VP = 2

    override fun dawnHint(g: GameState, p: Int) = "${arrivals(g, p)} shark${if (arrivals(g, p) == 1) "" else "s"} arrive at a gate of your choice (1, plus 1 per Blood token, up to 3)."
    override fun warriorNoun(n: Int) = if (n == 1) "shark" else "sharks"
    override fun newState(): FactionState = SharksState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as SharksState

    /** Sharks arriving this Dawn: 1, plus 1 per Blood token on the map, up to 3. */
    fun arrivals(g: GameState, p: Int): Int {
        val blood = g.reefs.sumOf { it.count(PieceType.BLOOD) }
        return minOf(MAX_ARRIVALS, 1 + blood, st(g, p).supply)
    }

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
        Game.log(g, "Sharks: move ${m.n} ${warriorNoun(m.n)} from ${Board.name(m.from)} to ${Board.name(m.to)}" + (if (m.scent) ", following Blood." else "."))
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

    override fun duskAuto(g: GameState, p: Int) {
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            if (rs.warriors(id) == 0) continue
            val eaten = rs.count(PieceType.BLOOD)
            if (eaten == 0) continue
            rs.pieces.removeAll { it.type == PieceType.BLOOD }
            g.blood += eaten
            Game.scoreVp(g, p, eaten * BLOOD_VP, "eating Blood in ${Board.name(reef)}")
            if (g.winner != null) return
        }
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
        v += onMap * 9.0 + s.supply * 2.0
        if (self && g.current == p && g.phase == Phase.DAY) v -= (onMap - s.moved.values.sum()) * 8.0
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            val here = rs.warriors(id)
            if (rs.has(PieceType.BLOOD)) {
                v += when {
                    here > 0 -> 7.0
                    g.reefs.indices.any { g.reefs[it].warriors(id) > 0 && Board.distance(it, reef) <= 2 } -> 2.5
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
    override val canCraft = true
    override val actionsPerDay = 3
    override val setupHint = "2 coral and 3 polyps in a reef that isn't a gate, and 1 polyp in each neighboring reef."
    const val SETUP_CORAL = 2
    const val SETUP_POLYPS = 3
    const val DRIFT_POLYPS = 2

    override fun warriorNoun(n: Int) = if (n == 1) "polyp" else "polyps"
    override fun newState(): FactionState = CoralState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as CoralState

    fun coralOnMap(g: GameState): Int = g.reefs.sumOf { it.buildingsOf(id) }

    /** Each coral scores when grown: 1, 1, 1, 2, 2, 2, 3... counting coral beyond the two you start with. */
    fun growVp(coralOnMapAfter: Int): Int = maxOf(1, coralOnMapAfter / 3)

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
        if (s.coral > 0) {
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
        return out
    }

    private fun spawnSources(g: GameState, suit: Suit): List<Int> =
        g.reefs.indices.filter { g.reefs[it].buildingsOf(id) > 0 && (suit == Suit.MOON || g.suitOf(it) == suit) }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Grow -> {
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
            else -> error("Coral can't ${o.describe()}")
        }
    }

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
        return "${s.polyps} polyps and ${s.coral} coral left to place"
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
