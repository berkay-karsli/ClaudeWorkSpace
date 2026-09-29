package com.reef.engine

/**
 * The Mantle and eight arms are all warriors. Each arm keeps a standing order (a card under it)
 * and carries it out every Day, in number order. Warrior counts on the map follow [OctopusState].
 */
object OctopusRules : FactionRules {
    override val id = FactionId.OCTOPUS
    override val setupHint = "the Mantle and all 8 arms in one reef that isn't a gate."
    const val ARMS = 8
    const val REACH = 2
    const val ORDERS_PER_DAWN = 2

    override fun warriorNoun(n: Int) = if (n == 1) "arm" else "arms"
    override fun newState(): FactionState = OctopusState()
    private fun st(g: GameState, p: Int) = g.players[p].fs as OctopusState

    fun orderName(suit: Suit): String = when (suit) {
        Suit.KELP -> "reach"
        Suit.SPONGE -> "grab"
        Suit.PEARL -> "steal"
        Suit.MOON -> "any order"
    }

    override fun dawnHint(g: GameState, p: Int) =
        if (st(g, p).mantle < 0) "the Mantle returns beside one of your arms (or at a gate if no arm is left)."
        else "put 1 or 2 cards under arms with no order. Kelp: reach · Sponge: grab · Pearl: steal · Moon: any."
    override fun duskHint(g: GameState, p: Int) = "lost arms regrow at the Mantle, then the garden scores."

    override fun setupOptions(g: GameState, p: Int): List<Option> =
        if (g.players[p].setupDone) emptyList() else Board.reefs.filter { !it.gate }.map { PlaceSetup(it.id) }

    override fun applySetup(g: GameState, p: Int, o: Option) {
        val reef = (o as PlaceSetup).reef
        val s = st(g, p)
        s.mantle = reef
        for (i in 0 until ARMS) s.arms[i] = reef
        sync(g, p)
        g.players[p].setupDone = true
        Game.log(g, "Octopus: settles into a den in ${Board.name(reef)}.")
    }

    override fun beginTurn(g: GameState, p: Int) {
        val s = st(g, p)
        s.nextArm = 0
        s.ordersAdded = 0
        s.ordersDone = false
        s.mantleDone = false
        s.rewired = false
    }

    // ---- Dawn: the Mantle comes back, then orders ------------------------------------------

    private fun mantleReturnReefs(g: GameState, p: Int): List<Int> {
        val arms = st(g, p).arms.filter { it >= 0 }.distinct().sorted()
        return arms.ifEmpty { Board.gates }
    }

