package com.reef.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.reef.engine.FactionId
import com.reef.engine.Suit

/** The same night-reef palette as the design guide's dark theme. */
object Reef {
    val night = Color(0xFF06171D)
    val surface = Color(0xFF0C232B)
    val raised = Color(0xFF12313B)
    val ink = Color(0xFFD9E9E8)
    val muted = Color(0xFF8EAAB1)
    val line = Color(0xFF1F3F48)
    val accent = Color(0xFFFF7D63)
    val current = Color(0xFF4CC3CF)
    val shore = Color(0xFF3D3825)
    val blood = Color(0xFFE0474C)

    fun suit(s: Suit): Color = when (s) {
        Suit.KELP -> Color(0xFF86BF5E)
        Suit.SPONGE -> Color(0xFFF0B95A)
        Suit.PEARL -> Color(0xFF9FB4EA)
        Suit.MOON -> Color(0xFFC9CFDB)
    }

    fun faction(f: FactionId): Color = when (f) {
        FactionId.SHARKS -> Color(0xFF8DB3C4)
        FactionId.SARDINES -> Color(0xFFC0CCDA)
        FactionId.LIONFISH -> Color(0xFFF08A5D)
        FactionId.STARFISH -> Color(0xFFEC8BBD)
        FactionId.CORAL -> Color(0xFFFF939A)
        FactionId.JELLYFISH -> Color(0xFFC29BE9)
        FactionId.PARROTFISH -> Color(0xFF4FD0BD)
        FactionId.TURTLES -> Color(0xFFCFC85A)
        FactionId.SNAKE -> Color(0xFF6CCBEF)
        FactionId.REMORAS -> Color(0xFFBDB3A2)
        FactionId.CRABS -> Color(0xFFEAA25E)
        FactionId.ANGLERS -> Color(0xFFA2A7F3)
        FactionId.OCTOPUS -> Color(0xFFF07487)
        FactionId.CUTTLEFISH -> Color(0xFFF08AC6)
    }
}

@Composable
fun ReefTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Reef.current,
            onPrimary = Reef.night,
            secondary = Reef.accent,
            background = Reef.night,
            onBackground = Reef.ink,
            surface = Reef.surface,
            onSurface = Reef.ink,
            surfaceVariant = Reef.raised,
            onSurfaceVariant = Reef.muted,
            outline = Reef.line,
        ),
        content = content,
    )
}
