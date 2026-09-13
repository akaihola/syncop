package fi.kaihola.syncop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTest {
    @Test
    fun deviationIsToNearestClick() {
        val s = Session()
        s.addClick(48_000); s.addClick(72_000)
        assertEquals(10f, s.deviationMs(48_480)!!, 0.01f)
        assertEquals(-10f, s.deviationMs(71_520)!!, 0.01f)
        assertNull(Session().deviationMs(0))
    }

    @Test
    fun colourRunsGreenToRed() {
        assertEquals(0xFF24F224.toInt() and 0xFF00FF00.toInt(), deviationColor(0f) and 0xFF00FF00.toInt())
        val red = deviationColor(50f)
        assertEquals(0xF2, (red shr 16) and 0xFF)
        assertEquals(0x24, (red shr 8) and 0xFF)
        val yellow = deviationColor(25f)
        assertEquals((yellow shr 16) and 0xFF, (yellow shr 8) and 0xFF)
    }

    @Test
    fun deviationUsesSelectedGrid() {
        val s = Session()
        s.addClick(48_000); s.addClick(72_000)
        assertEquals(0f, s.deviationMs(60_000, MeasurementGrid.HALF, ClickDensity.WHOLE)!!, 0.01f)
        assertEquals(-62.5f, s.deviationMs(51_000, MeasurementGrid.QUARTER, ClickDensity.WHOLE)!!, 0.01f)
    }

    @Test
    fun selectedGridFollowsLocalTempo() {
        val s = Session()
        s.addClick(48_000); s.addClick(72_000); s.addClick(90_000)
        assertEquals(0f, s.deviationMs(81_000, MeasurementGrid.HALF, ClickDensity.WHOLE)!!, 0.01f)
    }
}
