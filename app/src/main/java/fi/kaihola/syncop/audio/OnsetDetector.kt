package fi.kaihola.syncop.audio

import fi.kaihola.syncop.model.SAMPLE_RATE
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streaming attack detector. Feed PCM in order; it reports onset frame indices.
 *
 * The signal is band-limited to roughly 100 Hz – 2 kHz before computing an RMS envelope
 * in [HOP] frames. The metronome click sits at 4 kHz and is thus mostly rejected; in addition,
 * [maskUntil] lets the caller blank the window where speaker bleed of its own click is expected.
 * An onset fires when the envelope jumps well above its slow-moving background, with a refractory
 * period so one attack is not reported twice.
 */
class OnsetDetector(
    private val riseRatio: Float = 3.0f,
    private val minLevel: Float = 0.02f,
    refractoryMs: Int = 80,
) {
    companion object { const val HOP = SAMPLE_RATE / 200 /* 5 ms */ }

    private val hp = Biquad.highPass(100.0, SAMPLE_RATE)
    // Three cascaded sections: ~36 dB down at the 4 kHz click, one octave above the corner.
    private val lp = List(3) { Biquad.lowPass(2000.0, SAMPLE_RATE) }
    private val refractoryFrames = refractoryMs.toLong() * SAMPLE_RATE / 1000
    private val hopBuf = DoubleArray(HOP)
    private var hopFill = 0
    private var frame = 0L
    private var background = 0.0
    private var prevRms = 0.0
    private var lastOnset = -1L shl 40
    private var maskEnd = Long.MIN_VALUE

    /** Ignore onsets in frames before [frameEnd]; used to skip the app's own click bleed. */
    fun maskUntil(frameEnd: Long) { maskEnd = max(maskEnd, frameEnd) }

    /** Process [count] samples, invoking [onOnset] with each detected attack frame. */
    fun feed(samples: ShortArray, count: Int, onOnset: (Long) -> Unit) {
        for (i in 0 until count) {
            val x = samples[i] / 32768.0
            hopBuf[hopFill++] = lp.fold(hp.process(x)) { v, f -> f.process(v) }
            if (hopFill == HOP) {
                hopFill = 0
                analyseHop(onOnset)
            }
            frame++
        }
    }

    private fun analyseHop(onOnset: (Long) -> Unit) {
        var sum = 0.0
        for (v in hopBuf) sum += v * v
        val rms = sqrt(sum / HOP)
        val hopStart = frame + 1 - HOP
        val rising = rms > prevRms * 1.5 && rms > background * riseRatio && rms > minLevel
        val allowed = hopStart - lastOnset > refractoryFrames && hopStart >= maskEnd
        if (rising && allowed) {
            lastOnset = hopStart
            onOnset(hopStart)
        }
        // Slow background follower: fast to fall, slow to rise, so sustained notes settle.
        background = if (rms > background) background * 0.97 + rms * 0.03 else background * 0.8 + rms * 0.2
        prevRms = rms
    }

    fun reset() {
        hp.reset(); lp.forEach { it.reset() }
        hopFill = 0; frame = 0; background = 0.0; prevRms = 0.0
        lastOnset = -1L shl 40; maskEnd = Long.MIN_VALUE
    }
}
