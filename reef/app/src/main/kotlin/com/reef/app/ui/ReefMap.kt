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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.reef.app.ui.art.Art
import com.reef.app.ui.art.Icons
import com.reef.app.ui.art.Portraits
import com.reef.app.ui.art.Scenery
import com.reef.app.ui.art.Suits
import com.reef.app.ui.art.Tokens
import com.reef.engine.AnglersState
import com.reef.engine.Board
import com.reef.engine.CrabsState
import com.reef.engine.FactionId
import com.reef.engine.Game
import com.reef.engine.GameState
import com.reef.engine.MarkerType
import com.reef.engine.OctopusState
import com.reef.engine.ParrotfishState
import com.reef.engine.PieceType
import com.reef.engine.RemorasState
import com.reef.engine.SnakeState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Something to point out on the map while a choice is being made: an arrow from [from] to [to]
 * (or just a ring when [from] is null), with the action's picture and an optional [label].
 */
data class MapMark(val from: Int?, val to: Int, val icon: ImageVector?, val strong: Boolean, val label: String? = null)

/** Maps the 800 x 580 design-map coordinates onto the canvas, with the player's zoom and pan. */
private class MapTransform(val width: Float, val height: Float, val zoom: Float, val pan: Offset) {
    val scale = minOf(width / Board.WIDTH, height / Board.HEIGHT) * zoom
    fun screen(x: Float, y: Float) = Offset(width / 2f + pan.x + (x - Board.WIDTH / 2f) * scale, height / 2f + pan.y + (y - Board.HEIGHT / 2f) * scale)
    fun world(o: Offset) = Offset((o.x - width / 2f - pan.x) / scale + Board.WIDTH / 2f, (o.y - height / 2f - pan.y) / scale + Board.HEIGHT / 2f)
}

private const val R = 38f

/** Hollow and Arch sit under the Gyre's arrows, so their names go above them, as on the design page. */
private val NAME_ABOVE = setOf(5, 6)

/** Landmarks drawn behind their reefs; every other reef shows the scenery of its suit. */
private val LANDMARKS = mapOf(0 to Scenery.rock, 3 to Scenery.wreck, 8 to Scenery.driftwood, 6 to Scenery.arch, 10 to Scenery.hole)

/** Every picture the map may draw, painted once and reused. */
private class Painters(private val map: Map<ImageVector, VectorPainter>) {
    operator fun get(i: ImageVector): VectorPainter = map.getValue(i)
}

@Composable
private fun rememberPainters(): Painters {
    val all = remember { Portraits.all + Tokens.all + Suits.all + Scenery.all + Icons.all }
    val painters = all.map { rememberVectorPainter(it) }
    return remember(painters) { Painters(all.zip(painters).toMap()) }
}

@Composable
fun ReefMap(
    g: GameState,
    version: Int,
    highlights: Set<Int>,
    chosen: List<Int>,
    marks: List<MapMark>,
    onReefTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val art = rememberPainters()
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
                    if (hypot(hit.x - w.x, hit.y - w.y) <= R + 18f && hit.id in tappable) tap(hit.id)
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val redrawOn = version
        val t = MapTransform(size.width, size.height, zoom, pan)
        drawWater(t, measurer, art, g)
        drawChannels(t, g, art)
        drawSnake(t, g)
        drawOctopusReach(t, g)
        for (info in Board.reefs) drawReef(g, info.id, t, measurer, art, info.id in highlights, info.id in chosen)
        drawMarks(t, marks, art, measurer)
    }
}

// ---- Drawing helpers --------------------------------------------------------------------------

