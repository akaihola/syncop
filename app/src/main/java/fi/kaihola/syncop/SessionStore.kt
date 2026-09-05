package fi.kaihola.syncop

import android.content.Context
import fi.kaihola.syncop.model.Onset
import fi.kaihola.syncop.model.SAMPLE_RATE
import fi.kaihola.syncop.model.Session
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Serializable
private data class Meta(val clicks: List<Long>, val onsets: List<Pair<Long, Float?>>, val tempo: Int, val latencyMs: Int)

/** Saves and restores the session in the app's private storage and exports it as WAV. */
class SessionStore(private val context: Context) {
    private val pcmFile get() = File(context.filesDir, "session.pcm")
    private val metaFile get() = File(context.filesDir, "session.json")

    fun save(session: Session, tempo: Int, latencyMs: Int) {
        val pcm = session.pcmCopy()
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm)
        pcmFile.writeBytes(bytes.array())
        val meta = Meta(session.clicks.toList(), session.onsets.map { it.frame to it.deviationMs }, tempo, latencyMs)
        metaFile.writeText(Json.encodeToString(Meta.serializer(), meta))
    }

    /** Returns (tempo, latencyMs) if a session was restored. */
    fun load(session: Session): Pair<Int, Int>? {
        if (!pcmFile.exists() || !metaFile.exists()) return null
        return runCatching {
            val bytes = pcmFile.readBytes()
            val pcm = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
            val meta = Json.decodeFromString(Meta.serializer(), metaFile.readText())
            session.load(pcm, meta.clicks, meta.onsets.map { Onset(it.first, it.second) })
            meta.tempo to meta.latencyMs
        }.getOrNull()
    }

    fun delete() { pcmFile.delete(); metaFile.delete() }

    /** Write a 16-bit mono WAV into the export cache dir and return the file. */
    fun exportWav(session: Session): File {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, "syncop-recording.wav")
        val pcm = session.pcmCopy()
        val dataBytes = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataBytes)
        buf.asShortBuffer().put(pcm)
        file.writeBytes(buf.array())
        return file
    }
}
