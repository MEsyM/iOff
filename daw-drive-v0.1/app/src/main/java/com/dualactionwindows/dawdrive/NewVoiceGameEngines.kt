package com.dualactionwindows.dawdrive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private fun normalizeVoice(raw: String): String =
    Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

private fun aliases(obj: JSONObject, key: String = "answers"): List<String> {
    val arr = obj.optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
}

class NameThatSoundEngine(context: Context) {
    data class Round(val id:String,val cue:String,val prompt:String,val answers:List<String>,val explanation:String)
    data class Result(val correct:Boolean,val answer:String,val explanation:String,val finished:Boolean)

    private val content = LiveGameContent(context)
    private var data = content.items("name_that_sound", fallback())
    private var index = 0
    private var answered = 0
    private var correct = 0

    init { content.refresh("name_that_sound") { data = it } }

    fun current(): Round {
        if (data.length()==0) data=fallback()
        val o=data.getJSONObject(index % data.length())
        return Round(
            o.optString("id","sound_$index"),
            o.optString("cue", o.optString("prompt")),
            o.optString("prompt","What sound is this?"),
            aliases(o),
            o.optString("explanation")
        )
    }
    fun answer(raw:String): Result {
        val r=current()
        val n=normalizeVoice(raw)
        val ok=r.answers.any { a -> val x=normalizeVoice(a); n==x || n.contains(x) || x.contains(n) }
        answered++; if(ok) correct++
        index=(index+1)%data.length().coerceAtLeast(1)
        return Result(ok,r.answers.firstOrNull().orEmpty(),r.explanation,answered%10==0)
    }
    fun skip():Boolean { answered++; index=(index+1)%data.length().coerceAtLeast(1); return answered%10==0 }
    fun score(cs:Boolean)=if(cs) "Zvuky: $correct správně z $answered." else "Sounds: $correct correct out of $answered."

    private fun fallback()=JSONArray(
        """[
          {"id":"sound_dog","cue":"Haf haf!","prompt":"Které zvíře vydává tento zvuk?","answers":["pes","dog"],"explanation":"Je to pes."},
          {"id":"sound_cat","cue":"Mňau!","prompt":"Které zvíře vydává tento zvuk?","answers":["kočka","cat"],"explanation":"Je to kočka."},
          {"id":"sound_cow","cue":"Búú!","prompt":"Které zvíře vydává tento zvuk?","answers":["kráva","cow"],"explanation":"Je to kráva."},
          {"id":"sound_train","cue":"Čú čú!","prompt":"Co může vydávat tento zvuk?","answers":["vlak","train"],"explanation":"Je to vlak."}
        ]"""
    )
}

class StoryAdventureEngine(context: Context) {
    data class Scene(val id:String,val narration:String,val choices:List<Choice>,val ending:Boolean)
    data class Choice(val label:String,val aliases:List<String>,val next:String)
    private val content=LiveGameContent(context)
    private var data=content.items("story_adventure",fallback())
    private var sceneId="start"
    init { content.refresh("story_adventure"){data=it} }

