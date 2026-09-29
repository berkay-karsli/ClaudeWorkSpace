package com.woodland.app

import android.content.Context
import android.graphics.Bitmap
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
    const val FOREST = 0xFF34482A.toInt()
    const val FOREST_DARK = 0xFF1F2B19.toInt()
    const val PARCHMENT = 0xFFF3E7C9.toInt()
    const val PATH = 0xFFD4B27A.toInt()
    const val PATH_EDGE = 0xFF8C6A3C.toInt()
    const val HIGHLIGHT = 0xFFFFD54F.toInt()
    const val INK = 0xFF2B2118.toInt()
    const val WOOD = 0xFF9A6334.toInt()

    fun faction(f: Faction) = when (f) {
        Faction.CATS -> 0xFFE0782A.toInt()
        Faction.BIRDS -> 0xFF2E67B8.toInt()
        Faction.ALLIANCE -> 0xFF3D9446.toInt()
        Faction.VAGABOND -> 0xFF6E6A66.toInt()
    }

    fun suit(s: Suit) = when (s) {
        Suit.FOX -> 0xFFD1462F.toInt()
        Suit.RABBIT -> 0xFFE4B83A.toInt()
        Suit.MOUSE -> 0xFFE08A3C.toInt()
        Suit.BIRD -> 0xFF5FA6D6.toInt()
    }

    val CANOPY = intArrayOf(0xFFC8622B.toInt(), 0xFFD99A3B.toInt(), 0xFF9C3F28.toInt(), 0xFF7C8B3A.toInt(), 0xFFB5482E.toInt(), 0xFF5F7A33.toInt())
}

