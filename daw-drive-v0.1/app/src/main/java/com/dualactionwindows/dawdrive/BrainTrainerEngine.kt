package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import kotlin.math.max
import kotlin.random.Random

class BrainTrainerEngine(context: Context) {

    enum class Type { NUMBER_MEMORY, SEQUENCE, MATH, ODD_ONE_OUT, ATTENTION, FAST_COMPARE }

    data class Challenge(
        val type: Type,
        val promptCs: String,
        val promptEn: String,
        val answers: List<String>,
        val explanationCs: String,
        val explanationEn: String
    ) {
        fun prompt(language: TriviaGameEngine.Language) =
            if (language == TriviaGameEngine.Language.CS) promptCs else promptEn
        fun explanation(language: TriviaGameEngine.Language) =
            if (language == TriviaGameEngine.Language.CS) explanationCs else explanationEn
    }

    data class Profile(
        val level: Int,
        val xp: Int,
        val streak: Int,
        val bestStreak: Int,
        val answered: Int,
        val correct: Int,
        val accuracy: Int
    )

    data class Result(
        val correct: Boolean,
        val expected: String,
        val explanation: String,
        val xpEarned: Int,
        val streak: Int,
        val level: Int,
        val promoted: Boolean,
        val roundFinished: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var current: Challenge? = null
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)
    private var lastType: Type? = prefs.getString(KEY_LAST_TYPE, null)?.let {
        runCatching { Type.valueOf(it) }.getOrNull()
    }

    fun profile(): Profile {
        val xp = prefs.getInt(KEY_XP, 0)
        val answered = prefs.getInt(KEY_ANSWERED, 0)
        val correct = prefs.getInt(KEY_CORRECT, 0)
        return Profile(
            level = levelForXp(xp),
            xp = xp,
            streak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            answered = answered,
            correct = correct,
            accuracy = if (answered == 0) 0 else correct * 100 / answered
        )
    }

    fun startOrResume(language: TriviaGameEngine.Language): Challenge {
        if (roundAnswered >= ROUND_SIZE) resetRound()
        return current ?: nextChallenge(language)
    }

    fun currentChallenge(): Challenge? = current

    fun nextChallenge(language: TriviaGameEngine.Language): Challenge {
        val level = adaptiveLevel()
        val type = Type.entries.filter { it != lastType }.random()
        val challenge = when (type) {
            Type.NUMBER_MEMORY -> numberMemory(level)
            Type.SEQUENCE -> sequence(level)
            Type.MATH -> math(level)
            Type.ODD_ONE_OUT -> oddOneOut(level)
            Type.ATTENTION -> attention(level)
            Type.FAST_COMPARE -> fastCompare(level)
        }
        current = challenge
        lastType = type
        prefs.edit().putString(KEY_LAST_TYPE, type.name).apply()
        return challenge
    }

    fun answerCandidates(candidates: List<String>, language: TriviaGameEngine.Language): Result {
        val challenge = current ?: startOrResume(language)
        val correct = candidates.any { candidate ->
            challenge.answers.any { expected -> matches(candidate, expected, language) }
        }

        val before = profile()
        val streak = if (correct) before.streak + 1 else 0
        val xpEarned = if (correct) 10 + before.level * 2 + if (streak >= 5) 5 else 0 else 1
        val newXp = before.xp + xpEarned
        val newLevel = levelForXp(newXp)

        roundAnswered += 1
        if (correct) roundCorrect += 1
        val answered = before.answered + 1
        val totalCorrect = before.correct + if (correct) 1 else 0

        updateAdaptive(correct)
        current = null

        prefs.edit()
            .putInt(KEY_XP, newXp)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST_STREAK, max(before.bestStreak, streak))
            .putInt(KEY_ANSWERED, answered)
            .putInt(KEY_CORRECT, totalCorrect)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .apply()

