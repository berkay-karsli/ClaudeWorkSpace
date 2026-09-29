package com.woodland.engine

private val BIRDS = Faction.BIRDS

/** VP scored each evening by the number of roosts on the map. */
val ROOST_VP = listOf(0, 0, 1, 2, 3, 4, 4, 5)

internal suspend fun Game.birdsSetup() {
    phase = "Setup"
    current = BIRDS
    var corner = -1
    if (catsCorner >= 0) {
        corner = WoodlandMap.diagonal(catsCorner)
    } else {
        choose(BIRDS, "Choose a corner for your first roost", WoodlandMap.corners.map { c ->
            Choice("Roost in ${name(c)}", c, ai = 0.0) { corner = c }
        })
    }
    board[corner].buildings += BuildingType.ROOST
    board[corner].warriors[BIRDS.ordinal] += 6
    log("🦅 The Eyrie roosts in ${name(corner)}")
    chooseLeader()
}

private suspend fun Game.chooseLeader() {
    val p = player(BIRDS)
    var options = Leader.entries.filter { it !in p.usedLeaders && it != p.leader }
    if (options.isEmpty()) {
        p.usedLeaders.clear()
        options = Leader.entries.filter { it != p.leader }
    }
    val pref = mapOf(Leader.CHARISMATIC to 3.0, Leader.COMMANDER to 2.0, Leader.BUILDER to 1.5, Leader.DESPOT to 0.0)
    choose(BIRDS, "Choose a leader", options.map { l ->
        Choice("${l.label}: ${l.text} (viziers: ${l.viziers.joinToString { it.label }})", ai = pref.getValue(l)) {
            p.leader = l
            for (col in p.decree) col.removeAll { it.kind == CardKind.VIZIER }
            for (col in l.viziers) p.decree[col.ordinal] += Card(-1, Suit.BIRD, "Loyal Vizier", CardKind.VIZIER)
            log("🦅 ${l.label} takes the throne")
        }
    })
}

private fun Game.recruitCount() = if (player(BIRDS).leader == Leader.CHARISMATIC) 2 else 1

private fun Game.buildable(c: Int) = board[c].let {
    rules(BIRDS, c) && BuildingType.ROOST !in it.buildings && it.freeSlots > 0 && canPlace(BIRDS, c)
} && count(BuildingType.ROOST) < BuildingType.ROOST.max

/** Rough measure of whether a decree card of [suit] in [col] can be carried out now. */
private fun Game.feasibility(col: DecreeColumn, suit: Suit): Double {
    val p = player(BIRDS)
    val load = p.decree[col.ordinal].count { it.suit == suit || it.suit == Suit.BIRD }
    val targets = board.filter { suit.matches(it.suit) }
    val n = when (col) {
        DecreeColumn.RECRUIT -> targets.count { BuildingType.ROOST in it.buildings }
        DecreeColumn.MOVE -> legalMoves(BIRDS).count { (a, _) -> suit.matches(board[a].suit) }.coerceAtMost(3)
        DecreeColumn.BATTLE -> targets.count { it.warriors(BIRDS) > 0 && enemiesIn(BIRDS, it.id).isNotEmpty() }
        DecreeColumn.BUILD -> targets.count { buildable(it.id) } + targets.count { cs ->
            // Places the move orders could take over this turn.
            !buildable(cs.id) && cs.freeSlots > 0 && BuildingType.ROOST !in cs.buildings && canPlace(BIRDS, cs.id) &&
                WoodlandMap.adjacent[cs.id].any { board[it].warriors(BIRDS) >= 3 }
        } / 2
    }
    val weight = when (col) {
        DecreeColumn.RECRUIT -> 6.0
        DecreeColumn.MOVE -> 4.0
        DecreeColumn.BATTLE -> 4.5
        DecreeColumn.BUILD -> 7.0
    }
    // Each build order needs a fresh clearing every turn, so stacking them is what topples leaders.
    val strain = if (col == DecreeColumn.BUILD) 5.0 else 1.5
    return if (n > load) weight - strain * load else -8.0
}

