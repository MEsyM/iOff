package com.dualactionwindows.dawdrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.util.Locale

class RoadGameMediaService : MediaBrowserServiceCompat(), TextToSpeech.OnInitListener {

    data class Challenge(
        val prompt: String,
        val acceptedAnswers: List<String> = emptyList(),
        val validator: ((String) -> Boolean)? = null
    )

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ttsReady = false
    private var voiceModeEnabled = false
    private var awaitingAnswer = false
    private var listening = false
    private var retryCount = 0

    private var currentPack = "trivia"
    private var currentIndex = 0
    private var score = 0
    private var attempted = 0

    private val packs = mapOf(
        "trivia" to listOf(
            Challenge("Quick trivia. What is the capital city of Australia?", listOf("canberra")),
            Challenge("Which planet is known as the Red Planet?", listOf("mars")),
            Challenge("How many sides does a hexagon have?", listOf("6", "six")),
            Challenge("What is the largest ocean on Earth?", listOf("pacific", "pacific ocean")),
            Challenge("Which element has the chemical symbol O?", listOf("oxygen"))
        ),
        "words" to listOf(
            Challenge("Word challenge. Name three animals beginning with the letter B.", validator = startsWithCountValidator('b', 3)),
            Challenge("Name three countries beginning with the letter S.", validator = startsWithCountValidator('s', 3)),
            Challenge("Name four foods beginning with the letter C.", validator = startsWithCountValidator('c', 4)),
            Challenge("Name three professions beginning with the letter D.", validator = startsWithCountValidator('d', 3))
        ),
        "math" to listOf(
            Challenge("Mental math. What is 17 plus 28?", listOf("45", "forty five", "forty-five")),
            Challenge("What is 12 times 8?", listOf("96", "ninety six", "ninety-six")),
            Challenge("What is 150 minus 67?", listOf("83", "eighty three", "eighty-three")),
            Challenge("What is half of 246?", listOf("123", "one hundred twenty three", "one hundred and twenty three")),
            Challenge("What is 25 percent of 200?", listOf("50", "fifty"))
        )
    )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        tts = TextToSpeech(this, this)

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        listening = false
                        if (!voiceModeEnabled || !awaitingAnswer) return

                        if (retryCount < 1) {
                            retryCount += 1
                            mainHandler.postDelayed({ startListeningForAnswer() }, 700)
                        } else {
                            retryCount = 0
                            speakSystemMessage("I didn't catch that. Say repeat to hear the question again, or next to skip.")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        listening = false
                        retryCount = 0
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            .orEmpty()

                        val best = matches.firstOrNull().orEmpty().trim()
                        if (best.isBlank()) {
                            startListeningForAnswer()
                        } else {
                            evaluateAnswer(best)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        mediaSession = MediaSessionCompat(this, "DAWDriveRoadGames").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = speakCurrent()

                override fun onPause() {
                    stopListening()
                    awaitingAnswer = false
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onStop() {
                    stopListening()
                    awaitingAnswer = false
                    tts.stop()
                    setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }

                override fun onSkipToNext() = move(1)
                override fun onSkipToPrevious() = move(-1)

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    selectPack(mediaId)
                    currentIndex = 0
                    score = 0
                    attempted = 0
                    speakCurrent()
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    val raw = query.orEmpty().trim()
                    val normalized = normalize(raw)

                    if (awaitingAnswer && normalized.isNotBlank()) {
                        evaluateAnswer(raw)
                        return
                    }

                    currentPack = when {
                        "math" in normalized || "number" in normalized -> "math"
                        "word" in normalized || "letter" in normalized -> "words"
                        else -> "trivia"
                    }
                    currentIndex = 0
                    score = 0
                    attempted = 0
                    speakCurrent()
                }
            })

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateMetadata()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE -> {
                voiceModeEnabled = true
                startForeground(NOTIFICATION_ID, buildNotification())
                speakSystemMessage("Road Voice is ready. Open DAW Drive Road Games in Android Auto.")
            }

