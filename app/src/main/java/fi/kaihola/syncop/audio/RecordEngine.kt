package fi.kaihola.syncop.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
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
 * (read live, so tempo changes take effect at the next beat). Input frames are appended to
 * the session shifted back by [latencyFrames], so an attack heard exactly on a click lands
 * on the click's frame.
 */
class RecordEngine(
    private val session: Session,
    private val tempoBpm: () -> Int,
    private val latencyFrames: () -> Long,
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

    val calibrationMs: Float? get() = calibration.estimateMs

    val isRunning get() = running.get()

    @SuppressLint("MissingPermission")
    fun start() {
        if (!running.compareAndSet(false, true)) return
        val startFrame = session.length
        detector.reset()
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
        var skip = latencyFrames()   // drop this many input frames so input aligns with output
        record.startRecording()
        while (running.get()) {
            val n = record.read(chunk, 0, chunk.size)
            if (n <= 0) continue
            var offset = 0
            if (skip > 0) {
                val drop = minOf(skip, n.toLong()).toInt()
                skip -= drop; offset = drop
            }
            val count = n - offset
            if (count <= 0) continue
            val data = if (offset == 0) chunk else chunk.copyOfRange(offset, n)
            session.append(data, count)
            feedCalibration(data, count, inputFrame)
            detector.feed(data, count) { f -> onOnset(f) }
            inputFrame += count
            onProgress(inputFrame)
        }
        record.stop(); record.release()
    }

    /** Collect the window after each click and look for the click's own bleed in it. */
    private fun feedCalibration(data: ShortArray, count: Int, inputFrame: Long) {
        for (i in 0 until count) {
            val f = inputFrame + i
            if (calibFill < 0) {
                val next: Long = synchronized(pendingClicks) { pendingClicks.firstOrNull() } ?: return
                if (f < next) continue
                synchronized(pendingClicks) { pendingClicks.removeFirst() }
                calibFill = 0
            }
            calibWindow[calibFill++] = data[i]
            if (calibFill == calibWindow.size) {
                calibFill = -1
                val lag = calibration.analyse(calibWindow)
                if (lag != null) {
                    val bleedFrame = f - calibWindow.size + 1 + lag
                    onBleed(bleedFrame)
                    detector.maskUntil(bleedFrame + ClickSynth.template.size * 3)
                }
            }
        }
    }
}
