package com.reef.engine

/**
 * One choice a player can make. The UI narrows options down by [kind], then by the [reefs] path
 * (tapped in order on the map), then [card], [target] and [count]. Bots pick from the same list.
 */
sealed class Option {
    abstract val kind: String
    open val reefs: List<Int> = emptyList()
    open val card: Int? = null
    open val target: FactionId? = null
    open val count: Int? = null

    /** Whether this uses one of a fixed-action faction's Day actions. */
    open val costsAction: Boolean = false

    /** Whether the result depends on dice. */
    open val random: Boolean = false
    abstract fun describe(): String
}

data class PlaceSetup(val reef: Int) : Option() {
    override val kind = "Set up"
    override val reefs = listOf(reef)
    override fun describe() = "Set up in ${Board.name(reef)}"
}

data class Arrive(val reef: Int) : Option() {
    override val kind = "Arrive"
    override val reefs = listOf(reef)
    override fun describe() = "A new shark arrives at ${Board.name(reef)}"
}

data class Move(val from: Int, val to: Int, val n: Int, val scent: Boolean) : Option() {
    override val kind = "Move"
    override val reefs = listOf(from, to)
    override val count = n
    override val costsAction = true
    override fun describe() = "Move $n from ${Board.name(from)} to ${Board.name(to)}" + if (scent) " (following Blood)" else ""
}

/** Sharks' action: move, then optionally battle [prey] where the sharks arrive. */
data class Hunt(val from: Int, val to: Int, val n: Int, val scent: Boolean, val prey: FactionId?) : Option() {
    override val kind = "Hunt"
    override val reefs = listOf(from, to)
    override val count = n
    override val target = prey
    override val costsAction = true
    override val random = prey != null
    override fun describe() = "Hunt: $n from ${Board.name(from)} to ${Board.name(to)}" +
        (if (scent) " following Blood" else "") + (prey?.let { ", then battle ${it.display}" } ?: ", no battle")
}

data class Battle(val reef: Int, val defender: FactionId) : Option() {
    override val kind = "Battle"
    override val reefs = listOf(reef)
    override val target = defender
    override val costsAction = true
    override val random = true
    override fun describe() = "Battle ${defender.display} in ${Board.name(reef)}"
}

data class Grow(val reef: Int, val cardId: Int) : Option() {
    override val kind = "Grow"
    override val reefs = listOf(reef)
    override val card = cardId
    override val costsAction = true
    override fun describe() = "Grow coral in ${Board.name(reef)}, discarding ${Cards[cardId].name}"
}

/** A spawn from coral already on the map, or, with no coral left, 2 polyps drifting into [into]. */
data class Spawn(val cardId: Int, val into: Int? = null) : Option() {
    override val kind = "Spawn"
    override val reefs = listOfNotNull(into)
    override val card = cardId
    override val costsAction = true
    override fun describe(): String {
        val c = Cards[cardId]
        if (into != null) return "Spawn 2 polyps into ${Board.name(into)}, discarding ${c.name}"
        val from = if (c.suit == Suit.MOON) "every reef with coral" else "${c.suit.label} reefs with coral"
        return "Spawn from $from, discarding ${c.name}"
    }
}

data class Craft(val cardId: Int) : Option() {
    override val kind = "Craft"
    override val card = cardId
    override fun describe() = "Craft ${Cards[cardId].name} (${Cards[cardId].vp} VP)"
}

data class Surge(val cardId: Int) : Option() {
    override val kind = "Extra action"
    override val card = cardId
    override fun describe() = "Discard ${Cards[cardId].name} for 1 extra action"
}

data class PlayDominance(val cardId: Int) : Option() {
    override val kind = "Dominance"
    override val card = cardId
    override fun describe() = "Play ${Cards[cardId].name}. You stop scoring VP."
}

data object EndDay : Option() {
    override val kind = "End Day"
    override fun describe() = "End your Day"
}

data class PlayAmbush(val cardId: Int) : Option() {
    override val kind = "Ambush"
    override val card = cardId
    override val random = true
    override fun describe() = "Ambush with ${Cards[cardId].name}: 2 hits before the roll"
}

data object NoAmbush : Option() {
    override val kind = "No ambush"
    override val random = true
    override fun describe() = "Don't ambush"
}

data class Discard(val cardId: Int) : Option() {
    override val kind = "Discard"
    override val card = cardId
    override fun describe() = "Discard ${Cards[cardId].name}"
}

/** What the game is waiting for: which player decides, and their options. */
data class Decision(val player: Int, val prompt: String, val options: List<Option>)