            ACTION_STOP_VOICE -> {
                voiceModeEnabled = false
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return Service.START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            ttsReady = true
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    when (utteranceId) {
                        QUESTION_UTTERANCE_ID -> {
                            if (voiceModeEnabled && awaitingAnswer) {
                                mainHandler.post { startListeningForAnswer() }
                            }
                        }

                        FEEDBACK_UTTERANCE_ID -> {
                            mainHandler.post { moveToNextAfterFeedback() }
                        }
                    }
                }
            })
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }

        result.sendResult(
            mutableListOf(
                mediaItem("trivia", "Quick Trivia", "Automatic hands-free answers with Road Voice"),
                mediaItem("words", "Word Challenge", "Automatic hands-free answers with Road Voice"),
                mediaItem("math", "Mental Math", "Automatic hands-free answers with Road Voice")
            )
        )
    }

    private fun startListeningForAnswer() {
        if (!voiceModeEnabled || !awaitingAnswer || listening) return
        val recognizer = speechRecognizer ?: run {
            speakSystemMessage("Speech recognition is not available on this phone.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        }

        listening = true
        recognizer.startListening(intent)
    }

    private fun stopListening() {
        if (listening) {
            speechRecognizer?.cancel()
            listening = false
        }
    }

    private fun mediaItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun selectPack(mediaId: String?) {
        currentPack = if (packs.containsKey(mediaId)) mediaId!! else "trivia"
        awaitingAnswer = false
    }

    private fun move(delta: Int) {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty()) return

        stopListening()
        awaitingAnswer = false
        currentIndex = (currentIndex + delta + items.size) % items.size
        speakCurrent()
    }

    private fun speakCurrent() {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty() || !ttsReady) {
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            return
        }

        stopListening()
        updateMetadata()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        awaitingAnswer = true
        retryCount = 0

        val suffix = if (voiceModeEnabled) {
            " Answer now."
        } else {
            " Use Android Auto voice search to answer, or start Road Voice on your phone for automatic listening."
        }

        tts.speak(
            items[currentIndex].prompt + suffix,
            TextToSpeech.QUEUE_FLUSH,
            null,
            QUESTION_UTTERANCE_ID
        )
    }

    private fun evaluateAnswer(rawAnswer: String) {
        val challenge = packs[currentPack].orEmpty().getOrNull(currentIndex) ?: return

        stopListening()
        awaitingAnswer = false
        attempted += 1

        val normalized = normalize(rawAnswer)
        val correct = challenge.validator?.invoke(normalized)
            ?: challenge.acceptedAnswers.any {
                val accepted = normalize(it)
                normalized == accepted || normalized.contains(accepted)
            }

        if (correct) score += 1

        val feedback = if (correct) {
            "Correct. Score " + score + " out of " + attempted + ". Next question."
        } else {
            val answerHint = challenge.acceptedAnswers.firstOrNull()
            if (answerHint != null) {
                "Not quite. The answer is " + answerHint + ". I heard " + rawAnswer +
                    ". Score " + score + " out of " + attempted + ". Next question."
            } else {
                "I heard " + rawAnswer + ". I couldn't validate that answer. Score " +
                    score + " out of " + attempted + ". Next question."
            }
        }

        updateMetadata()
        tts.speak(
            feedback,
            TextToSpeech.QUEUE_FLUSH,
            null,
            FEEDBACK_UTTERANCE_ID
        )
    }

    private fun moveToNextAfterFeedback() {
        val items = packs[currentPack].orEmpty()
        if (items.isEmpty()) return

        currentIndex = (currentIndex + 1) % items.size
        speakCurrent()
    }

    private fun speakSystemMessage(message: String) {
        if (ttsReady) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, SYSTEM_UTTERANCE_ID)
        }
    }

    private fun updateMetadata() {
        val title = when (currentPack) {
            "words" -> "Word Challenge"
            "math" -> "Mental Math"
            else -> "Quick Trivia"
        }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    title + " - " + (currentIndex + 1)
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                    "DAW Drive Road Games - Score " + score + "/" + attempted
                )
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                    (currentIndex + 1).toLong()
                )
                .build()
        )
    }

    private fun setPlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .setState(
                    state,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
    }

    private fun startsWithCountValidator(letter: Char, required: Int): (String) -> Boolean = { answer ->
        answer
            .split(Regex("[,;\\s]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .count { it.startsWith(letter, ignoreCase = true) } >= required
    }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DAW Drive Road Voice",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DAW Drive Road Voice")
            .setContentText("Hands-free Road Games microphone is active")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts.stop()
        tts.shutdown()
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_VOICE = "com.dualactionwindows.dawdrive.START_ROAD_VOICE"
        const val ACTION_STOP_VOICE = "com.dualactionwindows.dawdrive.STOP_ROAD_VOICE"

        private const val ROOT_ID = "road_games_root"
        private const val CHANNEL_ID = "daw_drive_road_voice"
        private const val NOTIFICATION_ID = 4107

        private const val QUESTION_UTTERANCE_ID = "road_game_question"
        private const val FEEDBACK_UTTERANCE_ID = "road_game_feedback"
        private const val SYSTEM_UTTERANCE_ID = "road_game_system"
    }
}