class BoardView(context: Context) : View(context) {
    var game: Game? = null
    /** Clearings (and forests, as Game.FOREST_BASE + id) that can be tapped for the current choice. */
    var highlights: Set<Int> = emptySet()
    var arrows: List<Pair<Int, Int>> = emptyList()
    var selected: Int = -1
    var onClearingTap: ((Int) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bgTrees = Random(11).let { r -> List(140) { Triple(r.nextFloat(), r.nextFloat(), r.nextInt(6)) } }
    private val forestTrees = Random(5).let { r -> List(900) { Triple(r.nextFloat(), r.nextFloat(), r.nextInt(6)) } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        var h = w
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) h = min(h, MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(w, h)
    }

    // Inset the map so tokens on edge clearings stay on screen.
    private fun px(x: Float) = (0.05f + 0.9f * x / 100f) * width
    private fun py(y: Float) = (0.05f + 0.9f * y / 100f) * height
    private fun cx(c: Int) = px(WoodlandMap.clearings[c].x)
    private fun cy(c: Int) = py(WoodlandMap.clearings[c].y)
    private fun fx(f: Int) = px(WoodlandMap.forests[f].x)
    private fun fy(f: Int) = py(WoodlandMap.forests[f].y)
    private val radius get() = min(width, height) * 0.08f

    private fun forestPath(f: Int): Path {
        val p = Path()
        val cl = WoodlandMap.forests[f].clearings
        cl.forEachIndexed { i, c -> if (i == 0) p.moveTo(cx(c), cy(c)) else p.lineTo(cx(c), cy(c)) }
        p.close()
        return p
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = radius

        val bg = landscape ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            drawLandscape(Canvas(it))
            landscape = it
        }
        canvas.drawBitmap(bg, 0f, 0f, null)

        for (f in WoodlandMap.forests.indices) {
            val path = forestPath(f)
            if (Game.FOREST_BASE + f in highlights || Game.FOREST_BASE + f == selected) {
                stroke.color = Palette.HIGHLIGHT
                stroke.strokeWidth = r * 0.3f
                canvas.save()
                canvas.clipPath(path)
                fill.color = 0x66FFD54F
                canvas.drawPath(path, fill)
                canvas.drawPath(path, stroke)
                canvas.restore()
                badge(canvas, fx(f), fy(f), r * 0.32f, 0xCC1F2B19.toInt(), WoodlandMap.forestName(f).drop(2), r)
            }
        }

        stroke.strokeCap = Paint.Cap.ROUND
        for ((a, b) in arrows) if (b < Game.FOREST_BASE) drawArrow(canvas, a, b, r)

        for (cs in g.board) {
            val c = cs.id
            val x = cx(c)
            val y = cy(c)
            if (c in highlights) {
                stroke.color = Palette.HIGHLIGHT
                stroke.strokeWidth = r * 0.26f
                canvas.drawCircle(x, y, r * 1.13f, stroke)
            }
            if (c == selected) {
                stroke.color = 0xFFFFFFFF.toInt()
                stroke.strokeWidth = r * 0.12f
                canvas.drawCircle(x, y, r * 1.3f, stroke)
            }
            fill.color = 0x55000000
            canvas.drawCircle(x + r * 0.06f, y + r * 0.08f, r, fill)
            fill.color = Palette.PARCHMENT
            canvas.drawCircle(x, y, r, fill)
            stroke.color = Palette.suit(cs.suit)
            stroke.strokeWidth = r * 0.14f
            canvas.drawCircle(x, y, r * 0.92f, stroke)

            suitIcon(canvas, cs.suit, x - r * 0.2f, y - r * 0.42f, r * 0.26f)
            text.color = Palette.INK
            text.textSize = r * 0.38f
            text.isFakeBoldText = true
            canvas.drawText("${c + 1}", x + r * 0.28f, y - r * 0.28f, text)
            text.isFakeBoldText = false

            // Building slots (a ruin fills one until it is explored).
            val s = r * 0.42f
            val gap = r * 0.07f
            val total = cs.def.slots * s + (cs.def.slots - 1) * gap
            var sx = x - total / 2
            val sy = y + r * 0.02f
            var i = 0
            for (slot in 0 until cs.def.slots) {
                val rect = RectF(sx, sy, sx + s, sy + s)
                val b = cs.buildings.getOrNull(i)
                when {
                    b != null -> {
                        building(canvas, rect, b); i++
                    }
                    cs.hasRuin && slot == cs.def.slots - 1 -> ruin(canvas, rect)
                    else -> {
                        stroke.color = 0xFFA8946C.toInt()
                        stroke.strokeWidth = r * 0.05f
                        canvas.drawRoundRect(rect, s * 0.15f, s * 0.15f, stroke)
                    }
                }
                sx += s + gap
            }

            // Warriors around the rim.
            for (f in g.order) {
                val n = cs.warriors(f)
                if (n > 0) {
                    val a = Math.toRadians(warriorAngle(f))
                    val mx = x + (cos(a) * r * 1.0).toFloat()
                    val my = y + (sin(a) * r * 1.0).toFloat()
                    meeple(canvas, mx, my, r * 0.62f, f)
                    countBadge(canvas, mx + r * 0.2f, my + r * 0.2f, "$n", r)
                }
            }
            if (cs.wood > 0) woodToken(canvas, c, cs.wood, r)
            if (cs.keep) keepToken(canvas, x + r * 0.72f, y - r * 0.72f, r * 0.3f)
            if (cs.sympathy) sympathyToken(canvas, x - r * 0.72f, y - r * 0.72f, r * 0.27f)
            if (g.has(Faction.VAGABOND) && g.vbClearing == c) meeple(canvas, x, y - r * 1.0f, r * 0.7f, Faction.VAGABOND)
        }
        if (g.has(Faction.VAGABOND) && g.vbForest >= 0) meeple(canvas, fx(g.vbForest), fy(g.vbForest), r * 0.8f, Faction.VAGABOND)
    }

