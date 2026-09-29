package com.woodland.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.woodland.engine.BuildingType
import com.woodland.engine.Faction
import com.woodland.engine.Game
import com.woodland.engine.Suit
import com.woodland.engine.WoodlandMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object Palette {
    const val FOREST = 0xFF2E4A2A.toInt()
    const val FOREST_DARK = 0xFF1C2E1A.toInt()
    const val PARCHMENT = 0xFFF1E6C8.toInt()
    const val PATH = 0xFFCDB88A.toInt()
    const val HIGHLIGHT = 0xFFFFD54F.toInt()
    const val INK = 0xFF2B2118.toInt()
    const val WOOD = 0xFF8D5A2B.toInt()

    fun faction(f: Faction) = when (f) {
        Faction.CATS -> 0xFFE07B24.toInt()
        Faction.BIRDS -> 0xFF2F6FC2.toInt()
        Faction.ALLIANCE -> 0xFF3C9A48.toInt()
    }

    fun suit(s: Suit) = when (s) {
        Suit.FOX -> 0xFFD2483A.toInt()
        Suit.RABBIT -> 0xFFE3BE3C.toInt()
        Suit.MOUSE -> 0xFFB87333.toInt()
        Suit.BIRD -> 0xFF5BA7DA.toInt()
    }
}

class BoardView(context: Context) : View(context) {
    var game: Game? = null
    var highlights: Set<Int> = emptySet()
    var arrows: List<Pair<Int, Int>> = emptyList()
    var selected: Int = -1
    var onClearingTap: ((Int) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val trees = Random(7).let { r -> List(110) { Triple(r.nextFloat(), r.nextFloat(), 0.018f + r.nextFloat() * 0.03f) } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var h = w
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) h = min(h, MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(w, h)
    }

    // Inset the map so badges on edge clearings stay on screen.
    private fun cx(c: Int) = (0.05f + 0.9f * WoodlandMap.clearings[c].x / 100f) * width
    private fun cy(c: Int) = (0.05f + 0.9f * WoodlandMap.clearings[c].y / 100f) * height
    private val radius get() = min(width, height) * 0.08f

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = radius
        canvas.drawColor(Palette.FOREST)
        for ((x, y, s) in trees) {
            fill.color = if (s > 0.035f) Palette.FOREST_DARK else 0xFF3D5E36.toInt()
            canvas.drawCircle(x * w, y * h, s * w, fill)
        }

        stroke.color = Palette.PATH
        stroke.strokeWidth = w * 0.02f
        stroke.strokeCap = Paint.Cap.ROUND
        for ((a, b) in WoodlandMap.paths) canvas.drawLine(cx(a), cy(a), cx(b), cy(b), stroke)

        for ((a, b) in arrows) drawArrow(canvas, a, b, r)

        for (cs in g.board) {
            val c = cs.id
            val x = cx(c)
            val y = cy(c)
            if (c in highlights) {
                stroke.color = Palette.HIGHLIGHT
                stroke.strokeWidth = r * 0.28f
                canvas.drawCircle(x, y, r * 1.12f, stroke)
            }
            if (c == selected) {
                stroke.color = 0xFFFFFFFF.toInt()
                stroke.strokeWidth = r * 0.12f
                canvas.drawCircle(x, y, r * 1.28f, stroke)
            }
            fill.color = Palette.PARCHMENT
            canvas.drawCircle(x, y, r, fill)
            stroke.color = Palette.suit(cs.suit)
            stroke.strokeWidth = r * 0.16f
            canvas.drawCircle(x, y, r * 0.92f, stroke)

            text.color = Palette.INK
            text.textSize = r * 0.5f
            text.isFakeBoldText = true
            canvas.drawText("${cs.suit.symbol}${c + 1}", x, y - r * 0.12f, text)
            text.isFakeBoldText = false

            // Building slots
            val s = r * 0.42f
            val gap = r * 0.08f
            val total = cs.def.slots * s + (cs.def.slots - 1) * gap
            var sx = x - total / 2
            val sy = y + r * 0.08f
            for (i in 0 until cs.def.slots) {
                val rect = RectF(sx, sy, sx + s, sy + s)
                val b = cs.buildings.getOrNull(i)
                if (b == null) {
                    stroke.color = 0xFF9C8B6A.toInt()
                    stroke.strokeWidth = r * 0.05f
                    canvas.drawRoundRect(rect, s * 0.15f, s * 0.15f, stroke)
                } else {
                    fill.color = Palette.faction(b.owner)
                    canvas.drawRoundRect(rect, s * 0.15f, s * 0.15f, fill)
                    text.color = 0xFFFFFFFF.toInt()
                    text.textSize = s * 0.7f
                    text.isFakeBoldText = true
                    canvas.drawText(buildingLetter(b), rect.centerX(), rect.centerY() + s * 0.25f, text)
                    text.isFakeBoldText = false
                }
                sx += s + gap
            }

            // Warriors and tokens sit around the rim.
            for (f in g.order) {
                val n = cs.warriors(f)
                if (n > 0) badge(canvas, c, warriorAngle(f), Palette.faction(f), "$n", r)
            }
            if (cs.wood > 0) badge(canvas, c, 135.0, Palette.WOOD, if (cs.wood > 1) "▮${cs.wood}" else "▮", r, square = true)
            if (cs.keep) badge(canvas, c, -45.0, 0xFF6D4C41.toInt(), "♜", r, square = true)
            if (cs.sympathy) badge(canvas, c, -135.0, Palette.faction(Faction.ALLIANCE), "✦", r)
        }
    }

