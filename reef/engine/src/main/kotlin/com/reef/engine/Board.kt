package com.reef.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val label: String) {
    KELP("Kelp"),
    SPONGE("Sponge"),
    PEARL("Pearl"),

    /** Wild: counts as any suit when you pay with it. */
    MOON("Moon"),
}

/** A reef's fixed printed facts. Its changing contents live in [ReefState]. */
data class ReefInfo(
    val id: Int,
    val name: String,
    val suit: Suit,
    val slots: Int,
    val gate: Boolean,
    val shore: Boolean,
    val rim: Boolean,
    /** Position on the 800 x 580 map, the same coordinates as the design page. */
    val x: Float,
    val y: Float,
)

object Board {
    const val WIDTH = 800f
    const val HEIGHT = 580f

    val reefs: List<ReefInfo> = listOf(
        ReefInfo(0, "Gull Rock", Suit.PEARL, 1, gate = true, shore = true, rim = false, x = 90f, y = 120f),
        ReefInfo(1, "Flats", Suit.SPONGE, 2, gate = false, shore = true, rim = false, x = 300f, y = 110f),
        ReefInfo(2, "Kelp Wood", Suit.KELP, 2, gate = false, shore = true, rim = false, x = 510f, y = 130f),
        ReefInfo(3, "Shipwreck", Suit.PEARL, 1, gate = true, shore = true, rim = false, x = 710f, y = 120f),
        ReefInfo(4, "Garden", Suit.SPONGE, 2, gate = false, shore = false, rim = false, x = 110f, y = 290f),
        ReefInfo(5, "Hollow", Suit.SPONGE, 3, gate = false, shore = false, rim = false, x = 310f, y = 270f),
        ReefInfo(6, "Arch", Suit.KELP, 3, gate = false, shore = false, rim = false, x = 500f, y = 290f),
        ReefInfo(7, "Meadow", Suit.KELP, 2, gate = false, shore = false, rim = false, x = 700f, y = 280f),
        ReefInfo(8, "Driftwood", Suit.KELP, 1, gate = true, shore = false, rim = false, x = 90f, y = 460f),
        ReefInfo(9, "Oyster Beds", Suit.PEARL, 2, gate = false, shore = false, rim = true, x = 290f, y = 440f),
        ReefInfo(10, "Blue Hole", Suit.PEARL, 2, gate = false, shore = false, rim = true, x = 500f, y = 450f),
        ReefInfo(11, "Shelf", Suit.SPONGE, 1, gate = true, shore = false, rim = true, x = 710f, y = 460f),
    )

    /** One-way printed currents (the Gyre). Moving from the first reef to the second never needs rule. */
    val currents: List<Pair<Int, Int>> = listOf(5 to 6, 6 to 10, 10 to 9, 9 to 5)

    /** Every channel, as unordered pairs. The Gyre's channels are ordinary channels that also carry a current. */
    val channels: List<Pair<Int, Int>> = listOf(
        0 to 1, 1 to 2, 2 to 3, 0 to 4, 1 to 5, 2 to 6, 3 to 7, 4 to 5,
        6 to 7, 4 to 8, 7 to 11, 8 to 9, 10 to 11, 0 to 5, 7 to 10,
    ) + currents

    /** Pairs of opposite gates, for Moon Dominance. */
    val oppositeGates: List<Pair<Int, Int>> = listOf(0 to 11, 3 to 8)

    val gates: List<Int> = reefs.filter { it.gate }.map { it.id }

    private val adjacency: List<List<Int>> = reefs.map { r ->
        channels.mapNotNull { (a, b) ->
            when (r.id) {
                a -> b
                b -> a
                else -> null
            }
        }.sorted()
    }

    private val distances: List<IntArray> = reefs.map { start ->
        val dist = IntArray(reefs.size) { Int.MAX_VALUE }
        dist[start.id] = 0
        val queue = ArrayDeque(listOf(start.id))
        while (queue.isNotEmpty()) {
            val r = queue.removeFirst()
            for (n in adjacency[r]) if (dist[n] == Int.MAX_VALUE) {
                dist[n] = dist[r] + 1
                queue.addLast(n)
            }
        }
        dist
    }

    fun neighbors(reef: Int): List<Int> = adjacency[reef]
    fun isCurrent(from: Int, to: Int): Boolean = (from to to) in currents
    fun distance(a: Int, b: Int): Int = distances[a][b]
    fun name(reef: Int): String = reefs[reef].name
}
