package fi.kaihola.syncop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import fi.kaihola.syncop.model.deviationColor
import fi.kaihola.syncop.ui.theme.Fog
import fi.kaihola.syncop.ui.theme.Paper
import fi.kaihola.syncop.ui.theme.RecordRed
import kotlin.math.max

/** Fraction of the width at which the playhead line sits. */
const val PLAYHEAD_FRACTION = 0.72f

/**
 * Scrolling timeline: amplitude envelope with click ticks above and coloured attack markers on
 * the attacks themselves. The playhead line is fixed; content moves under it. [secondsVisible]
 * is the zoom level; dragging changes [playhead], pinching changes zoom.
 */
@Composable
fun Timeline(
    session: Session,
    revision: Int,
    playhead: Long,
    secondsVisible: Float,
    interactive: Boolean,
    onScroll: (Long) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(interactive, secondsVisible) {
                if (!interactive) return@pointerInput
                detectTransformGestures { _, pan, zoom, _ ->
                    val framesPerPx = secondsVisible * SAMPLE_RATE / size.width
                    if (pan.x != 0f) onScroll((playhead - pan.x * framesPerPx).toLong())
                    if (zoom != 1f) onZoom((secondsVisible / zoom).coerceIn(1f, 30f))
                }
            },
    ) {
        revision.hashCode() // read so the canvas redraws when content changes
        drawTimeline(session, playhead, secondsVisible)
    }
}

private fun DrawScope.drawTimeline(session: Session, playhead: Long, secondsVisible: Float) {
    val w = size.width
    val h = size.height
    val framesPerPx = secondsVisible * SAMPLE_RATE / w
    val playheadX = w * PLAYHEAD_FRACTION
    val firstFrame = playhead - (playheadX * framesPerPx).toLong()
    fun xOf(frame: Long) = (frame - firstFrame) / framesPerPx

    val clickLaneY = h * 0.09f
    val waveTop = h * 0.16f
    val waveMid = h * 0.60f
    val waveHalf = (waveMid - waveTop)

    // Second gridlines
    val secStep = SAMPLE_RATE.toLong()
    var sec = (firstFrame / secStep) * secStep
    while (sec < firstFrame + (w * framesPerPx).toLong() + secStep) {
        if (sec >= 0) {
            val x = xOf(sec)
            drawLine(Fog.copy(alpha = 0.12f), Offset(x, waveTop - 8f), Offset(x, h), strokeWidth = 1f)
        }
        sec += secStep
    }

    // Recorded region background
    if (session.length > 0) {
        val x0 = xOf(0).coerceAtLeast(0f)
        val x1 = xOf(session.length).coerceAtMost(w)
        if (x1 > x0) drawRect(Color.White.copy(alpha = 0.025f), Offset(x0, waveTop - 8f), androidx.compose.ui.geometry.Size(x1 - x0, h - waveTop + 8f))
    }

    // Amplitude envelope, one column per pixel, mirrored around the midline
    val path = Path()
    val cols = w.toInt()
    val peaks = FloatArray(cols)
    synchronized(session) {
        for (c in 0 until cols) {
            val f0 = firstFrame + (c * framesPerPx).toLong()
            val f1 = f0 + max(1L, framesPerPx.toLong())
            if (f1 <= 0 || f0 >= session.length) continue
            peaks[c] = session.peak(f0, f1)
        }
    }
    path.moveTo(0f, waveMid)
    for (c in 0 until cols) path.lineTo(c.toFloat(), waveMid - waveHalf * peaks[c])
    path.lineTo(cols.toFloat(), waveMid)
    for (c in cols - 1 downTo 0) path.lineTo(c.toFloat(), waveMid + waveHalf * peaks[c])
    path.close()
    drawPath(path, Paper.copy(alpha = 0.75f))

    // Click ticks: small downward triangles in the lane above the waveform
    val clicks = synchronized(session) { session.clicks.toList() }
    val tickHalf = 7f
    for (c in clicks) {
        val x = xOf(c)
        if (x < -tickHalf || x > w + tickHalf) continue
        val tri = Path().apply {
            moveTo(x - tickHalf, clickLaneY - tickHalf)
            lineTo(x + tickHalf, clickLaneY - tickHalf)
            lineTo(x, clickLaneY + tickHalf)
            close()
        }
        drawPath(tri, Fog)
        drawLine(Fog.copy(alpha = 0.25f), Offset(x, clickLaneY + tickHalf), Offset(x, waveMid + waveHalf), strokeWidth = 1f)
    }

    // Attack markers on top of the waveform peak at the attack
    val onsets = synchronized(session) { session.onsets.toList() }
    for (o in onsets) {
        val x = xOf(o.frame)
        if (x < -12f || x > w + 12f) continue
        val col = x.toInt().coerceIn(0, cols - 1)
        var pk = 0f
        for (i in (col - 3).coerceAtLeast(0)..(col + 6).coerceAtMost(cols - 1)) pk = max(pk, peaks[i])
        val y = waveMid - waveHalf * pk - 14f
        val color = Color(deviationColor(o.deviationMs))
        drawCircle(color, radius = 9f, center = Offset(x, y))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 9f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    }

    // Playhead
    drawLine(RecordRed.copy(alpha = 0.9f), Offset(playheadX, 0f), Offset(playheadX, h), strokeWidth = 2f)
}
