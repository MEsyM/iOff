package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class SpellingBeeEngine(context: Context) {

    data class Word(
        val id: String,
        val difficulty: Int,
        val word: String,
        val definitionEn: String,
        val definitionCs: String
    )

    data class Profile(
        val level: Int,
        val xp: Int,
        val streak: Int,
        val bestStreak: Int,
        val totalAnswered: Int,
        val totalCorrect: Int,
        val roundAnswered: Int,
        val roundCorrect: Int
    )

    data class Result(
        val correct: Boolean,
        val expected: String,
        val xpEarned: Int,
        val level: Int,
        val promoted: Boolean,
        val streak: Int,
        val roundAnswered: Int,
        val roundCorrect: Int,
        val roundFinished: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var currentWordId: String? = prefs.getString(KEY_CURRENT_WORD, null)
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)

    private val words = listOf(
        Word("s001",1,"cat","a small domesticated animal","malé domácí zvíře"),
        Word("s002",1,"dog","a common domesticated animal","běžné domácí zvíře"),
        Word("s003",1,"sun","the star at the center of our solar system","hvězda ve středu sluneční soustavy"),
        Word("s004",1,"book","a set of written or printed pages","soubor psaných nebo tištěných stránek"),
        Word("s005",1,"road","a route for vehicles","cesta pro vozidla"),
        Word("s006",1,"green","a color between blue and yellow","barva mezi modrou a žlutou"),
        Word("s007",1,"water","a clear liquid essential for life","čirá kapalina nezbytná pro život"),
        Word("s008",1,"house","a building used as a home","budova používaná jako domov"),

        Word("s101",2,"window","an opening in a wall fitted with glass","otvor ve stěně osazený sklem"),
        Word("s102",2,"garden","an area where plants are grown","místo, kde se pěstují rostliny"),
        Word("s103",2,"planet","a large body orbiting a star","velké těleso obíhající kolem hvězdy"),
        Word("s104",2,"school","a place for education","místo pro vzdělávání"),
        Word("s105",2,"bridge","a structure carrying a route over an obstacle","stavba vedoucí cestu přes překážku"),
        Word("s106",2,"silver","a precious metallic element","drahý kovový prvek"),
        Word("s107",2,"engine","a machine that converts energy into motion","stroj převádějící energii na pohyb"),
        Word("s108",2,"travel","to go from one place to another","přesun z jednoho místa na jiné"),

        Word("s201",3,"science","the systematic study of the natural world","systematické studium přírodního světa"),
        Word("s202",3,"country","a nation with its own government","země s vlastní vládou"),
        Word("s203",3,"electric","relating to electricity","související s elektřinou"),
        Word("s204",3,"language","a system of human communication","systém lidské komunikace"),
        Word("s205",3,"traffic","vehicles moving on a road","vozidla pohybující se po silnici"),
        Word("s206",3,"weather","the state of the atmosphere","stav atmosféry"),
        Word("s207",3,"history","the study of past events","studium minulých událostí"),
        Word("s208",3,"battery","a device that stores electrical energy","zařízení ukládající elektrickou energii"),

        Word("s301",4,"necessary","required or essential","nutný nebo nezbytný"),
        Word("s302",4,"separate","to keep or place apart","oddělit nebo držet zvlášť"),
        Word("s303",4,"calendar","a system for organizing days and months","systém pro organizaci dnů a měsíců"),
        Word("s304",4,"business","commercial activity","obchodní činnost"),
        Word("s305",4,"temperature","a measure of how hot or cold something is","míra teploty"),
        Word("s306",4,"environment","the surroundings in which something exists","prostředí, ve kterém něco existuje"),
        Word("s307",4,"maintenance","the process of keeping something in good condition","udržování něčeho v dobrém stavu"),
        Word("s308",4,"available","able to be used or obtained","dostupný k použití nebo získání"),

        Word("s401",5,"accommodation","a place to stay or live","místo k pobytu nebo bydlení"),
        Word("s402",5,"conscientious","careful and responsible","pečlivý a zodpovědný"),
        Word("s403",5,"entrepreneur","a person who starts and runs a business","člověk, který zakládá a vede podnik"),
        Word("s404",5,"miscellaneous","consisting of varied items","složený z různorodých položek"),
        Word("s405",5,"rhythm","a repeated pattern of sound or movement","opakující se vzorec zvuku nebo pohybu"),
        Word("s406",5,"maintenance","the process of preserving good condition","proces udržování dobrého stavu"),
        Word("s407",5,"questionnaire","a written set of questions","písemný soubor otázek"),
        Word("s408",5,"recommendation","a suggestion that something is suitable","doporučení, že je něco vhodné")
    )

    fun profile(): Profile {
        val xp = prefs.getInt(KEY_XP, 0)
        return Profile(
            level = levelForXp(xp),
            xp = xp,
            streak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            totalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0),
            totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0),
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect
        )
    }

    fun currentWord(): Word? =
        currentWordId?.let { id -> words.firstOrNull { it.id == id } }

    fun startOrResume(): Word =
        currentWord() ?: nextWord()

    fun nextWord(): Word {
        if (roundAnswered >= ROUND_SIZE) {
            resetRound()
        }

        val level = profile().level
        val eligible = words.filter { it.difficulty <= min(5, level + 1) }
        val recent = prefs.getString(KEY_RECENT, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val selected = eligible
            .filterNot { recent.contains(it.id) }
            .minByOrNull { seenCount(it.id) * 10 + kotlin.math.abs(it.difficulty - level) }
            ?: eligible.minByOrNull { seenCount(it.id) }
            ?: words.first()

        currentWordId = selected.id

        val updatedRecent = (recent + selected.id).takeLast(6)
        prefs.edit()
            .putString(KEY_CURRENT_WORD, selected.id)
            .putString(KEY_RECENT, updatedRecent.joinToString(","))
            .apply()

        return selected
    }

    fun evaluate(candidates: List<String>): Result {
        val current = startOrResume()
        val before = profile()
        val expected = normalizeSpelling(current.word)

        val correct = candidates.any { candidate ->
            val normalized = normalizeSpelling(candidate)
            normalized == expected ||
                levenshtein(normalized, expected) <= if (expected.length >= 9) 1 else 0
        }

        val newStreak = if (correct) before.streak + 1 else 0
        val earned = if (correct) 10 + current.difficulty * 4 +
            when {
                newStreak >= 10 -> 20
                newStreak >= 5 -> 10
                newStreak >= 3 -> 5
                else -> 0
            } else 2

        val newXp = before.xp + earned
        val newLevel = levelForXp(newXp)
        val promoted = newLevel > before.level

        roundAnswered += 1
        if (correct) roundCorrect += 1

        prefs.edit()
            .putInt(KEY_XP, newXp)
            .putInt(KEY_STREAK, newStreak)
            .putInt(KEY_BEST_STREAK, max(before.bestStreak, newStreak))
            .putInt(KEY_TOTAL_ANSWERED, before.totalAnswered + 1)
            .putInt(KEY_TOTAL_CORRECT, before.totalCorrect + if (correct) 1 else 0)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .putInt(seenKey(current.id), seenCount(current.id) + 1)
            .remove(KEY_CURRENT_WORD)
            .apply()

        currentWordId = null

        return Result(
            correct = correct,
            expected = current.word,
            xpEarned = earned,
            level = newLevel,
            promoted = promoted,
            streak = newStreak,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            roundFinished = roundAnswered >= ROUND_SIZE
        )
    }

    fun roundSummary(language: TriviaGameEngine.Language): String {
        val accuracy = if (roundAnswered == 0) 0 else roundCorrect * 100 / roundAnswered
        return if (language == TriviaGameEngine.Language.CS) {
            "Kolo dokončeno. " + roundCorrect + " z " + roundAnswered +
                " správně. Úspěšnost " + accuracy + " procent."
        } else {
            "Round complete. " + roundCorrect + " out of " + roundAnswered +
                " correct. Accuracy " + accuracy + " percent."
        }
    }

    fun resetRound() {
        roundAnswered = 0
        roundCorrect = 0
        currentWordId = null
        prefs.edit()
            .putInt(KEY_ROUND_ANSWERED, 0)
            .putInt(KEY_ROUND_CORRECT, 0)
            .remove(KEY_CURRENT_WORD)
            .apply()
    }

    private fun normalizeSpelling(value: String): String {
        val withoutMarks = Normalizer.normalize(
            value.lowercase(Locale.US),
            Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")

        return withoutMarks
            .replace("double ", "")
            .replace(Regex("[^a-z]"), "")
            .trim()
    }

    private fun levelForXp(xp: Int): Int = when {
        xp >= 900 -> 5
        xp >= 450 -> 4
        xp >= 200 -> 3
        xp >= 80 -> 2
        else -> 1
    }

    private fun seenCount(id: String): Int =
        prefs.getInt(seenKey(id), 0)

    private fun seenKey(id: String) = "seen_" + id

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

    companion object {
        private const val PREFS = "daw_spelling_bee"
        private const val KEY_XP = "xp"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_TOTAL_ANSWERED = "total_answered"
        private const val KEY_TOTAL_CORRECT = "total_correct"
        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val KEY_CURRENT_WORD = "current_word"
        private const val KEY_RECENT = "recent"
        private const val ROUND_SIZE = 10
    }
}
