package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationTest {
    private fun windowWithBleed(cal: Calibration, lagFrames: Int): ShortArray =
        ShortArray(cal.searchFrames).also { ClickSynth.mixInto(it, cal.leadFrames + lagFrames, 0.3f) }

    @Test
    fun findsClickLagInWindow() {
        val cal = Calibration()
        val lagFrames = 37 * SAMPLE_RATE / 1000
        repeat(3) {
            val lag = cal.analyse(windowWithBleed(cal, lagFrames))
            assertEquals(lagFrames.toDouble(), lag!!.toDouble(), 10.0)
        }
        assertEquals(37f, cal.estimateMs!!, 0.5f)
    }

    @Test
    fun estimateIsBasePlusLag() {
        val cal = Calibration()
        val lagFrames = 5 * SAMPLE_RATE / 1000
        val base = 30L * SAMPLE_RATE / 1000
        repeat(3) {
            val lag = cal.analyse(windowWithBleed(cal, lagFrames), base)
            assertEquals(lagFrames.toDouble(), lag!!.toDouble(), 10.0)
        }
        assertEquals(35f, cal.estimateMs!!, 0.5f)
    }

    @Test
    fun bleedBeforeClickGivesNegativeLag() {
        val cal = Calibration()
        val lagFrames = -8 * SAMPLE_RATE / 1000
        val lag = cal.analyse(windowWithBleed(cal, lagFrames))
        assertEquals(lagFrames.toDouble(), lag!!.toDouble(), 10.0)
    }

    @Test
    fun resetClearsEstimate() {
        val cal = Calibration()
        repeat(3) { cal.analyse(windowWithBleed(cal, 100)) }
        cal.reset()
        assertNull(cal.estimateMs)
    }

    @Test
    fun silenceGivesNoEstimate() {
        val cal = Calibration()
        assertNull(cal.analyse(ShortArray(cal.searchFrames)))
        assertNull(cal.estimateMs)
    }

    @Test
    fun alignmentSkipsInputCapturedBeforeOutputStart() {
        // Output frame 480 was presented at 30 ms, so output frame 0 was presented at 20 ms.
        // Input frame 1920 was captured at 40 ms, so input frame 0 was captured at 0 ms.
        // The 20 ms of input before the output start is 960 frames.
        val skip = alignmentSkipFrames(outNanos = 30_000_000, outFrame = 480, inNanos = 40_000_000, inFrame = 1920)
        assertEquals(960L, skip)
    }

    @Test
    fun alignmentIsNeverNegative() {
        // Input started 10 ms after output frame 0 was presented.
        val skip = alignmentSkipFrames(outNanos = 30_000_000, outFrame = 480, inNanos = 30_000_000, inFrame = 0)
        assertEquals(0L, skip)
    }
}
