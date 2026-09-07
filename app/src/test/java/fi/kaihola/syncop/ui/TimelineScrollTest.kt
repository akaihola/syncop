package fi.kaihola.syncop.ui

import fi.kaihola.syncop.model.Onset
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import fi.kaihola.syncop.model.deviationColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TimelineScrollTest {
    @Test
    fun repeatedZoomsAccumulate() {
        var seconds = 6f
        repeat(3) { seconds = zoomSeconds(seconds, 2f) }

        assertEquals(1f, seconds, 0f)
    }

    @Test
    fun zoomIsClampedToVisibleRange() {
        assertEquals(1f, zoomSeconds(2f, 10f), 0f)
        assertEquals(30f, zoomSeconds(20f, 0.5f), 0f)
    }

    @Test
    fun accumulatedSmallPansMatchOneBigPan() {
        val framesPerPx = 133f
        var accumulated = 1_000L
        repeat(10) { accumulated = panFrame(accumulated, 5f, framesPerPx) }

        val direct = panFrame(1_000L, 50f, framesPerPx)

        assertEquals(direct, accumulated)
    }

    @Test
    fun panRightMovesPlayheadBackward() {
        assertEquals(1_000L - (20f * 133f).toLong(), panFrame(1_000L, 20f, 133f))
    }

    @Test
    fun markerPeakIsStableAcrossHorizontalOffsets() {
        val session = Session()
        val samples = ShortArray(SAMPLE_RATE)
        samples[5_000] = 10_000
        samples[5_003] = 20_000
        session.append(samples, samples.size)

        val framesPerPx = 100f
        val firstFrameA = 10_000L - (720f * framesPerPx).toLong()
        val firstFrameB = 20_000L - (720f * framesPerPx).toLong()
        val xAtOffsetA = (5_000L - firstFrameA) / framesPerPx
        val xAtOffsetB = (5_000L - firstFrameB) / framesPerPx
        val peakAtOffsetA = markerPeak(session, 5_000L, framesPerPx)
        val peakAtOffsetB = markerPeak(session, 5_000L, framesPerPx)

        assertNotEquals(xAtOffsetA, xAtOffsetB)
        assertEquals(peakAtOffsetA, peakAtOffsetB, 0f)
        assertEquals(20_000 / 32_768f, peakAtOffsetA, 0f)
    }

    @Test
    fun markerKeepsOnsetFrameAndAccuracyColor() {
        val onset = Onset(5_000L, 25f)

        assertEquals(5_000L, onset.frame)
        assertEquals(deviationColor(25f), deviationColor(onset.deviationMs))
    }
}
