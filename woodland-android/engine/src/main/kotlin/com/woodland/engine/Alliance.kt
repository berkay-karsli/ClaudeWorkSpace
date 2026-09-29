package com.woodland.engine

private val ALLIANCE = Faction.ALLIANCE

/** Supporters needed to spread sympathy, by how many tokens are already on the map. */
val SYMPATHY_COST = listOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 3)
val SYMPATHY_VP = listOf(0, 1, 1, 1, 2, 2, 3, 4, 4, 4)

internal suspend fun Game.allianceSetup() {
    val p = player(ALLIANCE)
    repeat(3) { drawCard()?.let { p.supporters += it } }
    log("🌿 The Uprising gathers 3 secret supporters")
}

private fun Game.matchingSupporters(suit: Suit) = player(ALLIANCE).supporters.count { it.suit.matches(suit) }

/** Spends supporters, using exact suit matches before wild birds. */
private fun Game.paySupporters(suit: Suit, n: Int) {
    val p = player(ALLIANCE)
    val pay = p.supporters.filter { it.suit == suit }.take(n).toMutableList()
    if (pay.size < n) pay += p.supporters.filter { it.suit == Suit.BIRD }.take(n - pay.size)
    check(pay.size == n)
    for (c in pay) {
        p.supporters.remove(c)
        discard(c)
    }
}

private fun Game.martialLaw(c: Int) = order.any { it != ALLIANCE && board[c].warriors(it) >= 3 }

private fun Game.sympathyCost(c: Int) =
    SYMPATHY_COST[sympathyOnMap()] + if (martialLaw(c)) 1 else 0

private fun Game.placeSympathy(c: Int, how: String) {
    val vp = SYMPATHY_VP[sympathyOnMap()]
    board[c].sympathy = true
    log("🌿 $how: sympathy in ${name(c)}")
    score(ALLIANCE, vp, "sympathy")
}

private fun Game.spreadTargets(): List<Int> {
    if (sympathyOnMap() >= Game.MAX_SYMPATHY) return emptyList()
    val open = board.filter { !it.sympathy && canPlace(ALLIANCE, it.id) }.map { it.id }
    if (sympathyOnMap() == 0) return open
    return open.filter { c -> WoodlandMap.adjacent[c].any { board[it].sympathy } }
}

