package fi.kaihola.syncop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrollTest {
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
