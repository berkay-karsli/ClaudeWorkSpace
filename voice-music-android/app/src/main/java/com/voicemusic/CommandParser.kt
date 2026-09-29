package com.voicemusic

import java.util.Locale

sealed interface Command {
    data object OpenApp : Command
    data object Next : Command
    data object Previous : Command
    data object Pause : Command
    data object Resume : Command
    data object VolumeUp : Command
    data object VolumeDown : Command
    data class Play(val query: String) : Command
    data class Unknown(val text: String) : Command
}

/**
 * Turns a spoken sentence into a [Command]. Understands English and a handful of Turkish phrases.
 */
object CommandParser {

    /** Parses several recognizer alternatives and returns the first one that makes sense. */
    fun parseBest(candidates: List<String>): Command {
        for (candidate in candidates) {
            val command = parse(candidate)
            if (command !is Command.Unknown) return command
        }
        return Command.Unknown(candidates.firstOrNull().orEmpty())
    }

    fun parse(raw: String): Command {
        val text = normalize(raw)
        if (text.isEmpty()) return Command.Unknown(raw)

        if (OPEN_APP.containsMatchIn(text)) return Command.OpenApp

        CHANGE_TO.find(text)?.let { return playOrResume(it.groupValues[1]) }

        PLAY_EN.find(text)?.let {
            val rest = it.groupValues[1]
            return when {
                PLAY_NEXT.matches(rest) -> Command.Next
                PLAY_PREVIOUS.matches(rest) -> Command.Previous
                else -> playOrResume(rest)
            }
        }

        // Short control phrases. Long sentences are more likely a song title.
        if (text.split(' ').size <= 4) {
            when {
                VOLUME_UP.containsMatchIn(text) -> return Command.VolumeUp
                VOLUME_DOWN.containsMatchIn(text) -> return Command.VolumeDown
                PREVIOUS.containsMatchIn(text) -> return Command.Previous
                NEXT.containsMatchIn(text) -> return Command.Next
                PAUSE.containsMatchIn(text) -> return Command.Pause
                RESUME.matches(text) -> return Command.Resume
            }
        }

        PLAY_TR.find(text)?.let { return playOrResume(it.groupValues[1].ifEmpty { it.groupValues[2] }) }

        return Command.Unknown(raw)
    }

    private fun playOrResume(query: String): Command {
        val cleaned = query
            .replace(ON_YOUTUBE_MUSIC, "")
            .removeSuffix("'i").removeSuffix("'ı").removeSuffix("'u").removeSuffix("'ü")
            .trim()
        return if (cleaned.isEmpty() || cleaned in GENERIC_MUSIC) Command.Resume else Command.Play(cleaned)
    }

    private fun normalize(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("[.,!?;:\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("please ")
            .removeSuffix(" please")
            .trim()

    private val GENERIC_MUSIC = setOf(
        "music", "some music", "the music", "my music", "a song", "something", "youtube music",
        "müzik", "bir şarkı", "şarkı",
    )

    private val ON_YOUTUBE_MUSIC = Regex("\\s+(on|in|from) youtube( music)?$")

    private val OPEN_APP = Regex(
        "^(open|launch|start|show)( the)? (youtube music|youtube|music app|music)( app)?$" +
            "|^(youtube music|youtube|müzik)('?[iyu]| uygulamasını)? aç$"
    )

    private val CHANGE_TO = Regex("^(?:change|switch)(?: the)?(?: song| music| track)? to (.+)$")

    private val PLAY_EN = Regex("^(?:play|put on|listen to|i want to hear)\\s+(.+)$")
    private val PLAY_NEXT = Regex("(the )?next( song| track| one)?")
    private val PLAY_PREVIOUS = Regex("(the )?(previous|last)( song| track| one)?")
    private val PLAY_TR = Regex("^(?:çal\\s+(.+)|(.+?)\\s+(?:çal|oynat|aç))$")

    private val VOLUME_UP = Regex("\\b(volume up|louder|turn it up|turn up|increase (the )?volume)\\b|sesi (aç|artır|yükselt)")
    private val VOLUME_DOWN = Regex("\\b(volume down|quieter|softer|turn it down|turn down|lower (the )?volume|decrease (the )?volume)\\b|sesi (kıs|azalt|düşür)")
    private val PREVIOUS = Regex("\\b(previous|go back|last song|back)\\b|önceki|geri")
    private val NEXT = Regex("\\b(next|skip|change|another|different)\\b|sonraki|geç|atla|değiştir|başka")
    private val PAUSE = Regex("\\b(pause|stop|hold on|quiet|silence)\\b|durdur|duraklat|dur|sus")
    private val RESUME = Regex("^(play|resume|continue|unpause|keep playing|play music|go on|devam( et)?|oynat|çal)$")
}
