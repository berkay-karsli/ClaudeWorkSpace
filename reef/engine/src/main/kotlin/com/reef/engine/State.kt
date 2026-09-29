package com.reef.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class FactionId(val display: String) {
    SHARKS("Sharks"),
    SARDINES("Sardines"),
    LIONFISH("Lionfish"),
    STARFISH("Starfish"),
    CORAL("Coral"),
    JELLYFISH("Jellyfish"),
    PARROTFISH("Parrotfish"),
    TURTLES("Sea Turtles"),
    SNAKE("Sea Snake"),
    REMORAS("Remoras"),
    CRABS("Hermit Crabs"),
    ANGLERS("Anglerfish"),
    OCTOPUS("Octopus"),
    CUTTLEFISH("Cuttlefish");

    /** The faction's key in the design data (plates.json). */
    val key: String get() = name.lowercase()
}

/**
 * Buildings fill slots and count for rule. Tokens sit in a reef and don't count for rule;
 * Rubble is the one token that also fills a slot.
 */
@Serializable
enum class PieceType(val label: String, val building: Boolean, val fillsSlot: Boolean) {
    CORAL("coral", building = true, fillsSlot = true),
    MARKET("market", building = true, fillsSlot = true),
    NEST("nest", building = true, fillsSlot = true),
    BLOOD("Blood", building = false, fillsSlot = false),
    RUBBLE("Rubble", building = false, fillsSlot = true),
    EGG("egg", building = false, fillsSlot = false),
    LURE("lure", building = false, fillsSlot = false),
    PIGMENT("pigment", building = false, fillsSlot = false),
    CYST("cyst", building = false, fillsSlot = false),
    COCOON("cocoon", building = false, fillsSlot = false),
}

/**
 * A building or token in a reef. [owner] is null for neutral tokens such as Blood.
 * [suit] is a pigment's color; [variant] is a lure's offer.
 */
@Serializable
data class Piece(val owner: FactionId?, val type: PieceType, val suit: Suit? = null, val variant: String? = null)

@Serializable
class ReefState(
    val warriors: MutableMap<FactionId, Int> = mutableMapOf(),
    val pieces: MutableList<Piece> = mutableListOf(),
) {
    fun warriors(f: FactionId): Int = warriors[f] ?: 0
    fun addWarriors(f: FactionId, n: Int) {
        val v = warriors(f) + n
        check(v >= 0) { "negative warriors for $f" }
        if (v == 0) warriors.remove(f) else warriors[f] = v
    }
    fun setWarriors(f: FactionId, n: Int) {
        if (n <= 0) warriors.remove(f) else warriors[f] = n
    }
    fun buildings(): List<Piece> = pieces.filter { it.type.building }
    fun buildingsOf(f: FactionId): Int = pieces.count { it.owner == f && it.type.building }
    fun has(type: PieceType): Boolean = pieces.any { it.type == type }
    fun count(type: PieceType, owner: FactionId? = null): Int = pieces.count { it.type == type && (owner == null || it.owner == owner) }
}

@Serializable
enum class MarkerType { SANDBAR, CURRENT }

/**
 * A channel marker on the channel between reefs [a] and [b]. A current arrow flows away from [from].
 * [seq] orders markers by when they were placed.
 */
@Serializable
data class ChannelMarker(val a: Int, val b: Int, val type: MarkerType, val owner: FactionId, val from: Int? = null, val seq: Int = 0) {
    fun joins(x: Int, y: Int) = (a == x && b == y) || (a == y && b == x)
    fun other(x: Int) = if (x == a) b else a
}

// ---- Faction states ----------------------------------------------------------------------------

@Serializable
sealed class FactionState

@Serializable
@SerialName("sharks")
class SharksState(
    var supply: Int = 12,
    /** Sharks per reef that have moved this turn. The rest drown at Dusk. */
    val moved: MutableMap<Int, Int> = mutableMapOf(),
    var arrived: Boolean = false,
) : FactionState()

@Serializable
@SerialName("coral")
class CoralState(var polyps: Int = 20, var coral: Int = 15, var spawned: Boolean = false, var bleached: Int = 0) : FactionState()

@Serializable
@SerialName("sardines")
class SardinesState(
    var supply: Int = 30,
    /** For each reef, how many of the sardines there came in by each gate. */
    val origins: MutableMap<Int, MutableMap<Int, Int>> = mutableMapOf(),
    var runGate: Int? = null,
    var runDone: Boolean = false,
    var exitDone: Boolean = false,
    /** Where a school of 6 or more just arrived and may push, until the next action. */
    var pushAt: Int? = null,
) : FactionState()

