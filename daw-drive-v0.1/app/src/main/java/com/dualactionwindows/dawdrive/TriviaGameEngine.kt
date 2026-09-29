package com.dualactionwindows.dawdrive

import android.content.Context
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class TriviaGameEngine(context: Context) {

    data class Question(
        val id: String,
        val level: Int,
        val category: String,
        val prompt: String,
        val answers: List<String>,
        val explanation: String
    )

    data class AnswerResult(
        val correct: Boolean,
        val expected: String,
        val explanation: String,
        val xpEarned: Int,
        val totalXp: Int,
        val level: Int,
        val streak: Int,
        val roundAnswered: Int,
        val roundCorrect: Int,
        val roundFinished: Boolean,
        val promoted: Boolean
    )

    data class Profile(
        val level: Int,
        val xp: Int,
        val totalAnswered: Int,
        val totalCorrect: Int,
        val bestStreak: Int,
        val currentStreak: Int,
        val roundsCompleted: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val recentIds = ArrayDeque<String>()
    private var currentQuestion: Question? = null
    private var roundAnswered = 0
    private var roundCorrect = 0

    val questions = listOf(
        Question("geo_1",1,"Geography","What is the capital of France?", listOf("paris"), "Paris is the capital of France."),
        Question("sci_1",1,"Science","Which planet is known as the Red Planet?", listOf("mars"), "Mars looks reddish because of iron oxide on its surface."),
        Question("gen_1",1,"General","How many days are in a leap year?", listOf("366","three hundred sixty six","three hundred and sixty six"), "A leap year has 366 days."),
        Question("geo_2",1,"Geography","Which ocean is the largest?", listOf("pacific","pacific ocean"), "The Pacific is the largest ocean on Earth."),
        Question("sci_2",1,"Science","What gas do humans need to breathe to survive?", listOf("oxygen"), "Humans need oxygen for cellular respiration."),
        Question("hist_1",1,"History","The pyramids of Giza are in which country?", listOf("egypt"), "The Giza pyramid complex is in Egypt."),

        Question("geo_3",2,"Geography","What is the capital of Australia?", listOf("canberra"), "Canberra is the capital of Australia."),
        Question("sci_3",2,"Science","What is the chemical symbol for gold?", listOf("au"), "Gold has the chemical symbol Au."),
        Question("hist_2",2,"History","Who was the first person to walk on the Moon?", listOf("neil armstrong","armstrong"), "Neil Armstrong walked on the Moon in 1969."),
        Question("gen_2",2,"General","How many sides does a hexagon have?", listOf("6","six"), "A hexagon has six sides."),
        Question("geo_4",2,"Geography","Which country has the city of Barcelona?", listOf("spain"), "Barcelona is in Spain."),
        Question("sci_4",2,"Science","What force keeps planets in orbit around the Sun?", listOf("gravity","gravitation"), "Gravity keeps planets in orbit."),

        Question("geo_5",3,"Geography","What is the capital of Canada?", listOf("ottawa"), "Ottawa is the capital of Canada."),
        Question("sci_5",3,"Science","What is the largest organ in the human body?", listOf("skin","the skin"), "The skin is the body's largest organ."),
        Question("hist_3",3,"History","In which year did World War Two end?", listOf("1945","nineteen forty five"), "World War Two ended in 1945."),
        Question("gen_3",3,"General","What is the square root of 144?", listOf("12","twelve"), "The square root of 144 is 12."),
        Question("geo_6",3,"Geography","Which river runs through Budapest?", listOf("danube","danube river"), "The Danube runs through Budapest."),
        Question("sci_6",3,"Science","What is the most abundant gas in Earth's atmosphere?", listOf("nitrogen"), "Nitrogen makes up about 78 percent of Earth's atmosphere."),

        Question("geo_7",4,"Geography","What is the capital of New Zealand?", listOf("wellington"), "Wellington is the capital of New Zealand."),
        Question("sci_7",4,"Science","What part of a cell contains most of its genetic material?", listOf("nucleus","the nucleus"), "Most genetic material is stored in the nucleus."),
        Question("hist_4",4,"History","Which civilization built Machu Picchu?", listOf("inca","incas","the inca"), "Machu Picchu was built by the Inca."),
        Question("gen_4",4,"General","What is 15 percent of 200?", listOf("30","thirty"), "Fifteen percent of 200 is 30."),
        Question("geo_8",4,"Geography","Which country borders both Spain and France?", listOf("andorra"), "Andorra lies between Spain and France."),
        Question("sci_8",4,"Science","What is the SI unit of electrical resistance?", listOf("ohm","ohms"), "Electrical resistance is measured in ohms."),

        Question("geo_9",5,"Geography","What is the capital of Kazakhstan?", listOf("astana"), "Astana is the capital of Kazakhstan."),
        Question("sci_9",5,"Science","What is the atomic number of carbon?", listOf("6","six"), "Carbon has atomic number 6."),
        Question("hist_5",5,"History","Which treaty ended World War One between Germany and the Allied powers?", listOf("treaty of versailles","versailles"), "The Treaty of Versailles was signed in 1919."),
        Question("gen_5",5,"General","What is 17 squared?", listOf("289","two hundred eighty nine","two hundred and eighty nine"), "Seventeen squared is 289."),
        Question("geo_10",5,"Geography","Which African country has Addis Ababa as its capital?", listOf("ethiopia"), "Addis Ababa is the capital of Ethiopia."),
        Question("sci_10",5,"Science","Which scientist formulated the three laws of motion?", listOf("isaac newton","newton"), "Isaac Newton formulated the three laws of motion.")
    )

    fun profile(): Profile = Profile(
        level = levelForXp(prefs.getInt(KEY_XP, 0)),
        xp = prefs.getInt(KEY_XP, 0),
        totalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0),
        totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0),
        bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
        currentStreak = prefs.getInt(KEY_CURRENT_STREAK, 0),
        roundsCompleted = prefs.getInt(KEY_ROUNDS_COMPLETED, 0)
    )

    fun startOrResumeRound(): Question {
        if (roundAnswered >= ROUND_SIZE) {
            roundAnswered = 0
            roundCorrect = 0
        }
        return nextQuestion()
    }

    fun nextQuestion(): Question {
        val profile = profile()
        val maxLevel = min(5, max(1, profile.level))
        val eligible = questions.filter { it.level <= maxLevel }

        val unseen = eligible.filterNot { recentIds.contains(it.id) }
        val pool = if (unseen.isNotEmpty()) unseen else eligible

        val targetLevel = adaptiveTargetLevel(profile.level)
        val ranked = pool.sortedWith(
            compareBy<Question> { kotlin.math.abs(it.level - targetLevel) }
                .thenBy { timesSeen(it.id) }
                .thenBy { it.id }
        )

        val selected = ranked.firstOrNull() ?: questions.first()
        currentQuestion = selected

        recentIds.addLast(selected.id)
        while (recentIds.size > RECENT_WINDOW) recentIds.removeFirst()

        return selected
    }

    fun currentQuestion(): Question? = currentQuestion

    fun answer(rawAnswer: String): AnswerResult {
        val question = currentQuestion ?: nextQuestion()
        val before = profile()
        val normalized = normalize(rawAnswer)
        val correct = question.answers.any { answerMatches(normalized, normalize(it)) }

        val oldStreak = before.currentStreak
        val newStreak = if (correct) oldStreak + 1 else 0
        val baseXp = 10 + (question.level - 1) * 4
        val streakBonus = if (correct) min(20, (newStreak / 3) * 5) else 0
        val earned = if (correct) baseXp + streakBonus else 2

        val newXp = before.xp + earned
        val afterLevel = levelForXp(newXp)
        val promoted = afterLevel > before.level

        val seen = timesSeen(question.id) + 1
        val misses = wrongCount(question.id) + if (correct) 0 else 1

        roundAnswered += 1
        if (correct) roundCorrect += 1

        val roundFinished = roundAnswered >= ROUND_SIZE

        prefs.edit()
            .putInt(KEY_XP, newXp)
            .putInt(KEY_TOTAL_ANSWERED, before.totalAnswered + 1)
            .putInt(KEY_TOTAL_CORRECT, before.totalCorrect + if (correct) 1 else 0)
            .putInt(KEY_CURRENT_STREAK, newStreak)
            .putInt(KEY_BEST_STREAK, max(before.bestStreak, newStreak))
            .putInt(KEY_ROUNDS_COMPLETED, before.roundsCompleted + if (roundFinished) 1 else 0)
            .putInt(seenKey(question.id), seen)
            .putInt(wrongKey(question.id), misses)
            .apply()

        return AnswerResult(
            correct = correct,
            expected = question.answers.first(),
            explanation = question.explanation,
            xpEarned = earned,
            totalXp = newXp,
            level = afterLevel,
            streak = newStreak,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            roundFinished = roundFinished,
            promoted = promoted
        )
    }

    fun roundSummary(): String {
        val profile = profile()
        val accuracy = if (roundAnswered == 0) 0 else (roundCorrect * 100 / roundAnswered)
        return "Round complete. You got " + roundCorrect + " out of " + roundAnswered +
            " correct. Accuracy " + accuracy + " percent. Level " + profile.level +
            ", total XP " + profile.xp + "."
    }

    fun resetRound() {
        roundAnswered = 0
        roundCorrect = 0
        currentQuestion = null
    }

    private fun adaptiveTargetLevel(profileLevel: Int): Int {
        val recentPerformance = recentPerformanceScore()
        return when {
            recentPerformance >= 80 -> min(5, profileLevel + 1)
            recentPerformance <= 45 -> max(1, profileLevel - 1)
            else -> profileLevel
        }
    }

    private fun recentPerformanceScore(): Int {
        val attempted = prefs.getInt(KEY_TOTAL_ANSWERED, 0)
        val correct = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        if (attempted == 0) return 60
        return (correct * 100 / attempted)
    }

    private fun timesSeen(id: String): Int = prefs.getInt(seenKey(id), 0)
    private fun wrongCount(id: String): Int = prefs.getInt(wrongKey(id), 0)

    private fun answerMatches(actual: String, expected: String): Boolean {
        if (actual == expected) return true
        if (actual.contains(expected) && expected.length >= 3) return true
        if (expected.contains(actual) && actual.length >= 4) return true
        return levenshtein(actual, expected) <= when {
            expected.length <= 4 -> 0
            expected.length <= 7 -> 1
            else -> 2
        }
    }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levelForXp(xp: Int): Int = when {
        xp >= 700 -> 5
        xp >= 400 -> 4
        xp >= 200 -> 3
        xp >= 80 -> 2
        else -> 1
    }

    private fun seenKey(id: String) = "seen_" + id
    private fun wrongKey(id: String) = "wrong_" + id

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)

        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                curr[j + 1] = min(
                    min(curr[j] + 1, prev[j + 1] + 1),
                    prev[j] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    companion object {
        private const val PREFS_NAME = "daw_trivia_profile"
        private const val KEY_XP = "xp"
        private const val KEY_TOTAL_ANSWERED = "total_answered"
        private const val KEY_TOTAL_CORRECT = "total_correct"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_CURRENT_STREAK = "current_streak"
        private const val KEY_ROUNDS_COMPLETED = "rounds_completed"

        private const val ROUND_SIZE = 10
        private const val RECENT_WINDOW = 8
    }
}
