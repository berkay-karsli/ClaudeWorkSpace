package com.reef.engine

/**
 * One choice a player can make. The UI narrows options down by [kind], then by the [reefs] path
 * (tapped in order on the map), then [card], [target], [variant] and [count]. Bots pick from the
 * same list. [icon] names the action's picture in the app.
 */
sealed class Option {
    abstract val kind: String
    open val reefs: List<Int> = emptyList()
    open val card: Int? = null
    open val target: FactionId? = null
    open val count: Int? = null
    open val variant: String? = null

    /** Whether this uses one of a fixed-action faction's Day actions. */
    open val costsAction: Boolean = false

    /** Whether the result depends on dice. */
    open val random: Boolean = false
    abstract fun describe(): String
}

private fun r(reef: Int) = Board.name(reef)
private fun c(card: Int) = Cards[card].name

// ---- Shared ------------------------------------------------------------------------------------

data class PlaceSetup(val reef: Int) : Option() {
    override val kind = "Set up"
    override val reefs = listOf(reef)
    override fun describe() = "Set up in ${r(reef)}"
}

/** New warriors arriving at a reef (usually a gate). */
data class Arrive(val reef: Int, val n: Int = 1, val what: String = "") : Option() {
    override val kind = "Arrive"
    override val reefs = listOf(reef)
    override fun describe() = (if (what.isEmpty()) "Arrive" else "$n $what arrive") + " at ${r(reef)}"
}

data class Move(val from: Int, val to: Int, val n: Int, val scent: Boolean = false) : Option() {
    override val kind = "Move"
    override val reefs = listOf(from, to)
    override val count = n
    override val costsAction = true
    override fun describe() = "Move $n from ${r(from)} to ${r(to)}"
}

data class Battle(val reef: Int, val defender: FactionId) : Option() {
    override val kind = "Battle"
    override val reefs = listOf(reef)
    override val target = defender
    override val costsAction = true
    override val random = true
    override fun describe() = "Battle ${defender.display} in ${r(reef)}"
}

data class DigOut(val a: Int, val b: Int) : Option() {
    override val kind = "Dig out"
    override val reefs = listOf(a, b)
    override val costsAction = true
    override fun describe() = "Dig out the sandbar between ${r(a)} and ${r(b)}"
}

data class Craft(val cardId: Int) : Option() {
    override val kind = "Craft"
    override val card = cardId
    override fun describe() = "Craft ${c(cardId)} (${Cards[cardId].vp} VP)"
}

data class Surge(val cardId: Int) : Option() {
    override val kind = "Extra action"
    override val card = cardId
    override fun describe() = "Discard ${c(cardId)} for 1 extra action"
}

data class PlayDominance(val cardId: Int) : Option() {
    override val kind = "Dominance"
    override val card = cardId
    override fun describe() = "Play ${c(cardId)}. You stop scoring VP."
}

data object EndDay : Option() {
    override val kind = "End Day"
    override fun describe() = "End your Day"
}

/** Ends a step that can be repeated, such as adding cards or placing lures. */
data class Done(val what: String) : Option() {
    override val kind = "Done"
    override val variant = what
    override fun describe() = what
}

data class PlayAmbush(val cardId: Int) : Option() {
    override val kind = "Ambush"
    override val card = cardId
    override val random = true
    override fun describe() = "Ambush with ${c(cardId)}: 2 hits before the roll"
}

data object NoAmbush : Option() {
    override val kind = "Fight"
    override val random = true
    override fun describe() = "Fight without an ambush"
}

data class Discard(val cardId: Int) : Option() {
    override val kind = "Discard"
    override val card = cardId
    override fun describe() = "Discard ${c(cardId)}"
}

// ---- Sharks ------------------------------------------------------------------------------------

/** Sharks' action: move, then optionally battle [prey] where the sharks arrive. */
data class Hunt(val from: Int, val to: Int, val n: Int, val scent: Boolean, val prey: FactionId?) : Option() {
    override val kind = "Hunt"
    override val reefs = listOf(from, to)
    override val count = n
    override val target = prey
    override val costsAction = true
    override val random = prey != null
    override fun describe() = "Hunt: $n from ${r(from)} to ${r(to)}" +
        (if (scent) " following Blood" else "") + (prey?.let { ", then battle ${it.display}" } ?: ", no battle")
}

// ---- Coral -------------------------------------------------------------------------------------

