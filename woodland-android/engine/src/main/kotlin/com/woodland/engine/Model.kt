package com.woodland.engine

enum class Suit(val symbol: String, val label: String) {
    FOX("🦊", "Fox"), RABBIT("🐰", "Rabbit"), MOUSE("🐭", "Mouse"), BIRD("🐦", "Bird");

    /** Bird cards are wild: they match any clearing. */
    fun matches(clearingSuit: Suit) = this == BIRD || this == clearingSuit

    companion object {
        val CLEARING_SUITS = listOf(FOX, RABBIT, MOUSE)
    }
}

enum class Faction(val label: String, val icon: String, val maxWarriors: Int) {
    CATS("Cat Dominion", "🐱", 25),
    BIRDS("Bird Dynasty", "🦅", 20),
    ALLIANCE("Forest Uprising", "🌿", 10),
    VAGABOND("Vagabond", "🦝", 0);
}

enum class ItemType(val icon: String, val label: String) {
    BOOTS("👢", "Boots"),
    BAG("🎒", "Bag"),
    CROSSBOW("🏹", "Crossbow"),
    HAMMER("🔨", "Hammer"),
    SWORD("🗡️", "Sword"),
    TEA("🍵", "Tea"),
    COINS("💰", "Coins"),
    TORCH("🔥", "Torch");

    companion object {
        /** Items available to craft at the start of the game. */
        val SUPPLY = mapOf(BOOTS to 2, BAG to 2, CROSSBOW to 1, HAMMER to 2, SWORD to 2, TEA to 2, COINS to 2)
    }
}

enum class BuildingType(val owner: Faction, val label: String, val max: Int) {
    SAWMILL(Faction.CATS, "Sawmill", 6),
    WORKSHOP(Faction.CATS, "Workshop", 6),
    RECRUITER(Faction.CATS, "Recruiter", 6),
    ROOST(Faction.BIRDS, "Roost", 7),
    BASE(Faction.ALLIANCE, "Base", 1);
}

enum class TokenType(val owner: Faction, val label: String) {
    WOOD(Faction.CATS, "wood"),
    KEEP(Faction.CATS, "keep"),
    SYMPATHY(Faction.ALLIANCE, "sympathy");
}

enum class CardKind(val label: String, val persistent: Boolean = false) {
    ITEM("Item"),
    AMBUSH("Ambush"),
    FAVOR("Favor"),
    DOMINANCE("Dominance"),
    ARMORERS("Armorers", true),
    SAPPERS("Sappers", true),
    BRUTAL_TACTICS("Brutal Tactics", true),
    SCOUTING_PARTY("Scouting Party", true),
    ROYAL_CLAIM("Royal Claim", true),
    STAND_AND_DELIVER("Stand and Deliver", true),
    TAX_COLLECTOR("Tax Collector", true),
    COMMAND_WARREN("Command Warren", true),
    COBBLER("Cobbler", true),
    BETTER_BURROW_BANK("Burrow Bank", true),
    VIZIER("Loyal Vizier");
}