    private fun warriorAngle(f: Faction) = when (f) {
        Faction.CATS -> 180.0
        Faction.BIRDS -> 0.0
        Faction.ALLIANCE -> 90.0
    }

    private fun buildingLetter(b: BuildingType) = when (b) {
        BuildingType.SAWMILL -> "S"
        BuildingType.WORKSHOP -> "W"
        BuildingType.RECRUITER -> "R"
        BuildingType.ROOST -> "N"
        BuildingType.BASE -> "B"
    }

    private fun badge(canvas: Canvas, c: Int, angle: Double, color: Int, label: String, r: Float, square: Boolean = false) {
        val a = Math.toRadians(angle)
        val bx = cx(c) + (cos(a) * r * 0.98).toFloat()
        val by = cy(c) + (sin(a) * r * 0.98).toFloat()
        val br = r * 0.34f
        fill.color = color
        if (square) canvas.drawRoundRect(RectF(bx - br, by - br, bx + br, by + br), br * 0.3f, br * 0.3f, fill)
        else canvas.drawCircle(bx, by, br, fill)
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = r * 0.05f
        if (square) canvas.drawRoundRect(RectF(bx - br, by - br, bx + br, by + br), br * 0.3f, br * 0.3f, stroke)
        else canvas.drawCircle(bx, by, br, stroke)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = if (label.length > 2) br * 0.85f else br * 1.1f
        text.isFakeBoldText = true
        canvas.drawText(label, bx, by + text.textSize * 0.36f, text)
        text.isFakeBoldText = false
    }

    private fun drawArrow(canvas: Canvas, a: Int, b: Int, r: Float) {
        val x1 = cx(a)
        val y1 = cy(a)
        val x2 = cx(b)
        val y2 = cy(b)
        val ang = atan2(y2 - y1, x2 - x1)
        val ex = x2 - cos(ang) * r * 1.15f
        val ey = y2 - sin(ang) * r * 1.15f
        stroke.color = 0xCCFFD54F.toInt()
        stroke.strokeWidth = r * 0.12f
        canvas.drawLine(x1 + cos(ang) * r, y1 + sin(ang) * r, ex, ey, stroke)
        val head = Path()
        val hs = r * 0.4f
        head.moveTo(ex, ey)
        head.lineTo(ex - cos(ang - 0.5f) * hs, ey - sin(ang - 0.5f) * hs)
        head.lineTo(ex - cos(ang + 0.5f) * hs, ey - sin(ang + 0.5f) * hs)
        head.close()
        fill.color = 0xCCFFD54F.toInt()
        canvas.drawPath(head, fill)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = WoodlandMap.clearings.indices.minByOrNull { hypot(cx(it) - event.x, cy(it) - event.y) }
            if (hit != null && hypot(cx(hit) - event.x, cy(hit) - event.y) < radius * 1.4f) {
                onClearingTap?.invoke(hit)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
