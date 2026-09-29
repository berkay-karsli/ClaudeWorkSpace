package com.reef.engine

import kotlin.math.max
import kotlin.math.min

/** A seat at the table: which faction, and whether a person or a bot plays it. */
data class Seat(val faction: FactionId, val human: Boolean)

/** What removed a piece, for the rules that care (stings score for jellyfish, snaps for anglers...). */
enum class Source { BATTLE, AMBUSH, VENOM, SNAP, STING, GORGE, BAIT_BALL, COIL }

/** Bookkeeping for one attack: a turtle shell ignores its first hit, remora scraps pay once. */
class AttackCtx {
    var shellUsed = false
    val scrapsPaid = mutableSetOf<FactionId>()
}

/**
 * The shared rules. A game is a [GameState] plus two calls: [decision] says who must choose and
 * from what, and [apply] carries out a choice and runs the game forward to the next decision.
 */
object Game {
    const val WIN_VP = 30
    const val HAND_LIMIT = 5
    const val STARTING_HAND = 3
    const val DOMINANCE_MIN_VP = 10

    fun rules(f: FactionId): FactionRules = when (f) {
        FactionId.SHARKS -> SharksRules
        FactionId.SARDINES -> SardinesRules
        FactionId.LIONFISH -> LionfishRules
        FactionId.STARFISH -> StarfishRules
        FactionId.CORAL -> CoralRules
        FactionId.JELLYFISH -> JellyfishRules
        FactionId.PARROTFISH -> ParrotfishRules
        FactionId.TURTLES -> TurtlesRules
        FactionId.SNAKE -> SnakeRules
        FactionId.REMORAS -> RemorasRules
        FactionId.CRABS -> CrabsRules
        FactionId.ANGLERS -> AnglersRules
        FactionId.OCTOPUS -> OctopusRules
        FactionId.CUTTLEFISH -> CuttlefishRules
    }

    fun rules(g: GameState, p: Int): FactionRules = rules(g.players[p].faction)

    fun newGame(seats: List<Seat>, seed: Long): GameState {
        require(seats.size in 2..4) { "A game has 2 to 4 factions" }
        require(seats.map { it.faction }.distinct().size == seats.size) { "Each faction can only be played once" }
        require(FactionId.REMORAS !in seats.map { it.faction } || seats.size >= 3) { "Remoras need at least two other factions" }
        val g = GameState(
            players = seats.map { PlayerState(it.faction, it.human, rules(it.faction).newState()) }.toMutableList(),
            reefs = MutableList(Board.reefs.size) { ReefState() },
            drawPile = Cards.all.map { it.id }.toMutableList(),
            discard = mutableListOf(),
            rng = seed,
        )
        shuffle(g, g.drawPile)
        for (p in g.players.indices) {
            draw(g, p, STARTING_HAND)
            rules(g, p).onNewGame(g, p)
        }
        log(g, "A new game: " + g.players.joinToString(" vs ") { it.faction.display } + ".")
        advance(g)
        return g
    }

    // ---- Decisions -------------------------------------------------------------------------

    fun decision(g: GameState): Decision? {
        if (g.phase == Phase.OVER) return null
        g.pending.lastOrNull()?.let { return pendingDecision(g, it) }
        val p = g.current
        val name = g.players[p].faction.display
        val r = rules(g, p)
        return when (g.phase) {
            Phase.SETUP -> Decision(p, "$name, set up: ${r.setupHint}", r.setupOptions(g, p))
            Phase.DAWN -> Decision(p, "$name, Dawn: ${r.dawnHint(g, p)}", r.dawnOptions(g, p))
            Phase.DAY -> {
                val actions = if (r.actionsPerDay != null) ", ${g.turn.actionsLeft} action${if (g.turn.actionsLeft == 1) "" else "s"} left" else ""
                Decision(p, "$name, Day$actions", dayOptions(g, p))
            }
            Phase.DUSK -> if (!g.turn.duskStarted) {
                Decision(p, "$name, Dusk: ${r.duskHint(g, p)}", r.duskOptions(g, p))
            } else {
                Decision(p, "$name, Dusk: discard down to $HAND_LIMIT cards", g.players[p].hand.map { Discard(it) })
            }
            Phase.OVER -> null
        }
    }

