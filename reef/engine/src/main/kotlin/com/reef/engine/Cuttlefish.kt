package com.reef.engine

/** A Gallery card: a pattern of reefs that must all hold cuttlefish. */
data class Pattern(val id: Int, val name: String, val text: String, val vp: Int, val met: (GameState, Set<Int>) -> Boolean)

object Gallery {
    private fun connected(reefs: List<Int>): Boolean {
        val seen = mutableSetOf(reefs[0])
        val queue = ArrayDeque(listOf(reefs[0]))
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            for (n in Board.neighbors(r)) if (n in reefs && seen.add(n)) queue.addLast(n)
        }
        return seen.size == reefs.size
    }

    private fun <T> triples(xs: List<T>): Sequence<List<T>> = sequence {
        for (i in xs.indices) for (j in i + 1 until xs.size) for (k in j + 1 until xs.size) yield(listOf(xs[i], xs[j], xs[k]))
    }

    private fun trio(suit: Suit) = { g: GameState, held: Set<Int> ->
        triples(held.filter { g.suitOf(it) == suit }).any { connected(it) }
    }

    private fun neighborsOf(a: Suit, b: Suit) = { g: GameState, held: Set<Int> ->
        held.any { x -> g.suitOf(x) == a && Board.neighbors(x).any { it in held && g.suitOf(it) == b } }
    }

    val all: List<Pattern> = listOf(
        Pattern(0, "Kelp Forest", "3 connected Kelp reefs", 3, trio(Suit.KELP)),
        Pattern(1, "Sponge Garden", "3 connected Sponge reefs", 3, trio(Suit.SPONGE)),
        Pattern(2, "Pearl Bed", "3 connected Pearl reefs", 3, trio(Suit.PEARL)),
        Pattern(3, "Rainbow", "3 connected reefs, one of each suit", 3, { g, held ->
            triples(held.toList()).any { t -> t.map { g.suitOf(it) }.toSet().size == 3 && connected(t) }
        }),
        Pattern(4, "Twin Gates", "2 gates of the same suit", 2, { g, held ->
            held.filter { Board.reefs[it].gate }.groupBy { g.suitOf(it) }.values.any { it.size >= 2 }
        }),
        Pattern(5, "Far Mirror", "a pair of opposite gates of the same suit", 4, { g, held ->
            Board.oppositeGates.any { (a, b) -> a in held && b in held && g.suitOf(a) == g.suitOf(b) }
        }),
        Pattern(6, "Deep Pair", "2 rim reefs of the same suit", 2, { g, held ->
            held.filter { Board.reefs[it].rim }.groupBy { g.suitOf(it) }.values.any { it.size >= 2 }
        }),
        Pattern(7, "Shoreline", "3 shore reefs, one of each suit", 3, { g, held ->
            held.filter { Board.reefs[it].shore }.map { g.suitOf(it) }.toSet().size == 3
        }),
        Pattern(8, "Monochrome", "4 reefs of one suit", 3, { g, held ->
            held.groupBy { g.suitOf(it) }.values.any { it.size >= 4 }
        }),
        Pattern(9, "Kelp by Sponge", "a Kelp reef next to a Sponge reef", 2, neighborsOf(Suit.KELP, Suit.SPONGE)),
        Pattern(10, "Sponge by Pearl", "a Sponge reef next to a Pearl reef", 2, neighborsOf(Suit.SPONGE, Suit.PEARL)),
        Pattern(11, "Pearl by Kelp", "a Pearl reef next to a Kelp reef", 2, neighborsOf(Suit.PEARL, Suit.KELP)),
    )

    operator fun get(id: Int) = all[id]
}

object CuttlefishRules : FactionRules {
    override val id = FactionId.CUTTLEFISH
    override val actionsPerDay = 3
    override val canDig = true
    override val setupHint = "3 cuttlefish in each of two reefs."
    const val SETUP = 3
    const val GALLERY = 3

    override fun warriorNoun(n: Int) = "cuttlefish"
    override fun newState(): FactionState = CuttlefishState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as CuttlefishState

    override fun dawnHint(g: GameState, p: Int) = "with no pigment on the map, 2 cuttlefish arrive at a gate of your choice."
    override fun duskHint(g: GameState, p: Int) = "score one Gallery pattern your cuttlefish hold."

    /** A reef is camouflaged while it holds a cuttlefish pigment. */
    fun camouflaged(g: GameState, reef: Int): Boolean = g.reefs[reef].pieces.any { it.type == PieceType.PIGMENT && it.owner == id }

    fun held(g: GameState): Set<Int> = g.reefs.indices.filter { g.reefs[it].warriors(id) > 0 }.toSet()
    fun pigmentsOnMap(g: GameState): Int = g.reefs.sumOf { it.count(PieceType.PIGMENT, id) }

    override fun onNewGame(g: GameState, p: Int) {
        val s = st(g, p)
        s.galleryDeck += Gallery.all.map { it.id }
        Game.shuffle(g, s.galleryDeck)
        repeat(GALLERY) { s.gallery += s.galleryDeck.removeAt(0) }
    }

