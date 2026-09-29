package com.voicemusic

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    private fun assertParses(expected: Command, vararg inputs: String) {
        for (input in inputs) assertEquals("\"$input\"", expected, CommandParser.parse(input))
    }

    @Test
    fun openApp() = assertParses(Command.OpenApp, "Open YouTube Music", "launch youtube music app", "YouTube Music'i aç")

    @Test
    fun next() = assertParses(Command.Next, "next", "Skip.", "change song", "change the music", "play the next song", "sonraki şarkı", "geç")

    @Test
    fun previous() = assertParses(Command.Previous, "previous", "go back", "play the previous song", "önceki")

    @Test
    fun pause() = assertParses(Command.Pause, "pause", "stop the music", "durdur")

    @Test
    fun resume() = assertParses(Command.Resume, "resume", "play", "continue", "play some music", "devam et")

    @Test
    fun volume() {
        assertParses(Command.VolumeUp, "volume up", "turn it up", "sesi aç")
        assertParses(Command.VolumeDown, "volume down", "quieter please", "sesi kıs")
    }

    @Test
    fun playQuery() {
        assertParses(Command.Play("bohemian rhapsody"), "Play Bohemian Rhapsody", "play bohemian rhapsody on youtube music")
        assertParses(Command.Play("tarkan"), "change to Tarkan", "switch song to tarkan", "Tarkan çal", "çal tarkan")
        assertParses(Command.Play("don't stop me now"), "play don't stop me now")
    }

    @Test
    fun unknown() {
        assertEquals(Command.Unknown("what's the weather"), CommandParser.parse("what's the weather"))
    }

    @Test
    fun parseBestSkipsUnknownAlternatives() {
        assertEquals(Command.Next, CommandParser.parseBest(listOf("necks", "next")))
    }
}
