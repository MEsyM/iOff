package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

class FamilyGameEngine(context: Context) {

    enum class Mode { ROUND, BATTLE }

    enum class Difficulty(val minQuestionDifficulty: Int, val maxQuestionDifficulty: Int) {
        KIDS(1, 1),
        EASY(1, 2),
        NORMAL(2, 3),
        HARD(3, 4),
        EXPERT(4, 5);

        fun labelCs(): String = when (this) {
            KIDS -> "Děti"
            EASY -> "Lehká"
            NORMAL -> "Normální"
            HARD -> "Těžká"
            EXPERT -> "Expert"
        }

        fun labelEn(): String = when (this) {
            KIDS -> "Kids"
            EASY -> "Easy"
            NORMAL -> "Normal"
            HARD -> "Hard"
            EXPERT -> "Expert"
        }
    }

    data class Player(
        val id: Int,
        val name: String,
        val difficulty: Difficulty,
        val adaptiveOffset: Int,
        val effectiveMinDifficulty: Int,
        val effectiveMaxDifficulty: Int,
        val score: Int,
        val streak: Int,
        val bestStreak: Int
    )

    data class Question(
        val id: String,
        val difficulty: Int,
        val categoryName: String,
        val prompt: String,
        val answers: List<String>,
        val explanation: String,
        val value: Int
    )