    private fun pendingDecision(g: GameState, pending: Pending): Decision {
        val p = pending.player
        val name = g.players[p].faction.display
        return when (pending) {
            is DefendPending -> {
                val a = g.players[pending.attacker].faction.display
                val options = ambushCards(g, p, pending.reef).map<Int, Option> { PlayAmbush(it) }.toMutableList()
                if (g.players[p].faction == FactionId.SARDINES && g.reefs[pending.reef].warriors(FactionId.SARDINES) > 0) {
                    options += neighbors(g, pending.reef, FactionId.SARDINES).map { BaitBall(it) }
                }
                options += OctopusRules.inkOptions(g, p, pending.reef)
                options += NoAmbush
                Decision(p, "$name: $a attack you in ${Board.name(pending.reef)}. How do you defend?", options)
            }
            is ShopPending -> Decision(p, "$name: the Hermit Crabs sell shells for ${CrabsRules.price(g)} card${if (CrabsRules.price(g) == 1) "" else "s"} each. Each one blocks the next hit on your pieces in its reef.", CrabsRules.shopOptions(g, p) + Done("Buy nothing more"))
            is PayPending -> Decision(p, "$name: pay ${pending.left} more card${if (pending.left == 1) "" else "s"} for the shell", g.players[p].hand.map { PayCard(it) })
        }
    }

    fun apply(g: GameState, o: Option) {
        val d = decision(g) ?: error("The game is over")
        require(o in d.options) { "Not a legal choice now: ${o.describe()}" }
        val p = d.player
        val pending = g.pending.lastOrNull()
        when {
            pending != null -> applyPending(g, pending, o)
            g.phase == Phase.SETUP -> rules(g, p).applySetup(g, p, o)
            g.phase == Phase.DAWN -> rules(g, p).applyDawn(g, p, o)
            g.phase == Phase.DAY -> applyDay(g, p, o)
            g.phase == Phase.DUSK -> if (!g.turn.duskStarted) {
                rules(g, p).applyDusk(g, p, o)
            } else {
                val c = (o as Discard).cardId
                discardFromHand(g, p, c)
                log(g, "${g.players[p].faction.display}: discard ${Cards[c].name}.")
            }
            else -> {}
        }
        RemorasRules.settle(g)
        advance(g)
    }

    private fun applyPending(g: GameState, pending: Pending, o: Option) {
        when (pending) {
            is DefendPending -> {
                g.pending.removeAt(g.pending.lastIndex)
                if (o is InkCloud) {
                    OctopusRules.ink(g, pending.player, pending.attacker, pending.reef, o)
                    if (pending.snap > 0) AnglersRules.sinkBack(g, pending.reef)
                } else {
                    resolveBattle(g, pending.attacker, pending.player, pending.reef, (o as? PlayAmbush)?.cardId, (o as? BaitBall)?.to, pending.snap)
                }
            }
            is ShopPending -> when (o) {
                is BuyShell -> g.pending.add(PayPending(pending.player, o.reef, CrabsRules.price(g)))
                else -> g.pending.removeAt(g.pending.lastIndex)
            }
            is PayPending -> {
                val card = (o as PayCard).cardId
                g.players[pending.player].hand.remove(card)
                CrabsRules.receive(g, card)
                pending.left--
                if (pending.left == 0) {
                    g.pending.removeAt(g.pending.lastIndex)
                    CrabsRules.sell(g, pending.player, pending.reef)
                    // Stop offering once nothing more can be bought.
                    val shop = g.pending.lastOrNull() as? ShopPending
                    if (shop != null && CrabsRules.shopOptions(g, shop.player).isEmpty()) g.pending.removeAt(g.pending.lastIndex)
                }
            }
        }
    }

    private fun dayOptions(g: GameState, p: Int): List<Option> {
        val r = rules(g, p)
        val pl = g.players[p]
        val out = mutableListOf<Option>()
        if (r.canAct(g, p)) {
            out += r.dayOptions(g, p)
            if (r.canDig) out += digOptions(g, p)
        }
        out += r.freeDayOptions(g, p)
        out += pl.hand.filter { Cards[it].kind == CardKind.GEAR && craftPayment(g, p, Cards[it].cost) != null }.map { Craft(it) }
        if (r.actionsPerDay != null && !g.turn.surgeUsed) out += pl.hand.map { Surge(it) }
        if (pl.dominance == null && pl.vp >= DOMINANCE_MIN_VP) out += pl.hand.filter { Cards[it].kind == CardKind.DOMINANCE }.map { PlayDominance(it) }
        if (r.canEndDay(g, p)) out += EndDay
        return out
    }

