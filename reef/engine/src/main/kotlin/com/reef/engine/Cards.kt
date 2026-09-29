package com.reef.engine

import kotlinx.serialization.Serializable

@Serializable
enum class CardKind { GEAR, AMBUSH, DOMINANCE }

@Serializable
enum class GearEffect(val text: String) {
    NONE(""),
    EXTRA_DRAW("Draw 1 extra card at Dusk."),
    EXTRA_ACTION("1 extra Day action each turn, if your Day has a fixed number of actions."),
    BATTLE_HIT("Deal 1 extra hit in battles you start."),
    DEFENSE("Ignore the first hit in battles you defend."),
}

/**
 * One card of the 54-card Tide deck. Every card has a suit, which is what you pay with.
 * Gear cards can also be crafted: [cost] lists the suits needed, where [Suit.MOON] means any suit.
 */
data class Card(
    val id: Int,
    val name: String,
    val suit: Suit,
    val kind: CardKind,
    val cost: List<Suit> = emptyList(),
    val vp: Int = 0,
    val effect: GearEffect = GearEffect.NONE,
) {
    fun describe(): String = when (kind) {
        CardKind.AMBUSH -> if (suit == Suit.MOON) "Ambush in any reef: 2 hits before the roll." else "Ambush in a ${suit.label} reef: 2 hits before the roll."
        CardKind.DOMINANCE -> if (suit == Suit.MOON) "Dominance: win by ruling two opposite gates." else "Dominance: win by ruling three ${suit.label} reefs."
        CardKind.GEAR -> buildString {
            append("Craft with ")
            append(cost.joinToString(" + ") { if (it == Suit.MOON) "any" else it.label })
            append(": $vp VP.")
            if (effect != GearEffect.NONE) append(" ").append(effect.text)
        }
    }
}

object Cards {
    val all: List<Card> = buildList {
        fun add(n: Int, name: String, suit: Suit, kind: CardKind, cost: List<Suit> = emptyList(), vp: Int = 0, effect: GearEffect = GearEffect.NONE) =
            repeat(n) { add(Card(size, name, suit, kind, cost, vp, effect)) }

        val any = Suit.MOON
        data class SuitSet(val suit: Suit, val charm: String, val treasure: String, val relic: String, val tool: String, val effect: GearEffect)
        for (s in listOf(
            SuitSet(Suit.KELP, "Kelp Charm", "Kelp Crown", "Kelp Idol", "Plankton Net", GearEffect.EXTRA_DRAW),
            SuitSet(Suit.SPONGE, "Sponge Charm", "Sponge Throne", "Sponge Idol", "Barbed Spine", GearEffect.BATTLE_HIT),
            SuitSet(Suit.PEARL, "Pearl Bead", "Black Pearl", "Pearl Idol", "Nacre Plate", GearEffect.DEFENSE),
        )) {
            add(1, "${s.suit.label} Dominance", s.suit, CardKind.DOMINANCE)
            add(1, "${s.suit.label} Ambush", s.suit, CardKind.AMBUSH)
            add(6, s.charm, s.suit, CardKind.GEAR, listOf(s.suit), 1)
            add(3, s.treasure, s.suit, CardKind.GEAR, listOf(s.suit, s.suit), 2)
            add(1, s.relic, s.suit, CardKind.GEAR, listOf(s.suit, s.suit, any), 3)
            add(2, s.tool, s.suit, CardKind.GEAR, listOf(s.suit, any), 1, s.effect)
        }
        add(1, "Moon Dominance", Suit.MOON, CardKind.DOMINANCE)
        add(2, "Moon Ambush", Suit.MOON, CardKind.AMBUSH)
        add(3, "Moonstone", Suit.MOON, CardKind.GEAR, listOf(any), 1)
        add(2, "Moon Shell", Suit.MOON, CardKind.GEAR, listOf(any, any), 2)
        add(2, "Tide Chart", Suit.MOON, CardKind.GEAR, listOf(any, any), 1, GearEffect.EXTRA_ACTION)
        add(2, "Glow Lantern", Suit.MOON, CardKind.GEAR, listOf(any, any), 1, GearEffect.EXTRA_DRAW)
    }

    operator fun get(id: Int): Card = all[id]

    init {
        check(all.size == 54) { "The Tide deck must have 54 cards, has ${all.size}" }
    }
}