class Card(
    val id: Int,
    val suit: Suit,
    val name: String,
    val kind: CardKind,
    val cost: List<Suit> = emptyList(),
    val vp: Int = 0,
    val item: ItemType? = null,
) {
    val costText: String get() = if (cost.isEmpty()) "" else cost.joinToString("") { it.symbol }

    val effectText: String
        get() = when (kind) {
            CardKind.ITEM -> "Craft: take a ${item?.label} ${item?.icon} and score $vp VP"
            CardKind.AMBUSH -> "Defending in a ${if (suit == Suit.BIRD) "any" else suit.label} clearing: deal 2 hits before the roll"
            CardKind.FAVOR -> "Craft: remove all enemy pieces in ${suit.label} clearings"
            CardKind.DOMINANCE -> if (suit == Suit.BIRD) "With 10+ VP, play it and stop scoring. Win if you rule two opposite corners at the start of your turn"
            else "With 10+ VP, play it and stop scoring. Win if you rule 3 ${suit.label} clearings at the start of your turn"
            CardKind.ARMORERS -> "In battle: discard to ignore all rolled hits you take"
            CardKind.SAPPERS -> "Defending: discard to deal an extra hit"
            CardKind.BRUTAL_TACTICS -> "Attacking: may deal an extra hit, defender scores 1 VP"
            CardKind.SCOUTING_PARTY -> "Attacking: you are not affected by ambushes"
            CardKind.ROYAL_CLAIM -> "Birdsong: discard to score 1 VP per clearing you rule"
            CardKind.STAND_AND_DELIVER -> "Birdsong: take a random card from another player; they score 1 VP"
            CardKind.TAX_COLLECTOR -> "Once each Daylight: remove one of your warriors to draw a card"
            CardKind.COMMAND_WARREN -> "Start of Daylight: you may start a battle"
            CardKind.COBBLER -> "Start of Evening: you may take a move"
            CardKind.BETTER_BURROW_BANK -> "Start of Birdsong: you and another player each draw a card"
            CardKind.VIZIER -> "Permanent bird card in the Decree"
        }

    val title: String get() = "${suit.symbol} $name"
    override fun toString() = title
}

object Deck {
    private val F = Suit.FOX
    private val R = Suit.RABBIT
    private val M = Suit.MOUSE
    private val B = Suit.BIRD

    fun build(): List<Card> {
        val cards = mutableListOf<Card>()
        fun item(suit: Suit, item: ItemType, vp: Int, vararg cost: Suit, name: String = item.label) {
            cards += Card(cards.size, suit, name, CardKind.ITEM, cost.toList(), vp, item)
        }
        fun special(suit: Suit, kind: CardKind, vararg cost: Suit) {
            val name = when (kind) {
                CardKind.FAVOR -> "Favor of the ${suit.label}s"
                CardKind.DOMINANCE -> "${suit.label} Dominance"
                else -> kind.label
            }
            cards += Card(cards.size, suit, name, kind, cost.toList())
        }
        for (s in listOf(F, R, M, B)) special(s, CardKind.DOMINANCE)
        // Fox
        special(F, CardKind.AMBUSH)
        special(F, CardKind.FAVOR, F, F, F)
        item(F, ItemType.SWORD, 2, F, F)
        item(F, ItemType.SWORD, 2, F, F, name = "Fine Blade")
        item(F, ItemType.HAMMER, 2, F, name = "Anvil")
        item(F, ItemType.BOOTS, 1, F, name = "Travel Gear")
        item(F, ItemType.TEA, 2, F, name = "Tea Leaves")
        item(F, ItemType.COINS, 3, M, M, M, name = "Gold Coins")
        special(F, CardKind.STAND_AND_DELIVER, M, M, M)
        special(F, CardKind.TAX_COLLECTOR, R, F, M)
        special(F, CardKind.ARMORERS, F)
        special(F, CardKind.BRUTAL_TACTICS, F, F)
        // Rabbit
        special(R, CardKind.AMBUSH)
        special(R, CardKind.FAVOR, R, R, R)
        item(R, ItemType.BOOTS, 1, R, R)
        item(R, ItemType.BAG, 1, M, name = "Satchel")
        item(R, ItemType.TEA, 2, R, name = "Tea Leaves")
        item(R, ItemType.COINS, 3, R, R, name = "Gold Coins")
        item(R, ItemType.BAG, 1, R, name = "Knapsack")
        special(R, CardKind.BETTER_BURROW_BANK, R, R)
        special(R, CardKind.COMMAND_WARREN, R, R)
        special(R, CardKind.COBBLER, R, R)
        special(R, CardKind.SAPPERS, M)
        special(R, CardKind.ROYAL_CLAIM, F, R, M)
        // Mouse
        special(M, CardKind.AMBUSH)
        special(M, CardKind.FAVOR, M, M, M)
        item(M, ItemType.CROSSBOW, 1, F)
        item(M, ItemType.SWORD, 2, M, M)
        item(M, ItemType.HAMMER, 2, M)
        item(M, ItemType.BAG, 1, M, name = "Satchel")
        item(M, ItemType.BOOTS, 1, M, name = "Travel Gear")
        item(M, ItemType.TEA, 2, M, name = "Tea Leaves")
        item(M, ItemType.COINS, 3, M, M, M, name = "Gold Coins")
        special(M, CardKind.SCOUTING_PARTY, M, M)
        special(M, CardKind.SAPPERS, M)
        special(M, CardKind.TAX_COLLECTOR, R, F, M)
        // Bird (wild)
        special(B, CardKind.AMBUSH)
        special(B, CardKind.AMBUSH)
        special(B, CardKind.ARMORERS, F)
        special(B, CardKind.SAPPERS, M)
        special(B, CardKind.BRUTAL_TACTICS, R, R)
        special(B, CardKind.COMMAND_WARREN, R, R)
        special(B, CardKind.ROYAL_CLAIM, F, R, M)
        item(B, ItemType.CROSSBOW, 1, F)
        item(B, ItemType.SWORD, 2, F, F)
        item(B, ItemType.BOOTS, 1, R, name = "Woodland Runners")
        item(B, ItemType.BAG, 1, M, name = "Bindle")
        item(B, ItemType.COINS, 3, R, M, F, name = "Investments")
        item(B, ItemType.HAMMER, 2, F)
        return cards
    }
}