    private fun applyDay(g: GameState, p: Int, o: Option) {
        val pl = g.players[p]
        when (o) {
            is Craft -> {
                val card = Cards[o.cardId]
                for (unit in craftPayment(g, p, card.cost)!!) {
                    if (unit.spend) rules(g, p).spendCraftUnit(g, p, unit) else g.turn.use("craft:${unit.key}")
                }
                pl.hand.remove(o.cardId)
                pl.gear.add(o.cardId)
                log(g, "${pl.faction.display}: craft ${card.name}.")
                scoreVp(g, p, card.vp, "crafting ${card.name}")
            }
            is Surge -> {
                discardFromHand(g, p, o.cardId)
                g.turn.surgeUsed = true
                g.turn.actionsLeft++
                log(g, "${pl.faction.display}: discard ${Cards[o.cardId].name} for an extra action.")
            }
            is PlayDominance -> {
                pl.hand.remove(o.cardId)
                pl.dominance = o.cardId
                log(g, "${pl.faction.display}: play ${Cards[o.cardId].name}. ${Cards[o.cardId].describe()} No more VP from now on.")
            }
            EndDay -> enterDusk(g, p)
            is DigOut -> {
                rules(g, p).payAction(g, p)
                val m = g.markers.first { it.type == MarkerType.SANDBAR && it.joins(o.a, o.b) }
                g.markers.remove(m)
                g.state<ParrotfishState>(FactionId.PARROTFISH)?.let { it.sandbars++ }
                log(g, "${pl.faction.display}: dig out the sandbar between ${Board.name(o.a)} and ${Board.name(o.b)}.")
            }
            else -> {
                if (o.costsAction) rules(g, p).payAction(g, p)
                rules(g, p).applyDay(g, p, o)
            }
        }
    }

    // ---- Turn flow -------------------------------------------------------------------------

    fun advance(g: GameState) {
        while (true) {
            if (g.winner != null) {
                g.phase = Phase.OVER
                g.pending.clear()
                return
            }
            if (g.pending.isNotEmpty()) return
            val p = g.current
            val r = rules(g, p)
            when (g.phase) {
                Phase.SETUP -> {
                    if (!g.players[p].setupDone) return
                    val next = g.players.indexOfFirst { !it.setupDone }
                    if (next >= 0) g.current = next else {
                        g.current = 0
                        startTurn(g)
                    }
                }
                Phase.DAWN -> {
                    if (r.dawnOptions(g, p).isNotEmpty()) return
                    g.phase = Phase.DAY
                    val fixed = r.actionsPerDay
                    g.turn.actionsLeft = if (fixed == null) 0 else fixed + r.bonusActions(g, p) + gearCount(g, p, GearEffect.EXTRA_ACTION)
                }
                Phase.DAY -> {
                    if (!g.turn.shopDone) {
                        g.turn.shopDone = true
                        if (CrabsRules.shopOptions(g, p).isNotEmpty()) {
                            g.pending.add(ShopPending(p))
                            continue
                        }
                    }
                    val options = dayOptions(g, p)
                    if (options.size == 1 && options[0] == EndDay) {
                        enterDusk(g, p)
                        continue
                    }
                    return
                }
                Phase.DUSK -> {
                    if (!g.turn.duskStarted) {
                        if (r.duskOptions(g, p).isNotEmpty()) return
                        g.turn.duskStarted = true
                        r.duskAuto(g, p)
                        if (g.winner != null) continue
                        scoreGlory(g, p)
                        if (g.winner != null) continue
                        val n = 1 + r.extraDraws(g, p) + gearCount(g, p, GearEffect.EXTRA_DRAW)
                        draw(g, p, n)
                        log(g, "${g.players[p].faction.display}: draw $n card${if (n == 1) "" else "s"}.")
                    }
                    if (g.players[p].hand.size > HAND_LIMIT) return
                    g.current = (p + 1) % g.players.size
                    if (g.current == 0) g.round++
                    startTurn(g)
                }
                Phase.OVER -> return
            }
        }
    }

    private fun enterDusk(g: GameState, p: Int) {
        log(g, "${g.players[p].faction.display}: end the Day.")
        g.phase = Phase.DUSK
        rules(g, p).onDuskStart(g, p)
    }

    private fun startTurn(g: GameState) {
        val p = g.current
        val pl = g.players[p]
        g.turn = Turn()
        g.phase = Phase.DAWN
        log(g, "Round ${g.round}, ${pl.faction.display} to play.")
        pl.dominance?.let { card ->
            if (dominanceMet(g, p, card)) win(g, p, "${pl.faction.display}: victory by ${Cards[card].name}.")
        }
        rules(g, p).beginTurn(g, p)
    }

    // ---- Map: neighbors, currents, rule --------------------------------------------------------

