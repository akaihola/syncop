package fi.kaihola.syncop.model

import kotlin.math.abs

/** Attack-to-beat deviation at which a marker is fully red. */
const val RED_AT_MS = 50f

/**
 * Hue-based colour for a timing deviation: green (120°) when on the beat, through yellow (60°)
 * and orange (30°) to red (0°) at ±[RED_AT_MS] or beyond. Returned as packed 0xAARRGGBB.
 */
fun deviationColor(deviationMs: Float?): Int {
    if (deviationMs == null) return 0xFF9AA4B2.toInt()
    val t = (abs(deviationMs) / RED_AT_MS).coerceIn(0f, 1f)
    val hue = 120f * (1f - t)
    return hsvToArgb(hue, 0.85f, 0.95f)
}

internal fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
