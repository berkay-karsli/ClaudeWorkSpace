package com.reef.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reef.app.ui.art.Art
import com.reef.app.ui.art.Icons
import com.reef.app.ui.art.Portraits
import com.reef.app.ui.art.Suits
import com.reef.app.ui.art.Tokens
import com.reef.engine.AnglersRules
import com.reef.engine.AnglersState
import com.reef.engine.Board
import com.reef.engine.Cards
import com.reef.engine.CrabsState
import com.reef.engine.CuttlefishRules
import com.reef.engine.CuttlefishState
import com.reef.engine.Gallery
import com.reef.engine.GameState
import com.reef.engine.OctopusState
import com.reef.engine.Piece
import com.reef.engine.RemorasState
import com.reef.engine.Suit
import com.reef.engine.TurtlesState

/**
 * What a faction keeps off the map: the Cuttlefish's Gallery, the Octopus's orders and garden,
 * each turtle's food, the Hermit Crabs' Till, the Anglerfish's lures, the Remoras' rides.
 */
@Composable
fun FactionStatus(g: GameState, p: Int, @Suppress("UNUSED_PARAMETER") version: Int) {
    when (val s = g.players[p].fs) {
        is CuttlefishState -> {
            val held = CuttlefishRules.held(g)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (id in s.gallery) {
                    val pattern = Gallery[id]
                    val met = pattern.met(g, held)
                    Column(
                        Modifier.weight(1f).background(if (met) Color(0xFF1F4A3A) else Reef.raised, RoundedCornerShape(8.dp))
                            .border(1.dp, if (met) Color(0xFF8BE07A) else Reef.line, RoundedCornerShape(8.dp)).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pattern.name, color = Reef.ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                            VpCoin(pattern.vp)
                        }
                        Text(pattern.text + if (met) " ✓" else "", color = if (met) Color(0xFF8BE07A) else Reef.muted, fontSize = 10.sp, lineHeight = 12.sp)
                    }
                }
            }
        }
        is OctopusState -> {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                for (arm in 0 until 8) {
                    val order = s.orders[arm]
                    Column(
                        Modifier.alpha(if (s.arms[arm] < 0) 0.4f else 1f).background(Reef.raised, RoundedCornerShape(6.dp)).padding(horizontal = 3.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${arm + 1}", color = Reef.muted, fontSize = 9.sp)
                        if (order != null) ArtImage(orderIcon(Cards[order].suit), 22.dp) else Box(Modifier.width(22.dp)) { Text("–", color = Reef.muted, fontSize = 12.sp) }
                    }
                }
            }
            if (s.garden.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Garden", color = Reef.muted, fontSize = 11.sp)
                    for (item in s.garden) {
                        val image = if (item.kind == "card") Art.suit(item.suit ?: Suit.MOON) else Art.piece(Piece(item.owner, item.type!!, item.suit))
                        ArtImage(image, 20.dp)
                    }
                }
            }
        }
        is TurtlesState -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in s.turtles) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(Portraits.turtle, 22.dp)
                    Text(Board.name(t.reef), color = Reef.muted, fontSize = 10.sp)
                    for (food in t.food.sortedBy { it.ordinal }) ArtImage(Art.suit(food), 16.dp)
                }
            }
        }
        is CrabsState -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Till", color = Reef.muted, fontSize = 11.sp)
            for (c in s.till) ArtImage(Art.suit(Cards[c].suit), 18.dp)
            Spacer(Modifier.width(8.dp))
            ArtImage(Tokens.shell, 20.dp)
            Text("${s.pool} at ${s.price} card${if (s.price == 1) "" else "s"}", color = Reef.muted, fontSize = 11.sp)
        }
        is AnglersState -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val unlocked = AnglersRules.unlocked(s)
            for (i in 0 until 4) ArtImage(Icons.lure, 20.dp, Modifier.alpha(if (i < unlocked) 1f else 0.3f))
            Text(
                "${s.eaten} eaten" + when {
                    s.eaten < 4 -> " · 3rd lure at 4"
                    s.eaten < 8 -> " · 4th lure at 8"
                    else -> ""
                },
                color = Reef.muted, fontSize = 11.sp,
            )
        }
        is RemorasState -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((reef, m) in s.attached) for ((host, n) in m) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Portrait(host, 20.dp, ring = 1.dp)
                    Text(" $n in ${Board.name(reef)}", color = Reef.muted, fontSize = 10.sp)
                }
            }
        }
        else -> {}
    }
}

private fun orderIcon(s: Suit) = when (s) {
    Suit.KELP -> Icons.reach
    Suit.SPONGE -> Icons.grab
    Suit.PEARL -> Icons.steal
    Suit.MOON -> Suits.moon
}
