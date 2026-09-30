package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TriviaGameEngine(context: Context) {

    enum class Language(val code: String, val locale: Locale) {
        EN("en", Locale.US),
        CS("cs", Locale("cs", "CZ"));

        companion object {
            fun fromCode(code: String?): Language =
                entries.firstOrNull { it.code == code } ?: EN
        }
    }

    data class CategoryStat(
        val category: TriviaQuestionBank.Category,
        val answered: Int,
        val correct: Int,
        val accuracy: Int,
        val rating: Int,
        val averageResponseMs: Long
    )

    data class Profile(
        val level: Int,
        val levelName: String,
        val xp: Int,
        val xpForNextLevel: Int?,
        val totalAnswered: Int,
        val totalCorrect: Int,
        val accuracy: Int,
        val bestStreak: Int,
        val currentStreak: Int,
        val roundsCompleted: Int,
        val achievements: Set<String>,
        val language: Language,
        val categoryStats: List<CategoryStat>
    )

    data class LocalizedQuestion(
        val id: String,
        val difficulty: Int,
        val category: TriviaQuestionBank.Category,
        val categoryName: String,
        val prompt: String,
        val answers: List<String>,
        val explanation: String
    )

    data class AnswerResult(
        val correct: Boolean,
        val expected: String,
        val explanation: String,
        val xpEarned: Int,
        val bonusXp: Int,
        val totalXp: Int,
        val level: Int,
        val levelName: String,
        val streak: Int,
        val roundAnswered: Int,
        val roundCorrect: Int,
        val roundXp: Int,
        val roundFinished: Boolean,
        val perfectRound: Boolean,
        val promoted: Boolean,
        val newlyUnlockedAchievements: List<String>,
        val responseMs: Long
    )

    data class SkipResult(
        val roundAnswered: Int,
        val roundFinished: Boolean
    )

    data class RoundSummary(
        val correct: Int,
        val answered: Int,
        val accuracy: Int,
        val xpEarned: Int,
        val bestStreak: Int,
        val perfect: Boolean,
        val totalXp: Int,
        val level: Int,
        val levelName: String,
        val nextRoundAdjustment: String
    )

    data class RoundHistoryEntry(
        val roundNumber: Int,
        val correct: Int,
        val answered: Int,
        val accuracy: Int,
        val xpEarned: Int,
        val totalXp: Int,
        val bestStreak: Int,
        val completedAt: Long
    )

    data class MissedQuestionStat(
        val id: String,
        val prompt: String,
        val category: TriviaQuestionBank.Category,
        val wrongCount: Int,
        val seenCount: Int,
        val accuracy: Int
    )

    data class DashboardData(
        val rounds: List<RoundHistoryEntry>,
        val xpProgression: List<Pair<Int, Int>>,
        val categoryStats: List<CategoryStat>,
        val missedQuestions: List<MissedQuestionStat>
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var sessionStreak = prefs.getInt(KEY_CURRENT_STREAK, 0)
    private val recentIds = ArrayDeque<String>().apply {
        prefs.getString(KEY_RECENT_IDS, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.forEach { addLast(it) }
    }

    private var currentQuestionId: String? = prefs.getString(KEY_CURRENT_QUESTION_ID, null)
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)
    private var roundXp = prefs.getInt(KEY_ROUND_XP, 0)
    private var roundBestStreak = prefs.getInt(KEY_ROUND_BEST_STREAK, 0)
    private var lastCategoryKey = prefs.getString(KEY_LAST_CATEGORY, null)

    fun language(): Language =
        Language.fromCode(prefs.getString(KEY_LANGUAGE, Language.EN.code))

    fun setLanguage(language: Language) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    fun profile(): Profile {
        val xp = prefs.getInt(KEY_XP, 0)
        val totalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0)
        val totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        val level = levelForXp(xp)

        return Profile(
            level = level,
            levelName = levelName(level),
            xp = xp,
            xpForNextLevel = nextLevelXp(level),
            totalAnswered = totalAnswered,
            totalCorrect = totalCorrect,
            accuracy = percent(totalCorrect, totalAnswered),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            currentStreak = sessionStreak,
            roundsCompleted = prefs.getInt(KEY_ROUNDS_COMPLETED, 0),
            achievements = unlockedAchievements(),
            language = language(),
            categoryStats = TriviaQuestionBank.Category.entries.map { categoryStat(it) }
        )
    }

    fun startOrResumeRound(): LocalizedQuestion {
        if (roundAnswered >= ROUND_SIZE) {
            resetRound()
        }
        return currentQuestion() ?: nextQuestion()
    }

    fun currentQuestion(): LocalizedQuestion? {
        val id = currentQuestionId ?: return null
        val raw = TriviaQuestionBank.questions.firstOrNull { it.id == id } ?: return null
        return localize(raw)
    }

    fun nextQuestion(): LocalizedQuestion {
        val raw = selectNextQuestion()
        currentQuestionId = raw.id
        lastCategoryKey = raw.category.key

        recentIds.addLast(raw.id)
        while (recentIds.size > RECENT_WINDOW) {
            recentIds.removeFirst()
        }

        prefs.edit()
            .putString(KEY_CURRENT_QUESTION_ID, raw.id)
            .putString(KEY_RECENT_IDS, recentIds.joinToString(","))
            .putString(KEY_LAST_CATEGORY, raw.category.key)
            .apply()

        return localize(raw)
    }

    fun answer(rawAnswer: String, responseMs: Long): AnswerResult =
        answerCandidates(listOf(rawAnswer), responseMs)

    fun answerCandidates(candidates: List<String>, responseMs: Long): AnswerResult {
        val question = currentQuestion()
            ?: throw IllegalStateException("No active trivia question")

        val before = profile()
        val usable = candidates.filter { it.isNotBlank() }
        val matchedCandidate = usable.firstOrNull { candidate ->
            val normalizedCandidate = normalize(candidate)
            question.answers.any { accepted ->
                answerMatches(normalizedCandidate, normalize(accepted))
            }
        }

        val chosenAnswer = matchedCandidate ?: usable.firstOrNull().orEmpty()
        val normalizedActual = normalize(chosenAnswer)
        val correct = question.answers.any { accepted ->
            answerMatches(normalizedActual, normalize(accepted))
        }

        val newStreak = if (correct) sessionStreak + 1 else 0
        sessionStreak = newStreak
        val baseXp = if (correct) xpForDifficulty(question.difficulty) else WRONG_ANSWER_XP
        val streakBonus = if (correct) streakBonus(newStreak) else 0

        roundAnswered += 1
        if (correct) roundCorrect += 1
        roundBestStreak = max(roundBestStreak, newStreak)

        val perfectRound = roundAnswered >= ROUND_SIZE && roundCorrect == ROUND_SIZE
        val perfectBonus = if (perfectRound) PERFECT_ROUND_BONUS else 0
        val earned = baseXp + streakBonus + perfectBonus
        roundXp += earned

        val newXp = before.xp + earned
        val newLevel = levelForXp(newXp)
        val promoted = newLevel > before.level

        val totalAnswered = before.totalAnswered + 1
        val totalCorrect = before.totalCorrect + if (correct) 1 else 0

        updateQuestionStats(
            question = question,
            correct = correct,
            responseMs = responseMs,
            totalAnsweredAfter = totalAnswered
        )
        updateCategoryStats(question.category, correct, responseMs)

        val roundFinished = roundAnswered >= ROUND_SIZE
        val roundsCompleted = before.roundsCompleted + if (roundFinished) 1 else 0
        if (roundFinished) {
            appendRoundHistory(
                roundNumber = roundsCompleted,
                correct = roundCorrect,
                answered = roundAnswered,
                xpEarned = roundXp,
                totalXp = newXp,
                bestStreak = roundBestStreak
            )
        }
        currentQuestionId = null

        prefs.edit()
            .putInt(KEY_XP, newXp)
            .putInt(KEY_TOTAL_ANSWERED, totalAnswered)
            .putInt(KEY_TOTAL_CORRECT, totalCorrect)
            .putInt(KEY_CURRENT_STREAK, newStreak)
            .putInt(KEY_BEST_STREAK, max(before.bestStreak, newStreak))
            .putInt(KEY_ROUNDS_COMPLETED, roundsCompleted)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .putInt(KEY_ROUND_XP, roundXp)
            .putInt(KEY_ROUND_BEST_STREAK, roundBestStreak)
            .remove(KEY_CURRENT_QUESTION_ID)
            .apply()

        val unlockedNow = evaluateAchievements(
            totalAnswered = totalAnswered,
            totalCorrect = totalCorrect,
            bestStreak = max(before.bestStreak, newStreak),
            roundsCompleted = roundsCompleted,
            perfectRound = perfectRound
        )

        return AnswerResult(
            correct = correct,
            expected = question.answers.first(),
            explanation = question.explanation,
            xpEarned = baseXp + streakBonus,
            bonusXp = perfectBonus,
            totalXp = newXp,
            level = newLevel,
            levelName = levelName(newLevel),
            streak = newStreak,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            roundXp = roundXp,
            roundFinished = roundFinished,
            perfectRound = perfectRound,
            promoted = promoted,
            newlyUnlockedAchievements = unlockedNow,
            responseMs = responseMs
        )
    }

    fun skipCurrent(): SkipResult {
        roundAnswered += 1
        sessionStreak = 0
        currentQuestionId = null
        val finished = roundAnswered >= ROUND_SIZE
        val currentRounds = prefs.getInt(KEY_ROUNDS_COMPLETED, 0)

        val editor = prefs.edit()
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_CURRENT_STREAK, 0)
            .remove(KEY_CURRENT_QUESTION_ID)

        if (finished) {
            val completedRound = currentRounds + 1
            editor.putInt(KEY_ROUNDS_COMPLETED, completedRound)
            appendRoundHistory(
                roundNumber = completedRound,
                correct = roundCorrect,
                answered = roundAnswered,
                xpEarned = roundXp,
                totalXp = prefs.getInt(KEY_XP, 0),
                bestStreak = roundBestStreak
            )
            val achievements = unlockedAchievements().toMutableSet()
            achievements += ACH_FIRST_DRIVE
            editor.putString(KEY_ACHIEVEMENTS, achievements.joinToString(","))
        }

        editor.apply()
        return SkipResult(roundAnswered, finished)
    }

    fun repeatPrompt(): String =
        currentQuestion()?.prompt ?: startOrResumeRound().prompt

    fun scoreSummary(): String {
        val p = profile()
        return if (language() == Language.CS) {
            "Celkem máš " + p.totalCorrect + " správně z " + p.totalAnswered +
                ". Úspěšnost " + p.accuracy + " procent. Aktuální série " + p.currentStreak + "."
        } else {
            "Overall, you have " + p.totalCorrect + " correct out of " + p.totalAnswered +
                ". Accuracy " + p.accuracy + " percent. Current streak " + p.currentStreak + "."
        }
    }

    fun levelSummary(): String {
        val p = profile()
        return if (language() == Language.CS) {
            "Jsi level " + p.level + ", " + localizedLevelName(p.level) +
                ", s " + p.xp + " XP."
        } else {
            "You are level " + p.level + ", " + p.levelName +
                ", with " + p.xp + " XP."
        }
    }

    fun roundSummary(): RoundSummary {
        val p = profile()
        val accuracy = percent(roundCorrect, roundAnswered)

        val adjustment = when {
            accuracy >= 85 -> "harder"
            accuracy <= 50 -> "easier"
            else -> "steady"
        }

        return RoundSummary(
            correct = roundCorrect,
            answered = roundAnswered,
            accuracy = accuracy,
            xpEarned = roundXp,
            bestStreak = roundBestStreak,
            perfect = roundAnswered == ROUND_SIZE && roundCorrect == ROUND_SIZE,
            totalXp = p.xp,
            level = p.level,
            levelName = p.levelName,
            nextRoundAdjustment = adjustment
        )
    }

    fun resetRound() {
        roundAnswered = 0
        roundCorrect = 0
        roundXp = 0
        roundBestStreak = 0
        currentQuestionId = null

        prefs.edit()
            .putInt(KEY_ROUND_ANSWERED, 0)
            .putInt(KEY_ROUND_CORRECT, 0)
            .putInt(KEY_ROUND_XP, 0)
            .putInt(KEY_ROUND_BEST_STREAK, 0)
            .remove(KEY_CURRENT_QUESTION_ID)
            .apply()
    }

    fun categoryStat(category: TriviaQuestionBank.Category): CategoryStat {
        val answered = prefs.getInt(catAnsweredKey(category), 0)
        val correct = prefs.getInt(catCorrectKey(category), 0)
        val totalResponse = prefs.getLong(catResponseKey(category), 0L)
        val accuracy = percent(correct, answered)

        val rating = when {
            answered == 0 -> 50
            else -> min(100, max(0, (accuracy * 8 + responseSpeedScore(totalResponse / answered) * 2) / 10))
        }

        return CategoryStat(
            category = category,
            answered = answered,
            correct = correct,
            accuracy = accuracy,
            rating = rating,
            averageResponseMs = if (answered == 0) 0L else totalResponse / answered
        )
    }

    fun dashboardData(): DashboardData {
        val rounds = loadRoundHistory()
        val xpProgression = rounds.map { it.roundNumber to it.totalXp }
        val missed = TriviaQuestionBank.questions
            .mapNotNull { raw ->
                val wrong = qWrong(raw.id)
                val seen = qSeen(raw.id)
                if (wrong <= 0 || seen <= 0) return@mapNotNull null
                val q = localize(raw)
                MissedQuestionStat(
                    id = raw.id,
                    prompt = q.prompt,
                    category = raw.category,
                    wrongCount = wrong,
                    seenCount = seen,
                    accuracy = percent(qCorrect(raw.id), seen)
                )
            }
            .sortedWith(
                compareByDescending<MissedQuestionStat> { it.wrongCount }
                    .thenBy { it.accuracy }
                    .thenByDescending { it.seenCount }
            )
            .take(8)

        return DashboardData(
            rounds = rounds,
            xpProgression = xpProgression,
            categoryStats = TriviaQuestionBank.Category.entries.map { categoryStat(it) },
            missedQuestions = missed
        )
    }

    private fun appendRoundHistory(
        roundNumber: Int,
        correct: Int,
        answered: Int,
        xpEarned: Int,
        totalXp: Int,
        bestStreak: Int
    ) {
        val entry = listOf(
            roundNumber,
            correct,
            answered,
            percent(correct, answered),
            xpEarned,
            totalXp,
            bestStreak,
            System.currentTimeMillis()
        ).joinToString("|")

        val existing = prefs.getString(KEY_ROUND_HISTORY, "")
            ?.split(";")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val updated = (existing + entry).takeLast(MAX_ROUND_HISTORY)
        prefs.edit().putString(KEY_ROUND_HISTORY, updated.joinToString(";")).apply()
    }

    private fun loadRoundHistory(): List<RoundHistoryEntry> =
        prefs.getString(KEY_ROUND_HISTORY, "")
            ?.split(";")
            ?.mapNotNull { encoded ->
                val parts = encoded.split("|")
                if (parts.size != 8) return@mapNotNull null
                val values = parts.map { it.toLongOrNull() }
                if (values.any { it == null }) return@mapNotNull null
                RoundHistoryEntry(
                    roundNumber = values[0]!!.toInt(),
                    correct = values[1]!!.toInt(),
                    answered = values[2]!!.toInt(),
                    accuracy = values[3]!!.toInt(),
                    xpEarned = values[4]!!.toInt(),
                    totalXp = values[5]!!.toInt(),
                    bestStreak = values[6]!!.toInt(),
                    completedAt = values[7]!!
                )
            }
            .orEmpty()

    fun achievementTitle(id: String): String {
        val cs = language() == Language.CS
        return when (id) {
            ACH_FIRST_DRIVE -> if (cs) "První jízda" else "First Drive"
            ACH_HOT_STREAK -> if (cs) "Žhavá série" else "Hot Streak"
            ACH_UNSTOPPABLE -> if (cs) "Nezastavitelný" else "Unstoppable"
            ACH_PERFECT_ROUND -> if (cs) "Perfektní kolo" else "Perfect Round"
            ACH_CENTURY -> if (cs) "Stovka" else "Century"
            ACH_ROAD_SCHOLAR -> if (cs) "Silniční učenec" else "Road Scholar"
            ACH_WORLD_TRAVELER -> if (cs) "Světoběžník" else "World Traveler"
            ACH_SCIENCE_MIND -> if (cs) "Vědecká hlava" else "Science Mind"
            else -> id
        }
    }

    private fun selectNextQuestion(): TriviaQuestionBank.LocalizedQuestion {
        val p = profile()
        val targetDifficulty = targetDifficulty(p)
        val totalAnswered = p.totalAnswered
        val lastCategory = lastCategoryKey

        return TriviaQuestionBank.questions
            .asSequence()
            .filter { unlockedDifficulty(it.difficulty, p.level) }
            .map { q ->
                var score = 100.0

                score -= abs(q.difficulty - targetDifficulty) * 16.0

                val cat = categoryStat(q.category)
                score += (100 - cat.rating) * 0.32

                val seen = qSeen(q.id)
                val wrong = qWrong(q.id)
                val correct = qCorrect(q.id)
                val mastered = correct >= 3 && wrong == 0

                if (seen == 0) score += 35.0
                if (qDueAt(q.id) <= totalAnswered) score += 25.0
                if (wrong > 0) score += min(25.0, wrong * 6.0)
                if (mastered) score -= 35.0
                if (recentIds.contains(q.id)) score -= 80.0
                if (q.category.key == lastCategory) score -= 18.0

                val deterministicJitter =
                    ((q.id.hashCode() xor totalAnswered) and 0xF) / 4.0
                score += deterministicJitter

                q to score
            }
            .maxByOrNull { it.second }
            ?.first
            ?: TriviaQuestionBank.questions.first()
    }

    private fun targetDifficulty(profile: Profile): Int {
        val base = when (profile.level) {
            1 -> 2
            2 -> 3
            3 -> 4
            4 -> 6
            5 -> 7
            6 -> 9
            else -> 10
        }

        val recentAccuracy = recentAccuracy()
        return when {
            recentAccuracy >= 85 -> min(10, base + 1)
            recentAccuracy <= 50 -> max(1, base - 1)
            else -> base
        }
    }

    private fun unlockedDifficulty(difficulty: Int, playerLevel: Int): Boolean {
        val maxDifficulty = when (playerLevel) {
            1 -> 3
            2 -> 4
            3 -> 5
            4 -> 7
            5 -> 8
            6 -> 10
            else -> 10
        }
        return difficulty <= maxDifficulty
    }

    private fun recentAccuracy(): Int {
        val recent = prefs.getString(KEY_RECENT_RESULTS, "")
            ?.takeLast(RECENT_RESULT_WINDOW)
            .orEmpty()
        if (recent.isEmpty()) return 65
        return recent.count { it == '1' } * 100 / recent.length
    }

    private fun updateQuestionStats(
        question: LocalizedQuestion,
        correct: Boolean,
        responseMs: Long,
        totalAnsweredAfter: Int
    ) {
        val seen = qSeen(question.id) + 1
        val correctCount = qCorrect(question.id) + if (correct) 1 else 0
        val wrong = qWrong(question.id) + if (correct) 0 else 1

        val interval = when {
            !correct -> 5
            correctCount >= 3 && wrong == 0 -> 80
            correctCount >= 2 -> 35
            else -> 15
        }

        val oldResults = prefs.getString(KEY_RECENT_RESULTS, "").orEmpty()
        val newResults = (oldResults + if (correct) "1" else "0")
            .takeLast(RECENT_RESULT_WINDOW)

        prefs.edit()
            .putInt(qSeenKey(question.id), seen)
            .putInt(qCorrectKey(question.id), correctCount)
            .putInt(qWrongKey(question.id), wrong)
            .putInt(qLastSeenKey(question.id), totalAnsweredAfter)
            .putInt(qDueKey(question.id), totalAnsweredAfter + interval)
            .putLong(
                qResponseKey(question.id),
                prefs.getLong(qResponseKey(question.id), 0L) + responseMs
            )
            .putString(KEY_RECENT_RESULTS, newResults)
            .apply()
    }

    private fun updateCategoryStats(
        category: TriviaQuestionBank.Category,
        correct: Boolean,
        responseMs: Long
    ) {
        prefs.edit()
            .putInt(catAnsweredKey(category), prefs.getInt(catAnsweredKey(category), 0) + 1)
            .putInt(catCorrectKey(category), prefs.getInt(catCorrectKey(category), 0) + if (correct) 1 else 0)
            .putLong(catResponseKey(category), prefs.getLong(catResponseKey(category), 0L) + responseMs)
            .apply()
    }

    private fun evaluateAchievements(
        totalAnswered: Int,
        totalCorrect: Int,
        bestStreak: Int,
        roundsCompleted: Int,
        perfectRound: Boolean
    ): List<String> {
        val unlocked = unlockedAchievements().toMutableSet()
        val before = unlocked.toSet()

        if (roundsCompleted >= 1) unlocked += ACH_FIRST_DRIVE
        if (bestStreak >= 5) unlocked += ACH_HOT_STREAK
        if (bestStreak >= 10) unlocked += ACH_UNSTOPPABLE
        if (perfectRound) unlocked += ACH_PERFECT_ROUND
        if (totalAnswered >= 100) unlocked += ACH_CENTURY
        if (totalCorrect >= 500) unlocked += ACH_ROAD_SCHOLAR

        val geo = categoryStat(TriviaQuestionBank.Category.GEOGRAPHY)
        if (geo.answered >= 50 && geo.accuracy >= 90) unlocked += ACH_WORLD_TRAVELER

        val science = categoryStat(TriviaQuestionBank.Category.SCIENCE)
        if (science.answered >= 50 && science.accuracy >= 90) unlocked += ACH_SCIENCE_MIND

        prefs.edit()
            .putString(KEY_ACHIEVEMENTS, unlocked.joinToString(","))
            .apply()

        return (unlocked - before).toList()
    }

    private fun unlockedAchievements(): Set<String> =
        prefs.getString(KEY_ACHIEVEMENTS, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun localize(q: TriviaQuestionBank.LocalizedQuestion): LocalizedQuestion {
        val cs = language() == Language.CS
        return LocalizedQuestion(
            id = q.id,
            difficulty = q.difficulty,
            category = q.category,
            categoryName = localizedCategory(q.category),
            prompt = if (cs) q.promptCs else q.promptEn,
            answers = if (cs) q.answersCs else q.answersEn,
            explanation = if (cs) q.explanationCs else q.explanationEn
        )
    }

    private fun localizedCategory(category: TriviaQuestionBank.Category): String {
        if (language() == Language.EN) {
            return category.key.replaceFirstChar { it.uppercase() }
        }

        return when (category) {
            TriviaQuestionBank.Category.GEOGRAPHY -> "Geografie"
            TriviaQuestionBank.Category.SCIENCE -> "Věda"
            TriviaQuestionBank.Category.HISTORY -> "Historie"
            TriviaQuestionBank.Category.GENERAL -> "Všeobecné"
            TriviaQuestionBank.Category.TECHNOLOGY -> "Technologie"
            TriviaQuestionBank.Category.NATURE -> "Příroda"
            TriviaQuestionBank.Category.SPORTS -> "Sport"
            TriviaQuestionBank.Category.CULTURE -> "Kultura"
            TriviaQuestionBank.Category.MOVIES -> "Filmy"
            TriviaQuestionBank.Category.CARS -> "Auta"
            TriviaQuestionBank.Category.NUMBERS -> "Čísla"
        }
    }

    private fun localizedLevelName(level: Int): String =
        if (language() == Language.CS) {
            when (level) {
                1 -> "Nováček"
                2 -> "Průzkumník"
                3 -> "Vyzyvatel"
                4 -> "Expert"
                5 -> "Mistr"
                6 -> "Elita"
                else -> "Legenda"
            }
        } else {
            levelName(level)
        }

    private fun levelName(level: Int): String =
        when (level) {
            1 -> "Rookie"
            2 -> "Explorer"
            3 -> "Challenger"
            4 -> "Expert"
            5 -> "Master"
            6 -> "Elite"
            else -> "Legend"
        }

    private fun levelForXp(xp: Int): Int =
        when {
            xp >= 6000 -> 7
            xp >= 3000 -> 6
            xp >= 1500 -> 5
            xp >= 700 -> 4
            xp >= 300 -> 3
            xp >= 100 -> 2
            else -> 1
        }

    private fun nextLevelXp(level: Int): Int? =
        when (level) {
            1 -> 100
            2 -> 300
            3 -> 700
            4 -> 1500
            5 -> 3000
            6 -> 6000
            else -> null
        }

    private fun xpForDifficulty(difficulty: Int): Int =
        8 + difficulty * 2

    private fun streakBonus(streak: Int): Int =
        when {
            streak >= 10 -> 25
            streak >= 8 -> 15
            streak >= 5 -> 10
            streak >= 3 -> 5
            else -> 0
        }

    private fun responseSpeedScore(ms: Long): Int =
        when {
            ms <= 1800 -> 100
            ms <= 3000 -> 85
            ms <= 5000 -> 70
            ms <= 8000 -> 55
            else -> 40
        }

    private fun answerMatches(actual: String, expected: String): Boolean {
        if (actual == expected) return true
        if (expected.length >= 3 && actual.contains(expected)) return true
        if (actual.length >= 4 && expected.contains(actual)) return true

        val tolerance = when {
            expected.length <= 4 -> 0
            expected.length <= 7 -> 1
            else -> 2
        }
        return levenshtein(actual, expected) <= tolerance
    }

    private fun normalize(value: String): String {
        val withoutMarks = Normalizer.normalize(value.lowercase(language().locale), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return withoutMarks
            .replace(Regex("[^a-z0-9\\s.-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun percent(correct: Int, answered: Int): Int =
        if (answered == 0) 0 else correct * 100 / answered

    private fun qSeen(id: String) = prefs.getInt(qSeenKey(id), 0)
    private fun qCorrect(id: String) = prefs.getInt(qCorrectKey(id), 0)
    private fun qWrong(id: String) = prefs.getInt(qWrongKey(id), 0)
    private fun qDueAt(id: String) = prefs.getInt(qDueKey(id), 0)

    private fun qSeenKey(id: String) = "q_" + id + "_seen"
    private fun qCorrectKey(id: String) = "q_" + id + "_correct"
    private fun qWrongKey(id: String) = "q_" + id + "_wrong"
    private fun qLastSeenKey(id: String) = "q_" + id + "_last"
    private fun qDueKey(id: String) = "q_" + id + "_due"
    private fun qResponseKey(id: String) = "q_" + id + "_response"

    private fun catAnsweredKey(category: TriviaQuestionBank.Category) =
        "cat_" + category.key + "_answered"

    private fun catCorrectKey(category: TriviaQuestionBank.Category) =
        "cat_" + category.key + "_correct"

    private fun catResponseKey(category: TriviaQuestionBank.Category) =
        "cat_" + category.key + "_response"

    companion object {
        private const val PREFS_NAME = "daw_trivia_profile"

        private const val KEY_LANGUAGE = "language"
        private const val KEY_XP = "xp"
        private const val KEY_TOTAL_ANSWERED = "total_answered"
        private const val KEY_TOTAL_CORRECT = "total_correct"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_CURRENT_STREAK = "current_streak"
        private const val KEY_ROUNDS_COMPLETED = "rounds_completed"
        private const val KEY_ACHIEVEMENTS = "achievements"
        private const val KEY_RECENT_RESULTS = "recent_results"

        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val KEY_ROUND_XP = "round_xp"
        private const val KEY_ROUND_BEST_STREAK = "round_best_streak"
        private const val KEY_RECENT_IDS = "recent_ids"
        private const val KEY_CURRENT_QUESTION_ID = "current_question_id"
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val KEY_ROUND_HISTORY = "round_history"

        private const val ROUND_SIZE = 10
        private const val RECENT_WINDOW = 8
        private const val RECENT_RESULT_WINDOW = 20
        private const val MAX_ROUND_HISTORY = 30

        private const val WRONG_ANSWER_XP = 2
        private const val PERFECT_ROUND_BONUS = 50

        const val ACH_FIRST_DRIVE = "first_drive"
        const val ACH_HOT_STREAK = "hot_streak"
        const val ACH_UNSTOPPABLE = "unstoppable"
        const val ACH_PERFECT_ROUND = "perfect_round"
        const val ACH_CENTURY = "century"
        const val ACH_ROAD_SCHOLAR = "road_scholar"
        const val ACH_WORLD_TRAVELER = "world_traveler"
        const val ACH_SCIENCE_MIND = "science_mind"
    }
}