private fun DrawScope.image(p: VectorPainter, center: Offset, w: Float, h: Float = w, alpha: Float = 1f, degrees: Float = 0f) {
    translate(center.x - w / 2f, center.y - h / 2f) {
        rotate(degrees, pivot = Offset(w / 2f, h / 2f)) {
            with(p) { draw(Size(w, h), alpha = alpha) }
        }
    }
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, center: Offset, px: Float, color: Color, bold: Boolean = false, spacing: Float = 0f) {
    val style = TextStyle(color = color, fontSize = px.toSp(), fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = (px * spacing).toSp())
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

/** A small round tag with a number, used for counts on pieces. */
private fun DrawScope.countTag(measurer: TextMeasurer, text: String, center: Offset, s: Float, fill: Color = Reef.night, ink: Color = Reef.ink) {
    val r = (if (text.length > 1) 9f else 8f) * s
    drawCircle(fill, radius = r, center = center)
    drawCircle(ink.copy(alpha = 0.7f), radius = r, center = center, style = Stroke(width = 1.2f * s))
    label(measurer, text, center, 10.5f * s, ink, bold = true)
}

private fun wavy(t: MapTransform, y: Float, amp: Float, period: Float, down: Boolean): Path {
    val p = Path()
    val start = t.screen(-40f, if (down) -60f else Board.HEIGHT + 60f)
    p.moveTo(start.x, start.y)
    var x = -40f
    var up = false
    val first = t.screen(x, y)
    p.lineTo(first.x, first.y)
    while (x < Board.WIDTH + 40f) {
        val mid = t.screen(x + period / 2f, y + if (up) -amp else amp)
        val end = t.screen(x + period, y)
        p.quadraticTo(mid.x, mid.y, end.x, end.y)
        x += period
        up = !up
    }
    val close = t.screen(Board.WIDTH + 40f, if (down) -60f else Board.HEIGHT + 60f)
    p.lineTo(close.x, close.y)
    p.close()
    return p
}

// ---- Layers -----------------------------------------------------------------------------------

private fun DrawScope.drawWater(t: MapTransform, measurer: TextMeasurer, art: Painters, g: GameState) {
    val s = t.scale
    val top = t.screen(0f, 0f).y
    val bottom = t.screen(0f, Board.HEIGHT).y
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1F7C92), Color(0xFF12526A), Color(0xFF0A3347), Color(0xFF051B26)), startY = top, endY = bottom))
    // The water is printed too: rows of carved wave lines.
    var row = 0
    var y = 70f
    while (y < 520f) {
        val p = Path()
        var x = if (row % 2 == 0) -20f else 10f
        val start = t.screen(x, y)
        p.moveTo(start.x, start.y)
        while (x < Board.WIDTH + 20f) {
            val mid = t.screen(x + 15f, y - 4f)
            val end = t.screen(x + 30f, y)
            p.quadraticTo(mid.x, mid.y, end.x, end.y)
            x += 60f
            val gap = t.screen(x, y)
            p.moveTo(gap.x, gap.y)
        }
        drawPath(p, Color(0x1FF3E7CC), style = Stroke(width = 1.6f * s, cap = StrokeCap.Round))
        y += 26f
        row++
    }
    // The shore along the top, the Trench along the bottom.
    drawPath(wavy(t, 40f, 5f, 60f, down = true), Brush.verticalGradient(listOf(Color(0xFFF3DFB0), Color(0xFFCFAE6A)), startY = top, endY = t.screen(0f, 46f).y))
    drawPath(wavy(t, 40f, 5f, 60f, down = true), Color(0xFF7A5A26), style = Stroke(width = 1.5f * s))
    label(measurer, "THE SHORE", t.screen(400f, 18f), 11f * s, Color(0xFF6A4A1A), bold = true, spacing = 0.35f)
    drawPath(wavy(t, 534f, 6f, 70f, down = false), Brush.verticalGradient(listOf(Color(0xFF0A1E30), Color(0xFF01060C)), startY = t.screen(0f, 528f).y, endY = bottom))
    label(measurer, "THE TRENCH", t.screen(400f, 560f), 11f * s, Reef.muted, bold = true, spacing = 0.35f)
    g.state<AnglersState>(FactionId.ANGLERS)?.let { a ->
        val c = t.screen(250f, 558f)
        drawCircle(Color(0x4DF6D64A), radius = 18f * s, center = c)
        image(art[Portraits.angler], c, 30f * s)
        countTag(measurer, "${a.trench}", Offset(c.x + 14f * s, c.y + 8f * s), s)
    }
}

