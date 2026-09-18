package fi.kaihola.syncop.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickDensityTest {
    @Test
    fun exposesOnlySupportedDensities() {
        assertEquals(listOf("1/1", "1/2", "1/4", "1/8", "1/16"), ClickDensity.entries.map { it.label })
        assertEquals(listOf(1, 2, 4, 8, 16), ClickDensity.entries.map { it.denominator })
    }

    @Test
    fun quarterIsOneClickPerBeatAt120Bpm() {
        assertEquals(24_000L, ClickDensity.QUARTER.framesAt(120))
    }

    @Test
    fun dividesTheBeatAt120Bpm() {
        assertEquals(listOf(96_000L, 48_000L, 24_000L, 12_000L, 6_000L), ClickDensity.entries.map { it.framesAt(120) })
    }
}
