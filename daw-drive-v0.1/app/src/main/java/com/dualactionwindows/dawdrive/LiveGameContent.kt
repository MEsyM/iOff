package com.dualactionwindows.dawdrive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live game content client shared by voice games.
 * - reads last published payload from local cache immediately
 * - refreshes from /v1/content/{kind} in the background
 * - falls back to bundled seed JSON when offline or before first publish
 */
class LiveGameContent(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun items(kind: String, fallback: JSONArray): JSONArray {
        val cached = prefs.getString(cacheKey(kind), null)
        val initial = runCatching {
            if (cached.isNullOrBlank()) fallback else JSONArray(cached)
        }.getOrElse { fallback }
        refresh(kind)
        return initial
    }

    fun refresh(kind: String, onUpdated: ((JSONArray) -> Unit)? = null) {
        Thread {
            runCatching {
                val c = (URL(BASE_URL + "/v1/content/" + kind).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 8000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "LoneRider-Android")
                }
                try {
                    val code = c.responseCode
                    if (code !in 200..299) return@runCatching
                    val raw = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(raw)
                    val items = root.optJSONArray("items") ?: JSONArray()
                    if (items.length() > 0) {
                        prefs.edit()
                            .putString(cacheKey(kind), items.toString())
                            .putString(versionKey(kind), root.optString("contentVersion"))
                            .apply()
                        onUpdated?.invoke(items)
                    }
                } finally {
                    c.disconnect()
                }
            }
        }.start()
    }

    fun contentVersion(kind: String): String? =
        prefs.getString(versionKey(kind), null)

    private fun cacheKey(kind: String) = "content_" + kind
    private fun versionKey(kind: String) = "version_" + kind

    companion object {
        private const val BASE_URL = "https://api-production-c853.up.railway.app"
        private const val PREFS = "lone_rider_live_content"
    }
}
