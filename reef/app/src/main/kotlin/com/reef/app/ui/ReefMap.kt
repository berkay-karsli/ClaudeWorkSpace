package com.reef.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.reef.engine.Board
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.PieceType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Maps the 800 x 580 design-map coordinates onto the canvas, with the player's zoom and pan. */
private class MapTransform(val width: Float, val height: Float, val zoom: Float, val pan: Offset) {
    val scale = minOf(width / Board.WIDTH, height / Board.HEIGHT) * zoom
    fun screen(x: Float, y: Float) = Offset(width / 2f + pan.x + (x - Board.WIDTH / 2f) * scale, height / 2f + pan.y + (y - Board.HEIGHT / 2f) * scale)
    fun world(o: Offset) = Offset((o.x - width / 2f - pan.x) / scale + Board.WIDTH / 2f, (o.y - height / 2f - pan.y) / scale + Board.HEIGHT / 2f)
}

private const val REEF_RADIUS = 34f

/** Hollow and Arch sit under the Gyre's arrows, so their names go above them, as on the design page. */
private val NAME_ABOVE = setOf(5, 6)

@Composable
fun ReefMap(
    g: GameState,
    version: Int,
    highlights: Set<Int>,
    chosen: List<Int>,
    onReefTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val tappable by rememberUpdatedState(highlights)
    val tap by rememberUpdatedState(onReefTap)

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 3f)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    var p = (centroid - center) - (centroid - center - pan) * (newZoom / zoom) + panChange
                    val limitX = size.width * (newZoom - 1f) / 2f + 60f
                    val limitY = size.height * (newZoom - 1f) / 2f + 60f
                    p = Offset(p.x.coerceIn(-limitX, limitX), p.y.coerceIn(-limitY, limitY))
                    zoom = newZoom
                    pan = p
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val t = MapTransform(size.width.toFloat(), size.height.toFloat(), zoom, pan)
                    val w = t.world(pos)
                    val hit = Board.reefs.minByOrNull { hypot(it.x - w.x, it.y - w.y) } ?: return@detectTapGestures
                    if (hypot(hit.x - w.x, hit.y - w.y) <= REEF_RADIUS + 14f && hit.id in tappable) tap(hit.id)
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val redrawOn = version
        val t = MapTransform(size.width, size.height, zoom, pan)
        drawRect(Reef.night)
        drawBands(t, measurer)
        drawChannels(t)
        for (info in Board.reefs) drawReef(g, info.id, t, measurer, info.id in highlights, info.id in chosen)
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, center: Offset, px: Float, color: Color, bold: Boolean = false, spacing: Float = 0f) {
    val style = TextStyle(color = color, fontSize = px.toSp(), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = (px * spacing).toSp())
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

private fun DrawScope.drawBands(t: MapTransform, measurer: TextMeasurer) {
    val s = t.scale
    drawRoundRect(Reef.shore, topLeft = t.screen(20f, 8f), size = Size(760f * s, 32f * s), cornerRadius = CornerRadius(6f * s))
    label(measurer, "THE SHORE", t.screen(400f, 24f), 12f * s, Reef.ink, spacing = 0.3f)
    drawRoundRect(Color(0xFF02090C), topLeft = t.screen(230f, 536f), size = Size(540f * s, 38f * s), cornerRadius = CornerRadius(6f * s))
    label(measurer, "THE TRENCH", t.screen(500f, 555f), 12f * s, Reef.muted, spacing = 0.3f)
}

private fun DrawScope.drawChannels(t: MapTransform) {
    val s = t.scale
    for ((a, b) in Board.channels) {
        if (Board.isCurrent(a, b)) continue
        val ra = Board.reefs[a]
        val rb = Board.reefs[b]
        drawLine(Reef.muted.copy(alpha = 0.45f), t.screen(ra.x, ra.y), t.screen(rb.x, rb.y), strokeWidth = 3f * s, cap = StrokeCap.Round)
    }
    for ((a, b) in Board.currents) {
        val ra = Board.reefs[a]
        val rb = Board.reefs[b]
        val d = hypot(rb.x - ra.x, rb.y - ra.y)
        val ux = (rb.x - ra.x) / d
        val uy = (rb.y - ra.y) / d
        val start = t.screen(ra.x + ux * 38f, ra.y + uy * 38f)
        val end = t.screen(rb.x - ux * 44f, rb.y - uy * 44f)
        drawLine(Reef.current, start, end, strokeWidth = 3.5f * s, cap = StrokeCap.Round)
        val angle = atan2(uy, ux)
        val tip = t.screen(rb.x - ux * 38f, rb.y - uy * 38f)
        val head = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 12f * s * cos(angle - 0.45f), tip.y - 12f * s * sin(angle - 0.45f))
            lineTo(tip.x - 12f * s * cos(angle + 0.45f), tip.y - 12f * s * sin(angle + 0.45f))
            close()
        }
        drawPath(head, Reef.current)
    }
}

