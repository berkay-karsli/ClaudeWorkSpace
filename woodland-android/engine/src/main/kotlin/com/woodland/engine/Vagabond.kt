package com.woodland.engine

private val VB = Faction.VAGABOND

private fun itemValue(t: ItemType) = when (t) {
    ItemType.SWORD -> 5.0
    ItemType.BOOTS -> 4.5
    ItemType.TORCH -> 4.0
    ItemType.CROSSBOW -> 3.0
    ItemType.HAMMER -> 3.0
    ItemType.TEA -> 2.5
    ItemType.COINS -> 2.5
    ItemType.BAG -> 1.5
}

/** Items that sit on the tea/coins/bag tracks don't take satchel space while undamaged. */
private fun onTrack(i: Item) = !i.damaged && i.type in listOf(ItemType.TEA, ItemType.COINS, ItemType.BAG)

internal suspend fun Game.vagabondSetup() {
    val p = player(VB)
    phase = "Setup"
    current = VB
    val pref = mapOf(VbCharacter.THIEF to 2.0, VbCharacter.RANGER to 2.5, VbCharacter.TINKER to 1.5)
    choose(VB, "Choose your Vagabond", VbCharacter.entries.map { ch ->
        Choice("${ch.label} (${ch.start.joinToString("") { it.icon }}): ${ch.special}", ai = pref.getValue(ch)) {
            p.character = ch
            ch.start.forEach { p.items += Item(it) }
        }
    })
    for (f in order) if (f != VB) p.relations[f] = Relation.INDIFFERENT
    questDeck.addAll(Quests.build().shuffled(rng))
    repeat(3) { quests += questDeck.removeAt(questDeck.lastIndex) }
    choose(VB, "Choose a forest to start in", WoodlandMap.forests.map { f ->
        Choice("Start in forest ${WoodlandMap.forestName(f.id)}", Game.FOREST_BASE + f.id,
            ai = f.clearings.count { board[it].hasRuin }.toDouble()) {
            vbForest = f.id
            vbClearing = -1
        }
    })
    log("🦝 The ${p.character!!.label} slips into the woods")
}

/** The Vagabond chooses which undamaged items absorb hits. */
internal suspend fun Game.damageItems(n: Int) {
    val p = player(VB)
    repeat(n) {
        val candidates = p.items.filter { !it.damaged }
        if (candidates.isEmpty()) return
        val i = ask(VB, "Damage an item", candidates.map {
            Option("Damage $it", vbClearing, ai = -itemValue(it.type) + if (it.exhausted) 1.0 else 0.0)
        })
        candidates[i].damaged = true
        log("🦝 ${candidates[i].type.label} damaged")
    }
}

private fun Game.hostileWarriors(c: Int) =
    order.any { it != VB && player(VB).relations[it] == Relation.HOSTILE && board[c].warriors(it) > 0 }

private fun Game.exhaust(t: ItemType, n: Int = 1) {
    val p = player(VB)
    repeat(n) { p.items.first { it.type == t && it.ready }.exhausted = true }
}

/** How attractive a clearing is to stand in, for the computer Vagabond. */
private fun Game.goal(c: Int): Double {
    val p = player(VB)
    val cs = board[c]
    var s = 0.0
    if (cs.hasRuin && p.undamaged(ItemType.TORCH) > 0) s += 6.0
    for (q in quests.filter { it.suit == cs.suit }) {
        if (q.items.groupBy { it }.all { (t, l) -> p.undamaged(t) >= l.size }) s += 4.0
    }
    for (f in order.filter { it != VB && hasPiece(it, c) }) {
        if (p.relations[f] != Relation.HOSTILE && p.hand.any { it.suit.matches(cs.suit) }) s += 2.0
        if (p.relations[f] == Relation.HOSTILE && cs.warriors(f) == 0 && cs.buildings(f) + cs.tokens(f) > 0) s += 2.0
        if (p.relations[f] == Relation.HOSTILE && cs.warriors(f) >= 3) s -= 2.0
    }
    return s
}

private suspend fun Game.enterClearing(c: Int) {
    vbClearing = c
    vbForest = -1
    if (board[c].sympathy) outrage(VB, c)
}

