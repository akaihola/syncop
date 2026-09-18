package fi.kaihola.syncop.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
enum class ClickDensity(val label: String, val denominator: Int) {
    WHOLE("1/1", 1),
    HALF("1/2", 2),
    QUARTER("1/4", 4),
    EIGHTH("1/8", 8),
    SIXTEENTH("1/16", 16),

    ;

    fun framesAt(tempoBpm: Int): Long = (60.0 * SAMPLE_RATE * 4 / tempoBpm / denominator).roundToLong()
}