    fun current():Scene {
        val o=find(sceneId) ?: data.optJSONObject(0) ?: fallback().getJSONObject(0)
        val ch=o.optJSONArray("choices") ?: JSONArray()
        return Scene(o.optString("id","start"),o.optString("narration"),(0 until ch.length()).mapNotNull{i->
            ch.optJSONObject(i)?.let { c -> Choice(c.optString("label"),aliases(c,"aliases")+c.optString("label"),c.optString("next")) }
        },o.optBoolean("ending",false))
    }
    fun choose(raw:String):Boolean {
        val n=normalizeVoice(raw)
        val choice=current().choices.firstOrNull { c -> c.aliases.any { a -> val x=normalizeVoice(a); n==x || n.contains(x) } } ?: return false
        sceneId=choice.next
        return true
    }
    fun restart(){sceneId="start"}
    private fun find(id:String):JSONObject?=(0 until data.length()).mapNotNull{data.optJSONObject(it)}.firstOrNull{it.optString("id")==id}
    private fun fallback()=JSONArray(
        """[
          {"id":"start","narration":"Stojíš před opuštěným hradem. Vlevo vede cesta do lesa, vpravo jsou otevřené dveře hradu.","choices":[{"label":"les","aliases":["les","do lesa","forest"],"next":"forest"},{"label":"hrad","aliases":["hrad","dovnitř","castle"],"next":"castle"}]},
          {"id":"forest","narration":"V lese najdeš zářící klíč. Bereš ho a vracíš se ke hradu.","choices":[{"label":"vezmu klíč","aliases":["klíč","vezmu","ano","key"],"next":"win"}]},
          {"id":"castle","narration":"Dveře do věže jsou zamčené. Můžeš se vrátit ven.","choices":[{"label":"vrátit se","aliases":["zpět","vrátit","back"],"next":"start"}]},
          {"id":"win","narration":"Klíč otevřel věž a uvnitř je mapa k dalšímu dobrodružství. Vyhrál jsi.","choices":[],"ending":true}
        ]"""
    )
}

class WordChainEngine(context: Context) {
    private val content=LiveGameContent(context)
    private var rules=content.items("word_chain",fallback())
    private var last:String?=null
    private val used=linkedSetOf<String>()
    private var score=0
    init { content.refresh("word_chain"){rules=it} }

    fun reset(){last=null;used.clear();score=0}
    fun prompt(cs:Boolean):String {
        val l=last
        return if(l==null) {
            if(cs) "Řekni první slovo." else "Say the first word."
        } else {
            val c=normalizeVoice(l).lastOrNull() ?: '?'
            if(cs) "Poslední slovo je $l. Řekni slovo na písmeno $c." else "The last word is $l. Say a word starting with $c."
        }
    }
    fun submit(raw:String):Pair<Boolean,String> {
        val word=normalizeVoice(raw).split(" ").firstOrNull().orEmpty()
        if(word.length<2) return false to "too_short"
        if(word in used) return false to "used"
        val expected=last?.let{normalizeVoice(it).lastOrNull()}
        if(expected!=null && word.firstOrNull()!=expected) return false to "wrong_letter"
        val banned=(0 until rules.length()).mapNotNull{rules.optJSONObject(it)?.optString("word")}.map(::normalizeVoice)
        if(word in banned) return false to "blocked"
        used+=word;last=word;score++
        return true to word
    }
    fun score()=score
    private fun fallback()=JSONArray("""[{"word":"xxx"},{"word":"yyy"}]""")
}

class AiEnglishConversationEngine(private val context: Context) {
    enum class Level { A1, A2, B1, B2, C1, C2 }
    enum class Topic(val label: String) {
        TRAVEL("Travel"),
        BUSINESS("Business"),
        DAILY_LIFE("Daily life"),
        KIDS("Kids")
    }
    enum class Scenario(val label: String) {
        HOTEL("Hotel"),
        AIRPORT("Airport"),
        RESTAURANT("Restaurant"),
        MEETING("Meeting"),
        SALES_CALL("Sales call"),
        CUSTOMER_COMPLAINT("Customer complaint")
    }

    data class Settings(
        val level: Level,
        val topic: Topic,
        val scenario: Scenario,
        val gentleCorrections: Boolean
    )

    data class Turn(
        val id: String,
        val rolePrompt: String,
        val expected: List<String> = emptyList(),
        val feedback: String = "",
        val next: String? = null
    )

    data class Reply(
        val natural: Boolean,
        val feedback: String,
        val correction: String,
        val nextPrompt: String,
        val grammar: Int,
        val vocabulary: Int,
        val fluency: Int,
        val pronunciation: Int,
        val sessionFinished: Boolean,
        val summary: String
    )

