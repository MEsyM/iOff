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
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private lateinit var spellingEngine: SpellingBeeEngine
    private lateinit var guessWhoEngine: GuessWhoEngine
    private lateinit var kidsTriviaEngine: KidsTriviaEngine
    private lateinit var familyEngine: FamilyGameEngine

    private enum class ActiveGame { TRIVIA, SPELLING, GUESS_WHO, KIDS_TRIVIA, FAMILY }
    private enum class ArtworkState { IDLE, LISTENING, CORRECT, WRONG }

    private var activeGame = ActiveGame.TRIVIA
    private var artworkState = ArtworkState.IDLE

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var hasAudioFocus = false
    private var pendingSpeech: Pair<String, String>? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        DawDebugLog.log(
            this,
            "AUDIO_FOCUS_CHANGE",
            "change=" + audioFocusChangeName(change) +
                " activeGame=" + activeGame +
                " ttsReady=" + ttsReady
        )

        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                pendingSpeech?.let { pending ->
                    pendingSpeech = null
                    performTtsSpeak(pending.first, pending.second)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                if (::tts.isInitialized) {
                    tts.stop()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
            }
        }
    }

    private var ttsReady = false
    private var voiceModeEnabled = false
    private var awaitingAnswer = false
    private var listening = false
    private var retryCount = 0
    private var sessionStarted = false
    private var questionListeningStartedAt = 0L
    private var pendingStartAfterTts = false
    private var fallbackNoticeSpoken = false

    override fun onCreate() {
        super.onCreate()

        DawDebugLog.log(this, "SERVICE_CREATE")
        createNotificationChannels()

        audioManager = getSystemService(AudioManager::class.java)
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusListener, mainHandler)
            .build()

        triviaEngine = TriviaGameEngine(this)
        spellingEngine = SpellingBeeEngine(this)
        guessWhoEngine = GuessWhoEngine(this)
        kidsTriviaEngine = KidsTriviaEngine(this)
        familyEngine = FamilyGameEngine(this)
        tts = TextToSpeech(this, this)

        createSpeechRecognizer()

        mediaSession = MediaSessionCompat(this, "DAWDriveQuickTrivia").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_ON_PLAY", "game=" + activeGame)
                    tryEnableVoiceFromMediaSession()
                    startOrResumeActiveGame()
                }

                override fun onPause() {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_ON_PAUSE", "game=" + activeGame)
                    pauseGame()
                }

                override fun onStop() {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_ON_STOP", "game=" + activeGame)
                    shutdownService()
                }

                override fun onSkipToNext() {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_NEXT", "game=" + activeGame)
                    skipCurrentQuestion()
                }

                override fun onSkipToPrevious() {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_PREVIOUS", "game=" + activeGame)
                    repeatCurrentQuestion()
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_PLAY_FROM_ID", "mediaId=" + mediaId)
                    tryEnableVoiceFromMediaSession()
                    when (mediaId) {
                        MEDIA_ID_SPELLING -> {
                            activeGame = ActiveGame.SPELLING
                            sessionStarted = false
                            startOrResumeSpelling()
                        }
                        MEDIA_ID_GUESS_WHO -> {
                            activeGame = ActiveGame.GUESS_WHO
                            sessionStarted = false
                            startOrResumeGuessWho()
                        }
                        MEDIA_ID_KIDS_TRIVIA -> {
                            activeGame = ActiveGame.KIDS_TRIVIA
                            sessionStarted = false
                            startOrResumeKidsTrivia()
                        }
                        MEDIA_ID_FAMILY -> {
                            activeGame = ActiveGame.FAMILY
                            sessionStarted = false
                            startOrResumeFamily()
                        }
                        else -> {
                            activeGame = ActiveGame.TRIVIA
                            sessionStarted = false
                            startOrResumeTrivia()
                        }
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    DawDebugLog.log(this@RoadGameMediaService, "MEDIA_PLAY_FROM_SEARCH", "query=" + query.orEmpty())
                    val spoken = query.orEmpty().trim()
                    if (spoken.isBlank()) {
                        startOrResumeActiveGame()
                        return
                    }

                    if (handleVoiceCommand(spoken)) {
                        return
                    }

                    if (awaitingAnswer) {
                        when (activeGame) {
                            ActiveGame.SPELLING -> evaluateSpellingCandidates(listOf(spoken))
                            ActiveGame.GUESS_WHO -> evaluateGuessWhoCandidates(listOf(spoken))
                            ActiveGame.KIDS_TRIVIA -> evaluateKidsTriviaCandidates(listOf(spoken))
                            ActiveGame.FAMILY -> evaluateFamilyCandidates(listOf(spoken))
                            ActiveGame.TRIVIA -> evaluateSpeechCandidates(listOf(spoken))
                        }
                    } else {
                        startOrResumeActiveGame()
                    }
                }
            })

            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
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
                override fun onReadyForSpeech(params: Bundle?) {
                    DawDebugLog.log(this@RoadGameMediaService, "STT_READY", "game=" + activeGame)
                }
                override fun onBeginningOfSpeech() {
                    DawDebugLog.log(this@RoadGameMediaService, "STT_BEGIN", "game=" + activeGame)
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    DawDebugLog.log(
                        this@RoadGameMediaService,
                        "STT_ERROR",
                        "code=" + error + " game=" + activeGame +
                            " awaitingAnswer=" + awaitingAnswer
                    )
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

                    DawDebugLog.log(
                        this@RoadGameMediaService,
                        "STT_RESULTS",
                        "game=" + activeGame + " candidates=" + candidates.joinToString(" / ")
                    )

                    if (candidates.isEmpty()) {
                        startListeningForAnswer()
                        return
                    }

                    val commandCandidate = candidates.firstOrNull { handleVoiceCommand(it, dryRun = true) }
                    if (commandCandidate != null) {
                        handleVoiceCommand(commandCandidate)
                        return
                    }

                    when (activeGame) {
                        ActiveGame.SPELLING -> evaluateSpellingCandidates(candidates)
                        ActiveGame.GUESS_WHO -> evaluateGuessWhoCandidates(candidates)
                        ActiveGame.KIDS_TRIVIA -> evaluateKidsTriviaCandidates(candidates)
                        ActiveGame.FAMILY -> evaluateFamilyCandidates(candidates)
                        ActiveGame.TRIVIA -> evaluateSpeechCandidates(candidates)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DawDebugLog.log(
            this,
            "SERVICE_START_COMMAND",
            "action=" + intent?.action + " startId=" + startId
        )
        when (intent?.action) {
            ACTION_START_VOICE -> {
                val activated = activateRoadVoiceForeground()

                if (activated) {
                    if (intent.getBooleanExtra(EXTRA_RESUME_GAME, false)) {
                        mainHandler.postDelayed({ startOrResumeTrivia() }, 250)
                    } else {
                        speakSystem(
                            en = "Road Voice is ready.",
                            cs = "Road Voice je připraven."
                        )
                    }
                }
            }

            ACTION_STOP_VOICE -> {
                shutdownService()
            }
        }

        return Service.START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        DawDebugLog.log(this, "TTS_INIT", "status=" + status)
        if (status != TextToSpeech.SUCCESS) return

        ttsReady = true
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        applyVoiceLanguage()

        if (pendingStartAfterTts) {
            pendingStartAfterTts = false
            mainHandler.post { startOrResumeActiveGame() }
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                DawDebugLog.log(
                    this@RoadGameMediaService,
                    "TTS_START",
                    "id=" + utteranceId + " game=" + activeGame
                )
            }

            override fun onError(utteranceId: String?) {
                DawDebugLog.log(
                    this@RoadGameMediaService,
                    "TTS_ERROR",
                    "id=" + utteranceId + " game=" + activeGame
                )
                abandonAudioFocus("tts_error")
            }

            override fun onDone(utteranceId: String?) {
                DawDebugLog.log(
                    this@RoadGameMediaService,
                    "TTS_DONE",
                    "id=" + utteranceId + " game=" + activeGame
                )
                abandonAudioFocus("tts_done")
                when (utteranceId) {
                    SESSION_INTRO_UTTERANCE_ID -> {
                        mainHandler.post {
                            when (activeGame) {
                                ActiveGame.SPELLING -> speakCurrentSpelling()
                                ActiveGame.GUESS_WHO -> speakCurrentGuessWhoHint()
                                ActiveGame.KIDS_TRIVIA -> speakCurrentKidsQuestion()
                                ActiveGame.FAMILY -> speakCurrentFamilyQuestion()
                                ActiveGame.TRIVIA -> speakCurrentQuestion()
                            }
                        }
                    }

                    QUESTION_UTTERANCE_ID -> {
                        if (awaitingAnswer) {
                            if (voiceModeEnabled) {
                                mainHandler.post {
                                    questionListeningStartedAt = SystemClock.elapsedRealtime()
                                    startListeningForAnswer()
                                }
                            } else {
                                mainHandler.post {
                                    artworkState = ArtworkState.IDLE
                                    updateMetadata()
                                }
                            }
                        }
                    }

                    FEEDBACK_UTTERANCE_ID -> {
                        mainHandler.post {
                            when (activeGame) {
                                ActiveGame.SPELLING -> moveToNextSpellingWord()
                                ActiveGame.GUESS_WHO -> moveToNextGuessWhoPerson()
                                ActiveGame.KIDS_TRIVIA -> moveToNextKidsQuestion()
                                ActiveGame.FAMILY -> moveToNextFamilyQuestion()
                                ActiveGame.TRIVIA -> moveToNextQuestion()
                            }
                        }
                    }

                    ROUND_SUMMARY_UTTERANCE_ID -> {
                        mainHandler.postDelayed({
                            when (activeGame) {
                                ActiveGame.SPELLING -> startNextSpellingRound()
                                ActiveGame.GUESS_WHO -> startNextGuessWhoRound()
                                ActiveGame.KIDS_TRIVIA -> startNextKidsRound()
                                ActiveGame.FAMILY -> startNextFamilySession()
                                ActiveGame.TRIVIA -> startNextRound()
                            }
                        }, 700)
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
    ): BrowserRoot {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_KEY, CONTENT_STYLE_GRID)
            putInt(CONTENT_STYLE_BROWSABLE_KEY, CONTENT_STYLE_GRID)
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }

        val p = triviaEngine.profile()
        val spelling = spellingEngine.profile()
        val guessWho = guessWhoEngine.profile()
        val kids = kidsTriviaEngine.profile()
        val familyPlayers = familyEngine.playerNames()
        val familySubtitle = familyPlayers.joinToString(" vs ") + " • " +
            if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE) "Battle" else "Round"
        val triviaSubtitle = if (p.language == TriviaGameEngine.Language.CS) {
            "Level " + p.level + " • " + p.xp + " XP • hlasová kariéra"
        } else {
            "Level " + p.level + " • " + p.xp + " XP • voice career"
        }
        val spellingSubtitle = if (p.language == TriviaGameEngine.Language.CS) {
            "Level " + spelling.level + " • " + spelling.xp + " XP • hláskování"
        } else {
            "Level " + spelling.level + " • " + spelling.xp + " XP • spelling"
        }
        val guessWhoSubtitle = if (p.language == TriviaGameEngine.Language.CS) {
            "Level " + guessWho.level + " • " + guessWho.xp + " XP • osobnosti"
        } else {
            "Level " + guessWho.level + " • " + guessWho.xp + " XP • personalities"
        }
        val kidsSubtitle = if (p.language == TriviaGameEngine.Language.CS) {
            "6–12 let • Level " + kids.level + " • " + kids.xp + " XP"
        } else {
            "Ages 6–12 • Level " + kids.level + " • " + kids.xp + " XP"
        }

        result.sendResult(
            mutableListOf(
                mediaItem(
                    MEDIA_ID_TRIVIA,
                    "Quick Trivia",
                    triviaSubtitle,
                    R.drawable.trivia_idle
                ),
                mediaItem(
                    MEDIA_ID_SPELLING,
                    "Spelling Bee",
                    spellingSubtitle,
                    R.drawable.spelling_idle
                ),
                mediaItem(
                    MEDIA_ID_GUESS_WHO,
                    "Guess Who",
                    guessWhoSubtitle,
                    R.drawable.guesswho_idle
                ),
                mediaItem(
                    MEDIA_ID_KIDS_TRIVIA,
                    "Trivia Kids 6–12",
                    kidsSubtitle,
                    R.drawable.kids_idle
                ),
                mediaItem(
                    MEDIA_ID_FAMILY,
                    "Family",
                    familySubtitle,
                    R.drawable.kids_idle
                )
            )
        )
    }

    private fun startOrResumeActiveGame() {
        when (activeGame) {
            ActiveGame.SPELLING -> startOrResumeSpelling()
            ActiveGame.GUESS_WHO -> startOrResumeGuessWho()
            ActiveGame.KIDS_TRIVIA -> startOrResumeKidsTrivia()
            ActiveGame.FAMILY -> startOrResumeFamily()
            ActiveGame.TRIVIA -> startOrResumeTrivia()
        }
    }

    private fun startOrResumeTrivia() {
        if (!ensurePlaybackForeground()) return
        if (!ttsReady) {
            pendingStartAfterTts = true
            setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            return
        }

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

    private fun startOrResumeSpelling() {
        if (!ensurePlaybackForeground()) return
        if (!ttsReady) {
            pendingStartAfterTts = true
            setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            return
        }

        spellingEngine.startOrResume()
        applyVoiceLanguage()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata()

        if (!sessionStarted) {
            sessionStarted = true
            val p = spellingEngine.profile()
            speakSystem(
                en = "Welcome to Spelling Bee. Level " + p.level +
                    ". Ten words. Listen, then spell each word aloud.",
                cs = "Vítej ve Spelling Bee. Level " + p.level +
                    ". Deset slov. Poslechni si slovo a potom ho nahlas vyhláskuj.",
                utteranceId = SESSION_INTRO_UTTERANCE_ID
            )
        } else {
            speakCurrentSpelling()
        }
    }

    private fun speakCurrentSpelling() {
        if (!ttsReady) return

        stopListening()
        awaitingAnswer = true
        retryCount = 0

        val word = spellingEngine.currentWord() ?: spellingEngine.startOrResume()
        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS
        updateMetadata()

        val definition = if (cs) word.definitionCs else word.definitionEn
        val text = if (cs) {
            "Slovo je " + word.word + ". Význam: " + definition +
                ". Vyhláskuj ho česky, názvy písmen."
        } else {
            "Your word is " + word.word + ". Definition: " + definition +
                ". Spell it now."
        }

        speak(text, QUESTION_UTTERANCE_ID)
    }

    private fun startOrResumeKidsTrivia() {
        if (!ensurePlaybackForeground()) return
        if (!ttsReady) {
            pendingStartAfterTts = true
            setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            return
        }

        kidsTriviaEngine.startOrResume(triviaEngine.language())
        applyVoiceLanguage()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata()

        if (!sessionStarted) {
            sessionStarted = true
            val p = kidsTriviaEngine.profile()
            speakSystem(
                en = "Welcome to Trivia Kids. Level " + p.level +
                    ". Ten fun questions for ages six to twelve. Let's go.",
                cs = "Vítej v Trivia Kids. Level " + p.level +
                    ". Deset zábavných otázek pro děti od šesti do dvanácti let. Jdeme na to.",
                utteranceId = SESSION_INTRO_UTTERANCE_ID
            )
        } else {
            speakCurrentKidsQuestion()
        }
    }

    private fun speakCurrentKidsQuestion() {
        if (!ttsReady) return
        stopListening()
        awaitingAnswer = true
        retryCount = 0
        artworkState = ArtworkState.IDLE

        val q = kidsTriviaEngine.currentQuestion(triviaEngine.language())
            ?: kidsTriviaEngine.startOrResume(triviaEngine.language())
        updateMetadata()

        speak(q.categoryName + ". " + q.prompt, QUESTION_UTTERANCE_ID)
    }

    private fun startOrResumeFamily() {
        if (!ensurePlaybackForeground()) return
        if (!ttsReady) {
            pendingStartAfterTts = true
            setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            return
        }

        if (familyEngine.mode() != FamilyGameEngine.Mode.BATTLE || !familyEngine.battleNeedsChoice()) {
            familyEngine.startOrResume(triviaEngine.language())
        }
        applyVoiceLanguage()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata()

        val battle = familyEngine.mode() == FamilyGameEngine.Mode.BATTLE
        if (!sessionStarted) {
            sessionStarted = true
            val players = familyEngine.playerNames().joinToString(", ")
            speakSystem(
                en = if (battle) {
                    "Welcome to Family Battle two point zero. Players: " + players +
                        ". The winner of a question chooses the next category and value. Say for example Sport for 300. Then the first player says only their name to buzz in. After I confirm the name, answer the question."
                } else {
                    "Welcome to Family Round. Players: " + players +
                        ". Each player gets their own question. Correct answers earn points and streak bonuses."
                },
                cs = if (battle) {
                    "Vítejte ve Family Battle dva nula. Hráči: " + players +
                        ". Vítěz otázky vybírá další kategorii a hodnotu. Řekni například Sport za 300. Potom se první hráč přihlásí pouze svým jménem a po potvrzení odpoví."
                } else {
                    "Vítejte ve Family Round. Hráči: " + players +
                        ". Každý dostane svou otázku. Za správné odpovědi jsou body a bonusy za sérii."
                },
                utteranceId = SESSION_INTRO_UTTERANCE_ID
            )
        } else {
            if (battle && familyEngine.battleNeedsChoice()) {
                speakFamilyBattleChoicePrompt()
            } else {
                speakCurrentFamilyQuestion()
            }
        }
    }

    private fun speakFamilyBattleChoicePrompt() {
        if (!ttsReady) return
        stopListening()
        awaitingAnswer = true
        retryCount = 0
        artworkState = ArtworkState.IDLE

        val language = triviaEngine.language()
        val cs = language == TriviaGameEngine.Language.CS
        val selector = familyEngine.battleSelector()
        updateMetadata()

        val text = if (cs) {
            selector.name + ", vybíráš další otázku. " +
                familyEngine.availableBattleChoices(language) +
                " Řekni například Sport za 300."
        } else {
            selector.name + ", choose the next question. " +
                familyEngine.availableBattleChoices(language) +
                " Say for example Sport for 300."
        }
        speak(text, QUESTION_UTTERANCE_ID)
    }

    private fun speakCurrentFamilyQuestion() {
        if (!ttsReady) return
        stopListening()
        awaitingAnswer = true
        retryCount = 0
        artworkState = ArtworkState.IDLE

        val language = triviaEngine.language()
        val battle = familyEngine.mode() == FamilyGameEngine.Mode.BATTLE
        if (battle && familyEngine.battleNeedsChoice()) {
            speakFamilyBattleChoicePrompt()
            return
        }
        val q = familyEngine.currentQuestion(language) ?: familyEngine.startOrResume(language)
        val cs = language == TriviaGameEngine.Language.CS
        updateMetadata()

        val prefix = if (battle) {
            if (q.bonusRound) {
                if (cs) "Bonusové kolo za " + q.value + " bodů. " else "Bonus round for " + q.value + " points. "
            } else {
                if (cs) "Battle za " + q.value + " bodů. " else "Battle for " + q.value + " points. "
            }
        } else {
            val player = familyEngine.currentPlayer()
            if (cs) player.name + ", tvoje otázka. " else player.name + ", your question. "
        }

        val suffix = if (battle) {
            if (cs) " Kdo ví, řekne nejdřív svoje jméno." else " If you know it, say your name first."
        } else ""

        speak(prefix + q.categoryName + ". " + q.prompt + suffix, QUESTION_UTTERANCE_ID)
    }

    private fun evaluateFamilyCandidates(candidates: List<String>) {
        if (!awaitingAnswer) return
        stopListening()

        val language = triviaEngine.language()
        val cs = language == TriviaGameEngine.Language.CS

        if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE && familyEngine.battleNeedsChoice()) {
            val choice = familyEngine.parseBattleChoice(candidates)
            if (choice == null) {
                awaitingAnswer = true
                artworkState = ArtworkState.LISTENING
                updateMetadata()
                speakSystem(
                    en = "I did not catch a valid category and value. Say for example Sport for 300.",
                    cs = "Nerozuměl jsem kategorii a hodnotě. Řekni například Sport za 300.",
                    utteranceId = COMMAND_UTTERANCE_ID
                )
                return
            }

            familyEngine.chooseBattleQuestion(choice, language)
            awaitingAnswer = true
            artworkState = ArtworkState.IDLE
            updateMetadata()
            val selected = familyEngine.currentQuestion(language)!!
            speak(
                (if (cs) "Vybráno. " else "Selected. ") +
                    selected.categoryName + ". " + selected.value +
                    (if (cs) " bodů. " else " points. ") +
                    selected.prompt +
                    (if (cs) " Kdo ví, řekne nejdřív svoje jméno." else " If you know it, say your name first."),
                QUESTION_UTTERANCE_ID
            )
            return
        }

        if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE && familyEngine.lockedPlayer() == null) {
            val buzzer = familyEngine.buzz(candidates)
            if (buzzer == null) {
                awaitingAnswer = true
                artworkState = ArtworkState.LISTENING
                updateMetadata()
                speakSystem(
                    en = "I did not catch a player name. Say only your player name.",
                    cs = "Nerozuměl jsem jménu hráče. Řekni pouze svoje hráčské jméno.",
                    utteranceId = COMMAND_UTTERANCE_ID
                )
                return
            }

            awaitingAnswer = true
            artworkState = ArtworkState.LISTENING
            updateMetadata()
            speak(
                if (cs) buzzer.name + ", odpovídej." else buzzer.name + ", answer now.",
                COMMAND_UTTERANCE_ID
            )
            return
        }

        awaitingAnswer = false
        val result = if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE) {
            familyEngine.answerBattle(candidates, language)
        } else {
            familyEngine.answerRound(candidates, language)
        }

        artworkState = if (result.correct) ArtworkState.CORRECT else ArtworkState.WRONG
        updateMetadata()

        val feedback = buildString {
            if (result.correct) {
                append(if (cs) "Správně, " else "Correct, ")
                append(result.player.name)
                append(". +")
                append(result.pointsDelta)
                append(if (cs) " bodů. " else " points. ")
                if (result.streak >= 2) {
                    append(if (cs) "Série " else "Streak ")
                    append(result.streak)
                    append(". ")
                }
            } else {
                append(if (cs) "Špatně, " else "Wrong, ")
                append(result.player.name)
                append(".")
                if (result.pointsDelta < 0) {
                    append(" ")
                    append(result.pointsDelta)
                    append(if (cs) " bodů." else " points.")
                }
                append(" ")
            }

            if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE && !result.questionResolved) {
                val stealText = when (result.stealAttempt + 1) {
                    2 -> if (cs) " Další správná odpověď bere 70 procent bodů." else " The next correct answer gets 70 percent of the points."
                    else -> if (cs) " Další správná odpověď bere 50 procent bodů." else " The next correct answer gets 50 percent of the points."
                }
                append(
                    if (cs) "Otázka je stále otevřená pro ostatní. Řekněte svoje jméno." + stealText
                    else "The question is still open for the other players. Say your name." + stealText
                )
            } else if (!result.correct) {
                append(if (cs) "Správná odpověď je " else "The answer is ")
                append(result.expected)
                append(". ")
                append(result.explanation)
            }

            if (result.questionResolved) {
                append(" ")
                append(if (cs) "Skóre: " else "Score: ")
                append(familyEngine.scoreboard())

                if (result.winnerSelectsNext) {
                    append(" ")
                    append(
                        if (cs) result.player.name + " vybírá další kategorii a hodnotu."
                        else result.player.name + " chooses the next category and value."
                    )
                } else if (result.bonusRound && !result.sessionFinished) {
                    append(" ")
                    append(if (cs) "Pokračujeme bonusovým kolem." else "Continuing the bonus round.")
                }
            }
        }

        if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE && !result.questionResolved) {
            awaitingAnswer = true
            speak(feedback, COMMAND_UTTERANCE_ID)
        } else {
            speak(
                feedback,
                if (result.sessionFinished) ROUND_SUMMARY_UTTERANCE_ID else FEEDBACK_UTTERANCE_ID
            )
        }
    }

    private fun moveToNextFamilyQuestion() {
        artworkState = ArtworkState.IDLE
        if (familyEngine.mode() == FamilyGameEngine.Mode.BATTLE) {
            if (familyEngine.battleNeedsChoice()) {
                speakFamilyBattleChoicePrompt()
            } else {
                familyEngine.startOrResume(triviaEngine.language())
                speakCurrentFamilyQuestion()
            }
        } else {
            familyEngine.nextQuestion(triviaEngine.language())
            speakCurrentFamilyQuestion()
        }
    }

    private fun startNextFamilySession() {
        artworkState = ArtworkState.IDLE
        familyEngine.resetSession(keepScores = true)
        if (familyEngine.mode() != FamilyGameEngine.Mode.BATTLE) {
            familyEngine.startOrResume(triviaEngine.language())
        }
        speakSystem(
            en = "Next Family round. Current score: " + familyEngine.scoreboard() + ".",
            cs = "Další Family kolo. Aktuální skóre: " + familyEngine.scoreboard() + ".",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun startOrResumeGuessWho() {
        if (!ensurePlaybackForeground()) return
        if (!ttsReady) {
            pendingStartAfterTts = true
            setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            return
        }

        guessWhoEngine.startOrResume()
        applyVoiceLanguage()
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateMetadata()

        if (!sessionStarted) {
            sessionStarted = true
            val p = guessWhoEngine.profile()
            speakSystem(
                en = "Welcome to Guess Who. Level " + p.level +
                    ". I will give you up to three clues about a Czech or world personality. Guess the name, or say next hint.",
                cs = "Vítej v Guess Who. Level " + p.level +
                    ". Dostaneš až tři nápovědy k české nebo světové osobnosti. Řekni jméno, nebo řekni další nápověda.",
                utteranceId = SESSION_INTRO_UTTERANCE_ID
            )
        } else {
            speakCurrentGuessWhoHint()
        }
    }

    private fun speakCurrentGuessWhoHint() {
        if (!ttsReady) return

        stopListening()
        awaitingAnswer = true
        retryCount = 0
        artworkState = ArtworkState.IDLE

        guessWhoEngine.startOrResume()
        val language = triviaEngine.language()
        val hintNumber = guessWhoEngine.currentHintNumber()
        val clue = guessWhoEngine.currentClue(language)
        updateMetadata()

        val text = if (language == TriviaGameEngine.Language.CS) {
            "Nápověda " + hintNumber + " ze tří. " + clue +
                " Řekni jméno, nebo další nápověda."
        } else {
            "Hint " + hintNumber + " of three. " + clue +
                " Say the name, or say next hint."
        }

        speak(text, QUESTION_UTTERANCE_ID)
    }

    private fun startListeningForAnswer() {
        if (!voiceModeEnabled || !awaitingAnswer || listening) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DawDebugLog.log(this, "STT_PERMISSION_MISSING", "game=" + activeGame)
            voiceModeEnabled = false
            artworkState = ArtworkState.IDLE
            updateMetadata()
            showEnableRoadVoiceNotification(needsPermission = true)
            return
        }

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

        artworkState = ArtworkState.LISTENING
        updateMetadata()
        listening = true
        DawDebugLog.log(
            this,
            "STT_START",
            "game=" + activeGame + " locale=" + locale.toLanguageTag()
        )
        try {
            recognizer.startListening(intent)
        } catch (e: SecurityException) {
            listening = false
            artworkState = ArtworkState.IDLE
            voiceModeEnabled = false
            updateMetadata()
            DawDebugLog.log(this, "STT_SECURITY_ERROR", e.message.orEmpty())
            showEnableRoadVoiceNotification(needsPermission = true)
        } catch (e: IllegalStateException) {
            listening = false
            artworkState = ArtworkState.IDLE
            updateMetadata()
            DawDebugLog.log(this, "STT_STATE_ERROR", e.message.orEmpty())
            mainHandler.postDelayed({
                if (voiceModeEnabled && awaitingAnswer) startListeningForAnswer()
            }, 700)
        }
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
        artworkState = if (result.correct) ArtworkState.CORRECT else ArtworkState.WRONG
        updateMetadata()

        val text = buildFeedback(result)

        speak(
            text,
            if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
            else FEEDBACK_UTTERANCE_ID
        )
    }

    private fun evaluateSpellingCandidates(candidates: List<String>) {
        if (!awaitingAnswer) return

        stopListening()
        awaitingAnswer = false

        val result = spellingEngine.evaluate(
            candidates,
            triviaEngine.language()
        )
        artworkState = if (result.correct) ArtworkState.CORRECT else ArtworkState.WRONG
        updateMetadata()

        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS
        val feedback = buildString {
            if (result.correct) {
                append(if (cs) "Správně." else "Correct.")
                if (result.streak >= 3) {
                    append(if (cs) " Série " else " Streak ")
                    append(result.streak)
                    append(".")
                }
            } else {
                append(if (cs) "Ne. Správně se píše " else "Not quite. The correct spelling is ")
                append(result.expected.toCharArray().joinToString(" "))
                append(".")
            }

            if (result.promoted) {
                append(if (cs) " Postupuješ na level " else " Level up. You are now level ")
                append(result.level)
                append(".")
            }

            if (result.roundFinished) {
                append(" ")
                append(spellingEngine.roundSummary(triviaEngine.language()))
            }
        }

        speak(
            feedback,
            if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
            else FEEDBACK_UTTERANCE_ID
        )
    }

    private fun evaluateKidsTriviaCandidates(candidates: List<String>) {
        if (!awaitingAnswer) return
        stopListening()
        awaitingAnswer = false

        val result = kidsTriviaEngine.answerCandidates(
            candidates,
            triviaEngine.language()
        )
        artworkState = if (result.correct) ArtworkState.CORRECT else ArtworkState.WRONG
        updateMetadata()

        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS
        val feedback = buildString {
            if (result.correct) {
                append(if (cs) "Správně!" else "Correct!")
                if (result.streak >= 3) {
                    append(if (cs) " Série " else " Streak ")
                    append(result.streak)
                    append(".")
                }
            } else {
                append(if (cs) "Nevadí. Správná odpověď je " else "Not quite. The answer is ")
                append(result.expected)
                append(". ")
                append(result.explanation)
            }
            if (result.promoted) {
                append(if (cs) " Postupuješ na level " else " Level up. You are now level ")
                append(result.level)
                append(".")
            }
            if (result.roundFinished) {
                append(" ")
                append(kidsTriviaEngine.roundSummary(triviaEngine.language()))
            }
        }

        speak(
            feedback,
            if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID else FEEDBACK_UTTERANCE_ID
        )
    }

    private fun evaluateGuessWhoCandidates(candidates: List<String>) {
        if (!awaitingAnswer) return

        stopListening()
        awaitingAnswer = false

        val language = triviaEngine.language()
        val result = guessWhoEngine.evaluateGuess(candidates, language)
        val cs = language == TriviaGameEngine.Language.CS

        when (result.outcome) {
            GuessWhoEngine.GuessOutcome.CORRECT -> {
                artworkState = ArtworkState.CORRECT
                updateMetadata()

                val feedback = buildString {
                    append(if (cs) "Správně. Je to " else "Correct. It is ")
                    append(result.personName)
                    append(". +")
                    append(result.xpEarned)
                    append(" XP.")
                    if (result.streak >= 3) {
                        append(if (cs) " Série " else " Streak ")
                        append(result.streak)
                        append(".")
                    }
                    if (result.promoted) {
                        append(if (cs) " Postupuješ na level " else " Level up. You are now level ")
                        append(result.level)
                        append(".")
                    }
                    if (result.roundFinished) {
                        append(" ")
                        append(guessWhoEngine.roundSummary(language))
                    }
                }

                speak(
                    feedback,
                    if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
                    else FEEDBACK_UTTERANCE_ID
                )
            }

            GuessWhoEngine.GuessOutcome.WRONG_CONTINUE -> {
                artworkState = ArtworkState.WRONG
                updateMetadata()
                awaitingAnswer = true
                speakSystem(
                    en = "Not quite. Guess again, or say next hint.",
                    cs = "Ne tak docela. Zkus jiné jméno, nebo řekni další nápověda.",
                    utteranceId = COMMAND_UTTERANCE_ID
                )
            }

            GuessWhoEngine.GuessOutcome.REVEALED -> {
                artworkState = ArtworkState.WRONG
                updateMetadata()

                val feedback = buildString {
                    append(if (cs) "Ne. Hledaná osobnost byla " else "No. The person was ")
                    append(result.personName)
                    append(".")
                    if (result.roundFinished) {
                        append(" ")
                        append(guessWhoEngine.roundSummary(language))
                    }
                }

                speak(
                    feedback,
                    if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
                    else FEEDBACK_UTTERANCE_ID
                )
            }
        }
    }

    private fun nextGuessWhoHint(auto: Boolean = false) {
        if (activeGame != ActiveGame.GUESS_WHO) return

        stopListening()
        awaitingAnswer = false

        val language = triviaEngine.language()
        val result = guessWhoEngine.nextHint(language)

        if (result.revealed) {
            artworkState = ArtworkState.WRONG
            updateMetadata()
            val text = if (language == TriviaGameEngine.Language.CS) {
                (if (auto) "Bez odpovědi. " else "") +
                    "Už nejsou další nápovědy. Osobnost byla " + result.personName + "." +
                    if (result.roundFinished) " " + guessWhoEngine.roundSummary(language) else ""
            } else {
                (if (auto) "No answer. " else "") +
                    "There are no more hints. The person was " + result.personName + "." +
                    if (result.roundFinished) " " + guessWhoEngine.roundSummary(language) else ""
            }
            speak(
                text,
                if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
                else FEEDBACK_UTTERANCE_ID
            )
            return
        }

        artworkState = ArtworkState.IDLE
        awaitingAnswer = true
        updateMetadata()

        val text = if (language == TriviaGameEngine.Language.CS) {
            "Nápověda " + result.hintNumber + " ze tří. " + result.clue +
                " Řekni jméno, nebo další nápověda."
        } else {
            "Hint " + result.hintNumber + " of three. " + result.clue +
                " Say the name, or say next hint."
        }
        speak(text, QUESTION_UTTERANCE_ID)
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

    private fun moveToNextGuessWhoPerson() {
        artworkState = ArtworkState.IDLE
        guessWhoEngine.nextPerson()
        speakCurrentGuessWhoHint()
    }

    private fun startNextGuessWhoRound() {
        artworkState = ArtworkState.IDLE
        guessWhoEngine.resetRound()
        guessWhoEngine.startOrResume()
        speakSystem(
            en = "Next Guess Who round.",
            cs = "Další kolo Guess Who.",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun moveToNextKidsQuestion() {
        artworkState = ArtworkState.IDLE
        kidsTriviaEngine.nextQuestion(triviaEngine.language())
        speakCurrentKidsQuestion()
    }

    private fun startNextKidsRound() {
        artworkState = ArtworkState.IDLE
        kidsTriviaEngine.resetRound()
        kidsTriviaEngine.startOrResume(triviaEngine.language())
        speakSystem(
            en = "Next Kids Trivia round. Let's go.",
            cs = "Další kolo Trivia Kids. Jdeme na to.",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun moveToNextSpellingWord() {
        artworkState = ArtworkState.IDLE
        spellingEngine.nextWord()
        speakCurrentSpelling()
    }

    private fun startNextSpellingRound() {
        artworkState = ArtworkState.IDLE
        spellingEngine.resetRound()
        spellingEngine.startOrResume()
        speakSystem(
            en = "Next Spelling Bee round.",
            cs = "Další kolo Spelling Bee.",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun moveToNextQuestion() {
        artworkState = ArtworkState.IDLE
        triviaEngine.nextQuestion()
        speakCurrentQuestion()
    }

    private fun startNextRound() {
        artworkState = ArtworkState.IDLE
        triviaEngine.resetRound()
        triviaEngine.startOrResumeRound()

        speakSystem(
            en = "Next round. Let's go.",
            cs = "Další kolo. Jdeme na to.",
            utteranceId = SESSION_INTRO_UTTERANCE_ID
        )
    }

    private fun repeatCurrentQuestion() {
        if (!ensurePlaybackForeground()) return
        stopListening()
        awaitingAnswer = true
        retryCount = 0
        artworkState = ArtworkState.IDLE
        when (activeGame) {
            ActiveGame.SPELLING -> speakCurrentSpelling()
            ActiveGame.GUESS_WHO -> speakCurrentGuessWhoHint()
            ActiveGame.KIDS_TRIVIA -> speakCurrentKidsQuestion()
            ActiveGame.FAMILY -> speakCurrentFamilyQuestion()
            ActiveGame.TRIVIA -> speakCurrentQuestion()
        }
    }

    private fun skipCurrentQuestion() {
        if (!ensurePlaybackForeground()) return

        stopListening()
        awaitingAnswer = false

        when (activeGame) {
            ActiveGame.SPELLING -> {
                val finished = spellingEngine.skipCurrent()
                if (finished) {
                    speak(
                        spellingEngine.roundSummary(triviaEngine.language()),
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

            ActiveGame.KIDS_TRIVIA -> {
                val finished = kidsTriviaEngine.skipCurrent()
                if (finished) {
                    speak(
                        kidsTriviaEngine.roundSummary(triviaEngine.language()),
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

            ActiveGame.GUESS_WHO -> {
                val result = guessWhoEngine.skipCurrent()
                artworkState = ArtworkState.WRONG
                updateMetadata()
                val language = triviaEngine.language()
                val text = if (language == TriviaGameEngine.Language.CS) {
                    "Přeskakuji. Osobnost byla " + result.personName + "." +
                        if (result.roundFinished) " " + guessWhoEngine.roundSummary(language) else ""
                } else {
                    "Skipped. The person was " + result.personName + "." +
                        if (result.roundFinished) " " + guessWhoEngine.roundSummary(language) else ""
                }
                speak(
                    text,
                    if (result.roundFinished) ROUND_SUMMARY_UTTERANCE_ID
                    else FEEDBACK_UTTERANCE_ID
                )
            }

            ActiveGame.FAMILY -> {
                val finished = familyEngine.skip(triviaEngine.language())
                if (finished) {
                    speakSystem(
                        en = "Skipped. Family round complete. Score: " + familyEngine.scoreboard() + ".",
                        cs = "Přeskakuji. Family kolo dokončeno. Skóre: " + familyEngine.scoreboard() + ".",
                        utteranceId = ROUND_SUMMARY_UTTERANCE_ID
                    )
                } else {
                    speakSystem(
                        en = "Skipped.",
                        cs = "Přeskakuji.",
                        utteranceId = FEEDBACK_UTTERANCE_ID
                    )
                }
            }

            ActiveGame.TRIVIA -> {
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
        }
    }

    private fun autoSkipAfterSilence() {
        awaitingAnswer = false
        stopListening()

        val text = if (triviaEngine.language() == TriviaGameEngine.Language.CS) {
            "Neslyšel jsem odpověď. Přeskakuji."
        } else {
            "I didn't catch an answer. Skipping this one."
        }

        when (activeGame) {
            ActiveGame.SPELLING -> {
                val finished = spellingEngine.skipCurrent()
                if (finished) {
                    speak(
                        text + " " + spellingEngine.roundSummary(triviaEngine.language()),
                        ROUND_SUMMARY_UTTERANCE_ID
                    )
                } else {
                    speak(text, FEEDBACK_UTTERANCE_ID)
                }
            }

            ActiveGame.GUESS_WHO -> {
                nextGuessWhoHint(auto = true)
            }

            ActiveGame.KIDS_TRIVIA -> {
                val finished = kidsTriviaEngine.skipCurrent()
                if (finished) {
                    speak(text + " " + kidsTriviaEngine.roundSummary(triviaEngine.language()), ROUND_SUMMARY_UTTERANCE_ID)
                } else {
                    speak(text, FEEDBACK_UTTERANCE_ID)
                }
            }

            ActiveGame.FAMILY -> {
                val finished = familyEngine.skip(triviaEngine.language())
                if (finished) {
                    speak(text + " " + (if (triviaEngine.language() == TriviaGameEngine.Language.CS) "Skóre: " else "Score: ") + familyEngine.scoreboard(), ROUND_SUMMARY_UTTERANCE_ID)
                } else {
                    speak(text, FEEDBACK_UTTERANCE_ID)
                }
            }

            ActiveGame.TRIVIA -> {
                val skip = triviaEngine.skipCurrent()
                if (skip.roundFinished) {
                    speak(text + " " + localizedRoundSummary(), ROUND_SUMMARY_UTTERANCE_ID)
                } else {
                    speak(text, FEEDBACK_UTTERANCE_ID)
                }
            }
        }
    }

    private fun handleVoiceCommand(text: String, dryRun: Boolean = false): Boolean {
        val normalized = normalizeCommand(text)

        val command = when {
            activeGame == ActiveGame.GUESS_WHO &&
                normalized in setOf(
                    "next hint", "hint", "another hint", "give me a hint",
                    "next", "i dont know", "i don t know",
                    "dalsi napoveda", "napoveda", "dej napovedu", "nevim", "dalsi"
                ) -> "hint"

            normalized in setOf("repeat", "repeat question", "again", "zopakuj", "znovu", "opakuj") ->
                "repeat"

            normalized in setOf("skip", "preskoc", "preskocit") ->
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
            "hint" -> nextGuessWhoHint()

            "score" -> {
                stopListening()
                val language = triviaEngine.language()
                val summary = when (activeGame) {
                    ActiveGame.SPELLING -> spellingEngine.scoreSummary(language)
                    ActiveGame.GUESS_WHO -> guessWhoEngine.scoreSummary(language)
                    ActiveGame.KIDS_TRIVIA -> kidsTriviaEngine.scoreSummary(language)
                    ActiveGame.FAMILY -> (if (language == TriviaGameEngine.Language.CS) "Skóre: " else "Score: ") + familyEngine.scoreboard()
                    ActiveGame.TRIVIA -> triviaEngine.scoreSummary()
                }
                speak(summary, COMMAND_UTTERANCE_ID)
            }

            "level" -> {
                stopListening()
                val language = triviaEngine.language()
                val summary = when (activeGame) {
                    ActiveGame.SPELLING -> spellingEngine.levelSummary(language)
                    ActiveGame.GUESS_WHO -> guessWhoEngine.levelSummary(language)
                    ActiveGame.KIDS_TRIVIA -> kidsTriviaEngine.levelSummary(language)
                    ActiveGame.FAMILY -> if (language == TriviaGameEngine.Language.CS) {
                        "Family nemá levely. Aktuální skóre: " + familyEngine.scoreboard()
                    } else {
                        "Family has no levels. Current score: " + familyEngine.scoreboard()
                    }
                    ActiveGame.TRIVIA -> triviaEngine.levelSummary()
                }
                speak(summary, COMMAND_UTTERANCE_ID)
            }

            "stop" -> {
                shutdownService()
            }
        }

        return true
    }

    private fun pauseGame() {
        artworkState = ArtworkState.IDLE
        updateMetadata()
        mainHandler.removeCallbacksAndMessages(null)
        stopListening()
        awaitingAnswer = false
        tts.stop()
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun shutdownService() {
        DawDebugLog.log(this, "SERVICE_SHUTDOWN", "game=" + activeGame)
        mainHandler.removeCallbacksAndMessages(null)
        stopListening()
        awaitingAnswer = false
        sessionStarted = false
        voiceModeEnabled = false
        pendingStartAfterTts = false
        fallbackNoticeSpoken = false
        if (::tts.isInitialized) {
            tts.stop()
        }
        if (::mediaSession.isInitialized) {
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
            mediaSession.isActive = false
        }
        abandonAudioFocus("shutdown")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownService()
        super.onTaskRemoved(rootIntent)
    }

    private fun ensurePlaybackForeground(): Boolean {
        DawDebugLog.log(
            this,
            "FOREGROUND_REQUEST",
            "voiceMode=" + voiceModeEnabled + " game=" + activeGame
        )
        return try {
            val type = if (voiceModeEnabled) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }

            startForeground(
                NOTIFICATION_ID,
                buildActiveNotification(),
                type
            )

            if (!voiceModeEnabled && !fallbackNoticeSpoken) {
                fallbackNoticeSpoken = true
                showEnableRoadVoiceNotification(
                    needsPermission =
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) != PackageManager.PERMISSION_GRANTED
                )
            }
            DawDebugLog.log(this, "FOREGROUND_OK", "voiceMode=" + voiceModeEnabled)
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            DawDebugLog.log(this, "FOREGROUND_BLOCKED", e.javaClass.simpleName + ": " + e.message)
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            showEnableRoadVoiceNotification(needsPermission = false)
            false
        } catch (e: SecurityException) {
            DawDebugLog.log(this, "FOREGROUND_SECURITY_ERROR", e.message.orEmpty())
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
            showEnableRoadVoiceNotification(needsPermission = false)
            false
        }
    }

    private fun activateRoadVoiceForeground(): Boolean {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceModeEnabled = false
            DawDebugLog.log(this, "VOICE_ENABLE_NO_PERMISSION")
            showEnableRoadVoiceNotification(needsPermission = true)
            return false
        }

        return try {
            startForeground(
                NOTIFICATION_ID,
                buildActiveNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            voiceModeEnabled = true
            getSystemService(NotificationManager::class.java)
                .cancel(ENABLE_NOTIFICATION_ID)
            DawDebugLog.log(this, "VOICE_ENABLE_OK", "source=foreground")
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            voiceModeEnabled = false
            DawDebugLog.log(this, "VOICE_ENABLE_BLOCKED", e.message.orEmpty())
            showEnableRoadVoiceNotification(needsPermission = false)
            false
        } catch (e: SecurityException) {
            voiceModeEnabled = false
            DawDebugLog.log(this, "VOICE_ENABLE_SECURITY_ERROR", e.message.orEmpty())
            showEnableRoadVoiceNotification(needsPermission = false)
            false
        }
    }

    private fun tryEnableVoiceFromMediaSession() {
        if (voiceModeEnabled) return

        val hasMicPermission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        DawDebugLog.log(
            this,
            "VOICE_ENABLE_FROM_MEDIA",
            "permission=" + hasMicPermission + " game=" + activeGame
        )

        if (!hasMicPermission) {
            showEnableRoadVoiceNotification(needsPermission = true)
            return
        }

        activateRoadVoiceForeground()
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
            .setContentTitle(if (cs) "Zapnout Lone Rider Voice" else "Enable Lone Rider Voice")
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
        val language = triviaEngine.language()
        val cs = language == TriviaGameEngine.Language.CS
        val artUri = artworkUri(activeGame, artworkState).toString()

        if (activeGame == ActiveGame.FAMILY) {
            val q = familyEngine.currentQuestion(language)
            val battle = familyEngine.mode() == FamilyGameEngine.Mode.BATTLE
            val player = if (!battle) familyEngine.currentPlayer().name else familyEngine.lockedPlayer()?.name
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        "Family • " + if (battle) "Battle" else "Round"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        familyEngine.scoreboard()
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        q?.prompt ?: if (cs) "Připraveno" else "Ready"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                        when {
                            battle && player != null -> if (cs) "Odpovídá " + player else "Answering: " + player
                            battle -> if (cs) "Řekni jméno pro buzz" else "Say your name to buzz"
                            else -> if (cs) "Na tahu: " + player else "Turn: " + player
                        }
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                    .build()
            )
            return
        }

        if (activeGame == ActiveGame.KIDS_TRIVIA) {
            val p = kidsTriviaEngine.profile()
            val q = kidsTriviaEngine.currentQuestion(language)
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Trivia Kids 6–12 • Level " + p.level)
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        p.xp.toString() + " XP • " + (if (cs) "série " else "streak ") + p.streak
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        q?.prompt ?: if (cs) "Připraveno" else "Ready"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                        q?.categoryName ?: "Trivia Kids"
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                    .build()
            )
            return
        }

        if (activeGame == ActiveGame.GUESS_WHO) {
            val p = guessWhoEngine.profile()
            val hintNumber = guessWhoEngine.currentHintNumber()
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        "Guess Who • Level " + p.level
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        p.xp.toString() + " XP • " +
                            (if (cs) "série " else "streak ") + p.streak
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        if (cs) "Nápověda " + hintNumber + " ze 3"
                        else "Hint " + hintNumber + " of 3"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                        if (cs) "Uhodni osobnost" else "Guess the personality"
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                    .putLong(
                        MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                        (p.totalAnswered + 1).toLong()
                    )
                    .build()
            )
            return
        }

        if (activeGame == ActiveGame.SPELLING) {
            val p = spellingEngine.profile()
            val word = spellingEngine.currentWord()
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        "Spelling Bee • Level " + p.level
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        p.xp.toString() + " XP • " +
                            (if (cs) "série " else "streak ") + p.streak
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        word?.let {
                            if (cs) "Vyhláskuj: " + it.word else "Spell: " + it.word
                        } ?: if (cs) "Připraveno" else "Ready"
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                        "Spelling Bee"
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                    .putLong(
                        MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                        (p.totalAnswered + 1).toLong()
                    )
                    .build()
            )
            return
        }

        val p = triviaEngine.profile()
        val q = triviaEngine.currentQuestion()
        val title = "Quick Trivia • Level " + p.level
        val artist = p.xp.toString() + " XP • " +
            (if (cs) "série " else "streak ") + p.currentStreak +
            (q?.let { " • " + it.categoryName } ?: "")

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                    q?.prompt ?: if (cs) "Připraveno" else "Ready"
                )
                .putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                    q?.categoryName ?: "Quick Trivia"
                )
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, artUri)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, artUri)
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                    (p.totalAnswered + 1).toLong()
                )
                .build()
        )
    }

    private fun artworkUri(game: ActiveGame, state: ArtworkState): Uri {
        val resId = when (game) {
            ActiveGame.TRIVIA -> when (state) {
                ArtworkState.IDLE -> R.drawable.trivia_idle
                ArtworkState.LISTENING -> R.drawable.trivia_listening
                ArtworkState.CORRECT -> R.drawable.trivia_correct
                ArtworkState.WRONG -> R.drawable.trivia_wrong
            }
            ActiveGame.SPELLING -> when (state) {
                ArtworkState.IDLE -> R.drawable.spelling_idle
                ArtworkState.LISTENING -> R.drawable.spelling_listening
                ArtworkState.CORRECT -> R.drawable.spelling_correct
                ArtworkState.WRONG -> R.drawable.spelling_wrong
            }
            ActiveGame.GUESS_WHO -> when (state) {
                ArtworkState.IDLE -> R.drawable.guesswho_idle
                ArtworkState.LISTENING -> R.drawable.guesswho_listening
                ArtworkState.CORRECT -> R.drawable.guesswho_correct
                ArtworkState.WRONG -> R.drawable.guesswho_wrong
            }
            ActiveGame.KIDS_TRIVIA -> when (state) {
                ArtworkState.IDLE -> R.drawable.kids_idle
                ArtworkState.LISTENING -> R.drawable.kids_listening
                ArtworkState.CORRECT -> R.drawable.kids_correct
                ArtworkState.WRONG -> R.drawable.kids_wrong
            }
            ActiveGame.FAMILY -> when (state) {
                ArtworkState.IDLE -> R.drawable.kids_idle
                ArtworkState.LISTENING -> R.drawable.kids_listening
                ArtworkState.CORRECT -> R.drawable.kids_correct
                ArtworkState.WRONG -> R.drawable.kids_wrong
            }
        }
        return Uri.parse("android.resource://" + packageName + "/" + resId)
    }

    private fun mediaItem(
        id: String,
        title: String,
        subtitle: String,
        artworkResId: Int
    ): MediaBrowserCompat.MediaItem {
        val itemExtras = Bundle().apply {
            putInt(CONTENT_STYLE_SINGLE_ITEM_KEY, CONTENT_STYLE_GRID)
        }

        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconUri(Uri.parse("android.resource://" + packageName + "/" + artworkResId))
            .setExtras(itemExtras)
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
        if (!ttsReady) {
            DawDebugLog.log(
                this,
                "TTS_SKIP_NOT_READY",
                "id=" + utteranceId + " game=" + activeGame
            )
            return
        }

        applyVoiceLanguage()
        val focusResult = audioManager.requestAudioFocus(audioFocusRequest)

        DawDebugLog.log(
            this,
            "AUDIO_FOCUS_REQUEST",
            "result=" + audioFocusRequestName(focusResult) +
                " id=" + utteranceId +
                " game=" + activeGame +
                " text=" + text.take(120)
        )

        when (focusResult) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasAudioFocus = true
                pendingSpeech = null
                performTtsSpeak(text, utteranceId)
            }

            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                hasAudioFocus = false
                pendingSpeech = text to utteranceId
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
            }

            else -> {
                hasAudioFocus = false
                pendingSpeech = text to utteranceId
                setPlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                mainHandler.postDelayed({
                    if (pendingSpeech != null) {
                        DawDebugLog.log(
                            this,
                            "AUDIO_FOCUS_RETRY",
                            "id=" + utteranceId + " game=" + activeGame
                        )
                        val retry = audioManager.requestAudioFocus(audioFocusRequest)
                        DawDebugLog.log(
                            this,
                            "AUDIO_FOCUS_RETRY_RESULT",
                            "result=" + audioFocusRequestName(retry)
                        )
                        if (retry == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            hasAudioFocus = true
                            pendingSpeech = null
                            performTtsSpeak(text, utteranceId)
                        }
                    }
                }, 700)
            }
        }
    }

    private fun performTtsSpeak(text: String, utteranceId: String) {
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        val result = tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
        DawDebugLog.log(
            this,
            "TTS_SPEAK_CALL",
            "id=" + utteranceId +
                " result=" + result +
                " focus=" + hasAudioFocus +
                " game=" + activeGame
        )
    }

    private fun abandonAudioFocus(reason: String) {
        if (!::audioManager.isInitialized || !::audioFocusRequest.isInitialized) return
        val result = audioManager.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
        DawDebugLog.log(
            this,
            "AUDIO_FOCUS_ABANDON",
            "reason=" + reason + " result=" + result
        )
    }

    private fun audioFocusRequestName(result: Int): String = when (result) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
        else -> result.toString()
    }

    private fun audioFocusChangeName(change: Int): String = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_CAN_DUCK"
        else -> change.toString()
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
                "Lone Rider Road Voice",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        manager.createNotificationChannel(
            NotificationChannel(
                ENABLE_CHANNEL_ID,
                "Lone Rider setup",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildActiveNotification(): Notification {
        val cs = triviaEngine.language() == TriviaGameEngine.Language.CS

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Lone Rider Road Voice")
            .setContentText(
                when (activeGame) {
                    ActiveGame.SPELLING ->
                        if (cs) "Spelling Bee je aktivní" else "Spelling Bee is active"
                    ActiveGame.GUESS_WHO ->
                        if (cs) "Guess Who je aktivní" else "Guess Who is active"
                    ActiveGame.KIDS_TRIVIA ->
                        if (cs) "Trivia Kids je aktivní" else "Kids Trivia is active"
                    ActiveGame.FAMILY ->
                        if (cs) "Family hra je aktivní" else "Family game is active"
                    ActiveGame.TRIVIA ->
                        if (cs) "Quick Trivia je aktivní" else "Quick Trivia is active"
                }
            )
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        DawDebugLog.log(this, "SERVICE_DESTROY", "game=" + activeGame)
        mainHandler.removeCallbacksAndMessages(null)
        abandonAudioFocus("destroy")
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
        private const val MEDIA_ID_SPELLING = "spelling_bee"
        private const val MEDIA_ID_GUESS_WHO = "guess_who"
        private const val MEDIA_ID_KIDS_TRIVIA = "kids_trivia"
        private const val MEDIA_ID_FAMILY = "family_game"

        private const val CONTENT_STYLE_BROWSABLE_KEY =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_KEY =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_SINGLE_ITEM_KEY =
            "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT"
        private const val CONTENT_STYLE_GRID = 2

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
