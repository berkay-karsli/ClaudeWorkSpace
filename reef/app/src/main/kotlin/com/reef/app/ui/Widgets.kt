package com.reef.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.app.ui.art.Art
import com.reef.app.ui.art.Icons
import com.reef.engine.CardKind
import com.reef.engine.Cards
import com.reef.engine.FactionId
import com.reef.engine.Suit

/** A picture from the art library. */
@Composable
fun ArtImage(image: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    Image(image, contentDescription = null, modifier = modifier.size(size))
}

/** A faction's portrait in a round medallion ringed in its color. */
@Composable
fun Portrait(f: FactionId, size: Dp, modifier: Modifier = Modifier, ring: Dp = 2.dp) {
    val color = Reef.faction(f)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2C6E86), Color(0xFF0E3042))))
            .border(ring, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ArtImage(Art.portrait(f), size * 0.86f)
    }
}

/** A victory-point badge: a gold coin with the amount. */
@Composable
fun VpBadge(text: String, height: Dp = 20.dp) {
    Box(
        Modifier.height(height).background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFD49A1C))), RoundedCornerShape(50))
            .border(1.dp, Color(0xFF7A5A08), RoundedCornerShape(50)).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (text.contains("VP")) text else "$text VP", color = Color(0xFF3A2A04), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A small card back or face, colored by suit, for diagrams. */
@Composable
fun CardGlyph(suit: Suit?, count: String? = null, size: Dp = 26.dp) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(size * 0.74f).height(size).background(Brush.verticalGradient(listOf(Color(0xFFFDF7EA), Color(0xFFD9CCB0))), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF10222B), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (suit != null) ArtImage(Art.suit(suit), size * 0.56f) else Text("?", color = Color(0xFF10222B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (count != null) CountTag(count, Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 4.dp))
    }
}

@Composable
private fun CountTag(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Reef.night, RoundedCornerShape(50)).border(1.dp, Reef.line, RoundedCornerShape(50)).padding(horizontal = 4.dp)) {
        Text(text, color = Reef.ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** An action diagram: pictures joined by arrows, showing what you pay and what you get. */
@Composable
fun Diagram(glyphs: List<Glyph>, size: Dp = 26.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (g in glyphs) {
            when (g) {
                is Glyph.Pic -> Box(contentAlignment = Alignment.Center) {
                    ArtImage(g.image, size)
                    if (g.count != null) CountTag(g.count, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
                }
                is Glyph.Word -> Text(g.text, color = Reef.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                is Glyph.Vp -> VpBadge(g.text)
                is Glyph.Card -> CardGlyph(g.suit, g.count, size)
            }
            if (g is Glyph.Pic && g.count != null) Spacer(Modifier.width(4.dp))
        }
    }
}

/**
 * An action card: the action's picture, its name, what it does in one sentence and its
 * diagram. [ways] says how many different ways it can be taken right now.
 */
@Composable
fun ActionCard(f: FactionId, kind: String, ways: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val info = Guide.action(f, kind)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .background(if (selected) Color(0xFF1B4652) else Reef.raised, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Reef.current else Reef.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color(0xFF0E3042), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                ArtImage(Art.action(kind), 32.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, color = Reef.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ways > 1) Text("$ways ways", color = Reef.muted, fontSize = 11.sp)
            }
        }
        if (info.diagram.size > 1) Diagram(info.diagram)
        if (info.blurb.isNotEmpty()) Text(info.blurb, color = Reef.muted, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

/** A card from the Tide deck, as it looks in a hand. */
@Composable
fun PlayingCard(id: Int, highlighted: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val card = Cards[id]
    val shape = RoundedCornerShape(10.dp)
    val tint = Reef.suit(card.suit)
    Column(
        modifier
            .width(112.dp)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.28f), Reef.raised)), shape)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) Reef.accent else Reef.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtImage(Art.suit(card.suit), 22.dp)
            Spacer(Modifier.width(4.dp))
            Text(card.name, color = Reef.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            when (card.kind) {
                CardKind.AMBUSH -> {
                    ArtImage(Icons.ambush, 22.dp)
                    Text("Ambush", color = Reef.ink, fontSize = 11.sp)
                }
                CardKind.DOMINANCE -> {
                    ArtImage(Icons.dominance, 22.dp)
                    Text("Dominance", color = Reef.ink, fontSize = 11.sp)
                }
                CardKind.GEAR -> {
                    for (s in card.cost) ArtImage(Art.suit(s), 16.dp)
                    Spacer(Modifier.width(2.dp))
                    VpBadge("${card.vp}", 18.dp)
                }
            }
        }
        Text(
            when (card.kind) {
                CardKind.GEAR -> if (card.effect.text.isEmpty()) "Gear. Its suit pays for anything." else card.effect.text
                else -> card.describe().substringAfter(": ")
            },
            color = Reef.muted, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Chip(text: String, primary: Boolean = false, image: ImageVector? = null, faction: FactionId? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .background(if (primary) Reef.raised else Color.Transparent, RoundedCornerShape(50))
            .border(1.dp, if (primary) Reef.current else Reef.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = if (image != null || faction != null) 6.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (faction != null) {
            Portrait(faction, 24.dp, ring = 1.dp)
            Spacer(Modifier.width(6.dp))
        } else if (image != null) {
            ArtImage(image, 24.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = Reef.ink, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun Line() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Reef.line))
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(), color = Reef.muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = modifier.padding(top = 4.dp))
}
