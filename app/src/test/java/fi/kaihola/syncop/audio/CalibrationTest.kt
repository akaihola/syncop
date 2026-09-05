package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationTest {
    @Test
    fun findsClickLagInWindow() {
        val cal = Calibration()
        val lagFrames = 37 * SAMPLE_RATE / 1000
        repeat(3) {
            val window = ShortArray(cal.searchFrames)
            ClickSynth.mixInto(window, lagFrames, 0.3f)
            val lag = cal.analyse(window)
            assertEquals(lagFrames.toDouble(), lag!!.toDouble(), 10.0)
        }
        assertEquals(37f, cal.estimateMs!!, 0.5f)
    }

    @Test
    fun silenceGivesNoEstimate() {
        val cal = Calibration()
        assertNull(cal.analyse(ShortArray(cal.searchFrames)))
        assertNull(cal.estimateMs)
    }
}