data class Grow(val reef: Int, val cardId: Int) : Option() {
    override val kind = "Grow"
    override val reefs = listOf(reef)
    override val card = cardId
    override val costsAction = true
    override fun describe() = "Grow coral in ${r(reef)}, discarding ${c(cardId)}"
}

/** A spawn from coral already on the map, or, with no coral left, 2 polyps drifting into [into]. */
data class Spawn(val cardId: Int, val into: Int? = null) : Option() {
    override val kind = "Spawn"
    override val reefs = listOfNotNull(into)
    override val card = cardId
    override val costsAction = true
    override fun describe(): String {
        val cd = Cards[cardId]
        if (into != null) return "Spawn 2 polyps into ${r(into)}, discarding ${cd.name}"
        val from = if (cd.suit == Suit.MOON) "every reef with coral" else "${cd.suit.label} reefs with coral"
        return "Spawn from $from, discarding ${cd.name}"
    }
}

// ---- Sardines ----------------------------------------------------------------------------------

data class RunGate(val gate: Int) : Option() {
    override val kind = "Run"
    override val reefs = listOf(gate)
    override fun describe() = "The run arrives at ${r(gate)}"
}

data class RunCard(val cardId: Int) : Option() {
    override val kind = "Add fish"
    override val card = cardId
    override fun describe() = "Discard ${c(cardId)}: 1 more sardine"
}

data class Exit(val gate: Int, val n: Int) : Option() {
    override val kind = "Leave"
    override val reefs = listOf(gate)
    override val count = n
    override fun describe() = "$n sardines leave the map at ${r(gate)}"
}

data class BaitBall(val to: Int) : Option() {
    override val kind = "Bait ball"
    override val reefs = listOf(to)
    override fun describe() = "Bait ball: lose half the school, the rest flee to ${r(to)}"
}

// ---- Lionfish ----------------------------------------------------------------------------------

data class Gorge(val reef: Int, val prey: FactionId) : Option() {
    override val kind = "Gorge"
    override val reefs = listOf(reef)
    override val target = prey
    override val costsAction = true
    override fun describe() = "Gorge on ${prey.display} in ${r(reef)}: 1 hit, no dice"
}

// ---- Starfish ----------------------------------------------------------------------------------

data class StarSpawn(val cardId: Int, val reef: Int) : Option() {
    override val kind = "Spawn"
    override val reefs = listOf(reef)
    override val card = cardId
    override fun describe() = "Spawn 2 starfish in ${r(reef)}, discarding ${c(cardId)}"
}

// ---- Jellyfish ---------------------------------------------------------------------------------

data class SetCurrent(val from: Int, val to: Int) : Option() {
    override val kind = "Current"
    override val reefs = listOf(from, to)
    override fun describe() = "Set a current from ${r(from)} to ${r(to)}"
}

data class Bloom(val cardId: Int, val into: Int? = null) : Option() {
    override val kind = "Bloom"
    override val reefs = listOfNotNull(into)
    override val card = cardId
    override fun describe(): String {
        if (into != null) return "Bloom 3 jellyfish into ${r(into)}, discarding ${c(cardId)}"
        val s = Cards[cardId].suit
        return "Bloom in " + (if (s == Suit.MOON) "every reef with jellyfish" else "${s.label} reefs with jellyfish") + ", discarding ${c(cardId)}"
    }
}

data class Drift(val from: Int, val to: Int) : Option() {
    override val kind = "Drift"
    override val reefs = listOf(from, to)
    override fun describe() = "Jellyfish in ${r(from)} drift to ${r(to)}"
}

// ---- Parrotfish --------------------------------------------------------------------------------

data class GrazeEat(val reef: Int, val owner: FactionId, val type: PieceType) : Option() {
    override val kind = "Graze"
    override val reefs = listOf(reef)
    override val target = owner
    override val variant = type.label
    override val costsAction = true
    override fun describe() = "Graze ${Game.possessive(owner.display)} ${type.label} in ${r(reef)}: +3 Sand"
}

data class GrazeChew(val reef: Int) : Option() {
    override val kind = "Graze"
    override val reefs = listOf(reef)
    override val variant = "bare reef"
    override val costsAction = true
    override fun describe() = "Chew the bare reef in ${r(reef)}: +2 Sand"
}

