package com.woodland.engine

private val CATS = Faction.CATS
private val CAT_BUILDINGS = listOf(BuildingType.SAWMILL, BuildingType.WORKSHOP, BuildingType.RECRUITER)

/** Wood cost of the next building, by how many of that type are on the map. */
val CAT_BUILD_COST = listOf(0, 1, 2, 3, 3, 4)

val CAT_BUILD_VP = mapOf(
    BuildingType.SAWMILL to listOf(0, 1, 2, 3, 4, 5),
    BuildingType.WORKSHOP to listOf(0, 2, 2, 3, 4, 5),
    BuildingType.RECRUITER to listOf(0, 1, 2, 3, 3, 4),
)

internal suspend fun Game.catsSetup() {
    phase = "Setup"
    current = CATS
    var corner = -1
    choose(CATS, "Choose a corner for your keep", WoodlandMap.corners.map { c ->
        Choice("Keep in ${name(c)}", c, ai = 0.0) { corner = c }
    })
    catsCorner = corner
    board[corner].keep = true
    val far = WoodlandMap.diagonal(corner)
    for (cs in board) if (cs.id != far) cs.warriors[CATS.ordinal]++
    log("🐱 The keep is raised in ${name(corner)}; a garrison fills the woods")
    val near = listOf(corner) + WoodlandMap.adjacent[corner]
    for (type in CAT_BUILDINGS) {
        val spots = near.filter { board[it].freeSlots > 0 }
        choose(CATS, "Place your starting ${type.label}", spots.map { c ->
            Choice("${type.label} in ${name(c)}", c, ai = board[c].freeSlots.toDouble()) {
                board[c].buildings += type
            }
        })
    }
}

/** Clearings reachable from [c] through clearings the cats rule, nearest first. */
private fun Game.ruledNetwork(c: Int): List<Int> {
    val seen = mutableListOf(c)
    var i = 0
    while (i < seen.size) {
        for (n in WoodlandMap.adjacent[seen[i]]) if (n !in seen && rules(CATS, n)) seen += n
        i++
    }
    return seen
}

private fun Game.woodReachable(c: Int) = ruledNetwork(c).sumOf { board[it].wood }

private fun Game.payWood(c: Int, cost: Int) {
    var left = cost
    for (n in ruledNetwork(c)) {
        while (left > 0 && board[n].wood > 0) {
            board[n].wood--
            left--
        }
    }
}

private fun Game.buildOptions(): List<Choice> = CAT_BUILDINGS.flatMap { type ->
    val placed = count(type)
    if (placed >= type.max) return@flatMap emptyList()
    val cost = CAT_BUILD_COST[placed]
    val vp = CAT_BUILD_VP.getValue(type)[placed]
    board.filter { it.freeSlots > 0 && rules(CATS, it.id) && woodReachable(it.id) >= cost }.map { cs ->
        val bonus = when (type) {
            BuildingType.SAWMILL -> 3.0
            BuildingType.RECRUITER -> 2.0
            else -> 1.0
        }
        Choice("${type.label} in ${name(cs.id)} (−$cost wood, +$vp VP)", cs.id, ai = 20.0 + 4 * vp + bonus) {
            payWood(cs.id, cost)
            cs.buildings += type
            log("🐱 builds a ${type.label} in ${name(cs.id)}")
            score(CATS, vp, type.label)
        }
    }
}

internal suspend fun Game.catsTurn() {
    val p = player(CATS)
    phase = "Birdsong"
    generalBirdsong(CATS)
    var placed = 0
    for (cs in board) repeat(cs.buildings.count { it == BuildingType.SAWMILL }) {
        if (woodOnMap() < Game.MAX_WOOD) {
            cs.wood++; placed++
        }
    }
    if (placed > 0) log("🐱 Sawmills produce $placed wood")

    phase = "Daylight"
    generalDaylight(CATS)
    craftPhase(CATS)
    var actions = 3
    var recruited = false
    var done = false
    while (!done) {
        val choices = mutableListOf<Choice>()
        if (actions > 0) {
            val moves = legalMoves(CATS)
            if (moves.isNotEmpty()) {
                val best = moves.maxOf { (a, b) -> aiMoveScore(CATS, a, b) }
                choices += Choice("March (up to two moves)", ai = 4.0 + best) {
                    actions--
                    doMove(CATS, "March: first move", allowSkip = false)
                    doMove(CATS, "March: second move", allowSkip = true)
                }
            }
            val targets = battleTargets(CATS)
            if (targets.isNotEmpty()) {
                val best = targets.maxOf { (c, e) -> aiBattleScore(CATS, e, c) }
                choices += Choice("Battle", ai = 8.0 + best) {
                    actions--
                    pickBattle(CATS, targets)
                }
            }
            val recruiters = count(BuildingType.RECRUITER)
            if (!recruited && recruiters > 0 && warriorSupply(CATS) > 0) {
                choices += Choice("Recruit (+1 warrior at each recruiter)", ai = 6.0 + recruiters) {
                    actions--
                    recruited = true
                    var n = 0
                    for (cs in board) repeat(cs.buildings.count { it == BuildingType.RECRUITER }) {
                        if (warriorSupply(CATS) > 0) {
                            cs.warriors[CATS.ordinal]++; n++
                        }
                    }
                    log("🐱 recruits $n warriors")
                }
            }
            val builds = buildOptions()
            if (builds.isNotEmpty()) {
                choices += Choice("Build", ai = builds.maxOf { it.ai }) {
                    actions--
                    choose(CATS, "Build what, where?", buildOptions())
                }
            }
            val overwork = board.filter { cs ->
                BuildingType.SAWMILL in cs.buildings && p.hand.any { it.suit.matches(cs.suit) }
            }
            if (overwork.isNotEmpty() && woodOnMap() < Game.MAX_WOOD) {
                choices += Choice("Overwork (discard a card for wood at a sawmill)", ai = if (p.hand.size >= 4) 3.0 else 0.2) {
                    actions--
                    choose(CATS, "Overwork which sawmill?", overwork.map { cs ->
                        Choice("Overwork in ${name(cs.id)}", cs.id, ai = 0.0) {
                            val cards = p.hand.filter { it.suit.matches(cs.suit) }
                            choose(CATS, "Discard which card?", cards.map { card ->
                                Choice("Discard ${card.title}", cs.id, ai = -cardValue(card)) {
                                    p.hand.remove(card); discard(card); cs.wood++
                                    log("🐱 overworks ${name(cs.id)}")
                                }
                            })
                        }
                    })
                }
            }
        }
        val birds = p.hand.filter { it.suit == Suit.BIRD }
        if (birds.isNotEmpty()) {
            choices += Choice("Hire hawks: discard a 🐦 card for an extra action",
                ai = if (actions == 0 && p.hand.size >= 4) 1.0 else -5.0) {
                choose(CATS, "Discard which bird card?", birds.map { card ->
                    Choice("Discard ${card.title}", ai = -cardValue(card)) {
                        p.hand.remove(card); discard(card); actions++
                    }
                })
            }
        }
        choices += Choice(if (actions > 0) "End daylight ($actions action${if (actions > 1) "s" else ""} unused)" else "End daylight", ai = 0.0) {
            done = true
        }
        choose(CATS, "Daylight: $actions action${if (actions == 1) "" else "s"} left", choices)
    }

    phase = "Evening"
    generalEvening(CATS)
    val recruiters = count(BuildingType.RECRUITER)
    draw(CATS, 1 + (if (recruiters >= 3) 1 else 0) + (if (recruiters >= 5) 1 else 0))
    discardDown(CATS)
}
