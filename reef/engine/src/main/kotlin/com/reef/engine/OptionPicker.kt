package com.reef.engine

/**
 * Turns a flat list of options into small steps a person can take on a phone: choose what to do,
 * tap reefs on the map in order, pick a card, a target, a variant (which arm, which lure offer),
 * then a number. Works the same for every faction, because it only looks at the fields every
 * [Option] shares.
 */
object OptionPicker {

    /** What has been chosen so far. [targetChosen] separates "no target chosen yet" from "chose no target". */
    data class Pick(
        val kind: String? = null,
        val reefs: List<Int> = emptyList(),
        val card: Int? = null,
        val target: FactionId? = null,
        val targetChosen: Boolean = false,
        val variant: String? = null,
        val count: Int? = null,
    )

    sealed class Step {
        data class ChooseKind(val kinds: List<String>) : Step()
        data class ChooseReef(val reefs: Set<Int>, val index: Int) : Step()
        data class ChooseCard(val cards: Set<Int>) : Step()
        data class ChooseTarget(val targets: List<FactionId?>) : Step()
        data class ChooseVariant(val variants: List<String>) : Step()
        data class ChooseCount(val counts: List<Int>) : Step()
        data class Confirm(val option: Option) : Step()
    }

    fun kinds(options: List<Option>): List<String> = options.map { it.kind }.distinct()

    fun matching(options: List<Option>, pick: Pick): List<Option> = options.filter { o ->
        (pick.kind == null || o.kind == pick.kind) &&
            pick.reefs.indices.all { o.reefs.getOrNull(it) == pick.reefs[it] } &&
            (pick.card == null || o.card == pick.card) &&
            (!pick.targetChosen || o.target == pick.target) &&
            (pick.variant == null || o.variant == pick.variant) &&
            (pick.count == null || o.count == pick.count)
    }

    fun next(options: List<Option>, pick: Pick): Step {
        val kinds = kinds(options)
        if (pick.kind == null && kinds.size > 1) return Step.ChooseKind(kinds)
        val p = if (pick.kind == null) pick.copy(kind = kinds.first()) else pick
        val m = matching(options, p)
        check(m.isNotEmpty()) { "Nothing matches $p" }
        val i = p.reefs.size
        if (m.any { it.reefs.size > i }) return Step.ChooseReef(m.mapNotNull { it.reefs.getOrNull(i) }.toSet(), i)
        if (p.card == null) {
            val cards = m.mapNotNull { it.card }.toSet()
            if (cards.size > 1) return Step.ChooseCard(cards)
        }
        if (!p.targetChosen) {
            val targets = m.map { it.target }.distinct()
            if (targets.size > 1) return Step.ChooseTarget(targets.sortedBy { it?.ordinal ?: -1 })
        }
        if (p.variant == null) {
            val variants = m.mapNotNull { it.variant }.distinct()
            if (variants.size > 1) return Step.ChooseVariant(variants)
        }
        if (p.count == null) {
            val counts = m.mapNotNull { it.count }.distinct().sortedDescending()
            if (counts.size > 1) return Step.ChooseCount(counts)
        }
        return Step.Confirm(m.first())
    }

    /** The pick with its kind filled in when there's only one kind to choose from. */
    fun withKind(options: List<Option>, pick: Pick): Pick =
        if (pick.kind == null && kinds(options).size == 1) pick.copy(kind = kinds(options).first()) else pick
}
