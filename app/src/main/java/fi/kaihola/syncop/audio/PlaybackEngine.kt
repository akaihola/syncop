package fi.kaihola.syncop.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Streams the session from a start frame, optionally mixing the clicks back in. */
class PlaybackEngine(
    private val session: Session,
    private val withClicks: () -> Boolean,
    private val onProgress: (Long) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    val isRunning get() = running.get()

    fun start(fromFrame: Long) {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ loop(fromFrame.coerceIn(0, session.length)) }, "syncop-play").also { it.start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        thread?.join(); thread = null
    }

    private fun loop(fromFrame: Long) {
        val bufFrames = max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) / 2, SAMPLE_RATE / 10)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufFrames * 4)
            .build()
        val chunk = ShortArray(SAMPLE_RATE / 50) // 20 ms
        var frame = fromFrame
        track.play()
        var finished = false
        while (running.get()) {
            if (frame >= session.length) { finished = true; break }
            session.read(frame, chunk, chunk.size)
            if (withClicks()) mixClicks(chunk, frame)
            track.write(chunk, 0, chunk.size)
            frame += chunk.size
            // Report the frame actually being heard, compensating for the track buffer.
            val heard = fromFrame + track.playbackHeadPosition.toLong()
            onProgress(heard.coerceIn(fromFrame, frame))
        }
        if (finished) {
            // Let the tail drain before stopping.
            val remaining = frame - fromFrame - track.playbackHeadPosition
            Thread.sleep((remaining * 1000 / SAMPLE_RATE).coerceIn(0L, 500L))
        }
        track.stop(); track.release()
        running.set(false)
        if (finished) onFinished()
    }

    private fun mixClicks(chunk: ShortArray, frame: Long) {
        val clicks = session.clicks
        val len = ClickSynth.template.size
        var idx = clicks.binarySearch(frame - len)
        if (idx < 0) idx = -idx - 1
        while (idx < clicks.size && clicks[idx] < frame + chunk.size) {
            ClickSynth.mixInto(chunk, (clicks[idx] - frame).toInt(), 0.7f)
            idx++
        }
    }
}
