package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class OnsetDetectorTest {
    private fun signalWithHits(hitsAtMs: List<Int>, seconds: Int = 3): ShortArray {
        val s = ShortArray(SAMPLE_RATE * seconds)
        for (hit in hitsAtMs) {
            val start = hit * SAMPLE_RATE / 1000
            for (i in 0 until SAMPLE_RATE / 5) {
                val env = exp(-i / (SAMPLE_RATE * 0.05))
                val v = sin(2 * PI * 440 * i / SAMPLE_RATE) * env * 0.6 * Short.MAX_VALUE
                if (start + i < s.size) s[start + i] = v.toInt().toShort()
            }
        }
        return s
    }

    @Test
    fun detectsEachAttackOnceWithinTenMs() {
        val hits = listOf(500, 1000, 1500, 2000)
        val s = signalWithHits(hits)
        val found = ArrayList<Long>()
        OnsetDetector().feed(s, s.size) { found.add(it) }
        assertEquals(hits.size, found.size)
        for ((expectedMs, frame) in hits.zip(found)) {
            val ms = frame * 1000.0 / SAMPLE_RATE
            assertTrue("expected $expectedMs got $ms", abs(ms - expectedMs) <= 10)
        }
    }

    @Test
    fun ignoresMetronomeClickBleed() {
        val s = ShortArray(SAMPLE_RATE * 2)
        for (ms in listOf(500, 1000, 1500)) ClickSynth.mixInto(s, ms * SAMPLE_RATE / 1000, 0.5f)
        val found = ArrayList<Long>()
        OnsetDetector().feed(s, s.size) { found.add(it) }
        assertEquals(0, found.size)
    }
}
