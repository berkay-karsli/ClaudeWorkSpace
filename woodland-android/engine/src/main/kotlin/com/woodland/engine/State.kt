package com.woodland.engine

class ClearingState(val def: ClearingDef) {
    val id get() = def.id
    val suit get() = def.suit
    val warriors = IntArray(Faction.entries.size)
    val buildings = mutableListOf<BuildingType>()
    var wood = 0
    var keep = false
    var sympathy = false
    /** Items still hidden in this clearing's ruin; the ruin fills a slot until emptied. */
    val ruin = mutableListOf<ItemType>()

    fun warriors(f: Faction) = warriors[f.ordinal]
    fun buildings(f: Faction) = buildings.count { it.owner == f }
    fun tokens(f: Faction) = when (f) {
        Faction.CATS -> wood + (if (keep) 1 else 0)
        Faction.ALLIANCE -> if (sympathy) 1 else 0
        Faction.BIRDS, Faction.VAGABOND -> 0
    }

    /** Pieces that count for ruling: warriors and buildings. */
    fun rulePower(f: Faction) = warriors(f) + buildings(f)
    fun hasAnyPiece(f: Faction) = warriors(f) + buildings(f) + tokens(f) > 0
    val hasRuin get() = ruin.isNotEmpty()
    val freeSlots get() = def.slots - buildings.size - (if (hasRuin) 1 else 0)
}

enum class Leader(val label: String, val viziers: List<DecreeColumn>, val text: String) {
    BUILDER("Builder", listOf(DecreeColumn.RECRUIT, DecreeColumn.MOVE), "Crafted items score full VP"),
    CHARISMATIC("Charismatic", listOf(DecreeColumn.RECRUIT, DecreeColumn.BATTLE), "Recruit places 2 warriors"),
    COMMANDER("Commander", listOf(DecreeColumn.MOVE, DecreeColumn.BATTLE), "Deal an extra hit when attacking"),
    DESPOT("Despot", listOf(DecreeColumn.MOVE, DecreeColumn.BUILD), "+1 VP when removing buildings/tokens in battle");
}

enum class DecreeColumn(val label: String) { RECRUIT("Recruit"), MOVE("Move"), BATTLE("Battle"), BUILD("Build") }

enum class VbCharacter(val label: String, val start: List<ItemType>, val special: String) {
    THIEF("Thief", listOf(ItemType.BOOTS, ItemType.TORCH, ItemType.TEA, ItemType.SWORD), "Steal: exhaust a torch to take a random card from a player in your clearing"),
    TINKER("Tinker", listOf(ItemType.BOOTS, ItemType.TORCH, ItemType.BAG, ItemType.HAMMER), "Day Labor: exhaust a torch to take a card matching your clearing from the discard pile"),
    RANGER("Ranger", listOf(ItemType.BOOTS, ItemType.TORCH, ItemType.CROSSBOW, ItemType.SWORD), "Hideout: exhaust a torch to repair 3 items, then end Daylight");
}

/** The Vagabond's standing with a faction. [aids] is how many aids in one turn reach the next step. */
enum class Relation(val label: String, val aids: Int, val vp: Int) {
    HOSTILE("Hostile", 0, 0),
    INDIFFERENT("Indifferent", 1, 1),
    AMIABLE("Amiable", 2, 2),
    FRIENDLY("Friendly", 2, 2),
    ALLIED("Allied", 1, 2);
}

class Item(val type: ItemType) {
    var exhausted = false
    var damaged = false
    val ready get() = !exhausted && !damaged
    override fun toString() = type.icon + type.label + when {
        damaged && exhausted -> " (damaged, exhausted)"
        damaged -> " (damaged)"
        exhausted -> " (exhausted)"
        else -> ""
    }
}

class PlayerState(val faction: Faction, val human: Boolean) {
    var vp = 0
    val hand = mutableListOf<Card>()
    val effects = mutableListOf<Card>()
    /** Items this faction crafted; the Vagabond can take them when aiding. */
    val craftedItems = mutableListOf<ItemType>()
    /** An activated dominance card: this player no longer scores VP. */
    var dominance: Card? = null
    /** The Vagabond's coalition partner after it plays a dominance card. */
    var coalition: Faction? = null

    // Eyrie Dynasties
    var leader: Leader? = null
    val usedLeaders = mutableListOf<Leader>()
    val decree: List<MutableList<Card>> = DecreeColumn.entries.map { mutableListOf() }

    // Woodland Alliance
    val supporters = mutableListOf<Card>()
    var officers = 0

    // Vagabond
    var character: VbCharacter? = null
    val items = mutableListOf<Item>()
    val relations = mutableMapOf<Faction, Relation>()
    val aidsThisTurn = mutableMapOf<Faction, Int>()
    val questsDone = IntArray(3)

    fun hasEffect(kind: CardKind) = effects.any { it.kind == kind }
    fun undamaged(t: ItemType) = items.count { it.type == t && !it.damaged }
    fun ready(t: ItemType) = items.count { it.type == t && it.ready }
}

class Seat(val faction: Faction, val human: Boolean)

class GameConfig(val seats: List<Seat>) {
    init {
        require(seats.size in 2..4) { "2 to 4 factions" }
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
