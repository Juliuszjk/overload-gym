package com.apollo.overload.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.apollo.overload.ui.common.fmt1
import com.apollo.overload.ui.theme.LocalPalette
import com.apollo.overload.ui.theme.Type
import com.apollo.overload.ui.theme.archivo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val tickFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Expected curve sample in kg: middle and the band edges. */
class CurvePoint(val time: Long, val kg: Double, val up: Double, val down: Double)

/** Next load jump: a ring at [toKg] on [time] with a stem down to [fromKg]. */
class JumpMarker(val time: Long, val fromKg: Double, val toKg: Double, val label: String, val date: String)

/** Typical range in kg over time, from [from] on. */
class TypicalBand(val from: Long, val range: (Long) -> Pair<Double, Double>)

/** Extra chart layers; points before [loggedFrom] are estimates, drawn dotted in mute. */
class ChartOverlay(val curve: List<CurvePoint>, val today: Long?, val jump: JumpMarker?, val typical: TypicalBand?, val loggedFrom: Long)

/** Plot geometry shared by drawing and hit-testing (mockup chart(): 340x200 box). */
private class Geo(points: List<WorkPoint>, overlay: ChartOverlay?, val w: Float, val h: Float) {
    val u = w / 340f
    val l = 30 * u; val r = 12 * u; val t = 16 * u; val b = 24 * u
    val t0 = points.first().time
    val curve = overlay?.curve.orEmpty().filter { it.time >= t0 }
    val jump = overlay?.jump?.takeIf { it.time >= t0 }
    private val vals = points.map { it.kg } + curve.flatMap { listOf(it.up, it.down) } + listOfNotNull(jump?.toKg?.plus(1.5))
    val lo = (floor((vals.min() - 2) / 5) * 5).coerceAtLeast(0.0)
    val hi = lo + ceil((vals.max() + 2 - lo) / 20) * 20
    val t1 = maxOf(points.last().time, curve.lastOrNull()?.time ?: 0L, jump?.time ?: 0L)
    fun x(time: Long) = if (t1 == t0) w - r else l + (time - t0).toFloat() / (t1 - t0) * (w - l - r)
    fun y(v: Double) = (t + (hi - v) / (hi - lo) * (h - t - b)).toFloat()
}

/**
 * Working weight per workout on a time axis: 4 round axis steps, area in h2. Tapping or dragging selects the
 * nearest point, which is drawn in h2 with a vertical guide line. [overlay] adds the expected curve (dashed, with a
 * widening band), a Today line, the next-jump marker and the typical range.
 */
