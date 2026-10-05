package com.dualactionwindows.dawdrive

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

enum class NeuralSpeechStyle {
    NARRATION,
    FEEDBACK,
    UI
}

class ElevenLabsTtsPlayer(
    context: Context,
    private val onStart: (String) -> Unit,
    private val onDone: (String) -> Unit,
    private val onError: (String, Throwable?) -> Unit
) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val cacheDir = File(appContext.cacheDir, "elevenlabs_tts").apply { mkdirs() }

    @Volatile
    private var player: MediaPlayer? = null

    @Volatile
    private var generation = 0L

    fun isConfigured(): Boolean = true

    fun speak(
        text: String,
        locale: Locale,
        style: NeuralSpeechStyle,
        utteranceId: String,
        delayMs: Long = 0L
    ): Boolean {
        if (!isConfigured() || text.isBlank()) return false

        val requestGeneration = synchronized(this) {
            generation += 1
            stopPlayerLocked()
            generation
        }

        executor.execute {
            try {
                val normalized = formatForSpeech(text)
                val audio = cachedOrGenerate(
                    text = normalized,
                    locale = locale,
                    style = style
                )

                if (!isCurrent(requestGeneration)) return@execute

                if (delayMs > 0L) {
                    Thread.sleep(delayMs)
                    if (!isCurrent(requestGeneration)) return@execute
                }

                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(audio.absolutePath)
                    setOnPreparedListener { prepared ->
                        if (!isCurrent(requestGeneration)) {
                            prepared.release()
                            return@setOnPreparedListener
                        }
                        onStart(utteranceId)
                        prepared.start()
                    }
                    setOnCompletionListener { completed ->
                        synchronized(this@ElevenLabsTtsPlayer) {
                            if (player === completed) player = null
                        }
                        completed.release()
                        if (isCurrent(requestGeneration)) onDone(utteranceId)
                    }
                    setOnErrorListener { failed, _, _ ->
                        synchronized(this@ElevenLabsTtsPlayer) {
                            if (player === failed) player = null
                        }
                        failed.release()
                        if (isCurrent(requestGeneration)) {
                            onError(utteranceId, IllegalStateException("MediaPlayer playback failed"))
                        }
                        true
                    }
                }

                synchronized(this) {
                    if (!isCurrent(requestGeneration)) {
                        mediaPlayer.release()
                        return@execute
                    }
                    player = mediaPlayer
                }
                mediaPlayer.prepareAsync()
            } catch (t: Throwable) {
                if (isCurrent(requestGeneration)) onError(utteranceId, t)
            }
        }
        return true
    }

    fun prefetch(text: String, locale: Locale, style: NeuralSpeechStyle = NeuralSpeechStyle.NARRATION) {
        if (!isConfigured() || text.isBlank()) return
        executor.execute {
            try {
                cachedOrGenerate(formatForSpeech(text), locale, style)
            } catch (_: Throwable) {
                // Prefetch is best effort only.
            }
        }
    }

    @Synchronized
    fun stop() {
        generation += 1
        stopPlayerLocked()
    }

    @Synchronized
    fun release() {
        generation += 1
        stopPlayerLocked()
        executor.shutdownNow()
    }

    private fun isCurrent(value: Long): Boolean = generation == value

    private fun stopPlayerLocked() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Throwable) {
            }
            try {
                it.reset()
            } catch (_: Throwable) {
            }
            it.release()
        }
        player = null
    }

    private fun settings(style: NeuralSpeechStyle): VoiceSettings = when (style) {
        NeuralSpeechStyle.NARRATION -> VoiceSettings(0.45, 0.80, 0.15, 0.97)
        NeuralSpeechStyle.FEEDBACK -> VoiceSettings(0.30, 0.80, 0.35, 1.03)
        NeuralSpeechStyle.UI -> VoiceSettings(0.60, 0.80, 0.05, 1.00)
    }

    private fun cachedOrGenerate(
        text: String,
        locale: Locale,
        style: NeuralSpeechStyle
    ): File {
        val voiceSettings = settings(style)
        val key = sha256(
            listOf(
                text,
                locale.toLanguageTag(),
                CACHE_VERSION,
                voiceSettings.toString()
            ).joinToString("|")
        )
        val finalFile = File(cacheDir, "$key.mp3")
        if (finalFile.exists() && finalFile.length() > 512L) return finalFile

        val tempFile = File(cacheDir, "$key.tmp")
        val connection = (URL(TTS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Accept", "audio/mpeg")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "LoneRider-Android")
        }

        try {
            val body = JSONObject().apply {
                put("text", text)
                put("language", if (locale.language.equals("en", true)) "en" else "cs")
                put("style", style.name.lowercase(Locale.ROOT))
            }.toString()

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("ElevenLabs HTTP $status " + errorBody.take(240))
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() <= 512L) error("ElevenLabs returned empty audio")
            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            trimCache()
            return finalFile
        } finally {
            connection.disconnect()
            if (tempFile.exists() && finalFile.exists()) tempFile.delete()
        }
    }

    private fun formatForSpeech(value: String): String {
        val compact = value
            .replace(Regex("\\s+"), " ")
            .replace("...", "…")
            .trim()
        if (compact.isEmpty()) return compact
        return if (compact.last() in listOf('.', '!', '?', '…')) compact else "$compact."
    }

    private fun trimCache() {
        val files = cacheDir.listFiles()?.filter { it.extension == "mp3" }.orEmpty()
        val maxBytes = 96L * 1024L * 1024L
        var size = files.sumOf { it.length() }
        if (size <= maxBytes) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (size <= maxBytes) return
            val bytes = file.length()
            if (file.delete()) size -= bytes
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class VoiceSettings(
        val stability: Double,
        val similarity: Double,
        val style: Double,
        val speed: Double
    )

    companion object {
        private const val TTS_ENDPOINT = "https://api-production-c853.up.railway.app/v1/tts"
        private const val CACHE_VERSION = "proxy-v1"
    }
}
