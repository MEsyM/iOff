package com.dualactionwindows.dawdrive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

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

class AiEnglishConversationEngine(context: Context) {
    data class Turn(val id:String,val rolePrompt:String,val expected:List<String>,val feedback:String,val next:String?)
    private val content=LiveGameContent(context)
    private var data=content.items("ai_english",fallback())
    private var currentId="start"
    private var answered=0
    private var correct=0
    init { content.refresh("ai_english"){data=it} }

    fun current():Turn {
        val o=(0 until data.length()).mapNotNull{data.optJSONObject(it)}.firstOrNull{it.optString("id")==currentId}
            ?: data.optJSONObject(0) ?: fallback().getJSONObject(0)
        return Turn(o.optString("id"),o.optString("role_prompt"),aliases(o,"expected"),o.optString("feedback"),o.optString("next").takeIf{it.isNotBlank()})
    }
    fun answer(raw:String):Pair<Boolean,String> {
        val t=current(); val n=normalizeVoice(raw)
        val ok=t.expected.isEmpty() || t.expected.any{a->n.contains(normalizeVoice(a))}
        answered++; if(ok) correct++
        currentId=t.next ?: "start"
        return ok to if(t.feedback.isBlank()) {
            if(ok) "Good answer." else "Try a short natural English sentence."
        } else t.feedback
    }
    fun reset(){currentId="start";answered=0;correct=0}
    fun score()="English conversation: $correct / $answered"
    private fun fallback()=JSONArray(
        """[
          {"id":"start","role_prompt":"You are checking into a hotel. I am the receptionist. Hello, welcome. What is your name?","expected":["my name is","i am","i'm"],"feedback":"Great. A natural answer is: My name is ...","next":"booking"},
          {"id":"booking","role_prompt":"Nice to meet you. Do you have a reservation?","expected":["yes","reservation","booking"],"feedback":"Good. You can say: Yes, I have a reservation.","next":"bags"},
          {"id":"bags","role_prompt":"Would you like help with your bags?","expected":["yes","no","please","thank"],"feedback":"Nice. Keep the answer short and natural.","next":"start"}
        ]"""
    )
}