internal suspend fun Game.birdsTurn() {
    val p = player(BIRDS)
    phase = "Birdsong"
    generalBirdsong(BIRDS)
    if (p.hand.isEmpty()) {
        draw(BIRDS, 1)
        log("🦅 Emergency orders: draw a card")
    }

    var added = 0
    var birdAdded = false
    var stop = false
    while (!stop && added < 2) {
        val cards = p.hand.filter { !(birdAdded && it.suit == Suit.BIRD) }
        if (cards.isEmpty()) break
        var picked: Card? = null
        val choices = cards.map { card ->
            val best = DecreeColumn.entries.maxOf { feasibility(it, card.suit) } - if (card.suit == Suit.BIRD) 1.0 else 0.0
            Choice("Add ${card.title} to the Decree", ai = best) { picked = card }
        }.toMutableList()
        if (added > 0) choices += Choice("Done adding cards", ai = 2.5) { stop = true }
        choose(BIRDS, if (added == 0) "Add a card to the Decree (required)" else "Add a second card to the Decree?", choices, tag = "decree")
        val card = picked ?: break
        choose(BIRDS, "Which Decree column gets ${card.title}?", DecreeColumn.entries.map { col ->
            Choice("${col.label} (${card.suit.symbol})", ai = feasibility(col, card.suit)) {
                p.hand.remove(card)
                p.decree[col.ordinal] += card
            }
        }, tag = "decree")
        added++
        if (card.suit == Suit.BIRD) birdAdded = true
    }

    if (count(BuildingType.ROOST) == 0) {
        val spots = board.filter { it.freeSlots > 0 && canPlace(BIRDS, it.id) }
        if (spots.isNotEmpty()) {
            val fewest = spots.minOf { cs -> order.sumOf { cs.rulePower(it) + cs.tokens(it) } }
            val candidates = spots.filter { cs -> order.sumOf { cs.rulePower(it) + cs.tokens(it) } == fewest }
            choose(BIRDS, "A new roost: choose where the Eyrie returns", candidates.map { cs ->
                Choice("New roost in ${name(cs.id)}", cs.id, ai = 0.0) {
                    cs.buildings += BuildingType.ROOST
                    cs.warriors[BIRDS.ordinal] += minOf(3, warriorSupply(BIRDS))
                    log("🦅 A new roost rises in ${name(cs.id)}")
                }
            })
        }
    }

    phase = "Daylight"
    generalDaylight(BIRDS)
    craftPhase(BIRDS)
    if (!resolveDecree()) turmoil()

    phase = "Evening"
    generalEvening(BIRDS)
    val roosts = count(BuildingType.ROOST)
    score(BIRDS, ROOST_VP[roosts], "$roosts roost${if (roosts == 1) "" else "s"}")
    draw(BIRDS, 1 + (if (roosts >= 3) 1 else 0) + (if (roosts >= 6) 1 else 0))
    discardDown(BIRDS)
}

/** Bonus for a decree move that sets up the Build and Battle orders still to come. */
private fun Game.planBonus(later: List<Pair<DecreeColumn, Suit>>, a: Int, b: Int): Double {
    var s = 0.0
    val dst = board[b]
    val src = board[a]
    for ((col, suit) in later) {
        if (col == DecreeColumn.BUILD) {
            if (suit.matches(dst.suit) && !rules(BIRDS, b) && dst.freeSlots > 0 && BuildingType.ROOST !in dst.buildings && canPlace(BIRDS, b)) s += 4.0
            if (suit.matches(src.suit) && buildable(a)) s -= 4.0
        }
        if (col == DecreeColumn.BATTLE) {
            if (suit.matches(dst.suit) && enemiesIn(BIRDS, b).isNotEmpty()) s += 2.5
            if (suit.matches(src.suit) && enemiesIn(BIRDS, a).isNotEmpty() && src.warriors(BIRDS) <= 1) s -= 3.0
        }
    }
    return s
}

