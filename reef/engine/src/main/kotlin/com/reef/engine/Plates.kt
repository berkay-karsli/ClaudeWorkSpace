package com.reef.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The rules text shown in the app. It is exported from the design data (docs/build_field_guide.py),
 * so the app always says exactly what the design guide says.
 */
object Plates {
    @Serializable
    data class Titled(val title: String, val text: String)

    @Serializable
    data class TurnStep(val phase: String, val text: String)

    @Serializable
    data class Plate(
        val key: String,
        val name: String,
        val role: String,
        val tagline: String,
        val idea: String,
        val rules: List<Titled>,
        val pieces: String,
        val setup: String,
        val turn: List<TurnStep>,
        val scores: List<String>,
        /** How the faction moves, battles, recruits and crafts, each in a few words. */
        val ways: Map<String, String>,
        /** The choice its player faces every turn. */
        val choice: String,
        /** A rule taken from the real animal. */
        val quirk: Titled,
        /** The pieces it crafts with. */
        val craft: String,
        val complexity: Int,
        val reach: Int,
    )

    @Serializable
    private data class File(val plates: List<Plate>, val shared: List<Titled>)

    private val file: File by lazy {
        val text = Plates::class.java.getResourceAsStream("/plates.json")!!.bufferedReader().use { it.readText() }
        Json { ignoreUnknownKeys = true }.decodeFromString(File.serializer(), text)
    }

    val shared: List<Titled> get() = file.shared

    fun of(f: FactionId): Plate = file.plates.first { it.key == f.key }
}
