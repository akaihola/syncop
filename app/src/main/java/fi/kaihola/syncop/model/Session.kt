package fi.kaihola.syncop.model

import kotlin.math.abs
import kotlin.math.roundToLong

const val SAMPLE_RATE = 48_000

/** One detected attack in the recording. */
data class Onset(val frame: Long, val deviationMs: Float?)

/**
 * The recorded session: mono PCM16 audio plus the click grid and detected attacks.
 * Frame indices are positions in [pcm]. Clicks are the source of truth for scoring,
 * so tempo changes and resumed recordings keep working.
 */
class Session {
    private var pcm = ShortArray(SAMPLE_RATE * 60)
    var length = 0L
        private set
    val clicks = ArrayList<Long>()
    val onsets = ArrayList<Onset>()

    val durationSeconds: Double get() = length.toDouble() / SAMPLE_RATE

    fun append(samples: ShortArray, count: Int) {
        ensureCapacity(length + count)
        System.arraycopy(samples, 0, pcm, length.toInt(), count)
        length += count
    }

    fun sample(frame: Long): Short = if (frame in 0 until length) pcm[frame.toInt()] else 0

    /** Copy [count] frames starting at [from] into [dest], zero-padding beyond the end. */
    fun read(from: Long, dest: ShortArray, count: Int) {
        for (i in 0 until count) dest[i] = sample(from + i)
    }

    /** Peak absolute amplitude (0..1) over [from, to). */
    fun peak(from: Long, to: Long): Float {
        var max = 0
        val start = from.coerceAtLeast(0).toInt()
        val end = to.coerceAtMost(length).toInt()
        for (i in start until end) {
            val v = abs(pcm[i].toInt())
            if (v > max) max = v
        }
        return max / 32768f
    }

    fun addClick(frame: Long) { clicks.add(frame) }

    fun addOnset(frame: Long, grid: MeasurementGrid = MeasurementGrid.WHOLE, clickDensity: ClickDensity = ClickDensity.WHOLE) {
        onsets.add(Onset(frame, deviationMs(frame, grid, clickDensity)))
    }

    /** Signed distance in ms from [frame] to the nearest click, or null if no clicks. */
    fun deviationMs(frame: Long): Float? {
        if (clicks.isEmpty()) return null
        var idx = clicks.binarySearch(frame)
        if (idx < 0) idx = -idx - 1
        var best: Long? = null
        for (i in listOf(idx - 1, idx)) {
            val c = clicks.getOrNull(i) ?: continue
            if (best == null || abs(frame - c) < abs(frame - best)) best = c
        }
        return best?.let { (frame - it) * 1000f / SAMPLE_RATE }
    }

    fun deviationMs(frame: Long, grid: MeasurementGrid, clickDensity: ClickDensity): Float? {
        if (grid == MeasurementGrid.WHOLE) return deviationMs(frame)
        if (clicks.isEmpty()) return null
        var index = clicks.binarySearch(frame)
        if (index < 0) index = (-index - 1).coerceIn(0, clicks.lastIndex)
        val anchor = clicks[index]
        val interval = if (index > 0) anchor - clicks[index - 1] else clicks[1] - anchor
        val step = (interval.toDouble() * clickDensity.denominator / grid.denominator).roundToLong().coerceAtLeast(1)
        val line = anchor + ((frame - anchor).toDouble() / step).roundToLong() * step
        return (frame - line) * 1000f / SAMPLE_RATE
    }

    fun updateOnsetDeviations(grid: MeasurementGrid, clickDensity: ClickDensity) {
        for (i in onsets.indices) {
            val onset = onsets[i]
            onsets[i] = onset.copy(deviationMs = deviationMs(onset.frame, grid, clickDensity))
        }
    }

    fun clear() {
        length = 0
        clicks.clear()
        onsets.clear()
    }

    fun pcmCopy(): ShortArray = pcm.copyOf(length.toInt())

    fun load(samples: ShortArray, clicks: List<Long>, onsets: List<Onset>) {
        clear()
        append(samples, samples.size)
        this.clicks.addAll(clicks)
        this.onsets.addAll(onsets)
    }

    private fun ensureCapacity(needed: Long) {
        if (needed <= pcm.size) return
        var size = pcm.size.toLong()
        while (size < needed) size = size * 3 / 2
        pcm = pcm.copyOf(size.toInt())
    }
}
