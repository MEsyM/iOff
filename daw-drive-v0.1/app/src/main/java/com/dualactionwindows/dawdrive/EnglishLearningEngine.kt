package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class EnglishLearningEngine(context: Context) {

    enum class Audience { KID, ADULT }
    enum class Mode { LEARN, CHALLENGE }
    enum class LessonType { VOCABULARY, PHRASE, TRANSLATION, REPEAT }

    enum class Level(val audience: Audience, val rank: Int, val label: String) {
        KID_STARTER(Audience.KID, 1, "Starter"),
        KID_EXPLORER(Audience.KID, 2, "Explorer"),
        KID_SPEAKER(Audience.KID, 3, "Speaker"),
        KID_PRO(Audience.KID, 4, "Pro"),
        A1(Audience.ADULT, 1, "A1"),
        A2(Audience.ADULT, 2, "A2"),
        B1(Audience.ADULT, 3, "B1"),
        B2(Audience.ADULT, 4, "B2"),
        C1(Audience.ADULT, 5, "C1");

        companion object {
            fun defaultFor(audience: Audience): Level =
                if (audience == Audience.KID) KID_STARTER else A2
        }
    }

    data class ProfileSettings(
        val playerId: Int,
        val playerName: String,
        val audience: Audience,
        val level: Level,
        val mode: Mode,
        val xp: Int,
        val streak: Int,
        val bestStreak: Int,
        val answered: Int,
        val correct: Int
    )

    data class LessonItem(
        val id: String,
        val audience: Audience,
        val levelRank: Int,
        val type: LessonType,
        val topic: String,
        val promptCs: String,
        val promptEn: String,
        val answers: List<String>,
        val teaching: String
    )

    data class AnswerResult(
        val correct: Boolean,
        val expected: String,
        val teaching: String,
        val xpEarned: Int,
        val streak: Int,
        val roundFinished: Boolean,
        val roundAnswered: Int,
        val roundCorrect: Int
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val family = FamilyGameEngine(context)
    private var currentId: String? = prefs.getString(KEY_CURRENT_ID, null)
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)

    fun playerNames(): List<String> = family.playerNames()

    fun selectedPlayerId(): Int =
        prefs.getInt(KEY_SELECTED_PLAYER, 0).coerceIn(0, max(0, playerNames().lastIndex))

    fun setSelectedPlayer(playerId: Int) {
        prefs.edit().putInt(KEY_SELECTED_PLAYER, playerId).apply()
        currentId = null
        prefs.edit().remove(KEY_CURRENT_ID).apply()
    }

    fun settings(playerId: Int = selectedPlayerId()): ProfileSettings {
        val names = playerNames()
        val id = playerId.coerceIn(0, max(0, names.lastIndex))
        val audience = runCatching {
            Audience.valueOf(prefs.getString(audienceKey(id), Audience.ADULT.name) ?: Audience.ADULT.name)
        }.getOrDefault(Audience.ADULT)
        val defaultLevel = Level.defaultFor(audience)
        val level = runCatching {
            Level.valueOf(prefs.getString(levelKey(id), defaultLevel.name) ?: defaultLevel.name)
        }.getOrDefault(defaultLevel).let {
            if (it.audience == audience) it else defaultLevel
        }
        val mode = runCatching {
            Mode.valueOf(prefs.getString(modeKey(id), Mode.LEARN.name) ?: Mode.LEARN.name)
        }.getOrDefault(Mode.LEARN)

        return ProfileSettings(
            playerId = id,
            playerName = names.getOrElse(id) { "Player " + (id + 1) },
            audience = audience,
            level = level,
            mode = mode,
            xp = prefs.getInt(xpKey(id), 0),
            streak = prefs.getInt(streakKey(id), 0),
            bestStreak = prefs.getInt(bestStreakKey(id), 0),
            answered = prefs.getInt(answeredKey(id), 0),
            correct = prefs.getInt(correctKey(id), 0)
        )
    }

    fun saveSettings(playerId: Int, audience: Audience, level: Level, mode: Mode) {
        val safeLevel = if (level.audience == audience) level else Level.defaultFor(audience)
        prefs.edit()
            .putString(audienceKey(playerId), audience.name)
            .putString(levelKey(playerId), safeLevel.name)
            .putString(modeKey(playerId), mode.name)
            .apply()
        if (playerId == selectedPlayerId()) {
            currentId = null
            prefs.edit().remove(KEY_CURRENT_ID).apply()
        }
    }

    fun startOrResume(): LessonItem {
        if (roundAnswered >= ROUND_SIZE) resetRound()
        return currentItem() ?: nextItem()
    }

    fun currentItem(): LessonItem? =
        currentId?.let { id -> CONTENT.firstOrNull { it.id == id } }

    fun nextItem(): LessonItem {
        val profile = settings()
        val adaptive = adaptiveOffset(profile.playerId)
        val target = (profile.level.rank + adaptive).coerceIn(1, 5)
        val dueCounter = profile.answered

        val candidates = CONTENT
            .asSequence()
            .filter { it.audience == profile.audience }
            .filter { abs(it.levelRank - target) <= 1 || it.levelRank <= target }
            .filterNot { it.id in recentIds(profile.playerId) }
            .map { item ->
                val seen = prefs.getInt(seenKey(profile.playerId, item.id), 0)
                val wrong = prefs.getInt(wrongKey(profile.playerId, item.id), 0)
                val due = prefs.getInt(dueKey(profile.playerId, item.id), 0)
                var score = 100.0 - abs(item.levelRank - target) * 18.0
                if (seen == 0) score += 28.0
                if (wrong > 0) score += min(24.0, wrong * 6.0)
                if (due <= dueCounter) score += 22.0
                item to score
            }
            .sortedByDescending { it.second }
            .take(12)
            .toList()
            .ifEmpty {
                CONTENT.filter { it.audience == profile.audience }.map { it to 1.0 }
            }

        val floor = candidates.minOf { it.second }
        val weighted = candidates.map { it.first to max(1.0, it.second - floor + 6.0) }
        val total = weighted.sumOf { it.second }
        var pick = Random.nextDouble(total)
        val chosen = weighted.firstOrNull { (_, weight) ->
            pick -= weight
            pick <= 0.0
        }?.first ?: weighted.last().first

        currentId = chosen.id
        val recent = recentIds(profile.playerId).toMutableList().apply {
            add(chosen.id)
            while (size > RECENT_WINDOW) removeAt(0)
        }
        prefs.edit()
            .putString(KEY_CURRENT_ID, chosen.id)
            .putString(recentKey(profile.playerId), recent.joinToString(","))
            .apply()
        return chosen
    }

    fun isCurrentAnswerCorrect(candidates: List<String>): Boolean {
        val item = currentItem() ?: startOrResume()
        return candidates
            .filter { it.isNotBlank() }
            .any { actual -> item.answers.any { expected -> matches(actual, expected) } }
    }

    fun answerCandidates(candidates: List<String>): AnswerResult {
        val profile = settings()
        val item = currentItem() ?: startOrResume()
        val correct = candidates
            .filter { it.isNotBlank() }
            .any { actual -> item.answers.any { expected -> matches(actual, expected) } }

        val newStreak = if (correct) profile.streak + 1 else 0
        val xp = if (correct) {
            10 + item.levelRank * 2 + when {
                newStreak >= 10 -> 12
                newStreak >= 5 -> 7
                newStreak >= 3 -> 3
                else -> 0
            }
        } else if (profile.mode == Mode.LEARN) 2 else 0

        val answered = profile.answered + 1
        val correctTotal = profile.correct + if (correct) 1 else 0
        roundAnswered += 1
        if (correct) roundCorrect += 1

        val seen = prefs.getInt(seenKey(profile.playerId, item.id), 0) + 1
        val wrong = prefs.getInt(wrongKey(profile.playerId, item.id), 0) + if (correct) 0 else 1
        val interval = when {
            !correct -> 2
            seen >= 4 && wrong == 0 -> 24
            seen >= 2 -> 10
            else -> 5
        }

        updateAdaptive(profile.playerId, correct)
        currentId = null

        prefs.edit()
            .putInt(xpKey(profile.playerId), profile.xp + xp)
            .putInt(streakKey(profile.playerId), newStreak)
            .putInt(bestStreakKey(profile.playerId), max(profile.bestStreak, newStreak))
            .putInt(answeredKey(profile.playerId), answered)
            .putInt(correctKey(profile.playerId), correctTotal)
            .putInt(seenKey(profile.playerId, item.id), seen)
            .putInt(wrongKey(profile.playerId, item.id), wrong)
            .putInt(dueKey(profile.playerId, item.id), answered + interval)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .remove(KEY_CURRENT_ID)
            .apply()

        return AnswerResult(
            correct = correct,
            expected = item.answers.first(),
            teaching = item.teaching,
            xpEarned = xp,
            streak = newStreak,
            roundFinished = roundAnswered >= ROUND_SIZE,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect
        )
    }

    fun skipCurrent(): Boolean {
        currentId = null
        roundAnswered += 1
        prefs.edit()
            .remove(KEY_CURRENT_ID)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .apply()
        return roundAnswered >= ROUND_SIZE
    }

    fun roundSummary(): String {
        val p = settings()
        val pct = if (roundAnswered == 0) 0 else roundCorrect * 100 / roundAnswered
        return p.playerName + ": " + roundCorrect + " z " + roundAnswered +
            " správně, " + pct + " procent. Celkem " + p.xp + " XP."
    }

    fun resetRound() {
        currentId = null
        roundAnswered = 0
        roundCorrect = 0
        prefs.edit()
            .remove(KEY_CURRENT_ID)
            .putInt(KEY_ROUND_ANSWERED, 0)
            .putInt(KEY_ROUND_CORRECT, 0)
            .apply()
    }

    private fun adaptiveOffset(playerId: Int): Int =
        prefs.getInt(adaptiveKey(playerId), 0).coerceIn(-1, 1)

    private fun updateAdaptive(playerId: Int, correct: Boolean) {
        val key = adaptiveKey(playerId)
        val goodKey = goodRunKey(playerId)
        val badKey = badRunKey(playerId)
        var offset = adaptiveOffset(playerId)
        var good = prefs.getInt(goodKey, 0)
        var bad = prefs.getInt(badKey, 0)
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
        prefs.edit().putInt(key, offset).putInt(goodKey, good).putInt(badKey, bad).apply()
    }

    private fun recentIds(playerId: Int): List<String> =
        prefs.getString(recentKey(playerId), "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun matches(actual: String, expected: String): Boolean {
        val a = normalize(actual)
        val e = normalize(expected)
        if (a == e) return true
        if (e.length >= 4 && a.contains(e)) return true
        if (a.length >= 5 && e.contains(a)) return true
        val tolerance = when {
            e.length <= 4 -> 0
            e.length <= 8 -> 1
            else -> 2
        }
        return levenshtein(a, e) <= tolerance
    }

    private fun normalize(value: String): String {
        val s = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return s.replace(Regex("[^a-z0-9\\s']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), previous[j] + cost)
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    private fun audienceKey(id: Int) = "p_" + id + "_audience"
    private fun levelKey(id: Int) = "p_" + id + "_level"
    private fun modeKey(id: Int) = "p_" + id + "_mode"
    private fun xpKey(id: Int) = "p_" + id + "_xp"
    private fun streakKey(id: Int) = "p_" + id + "_streak"
    private fun bestStreakKey(id: Int) = "p_" + id + "_best_streak"
    private fun answeredKey(id: Int) = "p_" + id + "_answered"
    private fun correctKey(id: Int) = "p_" + id + "_correct"
    private fun adaptiveKey(id: Int) = "p_" + id + "_adaptive"
    private fun goodRunKey(id: Int) = "p_" + id + "_good_run"
    private fun badRunKey(id: Int) = "p_" + id + "_bad_run"
    private fun recentKey(id: Int) = "p_" + id + "_recent"
    private fun seenKey(id: Int, itemId: String) = "p_" + id + "_" + itemId + "_seen"
    private fun wrongKey(id: Int, itemId: String) = "p_" + id + "_" + itemId + "_wrong"
    private fun dueKey(id: Int, itemId: String) = "p_" + id + "_" + itemId + "_due"

    companion object {
        private const val PREFS_NAME = "lone_rider_english"
        private const val KEY_SELECTED_PLAYER = "selected_player"
        private const val KEY_CURRENT_ID = "current_id"
        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val ROUND_SIZE = 10
        private const val RECENT_WINDOW = 8

        val CONTENT = listOf(
            LessonItem("k001",Audience.KID,1,LessonType.VOCABULARY,"animals","Jak se anglicky řekne pes?","How do you say dog in Czech?",listOf("dog"),"Dog znamená pes."),
            LessonItem("k002",Audience.KID,1,LessonType.VOCABULARY,"animals","Jak se anglicky řekne kočka?","How do you say cat in Czech?",listOf("cat"),"Cat znamená kočka."),
            LessonItem("k003",Audience.KID,1,LessonType.VOCABULARY,"colors","Jak se anglicky řekne modrá?","How do you say blue in Czech?",listOf("blue"),"Blue znamená modrá."),
            LessonItem("k004",Audience.KID,1,LessonType.PHRASE,"basics","Řekni anglicky: Dobré ráno.","Say in English: Good morning.",listOf("good morning"),"Good morning je Dobré ráno."),
            LessonItem("k005",Audience.KID,1,LessonType.TRANSLATION,"school","Řekni anglicky: škola.","Say in English: school.",listOf("school"),"School znamená škola."),
            LessonItem("k006",Audience.KID,2,LessonType.PHRASE,"food","Řekni anglicky: Mám hlad.","Say in English: I am hungry.",listOf("i am hungry","i'm hungry"),"I'm hungry znamená Mám hlad."),
            LessonItem("k007",Audience.KID,2,LessonType.PHRASE,"feelings","Řekni anglicky: Jsem unavený.","Say in English: I am tired.",listOf("i am tired","i'm tired"),"I'm tired znamená Jsem unavený."),
            LessonItem("k008",Audience.KID,2,LessonType.VOCABULARY,"football","Jak se anglicky řekne brankář?","How do you say goalkeeper?",listOf("goalkeeper","goalie"),"Goalkeeper znamená brankář."),
            LessonItem("k009",Audience.KID,2,LessonType.PHRASE,"family","Řekni anglicky: To je můj bratr.","Say in English: This is my brother.",listOf("this is my brother"),"This is my brother znamená To je můj bratr."),
            LessonItem("k010",Audience.KID,2,LessonType.REPEAT,"speaking","Zopakuj anglicky: Can I play?","Repeat: Can I play?",listOf("can i play"),"Can I play znamená Můžu si hrát?"),
            LessonItem("k011",Audience.KID,3,LessonType.PHRASE,"school","Řekni anglicky: Nerozumím tomu.","Say in English: I don't understand.",listOf("i don't understand","i do not understand"),"I don't understand znamená Nerozumím."),
            LessonItem("k012",Audience.KID,3,LessonType.PHRASE,"travel","Řekni anglicky: Kde je toaleta?","Say in English: Where is the toilet?",listOf("where is the toilet","where is the bathroom"),"Where is the toilet? je praktická cestovní fráze."),
            LessonItem("k013",Audience.KID,3,LessonType.TRANSLATION,"time","Řekni anglicky: Kolik je hodin?","Say in English: What time is it?",listOf("what time is it"),"What time is it? znamená Kolik je hodin?"),
            LessonItem("k014",Audience.KID,3,LessonType.VOCABULARY,"nature","Jak se anglicky řekne bouřka?","How do you say storm?",listOf("storm","thunderstorm"),"Storm znamená bouřka."),
            LessonItem("k015",Audience.KID,4,LessonType.PHRASE,"conversation","Řekni anglicky: Mohl bys mi pomoct?","Say in English: Could you help me?",listOf("could you help me","can you help me"),"Could you help me? je zdvořilá žádost o pomoc."),
            LessonItem("k016",Audience.KID,4,LessonType.TRANSLATION,"school","Řekni anglicky: Zapomněl jsem domácí úkol.","Say in English: I forgot my homework.",listOf("i forgot my homework","i've forgotten my homework"),"I forgot my homework znamená Zapomněl jsem domácí úkol."),
            LessonItem("k017",Audience.KID,4,LessonType.PHRASE,"football","Řekni anglicky: Přihraj mi!","Say in English: Pass it to me!",listOf("pass it to me","pass to me"),"Pass it to me! znamená Přihraj mi."),
            LessonItem("k018",Audience.KID,4,LessonType.PHRASE,"conversation","Řekni anglicky: Co budeme dělat dál?","Say in English: What are we going to do next?",listOf("what are we going to do next","what will we do next"),"What are we going to do next? je běžná otázka."),

            LessonItem("a001",Audience.ADULT,1,LessonType.VOCABULARY,"travel","Jak se anglicky řekne jízdenka?","Say the English word for jízdenka.",listOf("ticket"),"Ticket znamená jízdenka nebo vstupenka."),
            LessonItem("a002",Audience.ADULT,1,LessonType.PHRASE,"travel","Řekni anglicky: Kde je nádraží?","Say in English: Where is the train station?",listOf("where is the train station","where is the station"),"Where is the train station? je základní cestovní fráze."),
            LessonItem("a003",Audience.ADULT,1,LessonType.PHRASE,"restaurant","Řekni anglicky: Účet, prosím.","Say in English: The bill, please.",listOf("the bill please","bill please","can i have the bill please"),"Can I have the bill, please? zní přirozeně a zdvořile."),
            LessonItem("a004",Audience.ADULT,1,LessonType.PHRASE,"hotel","Řekni anglicky: Mám rezervaci.","Say in English: I have a reservation.",listOf("i have a reservation","i've got a reservation"),"I have a reservation je standardní fráze při check-inu."),
            LessonItem("a005",Audience.ADULT,2,LessonType.PHRASE,"travel","Řekni anglicky: Kolik stojí jízdenka?","Say in English: How much is the ticket?",listOf("how much is the ticket","how much does the ticket cost"),"Obě varianty jsou správně."),
            LessonItem("a006",Audience.ADULT,2,LessonType.PHRASE,"hotel","Zeptej se anglicky, jestli je snídaně v ceně.","Ask whether breakfast is included.",listOf("is breakfast included","is breakfast included in the price"),"Is breakfast included in the price? je přirozená varianta."),
            LessonItem("a007",Audience.ADULT,2,LessonType.PHRASE,"phone","Řekni anglicky: Můžete mi zavolat později?","Say in English: Could you call me later?",listOf("can you call me later","could you call me later"),"Could you call me later? je zdvořilejší."),
            LessonItem("a008",Audience.ADULT,2,LessonType.VOCABULARY,"business","Jak se anglicky řekne obchodní nabídka?","Say an English word for a commercial quote.",listOf("quote","quotation","offer"),"Quote nebo quotation se často používá pro cenovou nabídku."),
            LessonItem("a009",Audience.ADULT,3,LessonType.PHRASE,"business","Řekni anglicky: Pošlu vám nabídku do zítřka.","Say in English: I will send you the quote by tomorrow.",listOf("i will send you the quote by tomorrow","i'll send you the quote by tomorrow"),"By tomorrow znamená nejpozději do zítřka."),
            LessonItem("a010",Audience.ADULT,3,LessonType.PHRASE,"business","Řekni anglicky: Potřebujeme potvrdit termín dodání.","Say in English: We need to confirm the delivery date.",listOf("we need to confirm the delivery date","we need to confirm the delivery time"),"Delivery date je termín dodání."),
            LessonItem("a011",Audience.ADULT,3,LessonType.PHRASE,"smalltalk","Řekni anglicky: Jak dlouho už pracujete v této firmě?","Say in English: How long have you worked at this company?",listOf("how long have you worked at this company","how long have you been working at this company"),"Present perfect se používá pro děj trvající do současnosti."),
            LessonItem("a012",Audience.ADULT,3,LessonType.REPEAT,"pronunciation","Zopakuj: I'd like to reschedule our meeting.","Repeat: I'd like to reschedule our meeting.",listOf("i'd like to reschedule our meeting","i would like to reschedule our meeting"),"Reschedule znamená přesunout na jiný termín."),
            LessonItem("a013",Audience.ADULT,4,LessonType.PHRASE,"business","Řekni anglicky: Můžeme se domluvit na kompromisu?","Say in English: Can we find a compromise?",listOf("can we agree on a compromise","could we agree on a compromise","can we find a compromise"),"Can we find a compromise? zní přirozeně při vyjednávání."),
            LessonItem("a014",Audience.ADULT,4,LessonType.PHRASE,"business","Řekni anglicky: Tato cena platí pouze při objednávce do konce týdne.","Say in English: This price only applies if you order by the end of the week.",listOf("this price is valid only if you order by the end of the week","this price only applies if you order by the end of the week"),"Only applies if je běžná obchodní formulace."),
            LessonItem("a015",Audience.ADULT,4,LessonType.PHRASE,"conversation","Řekni anglicky: Nechci dělat ukvapené závěry.","Say in English: I don't want to jump to conclusions.",listOf("i don't want to jump to conclusions","i do not want to jump to conclusions"),"Jump to conclusions je běžný idiom."),
            LessonItem("a016",Audience.ADULT,4,LessonType.TRANSLATION,"business","Přelož: Pokud budeme mít specifikaci dnes, můžeme nabídku dokončit zítra.","Translate: If we get the specification today, we can finish the quote tomorrow.",listOf("if we have the specification today we can finish the quote tomorrow","if we get the specification today we can finish the quote tomorrow"),"V podmínce pro reálnou možnost používáme present simple + can."),
            LessonItem("a017",Audience.ADULT,5,LessonType.PHRASE,"business","Řekni anglicky: Rád bych objasnil jeden bod, než budeme pokračovat.","Say in English: I'd like to clarify one point before we continue.",listOf("i'd like to clarify one point before we continue","i would like to clarify one point before we continue"),"Clarify one point je užitečná formální fráze."),
            LessonItem("a018",Audience.ADULT,5,LessonType.PHRASE,"negotiation","Řekni anglicky: Pokud se přiblížíte této ceně, můžeme obchod uzavřít dnes.","Say in English: If you can get closer to this price, we can close the deal today.",listOf("if you can get closer to this price we can close the deal today","if you get closer to this price we can close the deal today"),"Close the deal znamená uzavřít obchod."),
            LessonItem("a019",Audience.ADULT,5,LessonType.PHRASE,"business","Řekni anglicky: Nejde jen o cenu, ale i o specifikaci a rozsah dodávky.","Say in English: It's not just about the price but also the specification and scope of supply.",listOf("it's not just about the price but also the specification and scope of supply","it is not just about the price but also the specification and scope of supply"),"Scope of supply znamená rozsah dodávky."),
            LessonItem("a020",Audience.ADULT,5,LessonType.REPEAT,"conversation","Zopakuj: From my perspective, the main issue is timing rather than cost.","Repeat: From my perspective, the main issue is timing rather than cost.",listOf("from my perspective the main issue is timing rather than cost"),"Rather than znamená spíše než.")
        )
    }
}