private fun DrawScope.drawChannels(t: MapTransform, g: GameState, art: Painters) {
    val s = t.scale
    for ((a, b) in Board.channels) {
        if (Board.isCurrent(a, b)) continue
        val ra = Board.reefs[a]
        val rb = Board.reefs[b]
        drawLine(Color(0x2EBFE8F0), t.screen(ra.x, ra.y), t.screen(rb.x, rb.y), strokeWidth = 12f * s, cap = StrokeCap.Round)
        drawLine(Color(0x66BFE8F0), t.screen(ra.x, ra.y), t.screen(rb.x, rb.y), strokeWidth = 2f * s, cap = StrokeCap.Round)
    }
    for ((a, b) in Board.currents) arrow(t, a, b, Reef.current, 4f, glow = true)
    for (m in g.markers) {
        when (m.type) {
            MarkerType.CURRENT -> arrow(t, m.from!!, m.other(m.from!!), Reef.faction(FactionId.JELLYFISH), 4f, glow = true)
            MarkerType.SANDBAR -> {
                val ra = Board.reefs[m.a]
                val rb = Board.reefs[m.b]
                val mid = t.screen((ra.x + rb.x) / 2f, (ra.y + rb.y) / 2f)
                val deg = Math.toDegrees(atan2((rb.y - ra.y).toDouble(), (rb.x - ra.x).toDouble())).toFloat() + 90f
                image(art[Tokens.sandbar], mid, 52f * s, 20f * s, degrees = deg)
            }
        }
    }
}

/** A current's arrow along the channel from [a] to [b]. */
private fun DrawScope.arrow(t: MapTransform, a: Int, b: Int, color: Color, width: Float, glow: Boolean) {
    val s = t.scale
    val ra = Board.reefs[a]
    val rb = Board.reefs[b]
    val d = hypot(rb.x - ra.x, rb.y - ra.y)
    val ux = (rb.x - ra.x) / d
    val uy = (rb.y - ra.y) / d
    val start = t.screen(ra.x + ux * (R + 4f), ra.y + uy * (R + 4f))
    val end = t.screen(rb.x - ux * (R + 12f), rb.y - uy * (R + 12f))
    if (glow) drawLine(color.copy(alpha = 0.25f), start, end, strokeWidth = (width + 6f) * s, cap = StrokeCap.Round)
    drawLine(color, start, end, strokeWidth = width * s, cap = StrokeCap.Round)
    val angle = atan2(uy, ux)
    val tip = t.screen(rb.x - ux * (R + 3f), rb.y - uy * (R + 3f))
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - 14f * s * cos(angle - 0.5f), tip.y - 14f * s * sin(angle - 0.5f))
        lineTo(tip.x - 14f * s * cos(angle + 0.5f), tip.y - 14f * s * sin(angle + 0.5f))
        close()
    }
    drawPath(head, color)
}

/** The Sea Snake's body is one line: draw it across every channel it spans. */
private fun DrawScope.drawSnake(t: MapTransform, g: GameState) {
    val body = g.state<SnakeState>(FactionId.SNAKE)?.body ?: return
    val s = t.scale
    for (i in 1 until body.size) {
        val a = body[i - 1]
        val b = body[i]
        if (a == b) continue
        val ra = Board.reefs[a]
        val rb = Board.reefs[b]
        val p1 = t.screen(ra.x, ra.y)
        val p2 = t.screen(rb.x, rb.y)
        drawLine(Color(0xFF0B2233), p1, p2, strokeWidth = 13f * s, cap = StrokeCap.Round)
        drawLine(Color(0xFF8FD3EE), p1, p2, strokeWidth = 9f * s, cap = StrokeCap.Round)
        drawLine(Color(0xFF163A57), p1, p2, strokeWidth = 9f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * s, 7f * s)))
    }
}

