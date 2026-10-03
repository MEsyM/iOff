package com.dualactionwindows.dawdrive

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object CloudAccountManager {
    private const val BASE_URL = "https://api-production-c853.up.railway.app"
    private const val PREFS = "lone_rider_cloud"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_ACTIVE_PROFILE = "active_profile"
    private const val KEY_INSTALLATION = "installation_id"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val MIN_SYNC_MS = 30_000L

    data class CloudProfile(
        val id: String,
        val name: String,
        val language: String,
        val ageGroup: String?
    )

    data class AccountState(
        val signedIn: Boolean,
        val email: String?,
        val activeProfileId: String?,
        val lastSync: Long
    )

    private val syncing = AtomicBoolean(false)
    private val listeners = mutableListOf<Pair<android.content.SharedPreferences, android.content.SharedPreferences.OnSharedPreferenceChangeListener>>()
    @Volatile private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) {
            syncProgressAsync(context)
            return
        }
        initialized = true
        val app = context.applicationContext
        listOf("daw_trivia_profile","daw_kids_trivia","daw_guess_who","daw_spelling_bee","lone_rider_english").forEach { name ->
            val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && (
                        key == "xp" || key == "total_answered" || key == "total_correct" ||
                        key == "streak" || key == "current_streak" || key == "best_streak" ||
                        key.endsWith("_xp") || key.endsWith("_answered") || key.endsWith("_correct") ||
                        key.endsWith("_streak") || key.endsWith("_best_streak")
                    )) {
                    syncProgressAsync(app)
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            listeners += prefs to listener
        }
        syncProgressAsync(app)
    }

    fun state(context: Context): AccountState {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return AccountState(
            signedIn=!p.getString(KEY_ACCESS,null).isNullOrBlank(),
            email=p.getString(KEY_EMAIL,null),
            activeProfileId=p.getString(KEY_ACTIVE_PROFILE,null),
            lastSync=p.getLong(KEY_LAST_SYNC,0L)
        )
    }

    fun signUp(context: Context,email: String,password: String): Result<Unit> =
        auth(context,"/v1/auth/signup",email,password)

    fun login(context: Context,email: String,password: String): Result<Unit> =
        auth(context,"/v1/auth/login",email,password)

    private fun auth(context: Context,path: String,email: String,password: String): Result<Unit> = runCatching {
        val response=request(
            context=context,
            method="POST",
            path=path,
            body=JSONObject().put("email",email.trim()).put("password",password),
            authenticated=false
        )
        saveTokens(context,response,email.trim().lowercase())
        ensureProfile(context)
        registerDevice(context)
        syncProgress(context,force=true)
    }

    fun logout(context: Context) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val refresh=prefs.getString(KEY_REFRESH,null)
        if(!refresh.isNullOrBlank()){
            runCatching {
                request(context,"POST","/v1/auth/logout",JSONObject().put("refresh_token",refresh),authenticated=true)
            }
        }
        prefs.edit()
            .remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EMAIL)
            .remove(KEY_ACTIVE_PROFILE).remove(KEY_LAST_SYNC).apply()
    }

    fun profiles(context: Context): Result<List<CloudProfile>> = runCatching {
        val response=request(context,"GET","/v1/profiles",authenticated=true)
        val array=response.optJSONArray("_array") ?: JSONArray()
        buildList {
            for(i in 0 until array.length()){
                val x=array.getJSONObject(i)
                add(CloudProfile(
                    id=x.getString("id"),
                    name=x.optString("name","Player"),
                    language=x.optString("language","en"),
                    ageGroup=x.optString("age_group").takeIf { it.isNotBlank() && it!="null" }
                ))
            }
        }
    }

    fun createProfile(context: Context,name: String,language: String="en"): Result<CloudProfile> = runCatching {
        val x=request(
            context,"POST","/v1/profiles",
            JSONObject().put("name",name.trim()).put("language",language).put("settings",JSONObject()),
            authenticated=true
        )
        val profile=CloudProfile(x.getString("id"),x.optString("name",name.trim()),language,null)
        setActiveProfile(context,profile.id)
        profile
    }

    fun setActiveProfile(context: Context,id: String) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE_PROFILE,id).apply()
        syncProgressAsync(context,force=true)
    }

    fun syncProgressAsync(context: Context, force: Boolean=false) {
        val app=context.applicationContext
        if(!state(app).signedIn) return
        if(!syncing.compareAndSet(false,true)) return
        Thread {
            try { syncProgress(app,force) }
            catch(t:Throwable){ DawDebugLog.log(app,"CLOUD_SYNC_ERROR",t.javaClass.simpleName+": "+(t.message?:"unknown")) }
            finally { syncing.set(false) }
        }.apply { name="LoneRiderCloudSync"; isDaemon=true; start() }
    }

    fun syncProgress(context: Context, force: Boolean=false) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        if(!force && now-prefs.getLong(KEY_LAST_SYNC,0L)<MIN_SYNC_MS) return
        var profileId=prefs.getString(KEY_ACTIVE_PROFILE,null)
        if(profileId.isNullOrBlank()) profileId=ensureProfile(context)
        if(profileId.isNullOrBlank()) return

        // Pull first so a new/reinstalled phone restores cloud totals before uploading local snapshots.
        val remote=request(context,"GET","/v1/sync/progress/"+profileId,authenticated=true)
        applyRemoteProgress(context,remote.optJSONArray("_array") ?: JSONArray())

        val events=JSONArray()
        localSnapshots(context).forEach { snap ->
            events.put(
                JSONObject()
                    .put("event_id",UUID.randomUUID().toString())
                    .put("profile_id",profileId)
                    .put("game",snap.game)
                    .put("event_type","progress_snapshot")
                    .put("question_id",JSONObject.NULL)
                    .put("xp_delta",snap.xp)
                    .put("correct",JSONObject.NULL)
                    .put("occurred_at",Instant.now().toString())
                    .put("payload",JSONObject()
                        .put("total_answered",snap.answered)
                        .put("total_correct",snap.correct)
                        .put("current_streak",snap.streak)
                        .put("best_streak",snap.bestStreak)
                    )
            )
        }
        if(events.length()>0){
            request(context,"POST","/v1/sync/events",JSONObject().put("events",events),authenticated=true)
        }
        prefs.edit().putLong(KEY_LAST_SYNC,now).apply()
        DawDebugLog.log(context,"CLOUD_SYNC_OK","profile="+profileId+" games="+events.length())
    }

    private data class Snapshot(
        val game:String,val xp:Int,val answered:Int,val correct:Int,val streak:Int,val bestStreak:Int
    )

    private fun localSnapshots(context: Context): List<Snapshot> {
        fun snap(pref:String,game:String,xp:String="xp",answered:String="total_answered",correct:String="total_correct",streak:String="streak",best:String="best_streak"): Snapshot {
            val p=context.getSharedPreferences(pref,Context.MODE_PRIVATE)
            return Snapshot(game,p.getInt(xp,0),p.getInt(answered,0),p.getInt(correct,0),p.getInt(streak,0),p.getInt(best,0))
        }
        val english=context.getSharedPreferences("lone_rider_english",Context.MODE_PRIVATE)
        val player=english.getInt("selected_player",0)
        return listOf(
            snap("daw_trivia_profile","trivia",streak="current_streak"),
            snap("daw_kids_trivia","kids_trivia"),
            snap("daw_guess_who","guess_who"),
            snap("daw_spelling_bee","spelling_bee"),
            Snapshot(
                "english",
                english.getInt("p_"+player+"_xp",0),
                english.getInt("p_"+player+"_answered",0),
                english.getInt("p_"+player+"_correct",0),
                english.getInt("p_"+player+"_streak",0),
                english.getInt("p_"+player+"_best_streak",0)
            )
        )
    }

    private fun applyRemoteProgress(context: Context,array: JSONArray) {
        for(i in 0 until array.length()){
            val x=array.getJSONObject(i)
            val game=x.optString("game")
            val xp=x.optInt("xp")
            val answered=x.optInt("total_answered")
            val correct=x.optInt("total_correct")
            val streak=x.optInt("current_streak")
            val best=x.optInt("best_streak")
            when(game){
                "trivia" -> mergePrefs(context,"daw_trivia_profile",xp,answered,correct,streak,best,"current_streak")
                "kids_trivia" -> mergePrefs(context,"daw_kids_trivia",xp,answered,correct,streak,best,"streak")
                "guess_who" -> mergePrefs(context,"daw_guess_who",xp,answered,correct,streak,best,"streak")
                "spelling_bee" -> mergePrefs(context,"daw_spelling_bee",xp,answered,correct,streak,best,"streak")
                "english" -> {
                    val p=context.getSharedPreferences("lone_rider_english",Context.MODE_PRIVATE)
                    val id=p.getInt("selected_player",0)
                    p.edit()
                        .putInt("p_"+id+"_xp",maxOf(p.getInt("p_"+id+"_xp",0),xp))
                        .putInt("p_"+id+"_answered",maxOf(p.getInt("p_"+id+"_answered",0),answered))
                        .putInt("p_"+id+"_correct",maxOf(p.getInt("p_"+id+"_correct",0),correct))
                        .putInt("p_"+id+"_streak",maxOf(p.getInt("p_"+id+"_streak",0),streak))
                        .putInt("p_"+id+"_best_streak",maxOf(p.getInt("p_"+id+"_best_streak",0),best))
                        .apply()
                }
            }
        }
    }

    private fun mergePrefs(context:Context,pref:String,xp:Int,answered:Int,correct:Int,streak:Int,best:Int,streakKey:String){
        val p=context.getSharedPreferences(pref,Context.MODE_PRIVATE)
        p.edit()
            .putInt("xp",maxOf(p.getInt("xp",0),xp))
            .putInt("total_answered",maxOf(p.getInt("total_answered",0),answered))
            .putInt("total_correct",maxOf(p.getInt("total_correct",0),correct))
            .putInt(streakKey,maxOf(p.getInt(streakKey,0),streak))
            .putInt("best_streak",maxOf(p.getInt("best_streak",0),best))
            .apply()
    }

    private fun ensureProfile(context:Context): String? {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        prefs.getString(KEY_ACTIVE_PROFILE,null)?.let { return it }
        val existing=profiles(context).getOrThrow()
        val profile=existing.firstOrNull() ?: createProfile(context,"Driver").getOrThrow()
        prefs.edit().putString(KEY_ACTIVE_PROFILE,profile.id).apply()
        return profile.id
    }

    private fun registerDevice(context:Context) {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var install=prefs.getString(KEY_INSTALLATION,null)
        if(install.isNullOrBlank()){
            install=UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION,install).apply()
        }
        val version=runCatching { context.packageManager.getPackageInfo(context.packageName,0).versionName }.getOrNull()
        request(
            context,"PUT","/v1/devices/current",
            JSONObject()
                .put("installation_id",install)
                .put("platform","android")
                .put("device_name",Build.MANUFACTURER+" "+Build.MODEL)
                .put("app_version",version ?: "unknown"),
            authenticated=true
        )
    }

    private fun saveTokens(context:Context,response:JSONObject,email:String){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY_ACCESS,response.getString("access_token"))
            .putString(KEY_REFRESH,response.getString("refresh_token"))
            .putString(KEY_EMAIL,email)
            .apply()
    }

    private fun refresh(context:Context): Boolean {
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val refresh=prefs.getString(KEY_REFRESH,null) ?: return false
        return runCatching {
            val r=request(context,"POST","/v1/auth/refresh",JSONObject().put("refresh_token",refresh),authenticated=false)
            saveTokens(context,r,prefs.getString(KEY_EMAIL,"").orEmpty())
            true
        }.getOrDefault(false)
    }

    private fun request(
        context:Context,
        method:String,
        path:String,
        body:JSONObject?=null,
        authenticated:Boolean
    ): JSONObject {
        fun execute(token:String?): Pair<Int,String> {
            val conn=(URL(BASE_URL+path).openConnection() as HttpURLConnection).apply {
                requestMethod=method
                connectTimeout=7000
                readTimeout=10000
                useCaches=false
                setRequestProperty("Accept","application/json")
                if(body!=null)setRequestProperty("Content-Type","application/json")
                if(!token.isNullOrBlank())setRequestProperty("Authorization","Bearer "+token)
                doOutput=body!=null
            }
            try {
                if(body!=null) conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val code=conn.responseCode
                val stream=if(code in 200..299) conn.inputStream else conn.errorStream
                val text=stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return code to text
            } finally { conn.disconnect() }
        }

        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var token=if(authenticated)prefs.getString(KEY_ACCESS,null) else null
        var result=execute(token)
        if(authenticated && result.first==401 && refresh(context)){
            token=prefs.getString(KEY_ACCESS,null)
            result=execute(token)
        }
        if(result.first !in 200..299) throw IllegalStateException("HTTP "+result.first+": "+result.second)
        if(result.second.isBlank()) return JSONObject()
        val trimmed=result.second.trim()
        return if(trimmed.startsWith("[")) JSONObject().put("_array",JSONArray(trimmed)) else JSONObject(trimmed)
    }
}
