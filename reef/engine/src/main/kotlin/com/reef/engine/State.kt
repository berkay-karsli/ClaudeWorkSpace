package com.reef.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class FactionId(val display: String) {
    SHARKS("Sharks"),
    CORAL("Coral"),
}

/** Buildings fill slots and count for rule. Tokens sit in a reef and don't. */
@Serializable
enum class PieceType(val label: String, val building: Boolean) {
    CORAL("coral", building = true),
    BLOOD("Blood", building = false),
}

/** A building or token in a reef. [owner] is null for neutral tokens such as Blood. */
@Serializable
data class Piece(val owner: FactionId?, val type: PieceType)

@Serializable
class ReefState(
    val warriors: MutableMap<FactionId, Int> = mutableMapOf(),
    val pieces: MutableList<Piece> = mutableListOf(),
) {
    fun warriors(f: FactionId): Int = warriors[f] ?: 0
    fun addWarriors(f: FactionId, n: Int) {
        val v = warriors(f) + n
        if (v == 0) warriors.remove(f) else warriors[f] = v
    }
    fun buildings(): List<Piece> = pieces.filter { it.type.building }
    fun buildingsOf(f: FactionId): Int = pieces.count { it.owner == f && it.type.building }
    fun has(type: PieceType): Boolean = pieces.any { it.type == type }
}

/** Faction-specific supply and turn tracking. */
@Serializable
sealed class FactionState

@Serializable
@SerialName("sharks")
class SharksState(
    var supply: Int = 10,
    /** Sharks per reef that have moved this turn. The rest drown at Dusk. */
    val moved: MutableMap<Int, Int> = mutableMapOf(),
    var arrived: Boolean = false,
) : FactionState()

@Serializable
@SerialName("coral")
class CoralState(
    var polyps: Int = 20,
    var coral: Int = 15,
    var spawned: Boolean = false,
) : FactionState()

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
    /** Buildings already used for crafting this turn, per reef. */
    val crafted: MutableMap<Int, Int> = mutableMapOf(),
)

/** A battle waiting for the defender to decide on an Ambush. */
@Serializable
class PendingBattle(val attacker: Int, val defender: Int, val reef: Int)

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
    var battle: PendingBattle? = null,
    var winner: Int? = null,
    var winText: String = "",
    /** Blood tokens not on the map. */
    var blood: Int = BLOOD_TOKENS,
    val log: MutableList<String> = mutableListOf(),
) {
    fun player(f: FactionId): Int = players.indexOfFirst { it.faction == f }
    fun inGame(f: FactionId): Boolean = players.any { it.faction == f }

    /** The suit a reef counts as right now. */
    fun suitOf(reef: Int): Suit = Board.reefs[reef].suit

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
