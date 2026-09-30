package com.dualactionwindows.dawdrive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

class FamilyGameActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    enum class Mode { SETUP, MENU, ROUNDS, BATTLE }
    enum class BattlePhase { QUESTION, BUZZ, ANSWER, RESULT }

    data class Player(
        val id: Long,
        val name: String,
        var score: Int = 0,
        var streak: Int = 0,
        var bestStreak: Int = 0,
        var lockedOut: Boolean = false
    )

    private val prefs by lazy {
        getSharedPreferences("daw_family_game", Context.MODE_PRIVATE)
    }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private val players = mutableStateListOf<Player>()
    private var mode by mutableStateOf(Mode.SETUP)
    private var language = TriviaGameEngine.Language.CS

    private var currentQuestion by mutableStateOf<TriviaQuestionBank.LocalizedQuestion?>(null)
    private var questionIndex = 0
    private var roundPlayerIndex by mutableStateOf(0)
    private var battleLockedPlayerId by mutableStateOf<Long?>(null)
    private var battlePhase by mutableStateOf(BattlePhase.QUESTION)
    private var statusText by mutableStateOf("")
    private var heardText by mutableStateOf("")
    private var listening by mutableStateOf(false)

    private val usedQuestionIds = mutableSetOf<String>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startListening()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        language = TriviaGameEngine(this).language()
        loadPlayers()
        if (players.size >= 2) mode = Mode.MENU

        tts = TextToSpeech(this, this)
        createRecognizer()

        setContent {
            FamilyGameScreen()
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = language.locale
        }
    }

    private fun createRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listening = false
                    if (mode == Mode.ROUNDS || mode == Mode.BATTLE) {
                        statusText = if (language == TriviaGameEngine.Language.CS) {
                            "Neslyšel jsem. Zkus to znovu."
                        } else {
                            "I didn't catch that. Try again."
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val candidates = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .filter { it.isNotBlank() }

                    if (candidates.isEmpty()) return
                    heardText = candidates.first()

                    when (mode) {
                        Mode.ROUNDS -> evaluateRoundsAnswer(candidates)
                        Mode.BATTLE -> handleBattleSpeech(candidates)
                        else -> Unit
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun startListening() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        recognizer?.cancel()

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        recognizer?.startListening(intent)
        listening = true
    }

    private fun speak(text: String, listenAfter: Boolean = false) {
        if (!ttsReady) {
            if (listenAfter) startListening()
            return
        }

        tts.stop()
        tts.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (listenAfter) {
                    runOnUiThread { startListening() }
                }
            }
        })

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "family_game"
        )
    }

    private fun startRounds() {
        resetScores()
        mode = Mode.ROUNDS
        roundPlayerIndex = 0
        usedQuestionIds.clear()
        nextRoundsQuestion()
    }

    private fun nextRoundsQuestion() {
        if (players.isEmpty()) return
        currentQuestion = pickQuestion()
        val player = players[roundPlayerIndex]
        val q = localizedPrompt(currentQuestion!!)

        statusText = if (language == TriviaGameEngine.Language.CS) {
            player.name + " je na řadě"
        } else {
            player.name + "'s turn"
        }

        speak(
            if (language == TriviaGameEngine.Language.CS) {
                player.name + ". Tvoje otázka. " + q
            } else {
                player.name + ". Your question. " + q
            },
            listenAfter = true
        )
    }

    private fun evaluateRoundsAnswer(candidates: List<String>) {
        val q = currentQuestion ?: return
        val player = players[roundPlayerIndex]
        val correct = candidates.any { isCorrectAnswer(it, q) }

        if (correct) {
            player.streak += 1
            player.bestStreak = maxOf(player.bestStreak, player.streak)
            val streakBonus = min(20, maxOf(0, player.streak - 1) * 5)
            val points = 10 + streakBonus
            player.score += points

            statusText = if (language == TriviaGameEngine.Language.CS) {
                "Správně! +" + points + " bodů. Série " + player.streak + "."
            } else {
                "Correct! +" + points + " points. Streak " + player.streak + "."
            }

            speak(statusText)
        } else {
            player.streak = 0
            statusText = if (language == TriviaGameEngine.Language.CS) {
                "Špatně. Správná odpověď: " + displayAnswer(q)
            } else {
                "Wrong. Correct answer: " + displayAnswer(q)
            }
            speak(statusText)
        }

        roundPlayerIndex = (roundPlayerIndex + 1) % players.size
    }

    private fun startBattle() {
        resetScores()
        mode = Mode.BATTLE
        usedQuestionIds.clear()
        nextBattleQuestion()
    }

    private fun nextBattleQuestion() {
        players.forEach { it.lockedOut = false }
        battleLockedPlayerId = null
        battlePhase = BattlePhase.QUESTION
        currentQuestion = pickQuestion()
        val q = localizedPrompt(currentQuestion!!)

        statusText = if (language == TriviaGameEngine.Language.CS) {
            "Poslouchejte otázku…"
        } else {
            "Listen to the question…"
        }

        speak(
            q + if (language == TriviaGameEngine.Language.CS) {
                ". Kdo ví odpověď, řekne svoje jméno."
            } else {
                ". If you know it, say your name."
            }
        )

        battlePhase = BattlePhase.BUZZ
        statusText = if (language == TriviaGameEngine.Language.CS) {
            "BUZZ! Řekni svoje jméno"
        } else {
            "BUZZ! Say your name"
        }
        startListening()
    }

    private fun handleBattleSpeech(candidates: List<String>) {
        when (battlePhase) {
            BattlePhase.BUZZ -> {
                val match = findBuzzPlayer(candidates)
                if (match == null) {
                    statusText = if (language == TriviaGameEngine.Language.CS) {
                        "Nezachytil jsem jméno hráče."
                    } else {
                        "I didn't catch a player name."
                    }
                    startListening()
                    return
                }

                val (player, remainder) = match
                if (player.lockedOut) {
                    startListening()
                    return
                }

                battleLockedPlayerId = player.id
                battlePhase = BattlePhase.ANSWER
                statusText = player.name + " buzz!"

                if (remainder.isNotBlank()) {
                    evaluateBattleAnswer(player, listOf(remainder))
                } else {
                    speak(
                        if (language == TriviaGameEngine.Language.CS) {
                            player.name + ", odpovídej."
                        } else {
                            player.name + ", answer now."
                        },
                        listenAfter = true
                    )
                }
            }

            BattlePhase.ANSWER -> {
                val player = players.firstOrNull { it.id == battleLockedPlayerId } ?: return
                evaluateBattleAnswer(player, candidates)
            }

            else -> Unit
        }
    }

    private fun evaluateBattleAnswer(player: Player, candidates: List<String>) {
        val q = currentQuestion ?: return
        val correct = candidates.any { isCorrectAnswer(it, q) }

        if (correct) {
            player.streak += 1
            player.bestStreak = maxOf(player.bestStreak, player.streak)
            val points = 20 + min(20, maxOf(0, player.streak - 1) * 5)
            player.score += points

            statusText = if (language == TriviaGameEngine.Language.CS) {
                "Správně, " + player.name + "! +" + points + " bodů."
            } else {
                "Correct, " + player.name + "! +" + points + " points."
            }

            battlePhase = BattlePhase.RESULT
            speak(statusText)
        } else {
            player.streak = 0
            player.score -= 5
            player.lockedOut = true

            statusText = if (language == TriviaGameEngine.Language.CS) {
                "Špatně, " + player.name + ". Minus 5. Ostatní mohou buzzovat."
            } else {
                "Wrong, " + player.name + ". Minus 5. Others can buzz."
            }

            battleLockedPlayerId = null

            if (players.all { it.lockedOut }) {
                battlePhase = BattlePhase.RESULT
                statusText += if (language == TriviaGameEngine.Language.CS) {
                    " Správná odpověď: " + displayAnswer(q)
                } else {
                    " Correct answer: " + displayAnswer(q)
                }
                speak(statusText)
            } else {
                battlePhase = BattlePhase.BUZZ
                speak(statusText, listenAfter = true)
            }
        }
    }

    private fun manualBuzz(player: Player) {
        if (mode != Mode.BATTLE || battlePhase != BattlePhase.BUZZ || player.lockedOut) return
        recognizer?.cancel()
        listening = false
        battleLockedPlayerId = player.id
        battlePhase = BattlePhase.ANSWER
        statusText = player.name + " buzz!"
        speak(
            if (language == TriviaGameEngine.Language.CS) {
                player.name + ", odpovídej."
            } else {
                player.name + ", answer now."
            },
            listenAfter = true
        )
    }

    private fun findBuzzPlayer(candidates: List<String>): Pair<Player, String>? {
        for (candidate in candidates) {
            val normalized = normalize(candidate)
            for (player in players.filterNot { it.lockedOut }) {
                val playerName = normalize(player.name)
                if (normalized == playerName) {
                    return player to ""
                }

                if (normalized.startsWith(playerName + " ")) {
                    return player to normalized.removePrefix(playerName).trim()
                }

                val idx = normalized.indexOf(playerName)
                if (idx >= 0) {
                    val before = normalized.substring(0, idx).trim()
                    val after = normalized.substring(idx + playerName.length).trim()
                    val remainder = (before + " " + after).trim()
                    return player to remainder
                }
            }
        }
        return null
    }

    private fun pickQuestion(): TriviaQuestionBank.LocalizedQuestion {
        val pool = TriviaQuestionBank.questions
            .filter { it.difficulty <= 4 }
            .filterNot { usedQuestionIds.contains(it.id) }

        val q = pool.randomOrNull()
            ?: TriviaQuestionBank.questions.random()

        usedQuestionIds += q.id
        return q
    }

    private fun localizedPrompt(q: TriviaQuestionBank.LocalizedQuestion): String =
        if (language == TriviaGameEngine.Language.CS) q.promptCs else q.promptEn

    private fun displayAnswer(q: TriviaQuestionBank.LocalizedQuestion): String =
        (if (language == TriviaGameEngine.Language.CS) q.answersCs else q.answersEn)
            .firstOrNull()
            .orEmpty()

    private fun isCorrectAnswer(
        candidate: String,
        q: TriviaQuestionBank.LocalizedQuestion
    ): Boolean {
        val actual = normalize(candidate)
        val accepted = if (language == TriviaGameEngine.Language.CS) {
            q.answersCs
        } else {
            q.answersEn
        }

        return accepted.any { expected ->
            val normalizedExpected = normalize(expected)
            actual == normalizedExpected ||
                (normalizedExpected.length >= 4 && actual.contains(normalizedExpected))
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(
            value.lowercase(language.locale),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun addPlayer(name: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank() || players.size >= 6) return
        if (players.any { normalize(it.name) == normalize(cleaned) }) return

        players += Player(
            id = System.currentTimeMillis(),
            name = cleaned
        )
        savePlayers()
    }

    private fun removePlayer(id: Long) {
        players.removeAll { it.id == id }
        savePlayers()
    }

    private fun savePlayers() {
        prefs.edit()
            .putString(
                "players",
                players.joinToString("|") { it.name.replace("|", "") }
            )
            .apply()
    }

    private fun loadPlayers() {
        val saved = prefs.getString("players", "")
            .orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        players.clear()
        saved.forEachIndexed { index, name ->
            players += Player(
                id = index.toLong() + 1L,
                name = name
            )
        }
    }

    private fun resetScores() {
        players.forEach {
            it.score = 0
            it.streak = 0
            it.bestStreak = 0
            it.lockedOut = false
        }
        questionIndex = 0
    }

    @Composable
    private fun FamilyGameScreen() {
        BackHandler(enabled = mode != Mode.MENU && mode != Mode.SETUP) {
            recognizer?.cancel()
            listening = false
            mode = Mode.MENU
        }

        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF080A0D)
            ) {
                when (mode) {
                    Mode.SETUP -> PlayerSetupScreen()
                    Mode.MENU -> FamilyMenuScreen()
                    Mode.ROUNDS -> RoundsScreen()
                    Mode.BATTLE -> BattleScreen()
                }
            }
        }
    }

    @Composable
    private fun PlayerSetupScreen() {
        var name by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Family Game • Players",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (language == TriviaGameEngine.Language.CS) {
                    "Přidej 2 až 6 hráčů. Krátká a odlišná jména fungují pro hlasový buzz nejlépe."
                } else {
                    "Add 2–6 players. Short, distinct names work best for voice buzz."
                },
                color = Color(0xFF9AA4B2)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (language == TriviaGameEngine.Language.CS) "Jméno" else "Name") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        addPlayer(name)
                        name = ""
                    }
                ) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Přidat" else "Add")
                }
            }

            players.forEachIndexed { index, player ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF171B22),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (index + 1).toString() + ". " + player.name,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Button(
                            onClick = { removePlayer(player.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF372126)
                            )
                        ) {
                            Text(if (language == TriviaGameEngine.Language.CS) "Smazat" else "Remove")
                        }
                    }
                }
            }

            Button(
                enabled = players.size >= 2,
                onClick = {
                    savePlayers()
                    mode = Mode.MENU
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (language == TriviaGameEngine.Language.CS) "Hotovo" else "Done")
            }

            if (players.size < 2) {
                Text(
                    if (language == TriviaGameEngine.Language.CS) {
                        "Potřebuješ minimálně 2 hráče."
                    } else {
                        "You need at least 2 players."
                    },
                    color = Color(0xFFB6A389)
                )
            }
        }
    }

    @Composable
    private fun FamilyMenuScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Family Game",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        players.joinToString(" • ") { it.name },
                        color = Color(0xFF9AA4B2)
                    )
                }

                Button(onClick = { mode = Mode.SETUP }) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Hráči" else "Players")
                }
            }

            ModeCard(
                title = if (language == TriviaGameEngine.Language.CS) "Kola" else "Rounds",
                subtitle = if (language == TriviaGameEngine.Language.CS) {
                    "Každý dostane vlastní otázku. 10 bodů za správnou + bonus za sérii."
                } else {
                    "Each player gets a question. 10 points for correct + streak bonus."
                },
                onClick = { startRounds() }
            )

            ModeCard(
                title = "Battle",
                subtitle = if (language == TriviaGameEngine.Language.CS) {
                    "Otázka pro všechny. Řekni svoje jméno jako buzz. Správně +20, špatně −5."
                } else {
                    "Question for everyone. Say your name to buzz. Correct +20, wrong −5."
                },
                onClick = { startBattle() }
            )

            Text(
                if (language == TriviaGameEngine.Language.CS) {
                    "Battle tip: můžeš říct jen „Petr“ a potom odpověď, nebo rovnou „Petr, Napoleon“."
                } else {
                    "Battle tip: say only “Petr” then answer, or say “Petr, Napoleon” in one phrase."
                },
                color = Color(0xFF8F99A8)
            )

            Button(onClick = { finish() }) {
                Text(if (language == TriviaGameEngine.Language.CS) "Zavřít" else "Close")
            }
        }
    }

    @Composable
    private fun RoundsScreen() {
        GameHeader(
            title = if (language == TriviaGameEngine.Language.CS) "Family • Kola" else "Family • Rounds"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(54.dp))
            Scoreboard()

            val player = players.getOrNull(roundPlayerIndex)
            Text(
                player?.name.orEmpty(),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                currentQuestion?.let { localizedPrompt(it) }.orEmpty(),
                color = Color(0xFFE9EEF4),
                fontSize = 26.sp,
                lineHeight = 33.sp
            )

            Text(
                statusText,
                color = Color(0xFFAAB5C3),
                fontSize = 18.sp
            )

            if (heardText.isNotBlank()) {
                Text(
                    "STT: " + heardText,
                    color = Color(0xFF727D8B),
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { startListening() }) {
                    Text(if (listening) "Listening…" else "Listen")
                }
                Button(onClick = { nextRoundsQuestion() }) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Další" else "Next")
                }
                Button(onClick = { mode = Mode.MENU }) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Konec" else "End")
                }
            }
        }
    }

    @Composable
    private fun BattleScreen() {
        GameHeader("Family • Battle")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(54.dp))
            Scoreboard()

            Text(
                currentQuestion?.let { localizedPrompt(it) }.orEmpty(),
                color = Color(0xFFE9EEF4),
                fontSize = 27.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                statusText,
                color = if (battlePhase == BattlePhase.BUZZ) {
                    Color(0xFFBDE5FF)
                } else {
                    Color(0xFFAAB5C3)
                },
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium
            )

            if (battlePhase == BattlePhase.BUZZ) {
                Text(
                    if (language == TriviaGameEngine.Language.CS) {
                        "Ruční buzz:"
                    } else {
                        "Manual buzz:"
                    },
                    color = Color(0xFF8F99A8)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    players.take(3).forEach { player ->
                        Button(
                            enabled = !player.lockedOut,
                            onClick = { manualBuzz(player) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(player.name)
                        }
                    }
                }

                if (players.size > 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        players.drop(3).forEach { player ->
                            Button(
                                enabled = !player.lockedOut,
                                onClick = { manualBuzz(player) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(player.name)
                            }
                        }
                    }
                }
            }

            if (heardText.isNotBlank()) {
                Text(
                    "STT: " + heardText,
                    color = Color(0xFF727D8B),
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { startListening() }) {
                    Text(if (listening) "Listening…" else "Listen")
                }
                Button(onClick = { nextBattleQuestion() }) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Další otázka" else "Next question")
                }
                Button(onClick = { mode = Mode.MENU }) {
                    Text(if (language == TriviaGameEngine.Language.CS) "Konec" else "End")
                }
            }
        }
    }

    @Composable
    private fun GameHeader(title: String) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Color(0xFF11151B))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    private fun Scoreboard() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            players.forEach { player ->
                Surface(
                    modifier = Modifier.weight(1f),
                    color = if (player.id == battleLockedPlayerId) {
                        Color(0xFF243743)
                    } else {
                        Color(0xFF171B22)
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            player.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            player.score.toString(),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "🔥 " + player.streak,
                            color = Color(0xFF929DAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ModeCard(
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = Color(0xFF171B22),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    color = Color(0xFFAFB7C2),
                    fontSize = 16.sp
                )
            }
        }
    }
}