data class PlaceSandbar(val a: Int, val b: Int) : Option() {
    override val kind = "Sandbar"
    override val reefs = listOf(a, b)
    override val costsAction = true
    override fun describe() = "Sandbar between ${r(a)} and ${r(b)} (2 Sand)"
}

data class RaiseIsland(val reef: Int) : Option() {
    override val kind = "Island"
    override val reefs = listOf(reef)
    override val costsAction = true
    override fun describe() = "Raise ${r(reef)} into an island (5 Sand)"
}

// ---- Sea Turtles -------------------------------------------------------------------------------

data class TurtleMove(val id: Int, val from: Int, val to: Int, val carrying: String) : Option() {
    override val kind = "Swim"
    override val reefs = listOf(from, to)
    override val variant = "Turtle ${id + 1} ($carrying)"
    override val costsAction = true
    override fun describe() = "Turtle ${id + 1} swims from ${r(from)} to ${r(to)}"
}

data class Feed(val id: Int, val reef: Int, val suit: Suit) : Option() {
    override val kind = "Feed"
    override val reefs = listOf(reef)
    override val variant = "Turtle ${id + 1}"
    override val costsAction = true
    override fun describe() = "Turtle ${id + 1} eats ${suit.label} food in ${r(reef)}"
}

data class Lay(val id: Int, val reef: Int, val eggs: Int) : Option() {
    override val kind = "Lay"
    override val reefs = listOf(reef)
    override val variant = "Turtle ${id + 1}"
    override val costsAction = true
    override fun describe() = "Turtle ${id + 1} lays $eggs egg${if (eggs == 1) "" else "s"} in ${r(reef)}"
}

data class BuildNest(val reef: Int) : Option() {
    override val kind = "Nest"
    override val reefs = listOf(reef)
    override val costsAction = true
    override fun describe() = "Build a nest in ${r(reef)}"
}

// ---- Sea Snake ---------------------------------------------------------------------------------

data class Slither(val from: Int, val to: Int) : Option() {
    override val kind = "Slither"
    override val reefs = listOf(from, to)
    override val costsAction = true
    override fun describe() = "Slither the Head from ${r(from)} to ${r(to)}"
}

data class Bite(val reef: Int, val prey: FactionId) : Option() {
    override val kind = "Bite"
    override val reefs = listOf(reef)
    override val target = prey
    override val costsAction = true
    override val random = true
    override fun describe() = "Bite ${prey.display} in ${r(reef)}"
}

// ---- Remoras -----------------------------------------------------------------------------------

data class Swim(val from: Int, val to: Int, val n: Int) : Option() {
    override val kind = "Swim"
    override val reefs = listOf(from, to)
    override val count = n
    override val costsAction = true
    override fun describe() = "Swim $n free remora${if (n == 1) "" else "s"} from ${r(from)} to ${r(to)}"
}

data class Attach(val reef: Int, val host: FactionId, val n: Int) : Option() {
    override val kind = "Attach"
    override val reefs = listOf(reef)
    override val target = host
    override val count = n
    override val costsAction = true
    override fun describe() = "Attach $n remora${if (n == 1) "" else "s"} to ${host.display} in ${r(reef)}"
}

data class LetGo(val reef: Int, val host: FactionId) : Option() {
    override val kind = "Let go"
    override val reefs = listOf(reef)
    override val target = host
    override val costsAction = true
    override fun describe() = "Let go of ${host.display} in ${r(reef)}"
}

// ---- Hermit Crabs ------------------------------------------------------------------------------

data class AddToTill(val cardId: Int) : Option() {
    override val kind = "Till"
    override val card = cardId
    override fun describe() = "Put ${c(cardId)} in your Till"
}

data class Recruit(val reef: Int) : Option() {
    override val kind = "Recruit"
    override val reefs = listOf(reef)
    override val costsAction = true
    override fun describe() = "2 crabs join at ${r(reef)}"
}

data class BuildMarket(val reef: Int, val cardId: Int) : Option() {
    override val kind = "Market"
    override val reefs = listOf(reef)
    override val card = cardId
    override fun describe() = "Build a market in ${r(reef)}, paying ${c(cardId)} from the Till"
}

data class WearShell(val reef: Int) : Option() {
    override val kind = "Wear shell"
    override val reefs = listOf(reef)
    override fun describe() = "Your crabs in ${r(reef)} wear a shell"
}