    data class SessionSnapshot(
        val level: String,
        val topic: String,
        val scenario: String,
        val turns: Int,
        val totalTurns: Int,
        val sessionCount: Int,
        val grammar: Int,
        val vocabulary: Int,
        val fluency: Int,
        val pronunciation: Int,
        val transcript: List<Pair<String, String>>
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var currentPrompt = ""
    private var answered = 0
    private var grammarTotal = 0
    private var vocabularyTotal = 0
    private var fluencyTotal = 0
    private var pronunciationTotal = 0
    private val history = mutableListOf<Pair<String, String>>()
    private var sessionId = UUID.randomUUID().toString()

    init {
        reset()
    }

    fun settings(): Settings {
        val level = runCatching { Level.valueOf(prefs.getString(KEY_LEVEL, Level.A2.name) ?: Level.A2.name) }.getOrDefault(Level.A2)
        val topic = runCatching { Topic.valueOf(prefs.getString(KEY_TOPIC, Topic.TRAVEL.name) ?: Topic.TRAVEL.name) }.getOrDefault(Topic.TRAVEL)
        val scenario = runCatching { Scenario.valueOf(prefs.getString(KEY_SCENARIO, Scenario.HOTEL.name) ?: Scenario.HOTEL.name) }.getOrDefault(Scenario.HOTEL)
        return Settings(level, topic, scenario, prefs.getBoolean(KEY_GENTLE_CORRECTIONS, true))
    }

    fun saveSettings(level: Level, topic: Topic, scenario: Scenario, gentleCorrections: Boolean) {
        prefs.edit()
            .putString(KEY_LEVEL, level.name)
            .putString(KEY_TOPIC, topic.name)
            .putString(KEY_SCENARIO, scenario.name)
            .putBoolean(KEY_GENTLE_CORRECTIONS, gentleCorrections)
            .apply()
        reset()
    }

    fun current(): Turn = Turn("turn_" + answered, currentPrompt)

    fun answerAsync(raw: String, callback: (Reply) -> Unit) {
        val spoken = raw.trim()
        if (spoken.isBlank()) {
            callback(localReply(spoken))
            return
        }

        val snapshotSettings = settings()
        val snapshotHistory = history.toList()
        Thread {
            val reply = runCatching {
                requestBackend(spoken, snapshotSettings, snapshotHistory)
            }.getOrElse {
                localReply(spoken)
            }
            applyReply(spoken, reply)
            callback(reply.copy(
                sessionFinished = answered >= SESSION_TURNS,
                summary = if (answered >= SESSION_TURNS) summary() else ""
            ))
        }.start()
    }

    fun reset() {
        sessionId = UUID.randomUUID().toString()
        answered = 0
        grammarTotal = 0
        vocabularyTotal = 0
        fluencyTotal = 0
        pronunciationTotal = 0
        history.clear()
        currentPrompt = openingPrompt(settings().scenario)
    }

    fun score(): String = summary()

    fun sessionSnapshot(): SessionSnapshot {
        val s = settings()
        val safeTurns = answered.coerceAtLeast(1)
        return SessionSnapshot(
            level = s.level.name,
            topic = s.topic.name.lowercase(Locale.US),
            scenario = s.scenario.name.lowercase(Locale.US),
            turns = answered,
            totalTurns = prefs.getInt(KEY_TOTAL_TURNS, 0),
            sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0),
            grammar = grammarTotal / safeTurns,
            vocabulary = vocabularyTotal / safeTurns,
            fluency = fluencyTotal / safeTurns,
            pronunciation = pronunciationTotal / safeTurns,
            transcript = history.toList()
        )
    }

    private fun applyReply(spoken: String, reply: Reply) {
        answered += 1
        grammarTotal += reply.grammar.coerceIn(0, 100)
        vocabularyTotal += reply.vocabulary.coerceIn(0, 100)
        fluencyTotal += reply.fluency.coerceIn(0, 100)
        pronunciationTotal += reply.pronunciation.coerceIn(0, 100)
        history.add("user" to spoken)
        history.add("assistant" to reply.nextPrompt)
        while (history.size > 12) history.removeAt(0)
        currentPrompt = if (answered >= SESSION_TURNS) {
            openingPrompt(settings().scenario)
        } else {
            reply.nextPrompt.ifBlank { followUpPrompt(settings().scenario, answered) }
        }
        if (answered >= SESSION_TURNS) {
            persistSessionSummary()
        }
    }