    data class AnswerResult(
        val player: Player,
        val correct: Boolean,
        val expected: String,
        val explanation: String,
        val pointsDelta: Int,
        val newScore: Int,
        val streak: Int,
        val questionResolved: Boolean,
        val sessionFinished: Boolean,
        val remainingBuzzers: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var currentQuestionId: String? = prefs.getString(KEY_QUESTION_ID, null)
    private var questionsAnswered = prefs.getInt(KEY_QUESTIONS_ANSWERED, 0)
    private var turnIndex = prefs.getInt(KEY_TURN_INDEX, 0)
    private var lockedPlayerId: Int? = null
    private val excludedBuzzers = mutableSetOf<Int>()

    fun mode(): Mode = runCatching {
        Mode.valueOf(prefs.getString(KEY_MODE, Mode.ROUND.name) ?: Mode.ROUND.name)
    }.getOrDefault(Mode.ROUND)

    fun setMode(mode: Mode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        resetSession()
    }

    fun playerNames(): List<String> {
        val saved = prefs.getString(KEY_PLAYERS, null)
            ?.split(PLAYER_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return if (saved.size >= 2) saved.take(MAX_PLAYERS) else listOf("Petr", "Player 2")
    }

    fun playerDifficulties(): List<Difficulty> {
        val saved = prefs.getString(KEY_DIFFICULTIES, "")
            ?.split(",")
            ?.map { raw -> runCatching { Difficulty.valueOf(raw) }.getOrDefault(Difficulty.NORMAL) }
            .orEmpty()
        return playerNames().indices.map { index -> saved.getOrNull(index) ?: Difficulty.NORMAL }
    }

    fun savePlayers(names: List<String>, difficulties: List<Difficulty> = emptyList()) {
        val configured = names.mapIndexedNotNull { index, rawName ->
            val cleanName = rawName.trim()
            if (cleanName.isBlank()) null
            else Triple(index, cleanName, difficulties.getOrNull(index) ?: Difficulty.NORMAL)
        }
            .distinctBy { normalize(it.second) }
            .take(MAX_PLAYERS)

        if (configured.size < 2) return

        val cleanNames = configured.map { it.second }
        val cleanDifficulties = configured.map { it.third }

        val editor = prefs.edit()
            .putString(KEY_PLAYERS, cleanNames.joinToString(PLAYER_SEPARATOR))
            .putString(KEY_DIFFICULTIES, cleanDifficulties.joinToString(",") { it.name })

        scoreKeys().forEach { editor.remove(it) }
        streakKeys().forEach { editor.remove(it) }
        bestStreakKeys().forEach { editor.remove(it) }
        editor.apply()
        resetSession()
    }

    fun players(): List<Player> {
        val difficulties = playerDifficulties()
        return playerNames().mapIndexed { index, name ->
            val difficulty = difficulties.getOrNull(index) ?: Difficulty.NORMAL
            val offset = prefs.getInt(adaptiveOffsetKey(index), 0)
                .coerceIn(MIN_ADAPTIVE_OFFSET, MAX_ADAPTIVE_OFFSET)
            val range = effectiveDifficultyRange(difficulty, offset)

            Player(
                id = index,
                name = name,
                difficulty = difficulty,
                adaptiveOffset = offset,
                effectiveMinDifficulty = range.first,
                effectiveMaxDifficulty = range.last,
                score = prefs.getInt(scoreKey(index), 0),
                streak = prefs.getInt(streakKey(index), 0),
                bestStreak = prefs.getInt(bestStreakKey(index), 0)
            )
        }

    fun currentPlayer(): Player {
        val all = players()
        if (all.isEmpty()) throw IllegalStateException("Family game needs at least two players")
        turnIndex %= all.size
        return all[turnIndex]
    }

    fun lockedPlayer(): Player? = lockedPlayerId?.let { id -> players().firstOrNull { it.id == id } }

    fun startOrResume(language: TriviaGameEngine.Language): Question {
        if (questionsAnswered >= sessionSize()) resetSession(keepScores = true)
        return currentQuestion(language) ?: nextQuestion(language)
    }

    fun currentQuestion(language: TriviaGameEngine.Language): Question? {
        val raw = currentQuestionId?.let { id -> TriviaQuestionBank.questions.firstOrNull { it.id == id } }
            ?: return null
        return localize(raw, language)
    }

    fun nextQuestion(language: TriviaGameEngine.Language): Question {
        lockedPlayerId = null
        excludedBuzzers.clear()

        val recent = prefs.getString(KEY_RECENT_IDS, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val pool = TriviaQuestionBank.questions.filterNot { it.id in recent }.ifEmpty {
            TriviaQuestionBank.questions
        }
        val preferredDifficulty = if (mode() == Mode.ROUND) {
            val player = currentPlayer()
            player.effectiveMinDifficulty..player.effectiveMaxDifficulty
        } else {
            battleDifficultyRange()
        }

        val chosen = pool
            .filter { it.difficulty in preferredDifficulty }
            .ifEmpty { pool }
            .random()
        currentQuestionId = chosen.id

        val updatedRecent = (recent + chosen.id).takeLast(RECENT_WINDOW)
        prefs.edit()
            .putString(KEY_QUESTION_ID, chosen.id)
            .putString(KEY_RECENT_IDS, updatedRecent.joinToString(","))
            .apply()

        return localize(chosen, language)
    }

    fun difficultySummary(language: TriviaGameEngine.Language): String =
        players().joinToString(" • ") { player ->
            val label = if (language == TriviaGameEngine.Language.CS) {
                player.difficulty.labelCs()
            } else {
                player.difficulty.labelEn()
            }
            val adaptive = when {
                player.adaptiveOffset > 0 -> " +" + player.adaptiveOffset
                player.adaptiveOffset < 0 -> " " + player.adaptiveOffset
                else -> ""
            }
            player.name + ": " + label + adaptive
        }

    private fun battleDifficultyRange(): IntRange {
        val levels = players().map { player ->
            val base = when (player.difficulty) {
                Difficulty.KIDS -> 1
                Difficulty.EASY -> 2
                Difficulty.NORMAL -> 3
                Difficulty.HARD -> 4
                Difficulty.EXPERT -> 5
            }
            (base + player.adaptiveOffset).coerceIn(1, 5)
        }

        if (levels.isEmpty()) return 2..3

        val sorted = levels.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0)
                .toInt()
                .coerceIn(1, 5)
        }

        return (median - 1).coerceAtLeast(1)..(median + 1).coerceAtMost(5)
    }

    fun buzz(candidates: List<String>): Player? {
        if (mode() != Mode.BATTLE || lockedPlayerId != null) return lockedPlayer()
        val available = players().filterNot { it.id in excludedBuzzers }
        val found = candidates.asSequence()
            .map(::normalize)
            .flatMap { spoken -> available.asSequence().map { it to spoken } }
            .firstOrNull { (player, spoken) -> nameMatches(spoken, normalize(player.name)) }
            ?.first
        if (found != null) lockedPlayerId = found.id
        return found
    }

    fun answerRound(candidates: List<String>, language: TriviaGameEngine.Language): AnswerResult {
        val player = currentPlayer()
        val question = currentQuestion(language) ?: startOrResume(language)
        val correct = answerMatches(candidates, question.answers)

        val oldStreak = player.streak
        val newStreak = if (correct) oldStreak + 1 else 0
        val streakBonus = if (correct) ((newStreak - 1).coerceAtLeast(0) * ROUND_STREAK_BONUS).coerceAtMost(ROUND_STREAK_CAP) else 0
        val delta = if (correct) ROUND_BASE_POINTS + streakBonus else 0
        val newScore = player.score + delta

        persistPlayer(player.id, newScore, newStreak, max(player.bestStreak, newStreak))
        updateAdaptiveDifficulty(player.id, correct)
        finishQuestion()
        turnIndex = (turnIndex + 1) % players().size
        prefs.edit().putInt(KEY_TURN_INDEX, turnIndex).apply()

        return AnswerResult(
            player = players().first { it.id == player.id },
            correct = correct,
            expected = question.answers.firstOrNull().orEmpty(),
            explanation = question.explanation,
            pointsDelta = delta,
            newScore = newScore,
            streak = newStreak,
            questionResolved = true,
            sessionFinished = questionsAnswered >= sessionSize(),
            remainingBuzzers = 0
        )
    }

    fun answerBattle(candidates: List<String>, language: TriviaGameEngine.Language): AnswerResult {
        val player = lockedPlayer() ?: throw IllegalStateException("No player buzzed in")
        val question = currentQuestion(language) ?: startOrResume(language)
        val correct = answerMatches(candidates, question.answers)

        val newStreak = if (correct) player.streak + 1 else 0
        val streakBonus = if (correct) ((newStreak - 1).coerceAtLeast(0) * BATTLE_STREAK_BONUS).coerceAtMost(BATTLE_STREAK_CAP) else 0
        val delta = if (correct) question.value + streakBonus else -question.value
        val newScore = player.score + delta
        persistPlayer(player.id, newScore, newStreak, max(player.bestStreak, newStreak))
        updateAdaptiveDifficulty(player.id, correct)

        if (correct) {
            finishQuestion()
            lockedPlayerId = null
            excludedBuzzers.clear()
        } else {
            excludedBuzzers += player.id
            lockedPlayerId = null
            if (excludedBuzzers.size >= players().size) {
                finishQuestion()
                excludedBuzzers.clear()
            }
        }

        return AnswerResult(
            player = players().first { it.id == player.id },
            correct = correct,
            expected = question.answers.firstOrNull().orEmpty(),
            explanation = question.explanation,
            pointsDelta = delta,
            newScore = newScore,
            streak = newStreak,
            questionResolved = correct || currentQuestionId == null,
            sessionFinished = questionsAnswered >= sessionSize(),
            remainingBuzzers = (players().size - excludedBuzzers.size).coerceAtLeast(0)
        )
    }

    fun skip(language: TriviaGameEngine.Language): Boolean {
        currentQuestion(language) ?: return false
        finishQuestion()
        if (mode() == Mode.ROUND) {
            turnIndex = (turnIndex + 1) % players().size
            prefs.edit().putInt(KEY_TURN_INDEX, turnIndex).apply()
        }
        lockedPlayerId = null
        excludedBuzzers.clear()
        return questionsAnswered >= sessionSize()
    }

    fun scoreboard(): String = players()
        .sortedByDescending { it.score }
        .joinToString(" • ") { it.name + " " + it.score }

    fun sessionSize(): Int = if (mode() == Mode.ROUND) players().size * ROUNDS_PER_PLAYER else BATTLE_QUESTIONS

    fun resetSession(keepScores: Boolean = false) {
        currentQuestionId = null
        questionsAnswered = 0
        turnIndex = 0
        lockedPlayerId = null
        excludedBuzzers.clear()

        val editor = prefs.edit()
            .remove(KEY_QUESTION_ID)
            .putInt(KEY_QUESTIONS_ANSWERED, 0)
            .putInt(KEY_TURN_INDEX, 0)

        if (!keepScores) {
            players().forEach { player ->
                editor.putInt(scoreKey(player.id), 0)
                editor.putInt(streakKey(player.id), 0)
                editor.putInt(bestStreakKey(player.id), 0)
            }
        } else {
            players().forEach { player -> editor.putInt(streakKey(player.id), 0) }
        }

        players().forEach { player ->
            editor.putInt(adaptiveOffsetKey(player.id), 0)
            editor.putInt(adaptiveCorrectRunKey(player.id), 0)
            editor.putInt(adaptiveWrongRunKey(player.id), 0)
        }
        editor.apply()
    }

    private fun effectiveDifficultyRange(
        difficulty: Difficulty,
        adaptiveOffset: Int
    ): IntRange {
        val shift = adaptiveOffset.coerceIn(MIN_ADAPTIVE_OFFSET, MAX_ADAPTIVE_OFFSET)
        val min = (difficulty.minQuestionDifficulty + shift).coerceIn(1, 5)
        val max = (difficulty.maxQuestionDifficulty + shift).coerceIn(1, 5)
        return min.coerceAtMost(max)..max.coerceAtLeast(min)
    }

    private fun updateAdaptiveDifficulty(playerId: Int, correct: Boolean) {
        var offset = prefs.getInt(adaptiveOffsetKey(playerId), 0)
            .coerceIn(MIN_ADAPTIVE_OFFSET, MAX_ADAPTIVE_OFFSET)
        var correctRun = prefs.getInt(adaptiveCorrectRunKey(playerId), 0)
        var wrongRun = prefs.getInt(adaptiveWrongRunKey(playerId), 0)

        if (correct) {
            correctRun += 1
            wrongRun = 0

            if (correctRun >= CORRECTS_TO_LEVEL_UP) {
                offset = (offset + 1).coerceAtMost(MAX_ADAPTIVE_OFFSET)
                correctRun = 0
            }
        } else {
            wrongRun += 1
            correctRun = 0

            if (wrongRun >= WRONGS_TO_LEVEL_DOWN) {
                offset = (offset - 1).coerceAtLeast(MIN_ADAPTIVE_OFFSET)
                wrongRun = 0
            }
        }

        prefs.edit()
            .putInt(adaptiveOffsetKey(playerId), offset)
            .putInt(adaptiveCorrectRunKey(playerId), correctRun)
            .putInt(adaptiveWrongRunKey(playerId), wrongRun)
            .apply()
    }

    private fun finishQuestion() {
        questionsAnswered += 1
        currentQuestionId = null
        prefs.edit()
            .remove(KEY_QUESTION_ID)
            .putInt(KEY_QUESTIONS_ANSWERED, questionsAnswered)
            .apply()
    }

    private fun persistPlayer(id: Int, score: Int, streak: Int, best: Int) {
        prefs.edit()
            .putInt(scoreKey(id), score)
            .putInt(streakKey(id), streak)
            .putInt(bestStreakKey(id), best)
            .apply()
    }

    private fun localize(raw: TriviaQuestionBank.LocalizedQuestion, language: TriviaGameEngine.Language): Question {
        val cs = language == TriviaGameEngine.Language.CS
        return Question(
            id = raw.id,
            difficulty = raw.difficulty,
            categoryName = categoryName(raw.category, cs),
            prompt = if (cs) raw.promptCs else raw.promptEn,
            answers = if (cs) raw.answersCs else raw.answersEn,
            explanation = if (cs) raw.explanationCs else raw.explanationEn,
            value = when (raw.difficulty) {
                1, 2 -> 100
                3, 4 -> 200
                else -> 300
            }
        )
    }

    private fun categoryName(category: TriviaQuestionBank.Category, cs: Boolean): String = when (category) {
        TriviaQuestionBank.Category.GEOGRAPHY -> if (cs) "Geografie" else "Geography"
        TriviaQuestionBank.Category.SCIENCE -> if (cs) "Věda" else "Science"
        TriviaQuestionBank.Category.HISTORY -> if (cs) "Historie" else "History"
        TriviaQuestionBank.Category.GENERAL -> if (cs) "Obecné" else "General"
        TriviaQuestionBank.Category.TECHNOLOGY -> if (cs) "Technologie" else "Technology"
        TriviaQuestionBank.Category.NATURE -> if (cs) "Příroda" else "Nature"
        TriviaQuestionBank.Category.SPORTS -> if (cs) "Sport" else "Sports"
        TriviaQuestionBank.Category.CULTURE -> if (cs) "Kultura" else "Culture"
        TriviaQuestionBank.Category.MOVIES -> if (cs) "Filmy" else "Movies"
        TriviaQuestionBank.Category.CARS -> if (cs) "Auta" else "Cars"
        TriviaQuestionBank.Category.NUMBERS -> if (cs) "Čísla" else "Numbers"
    }

    private fun answerMatches(candidates: List<String>, answers: List<String>): Boolean {
        val normalizedAnswers = answers.map(::normalize)
        return candidates.any { candidate ->
            val actual = normalize(candidate)
            normalizedAnswers.any { expected ->
                actual == expected || actual.contains(expected) || expected.contains(actual)
            }
        }
    }

    private fun nameMatches(spoken: String, name: String): Boolean {
        if (spoken == name) return true
        val words = spoken.split(" ")
        if (name in words) return true
        return spoken == "ja " + name || spoken == "jsem " + name || spoken == "i am " + name || spoken == "im " + name
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun scoreKey(id: Int) = "score_" + id
    private fun streakKey(id: Int) = "streak_" + id
    private fun bestStreakKey(id: Int) = "best_streak_" + id
    private fun adaptiveOffsetKey(id: Int) = "adaptive_offset_" + id
    private fun adaptiveCorrectRunKey(id: Int) = "adaptive_correct_run_" + id
    private fun adaptiveWrongRunKey(id: Int) = "adaptive_wrong_run_" + id
    private fun scoreKeys() = (0 until MAX_PLAYERS).map(::scoreKey)
    private fun streakKeys() = (0 until MAX_PLAYERS).map(::streakKey)
    private fun bestStreakKeys() = (0 until MAX_PLAYERS).map(::bestStreakKey)

    companion object {
        private const val PREFS_NAME = "family_game_v1"
        private const val KEY_PLAYERS = "players"
        private const val KEY_DIFFICULTIES = "difficulties"
        private const val KEY_MODE = "mode"
        private const val KEY_QUESTION_ID = "question_id"
        private const val KEY_QUESTIONS_ANSWERED = "questions_answered"
        private const val KEY_TURN_INDEX = "turn_index"
        private const val KEY_RECENT_IDS = "recent_ids"
        private const val PLAYER_SEPARATOR = "|||"
        private const val MAX_PLAYERS = 6
        private const val ROUNDS_PER_PLAYER = 5
        private const val BATTLE_QUESTIONS = 15
        private const val RECENT_WINDOW = 30
        private const val ROUND_BASE_POINTS = 100
        private const val ROUND_STREAK_BONUS = 25
        private const val ROUND_STREAK_CAP = 150
        private const val BATTLE_STREAK_BONUS = 20
        private const val BATTLE_STREAK_CAP = 100

        private const val CORRECTS_TO_LEVEL_UP = 3
        private const val WRONGS_TO_LEVEL_DOWN = 2
        private const val MIN_ADAPTIVE_OFFSET = -2
        private const val MAX_ADAPTIVE_OFFSET = 2
    }
}