/** Octopus arms reaching out from the Mantle's reef. */
private fun DrawScope.drawOctopusReach(t: MapTransform, g: GameState) {
    val o = g.state<OctopusState>(FactionId.OCTOPUS) ?: return
    if (o.mantle < 0) return
    val s = t.scale
    val m = Board.reefs[o.mantle]
    for (reef in o.arms.filter { it >= 0 && it != o.mantle }.distinct()) {
        val r = Board.reefs[reef]
        val mid = Offset((m.x + r.x) / 2f + (r.y - m.y) * 0.18f, (m.y + r.y) / 2f - (r.x - m.x) * 0.18f)
        val p = Path().apply {
            val a = t.screen(m.x, m.y); val c = t.screen(mid.x, mid.y); val b = t.screen(r.x, r.y)
            moveTo(a.x, a.y); quadraticTo(c.x, c.y, b.x, b.y)
        }
        drawPath(p, Color(0xFF4A0E18), style = Stroke(width = 9f * s, cap = StrokeCap.Round))
        drawPath(p, Color(0xFFD65A6C), style = Stroke(width = 6f * s, cap = StrokeCap.Round))
        drawPath(p, Color(0xFFFBD2DA), style = Stroke(width = 2f * s, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f * s))))
    }
}

private fun DrawScope.drawReef(g: GameState, reef: Int, t: MapTransform, measurer: TextMeasurer, art: Painters, highlighted: Boolean, chosen: Boolean) {
    val info = Board.reefs[reef]
    val rs = g.reefs[reef]
    val s = t.scale
    val c = t.screen(info.x, info.y)
    val suit = g.suitOf(reef)
    val suitColor = Reef.suit(suit)
    val ruler = Game.ruler(g, reef)

    if (chosen) drawCircle(Reef.current.copy(alpha = 0.35f), radius = (R + 16f) * s, center = c)
    drawCircle(suitColor.copy(alpha = 0.16f), radius = (R + 10f) * s, center = c)
    if (info.gate) {
        drawCircle(Color(0xCCF6F0E2), radius = (R + 7f) * s, center = c, style = Stroke(width = 2f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 5f * s))))
    }
    // Each reef is a print on paper: its scenery printed faintly under the pieces.
    drawCircle(Brush.radialGradient(listOf(Reef.paperLight, Reef.paperDark), center = Offset(c.x, c.y - 10f * s), radius = R * s * 1.2f), radius = R * s, center = c)
    image(art[LANDMARKS[reef] ?: suitScenery(suit)], Offset(c.x, c.y + 4f * s), 70f * s, alpha = 0.32f)
    // The rim shows who rules here: the ruler's color, or the suit's when nobody does.
    val rim = ruler?.let { Reef.faction(g.players[it].faction) } ?: suitColor
    val rimWidth = if (ruler != null) 5f else 3f
    drawCircle(Reef.printInk, radius = R * s, center = c, style = Stroke(width = (rimWidth + 3f) * s))
    drawCircle(rim, radius = R * s, center = c, style = Stroke(width = rimWidth * s))
    if (highlighted) drawCircle(Reef.accent, radius = (R + 5f) * s, center = c, style = Stroke(width = 3.5f * s))

    image(art[Art.suit(suit)], t.screen(info.x, info.y - 24f), 20f * s)
    if (g.state<ParrotfishState>(FactionId.PARROTFISH)?.islands?.contains(reef) == true) image(art[Tokens.island], t.screen(info.x + 24f, info.y - 26f), 34f * s)

    // Building slots, holding building art (or Rubble).
    val filling = rs.pieces.filter { it.type.fillsSlot }
    val slot = 17f * s
    val gap = 3f * s
    val rowWidth = info.slots * slot + (info.slots - 1) * gap
    for (i in 0 until info.slots) {
        val sc = Offset(c.x - rowWidth / 2f + i * (slot + gap) + slot / 2f, c.y - 3f * s)
        val piece = filling.getOrNull(i)
        drawRoundRect(Reef.paperLight, topLeft = Offset(sc.x - slot / 2f, sc.y - slot / 2f), size = Size(slot, slot), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s))
        if (piece == null) {
            drawRoundRect(Reef.printInk, topLeft = Offset(sc.x - slot / 2f, sc.y - slot / 2f), size = Size(slot, slot), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s), style = Stroke(width = 1.2f * s))
        } else {
            val owner = piece.owner
            if (owner != null && piece.type.building) {
                drawRoundRect(Reef.faction(owner), topLeft = Offset(sc.x - slot / 2f, sc.y - slot / 2f), size = Size(slot, slot), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s), style = Stroke(width = 1.6f * s))
            }
            image(art[Art.piece(piece)], sc, slot * 1.05f)
        }
    }

    // Tokens that don't fill slots, in a row: Blood, eggs, lures, pigments.
    val tokens = rs.pieces.filter { !it.type.fillsSlot }.groupBy { Triple(it.type, it.variant, it.suit) }
    var tx = c.x - (tokens.size - 1) * 13f * s
    for ((_, group) in tokens) {
        val tc = Offset(tx, c.y + 19f * s)
        val big = group.first().type == PieceType.LURE
        image(art[Art.piece(group.first())], tc, (if (big) 30f else 24f) * s)
        if (group.size > 1) countTag(measurer, "${group.size}", Offset(tc.x + 10f * s, tc.y + 7f * s), s * 0.8f)
        tx += 26f * s
    }

    // Warriors: a medallion per faction, with riders and shells beside it.
    val remoras = g.state<RemorasState>(FactionId.REMORAS)
    val crabs = g.state<CrabsState>(FactionId.CRABS)
    val octo = g.state<OctopusState>(FactionId.OCTOPUS)
    val spots = listOf(Offset(-38f, -28f), Offset(38f, -28f), Offset(-38f, 26f), Offset(38f, 26f), Offset(0f, 46f))
    var spot = 0
    for (pl in g.players) {
        val f = pl.faction
        val n = rs.warriors(f)
        val riders = remoras?.attached?.get(reef)?.get(f) ?: 0
        if (n == 0) continue
        val o = spots[spot++ % spots.size]
        val mc = t.screen(info.x + o.x, info.y + o.y)
        drawCircle(Brush.radialGradient(listOf(Reef.paperLight, Reef.paperDark), center = mc, radius = 15f * s), radius = 15f * s, center = mc)
        drawCircle(Reef.printInk, radius = 15.5f * s, center = mc, style = Stroke(width = 3.6f * s))
        drawCircle(Reef.faction(f), radius = 15f * s, center = mc, style = Stroke(width = 2.2f * s))
        image(art[Art.portrait(f)], mc, 26f * s)
        val mantle = f == FactionId.OCTOPUS && octo?.mantle == reef
        // The count is the warriors here; for the octopus, its arms, with the Mantle shown as a head.
        countTag(measurer, "${if (mantle) n - 1 else n}", Offset(mc.x + 12f * s, mc.y + 11f * s), s, fill = Reef.faction(f), ink = Reef.night)
        if (mantle) image(art[Icons.mantle], Offset(mc.x + 13f * s, mc.y - 12f * s), 18f * s)
        if (riders > 0) {
            val rc = Offset(mc.x - 15f * s, mc.y + 11f * s)
            image(art[Portraits.remora], rc, 18f * s)
            countTag(measurer, "$riders", Offset(rc.x - 6f * s, rc.y + 6f * s), s * 0.8f)
        }
        val shells = crabs?.shells?.get(reef)?.get(f) ?: 0
        if (shells > 0) {
            val sc = Offset(mc.x - 13f * s, mc.y - 12f * s)
            image(art[Tokens.shell], sc, 16f * s)
            if (shells > 1) countTag(measurer, "$shells", Offset(sc.x - 6f * s, sc.y - 4f * s), s * 0.8f)
        }
    }
    // Remoras riding a host whose own medallion shows them: nothing more to draw.

    val nameY = if (reef in NAME_ABOVE) info.y - R - 22f else info.y + R + if (info.gate) 20f else 16f
    label(measurer, info.name + if (info.gate) " · gate" else "", t.screen(info.x, nameY), 12.5f * s, Reef.ink, bold = true)
}

