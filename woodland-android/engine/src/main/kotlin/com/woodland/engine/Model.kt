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
    ALLIANCE("Forest Uprising", "🌿", 10);
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
    ARMORERS("Armorers", true),
    SAPPERS("Sappers", true),
    BRUTAL_TACTICS("Brutal Tactics", true),
    SCOUTING_PARTY("Scouting Party", true),
    ROYAL_CLAIM("Royal Claim", true),
    VIZIER("Loyal Vizier");
}

class Card(
    val id: Int,
    val suit: Suit,
    val name: String,
    val kind: CardKind,
    val cost: List<Suit> = emptyList(),
    val vp: Int = 0,
) {
    val costText: String get() = if (cost.isEmpty()) "" else cost.joinToString("") { it.symbol }

    val effectText: String
        get() = when (kind) {
            CardKind.ITEM -> "Craft for $vp VP"
            CardKind.AMBUSH -> "Defending in a ${if (suit == Suit.BIRD) "any" else suit.label} clearing: deal 2 hits before the roll"
            CardKind.FAVOR -> "Craft: remove all enemy pieces in ${suit.label} clearings"
            CardKind.ARMORERS -> "In battle: discard to ignore all rolled hits you take"
            CardKind.SAPPERS -> "Defending: discard to deal an extra hit"
            CardKind.BRUTAL_TACTICS -> "Attacking: may deal an extra hit, defender scores 1 VP"
            CardKind.SCOUTING_PARTY -> "Attacking: you are not affected by ambushes"
            CardKind.ROYAL_CLAIM -> "Birdsong: discard to score 1 VP per clearing you rule"
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
        fun item(suit: Suit, name: String, vp: Int, vararg cost: Suit) {
            cards += Card(cards.size, suit, name, CardKind.ITEM, cost.toList(), vp)
        }
        fun special(suit: Suit, kind: CardKind, vararg cost: Suit) {
            val name = if (kind == CardKind.FAVOR) "Favor of the ${suit.label}s" else kind.label
            cards += Card(cards.size, suit, name, kind, cost.toList())
        }

        // Fox
        special(F, CardKind.AMBUSH)
        special(F, CardKind.FAVOR, F, F, F)
        item(F, "Sword", 2, F, F)
        item(F, "Sword", 2, F, F)
        item(F, "Anvil", 2, F)
        item(F, "Hammer", 1, F)
        item(F, "Travel Gear", 1, F)
        item(F, "Crossbow", 1, F)
        item(F, "Gold Coins", 3, R, R, R)
        item(F, "Old Map", 2, M, F)
        special(F, CardKind.ARMORERS, F)
        special(F, CardKind.BRUTAL_TACTICS, F, F)
        // Rabbit
        special(R, CardKind.AMBUSH)
        special(R, CardKind.FAVOR, R, R, R)
        item(R, "Boots", 2, R, R)
        item(R, "Boots", 1, R)
        item(R, "Satchel", 1, M)
        item(R, "Tea Leaves", 2, R)
        item(R, "Gold Coins", 3, R, R)
        item(R, "Handcart", 1, R)
        item(R, "Lantern", 2, R, F)
        item(R, "Shield", 2, R, M)
        special(R, CardKind.SAPPERS, M)
        special(R, CardKind.ROYAL_CLAIM, F, R, M)
        // Mouse
        special(M, CardKind.AMBUSH)
        special(M, CardKind.FAVOR, M, M, M)
        item(M, "Crossbow", 1, F)
        item(M, "Sword", 2, M, M)
        item(M, "Hammer", 2, M)
        item(M, "Satchel", 1, M)
        item(M, "Travel Gear", 1, M)
        item(M, "Lantern", 2, M, R)
        item(M, "Tea Leaves", 2, M)
        item(M, "Gold Coins", 3, M, M, M)
        special(M, CardKind.SCOUTING_PARTY, M, M)
        special(M, CardKind.SAPPERS, M)
        // Bird (wild)
        special(B, CardKind.AMBUSH)
        special(B, CardKind.AMBUSH)
        special(B, CardKind.ARMORERS, F)
        special(B, CardKind.SAPPERS, M)
        special(B, CardKind.BRUTAL_TACTICS, R, R)
        special(B, CardKind.SCOUTING_PARTY, M)
        item(B, "Crossbow", 1, F)
        item(B, "Sword", 2, F, F)
        item(B, "Boots", 1, R)
        item(B, "Satchel", 1, M)
        item(B, "Gold Coins", 3, R, M, F)
        item(B, "Anvil", 2, F)
        return cards
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
)

/** An original 12-clearing woodland: four clearings of each suit, four corners. */
object WoodlandMap {
    val clearings = listOf(
        ClearingDef(0, Suit.FOX, 1, 11f, 10f, corner = true),
        ClearingDef(1, Suit.RABBIT, 2, 47f, 11f),
        ClearingDef(2, Suit.MOUSE, 1, 88f, 10f, corner = true),
        ClearingDef(3, Suit.MOUSE, 2, 24f, 36f),
        ClearingDef(4, Suit.FOX, 3, 60f, 33f),
        ClearingDef(5, Suit.RABBIT, 2, 89f, 42f),
        ClearingDef(6, Suit.RABBIT, 2, 10f, 63f),
        ClearingDef(7, Suit.MOUSE, 3, 41f, 60f),
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

    val corners = clearings.filter { it.corner }.map { it.id }

    fun diagonal(corner: Int): Int = when (corner) {
        0 -> 11; 11 -> 0; 2 -> 9; 9 -> 2
        else -> error("not a corner: $corner")
    }

    fun name(c: Int): String = "${clearings[c].suit.symbol}${c + 1}"
}