    private fun requestBackend(spoken: String, s: Settings, previous: List<Pair<String, String>>): Reply {
        val payload = JSONObject()
            .put("session_id", sessionId)
            .put("level", s.level.name)
            .put("topic", s.topic.name.lowercase(Locale.US))
            .put("scenario", s.scenario.name.lowercase(Locale.US))
            .put("user_text", spoken)
            .put("turn", answered + 1)
            .put("gentle_corrections", s.gentleCorrections)
            .put("history", JSONArray().apply {
                previous.takeLast(12).forEach { (role, text) ->
                    put(JSONObject().put("role", role).put("text", text))
                }
            })

        val connection = (URL(BASE_URL + "/v1/english/conversation/turn").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 18000
            useCaches = false
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "LoneRider-Android")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("English coach HTTP " + code)
            val o = JSONObject(raw)
            return Reply(
                natural = o.optBoolean("natural", true),
                feedback = o.optString("feedback", "Good. Keep going."),
                correction = o.optString("correction", ""),
                nextPrompt = o.optString("next_prompt", followUpPrompt(s.scenario, answered + 1)),
                grammar = o.optInt("grammar", 75),
                vocabulary = o.optInt("vocabulary", 75),
                fluency = o.optInt("fluency", 75),
                pronunciation = o.optInt("pronunciation", 75),
                sessionFinished = false,
                summary = ""
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun localReply(spoken: String): Reply {
        val n = normalizeVoice(spoken)
        val words = n.split(" ").filter { it.isNotBlank() }
        val hasVerb = listOf("am","is","are","have","has","want","need","would","could","can","like","think","work","book","order","help").any { it in words }
        val grammar = when {
            spoken.isBlank() -> 25
            words.size >= 5 && hasVerb -> 82
            words.size >= 3 -> 70
            else -> 55
        }
        val vocabulary = (58 + words.distinct().size * 4).coerceAtMost(88)
        val fluency = (55 + words.size * 4).coerceAtMost(88)
        val pronunciation = if (words.size >= 4) 78 else 68
        val natural = words.size >= 3
        val correction = when {
            spoken.isBlank() -> "Please answer with a short English sentence."
            !hasVerb && words.size >= 3 -> "Try adding a verb to make a complete sentence."
            words.size < 3 -> "Try a slightly fuller sentence."
            else -> ""
        }
        val s = settings()
        val feedback = if (correction.isBlank()) {
            "Good. That sounds natural."
        } else if (s.gentleCorrections) {
            "Nice try. " + correction
        } else {
            correction
        }
        return Reply(
            natural = natural,
            feedback = feedback,
            correction = correction,
            nextPrompt = followUpPrompt(s.scenario, answered + 1),
            grammar = grammar,
            vocabulary = vocabulary,
            fluency = fluency,
            pronunciation = pronunciation,
            sessionFinished = false,
            summary = ""
        )
    }

    private fun summary(): String {
        if (answered <= 0) return "English conversation ready."
        val g = grammarTotal / answered
        val v = vocabularyTotal / answered
        val f = fluencyTotal / answered
        val p = pronunciationTotal / answered
        return "Session complete. Grammar $g, vocabulary $v, fluency $f, pronunciation $p out of 100."
    }

    private fun persistSessionSummary() {
        val count = prefs.getInt(KEY_SESSION_COUNT, 0) + 1
        val existing = prefs.getInt(KEY_TOTAL_TURNS, 0)
        prefs.edit()
            .putInt(KEY_SESSION_COUNT, count)
            .putInt(KEY_TOTAL_TURNS, existing + answered)
            .putInt(KEY_LAST_GRAMMAR, if (answered == 0) 0 else grammarTotal / answered)
            .putInt(KEY_LAST_VOCABULARY, if (answered == 0) 0 else vocabularyTotal / answered)
            .putInt(KEY_LAST_FLUENCY, if (answered == 0) 0 else fluencyTotal / answered)
            .putInt(KEY_LAST_PRONUNCIATION, if (answered == 0) 0 else pronunciationTotal / answered)
            .apply()
    }

    private fun openingPrompt(scenario: Scenario): String = when (scenario) {
        Scenario.HOTEL -> "Hello, welcome to the hotel. How can I help you today?"
        Scenario.AIRPORT -> "Good morning. May I see your passport and boarding pass, please?"
        Scenario.RESTAURANT -> "Welcome. Are you ready to order, or would you like a few more minutes?"
        Scenario.MEETING -> "Thanks for joining the meeting. Could you briefly introduce yourself and your goal for today?"
        Scenario.SALES_CALL -> "Hi, thanks for calling. Tell me what problem you are trying to solve."
        Scenario.CUSTOMER_COMPLAINT -> "I am sorry there is a problem with your order. Could you explain what happened?"
    }

    private fun followUpPrompt(scenario: Scenario, turn: Int): String {
        val prompts = when (scenario) {
            Scenario.HOTEL -> listOf(
                "Do you already have a reservation?",
                "How many nights will you be staying?",
                "Would you prefer one bed or two?",
                "Would you like breakfast included?",
                "Do you need help with your bags?",
                "What time would you like a wake-up call?",
                "Is there anything else I can arrange for you?"
            )
            Scenario.AIRPORT -> listOf(
                "Are you checking any bags today?",
                "Did you pack your bags yourself?",
                "Would you prefer a window or aisle seat?",
                "Do you have any liquids or restricted items?",
                "Your gate changed. How would you ask where the new gate is?",
                "Your flight is delayed. What would you like to ask the airline?",
                "You have arrived. Ask me where to collect your luggage."
            )
            Scenario.RESTAURANT -> listOf(
                "What would you like to drink?",
                "Would you like to hear today's special?",
                "How would you like your meal prepared?",
                "Do you have any allergies?",
                "Would you like anything else with your meal?",
                "How is everything tasting?",
                "Would you like dessert or the bill?"
            )
            Scenario.MEETING -> listOf(
                "What is the biggest priority for your team?",
                "What result would make this project successful?",
                "Could you explain the main risk?",
                "What do you recommend we do next?",
                "Who needs to approve the decision?",
                "When could your team deliver the first version?",
                "Can you summarize the agreement in one sentence?"
            )
            Scenario.SALES_CALL -> listOf(
                "What are you using today?",
                "What is the biggest problem with the current solution?",
                "What matters most: price, speed, quality, or support?",
                "Who else is involved in the decision?",
                "What timeline are you working with?",
                "What would stop you from moving forward?",
                "How would you ask for the next meeting?"
            )
            Scenario.CUSTOMER_COMPLAINT -> listOf(
                "When did you first notice the problem?",
                "What exactly did you expect to receive?",
                "What impact has this caused for you?",
                "What solution would feel fair?",
                "Would a replacement solve the problem?",
                "How quickly do you need this resolved?",
                "Please summarize what you want us to do next."
            )
        }
        return prompts[(turn.coerceAtLeast(1) - 1) % prompts.size]
    }

    companion object {
        private const val BASE_URL = "https://api-production-c853.up.railway.app"
        private const val PREFS = "lone_rider_ai_english"
        private const val KEY_LEVEL = "level"
        private const val KEY_TOPIC = "topic"
        private const val KEY_SCENARIO = "scenario"
        private const val KEY_GENTLE_CORRECTIONS = "gentle_corrections"
        private const val KEY_SESSION_COUNT = "session_count"
        private const val KEY_TOTAL_TURNS = "total_turns"
        private const val KEY_LAST_GRAMMAR = "last_grammar"
        private const val KEY_LAST_VOCABULARY = "last_vocabulary"
        private const val KEY_LAST_FLUENCY = "last_fluency"
        private const val KEY_LAST_PRONUNCIATION = "last_pronunciation"
        private const val SESSION_TURNS = 8
    }
}