internal suspend fun Game.allianceTurn() {
    val p = player(ALLIANCE)
    phase = "Birdsong"
    royalClaim(ALLIANCE)
    var done = false
    while (!done) {
        val choices = mutableListOf<Choice>()
        val bases = baseSuits()
        for (cs in board.filter { it.sympathy && it.suit !in bases && matchingSupporters(it.suit) >= 2 }) {
            val loot = order.filter { it != ALLIANCE }.sumOf { cs.buildings(it) + cs.tokens(it) }
            choices += Choice("Revolt in ${name(cs.id)} (2 ${cs.suit.symbol} supporters)", cs.id, ai = 12.0 + 3 * loot) {
                paySupporters(cs.suit, 2)
                log("🌿 REVOLT in ${name(cs.id)}!")
                wipeEnemies(ALLIANCE, cs.id)
                cs.buildings += BuildingType.BASE
                val n = minOf(board.count { it.sympathy && it.suit == cs.suit }, warriorSupply(ALLIANCE))
                cs.warriors[ALLIANCE.ordinal] += n
                if (warriorSupply(ALLIANCE) > 0) p.officers++
                log("🌿 A base is founded with $n warriors")
            }
        }
        for (c in spreadTargets()) {
            val cost = sympathyCost(c)
            if (matchingSupporters(board[c].suit) >= cost) {
                choices += Choice("Spread sympathy to ${name(c)} ($cost ${board[c].suit.symbol} supporter${if (cost > 1) "s" else ""})",
                    c, ai = 9.0 - 2.0 * cost - (if (martialLaw(c)) 2.0 else 0.0)) {
                    paySupporters(board[c].suit, cost)
                    placeSympathy(c, "Spread")
                }
            }
        }
        if (choices.isEmpty()) break
        choices += Choice("Done with Birdsong", ai = 0.0) { done = true }
        choose(ALLIANCE, "Birdsong: revolt or spread sympathy (${p.supporters.size} supporters)", choices)
    }

    phase = "Daylight"
    craftPhase(ALLIANCE)
    done = false
    while (!done) {
        val choices = mutableListOf<Choice>()
        val room = baseSuits().isNotEmpty() || p.supporters.size < 5
        if (room) for (card in p.hand) {
            choices += Choice("Mobilize ${card.title} as a supporter", ai = 3.5 - cardValue(card)) {
                p.hand.remove(card)
                addSupporter(card)
            }
        }
        val bases = baseSuits()
        if (warriorSupply(ALLIANCE) > 0) for (card in p.hand.filter { c -> bases.any { c.suit.matches(it) } }) {
            choices += Choice("Train an officer with ${card.title}", ai = (if (p.officers < 4) 7.0 else 2.0) - cardValue(card)) {
                p.hand.remove(card)
                discard(card)
                p.officers++
                log("🌿 trains an officer (${p.officers})")
            }
        }
        if (choices.isEmpty()) break
        choices += Choice("Done with Daylight", ai = 0.0) { done = true }
        choose(ALLIANCE, "Daylight: mobilize or train", choices)
    }

    phase = "Evening"
    var ops = p.officers
    done = false
    while (!done && ops > 0) {
        val choices = mutableListOf<Choice>()
        val moves = legalMoves(ALLIANCE)
        if (moves.isNotEmpty()) {
            choices += Choice("Move", ai = 1.0 + moves.maxOf { (a, b) -> aiMoveScore(ALLIANCE, a, b) }) {
                ops--
                doMove(ALLIANCE, "Operation: move", allowSkip = false)
            }
        }
        val targets = battleTargets(ALLIANCE)
        if (targets.isNotEmpty()) {
            choices += Choice("Battle", ai = 6.0 + targets.maxOf { (c, e) -> aiBattleScore(ALLIANCE, e, c) }) {
                ops--
                pickBattle(ALLIANCE, targets)
            }
        }
        val baseClearings = board.filter { BuildingType.BASE in it.buildings }
        if (baseClearings.isNotEmpty() && warriorSupply(ALLIANCE) > 0) {
            choices += Choice("Recruit at a base", ai = 4.0) {
                ops--
                choose(ALLIANCE, "Recruit at which base?", baseClearings.map { cs ->
                    Choice("Recruit in ${name(cs.id)}", cs.id, ai = enemiesIn(ALLIANCE, cs.id).size.toDouble()) {
                        cs.warriors[ALLIANCE.ordinal]++
                        log("🌿 recruits in ${name(cs.id)}")
                    }
                })
            }
        }
        val organize = board.filter { it.warriors(ALLIANCE) > 0 && !it.sympathy && canPlace(ALLIANCE, it.id) }
        if (organize.isNotEmpty() && sympathyOnMap() < Game.MAX_SYMPATHY) {
            choices += Choice("Organize (trade a warrior for sympathy)", ai = 7.0 + SYMPATHY_VP[sympathyOnMap()]) {
                ops--
                choose(ALLIANCE, "Organize where?", organize.map { cs ->
                    Choice("Organize in ${name(cs.id)}", cs.id, ai = -cs.warriors(ALLIANCE).toDouble()) {
                        cs.warriors[ALLIANCE.ordinal]--
                        placeSympathy(cs.id, "Organize")
                    }
                })
            }
        }
        if (choices.isEmpty()) break
        choices += Choice("End operations", ai = 0.0) { done = true }
        choose(ALLIANCE, "Evening: $ops operation${if (ops == 1) "" else "s"} left", choices)
    }
    draw(ALLIANCE, 1 + baseSuits().size)
    discardDown(ALLIANCE)
}
