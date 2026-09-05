package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import kotlin.math.abs

/**
 * Estimates round-trip latency from the app's own click as picked up by the microphone.
 * For each click emitted at output frame `c`, the input around `c .. c + [searchMs]` is
 * band-passed at the click frequency and its energy peak located. The median lag over recent
 * clicks is the latency estimate; if no clear peak exists (headphones) no estimate is produced.
 */
class Calibration(private val searchMs: Int = 250) {
    private val lags = ArrayList<Long>()
    val searchFrames = searchMs * SAMPLE_RATE / 1000

    /** Latency estimate in frames, or null if there is not enough evidence. */
    val estimateFrames: Long? get() = if (lags.size < 3) null else lags.sorted()[lags.size / 2]
    val estimateMs: Float? get() = estimateFrames?.let { it * 1000f / SAMPLE_RATE }

    /** True when a click bleed was found at the last [analyse] call. */
    var bleedDetected = false
        private set

    /**
     * Analyse [window], which must start at the click's output frame and be at least
     * [searchFrames] long. Returns the detected bleed frame offset within the window, or null.
     */
    fun analyse(window: ShortArray): Long? {
        val bp = Biquad.bandPass(ClickSynth.FREQUENCY_HZ, SAMPLE_RATE, 8.0)
        val n = window.size
        val env = DoubleArray(n)
        var total = 0.0
        for (i in 0 until n) {
            val v = abs(bp.process(window[i] / 32768.0))
            env[i] = v
            total += v
        }
        val mean = total / n
        var bestIdx = -1
        var best = 0.0
        val len = ClickSynth.template.size
        var acc = 0.0
        for (i in 0 until n) {
            acc += env[i]
            if (i >= len) acc -= env[i - len]
            if (acc > best) { best = acc; bestIdx = i - len + 1 }
        }
        val peakMean = best / len
        bleedDetected = bestIdx >= 0 && peakMean > mean * 6 && peakMean > 0.002
        if (!bleedDetected) return null
        val lag = bestIdx.toLong()
        lags.add(lag)
        if (lags.size > 32) lags.removeAt(0)
        return lag
    }

    fun reset() { lags.clear(); bleedDetected = false }
}
