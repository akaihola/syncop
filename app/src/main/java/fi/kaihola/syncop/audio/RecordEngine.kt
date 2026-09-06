package fi.kaihola.syncop.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaRecorder
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Runs the metronome output and microphone input together.
 *
 * Both streams start at the session frame the recording is resumed from. Output frames are
 * counted as they are written; clicks are embedded at beat frames derived from [tempoBpm]
 * (read live, so tempo changes take effect at the next beat). The input is aligned to the
 * output with one [AudioTimestamp] from each stream, then shifted back by [latencyFrames], so
 * an attack heard exactly on a click lands on the click's frame. [autoLatencyFrames] is the
 * part of [latencyFrames] that came from [Calibration]; it is the base of the next estimate.
 */
class RecordEngine(
    private val session: Session,
    private val tempoBpm: () -> Int,
    private val latencyFrames: () -> Long,
    private val autoLatencyFrames: () -> Long,
    private val onClick: (Long) -> Unit,
    private val onOnset: (Long) -> Unit,
    private val onBleed: (Long) -> Unit,
    private val onProgress: (Long) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var outThread: Thread? = null
    private var inThread: Thread? = null
    private val detector = OnsetDetector()
    private val calibration = Calibration()
    private val pendingClicks = ArrayDeque<Long>()
    private val calibWindow = ShortArray(calibration.searchFrames)
    private var calibFill = -1
    private var calibClick = 0L
    private var appliedAutoFrames = 0L
    /** The output track, published so the input thread can read its timestamp. */
    @Volatile private var track: AudioTrack? = null

    val calibrationMs: Float? get() = calibration.estimateMs

    val isRunning get() = running.get()

    @SuppressLint("MissingPermission")
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val startFrame = session.length
        detector.reset()
        calibration.reset()
        synchronized(pendingClicks) { pendingClicks.clear() }
        calibFill = -1
        outThread = Thread({ outputLoop(startFrame) }, "syncop-out").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        inThread = Thread({ inputLoop(startFrame) }, "syncop-in").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        outThread?.join(); inThread?.join()
        outThread = null; inThread = null
    }

    private fun outputLoop(startFrame: Long) {
        val bufFrames = max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2, SAMPLE_RATE / 20)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufFrames * 4)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val chunk = ShortArray(SAMPLE_RATE / 100) // 10 ms
        var frame = startFrame
        // First click one beat after resume so the player has a moment to get ready.
        var nextClick = startFrame + beatFrames()
        track.play()
        this.track = track
        while (running.get()) {
            chunk.fill(0)
            val end = frame + chunk.size
            while (nextClick < end) {
                ClickSynth.mixInto(chunk, (nextClick - frame).toInt())
                synchronized(pendingClicks) { pendingClicks.addLast(nextClick) }
                onClick(nextClick)
                nextClick += beatFrames()
            }
            // A click whose tail overlaps the chunk start is finished here.
            val prev = nextClick - beatFrames()
            if (prev in (frame - ClickSynth.template.size)..<frame) ClickSynth.mixInto(chunk, (prev - frame).toInt())
            track.write(chunk, 0, chunk.size)
            frame = end
        }
        this.track = null
        track.stop(); track.release()
    }

    private fun beatFrames(): Long = (60.0 * SAMPLE_RATE / tempoBpm()).roundToLong()

    @SuppressLint("MissingPermission")
    private fun inputLoop(startFrame: Long) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(max(minBuf, SAMPLE_RATE / 5 * 2))
            .build()
        val chunk = ShortArray(SAMPLE_RATE / 100)
        var inputFrame = startFrame // frame index of the next input sample, in output clock
        // Input is held back until both streams report a timestamp, or for at most 500 ms.
        // Then the frames captured before output frame startFrame was presented are dropped,
        // plus latencyFrames() so a sound heard on a click lands on the click frame.
        val held = ArrayList<ShortArray>()
        var heldFrames = 0
        var skip = -1L
        record.startRecording()
        while (running.get()) {
            val n = record.read(chunk, 0, chunk.size)
            if (n <= 0) continue
            if (skip < 0) {
                held.add(chunk.copyOf(n)); heldFrames += n
                val align = alignmentFrames(record)
                if (align == null && heldFrames < SAMPLE_RATE / 2) continue
                appliedAutoFrames = autoLatencyFrames()
                skip = (align ?: 0L) + latencyFrames()
                for (h in held) {
                    inputFrame = consume(h, h.size, inputFrame, skip)
                    skip = (skip - h.size).coerceAtLeast(0)
                }
                held.clear()
                continue
            }
            inputFrame = consume(chunk, n, inputFrame, skip)
            skip = (skip - n).coerceAtLeast(0)
        }
        record.stop(); record.release()
    }

    /** Append [count] frames of [data] minus the first [skip] frames. Returns the next input frame. */
    private fun consume(data: ShortArray, count: Int, inputFrame: Long, skip: Long): Long {
        val offset = minOf(skip, count.toLong()).toInt()
        val n = count - offset
        if (n <= 0) return inputFrame
        val samples = if (offset == 0) data else data.copyOfRange(offset, count)
        session.append(samples, n)
        feedCalibration(samples, n, inputFrame)
        detector.feed(samples, n) { f -> onOnset(f) }
        onProgress(inputFrame + n)
        return inputFrame + n
    }

    /**
     * Input frames captured before output frame startFrame was presented, or null while either
     * stream has no timestamp yet. The output timestamp must show an advancing position, because
     * timestamps during warm-up can be wrong (AudioTrack.getTimestamp documentation).
     */
    private fun alignmentFrames(record: AudioRecord): Long? {
        val t = track ?: return null
        val out = AudioTimestamp()
        val inp = AudioTimestamp()
        if (!t.getTimestamp(out) || out.framePosition <= 0) return null
        if (record.getTimestamp(inp, AudioTimestamp.TIMEBASE_MONOTONIC) != AudioRecord.SUCCESS) return null
        return alignmentSkipFrames(out.nanoTime, out.framePosition, inp.nanoTime, inp.framePosition)
    }

    /** Collect the window around each click and look for the click's own bleed in it. */
    private fun feedCalibration(data: ShortArray, count: Int, inputFrame: Long) {
        for (i in 0 until count) {
            val f = inputFrame + i
            if (calibFill < 0) {
                val next: Long = synchronized(pendingClicks) { pendingClicks.firstOrNull() } ?: return
                if (f < next - calibration.leadFrames) continue
                synchronized(pendingClicks) { pendingClicks.removeFirst() }
                calibClick = next
                calibFill = 0
            }
            calibWindow[calibFill++] = data[i]
            if (calibFill == calibWindow.size) {
                calibFill = -1
                val lag = calibration.analyse(calibWindow, appliedAutoFrames)
                if (lag != null) {
                    val bleedFrame = calibClick + lag
                    onBleed(bleedFrame)
                    detector.maskUntil(bleedFrame + ClickSynth.template.size * 3)
                }
            }
        }
    }
}