private fun DrawScope.drawReef(g: GameState, reef: Int, t: MapTransform, measurer: TextMeasurer, highlighted: Boolean, chosen: Boolean) {
    val info = Board.reefs[reef]
    val rs = g.reefs[reef]
    val s = t.scale
    val c = t.screen(info.x, info.y)
    val suit = Reef.suit(g.suitOf(reef))

    if (chosen) drawCircle(Reef.current.copy(alpha = 0.35f), radius = (REEF_RADIUS + 12f) * s, center = c)
    if (highlighted) drawCircle(Reef.accent, radius = (REEF_RADIUS + 9f) * s, center = c, style = Stroke(width = 4f * s))
    if (info.gate) {
        drawCircle(Reef.ink.copy(alpha = 0.6f), radius = 42f * s, center = c, style = Stroke(width = 1.5f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * s, 5f * s))))
    }
    drawCircle(Reef.night, radius = REEF_RADIUS * s, center = c)
    drawCircle(suit.copy(alpha = 0.22f), radius = REEF_RADIUS * s, center = c)
    drawCircle(suit, radius = REEF_RADIUS * s, center = c, style = Stroke(width = 2.5f * s))
    label(measurer, g.suitOf(reef).label.uppercase(), t.screen(info.x, info.y - 12f), 9.5f * s, Reef.ink.copy(alpha = 0.75f), spacing = 0.1f)

    // Building slots, filled in the owner's color.
    val buildings = rs.buildings()
    val slotSize = 11f * s
    val gap = 4f * s
    val rowWidth = info.slots * slotSize + (info.slots - 1) * gap
    for (i in 0 until info.slots) {
        val topLeft = Offset(c.x - rowWidth / 2f + i * (slotSize + gap), c.y - 1f * s)
        val piece = buildings.getOrNull(i)
        if (piece?.owner != null) {
            drawRect(Reef.faction(piece.owner!!), topLeft = topLeft, size = Size(slotSize, slotSize))
        } else {
            drawRect(Reef.night, topLeft = topLeft, size = Size(slotSize, slotSize))
            drawRect(Reef.ink.copy(alpha = 0.8f), topLeft = topLeft, size = Size(slotSize, slotSize), style = Stroke(width = 1.5f * s))
        }
    }

    // Who rules here: a small dot in the ruler's color.
    Game.ruler(g, reef)?.let { p ->
        drawCircle(Reef.faction(g.players[p].faction), radius = 4f * s, center = t.screen(info.x, info.y + 22f))
    }

    // Warriors, as numbered badges around the reef.
    val spots = listOf(Offset(-36f, -26f), Offset(36f, -26f), Offset(-36f, 22f), Offset(36f, 22f))
    var spot = 0
    for (pl in g.players) {
        val n = rs.warriors(pl.faction)
        if (n == 0) continue
        val o = spots[spot++ % spots.size]
        val bc = t.screen(info.x + o.x, info.y + o.y)
        drawCircle(Reef.faction(pl.faction), radius = 13f * s, center = bc)
        drawCircle(Reef.night, radius = 13f * s, center = bc, style = Stroke(width = 1.5f * s))
        label(measurer, "$n", bc, 13f * s, Reef.night, bold = true)
    }

    // Tokens. Blood is a red drop above the reef.
    if (rs.pieces.any { it.type == PieceType.BLOOD }) {
        val b = t.screen(info.x, info.y - 44f)
        val drop = Path().apply {
            moveTo(b.x, b.y - 9f * s)
            lineTo(b.x - 6f * s, b.y + 1f * s)
            lineTo(b.x + 6f * s, b.y + 1f * s)
            close()
        }
        drawPath(drop, Reef.blood)
        drawCircle(Reef.blood, radius = 6.2f * s, center = Offset(b.x, b.y + 2f * s))
    }

    val nameY = if (reef in NAME_ABOVE) info.y - 50f else info.y + if (info.gate) 56f else 50f
    label(measurer, info.name, t.screen(info.x, nameY), 13.5f * s, Reef.ink, bold = true)
}