        return Result(
            correct = correct,
            expected = challenge.answers.first(),
            explanation = challenge.explanation(language),
            xpEarned = xpEarned,
            streak = streak,
            level = newLevel,
            promoted = newLevel > before.level,
            roundFinished = roundAnswered >= ROUND_SIZE
        )
    }

    fun skipCurrent(): Boolean {
        current = null
        roundAnswered += 1
        prefs.edit().putInt(KEY_ROUND_ANSWERED, roundAnswered).apply()
        return roundAnswered >= ROUND_SIZE
    }

    fun roundSummary(language: TriviaGameEngine.Language): String {
        val p = profile()
        val accuracy = if (roundAnswered == 0) 0 else roundCorrect * 100 / roundAnswered
        return if (language == TriviaGameEngine.Language.CS) {
            "Brain Trainer: " + roundCorrect + " z " + roundAnswered + " správně, " +
                accuracy + " procent. Level " + p.level + ", " + p.xp + " XP."
        } else {
            "Brain Trainer: " + roundCorrect + " out of " + roundAnswered + " correct, " +
                accuracy + " percent. Level " + p.level + ", " + p.xp + " XP."
        }
    }

    fun resetRound() {
        current = null
        roundAnswered = 0
        roundCorrect = 0
        prefs.edit().putInt(KEY_ROUND_ANSWERED, 0).putInt(KEY_ROUND_CORRECT, 0).apply()
    }

    private fun adaptiveLevel(): Int =
        (profile().level + prefs.getInt(KEY_ADAPTIVE, 0).coerceIn(-1, 1)).coerceIn(1, MAX_LEVEL)

    private fun updateAdaptive(correct: Boolean) {
        var good = prefs.getInt(KEY_GOOD_RUN, 0)
        var bad = prefs.getInt(KEY_BAD_RUN, 0)
        var offset = prefs.getInt(KEY_ADAPTIVE, 0).coerceIn(-1, 1)
        if (correct) {
            good += 1
            bad = 0
            if (good >= 4) {
                offset = (offset + 1).coerceAtMost(1)
                good = 0
            }
        } else {
            bad += 1
            good = 0
            if (bad >= 2) {
                offset = (offset - 1).coerceAtLeast(-1)
                bad = 0
            }
        }
        prefs.edit().putInt(KEY_GOOD_RUN, good).putInt(KEY_BAD_RUN, bad).putInt(KEY_ADAPTIVE, offset).apply()
    }

    private fun numberMemory(level: Int): Challenge {
        val length = (3 + level).coerceAtMost(8)
        val digits = List(length) { Random.nextInt(1, 10) }
        val spaced = digits.joinToString(" ")
        val compact = digits.joinToString("")
        return Challenge(
            Type.NUMBER_MEMORY,
            "Paměť. Zapamatuj si čísla: " + spaced + ". Teď je zopakuj ve stejném pořadí.",
            "Memory. Remember these digits: " + spaced + ". Now repeat them in the same order.",
            listOf(compact, spaced),
            "Správná sekvence byla " + spaced + ".",
            "The correct sequence was " + spaced + "."
        )
    }

    private fun sequence(level: Int): Challenge {
        return when (Random.nextInt(if (level >= 3) 4 else 3)) {
            0 -> {
                val start = Random.nextInt(1, 15)
                val step = Random.nextInt(2, 3 + level)
                val values = List(4) { start + it * step }
                val answer = start + 4 * step
                Challenge(Type.SEQUENCE,
                    "Sekvence. " + values.joinToString(", ") + ", co je dál?",
                    "Sequence. " + values.joinToString(", ") + ", what comes next?",
                    listOf(answer.toString()),
                    "Přičítáme " + step + ". Další číslo je " + answer + ".",
                    "We add " + step + " each time. The next number is " + answer + ".")
            }
            1 -> {
                val start = Random.nextInt(1, 5)
                val values = List(4) { start * (1 shl it) }
                val answer = start * 16
                Challenge(Type.SEQUENCE,
                    "Sekvence. " + values.joinToString(", ") + ", co je dál?",
                    "Sequence. " + values.joinToString(", ") + ", what comes next?",
                    listOf(answer.toString()),
                    "Každé číslo je dvojnásobek. Další je " + answer + ".",
                    "Each number doubles. The next is " + answer + ".")
            }
            2 -> {
                val n = Random.nextInt(2, 8)
                val values = listOf(n, n + 2, n + 5, n + 9)
                val answer = n + 14
                Challenge(Type.SEQUENCE,
                    "Sekvence. " + values.joinToString(", ") + ", co je dál?",
                    "Sequence. " + values.joinToString(", ") + ", what comes next?",
                    listOf(answer.toString()),
                    "Přičítáme 2, 3, 4 a potom 5. Další je " + answer + ".",
                    "We add 2, 3, 4, then 5. The next is " + answer + ".")
            }
            else -> {
                val a = Random.nextInt(2, 8)
                val b = Random.nextInt(10, 20)
                val values = listOf(a, b, a + 2, b + 2, a + 4)
                val answer = b + 4
                Challenge(Type.SEQUENCE,
                    "Dvě prokládané řady. " + values.joinToString(", ") + ", co je dál?",
                    "Two alternating sequences. " + values.joinToString(", ") + ", what comes next?",
                    listOf(answer.toString()),
                    "Střídají se dvě řady, obě rostou o 2. Další je " + answer + ".",
                    "Two sequences alternate, both increasing by 2. The next is " + answer + ".")
            }
        }
    }

    private fun math(level: Int): Challenge {
        val operation = if (level <= 1) Random.nextInt(2) else Random.nextInt(4)
        val maxValue = 10 + level * 12
        return when (operation) {
            0 -> {
                val a = Random.nextInt(2, maxValue)
                val b = Random.nextInt(2, maxValue)
                val answer = a + b
                Challenge(Type.MATH, "Počítej. " + a + " plus " + b + ".",
                    "Calculate. " + a + " plus " + b + ".", listOf(answer.toString()),
                    "Výsledek je " + answer + ".", "The answer is " + answer + ".")
            }
            1 -> {
                val a = Random.nextInt(10, maxValue + 10)
                val b = Random.nextInt(2, a)
                val answer = a - b
                Challenge(Type.MATH, "Počítej. " + a + " mínus " + b + ".",
                    "Calculate. " + a + " minus " + b + ".", listOf(answer.toString()),
                    "Výsledek je " + answer + ".", "The answer is " + answer + ".")
            }
            2 -> {
                val a = Random.nextInt(2, 5 + level)
                val b = Random.nextInt(2, 8 + level)
                val answer = a * b
                Challenge(Type.MATH, "Počítej. " + a + " krát " + b + ".",
                    "Calculate. " + a + " times " + b + ".", listOf(answer.toString()),
                    "Výsledek je " + answer + ".", "The answer is " + answer + ".")
            }
            else -> {
                val base = listOf(20, 40, 50, 60, 80, 100).random()
                val pct = listOf(10, 20, 25, 50).random()
                val answer = base * pct / 100
                Challenge(Type.MATH, "Kolik je " + pct + " procent z " + base + "?",
                    "What is " + pct + " percent of " + base + "?", listOf(answer.toString()),
                    "Výsledek je " + answer + ".", "The answer is " + answer + ".")
            }
        }
    }

    private fun oddOneOut(level: Int): Challenge {
        val evenGroup = Random.nextBoolean()
        val values = MutableList(4 + level / 2) {
            var n = Random.nextInt(2, 30 + level * 5)
            if ((n % 2 == 0) != evenGroup) n += 1
            n
        }
        var odd = Random.nextInt(2, 30 + level * 5)
        if ((odd % 2 == 0) == evenGroup) odd += 1
        values.add(Random.nextInt(values.size + 1), odd)
        val list = values.joinToString(", ")
        return Challenge(Type.ODD_ONE_OUT,
            "Které číslo sem nepatří podle sudosti? " + list + ".",
            "Which number does not belong by odd or even pattern? " + list + ".",
            listOf(odd.toString()),
            "Jiné než ostatní je " + odd + ".", odd.toString() + " is the one that differs.")
    }

    private fun attention(level: Int): Challenge {
        val divisor = if (level >= 4) listOf(3, 4, 5).random() else listOf(2, 3).random()
        val number = Random.nextInt(4, 50 + level * 10)
        val yes = number % divisor == 0
        return Challenge(Type.ATTENTION,
            "Pozornost. Řekni ano, pokud je " + number + " dělitelné " + divisor + ". Jinak řekni ne.",
            "Attention. Say yes if " + number + " is divisible by " + divisor + ". Otherwise say no.",
            if (yes) listOf("ano", "yes") else listOf("ne", "no"),
            if (yes) number.toString() + " je dělitelné " + divisor + "." else number.toString() + " není dělitelné " + divisor + ".",
            if (yes) number.toString() + " is divisible by " + divisor + "." else number.toString() + " is not divisible by " + divisor + ".")
    }

    private fun fastCompare(level: Int): Challenge {
        val a1 = Random.nextInt(2, 8 + level)
        val a2 = Random.nextInt(2, 8 + level)
        val b1 = Random.nextInt(2, 8 + level)
        val b2 = Random.nextInt(2, 8 + level)
        val left = a1 * a2
        val right = b1 * b2
        val answers = when {
            left > right -> listOf("prvni", "první", "first", "left", "leva", "levá")
            right > left -> listOf("druhy", "druhý", "second", "right", "prava", "pravá")
            else -> listOf("stejne", "stejně", "same", "equal", "remiza", "remíza")
        }
        return Challenge(Type.FAST_COMPARE,
            "Co je větší: " + a1 + " krát " + a2 + ", nebo " + b1 + " krát " + b2 + "? Řekni první, druhý, nebo stejně.",
            "Which is larger: " + a1 + " times " + a2 + ", or " + b1 + " times " + b2 + "? Say first, second, or equal.",
            answers,
            "Výsledky jsou " + left + " a " + right + ".",
            "The results are " + left + " and " + right + ".")
    }

    private fun matches(actualRaw: String, expectedRaw: String, language: TriviaGameEngine.Language): Boolean {
        val actual = normalize(actualRaw, language)
        val expected = normalize(expectedRaw, language)
        if (actual == expected) return true
        if (actual.replace(" ", "") == expected.replace(" ", "")) return true
        val actualNumber = spokenNumber(actual, language)
        val expectedNumber = spokenNumber(expected, language)
        if (actualNumber != null && expectedNumber != null && actualNumber == expectedNumber) return true
        return expected.length >= 3 && actual.contains(expected)
    }

    private fun normalize(value: String, language: TriviaGameEngine.Language): String =
        Normalizer.normalize(value.lowercase(language.locale), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun spokenNumber(value: String, language: TriviaGameEngine.Language): Int? {
        value.toIntOrNull()?.let { return it }
        val compact = value.replace(" ", "")
        if (compact.isNotEmpty() && compact.all { it.isDigit() }) return compact.toIntOrNull()
        val map = if (language == TriviaGameEngine.Language.CS) CZ_NUMBERS else EN_NUMBERS
        map[value]?.let { return it }
        val tokens = value.split(" ")
        val digits = tokens.mapNotNull { map[it] }
        if (digits.size == tokens.size && digits.all { it in 0..9 }) {
            return digits.joinToString("").toIntOrNull()
        }
        return null
    }

    private fun levelForXp(xp: Int): Int = (xp / 180 + 1).coerceAtMost(MAX_LEVEL)

    companion object {
        private const val PREFS_NAME = "lone_rider_brain_trainer"
        private const val KEY_XP = "xp"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_ANSWERED = "answered"
        private const val KEY_CORRECT = "correct"
        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val KEY_LAST_TYPE = "last_type"
        private const val KEY_ADAPTIVE = "adaptive"
        private const val KEY_GOOD_RUN = "good_run"
        private const val KEY_BAD_RUN = "bad_run"
        private const val ROUND_SIZE = 10
        private const val MAX_LEVEL = 8

        private val EN_NUMBERS = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
            "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
            "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
            "eighty" to 80, "ninety" to 90, "hundred" to 100
        )

        private val CZ_NUMBERS = mapOf(
            "nula" to 0, "jedna" to 1, "jeden" to 1, "dva" to 2, "tri" to 3,
            "ctyri" to 4, "pet" to 5, "sest" to 6, "sedm" to 7, "osm" to 8,
            "devet" to 9, "deset" to 10, "jedenact" to 11, "dvanact" to 12,
            "trinact" to 13, "ctrnact" to 14, "patnact" to 15, "sestnact" to 16,
            "sedmnact" to 17, "osmnact" to 18, "devatenact" to 19, "dvacet" to 20,
            "tricet" to 30, "ctyricet" to 40, "padesat" to 50, "sedesat" to 60,
            "sedmdesat" to 70, "osmdesat" to 80, "devadesat" to 90, "sto" to 100
        )
    }
}