@Composable
fun WeightChart(
    points: List<WorkPoint>, selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier, overlay: ChartOverlay? = null,
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val label = Type.display(10.sp, 600).copy(color = p.mute)
    val pts by rememberUpdatedState(points)
    val ov by rememberUpdatedState(overlay)
    val select by rememberUpdatedState(onSelect)
    fun pick(x: Float, width: Int, height: Int) {
        if (pts.isEmpty()) return
        val g = Geo(pts, ov, width.toFloat(), height.toFloat())
        select(pts.minBy { abs(g.x(it.time) - x) }.workoutId)
    }
    Canvas(
        modifier.fillMaxWidth().aspectRatio(340f / 200f)
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width, size.height) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { pick(it.x, size.width, size.height) },
                ) { change, _ -> change.consume(); pick(change.position.x, size.width, size.height) }
            }
    ) {
        if (points.isEmpty()) return@Canvas
        val g = Geo(points, overlay, size.width, size.height)
        val u = g.u; val l = g.l; val r = g.r; val w = g.w; val h = g.h

        val step = (g.hi - g.lo) / 4
        for (k in 0..4) {
            val v = g.lo + k * step
            val yy = g.y(v)
            drawLine(
                p.line, Offset(l, yy), Offset(w - r, yy), strokeWidth = 1 * u,
                pathEffect = if (k > 0) PathEffect.dashPathEffect(floatArrayOf(2 * u, 4 * u)) else null,
            )
            val m = measurer.measure(fmt1(v), label)
            drawText(m, topLeft = Offset(l - 6 * u - m.size.width, yy - m.size.height / 2f))
        }

        val ticks = if (g.t1 == g.t0) listOf(g.t1) else (0..3).map { g.t0 + (g.t1 - g.t0) * it / 3 }
        val zone = ZoneId.systemDefault()
        val tickLabels = ticks.map { tickFmt.format(Instant.ofEpochMilli(it).atZone(zone)) to it }.distinctBy { it.first }
        tickLabels.forEachIndexed { j, (text, time) ->
                val m = measurer.measure(text, label)
                val cx = g.x(time)
                val left = when {
                    tickLabels.size == 1 || j == tickLabels.lastIndex -> cx - m.size.width
                    j == 0 -> cx
                    else -> cx - m.size.width / 2f
                }
                drawText(m, topLeft = Offset(left, h - 6 * u - m.size.height * 0.8f))
            }

        val dash = { a: Float, b: Float -> PathEffect.dashPathEffect(floatArrayOf(a * u, b * u)) }
        fun poly(pts: List<Offset>) = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        fun band(top: List<Offset>, bottom: List<Offset>) = poly(top + bottom.reversed()).apply { close() }
        val plot = Rect(l, g.t, w - r, g.y(g.lo))

        overlay?.typical?.let { tb ->
            val from = maxOf(g.t0, tb.from)
            if (g.t1 > from) {
                val ts = (0..24).map { from + (g.t1 - from) * it / 24 }
                val shape = band(ts.map { Offset(g.x(it), g.y(tb.range(it).second)) }, ts.map { Offset(g.x(it), g.y(tb.range(it).first)) })
                clipRect(plot.left, plot.top, plot.right, plot.bottom) {
                    clipPath(shape) {
                        drawRect(p.mute.copy(alpha = 0.07f), plot.topLeft, plot.size)
                        val step = 6 * u; val span = plot.height
                        var x0 = plot.left - span
                        while (x0 < plot.right) {
                            drawLine(p.mute.copy(alpha = 0.32f), Offset(x0, plot.bottom), Offset(x0 + span, plot.top), strokeWidth = 1.2f * u)
                            x0 += step
                        }
                    }
                }
                val tl = ts[2]
                val (low, high) = tb.range(tl)
                // Only when the band shows in the plot at the label's x, kept inside the plot.
                if (g.y(high) < plot.bottom && g.y(low) > plot.top) {
                    val m = measurer.measure("TYPICAL RANGE", label.copy(fontSize = 9.5.sp, letterSpacing = 0.06.em))
                    val base = (g.y(high) - 4 * u).coerceIn(plot.top + 8 * u, plot.bottom - 4 * u)
                    drawText(m, topLeft = Offset(g.x(tl), base - m.firstBaseline))
                }
            }
        }

        if (g.curve.isNotEmpty()) {
            val c = g.curve
            drawPath(band(c.map { Offset(g.x(it.time), g.y(it.up)) }, c.map { Offset(g.x(it.time), g.y(it.down)) }), p.ink.copy(alpha = 0.09f))
            if (c.size > 1) drawPath(
                poly(c.map { Offset(g.x(it.time), g.y(it.kg)) }), p.ink2,
                style = Stroke(1.4f * u, cap = StrokeCap.Round, pathEffect = dash(4f, 4f)),
            )
            overlay?.today?.takeIf { it in g.t0..g.t1 }?.let { now ->
                val tx = g.x(now)
                drawLine(p.line2, Offset(tx, g.t), Offset(tx, h - g.b), strokeWidth = 1 * u, pathEffect = dash(1f, 3f))
                val m = measurer.measure("Today", label)
                drawText(m, topLeft = Offset(tx + 4 * u, g.t + 2 * u - m.firstBaseline))
            }
        }

        val xy = points.map { Offset(g.x(it.time), g.y(it.kg)) }
        val sel = points.indexOfFirst { it.workoutId == selected }.takeIf { it >= 0 } ?: points.lastIndex
        drawLine(p.h2.copy(alpha = 0.6f), Offset(xy[sel].x, g.t), Offset(xy[sel].x, g.y(g.lo)), strokeWidth = 1.2f * u)
        val firstReal = points.indexOfFirst { it.time >= (overlay?.loggedFrom ?: Long.MIN_VALUE) }.let { if (it < 0) points.size else it }
        if (firstReal > 0) {
            val line = xy.take(minOf(firstReal + 1, xy.size))
            if (line.size > 1) drawPath(poly(line), p.mute, style = Stroke(1.4f * u, cap = StrokeCap.Round, pathEffect = dash(1.5f, 3.5f)))
            (0 until firstReal).forEach { i ->
                if (i == sel) return@forEach
                drawCircle(p.bg, 2.4f * u, xy[i])
                drawCircle(p.mute, 2.4f * u, xy[i], style = Stroke(1.2f * u))
            }
        }
        val real = xy.drop(firstReal)
        if (real.size > 1) {
            val area = poly(real).apply { lineTo(real.last().x, g.y(g.lo)); lineTo(real.first().x, g.y(g.lo)); close() }
            drawPath(area, Brush.verticalGradient(listOf(p.h2.copy(alpha = 0.28f), p.h2.copy(alpha = 0f)), startY = g.t, endY = g.y(g.lo)))
            drawPath(poly(real), p.ink, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        real.forEachIndexed { k, o ->
            if (firstReal + k == sel) return@forEachIndexed
            drawCircle(p.bg, 2.6f * u, o)
            drawCircle(p.ink, 2.6f * u, o, style = Stroke(1.5f * u))
        }

        g.jump?.let { j ->
            val mx = g.x(j.time); val my = g.y(j.toKg)
            drawLine(p.ink2, Offset(mx, my + 6 * u), Offset(mx, g.y(j.fromKg)), strokeWidth = 1.2f * u)
            drawCircle(p.bg, 5 * u, Offset(mx, my))
            drawCircle(p.ink, 5 * u, Offset(mx, my), style = Stroke(1.6f * u))
            drawCircle(p.ink, 1.8f * u, Offset(mx, my))
            val a = measurer.measure(j.label, label.copy(color = p.ink, fontSize = 10.5.sp, fontFamily = archivo(800)))
            val d = measurer.measure(j.date, label)
            val right = mx + 9 * u + maxOf(a.size.width, d.size.width) <= w
            fun lx(m: androidx.compose.ui.text.TextLayoutResult) = if (right) mx + 9 * u else mx - 9 * u - m.size.width
            drawText(a, topLeft = Offset(lx(a), my - 1 * u - a.firstBaseline))
            drawText(d, topLeft = Offset(lx(d), my + 10 * u - d.firstBaseline))
        }

        drawCircle(p.h2.copy(alpha = 0.2f), 10 * u, xy[sel])
        drawCircle(p.h2, 5 * u, xy[sel])
    }
}
