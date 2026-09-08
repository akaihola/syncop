package fi.kaihola.syncop

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fi.kaihola.syncop.audio.PlaybackEngine
import fi.kaihola.syncop.audio.RecordEngine
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

enum class Transport { STOPPED, RECORDING, PLAYING }

class SyncopViewModel(app: Application) : AndroidViewModel(app) {
    val session = Session()
    private val store = SessionStore(app)
    /** The auto latency is a property of the device, so it outlives the session and erase. */
    private val prefs = app.getSharedPreferences("syncop", android.content.Context.MODE_PRIVATE)

    var tempo by mutableIntStateOf(120)
        private set
    var transport by mutableStateOf(Transport.STOPPED)
        private set
    /** Session frame under the playhead line. */
    var playhead by mutableLongStateOf(0L)
    /** Bumped whenever the session content changes so the timeline redraws. */
    var revision by mutableIntStateOf(0)
        private set
    var clicksOnPlayback by mutableStateOf(false)
    /** Manual latency offset in ms, added to the auto estimate. */
    var manualLatencyMs by mutableIntStateOf(0)
    var autoLatencyMs by mutableStateOf<Float?>(null)
        private set
    var hasPermission by mutableStateOf(false)

    private val totalLatencyFrames: Long
        get() = (((autoLatencyMs ?: 0f) + manualLatencyMs) * SAMPLE_RATE / 1000).toLong().coerceAtLeast(0)

    private val recorder: RecordEngine = RecordEngine(
        session = session,
        tempoBpm = { tempo },
        latencyFrames = { totalLatencyFrames },
        manualLatencyFrames = { manualLatencyMs.toLong() * SAMPLE_RATE / 1000 },
        onClick = { f -> synchronized(session) { session.addClick(f) }; bump() },
        onOnset = { f -> synchronized(session) { session.addOnset(f) }; bump() },
        onBleed = {
            autoLatencyMs = recorderCalibration()
            autoLatencyMs?.let { prefs.edit().putFloat(AUTO_LATENCY_KEY, it).apply() }
        },
        onProgress = { f -> playhead = f; bump() },
    )
    private val player: PlaybackEngine = PlaybackEngine(
        session = session,
        withClicks = { clicksOnPlayback },
        onProgress = { f -> playhead = f },
        onFinished = { transport = Transport.STOPPED },
    )

    private fun recorderCalibration(): Float? = recorder.calibrationMs

    init {
        store.load(session)?.let { (t, l) -> tempo = t; manualLatencyMs = l }
        if (prefs.contains(AUTO_LATENCY_KEY)) autoLatencyMs = prefs.getFloat(AUTO_LATENCY_KEY, 0f)
        playhead = session.length
    }

    private fun bump() { revision++ }

    fun changeTempo(bpm: Int) { tempo = bpm.coerceIn(MIN_TEMPO, MAX_TEMPO) }
    fun nudgeTempo(delta: Int) = changeTempo(tempo + delta)

    fun toggleRecord() {
        when (transport) {
            Transport.RECORDING -> stop()
            Transport.PLAYING -> { stop(); startRecording() }
            Transport.STOPPED -> startRecording()
        }
    }

    fun togglePlay() {
        when (transport) {
            Transport.PLAYING -> stop()
            Transport.RECORDING -> { stop(); startPlayback() }
            Transport.STOPPED -> startPlayback()
        }
    }

    private fun startRecording() {
        if (!hasPermission) return
        playhead = session.length
        transport = Transport.RECORDING
        recorder.start()
    }

    private fun startPlayback() {
        if (session.length == 0L) return
        if (playhead >= session.length) playhead = 0
        transport = Transport.PLAYING
        player.start(playhead)
    }

    fun stop() {
        when (transport) {
            Transport.RECORDING -> { recorder.stop(); persist() }
            Transport.PLAYING -> player.stop()
            Transport.STOPPED -> {}
        }
        transport = Transport.STOPPED
        bump()
    }

    fun erase() {
        stop()
        synchronized(session) { session.clear() }
        playhead = 0
        store.delete()
        bump()
    }

    fun exportWav(): File = store.exportWav(session)

    private fun persist() {
        val t = tempo; val l = manualLatencyMs
        viewModelScope.launch(Dispatchers.IO) { store.save(session, t, l) }
    }

    override fun onCleared() { stop() }

    companion object {
        const val MIN_TEMPO = 40
        const val MAX_TEMPO = 240
        private const val AUTO_LATENCY_KEY = "auto_latency_ms"
    }
}
