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

    /**
     * Picks the command from the recognizer's alternatives (best first). Anything that isn't a
     * control phrase is treated as a song to play, so saying just the song name is enough.
     */
    fun parseBest(candidates: List<String>): Command {
        for (candidate in candidates) {
            val command = parse(candidate)
            if (command !is Command.Unknown) return command
        }
        val best = candidates.firstOrNull { normalize(it).isNotEmpty() } ?: return Command.Unknown("")
        return playOrResume(normalize(best))
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

        // Control phrases must match the whole sentence, so song titles like "Hold On",
        // "Back in Black" or "Another Love" aren't mistaken for commands.
        when {
            VOLUME_UP.matches(text) -> return Command.VolumeUp
            VOLUME_DOWN.matches(text) -> return Command.VolumeDown
            PREVIOUS.matches(text) -> return Command.Previous
            NEXT.matches(text) -> return Command.Next
            PAUSE.matches(text) -> return Command.Pause
            RESUME.matches(text) -> return Command.Resume
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

    private val VOLUME_UP = Regex(
        "volume up|turn (it |the volume )?up|louder|increase (the )?volume|sesi (aç|artır|yükselt)"
    )
    private val VOLUME_DOWN = Regex(
        "volume down|turn (it |the volume )?down|quieter|softer|(lower|decrease) (the )?volume|sesi (kıs|azalt|düşür)"
    )
    private val PREVIOUS = Regex(
        "(the )?(previous|last) (song|track|one)|previous|go back|back|önceki( şarkı| parça)?|geri( dön| al)?"
    )
    private val NEXT = Regex(
        "(next|skip|change)( the)?( song| track| music| one)?|(another|different|new) (song|track|one)|" +
            "play something else|something else|(sonraki|başka|diğer)( şarkı| parça)?|" +
            "(geç|atla|değiştir)|(şarkıyı|parçayı) (geç|atla|değiştir)|(sonraki|başka|diğer) (şarkıya|parçaya) geç"
    )
    private val PAUSE = Regex(
        "(pause|stop)( the)?( music| song| playing| it)?|durdur|duraklat|dur|(müziği|şarkıyı) (durdur|duraklat|kapat)"
    )
    private val RESUME = Regex(
        "play|play music|(resume|continue|unpause)( the)?( music| song| playing)?|keep playing|go on|devam( et)?|oynat|çal"
    )
}