    override fun setupOptions(g: GameState, p: Int): List<Option> {
        if (g.players[p].setupDone) return emptyList()
        val first = st(g, p).setupFirst
        return Board.reefs.filter { it.id != first }.map { PlaceSetup(it.id) }
    }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        place(g, p, reef, SETUP)
        val s = st(g, p)
        if (s.setupFirst == null) s.setupFirst = reef else g.players[p].setupDone = true
        Game.log(g, "Cuttlefish: $SETUP cuttlefish settle in ${Board.name(reef)}.")
    }

    /** 1 cuttlefish arrives at each of your pigments. */
    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.arrived = false
        var n = 0
        for (reef in g.reefs.indices) if (g.reefs[reef].count(PieceType.PIGMENT, id) > 0) n += place(g, p, reef, 1)
        if (n > 0) {
            s.arrived = true
            Game.log(g, "Cuttlefish: $n cuttlefish arrive at your pigments.")
        }
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.arrived || s.supply == 0 || pigmentsOnMap(g) > 0) return emptyList()
        return Board.gates.map { Arrive(it, minOf(2, s.supply), "cuttlefish") }
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val a = o as Arrive
        st(g, p).arrived = true
        place(g, p, a.reef, a.n)
        Game.log(g, "Cuttlefish: ${a.n} cuttlefish arrive at ${Board.name(a.reef)}.")
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> = Game.standardMoves(g, p) + paintOptions(g, p)

    private fun paintOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val out = mutableListOf<Option>()
        val reefs = held(g)
        for (c in g.players[p].hand.distinct()) {
            val suits = if (Cards[c].suit == Suit.MOON) listOf(Suit.KELP, Suit.SPONGE, Suit.PEARL) else listOf(Cards[c].suit)
            for (suit in suits) {
                val available = (s.pigments[suit] ?: 0) > 0 || g.reefs.indices.any { r -> pigmentAt(g, r)?.suit == suit }
                if (!available) continue
                for (reef in reefs) if (g.suitOf(reef) != suit) out += Paint(reef, c, suit)
            }
        }
        return out
    }

    private fun pigmentAt(g: GameState, reef: Int): Piece? = g.reefs[reef].pieces.firstOrNull { it.type == PieceType.PIGMENT && it.owner == id }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is Move -> {
                Game.log(g, "Cuttlefish: move ${o.n} from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.moveWarriors(g, p, o.from, o.to, o.n)
            }
            is Paint -> {
                Game.discardFromHand(g, p, o.cardId)
                // A reef holds one pigment: the old one comes back first.
                pigmentAt(g, o.reef)?.let { old ->
                    g.reefs[o.reef].pieces.remove(old)
                    s.pigments[old.suit!!] = (s.pigments[old.suit] ?: 0) + 1
                }
                if ((s.pigments[o.suit] ?: 0) == 0) {
                    // No pigment of that suit left: move one, preferring one where no cuttlefish sit.
                    val held = held(g)
                    val from = g.reefs.indices.filter { pigmentAt(g, it)?.suit == o.suit }.minBy { if (it in held) 1 else 0 }
                    g.reefs[from].pieces.remove(pigmentAt(g, from))
                    Game.log(g, "Cuttlefish: the ${o.suit.label} pigment leaves ${Board.name(from)}.")
                } else {
                    s.pigments[o.suit] = s.pigments.getValue(o.suit) - 1
                }
                g.reefs[o.reef].pieces.add(Piece(id, PieceType.PIGMENT, suit = o.suit))
                Game.log(g, "Cuttlefish: paint ${Board.name(o.reef)} ${o.suit.label}.")
            }
            else -> error("Cuttlefish can't ${o.describe()}")
        }
    }

    override fun duskAuto(g: GameState, p: Int) {
        val s = st(g, p)
        val held = held(g)
        // One pattern per Dusk: the best one held.
        val slot = s.gallery.indices.filter { Gallery[s.gallery[it]].met(g, held) }.maxByOrNull { Gallery[s.gallery[it]].vp } ?: return
        val pattern = Gallery[s.gallery[slot]]
        Game.scoreVp(g, p, pattern.vp, "the ${pattern.name} pattern")
        s.galleryDeck += pattern.id
        s.gallery[slot] = s.galleryDeck.removeAt(0)
        Game.log(g, "Cuttlefish: a new Gallery card: ${Gallery[s.gallery[slot]].name}.")
    }

    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        g.reefs[reef].addWarriors(id, -n)
        st(g, p).supply += n
    }

    override fun pieceRemoved(g: GameState, p: Int, piece: Piece) {
        if (piece.type == PieceType.PIGMENT) {
            val s = st(g, p)
            s.pigments[piece.suit!!] = (s.pigments[piece.suit] ?: 0) + 1
        }
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
        return "${s.supply} cuttlefish and ${s.pigments.values.sum()} pigments left"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        val held = held(g)
        var v = Eval.onMap(g, id) * 1.5 + held.size * 2.5 + pigmentsOnMap(g) * 2.0
        for (id in s.gallery) {
            val pattern = Gallery[id]
            if (pattern.met(g, held)) v += pattern.vp * 4.0
        }
        for (reef in held) if (g.reefs[reef].warriors(this.id) == 1) v -= Eval.threat(g, p, reef).coerceAtMost(3) * 0.5
        return v + Eval.hand(g, p, 1.6)
    }
}
