package com.dualactionwindows.dawdrive

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.text.Normalizer
import java.util.Locale

class RoadGameMediaService : MediaBrowserServiceCompat(), TextToSpeech.OnInitListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var tts: TextToSpeech
    private lateinit var triviaEngine: TriviaGameEngine

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ttsReady = false
    private var voiceModeEnabled = false
    private var awaitingAnswer = false
    private var listening = false
    private var retryCount = 0
    private var sessionStarted = false
    private var questionListeningStartedAt = 0L

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()
        triviaEngine = TriviaGameEngine(this)
        tts = TextToSpeech(this, this)

        createSpeechRecognizer()

        mediaSession = MediaSessionCompat(this, "DAWDriveQuickTrivia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startOrResumeTrivia()
                }

                override fun onPause() {
                    pauseGame()
                }

                override fun onStop() {
                    stopGame()
                }

                override fun onSkipToNext() {
                    skipCurrentQuestion()
                }

                override fun onSkipToPrevious() {
                    repeatCurrentQuestion()
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId == MEDIA_ID_TRIVIA) {
                        startOrResumeTrivia()
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    val spoken = query.orEmpty().trim()
                    if (spoken.isBlank()) {
                        startOrResumeTrivia()
                        return
                    }

                    if (handleVoiceCommand(spoken)) {
                        return
                    }

                    if (awaitingAnswer) {
                        evaluateSpeechCandidates(listOf(spoken))
                    } else {
                        startOrResumeTrivia()
                    }
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

    private fun createSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = null
            return
        }

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
                        mainHandler.postDelayed({ startListeningForAnswer() }, 650)
                    } else {
                        retryCount = 0
                        autoSkipAfterSilence()
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    retryCount = 0

                    val candidates = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    if (candidates.isEmpty()) {
                        startListeningForAnswer()
                        return
                    }

                    val commandCandidate = candidates.firstOrNull { handleVoiceCommand(it, dryRun = true) }
                    if (commandCandidate != null) {
                        handleVoiceCommand(commandCandidate)
                        return
                    }

                    evaluateSpeechCandidates(candidates)
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VOICE -> {
                activateRoadVoiceForeground()

                if (intent.getBooleanExtra(EXTRA_RESUME_GAME, false)) {
                    mainHandler.postDelayed({ startOrResumeTrivia() }, 250)
                } else {
                    speakSystem(
                        en = "Road Voice is ready.",
                        cs = "Road Voice je připraven."
                    )
                }
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
        if (status != TextToSpeech.SUCCESS) return

        ttsReady = true
        applyVoiceLanguage()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                when (utteranceId) {
                    SESSION_INTRO_UTTERANCE_ID -> {
                        mainHandler.post { speakCurrentQuestion() }
                    }

                    QUESTION_UTTERANCE_ID -> {
                        if (voiceModeEnabled && awaitingAnswer) {
                            mainHandler.post {
                                questionListeningStartedAt = SystemClock.elapsedRealtime()
                                startListeningForAnswer()
                            }
                        }
                    }

                    FEEDBACK_UTTERANCE_ID -> {
                        mainHandler.post { moveToNextQuestion() }
                    }

                    ROUND_SUMMARY_UTTERANCE_ID -> {
                        mainHandler.postDelayed({ startNextRound() }, 700)
                    }

                    COMMAND_UTTERANCE_ID -> {
                        if (awaitingAnswer && voiceModeEnabled) {
                            mainHandler.postDelayed({ startListeningForAnswer() }, 250)
                        }
                    }
                }
            }
        })
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }

        val p = triviaEngine.profile()
        val subtitle = if (p.language == TriviaGameEngine.Language.CS) {
            "Level " + p.level + " • " + p.xp + " XP • hlasová kariéra"
        } else {
            "Level " + p.level + " • " + p.xp + " XP • voice career"
        }

        result.sendResult(
            mutableListOf(
                mediaItem(
                    MEDIA_ID_TRIVIA,
                    "Quick Trivia Career",
                    subtitle
                )
            )
        )
    }

    private fun startOrResumeTrivia() {
        if (!ensureRoadVoiceForPlayback()) return

        triviaEngine.startOrResumeRound()
        applyVoiceLanguage()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata()

        if (!sessionStarted) {
            sessionStarted = true
            speakSessionIntro()
        } else {
            speakCurrentQuestion()
        }
    }

    private fun speakSessionIntro() {
        val p = triviaEngine.profile()
        val text = if (p.language == TriviaGameEngine.Language.CS) {
            "Vítej zpět. Level " + p.level + ", " + p.xp +
                " XP. Deset otázek. Jdeme na to."
        } else {
            "Welcome back. Level " + p.level + ", " + p.xp +
                " XP. Ten questions. Let's go."
        }

        speak(text, SESSION_INTRO_UTTERANCE_ID)
    }

    private fun speakCurrentQuestion() {
        if (!ttsReady) return

        stopListening()
        awaitingAnswer = true
        retryCount = 0

        val q = triviaEngine.currentQuestion() ?: triviaEngine.startOrResumeRound()
        updateMetadata()

        val text = q.categoryName + ". " + q.prompt
        speak(text, QUESTION_UTTERANCE_ID)
    }

    private fun startListeningForAnswer() {
        if (!voiceModeEnabled || !awaitingAnswer || listening) return

        val recognizer = speechRecognizer
        if (recognizer == null) {
            speakSystem(
                en = "Speech recognition is not available. Game paused.",
                cs = "Rozpoznávání řeči není dostupné. Hra je pozastavena."
            )
            pauseGame()
            return
        }

        val locale = triviaEngine.language().locale

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1200L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                800L
            )
        }

        listening = true
        recognizer.startListening(intent)
    }

    private fun evaluateSpeechCandidates(candidates: List<String>) {
        if (!awaitingAnswer) return

        stopListening()
        awaitingAnswer = false

        val responseMs = if (questionListeningStartedAt > 0L) {
            maxOf(0L, SystemClock.elapsedRealtime() - questionListeningStartedAt)
        } else {
            0L
        }

        val result = triviaEngine.answerCandidates(candidates, responseMs)
        updateMetadata()

        val text = buildFeedback(result)

        speak(
            text,
            if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
            else FEEDBACK_UTTERANCE_ID
        )
    }

    private fun buildFeedback(result: TriviaGameEngine.AnswerResult): String {
        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS
        val parts = mutableListOf<String>()

        if (result.correct) {
            parts += if (cs) {
                when {
                    result.streak >= 10 -> "Správně. Deset v řadě!"
                    result.streak >= 5 -> "Správně. Série " + result.streak + "."
                    result.streak >= 3 -> "Správně. " + result.streak + " v řadě."
                    else -> listOf("Správně.", "Přesně.", "Jo, to sedí.")[result.roundAnswered % 3]
                }
            } else {
                when {
                    result.streak >= 10 -> "Correct. Ten in a row!"
                    result.streak >= 5 -> "Correct. " + result.streak + " answer streak."
                    result.streak >= 3 -> "Correct. " + result.streak + " in a row."
                    else -> listOf("Correct.", "That's right.", "Exactly.")[result.roundAnswered % 3]
                }
            }
        } else {
            parts += if (cs) {
                "Ne tak docela. Správná odpověď je " + result.expected + "."
            } else {
                "Not quite. The answer is " + result.expected + "."
            }

            parts += result.explanation
        }

        if (result.promoted) {
            parts += if (cs) {
                "Postupuješ na level " + result.level + ", " + result.levelName + "."
            } else {
                "Level up. You are now level " + result.level + ", " + result.levelName + "."
            }
        }

        result.newlyUnlockedAchievements.forEach { achievement ->
            parts += if (cs) {
                "Achievement odemčen: " + triviaEngine.achievementTitle(achievement) + "."
            } else {
                "Achievement unlocked: " + triviaEngine.achievementTitle(achievement) + "."
            }
        }

        if (result.roundFinished) {
            parts += localizedRoundSummary()
        }

        return parts.joinToString(" ")
    }

    private fun localizedRoundSummary(): String {
        val summary = triviaEngine.roundSummary()
        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS

        if (cs) {
            val intro = when {
                summary.perfect -> "Perfektní kolo. Deset z deseti!"
                summary.accuracy >= 90 -> "Skvělé kolo."
                else -> "Kolo dokončeno."
            }

            val adjustment = when (summary.nextRoundAdjustment) {
                "harder" -> "Další kolo bude o něco těžší."
                "easier" -> "Další kolo trochu přizpůsobím."
                else -> "Obtížnost zůstává podobná."
            }

            return intro + " " + summary.correct + " z " + summary.answered +
                " správně. Získal jsi " + summary.xpEarned + " XP. Nejlepší série " +
                summary.bestStreak + ". " + adjustment
        }

        val intro = when {
            summary.perfect -> "Perfect round. Ten out of ten!"
            summary.accuracy >= 90 -> "Great round."
            else -> "Round complete."
        }

        val adjustment = when (summary.nextRoundAdjustment) {
            "harder" -> "The next round will be slightly harder."
            "easier" -> "I'll adjust the next round."
            else -> "Difficulty stays about the same."
        }

        return intro + " " + summary.correct + " out of " + summary.answered +
            " correct. You earned " + summary.xpEarned + " XP. Best streak " +
            summary.bestStreak + ". " + adjustment
    }

    private fun moveToNextQuestion() {
        triviaEngine.nextQuestion()
        speakCurrentQuestion()
    }

    private fun startNextRound() {
        triviaEngine.resetRound()
        triviaEngine.startOrResumeRound()

        speakSystem(
            en = "Next round. Let's go.",
            cs = "Další kolo. Jdeme na to.",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun repeatCurrentQuestion() {
        if (!ensureRoadVoiceForPlayback()) return
        stopListening()
        awaitingAnswer = true
        retryCount = 0
        speakCurrentQuestion()
    }

    private fun skipCurrentQuestion() {
        if (!ensureRoadVoiceForPlayback()) return

        stopListening()
        awaitingAnswer = false

        val skip = triviaEngine.skipCurrent()
        if (skip.roundFinished) {
            speak(
                localizedRoundSummary(),
                ROUND_SUMMARY_UTTERANCE_ID
            )
        } else {
            speakSystem(
                en = "Skipped.",
                cs = "Přeskakuji.",
                utteranceId = FEEDBACK_UTTERANCE_ID
            )
        }
    }

    private fun autoSkipAfterSilence() {
        awaitingAnswer = false
        stopListening()

        val skip = triviaEngine.skipCurrent()
        val text = if (triviaEngine.language() == TriviaGameEngine.Language.CS) {
            "Neslyšel jsem odpověď. Přeskakuji."
        } else {
            "I didn't catch an answer. Skipping this one."
        }

        if (skip.roundFinished) {
            speak(text + " " + localizedRoundSummary(), ROUND_SUMMARY_UTTERANCE_ID)
        } else {
            speak(text, FEEDBACK_UTTERANCE_ID)
        }
    }

    private fun handleVoiceCommand(text: String, dryRun: Boolean = false): Boolean {
        val normalized = normalizeCommand(text)

        val command = when {
            normalized in setOf("repeat", "repeat question", "again", "zopakuj", "znovu", "opakuj") ->
                "repeat"

            normalized in setOf("skip", "next", "preskoc", "preskocit", "dalsi") ->
                "skip"

            normalized in setOf("score", "my score", "skore", "moje skore", "vysledek") ->
                "score"

            normalized in setOf("level", "my level", "uroven", "moje uroven") ->
                "level"

            normalized in setOf("stop", "stop game", "pause game", "zastav", "konec", "zastav hru") ->
                "stop"

            else -> null
        }

        if (command == null) return false
        if (dryRun) return true

        when (command) {
            "repeat" -> repeatCurrentQuestion()
            "skip" -> skipCurrentQuestion()

            "score" -> {
                stopListening()
                speak(
                    triviaEngine.scoreSummary(),
                    COMMAND_UTTERANCE_ID
                )
            }

            "level" -> {
                stopListening()
                speak(
                    triviaEngine.levelSummary(),
                    COMMAND_UTTERANCE_ID
                )
            }

            "stop" -> {
                stopGame()
                speakSystem(
                    en = "Game paused. Your progress is saved.",
                    cs = "Hra pozastavena. Postup je uložen."
                )
            }
        }

        return true
    }

    private fun pauseGame() {
        stopListening()
        awaitingAnswer = false
        tts.stop()
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun stopGame() {
        stopListening()
        awaitingAnswer = false
        sessionStarted = false
        tts.stop()
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun ensureRoadVoiceForPlayback(): Boolean {
        if (voiceModeEnabled) return true

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showEnableRoadVoiceNotification(needsPermission = true)
            speakSystem(
                en = "Road Voice needs one-time microphone permission. Tap the phone notification.",
                cs = "Road Voice potřebuje jednorázově povolit mikrofon. Klepni na notifikaci v telefonu."
            )
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            return false
        }

        return try {
            activateRoadVoiceForeground()
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            showEnableRoadVoiceNotification(needsPermission = false)
            speakSystem(
                en = "Tap the Road Voice notification once to enable hands-free mode.",
                cs = "Jednou klepni na notifikaci Road Voice pro aktivaci hands-free režimu."
            )
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            false
        } catch (_: SecurityException) {
            showEnableRoadVoiceNotification(needsPermission = false)
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            false
        }
    }

    private fun activateRoadVoiceForeground() {
        startForeground(NOTIFICATION_ID, buildActiveNotification())
        voiceModeEnabled = true
        getSystemService(NotificationManager::class.java)
            .cancel(ENABLE_NOTIFICATION_ID)
    }

    private fun showEnableRoadVoiceNotification(needsPermission: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ENABLE_ROAD_VOICE, true)
            putExtra(MainActivity.EXTRA_RESUME_GAME, true)
            putExtra(MainActivity.EXTRA_NEEDS_MIC_PERMISSION, needsPermission)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS

        val notification = Notification.Builder(this, ENABLE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (cs) "Zapnout DAW Road Voice" else "Enable DAW Road Voice")
            .setContentText(
                if (needsPermission) {
                    if (cs) "Klepni pro povolení mikrofonu a pokračování"
                    else "Tap to grant microphone access and continue"
                } else {
                    if (cs) "Klepni jednou pro hands-free režim"
                    else "Tap once to start hands-free mode"
                }
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(ENABLE_NOTIFICATION_ID, notification)
    }

    private fun updateMetadata() {
        val p = triviaEngine.profile()
        val q = triviaEngine.currentQuestion()
        val cs = p.language == TriviaGameEngine.Language.CS

        val title = if (cs) {
            "Quick Trivia • Level " + p.level
        } else {
            "Quick Trivia • Level " + p.level
        }

        val artist = if (cs) {
            p.xp.toString() + " XP • série " + p.currentStreak +
                (q?.let { " • " + it.categoryName } ?: "")
        } else {
            p.xp.toString() + " XP • streak " + p.currentStreak +
                (q?.let { " • " + it.categoryName } ?: "")
        }

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                    (p.totalAnswered + 1).toLong()
                )
                .build()
        )
    }

    private fun mediaItem(
        id: String,
        title: String,
        subtitle: String
    ): MediaBrowserCompat.MediaItem {
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

    private fun applyVoiceLanguage() {
        if (!ttsReady) return
        tts.language = triviaEngine.language().locale
    }

    private fun speak(
        text: String,
        utteranceId: String
    ) {
        if (!ttsReady) return
        applyVoiceLanguage()
        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    private fun speakSystem(
        en: String,
        cs: String,
        utteranceId: String = SYSTEM_UTTERANCE_ID
    ) {
        speak(
            if (triviaEngine.language() == TriviaGameEngine.Language.CS) cs else en,
            utteranceId
        )
    }

    private fun stopListening() {
        if (listening) {
            speechRecognizer?.cancel()
            listening = false
        }
    }

    private fun normalizeCommand(value: String): String {
        val withoutMarks = Normalizer
            .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

        return withoutMarks
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "DAW Drive Road Voice",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ENABLE_CHANNEL_ID,
                "DAW Drive setup",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildActiveNotification(): Notification {
        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("DAW Drive Road Voice")
            .setContentText(
                if (cs) "Hands-free Quick Trivia je aktivní"
                else "Hands-free Quick Trivia is active"
            )
            .setOngoing(true)
            .build()
    }

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
        const val ACTION_START_VOICE =
            "com.dualactionwindows.dawdrive.START_ROAD_VOICE"
        const val ACTION_STOP_VOICE =
            "com.dualactionwindows.dawdrive.STOP_ROAD_VOICE"
        const val EXTRA_RESUME_GAME = "resume_game"

        private const val ROOT_ID = "road_games_root"
        private const val MEDIA_ID_TRIVIA = "trivia_career"

        private const val CHANNEL_ID = "daw_drive_road_voice"
        private const val ENABLE_CHANNEL_ID = "daw_drive_enable_voice"
        private const val NOTIFICATION_ID = 4107
        private const val ENABLE_NOTIFICATION_ID = 4108

        private const val SESSION_INTRO_UTTERANCE_ID = "trivia_session_intro"
        private const val QUESTION_UTTERANCE_ID = "trivia_question"
        private const val FEEDBACK_UTTERANCE_ID = "trivia_feedback"
        private const val ROUND_SUMMARY_UTTERANCE_ID = "trivia_round_summary"
        private const val COMMAND_UTTERANCE_ID = "trivia_command"
        private const val SYSTEM_UTTERANCE_ID = "trivia_system"
    }
}
