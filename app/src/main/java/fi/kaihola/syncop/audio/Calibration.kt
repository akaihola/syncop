package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import kotlin.math.abs

/**
 * Estimates the residual latency from the app's own click as picked up by the microphone.
 * For each click emitted at output frame `c`, the input from `c - [leadMs]` to `c + [searchMs]`
 * is band-passed at the click frequency and its energy peak located. The lag is measured from
 * the click frame and can be negative when the applied latency shift overshoots. The stored value
 * is `base + lag`, where `base` is the latency that was already applied to the input, so the
 * estimate is a total and does not oscillate from run to run. The median over recent clicks is
 * the estimate; if no clear peak exists (headphones) no estimate is produced.
 */
class Calibration(private val searchMs: Int = 250, leadMs: Int = 20) {
    private val lags = ArrayList<Long>()
    /** Frames of input before the click frame that are included in the window. */
    val leadFrames = leadMs * SAMPLE_RATE / 1000
    val searchFrames = leadFrames + searchMs * SAMPLE_RATE / 1000

    /** Latency estimate in frames, or null if there is not enough evidence. */
    val estimateFrames: Long? get() = if (lags.size < 3) null else lags.sorted()[lags.size / 2]
    val estimateMs: Float? get() = estimateFrames?.let { it * 1000f / SAMPLE_RATE }

    /** True when a click bleed was found at the last [analyse] call. */
    var bleedDetected = false
        private set

    /**
     * Analyse [window], which must start [leadFrames] before the click's output frame and be
     * [searchFrames] long. [base] is the latency in frames already applied to the input.
     * Returns the bleed offset from the click frame (may be negative), or null.
     */
    fun analyse(window: ShortArray, base: Long = 0): Long? {
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
        val lag = bestIdx.toLong() - leadFrames
        lags.add(base + lag)
        if (lags.size > 32) lags.removeAt(0)
        return lag
    }

    fun reset() { lags.clear(); bleedDetected = false }
}

/**
 * Number of input frames captured before output frame 0 was presented, from one
 * [android.media.AudioTimestamp] of each stream on the same monotonic clock. Negative when
 * the input started after output frame 0: the caller must then pad that many frames of
 * silence in front of the input. A warm output stream can start well before the microphone.
 */
fun alignmentSkipFrames(outNanos: Long, outFrame: Long, inNanos: Long, inFrame: Long): Long {
    val outStartNanos = outNanos - outFrame * 1_000_000_000L / SAMPLE_RATE
    val framesUntilOutStart = (outStartNanos - inNanos) * SAMPLE_RATE / 1_000_000_000L
    return inFrame + framesUntilOutStart
}