class Quest(val id: Int, val suit: Suit, val name: String, val items: List<ItemType>) {
    val title get() = "${suit.symbol} $name (${items.joinToString("") { it.icon }})"
}

object Quests {
    fun build(): List<Quest> {
        val list = listOf(
            Triple(Suit.FOX, "Fend off a Bear", listOf(ItemType.TORCH, ItemType.CROSSBOW)),
            Triple(Suit.FOX, "Expel Bandits", listOf(ItemType.SWORD, ItemType.SWORD)),
            Triple(Suit.FOX, "Escort", listOf(ItemType.BOOTS, ItemType.BOOTS)),
            Triple(Suit.FOX, "Guard Duty", listOf(ItemType.TORCH, ItemType.SWORD)),
            Triple(Suit.FOX, "Errand", listOf(ItemType.TEA, ItemType.BOOTS)),
            Triple(Suit.RABBIT, "Give a Speech", listOf(ItemType.TORCH, ItemType.TEA)),
            Triple(Suit.RABBIT, "Fundraising", listOf(ItemType.TEA, ItemType.COINS)),
            Triple(Suit.RABBIT, "Logistics Help", listOf(ItemType.BOOTS, ItemType.BAG)),
            Triple(Suit.RABBIT, "Guard Duty", listOf(ItemType.TORCH, ItemType.SWORD)),
            Triple(Suit.RABBIT, "Errand", listOf(ItemType.TEA, ItemType.BOOTS)),
            Triple(Suit.MOUSE, "Repair a Shed", listOf(ItemType.TORCH, ItemType.HAMMER)),
            Triple(Suit.MOUSE, "Fend off a Bear", listOf(ItemType.TORCH, ItemType.CROSSBOW)),
            Triple(Suit.MOUSE, "Logistics Help", listOf(ItemType.BOOTS, ItemType.BAG)),
            Triple(Suit.MOUSE, "Expel Bandits", listOf(ItemType.SWORD, ItemType.SWORD)),
            Triple(Suit.MOUSE, "Escort", listOf(ItemType.BOOTS, ItemType.BOOTS)),
        )
        return list.mapIndexed { i, (s, n, items) -> Quest(i, s, n, items) }
    }
}

class ClearingDef(
    val id: Int,
    val suit: Suit,
    val slots: Int,
    /** Position on a 100 x 100 board. */
    val x: Float,
    val y: Float,
    val corner: Boolean = false,
    val ruin: Boolean = false,
)

