package com.reef.engine

/**
 * Everything that makes one faction different. The shared rules in [Game] call these hooks;
 * each faction implements exactly what its plate in the design guide says, and nothing else.
 */
interface FactionRules {
    val id: FactionId

    /** How many warriors each warrior piece counts as, for rule and for hits dealt. */
    val warriorWeight: Int get() = 1

    /** Only factions with buildings craft. */
    val canCraft: Boolean

    /** Day actions per turn, or null when the faction's Day isn't counted in actions. */
    val actionsPerDay: Int?

    val setupHint: String
    val dawnHint: String get() = "nothing to do."

    fun warriorNoun(n: Int): String

    fun newState(): FactionState
    fun setupOptions(g: GameState, p: Int): List<Option>
    fun applySetup(g: GameState, p: Int, o: Option)
    fun beginTurn(g: GameState, p: Int) {}
    fun dawnOptions(g: GameState, p: Int): List<Option> = emptyList()
    fun applyDawn(g: GameState, p: Int, o: Option) {}

    /** The faction's own Day actions. Shared ones (crafting, extra action, Dominance, End Day) are added by [Game]. */
    fun dayOptions(g: GameState, p: Int): List<Option>
    fun applyDay(g: GameState, p: Int, o: Option)
    fun dusk(g: GameState, p: Int) {}

    /** Cards drawn at Dusk on top of the shared one. */
    fun extraDraws(g: GameState, p: Int): Int = 0

    /** Extra hits this faction can deal when defending in [reef], beyond its warriors. */
    fun defenseCapBonus(g: GameState, p: Int, reef: Int): Int = 0

    /** Called after [n] of this faction's warriors were removed from [reef]; return them to the supply. */
    fun warriorsRemoved(g: GameState, p: Int, reef: Int, n: Int)

    /** Called after one of this faction's buildings or tokens was removed from the map. */
    fun pieceRemoved(g: GameState, p: Int, piece: Piece) {}

    /** One line for the faction board, such as "7 sharks in supply". */
    fun supplySummary(g: GameState, p: Int): String
}

object SharksRules : FactionRules {
    override val id = FactionId.SHARKS
    override val warriorWeight = 2
    override val canCraft = false
    override val actionsPerDay = 3
    override val setupHint = "place 3 sharks at any gate."
    override val dawnHint = "a new shark arrives at a gate of your choice."
    const val SETUP_SHARKS = 3

    override fun warriorNoun(n: Int) = if (n == 1) "shark" else "sharks"
    override fun newState(): FactionState = SharksState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as SharksState

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
        val s = st(g, p)
        return if (!s.arrived && s.supply > 0) Board.gates.map { Arrive(it) } else emptyList()
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val reef = (o as Arrive).reef
        val s = st(g, p)
        g.reefs[reef].addWarriors(id, 1)
        s.supply--
        s.arrived = true
        Game.log(g, "Sharks: a shark arrives at ${Board.name(reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val out = mutableListOf<Option>()
        for (from in g.reefs.indices) {
            val n = g.reefs[from].warriors(id)
            if (n == 0) continue
            val targets = linkedMapOf<Int, Boolean>()
            for (to in Board.neighbors(from)) {
                if (Game.ruledBy(g, from, p) || Game.ruledBy(g, to, p) || Board.isCurrent(from, to)) targets[to] = false
            }
            for (to in g.reefs.indices) {
                if (to != from && to !in targets && g.reefs[to].has(PieceType.BLOOD) && Board.distance(from, to) <= 2) targets[to] = true
            }
            for ((to, scent) in targets) {
                val prey = g.players.indices.filter { it != p }.map { g.players[it].faction }
                    .filter { f -> g.reefs[to].warriors(f) > 0 || g.reefs[to].pieces.any { it.owner == f } }
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
        val s = st(g, p)
        val movedHere = s.moved[m.from] ?: 0
        val unmoved = g.reefs[m.from].warriors(id) - movedHere
        // Unmoved sharks go first, so as few as possible are left to drown.
        val takeMoved = m.n - minOf(m.n, unmoved)
        setMoved(s, m.from, movedHere - takeMoved)
        g.reefs[m.from].addWarriors(id, -m.n)
        g.reefs[m.to].addWarriors(id, m.n)
        setMoved(s, m.to, (s.moved[m.to] ?: 0) + m.n)
        Game.log(g, "Sharks: move ${m.n} ${warriorNoun(m.n)} from ${Board.name(m.from)} to ${Board.name(m.to)}" + (if (m.scent) ", following Blood." else "."))
        m.prey?.let { Game.startBattle(g, p, m.to, it) }
    }

    override fun dusk(g: GameState, p: Int) {
        for (reef in g.reefs.indices) {
            val rs = g.reefs[reef]
            if (rs.warriors(id) == 0) continue
            val eaten = rs.pieces.count { it.type == PieceType.BLOOD }
            if (eaten == 0) continue
            rs.pieces.removeAll { it.type == PieceType.BLOOD }
            g.blood += eaten
            Game.scoreVp(g, p, eaten, "eating Blood in ${Board.name(reef)}")
            if (g.winner != null) return
        }
        val s = st(g, p)
        for (reef in g.reefs.indices) {
            val here = g.reefs[reef].warriors(id)
            val drowned = here - (s.moved[reef] ?: 0)
            if (drowned > 0) {
                g.reefs[reef].addWarriors(id, -drowned)
                s.supply += drowned
                Game.log(g, "Sharks: $drowned ${warriorNoun(drowned)} stayed still in ${Board.name(reef)} and drowned.")
            }
        }
        s.moved.clear()
    }

    override fun warriorsRemoved(g: GameState, p: Int, reef: Int, n: Int) {
        val s = st(g, p)
        s.supply += n
        setMoved(s, reef, minOf(s.moved[reef] ?: 0, g.reefs[reef].warriors(id)))
    }

    override fun supplySummary(g: GameState, p: Int) = "${st(g, p).supply} sharks in the open ocean"

    private fun setMoved(s: SharksState, reef: Int, n: Int) {
        if (n <= 0) s.moved.remove(reef) else s.moved[reef] = n
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
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { !it.gate && it.slots >= SETUP_CORAL }.map { PlaceSetup(it.id) }

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
                for (src in sources) for (n in Board.neighbors(src)) placed += placePolyps(g, s, n, 1)
                Game.log(g, "Coral: spawn from ${sources.joinToString { Board.name(it) }}, $placed new ${warriorNoun(placed)}.")
            }
            else -> error("Coral can't ${o.describe()}")
        }
    }

    override fun extraDraws(g: GameState, p: Int) = coralOnMap(g) / 5

    override fun defenseCapBonus(g: GameState, p: Int, reef: Int) = g.reefs[reef].buildingsOf(id)

    override fun warriorsRemoved(g: GameState, p: Int, reef: Int, n: Int) {
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
}
