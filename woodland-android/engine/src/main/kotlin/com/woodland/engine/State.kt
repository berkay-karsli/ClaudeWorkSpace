package com.woodland.engine

class ClearingState(val def: ClearingDef) {
    val id get() = def.id
    val suit get() = def.suit
    val warriors = IntArray(Faction.entries.size)
    val buildings = mutableListOf<BuildingType>()
    var wood = 0
    var keep = false
    var sympathy = false

    fun warriors(f: Faction) = warriors[f.ordinal]
    fun buildings(f: Faction) = buildings.count { it.owner == f }
    fun tokens(f: Faction) = when (f) {
        Faction.CATS -> wood + (if (keep) 1 else 0)
        Faction.ALLIANCE -> if (sympathy) 1 else 0
        Faction.BIRDS -> 0
    }

    /** Pieces that count for ruling: warriors and buildings. */
    fun rulePower(f: Faction) = warriors(f) + buildings(f)
    fun hasAnyPiece(f: Faction) = warriors(f) + buildings(f) + tokens(f) > 0
    val freeSlots get() = def.slots - buildings.size
}

enum class Leader(val label: String, val viziers: List<DecreeColumn>, val text: String) {
    BUILDER("Builder", listOf(DecreeColumn.RECRUIT, DecreeColumn.MOVE), "Crafted items score full VP"),
    CHARISMATIC("Charismatic", listOf(DecreeColumn.RECRUIT, DecreeColumn.BATTLE), "Recruit places 2 warriors"),
    COMMANDER("Commander", listOf(DecreeColumn.MOVE, DecreeColumn.BATTLE), "Deal an extra hit when attacking"),
    DESPOT("Despot", listOf(DecreeColumn.MOVE, DecreeColumn.BUILD), "+1 VP when removing buildings/tokens in battle");
}

enum class DecreeColumn(val label: String) { RECRUIT("Recruit"), MOVE("Move"), BATTLE("Battle"), BUILD("Build") }

class PlayerState(val faction: Faction, val human: Boolean) {
    var vp = 0
    val hand = mutableListOf<Card>()
    val effects = mutableListOf<Card>()

    // Bird Dynasty
    var leader: Leader? = null
    val usedLeaders = mutableListOf<Leader>()
    val decree: List<MutableList<Card>> = DecreeColumn.entries.map { mutableListOf() }

    // Forest Uprising
    val supporters = mutableListOf<Card>()
    var officers = 0

    fun hasEffect(kind: CardKind) = effects.any { it.kind == kind }
}

class Seat(val faction: Faction, val human: Boolean)

class GameConfig(val seats: List<Seat>) {
    init {
        require(seats.size in 2..3) { "2 or 3 factions" }
        require(seats.map { it.faction }.toSet().size == seats.size)
    }

    fun encode(): String = seats.joinToString(",") { "${it.faction.name}:${if (it.human) "H" else "A"}" }

    companion object {
        fun decode(s: String) = GameConfig(s.split(",").map {
            val (f, h) = it.split(":")
            Seat(Faction.valueOf(f), h == "H")
        })
    }
}