private fun suitScenery(suit: com.reef.engine.Suit): ImageVector = when (suit) {
    com.reef.engine.Suit.KELP -> Scenery.kelp
    com.reef.engine.Suit.SPONGE -> Scenery.sponge
    else -> Scenery.pearl
}

/** Arrows and rings that preview the choice being made. */
private fun DrawScope.drawMarks(t: MapTransform, marks: List<MapMark>, art: Painters, measurer: TextMeasurer) {
    val s = t.scale
    for (m in marks.sortedBy { it.strong }) {
        val color = if (m.strong) Reef.accent else Color(0xCCFFE2A8)
        val to = Board.reefs[m.to]
        val tc = t.screen(to.x, to.y)
        if (m.from != null && m.from != m.to) {
            val from = Board.reefs[m.from]
            val d = hypot(to.x - from.x, to.y - from.y)
            val ux = (to.x - from.x) / d
            val uy = (to.y - from.y) / d
            val start = Offset(from.x + ux * (R - 6f), from.y + uy * (R - 6f))
            val end = Offset(to.x - ux * (R + 6f), to.y - uy * (R + 6f))
            val bend = Offset((start.x + end.x) / 2f - uy * d * 0.12f, (start.y + end.y) / 2f + ux * d * 0.12f)
            val a = t.screen(start.x, start.y); val b = t.screen(bend.x, bend.y); val e = t.screen(end.x, end.y)
            val path = Path().apply { moveTo(a.x, a.y); quadraticTo(b.x, b.y, e.x, e.y) }
            val w = if (m.strong) 6f else 3.5f
            drawPath(path, Color(0x99000000), style = Stroke(width = (w + 3f) * s, cap = StrokeCap.Round))
            drawPath(path, color, style = Stroke(width = w * s, cap = StrokeCap.Round))
            val angle = atan2(e.y - b.y, e.x - b.x)
            val head = Path().apply {
                val tip = Offset(e.x + cos(angle) * 6f * s, e.y + sin(angle) * 6f * s)
                moveTo(tip.x, tip.y)
                lineTo(tip.x - 16f * s * cos(angle - 0.5f), tip.y - 16f * s * sin(angle - 0.5f))
                lineTo(tip.x - 16f * s * cos(angle + 0.5f), tip.y - 16f * s * sin(angle + 0.5f))
                close()
            }
            drawPath(head, color)
            if (m.label != null) {
                val mid = t.screen(bend.x, bend.y)
                countTag(measurer, m.label, Offset((mid.x + (a.x + e.x) / 2f) / 2f, (mid.y + (a.y + e.y) / 2f) / 2f), s * 1.1f, fill = color, ink = Reef.night)
            }
        } else if (m.strong) {
            drawCircle(color, radius = (R + 8f) * s, center = tc, style = Stroke(width = 4f * s))
        }
        if (m.icon != null) {
            val ic = t.screen(to.x + 30f, to.y - 34f)
            drawCircle(Color(0xE60E3042), radius = 15f * s, center = ic)
            drawCircle(color, radius = 15f * s, center = ic, style = Stroke(width = 2f * s))
            image(art[m.icon], ic, 24f * s)
        }
    }
}