    override fun dawnOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        if (s.mantle < 0) return mantleReturnReefs(g, p).map { MantleMove(it) }
        if (s.ordersDone) return emptyList()
        val out = mutableListOf<Option>()
        if (s.ordersAdded < ORDERS_PER_DAWN) {
            val open = (0 until ARMS).filter { s.arms[it] >= 0 && s.orders[it] == null }
            for (arm in open) for (c in g.players[p].hand.distinct()) out += SetOrder(arm, c)
        }
        if (!s.rewired) {
            // Swapping two orders only matters when they differ.
            for (a in 0 until ARMS) for (b in a + 1 until ARMS) {
                val x = s.orders[a]?.let { Cards[it].suit }
                val y = s.orders[b]?.let { Cards[it].suit }
                if (x != y) out += Rewire(a, b)
            }
        }
        return if (out.isEmpty()) out else out + Done("Start the Day")
    }

    override fun applyDawn(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is MantleMove -> {
                s.mantle = o.reef
                sync(g, p)
                Game.log(g, "Octopus: the Mantle returns in ${Board.name(o.reef)}.")
                snapBack(g, p)
            }
            is Rewire -> {
                val t = s.orders[o.a]
                s.orders[o.a] = s.orders[o.b]
                s.orders[o.b] = t
                s.rewired = true
                Game.log(g, "Octopus: arms ${o.a + 1} and ${o.b + 1} swap orders.")
            }
            is SetOrder -> {
                g.players[p].hand.remove(o.cardId)
                s.orders[o.arm] = o.cardId
                s.ordersAdded++
                Game.log(g, "Octopus: arm ${o.arm + 1} gets a new order: ${orderName(Cards[o.cardId].suit)}.")
            }
            else -> s.ordersDone = true
        }
    }

    // ---- Day: the arms act in order, then the Mantle may move ------------------------------

    /** The next arm with an order that is on the map, from [OctopusState.nextArm]. */
    private fun actingArm(s: OctopusState): Int? = (s.nextArm until ARMS).firstOrNull { s.orders[it] != null && s.arms[it] >= 0 }

    private fun armOptions(g: GameState, p: Int, arm: Int): List<Option> {
        val s = st(g, p)
        val reef = s.arms[arm]
        val suit = Cards[s.orders[arm]!!].suit
        val out = mutableListOf<Option>()
        if (suit == Suit.KELP || suit == Suit.MOON) {
            for (to in Game.neighbors(g, reef, id)) {
                if (s.mantle < 0 || Game.distance(g, to, s.mantle, id) <= REACH) out += ArmReach(arm, reef, to)
            }
        }
        if (suit == Suit.SPONGE || suit == Suit.MOON) out += Game.battleTargets(g, p, reef).map { ArmGrab(arm, reef, it) }
        if (suit == Suit.PEARL || suit == Suit.MOON) out += stealOptions(g, p, arm, reef)
        return out
    }

    private fun stealOptions(g: GameState, p: Int, arm: Int, reef: Int): List<Option> {
        val out = mutableListOf<Option>()
        val rs = g.reefs[reef]
        for (piece in rs.pieces.filter { !it.type.building && it.owner != null && it.owner != id }.distinctBy { it.owner to it.type }) {
            if (Game.rules(piece.owner!!).immune(g, g.player(piece.owner), reef)) continue
            out += ArmSteal(arm, reef, piece.owner, piece.type)
        }
        for (q in g.players.indices) {
            val f = g.players[q].faction
            if (f == id || g.players[q].hand.isEmpty()) continue
            if (rs.warriors(f) > 0 || rs.pieces.any { it.owner == f }) out += ArmSteal(arm, reef, f, null)
        }
        return out
    }

    override fun dayOptions(g: GameState, p: Int): List<Option> {
        val s = st(g, p)
        val arm = actingArm(s)
        if (arm != null) {
            val options = armOptions(g, p, arm)
            if (options.isNotEmpty()) return options
            val lost = (arm until ARMS).count { s.orders[it] != null }
            return listOf(Recoil(arm, lost))
        }
        if (s.mantleDone || s.mantle < 0) return emptyList()
        val reefs = s.arms.filter { it >= 0 && it != s.mantle }.distinct().sorted()
        return reefs.map { MantleMove(it) } + Done("The Mantle stays")
    }

    override fun canEndDay(g: GameState, p: Int): Boolean {
        val s = st(g, p)
        return actingArm(s) == null && (s.mantleDone || s.mantle < 0)
    }

    override fun applyDay(g: GameState, p: Int, o: Option) {
        val s = st(g, p)
        when (o) {
            is ArmReach -> {
                s.nextArm = o.arm + 1
                s.arms[o.arm] = o.to
                sync(g, p)
                Game.log(g, "Octopus: arm ${o.arm + 1} reaches from ${Board.name(o.from)} to ${Board.name(o.to)}.")
                Game.afterArrive(g, p, o.from, o.to, 1)
            }
            is ArmGrab -> {
                s.nextArm = o.arm + 1
                Game.log(g, "Octopus: arm ${o.arm + 1} grabs.")
                Game.startBattle(g, p, o.reef, o.prey)
            }
            is ArmSteal -> {
                s.nextArm = o.arm + 1
                steal(g, p, o)
            }
            is Recoil -> {
                s.nextArm = ARMS
                var lost = 0
                for (i in o.arm until ARMS) {
                    val c = s.orders[i] ?: continue
                    s.orders[i] = null
                    g.discard += c
                    lost++
                }
                Game.log(g, "Octopus: arm ${o.arm + 1} can't carry out its order and recoils. $lost order${if (lost == 1) " is" else "s are"} lost.")
                Game.loseVp(g, p, lost, "recoiling")
            }
            is MantleMove -> {
                val from = s.mantle
                s.mantle = o.reef
                s.mantleDone = true
                sync(g, p)
                Game.log(g, "Octopus: the Mantle jets to ${Board.name(o.reef)}.")
                Game.afterArrive(g, p, from, o.reef, 1)
                snapBack(g, p)
            }
            is Done -> s.mantleDone = true
            else -> error("Octopus can't ${o.describe()}")
        }
    }

    private fun steal(g: GameState, p: Int, o: ArmSteal) {
        val s = st(g, p)
        if (o.type != null) {
            val rs = g.reefs[o.reef]
            val piece = rs.pieces.first { it.owner == o.from && it.type == o.type }
            rs.pieces.remove(piece)
            s.garden += GardenItem("token", owner = piece.owner, type = piece.type, suit = piece.suit)
            Game.log(g, "Octopus: arm ${o.arm + 1} steals ${Game.possessive(o.from.display)} ${piece.type.label} for the garden.")
            Game.scoreVp(g, p, 1, "stealing a ${piece.type.label}")
        } else {
            val q = g.player(o.from)
            val hand = g.players[q].hand
            val card = hand[g.nextInt(hand.size)]
            hand.remove(card)
            s.garden += GardenItem("card", owner = o.from, suit = Cards[card].suit, card = card)
            Game.log(g, "Octopus: arm ${o.arm + 1} steals a ${Cards[card].suit.label} card from the ${o.from.display}.")
            Game.scoreVp(g, p, 1, "stealing a card")
        }
    }

    /** Arms more than 2 channels from the Mantle snap back to it. */
    private fun snapBack(g: GameState, p: Int) {
        val s = st(g, p)
        if (s.mantle < 0) return
        val moved = mutableSetOf<Int>()
        for (i in 0 until ARMS) {
            val reef = s.arms[i]
            if (reef >= 0 && Game.distance(g, reef, s.mantle, id) > REACH) {
                s.arms[i] = s.mantle
                moved += reef
            }
        }
        if (moved.isEmpty()) return
        sync(g, p)
        Game.log(g, "Octopus: arms out of reach snap back to the Mantle.")
        for (reef in moved) RemorasRules.follow(g, id, reef, s.mantle)
    }

    // ---- Ink, pushes and crafting -------------------------------------------------------------

    /** When battled in [reef], the octopus may discard any card to ink and jet to a neighboring reef. */
    fun inkOptions(g: GameState, p: Int, reef: Int): List<Option> {
        if (g.players[p].faction != id || g.reefs[reef].warriors(id) == 0) return emptyList()
        val dests = Game.neighbors(g, reef, id)
        return g.players[p].hand.distinct().flatMap { c -> dests.map { InkCloud(c, it) } }
    }

    fun ink(g: GameState, p: Int, attacker: Int, reef: Int, o: InkCloud) {
        Game.discardFromHand(g, p, o.cardId)
        Game.log(g, "Octopus: ink! The attack misses, and the octopus jets to ${Board.name(o.to)}.")
        val n = g.reefs[reef].warriors(id)
        jet(g, p, reef, o.to, n)
        g.lastBattle = BattleReport(g.players[attacker].faction, id, reef, emptyList(), 0, 0, listOf("Ink: the octopus jets to ${Board.name(o.to)}"), g.round, g.log.size)
    }

    /** Pushed arms go first, the Mantle last. */
    override fun displace(g: GameState, p: Int, from: Int, to: Int, n: Int) = jet(g, p, from, to, n)

    /** Moves [n] of the octopus's pieces from [from] to [to]: arms first, the Mantle last. */
    private fun jet(g: GameState, p: Int, from: Int, to: Int, n: Int) {
        val s = st(g, p)
        var left = n
        for (i in (0 until ARMS).reversed()) {
            if (left == 0) break
            if (s.arms[i] == from) {
                s.arms[i] = to
                left--
            }
        }
        val mantleMoved = left > 0 && s.mantle == from
        if (mantleMoved) s.mantle = to
        sync(g, p)
        Game.afterArrive(g, p, from, to, n)
        if (mantleMoved) snapBack(g, p)
    }

    /** Garden treasures: a stolen card pays its suit, a stolen token pays any suit. Crafting spends them. */
    override fun craftUnits(g: GameState, p: Int): List<CraftUnit> = st(g, p).garden.map { item ->
        if (item.kind == "card") CraftUnit("card:${item.card}", Cards[item.card!!].suit, spend = true)
        else CraftUnit("token:${item.owner}:${item.type}", Suit.MOON, spend = true)
    }

    override fun spendCraftUnit(g: GameState, p: Int, unit: CraftUnit) {
        val s = st(g, p)
        val item = s.garden.first { if (it.kind == "card") unit.key == "card:${it.card}" else unit.key == "token:${it.owner}:${it.type}" }
        s.garden.remove(item)
        if (item.kind == "card") g.discard += item.card!!
        else {
            val q = g.player(item.owner!!)
            if (q >= 0) Game.rules(item.owner).pieceRemoved(g, q, Piece(item.owner, item.type!!, item.suit))
        }
        Game.log(g, "Octopus: spend a treasure from the garden to craft.")
    }

    // ---- Dusk --------------------------------------------------------------------------------

    fun gardenKinds(s: OctopusState): Int =
        s.garden.filter { it.kind == "token" }.map { it.type }.distinct().size + s.garden.filter { it.kind == "card" }.map { it.suit }.distinct().size

    override fun duskAuto(g: GameState, p: Int) {
        val s = st(g, p)
        if (s.mantle >= 0) {
            val regrown = (0 until ARMS).filter { s.arms[it] < 0 }
            for (i in regrown) s.arms[i] = s.mantle
            if (regrown.isNotEmpty()) {
                sync(g, p)
                Game.log(g, "Octopus: ${regrown.size} ${warriorNoun(regrown.size)} regrow in ${Board.name(s.mantle)}.")
            }
        }
        val kinds = gardenKinds(s)
        Game.scoreVp(g, p, kinds, "$kinds kind${if (kinds == 1) "" else "s"} of treasure in the garden")
    }

    // ---- Losses --------------------------------------------------------------------------------

    /** Hits take arms first (arms without orders, then the highest numbers) and the Mantle last. */
    override fun removeWarriors(g: GameState, p: Int, reef: Int, n: Int, arrivals: Int) {
        val s = st(g, p)
        repeat(n) {
            val arm = (0 until ARMS).filter { s.arms[it] == reef }.maxWithOrNull(compareBy<Int> { s.orders[it] == null }.thenBy { it })
            if (arm != null) {
                s.arms[arm] = -1
            } else if (s.mantle == reef) {
                s.mantle = -1
                loseGarden(g, p)
            }
        }
        sync(g, p)
    }

    /** With the Mantle gone, every treasure goes back: tokens to their owners, cards to the discard pile. */
    private fun loseGarden(g: GameState, p: Int) {
        val s = st(g, p)
        Game.log(g, "Octopus: the Mantle is lost, and the garden with it.")
        for (item in s.garden) {
            if (item.kind == "card") g.discard += item.card!!
            else {
                val q = g.player(item.owner!!)
                if (q >= 0) Game.rules(item.owner).pieceRemoved(g, q, Piece(item.owner, item.type!!, item.suit))
            }
        }
        s.garden.clear()
    }

    fun sync(g: GameState, p: Int) {
        val s = st(g, p)
        for (reef in g.reefs.indices) g.reefs[reef].setWarriors(id, s.arms.count { it == reef } + (if (s.mantle == reef) 1 else 0))
    }

    override fun supplySummary(g: GameState, p: Int): String {
        val s = st(g, p)
        val arms = s.arms.count { it >= 0 }
        val orders = s.orders.count { it != null }
        val kinds = gardenKinds(s)
        return "$arms of $ARMS arms · $orders order${if (orders == 1) "" else "s"} · ${s.garden.size} treasure${if (s.garden.size == 1) "" else "s"} of $kinds kind${if (kinds == 1) "" else "s"}"
    }

    override fun value(g: GameState, p: Int, self: Boolean): Double {
        val s = st(g, p)
        var v = s.arms.count { it >= 0 } * 2.5 + gardenKinds(s) * 7.0 + s.garden.size * 1.0
        if (s.mantle >= 0) {
            v += 8.0
            val guard = g.reefs[s.mantle].warriors(id)
            v -= maxOf(0, Eval.threat(g, p, s.mantle) - guard + 1) * 2.0
        } else if (s.garden.isEmpty()) v -= 2.0
        // Standing orders that have something to do next Day.
        for (i in 0 until ARMS) {
            val c = s.orders[i] ?: continue
            if (s.arms[i] < 0) continue
            val reef = s.arms[i]
            val suit = Cards[c].suit
            // A standing order acts every turn: worth more than the card it costs, unless it
            // has nothing to do where the arm is and will recoil.
            val enemies = g.players.any { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 }
            val loot = g.reefs[reef].pieces.any { !it.type.building && it.owner != null && it.owner != id } ||
                g.players.any { it.faction != id && it.hand.isNotEmpty() && (g.reefs[reef].warriors(it.faction) > 0 || g.reefs[reef].pieces.any { pc -> pc.owner == it.faction }) }
            v += when (suit) {
                Suit.MOON -> 4.0
                Suit.KELP -> 3.0
                Suit.SPONGE -> if (enemies) 3.0 else -2.0
                Suit.PEARL -> if (loot) 3.0 else -2.0
            }
            if (suit == Suit.PEARL || suit == Suit.MOON) {
                if (g.reefs[reef].pieces.any { !it.type.building && it.owner != null && it.owner != id }) v += 2.0
                else if (g.players.any { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 }) v += 1.0
            }
            if (suit == Suit.SPONGE && g.players.any { it.faction != id && g.reefs[reef].warriors(it.faction) > 0 }) v += 1.5
        }
        return v + Eval.hand(g, p)
    }
}
