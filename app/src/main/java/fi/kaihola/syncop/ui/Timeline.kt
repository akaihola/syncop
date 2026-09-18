package fi.kaihola.syncop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.abs
import kotlin.math.max

/** Fraction of the width at which the playhead line sits. */
const val PLAYHEAD_FRACTION = 0.72f
private const val MIN_SECONDS_VISIBLE = 1f
private const val MAX_SECONDS_VISIBLE = 30f

fun zoomSeconds(secondsVisible: Float, zoom: Float): Float =
    (secondsVisible / zoom).coerceIn(MIN_SECONDS_VISIBLE, MAX_SECONDS_VISIBLE)

/** New scroll position after panning [panPx] pixels from [position], at [framesPerPx] zoom. */
fun panFrame(position: Long, panPx: Float, framesPerPx: Float): Long =
    position - (panPx * framesPerPx).toLong()

/** Peak around an attack in a fixed 10 ms window. */
fun markerPeak(session: Session, frame: Long): Float {
    return synchronized(session) {
        session.peak(frame - SAMPLE_RATE / 1000 * 3, frame + SAMPLE_RATE / 1000 * 7)
    }
}

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
    val currentPlayhead = rememberUpdatedState(playhead)
    val currentSecondsVisible = rememberUpdatedState(secondsVisible)
    val currentOnScroll = rememberUpdatedState(onScroll)
    val currentOnZoom = rememberUpdatedState(onZoom)
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                while (true) {
                    var scrollPosition = currentPlayhead.value
                    var gestureSecondsVisible = currentSecondsVisible.value
                    detectTransformGestures { _, pan, zoom, _ ->
                        val framesPerPx = gestureSecondsVisible * SAMPLE_RATE / size.width
                        if (pan.x != 0f) {
                            scrollPosition = panFrame(scrollPosition, pan.x, framesPerPx)
                            currentOnScroll.value(scrollPosition)
                        }
                        if (zoom != 1f) {
                            gestureSecondsVisible = zoomSeconds(gestureSecondsVisible, zoom)
                            currentOnZoom.value(gestureSecondsVisible)
                        }
                    }
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
    val onsets = synchronized(session) { session.onsets.toList() }
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

    // Accuracy colors show the detected attack in each peak column.
    for (c in 0 until cols) {
        if (peaks[c] == 0f) continue
        val f0 = firstFrame + (c * framesPerPx).toLong()
        val f1 = f0 + max(1L, framesPerPx.toLong())
        val midpoint = f0 + (f1 - f0) / 2
        val onset = onsets
            .asSequence()
            .filter { it.frame in f0 until f1 }
            .minByOrNull { abs(it.frame - midpoint) }
        val color = Color(deviationColor(onset?.deviationMs)).copy(alpha = 0.9f)
        drawLine(
            color,
            Offset(c.toFloat(), waveMid - waveHalf * peaks[c]),
            Offset(c.toFloat(), waveMid + waveHalf * peaks[c]),
            strokeWidth = 1f,
        )
    }

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
    for (o in onsets) {
        val x = xOf(o.frame)
        if (x < -12f || x > w + 12f) continue
        val pk = markerPeak(session, o.frame)
        val y = waveMid - waveHalf * pk - 14f
        val color = Color(deviationColor(o.deviationMs))
        drawCircle(color, radius = 9f, center = Offset(x, y))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 9f, center = Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    }

    // Playhead
    drawLine(RecordRed.copy(alpha = 0.9f), Offset(playheadX, 0f), Offset(playheadX, h), strokeWidth = 2f)
}
