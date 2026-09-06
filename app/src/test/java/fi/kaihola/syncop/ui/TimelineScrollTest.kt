package fi.kaihola.syncop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrollTest {
    @Test
    fun repeatedZoomsAccumulate() {
        var seconds = 6f
        repeat(3) { seconds = zoomSeconds(seconds, 2f) }

        assertEquals(0.75f, seconds, 0.0001f)
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
}