    private var landscape: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        landscape = null
    }

    /** The static woodland: floor, forests and paths, drawn once per size. */
    private fun drawLandscape(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(Palette.FOREST)
        for ((x, y, k) in bgTrees) tree(canvas, x * w, y * h, w * 0.028f, Palette.CANOPY[k], dim = true)
        for (f in WoodlandMap.forests.indices) {
            val path = forestPath(f)
            canvas.save()
            canvas.clipPath(path)
            fill.color = 0xFF4B5E2E.toInt()
            canvas.drawPath(path, fill)
            for ((x, y, k) in forestTrees) tree(canvas, x * w, y * h, w * 0.03f, Palette.CANOPY[k], dim = false)
            canvas.restore()
        }
        stroke.strokeCap = Paint.Cap.ROUND
        for ((a, b) in WoodlandMap.paths) {
            stroke.color = Palette.PATH_EDGE
            stroke.strokeWidth = w * 0.03f
            canvas.drawLine(cx(a), cy(a), cx(b), cy(b), stroke)
            stroke.color = Palette.PATH
            stroke.strokeWidth = w * 0.022f
            canvas.drawLine(cx(a), cy(a), cx(b), cy(b), stroke)
        }
    }

    private fun warriorAngle(f: Faction) = when (f) {
        Faction.CATS -> 180.0
        Faction.BIRDS -> 0.0
        Faction.ALLIANCE -> 90.0
        Faction.VAGABOND -> -90.0
    }

    private fun tree(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, dim: Boolean) {
        fill.color = 0x40000000
        canvas.drawCircle(x + size * 0.15f, y + size * 0.2f, size, fill)
        fill.color = if (dim) (color and 0x00FFFFFF) or 0x70000000 else color
        canvas.drawCircle(x, y, size, fill)
        fill.color = 0x30FFFFFF
        canvas.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.45f, fill)
    }

    /** Simple drawn animal heads for the clearing suits. */
    private fun suitIcon(canvas: Canvas, suit: Suit, x: Float, y: Float, s: Float) {
        fill.color = Palette.suit(suit)
        val p = Path()
        when (suit) {
            Suit.FOX -> {
                p.moveTo(x - s, y - s * 0.9f); p.lineTo(x - s * 0.35f, y - s * 0.35f); p.lineTo(x + s * 0.35f, y - s * 0.35f)
                p.lineTo(x + s, y - s * 0.9f); p.lineTo(x + s * 0.8f, y + s * 0.1f); p.lineTo(x, y + s); p.lineTo(x - s * 0.8f, y + s * 0.1f); p.close()
                canvas.drawPath(p, fill)
                fill.color = Palette.PARCHMENT
                canvas.drawCircle(x - s * 0.35f, y - s * 0.05f, s * 0.13f, fill)
                canvas.drawCircle(x + s * 0.35f, y - s * 0.05f, s * 0.13f, fill)
            }
            Suit.RABBIT -> {
                canvas.drawOval(RectF(x - s * 0.55f, y - s * 1.3f, x - s * 0.1f, y), fill)
                canvas.drawOval(RectF(x + s * 0.1f, y - s * 1.3f, x + s * 0.55f, y), fill)
                canvas.drawCircle(x, y + s * 0.2f, s * 0.72f, fill)
                fill.color = Palette.PARCHMENT
                canvas.drawCircle(x - s * 0.25f, y + s * 0.1f, s * 0.12f, fill)
                canvas.drawCircle(x + s * 0.25f, y + s * 0.1f, s * 0.12f, fill)
            }
            Suit.MOUSE -> {
                canvas.drawCircle(x - s * 0.62f, y - s * 0.5f, s * 0.48f, fill)
                canvas.drawCircle(x + s * 0.62f, y - s * 0.5f, s * 0.48f, fill)
                p.moveTo(x - s * 0.7f, y - s * 0.2f); p.lineTo(x + s * 0.7f, y - s * 0.2f); p.lineTo(x, y + s); p.close()
                canvas.drawPath(p, fill)
                canvas.drawCircle(x, y - s * 0.05f, s * 0.6f, fill)
                fill.color = Palette.PARCHMENT
                canvas.drawCircle(x - s * 0.25f, y, s * 0.12f, fill)
                canvas.drawCircle(x + s * 0.25f, y, s * 0.12f, fill)
            }
            Suit.BIRD -> {
                canvas.drawCircle(x, y, s * 0.8f, fill)
                p.moveTo(x + s * 0.6f, y - s * 0.2f); p.lineTo(x + s * 1.3f, y + s * 0.05f); p.lineTo(x + s * 0.6f, y + s * 0.3f); p.close()
                canvas.drawPath(p, fill)
            }
        }
    }

    /** A warrior figure shaped after its faction. */
    private fun meeple(canvas: Canvas, x: Float, y: Float, size: Float, f: Faction) {
        val color = Palette.faction(f)
        val body = Path().apply {
            moveTo(x - size * 0.42f, y + size * 0.5f)
            quadTo(x - size * 0.45f, y - size * 0.02f, x - size * 0.2f, y - size * 0.05f)
            lineTo(x + size * 0.2f, y - size * 0.05f)
            quadTo(x + size * 0.45f, y - size * 0.02f, x + size * 0.42f, y + size * 0.5f)
            close()
        }
        val hx = x
        val hy = y - size * 0.25f
        val hr = size * 0.27f
        val ears = Path()
        when (f) {
            Faction.CATS -> {
                ears.moveTo(hx - hr, hy - hr * 0.1f); ears.lineTo(hx - hr * 0.8f, hy - hr * 1.5f); ears.lineTo(hx - hr * 0.1f, hy - hr * 0.8f); ears.close()
                ears.moveTo(hx + hr, hy - hr * 0.1f); ears.lineTo(hx + hr * 0.8f, hy - hr * 1.5f); ears.lineTo(hx + hr * 0.1f, hy - hr * 0.8f); ears.close()
            }
            Faction.BIRDS -> {
                ears.moveTo(hx + hr * 0.8f, hy - hr * 0.2f); ears.lineTo(hx + hr * 1.7f, hy + hr * 0.15f); ears.lineTo(hx + hr * 0.8f, hy + hr * 0.45f); ears.close()
                ears.moveTo(hx - hr * 0.2f, hy - hr * 0.9f); ears.lineTo(hx - hr * 0.6f, hy - hr * 1.6f); ears.lineTo(hx + hr * 0.3f, hy - hr * 0.95f); ears.close()
            }
            Faction.ALLIANCE -> {
                ears.addCircle(hx - hr * 0.85f, hy - hr * 0.8f, hr * 0.5f, Path.Direction.CW)
                ears.addCircle(hx + hr * 0.85f, hy - hr * 0.8f, hr * 0.5f, Path.Direction.CW)
            }
            Faction.VAGABOND -> {
                ears.moveTo(hx - hr, hy); ears.lineTo(hx - hr * 0.7f, hy - hr * 1.4f); ears.lineTo(hx - hr * 0.1f, hy - hr * 0.9f); ears.close()
                ears.moveTo(hx + hr, hy); ears.lineTo(hx + hr * 0.7f, hy - hr * 1.4f); ears.lineTo(hx + hr * 0.1f, hy - hr * 0.9f); ears.close()
            }
        }
        stroke.color = 0xFF1B1410.toInt()
        stroke.strokeWidth = size * 0.08f
        fill.color = color
        for (path in listOf(body, ears)) {
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        }
        canvas.drawCircle(hx, hy, hr, fill)
        canvas.drawCircle(hx, hy, hr, stroke)
        if (f == Faction.VAGABOND) {
            fill.color = 0xFF2A2522.toInt()
            canvas.drawRoundRect(RectF(hx - hr * 0.95f, hy - hr * 0.3f, hx + hr * 0.95f, hy + hr * 0.2f), hr * 0.2f, hr * 0.2f, fill)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(hx - hr * 0.4f, hy - hr * 0.05f, hr * 0.13f, fill)
            canvas.drawCircle(hx + hr * 0.4f, hy - hr * 0.05f, hr * 0.13f, fill)
        } else {
            fill.color = 0xFF1B1410.toInt()
            canvas.drawCircle(hx - hr * 0.35f, hy - hr * 0.05f, hr * 0.12f, fill)
            canvas.drawCircle(hx + hr * 0.35f, hy - hr * 0.05f, hr * 0.12f, fill)
        }
    }

    private fun countBadge(canvas: Canvas, x: Float, y: Float, label: String, r: Float) {
        val br = r * 0.2f
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, br, fill)
        stroke.color = 0xFF1B1410.toInt()
        stroke.strokeWidth = r * 0.04f
        canvas.drawCircle(x, y, br, stroke)
        text.color = Palette.INK
        text.textSize = br * 1.4f
        text.isFakeBoldText = true
        canvas.drawText(label, x, y + text.textSize * 0.36f, text)
        text.isFakeBoldText = false
    }

    private fun badge(canvas: Canvas, x: Float, y: Float, br: Float, color: Int, label: String, r: Float) {
        fill.color = color
        canvas.drawCircle(x, y, br, fill)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = br * 1.1f
        text.isFakeBoldText = true
        canvas.drawText(label, x, y + text.textSize * 0.36f, text)
        text.isFakeBoldText = false
    }

    private fun building(canvas: Canvas, rect: RectF, b: BuildingType) {
        val s = rect.width()
        fill.color = Palette.faction(b.owner)
        canvas.drawRoundRect(rect, s * 0.15f, s * 0.15f, fill)
        stroke.color = 0xFF1B1410.toInt()
        stroke.strokeWidth = s * 0.07f
        canvas.drawRoundRect(rect, s * 0.15f, s * 0.15f, stroke)
        val x = rect.centerX()
        val y = rect.centerY()
        fill.color = 0xFFFFFFFF.toInt()
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.09f
        val p = Path()
        when (b) {
            BuildingType.SAWMILL -> {
                // Saw blade
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    p.moveTo(x + (cos(a) * s * 0.36).toFloat(), y + (sin(a) * s * 0.36).toFloat())
                    p.lineTo(x + (cos(a + 0.35) * s * 0.24).toFloat(), y + (sin(a + 0.35) * s * 0.24).toFloat())
                    p.lineTo(x + (cos(a - 0.35) * s * 0.24).toFloat(), y + (sin(a - 0.35) * s * 0.24).toFloat())
                    p.close()
                }
                canvas.drawPath(p, fill)
                canvas.drawCircle(x, y, s * 0.24f, fill)
                fill.color = Palette.faction(b.owner)
                canvas.drawCircle(x, y, s * 0.08f, fill)
            }
            BuildingType.WORKSHOP -> {
                // Hammer
                canvas.save()
                canvas.rotate(-35f, x, y)
                canvas.drawRect(x - s * 0.05f, y - s * 0.12f, x + s * 0.05f, y + s * 0.34f, fill)
                canvas.drawRoundRect(RectF(x - s * 0.26f, y - s * 0.3f, x + s * 0.26f, y - s * 0.1f), s * 0.04f, s * 0.04f, fill)
                canvas.restore()
            }
            BuildingType.RECRUITER -> {
                canvas.drawCircle(x, y - s * 0.14f, s * 0.12f, fill)
                p.moveTo(x - s * 0.22f, y + s * 0.3f); p.lineTo(x - s * 0.12f, y); p.lineTo(x + s * 0.12f, y); p.lineTo(x + s * 0.22f, y + s * 0.3f); p.close()
                canvas.drawPath(p, fill)
                canvas.drawLine(x + s * 0.28f, y - s * 0.1f, x + s * 0.28f, y + s * 0.14f, stroke)
                canvas.drawLine(x + s * 0.16f, y + s * 0.02f, x + s * 0.4f, y + s * 0.02f, stroke)
            }
            BuildingType.ROOST -> {
                // A tree-top roost
                p.moveTo(x - s * 0.32f, y); p.lineTo(x, y - s * 0.3f); p.lineTo(x + s * 0.32f, y); p.close()
                canvas.drawPath(p, fill)
                canvas.drawRect(x - s * 0.22f, y, x + s * 0.22f, y + s * 0.28f, fill)
                fill.color = Palette.faction(b.owner)
                canvas.drawCircle(x, y + s * 0.12f, s * 0.08f, fill)
            }
            BuildingType.BASE -> {
                // Banner
                canvas.drawLine(x - s * 0.2f, y - s * 0.32f, x - s * 0.2f, y + s * 0.32f, stroke)
                p.moveTo(x - s * 0.2f, y - s * 0.3f); p.lineTo(x + s * 0.3f, y - s * 0.15f); p.lineTo(x - s * 0.2f, y + s * 0.02f); p.close()
                canvas.drawPath(p, fill)
            }
        }
    }

    private fun ruin(canvas: Canvas, rect: RectF) {
        val s = rect.width()
        fill.color = 0xFF8E8A80.toInt()
        canvas.drawRoundRect(rect, s * 0.12f, s * 0.12f, fill)
        fill.color = 0xFF5C5850.toInt()
        val x = rect.centerX()
        val y = rect.centerY()
        canvas.drawRect(x - s * 0.3f, y - s * 0.05f, x - s * 0.15f, y + s * 0.32f, fill)
        canvas.drawRect(x + s * 0.15f, y - s * 0.2f, x + s * 0.3f, y + s * 0.32f, fill)
        canvas.drawRect(x - s * 0.3f, y - s * 0.12f, x + s * 0.05f, y - s * 0.02f, fill)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = s * 0.34f
        canvas.drawText("?", x, y + s * 0.12f, text)
    }

    private fun woodToken(canvas: Canvas, c: Int, n: Int, r: Float) {
        val a = Math.toRadians(135.0)
        val x = cx(c) + (cos(a) * r * 1.0).toFloat()
        val y = cy(c) + (sin(a) * r * 1.0).toFloat()
        val s = r * 0.27f
        fill.color = Palette.WOOD
        canvas.drawCircle(x, y, s, fill)
        stroke.color = 0xFF5E3A1B.toInt()
        stroke.strokeWidth = s * 0.14f
        canvas.drawCircle(x, y, s, stroke)
        canvas.drawCircle(x, y, s * 0.55f, stroke)
        if (n > 1) countBadge(canvas, x + s * 0.8f, y + s * 0.8f, "$n", r)
    }

    private fun keepToken(canvas: Canvas, x: Float, y: Float, s: Float) {
        fill.color = 0xFF7A4F2F.toInt()
        canvas.drawCircle(x, y, s * 1.15f, fill)
        fill.color = 0xFFF3E7C9.toInt()
        canvas.drawRect(x - s * 0.55f, y - s * 0.2f, x + s * 0.55f, y + s * 0.6f, fill)
        for (k in -1..1) canvas.drawRect(x + k * s * 0.4f - s * 0.14f, y - s * 0.55f, x + k * s * 0.4f + s * 0.14f, y - s * 0.2f, fill)
    }

    private fun sympathyToken(canvas: Canvas, x: Float, y: Float, s: Float) {
        fill.color = Palette.faction(Faction.ALLIANCE)
        canvas.drawCircle(x, y, s * 1.15f, fill)
        stroke.color = 0xFFFFFFFF.toInt()
        stroke.strokeWidth = s * 0.14f
        canvas.drawCircle(x, y, s * 1.15f, stroke)
        fill.color = 0xFFFFFFFF.toInt()
        val p = Path()
        p.moveTo(x, y - s * 0.7f)
        p.quadTo(x + s * 0.7f, y, x, y + s * 0.7f)
        p.quadTo(x - s * 0.7f, y, x, y - s * 0.7f)
        canvas.drawPath(p, fill)
    }

    private fun drawArrow(canvas: Canvas, a: Int, b: Int, r: Float) {
        val x1 = cx(a)
        val y1 = cy(a)
        val x2 = cx(b)
        val y2 = cy(b)
        val ang = atan2(y2 - y1, x2 - x1)
        val ex = x2 - cos(ang) * r * 1.15f
        val ey = y2 - sin(ang) * r * 1.15f
        stroke.color = 0xDDFFD54F.toInt()
        stroke.strokeWidth = r * 0.12f
        canvas.drawLine(x1 + cos(ang) * r, y1 + sin(ang) * r, ex, ey, stroke)
        val head = Path()
        val hs = r * 0.4f
        head.moveTo(ex, ey)
        head.lineTo(ex - cos(ang - 0.5f) * hs, ey - sin(ang - 0.5f) * hs)
        head.lineTo(ex - cos(ang + 0.5f) * hs, ey - sin(ang + 0.5f) * hs)
        head.close()
        fill.color = 0xDDFFD54F.toInt()
        canvas.drawPath(head, fill)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val c = WoodlandMap.clearings.indices.minByOrNull { hypot(cx(it) - event.x, cy(it) - event.y) }
            val f = WoodlandMap.forests.indices.minByOrNull { hypot(fx(it) - event.x, fy(it) - event.y) }
            val dc = if (c != null) hypot(cx(c) - event.x, cy(c) - event.y) else Float.MAX_VALUE
            val df = if (f != null) hypot(fx(f) - event.x, fy(f) - event.y) else Float.MAX_VALUE
            when {
                c != null && dc < radius * 1.4f -> onClearingTap?.invoke(c)
                f != null && df < radius * 1.2f -> onClearingTap?.invoke(Game.FOREST_BASE + f)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