internal suspend fun Game.vagabondTurn() {
    val p = player(VB)
    p.aidsThisTurn.clear()
    phase = "Birdsong"
    var refresh = 3 + 2 * p.undamaged(ItemType.TEA)
    for (item in p.items.filter { it.exhausted }.sortedBy { if (it.damaged) 1 else 0 }) {
        if (refresh-- <= 0) break
        item.exhausted = false
    }
    generalBirdsong(VB)

    // Slip
    val here = vbClearing
    val slip = mutableListOf<Choice>()
    val stayScore = if (here >= 0) goal(here) + 0.5 else -1.0
    slip += Choice("Stay where you are", ai = stayScore) {}
    val clearingsNear = if (here >= 0) WoodlandMap.adjacent[here] else WoodlandMap.forests[vbForest].clearings
    for (c in clearingsNear) slip += Choice("Slip to ${name(c)}", c, ai = goal(c)) { enterClearing(c) }
    val forestsNear = if (here >= 0) WoodlandMap.clearingForests[here] else WoodlandMap.forestAdjacent[vbForest]
    val damaged = p.items.count { it.damaged }
    for (f in forestsNear) slip += Choice("Slip into forest ${WoodlandMap.forestName(f)}", Game.FOREST_BASE + f,
        ai = if (damaged >= 3) 5.0 else -2.0) {
        vbForest = f; vbClearing = -1
    }
    choose(VB, "Slip: move for free to an adjacent clearing or forest", slip)
    if (vbClearing != here) log("🦝 slips to " + if (vbClearing >= 0) name(vbClearing) else "forest ${WoodlandMap.forestName(vbForest)}")

    phase = "Daylight"
    generalDaylight(VB)
    var done = false
    while (!done) {
        val choices = mutableListOf<Choice>()
        val c = vbClearing
        if (c >= 0) addClearingActions(c, choices)
        if (p.character == VbCharacter.RANGER && p.ready(ItemType.TORCH) > 0 && p.items.any { it.damaged }) {
            choices += Choice("Hideout: exhaust a torch, repair 3 items, end Daylight", ai = if (p.items.count { it.damaged } >= 3) 6.0 else 1.0) {
                exhaust(ItemType.TORCH)
                p.items.filter { it.damaged }.sortedByDescending { itemValue(it.type) }.take(3).forEach { it.damaged = false }
                log("🦝 hides out and repairs")
                done = true
            }
        }
        choices += Choice("End Daylight", ai = 0.0) { done = true }
        choose(VB, "Daylight: " + p.items.filter { it.ready }.joinToString("") { it.type.icon }.ifEmpty { "no ready items" }, choices)
    }

    phase = "Evening"
    generalEvening(VB)
    if (vbForest >= 0 && p.items.any { it.damaged }) {
        p.items.forEach { it.damaged = false }
        log("🦝 rests in the forest and repairs everything")
    }
    draw(VB, 1 + p.undamaged(ItemType.COINS))
    discardDown(VB)
    while (true) {
        val satchel = p.items.filter { !onTrack(it) }
        val capacity = 6 + 2 * p.undamaged(ItemType.BAG)
        if (satchel.size <= capacity) break
        val i = ask(VB, "Too many items: remove one", satchel.map { Option("Remove $it", ai = -itemValue(it.type)) })
        p.items.remove(satchel[i])
    }
}