data class SetPrice(val price: Int) : Option() {
    override val kind = "Price"
    override val count = price
    override fun describe() = "Shells cost $price card${if (price == 1) "" else "s"}"
}

data class BuyShell(val reef: Int) : Option() {
    override val kind = "Buy shell"
    override val reefs = listOf(reef)
    override fun describe() = "Buy a shell for your pieces in ${r(reef)}"
}

data class PayCard(val cardId: Int) : Option() {
    override val kind = "Pay"
    override val card = cardId
    override fun describe() = "Pay ${c(cardId)}"
}

// ---- Anglerfish --------------------------------------------------------------------------------

data class PlaceLure(val reef: Int, val offer: String) : Option() {
    override val kind = "Lure"
    override val reefs = listOf(reef)
    override val variant = offer
    override fun describe() = "Hang a $offer lure over ${r(reef)}"
}

data class MoveLure(val from: Int, val to: Int, val offer: String) : Option() {
    override val kind = "Move lure"
    override val reefs = listOf(from, to)
    override val variant = offer
    override fun describe() = if (from == to) "Change the lure over ${r(to)} to $offer" else "Move the lure from ${r(from)} to ${r(to)} ($offer)"
}

data class SpawnAnglers(val cardId: Int) : Option() {
    override val kind = "Spawn"
    override val card = cardId
    override fun describe() = "Discard ${c(cardId)}: 2 anglers join the Trench"
}

data class Snap(val reef: Int, val prey: FactionId, val n: Int) : Option() {
    override val kind = "Snap"
    override val reefs = listOf(reef)
    override val target = prey
    override val count = n
    override val random = true
    override fun describe() = "Snap: $n angler${if (n == 1) "" else "s"} rise at ${r(reef)} and bite ${prey.display}"
}

// ---- Octopus -----------------------------------------------------------------------------------

data class SetOrder(val arm: Int, val cardId: Int) : Option() {
    override val kind = "Order"
    override val card = cardId
    override val variant = "Arm ${arm + 1}"
    override fun describe() = "Arm ${arm + 1}: ${OctopusRules.orderName(Cards[cardId].suit)} (${c(cardId)})"
}

data class ArmReach(val arm: Int, val from: Int, val to: Int) : Option() {
    override val kind = "Reach"
    override val reefs = listOf(from, to)
    override val variant = "Arm ${arm + 1}"
    override fun describe() = "Arm ${arm + 1} reaches from ${r(from)} to ${r(to)}"
}

data class ArmGrab(val arm: Int, val reef: Int, val prey: FactionId) : Option() {
    override val kind = "Grab"
    override val reefs = listOf(reef)
    override val target = prey
    override val variant = "Arm ${arm + 1}"
    override val random = true
    override fun describe() = "Arm ${arm + 1} grabs at ${prey.display} in ${r(reef)}"
}

data class ArmSteal(val arm: Int, val reef: Int, val from: FactionId, val type: PieceType?) : Option() {
    override val kind = "Steal"
    override val reefs = listOf(reef)
    override val target = from
    override val variant = type?.label ?: "a card"
    override val random = type == null
    override fun describe() = "Arm ${arm + 1} steals " + (if (type == null) "a card from ${from.display}" else "${Game.possessive(from.display)} ${type.label}") + " in ${r(reef)}"
}

/** An arm that can't carry out its order recoils: its order and every higher arm's are lost. */
data class Recoil(val arm: Int, val lost: Int) : Option() {
    override val kind = "Recoil"
    override val variant = "Arm ${arm + 1}"
    override fun describe() = "Arm ${arm + 1} can't carry out its order: recoil, losing $lost order${if (lost == 1) "" else "s"} and $lost VP"
}

data class MantleMove(val reef: Int) : Option() {
    override val kind = "Mantle"
    override val reefs = listOf(reef)
    override fun describe() = "The Mantle jets to ${r(reef)}"
}

// ---- Cuttlefish --------------------------------------------------------------------------------

data class Paint(val reef: Int, val cardId: Int, val suit: Suit) : Option() {
    override val kind = "Paint"
    override val reefs = listOf(reef)
    override val card = cardId
    override val variant = suit.label
    override val costsAction = true
    override fun describe() = "Paint ${r(reef)} ${suit.label}, discarding ${c(cardId)}"
}

/** What the game is waiting for: which player decides, and their options. */
data class Decision(val player: Int, val prompt: String, val options: List<Option>)