@Serializable
@SerialName("lionfish")
class LionfishState(
    var supply: Int = 24,
    /** Reefs where the lionfish gorged this turn: they can't move out. */
    val stuffed: MutableSet<Int> = mutableSetOf(),
    /** Reefs still to breed this Dusk. */
    val breeding: MutableList<Int> = mutableListOf(),
) : FactionState()

@Serializable
@SerialName("starfish")
class StarfishState(var supply: Int = 20, var rubble: Int = 6, var spawnDone: Boolean = false, var setupFirst: Int? = null) : FactionState()

@Serializable
@SerialName("jellyfish")
class JellyfishState(
    var supply: Int = 24,
    var arrows: Int = 4,
    var cysts: Int = 4,
    /** The Dusk drift, planned once: reef to destination, -1 while undecided. */
    val drift: MutableMap<Int, Int> = mutableMapOf(),
    var driftPlanned: Boolean = false,
    var setupFirst: Int? = null,
) : FactionState()

@Serializable
@SerialName("parrotfish")
class ParrotfishState(
    var supply: Int = 16,
    var sandbars: Int = 8,
    var sand: Int = 0,
    val islands: MutableSet<Int> = mutableSetOf(),
    var arrivals: Int = 0,
    var cocoon: Int = 1,
    var cocoonDone: Boolean = false,
) : FactionState()

@Serializable
data class Turtle(val id: Int, var reef: Int, val food: MutableSet<Suit> = mutableSetOf())

@Serializable
@SerialName("turtles")
class TurtlesState(
    val turtles: MutableList<Turtle> = mutableListOf(),
    var nests: Int = 3,
    var eggs: Int = 10,
    var nestsBuilt: Int = 0,
    var nextId: Int = 0,
) : FactionState()

@Serializable
@SerialName("snake")
class SnakeState(
    /** The reef of each piece, Head first. Empty means the snake is gone until a new Head arrives. */
    val body: MutableList<Int> = mutableListOf(),
    var segments: Int = 14,
) : FactionState()

@Serializable
@SerialName("remoras")
class RemorasState(
    var supply: Int = 8,
    /** Remoras attached per reef, per host faction. */
    val attached: MutableMap<Int, MutableMap<FactionId, Int>> = mutableMapOf(),
    var arrived: Boolean = false,
) : FactionState()

@Serializable
@SerialName("crabs")
class CrabsState(
    var supply: Int = 12,
    var markets: Int = 5,
    var marketsBuilt: Int = 0,
    var pool: Int = 8,
    var price: Int = 1,
    val till: MutableList<Int> = mutableListOf(),
    /** Shells placed per reef, per faction wearing them. */
    val shells: MutableMap<Int, MutableMap<FactionId, Int>> = mutableMapOf(),
    var tillDone: Boolean = false,
    var priceSet: Boolean = false,
) : FactionState()

@Serializable
@SerialName("anglers")
class AnglersState(
    var supply: Int = 7,
    var trench: Int = 3,
    var lures: Int = 4,
    var eaten: Int = 0,
    val movedLures: MutableSet<Int> = mutableSetOf(),
    var luresDone: Boolean = false,
    val snapped: MutableSet<Int> = mutableSetOf(),
    var snapsDone: Boolean = false,
    /** Factions that took the bait since the last Dusk: they can be snapped anywhere. */
    val hooked: MutableSet<FactionId> = mutableSetOf(),
) : FactionState()

/** A stolen treasure: a token (its type and owner) or a card. */
@Serializable
data class GardenItem(val kind: String, val owner: FactionId? = null, val type: PieceType? = null, val suit: Suit? = null, val card: Int? = null)

@Serializable
@SerialName("octopus")
class OctopusState(
    var mantle: Int = -1,
    /** Each arm's reef, or -1 while it regrows. */
    val arms: MutableList<Int> = MutableList(8) { -1 },
    val orders: MutableList<Int?> = MutableList(8) { null },
    val garden: MutableList<GardenItem> = mutableListOf(),
    var nextArm: Int = 0,
    var ordersAdded: Int = 0,
    var ordersDone: Boolean = false,
    var mantleDone: Boolean = false,
    var rewired: Boolean = false,
) : FactionState()