private fun Game.addClearingActions(c: Int, choices: MutableList<Choice>) {
    val p = player(VB)
    val cs = board[c]

    // Move
    for (to in WoodlandMap.adjacent[c]) {
        val cost = 1 + if (hostileWarriors(to)) 1 else 0
        if (p.ready(ItemType.BOOTS) >= cost) {
            choices += Choice("Move to ${name(to)} (${"👢".repeat(cost)})", to, c, ai = 1.0 + goal(to) - goal(c) - cost) {
                exhaust(ItemType.BOOTS, cost)
                log("🦝 moves to ${name(to)}")
                enterClearing(to)
            }
        }
    }
    // Battle
    if (p.ready(ItemType.SWORD) > 0) {
        for (e in enemiesIn(VB, c)) {
            choices += Choice("Battle ${e.icon} ${e.label} (🗡️)", c, ai = aiBattleScore(VB, e, c) + 1.0) {
                exhaust(ItemType.SWORD)
                battle(VB, e, c)
            }
        }
    }
    // Explore
    if (cs.hasRuin && p.ready(ItemType.TORCH) > 0) {
        choices += Choice("Explore the ruin (🔥): take an item, +1 VP", c, ai = 12.0) {
            exhaust(ItemType.TORCH)
            val item = cs.ruin.removeAt(rng.nextInt(cs.ruin.size))
            p.items += Item(item)
            log("🦝 explores the ruin in ${name(c)} and finds ${item.icon} ${item.label}")
            score(VB, 1, "explored a ruin")
        }
    }
    // Quest
    for (q in quests.filter { it.suit == cs.suit }) {
        val need = q.items.groupBy { it }
        if (need.all { (t, l) -> p.ready(t) >= l.size }) {
            choices += Choice("Quest: ${q.title}", c, ai = 9.0 + p.questsDone[q.suit.ordinal]) {
                q.items.forEach { exhaust(it) }
                quests.remove(q)
                if (questDeck.isNotEmpty()) quests += questDeck.removeAt(questDeck.lastIndex)
                p.questsDone[q.suit.ordinal]++
                val vp = p.questsDone[q.suit.ordinal]
                log("🦝 completes the quest ${q.title}")
                choose(VB, "Quest reward", listOf(
                    Choice("Score $vp VP", ai = vp.toDouble() * 1.5) { score(VB, vp, "quest") },
                    Choice("Draw 2 cards", ai = 2.5) { draw(VB, 2) },
                ))
            }
        }
    }
    // Aid
    val readyItems = p.items.filter { it.ready }
    val cards = p.hand.filter { it.suit.matches(cs.suit) }
    if (readyItems.isNotEmpty() && cards.isNotEmpty()) {
        for (f in order.filter { it != VB && hasPiece(it, c) }) {
            val rel = p.relations[f] ?: Relation.INDIFFERENT
            val gain = when (rel) {
                Relation.HOSTILE -> 0.0
                Relation.ALLIED -> 2.0
                else -> if ((p.aidsThisTurn[f] ?: 0) + 1 >= rel.aids) rel.vp.toDouble() else 0.7
            }
            choices += Choice("Aid ${f.icon} ${f.label} (${rel.label}): give a card", c,
                ai = 2.0 + 2.5 * gain + (if (player(f).craftedItems.isNotEmpty()) 2.0 else 0.0) - (if (rel == Relation.HOSTILE) 3.0 else 0.0) - player(f).vp / 12.0) {
                aid(f, c)
            }
        }
    }
    // Strike
    if (p.ready(ItemType.CROSSBOW) > 0) {
        for (e in enemiesIn(VB, c)) {
            val hostile = p.relations[e] == Relation.HOSTILE
            val w = cs.warriors(e)
            choices += Choice("Strike ${e.icon} with the crossbow (🏹)", c,
                ai = when {
                    w == 0 -> 6.0
                    hostile -> 4.0
                    else -> -2.0
                }) {
                exhaust(ItemType.CROSSBOW)
                strike(e, c)
            }
        }
    }
    // Repair
    val broken = p.items.filter { it.damaged }
    if (p.ready(ItemType.HAMMER) > 0 && broken.isNotEmpty()) {
        choices += Choice("Repair an item (🔨)", ai = 3.0 + broken.maxOf { itemValue(it.type) } / 2) {
            exhaust(ItemType.HAMMER)
            val fixable = p.items.filter { it.damaged }
            val i = ask(VB, "Repair which item?", fixable.map { Option("Repair $it", ai = itemValue(it.type)) })
            fixable[i].damaged = false
        }
    }
    // Craft with hammers
    val avail = craftingPieces(VB)
    for (craft in craftChoices(VB, avail) { card -> exhaust(ItemType.HAMMER, card.cost.size) }) choices += craft
    // Special action
    if (p.ready(ItemType.TORCH) > 0) when (p.character) {
        VbCharacter.THIEF -> for (f in order.filter { it != VB && hasPiece(it, c) && player(it).hand.isNotEmpty() }) {
            choices += Choice("Steal a random card from ${f.icon} (🔥)", c, ai = 3.0) {
                exhaust(ItemType.TORCH)
                val card = player(f).hand.random(rng)
                player(f).hand.remove(card)
                p.hand += card
                log("🦝 steals a card from ${f.icon}")
            }
        }
        VbCharacter.TINKER -> {
            val found = discardPile.filter { it.suit.matches(cs.suit) }.distinctBy { it.name + it.suit }
            if (found.isNotEmpty()) choices += Choice("Day Labor: take a card from the discard pile (🔥)", ai = 2.0 + found.maxOf { cardValue(it) } / 2) {
                exhaust(ItemType.TORCH)
                val i = ask(VB, "Take which card?", found.map { Option("Take ${it.title}", ai = cardValue(it)) })
                discardPile.remove(found[i])
                p.hand += found[i]
            }
        }
        else -> {}
    }
}

