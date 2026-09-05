package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short 4 kHz click. Kept above the onset detector's analysis band (see [OnsetDetector])
 * so speaker bleed does not register as an attack, and used as the template for [Calibration].
 */
object ClickSynth {
    const val FREQUENCY_HZ = 4000.0
    const val DURATION_MS = 4
    val template: ShortArray = ShortArray(SAMPLE_RATE * DURATION_MS / 1000) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        val env = 1.0 - i.toDouble() / (SAMPLE_RATE * DURATION_MS / 1000)
        (sin(2 * PI * FREQUENCY_HZ * t) * env * 0.8 * Short.MAX_VALUE).toInt().toShort()
    }

    /** Mix the click into [buffer] so that it starts at [offset] (may be negative or beyond the end). */
    fun mixInto(buffer: ShortArray, offset: Int, gain: Float = 1f) {
        for (i in template.indices) {
            val j = offset + i
            if (j < 0 || j >= buffer.size) continue
            val v = buffer[j] + (template[i] * gain).toInt()
            buffer[j] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