@Serializable
@SerialName("cuttlefish")
class CuttlefishState(
    var supply: Int = 12,
    val pigments: MutableMap<Suit, Int> = mutableMapOf(Suit.KELP to 2, Suit.SPONGE to 2, Suit.PEARL to 2),
    val gallery: MutableList<Int> = mutableListOf(),
    val galleryDeck: MutableList<Int> = mutableListOf(),
    var setupFirst: Int? = null,
    var arrived: Boolean = false,
) : FactionState()

// ---- Players, turns, interruptions -------------------------------------------------------------

@Serializable
class PlayerState(
    val faction: FactionId,
    val human: Boolean,
    val fs: FactionState,
    var vp: Int = 0,
    val hand: MutableList<Int> = mutableListOf(),
    val gear: MutableList<Int> = mutableListOf(),
    /** The Dominance card this player has played, if any. While set, the player scores no VP. */
    var dominance: Int? = null,
    var setupDone: Boolean = false,
)

@Serializable
enum class Phase { SETUP, DAWN, DAY, DUSK, OVER }

@Serializable
class Turn(
    var actionsLeft: Int = 0,
    var surgeUsed: Boolean = false,
    /** Per-turn counters, such as how many blooms a faction has made. */
    val used: MutableMap<String, Int> = mutableMapOf(),
    var shopDone: Boolean = false,
    var duskStarted: Boolean = false,
) {
    fun used(key: String): Int = used[key] ?: 0
    fun use(key: String, n: Int = 1) { used[key] = used(key) + n }
}

/** A choice that interrupts the turn, made by [player] before the game goes on. */
@Serializable
sealed class Pending {
    abstract val player: Int
}

/** The defender of a battle may ambush, or with sardines give up half the school. */
@Serializable
@SerialName("defend")
class DefendPending(override val player: Int, val attacker: Int, val reef: Int, val snap: Int = 0) : Pending()

/** At the start of its Day, a faction may buy shells from the Hermit Crabs. */
@Serializable
@SerialName("shop")
class ShopPending(override val player: Int) : Pending()

/** Paying for a shell, one card at a time. */
@Serializable
@SerialName("pay")
class PayPending(override val player: Int, val reef: Int, var left: Int) : Pending()

@Serializable
class GameState(
    val players: MutableList<PlayerState>,
    val reefs: MutableList<ReefState>,
    val drawPile: MutableList<Int>,
    val discard: MutableList<Int>,
    var rng: Long,
    var current: Int = 0,
    var round: Int = 1,
    var phase: Phase = Phase.SETUP,
    var turn: Turn = Turn(),
    val pending: MutableList<Pending> = mutableListOf(),
    val markers: MutableList<ChannelMarker> = mutableListOf(),
    var markerSeq: Int = 0,
    var winner: Int? = null,
    var winText: String = "",
    /** Blood tokens not on the map. */
    var blood: Int = BLOOD_TOKENS,
    val log: MutableList<String> = mutableListOf(),
    /** The most recent battle, for the app to show. */
    var lastBattle: BattleReport? = null,
) {
    fun player(f: FactionId): Int = players.indexOfFirst { it.faction == f }
    fun inGame(f: FactionId): Boolean = players.any { it.faction == f }
    inline fun <reified T : FactionState> state(f: FactionId): T? = players.firstOrNull { it.faction == f }?.fs as? T

    /** The suit a reef counts as right now: a Cuttlefish pigment repaints it. */
    fun suitOf(reef: Int): Suit = reefs[reef].pieces.firstOrNull { it.type == PieceType.PIGMENT }?.suit ?: Board.reefs[reef].suit

    fun nextInt(bound: Int): Int {
        // SplitMix64, kept in the state so games and saves replay exactly.
        rng += -0x61c8864680b583ebL
        var z = rng
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return ((z ushr 1) % bound).toInt()
    }

    fun deepCopy(): GameState = json.decodeFromString(serializer(), json.encodeToString(serializer(), this))
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val BLOOD_TOKENS = 8
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): GameState = json.decodeFromString(serializer(), text)
    }
}

/** What happened in a battle, for the battle popup. */
@Serializable
data class BattleReport(
    val attacker: FactionId,
    val defender: FactionId,
    val reef: Int,
    val dice: List<Int>,
    val attackerHits: Int,
    val defenderHits: Int,
    val notes: List<String>,
    val round: Int,
    val seq: Int,
)