    fun sandbarBetween(g: GameState, a: Int, b: Int): Boolean = g.markers.any { it.type == MarkerType.SANDBAR && it.joins(a, b) }

    /** Neighbors of [reef] for faction [f]: a sandbar cuts a channel for everyone but the Parrotfish. */
    fun neighbors(g: GameState, reef: Int, f: FactionId?): List<Int> =
        Board.neighbors(reef).filter { f == FactionId.PARROTFISH || !sandbarBetween(g, reef, it) }

    /** Whether moving from [from] to [to] follows a current (printed or a Jellyfish arrow). */
    fun isCurrent(g: GameState, from: Int, to: Int): Boolean =
        Board.isCurrent(from, to) || g.markers.any { it.type == MarkerType.CURRENT && it.joins(from, to) && it.from == from }

    fun distance(g: GameState, a: Int, b: Int, f: FactionId?): Int {
        if (a == b) return 0
        val dist = mutableMapOf(a to 0)
        val queue = ArrayDeque(listOf(a))
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            for (n in neighbors(g, x, f)) if (n !in dist) {
                dist[n] = dist.getValue(x) + 1
                if (n == b) return dist.getValue(n)
                queue.addLast(n)
            }
        }
        return Int.MAX_VALUE
    }

    fun presence(g: GameState, p: Int, reef: Int): Int {
        val f = g.players[p].faction
        val rs = g.reefs[reef]
        return rules(f).ruleWeight * rs.warriors(f) + rs.buildingsOf(f)
    }

    /** The player who rules [reef], or null if nobody does. Parrotfish islands are always theirs. */
    fun ruler(g: GameState, reef: Int): Int? {
        g.state<ParrotfishState>(FactionId.PARROTFISH)?.let { if (reef in it.islands) return g.player(FactionId.PARROTFISH) }
        var best = -1
        var bestValue = 0
        var tied = false
        for (p in g.players.indices) {
            val v = presence(g, p, reef)
            if (v > bestValue) {
                best = p; bestValue = v; tied = false
            } else if (v == bestValue && v > 0) {
                tied = true
            }
        }
        return if (best >= 0 && !tied) best else null
    }

    fun ruledBy(g: GameState, reef: Int, p: Int): Boolean = ruler(g, reef) == p

    fun freeSlots(g: GameState, reef: Int): Int = Board.reefs[reef].slots - g.reefs[reef].pieces.count { it.type.fillsSlot }

    // ---- Movement ------------------------------------------------------------------------------

    /** Standard moves: any number of warriors to a neighboring reef, ruling where they leave or arrive. */
    fun standardMoves(g: GameState, p: Int): List<Option> {
        val f = g.players[p].faction
        val out = mutableListOf<Option>()
        for (from in g.reefs.indices) {
            val n = g.reefs[from].warriors(f)
            if (n == 0) continue
            for (to in neighbors(g, from, f)) {
                if (ruledBy(g, from, p) || ruledBy(g, to, p) || isCurrent(g, from, to)) {
                    for (k in n downTo 1) out += Move(from, to, k)
                }
            }
        }
        return out
    }

    /** Moves [n] of [p]'s warriors and runs everything that happens when warriors arrive. */
    fun moveWarriors(g: GameState, p: Int, from: Int, to: Int, n: Int) {
        if (n <= 0) return
        val f = g.players[p].faction
        g.reefs[from].addWarriors(f, -n)
        g.reefs[to].addWarriors(f, n)
        rules(f).onMoved(g, p, from, to, n)
        afterArrive(g, p, from, to, n)
    }

    /** What arriving warriors trigger: riding remoras follow, Treasure lures pay, jellyfish sting. */
    fun afterArrive(g: GameState, p: Int, from: Int, to: Int, arrivals: Int) {
        val f = g.players[p].faction
        RemorasRules.follow(g, f, from, to)
        if (f != FactionId.ANGLERS && g.reefs[to].pieces.any { it.type == PieceType.LURE && it.variant == AnglersRules.TREASURE }) {
            val key = "treasure:${f.name}"
            if (g.turn.used(key) == 0) {
                g.turn.use(key)
                draw(g, p, 1)
                log(g, "${f.display}: take the Treasure lure's bait in ${Board.name(to)} and draw a card.")
                AnglersRules.hook(g, f)
            }
        }
        if (f != FactionId.JELLYFISH && g.inGame(FactionId.JELLYFISH) && g.reefs[to].warriors(FactionId.JELLYFISH) >= 2) {
            log(g, "Jellyfish sting ${f.display} arriving in ${Board.name(to)}.")
            hit(g, g.player(FactionId.JELLYFISH), p, to, 1, Source.STING, AttackCtx(), arrivals)
        }
    }

    private fun digOptions(g: GameState, p: Int): List<Option> {
        val f = g.players[p].faction
        if (f == FactionId.PARROTFISH) return emptyList()
        return g.markers.filter { it.type == MarkerType.SANDBAR && g.reefs[it.a].warriors(f) > 0 && g.reefs[it.b].warriors(f) > 0 }
            .map { DigOut(it.a, it.b) }
    }

    // ---- Attacks -------------------------------------------------------------------------------

    /** The factions [a] may battle in [reef]. */
    fun battleTargets(g: GameState, a: Int, reef: Int): List<FactionId> {
        val fa = g.players[a].faction
        val rs = g.reefs[reef]
        val sheltered = fa != FactionId.ANGLERS && rs.pieces.any { it.type == PieceType.LURE && it.variant == AnglersRules.SHELTER }
        val out = mutableListOf<FactionId>()
        for (q in g.players.indices) {
            if (q == a) continue
            val fq = g.players[q].faction
            if (rs.warriors(fq) == 0 && rs.pieces.none { it.owner == fq }) continue
            if (rules(fq).immune(g, q, reef)) continue
            if (sheltered && fq != FactionId.ANGLERS) continue
            if (fq == FactionId.CUTTLEFISH && CuttlefishRules.camouflaged(g, reef) && camouflageCard(g, a, reef) == null) continue
            out += fq
        }
        return out
    }

    fun battleOptions(g: GameState, p: Int): List<Option> =
        g.reefs.indices.filter { g.reefs[it].warriors(g.players[p].faction) > 0 }.flatMap { reef -> battleTargets(g, p, reef).map { Battle(reef, it) } }

    /** The card an attacker discards to find camouflaged cuttlefish: the least useful one of the reef's suit. */
    fun camouflageCard(g: GameState, a: Int, reef: Int): Int? =
        g.players[a].hand.filter { Cards[it].suit == g.suitOf(reef) || Cards[it].suit == Suit.MOON }
            .minByOrNull { (if (Cards[it].suit == Suit.MOON) 10 else 0) + Cards[it].vp }

    fun ambushCards(g: GameState, p: Int, reef: Int): List<Int> =
        g.players[p].hand.filter { val c = Cards[it]; c.kind == CardKind.AMBUSH && (c.suit == Suit.MOON || c.suit == g.suitOf(reef)) }

    /**
     * Starts a battle. [snap] anglers rise from the Trench for it. The defender may interrupt with
     * an Ambush (or a bait ball); otherwise the battle is resolved at once.
     */
    fun startBattle(g: GameState, a: Int, reef: Int, defender: FactionId, snap: Int = 0) {
        val d = g.player(defender)
        val fa = g.players[a].faction
        log(g, "${fa.display}: battle ${defender.display} in ${Board.name(reef)}.")
        if (defender == FactionId.CUTTLEFISH && CuttlefishRules.camouflaged(g, reef)) {
            val card = camouflageCard(g, a, reef)!!
            discardFromHand(g, a, card)
            log(g, "${fa.display}: discard ${Cards[card].name} to spot the camouflaged cuttlefish.")
        }
        if (snap > 0) g.reefs[reef].addWarriors(FactionId.ANGLERS, snap)
        val bait = defender == FactionId.SARDINES && g.reefs[reef].warriors(defender) > 0
        if (ambushCards(g, d, reef).isNotEmpty() || bait || OctopusRules.inkOptions(g, d, reef).isNotEmpty()) {
            g.pending.add(DefendPending(d, a, reef, snap))
            return
        }
        resolveBattle(g, a, d, reef, null, null, snap)
    }

    private fun resolveBattle(g: GameState, a: Int, d: Int, reef: Int, ambush: Int?, baitTo: Int?, snap: Int) {
        fight(g, a, d, reef, ambush, baitTo, snap)
        // Whatever happened, surviving anglers sink back into the Trench.
        if (snap > 0) AnglersRules.sinkBack(g, reef)
    }

    private fun fight(g: GameState, a: Int, d: Int, reef: Int, ambush: Int?, baitTo: Int?, snap: Int) {
        val fa = g.players[a].faction
        val fd = g.players[d].faction
        val rs = g.reefs[reef]
        val notes = mutableListOf<String>()
        val ctxA = AttackCtx()
        val ctxD = AttackCtx()
        fun finish(dice: List<Int>, aHits: Int, dHits: Int) {
            g.lastBattle = BattleReport(fa, fd, reef, dice, aHits, dHits, notes, g.round, g.log.size)
        }
        if (baitTo != null) {
            val school = rs.warriors(FactionId.SARDINES)
            val lost = school / 2
            log(g, "Sardines: bait ball! $lost of $school are lost and the rest flee to ${Board.name(baitTo)}.")
            notes += "Bait ball: $lost sardines lost, ${school - lost} fled"
            removeByAttack(g, a, d, reef, lost, Source.BAIT_BALL, ctxA)
            moveWarriors(g, d, reef, baitTo, rs.warriors(FactionId.SARDINES))
            finish(emptyList(), lost, 0)
            return
        }
        if (ambush != null) {
            discardFromHand(g, d, ambush)
            log(g, "${fd.display}: ambush with ${Cards[ambush].name}.")
            notes += "${fd.display} ambush: 2 hits before the roll"
            hit(g, d, a, reef, 2, Source.AMBUSH, ctxD)
            if (g.winner != null) return
            if (rs.warriors(fa) == 0) {
                log(g, "The ambush stops the attack before it starts.")
                notes += "The attack never lands"
                finish(emptyList(), 0, 2)
                return
            }
        }
        if (fd == FactionId.LIONFISH && rs.warriors(fd) > 0) {
            log(g, "Lionfish venom: ${fa.display} take 1 hit before the dice.")
            notes += "Lionfish venom: 1 hit to ${fa.display}"
            hit(g, d, a, reef, 1, Source.VENOM, ctxD)
            if (g.winner != null) return
            if (rs.warriors(fa) == 0) {
                notes += "The attack never lands"
                finish(emptyList(), 0, 1)
                return
            }
        }
        if (snap > 0) {
            notes += "The first bite: 1 hit before the dice"
            hit(g, a, d, reef, 1, Source.SNAP, ctxA)
            if (g.winner != null) return
        }
        val r1 = g.nextInt(4)
        val r2 = g.nextInt(4)
        val attackerCap = rules(fa).attackCap(g, a, reef)
        val defenderWarriors = rs.warriors(fd)
        val defenderCap = rules(fd).hitWeight * defenderWarriors + rules(fd).defenseCapBonus(g, d, reef)
        var attackerHits = min(max(r1, r2), attackerCap)
        val defenderHits = min(min(r1, r2), defenderCap)
        if (defenderWarriors == 0) {
            attackerHits += 1
            notes += "${fd.display} have no warriors here: 1 extra hit"
        }
        attackerHits += gearCount(g, a, GearEffect.BATTLE_HIT)
        val riders = RemorasRules.pileOn(g, fa, reef)
        if (riders > 0) {
            attackerHits += riders
            notes += "Riding remoras pile on: $riders extra hit${if (riders == 1) "" else "s"}"
        }
        attackerHits = max(0, attackerHits - gearCount(g, d, GearEffect.DEFENSE))
        log(g, "Dice: $r1 and $r2. Hits: ${fa.display} $attackerHits, ${fd.display} $defenderHits.")
        val source = if (snap > 0) Source.SNAP else Source.BATTLE
        val removed = hit(g, a, d, reef, attackerHits, source, ctxA)
        hit(g, d, a, reef, defenderHits, Source.BATTLE, ctxD)
        if (fa == FactionId.SNAKE && removed > 0) SnakeRules.grow(g, a, removed)
        finish(listOf(r1, r2), attackerHits, defenderHits)
    }

    /**
     * Deals [hits] from [by] to [victim]'s pieces in [reef]: shells and turtle shells first, then
     * warriors, then tokens, then buildings. Every enemy building or token removed scores 1 VP for
     * [by]. Returns the warriors removed.
     */
    fun hit(g: GameState, by: Int, victim: Int, reef: Int, hits: Int, source: Source, ctx: AttackCtx, arrivals: Int = 0): Int {
        if (hits <= 0 || g.winner != null) return 0
        val f = g.players[victim].faction
        val r = rules(f)
        if (r.immune(g, victim, reef)) return 0
        var left = CrabsRules.absorb(g, f, reef, hits)
        if (f == FactionId.TURTLES && !ctx.shellUsed && g.reefs[reef].warriors(f) > 0 && left > 0) {
            ctx.shellUsed = true
            left--
            log(g, "Sea Turtles: the shell shrugs off the first hit.")
        }
        val rs = g.reefs[reef]
        val w = min(left, rs.warriors(f))
        if (w > 0) {
            r.removeWarriors(g, victim, reef, w, arrivals)
            left -= w
            log(g, "${f.display}: lose $w ${r.warriorNoun(w)} in ${Board.name(reef)}.")
        }
        var pieces = 0
        while (left > 0) {
            var idx = rs.pieces.indexOfFirst { it.owner == f && !it.type.building }
            if (idx < 0) idx = rs.pieces.indexOfFirst { it.owner == f && it.type.building }
            if (idx < 0) break
            val piece = rs.pieces[idx]
            removePiece(g, reef, piece)
            left--
            pieces++
            log(g, "${g.players[by].faction.display}: destroy ${possessive(f.display)} ${piece.type.label} in ${Board.name(reef)}.")
            scoreVp(g, by, 1, "destroying ${piece.type.label}")
        }
        if (w > 0) afterWarriorsRemoved(g, by, victim, reef, w, source)
        if (w + pieces > 0) RemorasRules.scraps(g, by, victim, ctx)
        return w
    }

    /**
     * Takes [piece] off the map in [reef] and gives it back to its owner. When another faction's
     * building goes, crabs in the reef move a market into the empty slot.
     */
    fun removePiece(g: GameState, reef: Int, piece: Piece) {
        check(g.reefs[reef].pieces.remove(piece)) { "No ${piece.type.label} in ${Board.name(reef)}" }
        val owner = piece.owner
        if (owner != null && g.inGame(owner)) rules(owner).pieceRemoved(g, g.player(owner), piece)
        if (piece.type.building) CrabsRules.vacancy(g, reef, piece)
    }

    /**
     * Pushes [n] of [victim]'s warriors from [from] to [to] for [by]. It counts as the victim's
     * own move: stings, riders and lures all treat it that way.
     */
    fun push(g: GameState, by: Int, victim: Int, from: Int, to: Int, n: Int) {
        val f = g.players[victim].faction
        log(g, "${g.players[by].faction.display}: push $n ${rules(f).warriorNoun(n)} of the ${f.display} from ${Board.name(from)} to ${Board.name(to)}.")
        rules(f).displace(g, victim, from, to, n)
    }

    /** The factions [by] may push out of [reef], each with where they can go. */
    fun pushTargets(g: GameState, by: Int, reef: Int): List<Pair<Int, List<Int>>> = g.players.indices.filter { q ->
        q != by && g.reefs[reef].warriors(g.players[q].faction) > 0 && rules(g, q).pushable(g, q, reef)
    }.map { q -> q to neighbors(g, reef, g.players[q].faction) }.filter { it.second.isNotEmpty() }

    /** Warriors lost to an attack without hits (a bait ball): still an attack for Blood and scraps. */
    private fun removeByAttack(g: GameState, by: Int, victim: Int, reef: Int, n: Int, source: Source, ctx: AttackCtx) {
        if (n <= 0) return
        val f = g.players[victim].faction
        rules(f).removeWarriors(g, victim, reef, n, 0)
        afterWarriorsRemoved(g, by, victim, reef, n, source)
        RemorasRules.scraps(g, by, victim, ctx)
    }

    private fun afterWarriorsRemoved(g: GameState, by: Int, victim: Int, reef: Int, n: Int, source: Source) {
        val fv = g.players[victim].faction
        placeBlood(g, reef)
        RemorasRules.dropOff(g, fv, reef)
        if (fv == FactionId.STARFISH) StarfishRules.regrow(g, victim, reef)
        when (source) {
            Source.STING -> scoreVp(g, by, n, "stinging")
            Source.GORGE -> scoreVp(g, by, n, "gorging")
            Source.SNAP -> AnglersRules.eat(g, by, n)
            Source.COIL -> SnakeRules.grow(g, by, n)
            else -> {}
        }
    }

    /** While sharks are in the game, any attack that removes a warrior leaves Blood, one per reef. */
    private fun placeBlood(g: GameState, reef: Int) {
        val rs = g.reefs[reef]
        if (!g.inGame(FactionId.SHARKS) || g.blood == 0 || rs.has(PieceType.BLOOD)) return
        rs.pieces.add(Piece(null, PieceType.BLOOD))
        g.blood--
        log(g, "Blood in the water at ${Board.name(reef)}.")
    }

    // ---- Cards, crafting, scoring ----------------------------------------------------------

    fun draw(g: GameState, p: Int, n: Int) {
        repeat(n) {
            if (g.drawPile.isEmpty()) {
                if (g.discard.isEmpty()) return
                g.drawPile.addAll(g.discard)
                g.discard.clear()
                shuffle(g, g.drawPile)
            }
            g.players[p].hand.add(g.drawPile.removeAt(g.drawPile.lastIndex))
        }
    }

    fun discardFromHand(g: GameState, p: Int, card: Int) {
        check(g.players[p].hand.remove(card)) { "Card $card is not in hand" }
        g.discard.add(card)
    }

    /** Cards of [suit] in [p]'s hand, counting Moon as any suit. */
    fun cardsOfSuit(g: GameState, p: Int, suit: Suit): List<Int> =
        g.players[p].hand.filter { Cards[it].suit == suit || Cards[it].suit == Suit.MOON || suit == Suit.MOON }

    fun gearCount(g: GameState, p: Int, effect: GearEffect): Int = g.players[p].gear.count { Cards[it].effect == effect }

    /** [p]'s crafting pieces that haven't paid yet this turn. */
    fun craftUnits(g: GameState, p: Int): List<CraftUnit> {
        val all = rules(g, p).craftUnits(g, p)
        val taken = mutableMapOf<String, Int>()
        return all.filter { u ->
            if (u.spend) return@filter true
            val t = taken.getOrPut(u.key) { g.turn.used("craft:${u.key}") }
            if (t > 0) {
                taken[u.key] = t - 1
                false
            } else {
                true
            }
        }
    }

    /**
     * The crafting pieces that would pay [cost], or null if it can't be paid. Each piece pays
     * its suit once per turn; a [Suit.MOON] piece pays any suit, and a [Suit.MOON] cost accepts
     * any piece. Pieces that last are used before pieces that are used up.
     */
    fun craftPayment(g: GameState, p: Int, cost: List<Suit>): List<CraftUnit>? {
        val pool = craftUnits(g, p).toMutableList()
        val used = mutableListOf<CraftUnit>()
        for (suit in cost.sortedBy { it == Suit.MOON }) {
            val i = pool.indices.filter { suit == Suit.MOON || pool[it].suit == suit || pool[it].suit == Suit.MOON }
                .minByOrNull { (if (pool[it].suit == Suit.MOON) 2 else 0) + (if (pool[it].spend) 1 else 0) } ?: return null
            used += pool.removeAt(i)
        }
        return used
    }

    fun scoreVp(g: GameState, p: Int, n: Int, why: String) {
        if (n <= 0 || g.winner != null) return
        val pl = g.players[p]
        if (pl.dominance != null) {
            log(g, "${pl.faction.display}: no VP for $why while playing Dominance.")
            return
        }
        pl.vp += n
        log(g, "${pl.faction.display}: +$n VP for $why (${pl.vp} VP).")
        if (pl.vp >= WIN_VP) win(g, p, "${pl.faction.display}: $WIN_VP VP, the first to get there.")
    }

    /** Losing VP, for the Octopus's recoil. VP never goes below zero. */
    fun loseVp(g: GameState, p: Int, n: Int, why: String) {
        val pl = g.players[p]
        val lost = min(n, pl.vp)
        if (lost <= 0) return
        pl.vp -= lost
        log(g, "${pl.faction.display}: -$lost VP for $why (${pl.vp} VP).")
    }

    /** Glory lures: a faction that rules the lure's reef at the end of its turn scores 1 VP. */
    private fun scoreGlory(g: GameState, p: Int) {
        if (g.players[p].faction == FactionId.ANGLERS) return
        for (reef in g.reefs.indices) {
            if (g.reefs[reef].pieces.any { it.type == PieceType.LURE && it.variant == AnglersRules.GLORY } && ruledBy(g, reef, p)) {
                scoreVp(g, p, 1, "the Glory lure in ${Board.name(reef)}")
            }
        }
    }

    fun dominanceMet(g: GameState, p: Int, card: Int): Boolean {
        val suit = Cards[card].suit
        return if (suit == Suit.MOON) {
            Board.oppositeGates.any { (a, b) -> ruledBy(g, a, p) && ruledBy(g, b, p) }
        } else {
            g.reefs.indices.count { g.suitOf(it) == suit && ruledBy(g, it, p) } >= 3
        }
    }

    private fun win(g: GameState, p: Int, text: String) {
        if (g.winner != null) return
        g.winner = p
        g.winText = text
        log(g, text)
    }

    fun possessive(name: String): String = if (name.endsWith("s")) "$name'" else "$name's"

    fun log(g: GameState, line: String) {
        g.log.add(line)
        if (g.log.size > 400) g.log.subList(0, g.log.size - 400).clear()
    }

    fun shuffle(g: GameState, list: MutableList<Int>) {
        for (i in list.lastIndex downTo 1) {
            val j = g.nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }
}