/** A forest: the area enclosed by the paths between [clearings]. */
class ForestDef(val id: Int, val clearings: List<Int>) {
    val x get() = clearings.map { WoodlandMap.clearings[it].x }.average().toFloat()
    val y get() = clearings.map { WoodlandMap.clearings[it].y }.average().toFloat()
}

/** An original 12-clearing woodland: four clearings of each suit, four corners, four ruins. */
object WoodlandMap {
    val clearings = listOf(
        ClearingDef(0, Suit.FOX, 1, 11f, 10f, corner = true),
        ClearingDef(1, Suit.RABBIT, 2, 47f, 11f),
        ClearingDef(2, Suit.MOUSE, 1, 88f, 10f, corner = true),
        ClearingDef(3, Suit.MOUSE, 2, 24f, 36f),
        ClearingDef(4, Suit.FOX, 3, 60f, 33f, ruin = true),
        ClearingDef(5, Suit.RABBIT, 2, 89f, 42f, ruin = true),
        ClearingDef(6, Suit.RABBIT, 2, 10f, 63f, ruin = true),
        ClearingDef(7, Suit.MOUSE, 3, 41f, 60f, ruin = true),
        ClearingDef(8, Suit.FOX, 2, 73f, 67f),
        ClearingDef(9, Suit.MOUSE, 1, 12f, 90f, corner = true),
        ClearingDef(10, Suit.FOX, 2, 48f, 89f),
        ClearingDef(11, Suit.RABBIT, 1, 88f, 90f, corner = true),
    )

    val paths = listOf(
        0 to 1, 0 to 3, 1 to 2, 1 to 3, 1 to 4, 2 to 5, 3 to 4, 3 to 6, 3 to 7, 4 to 5,
        4 to 7, 4 to 8, 5 to 8, 6 to 7, 6 to 9, 7 to 8, 7 to 10, 8 to 10, 8 to 11, 9 to 10, 10 to 11,
    )

    val adjacent: List<List<Int>> = clearings.indices.map { c ->
        paths.mapNotNull { (a, b) -> if (a == c) b else if (b == c) a else null }.sorted()
    }

    val forests = listOf(
        listOf(0, 1, 3), listOf(1, 3, 4), listOf(1, 2, 5, 4), listOf(3, 4, 7), listOf(4, 5, 8),
        listOf(4, 7, 8), listOf(3, 6, 7), listOf(6, 7, 10, 9), listOf(7, 8, 10), listOf(8, 10, 11),
    ).mapIndexed { i, c -> ForestDef(i, c) }

    private fun edgesOf(f: ForestDef) = f.clearings.indices.map { i ->
        val a = f.clearings[i]
        val b = f.clearings[(i + 1) % f.clearings.size]
        minOf(a, b) to maxOf(a, b)
    }.toSet()

    /** Forests separated by a single path. */
    val forestAdjacent: List<List<Int>> = forests.map { f ->
        forests.filter { g -> g.id != f.id && (edgesOf(f) intersect edgesOf(g)).isNotEmpty() }.map { it.id }
    }

    val clearingForests: List<List<Int>> = clearings.indices.map { c -> forests.filter { c in it.clearings }.map { it.id } }

    val corners = clearings.filter { it.corner }.map { it.id }

    fun diagonal(corner: Int): Int = when (corner) {
        0 -> 11; 11 -> 0; 2 -> 9; 9 -> 2
        else -> error("not a corner: $corner")
    }

    fun name(c: Int): String = "${clearings[c].suit.symbol}${c + 1}"
    fun forestName(f: Int): String = "🌲${"ABCDEFGHIJ"[f]}"

    /** Path distance between clearings. */
    val distance: List<IntArray> = clearings.indices.map { from ->
        val d = IntArray(clearings.size) { 99 }
        d[from] = 0
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for (n in adjacent[c]) if (d[n] == 99) {
                d[n] = d[c] + 1; queue.addLast(n)
            }
        }
        d
    }
}
