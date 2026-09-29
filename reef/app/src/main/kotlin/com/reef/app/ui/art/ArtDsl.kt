package com.reef.app.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The small drawing language the generated art (ArtLibrary.kt) is written in. */
internal class ArtScope(private val b: ImageVector.Builder) {
    /** A path: [f] fill, [s] stroke of width [w], [a] opacity for both. */
    fun p(d: String, f: Brush? = null, s: Brush? = null, w: Float = 0f, a: Float = 1f, cap: StrokeCap = StrokeCap.Round, eo: Boolean = false) {
        b.addPath(
            pathData = addPathNodes(d),
            pathFillType = if (eo) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = f,
            fillAlpha = a,
            stroke = s,
            strokeAlpha = a,
            strokeLineWidth = w,
            strokeLineCap = cap,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    /**
     * A group, transformed the Compose way: translate(tx + px, ty + py) rotate scale translate(-px, -py).
     * [clip] is a path in the group's own coordinates that its children are drawn inside.
     */
    fun g(tx: Float = 0f, ty: Float = 0f, rot: Float = 0f, px: Float = 0f, py: Float = 0f, sx: Float = 1f, sy: Float = sx, clip: String? = null, block: ArtScope.() -> Unit) {
        b.addGroup(
            rotate = rot, pivotX = px, pivotY = py, scaleX = sx, scaleY = sy, translationX = tx, translationY = ty,
            clipPathData = if (clip != null) addPathNodes(clip) else androidx.compose.ui.graphics.vector.EmptyPath,
        )
        block()
        b.clearGroup()
    }
}

internal fun art(name: String, w: Float, h: Float, block: ArtScope.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = w.dp, defaultHeight = h.dp, viewportWidth = w, viewportHeight = h)
        .apply { ArtScope(this).block() }
        .build()

internal fun c(argb: Long): Brush = SolidColor(Color(argb))

internal fun lin(x1: Float, y1: Float, x2: Float, y2: Float, vararg stops: Pair<Float, Long>): Brush =
    Brush.linearGradient(*stops.map { it.first to Color(it.second) }.toTypedArray(), start = Offset(x1, y1), end = Offset(x2, y2))

internal fun rad(cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Long>): Brush =
    Brush.radialGradient(*stops.map { it.first to Color(it.second) }.toTypedArray(), center = Offset(cx, cy), radius = r)