private suspend fun Game.aid(f: Faction, c: Int) {
    val p = player(VB)
    val cards = p.hand.filter { it.suit.matches(board[c].suit) }
    val i = ask(VB, "Give which card to ${f.label}?", cards.map { Option("Give ${it.title}", c, ai = -cardValue(it)) })
    val card = cards[i]
    val items = p.items.filter { it.ready }
    val j = ask(VB, "Exhaust which item?", items.map { Option("Exhaust $it", ai = -itemValue(it.type)) })
    items[j].exhausted = true
    p.hand.remove(card)
    player(f).hand += card
    log("🦝 aids ${f.icon} ${f.label}")
    val offered = player(f).craftedItems.distinct()
    if (offered.isNotEmpty()) {
        choose(VB, "Take one of their crafted items?", offered.map { t ->
            Choice("Take ${t.icon} ${t.label}", ai = itemValue(t)) {
                player(f).craftedItems.remove(t)
                p.items += Item(t).also { it.exhausted = true }
                log("🦝 takes ${t.icon} ${t.label} from ${f.icon}")
            }
        } + Choice("Take nothing", ai = 0.0) {})
    }
    val rel = p.relations[f] ?: Relation.INDIFFERENT
    when (rel) {
        Relation.HOSTILE -> {}
        Relation.ALLIED -> score(VB, 2, "aided an ally")
        else -> {
            val n = (p.aidsThisTurn[f] ?: 0) + 1
            p.aidsThisTurn[f] = n
            if (n >= rel.aids) {
                val next = Relation.entries[rel.ordinal + 1]
                p.relations[f] = next
                p.aidsThisTurn[f] = 0
                log("🦝 is now ${next.label} with ${f.icon}")
                score(VB, rel.vp, "${next.label} with ${f.label}")
            }
        }
    }
}

/** Crossbow: remove one warrior, or a building/token if the faction has no warriors there. */
private suspend fun Game.strike(e: Faction, c: Int) {
    val cs = board[c]
    log("🦝 strikes at ${e.icon} in ${name(c)}")
    if (cs.warriors(e) > 0) {
        removeWarriors(c, e, 1, VB)
        return
    }
    val choices = mutableListOf<Choice>()
    for (type in cs.buildings.filter { it.owner == e }.distinct()) {
        choices += Choice("Destroy a ${type.label}", c, ai = 2.0) { removeBuilding(c, type, VB, battle = false) }
    }
    if (e == Faction.CATS && cs.wood > 0) choices += Choice("Destroy a wood", c, ai = 0.5) { removeToken(c, TokenType.WOOD, VB, battle = false) }
    if (e == Faction.CATS && cs.keep) choices += Choice("Destroy the keep", c, ai = 3.0) { removeToken(c, TokenType.KEEP, VB, battle = false) }
    if (e == Faction.ALLIANCE && cs.sympathy) choices += Choice("Remove sympathy", c, ai = 1.5) { removeToken(c, TokenType.SYMPATHY, VB, battle = false) }
    if (choices.isNotEmpty()) choose(VB, "Strike: remove which piece?", choices)
}