private suspend fun Game.resolveDecree(): Boolean {
    val p = player(BIRDS)
    for (col in DecreeColumn.entries) {
        val later = DecreeColumn.entries.filter { it.ordinal > col.ordinal }
            .flatMap { c -> p.decree[c.ordinal].map { c to it.suit } }
        val remaining = p.decree[col.ordinal].toMutableList()
        while (remaining.isNotEmpty()) {
            val choices = mutableListOf<Choice>()
            for (card in remaining.distinctBy { it.suit }) {
                val s = card.suit.symbol
                val matching = board.filter { card.suit.matches(it.suit) }
                when (col) {
                    DecreeColumn.RECRUIT -> if (warriorSupply(BIRDS) > 0) {
                        for (cs in matching.filter { BuildingType.ROOST in it.buildings }) {
                            choices += Choice("$s Recruit in ${name(cs.id)}", cs.id, ai = 1.0 + enemiesIn(BIRDS, cs.id).size) {
                                remaining.remove(card)
                                val n = minOf(recruitCount(), warriorSupply(BIRDS))
                                cs.warriors[BIRDS.ordinal] += n
                                log("🦅 recruits $n in ${name(cs.id)}")
                            }
                        }
                    }
                    DecreeColumn.MOVE -> for ((a, b) in legalMoves(BIRDS).filter { card.suit.matches(board[it.first].suit) }) {
                        choices += Choice("$s Move ${name(a)} → ${name(b)}", b, a, ai = aiMoveScore(BIRDS, a, b) + planBonus(later, a, b)) {
                            remaining.remove(card)
                            val max = board[a].warriors(BIRDS)
                            val keep = if (BuildingType.ROOST in board[a].buildings && max > 1) 1 else 0
                            val i = ask(BIRDS, "How many warriors move from ${name(a)} to ${name(b)}?",
                                (1..max).map { Option("$it", b, a, ai = -kotlin.math.abs(it - (max - keep)).toDouble()) })
                            moveWarriors(BIRDS, a, b, i + 1)
                        }
                    }
                    DecreeColumn.BATTLE -> for (cs in matching.filter { it.warriors(BIRDS) > 0 }) {
                        for (enemy in enemiesIn(BIRDS, cs.id)) {
                            choices += Choice("$s Battle ${enemy.icon} in ${name(cs.id)}", cs.id, ai = aiBattleScore(BIRDS, enemy, cs.id)) {
                                remaining.remove(card)
                                battle(BIRDS, enemy, cs.id)
                            }
                        }
                    }
                    DecreeColumn.BUILD -> for (cs in matching.filter { buildable(it.id) }) {
                        choices += Choice("$s Build a roost in ${name(cs.id)}", cs.id, ai = cs.freeSlots.toDouble()) {
                            remaining.remove(card)
                            cs.buildings += BuildingType.ROOST
                            log("🦅 builds a roost in ${name(cs.id)}")
                        }
                    }
                }
            }
            if (choices.isEmpty()) {
                log("🦅 The Decree cannot be carried out (${col.label} ${remaining.joinToString("") { it.suit.symbol }})")
                return false
            }
            choose(BIRDS, "Decree — ${col.label}: ${remaining.joinToString(" ") { it.suit.symbol }} left", choices)
        }
    }
    return true
}

private suspend fun Game.turmoil() {
    val p = player(BIRDS)
    log("🦅 TURMOIL! The ${p.leader?.label} is deposed")
    val birdCards = p.decree.sumOf { col -> col.count { it.suit == Suit.BIRD } }
    for (col in p.decree) {
        val purge = col.filter { it.kind != CardKind.VIZIER }
        col.removeAll(purge)
        purge.forEach { discard(it) }
    }
    p.leader?.let { p.usedLeaders += it }
    chooseLeader()
    score(BIRDS, -birdCards, "humiliation in turmoil")
}
