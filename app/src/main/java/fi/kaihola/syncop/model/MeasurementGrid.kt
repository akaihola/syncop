package fi.kaihola.syncop.model

import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementGrid(val label: String, val denominator: Int) {
    WHOLE("1/1", 1), HALF("1/2", 2), QUARTER("1/4", 4),
    EIGHTH("1/8", 8), SIXTEENTH("1/16", 16), THIRTY_SECOND("1/32", 32),
}
