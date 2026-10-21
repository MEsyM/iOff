package com.dualactionwindows.dawdrive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live game content client shared by voice games.
 * - reads last published payload from local cache immediately
 * - refreshes from /v1/content/{kind} in the background
 * - falls back to bundled seed JSON when offline or before first publish
 * - exposes diagnostics so it is obvious whether gameplay is using backend data
 */
class LiveGameContent(private val context: Context) {
    data class ContentStatus(
        val source: String,
        val contentVersion: String?,
        val lastSyncAt: Long,
        val syncing: Boolean,
        val lastError: String?,
        val counts: Map<String, Int>
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val syncing = AtomicBoolean(false)

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
                val items = fetchKind(kind)
                if (items.length() > 0) onUpdated?.invoke(items)
            }.onFailure { error ->
                rememberError(error)
            }
        }.apply {
            name = "LoneRiderContent-$kind"
            isDaemon = true
            start()
        }
    }

    fun refreshAll(onStatus: ((ContentStatus) -> Unit)? = null) {
        if (!syncing.compareAndSet(false, true)) {
            onStatus?.invoke(diagnostics())
            return
        }

        onStatus?.invoke(diagnostics())
        Thread {
            var successCount = 0
            var lastError: Throwable? = null

            for (kind in SUPPORTED_KINDS) {
                try {
                    val items = fetchKind(kind)
                    if (items.length() > 0) successCount += 1
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            if (successCount > 0) {
                prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .putString(KEY_SOURCE, SOURCE_BACKEND)
                    .remove(KEY_LAST_ERROR)
                    .apply()
            } else if (lastError != null) {
                rememberError(lastError)
            }

            syncing.set(false)
            onStatus?.invoke(diagnostics())
        }.apply {
            name = "LoneRiderContentRefreshAll"
            isDaemon = true
            start()
        }
    }

    fun diagnostics(): ContentStatus {
        val counts = linkedMapOf<String, Int>()
        var newestVersion: String? = null
        for (kind in SUPPORTED_KINDS) {
            val raw = prefs.getString(cacheKey(kind), null)
            val count = runCatching {
                if (raw.isNullOrBlank()) 0 else JSONArray(raw).length()
            }.getOrDefault(0)
            counts[kind] = count

            val version = prefs.getString(versionKey(kind), null)
            if (!version.isNullOrBlank()) newestVersion = version
        }

        val hasBackendCache = counts.values.any { it > 0 } && !newestVersion.isNullOrBlank()
        return ContentStatus(
            source = if (hasBackendCache) SOURCE_BACKEND else SOURCE_LOCAL,
            contentVersion = newestVersion,
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L),
            syncing = syncing.get(),
            lastError = prefs.getString(KEY_LAST_ERROR, null),
            counts = counts
        )
    }

    fun contentVersion(kind: String): String? =
        prefs.getString(versionKey(kind), null)

    private fun fetchKind(kind: String): JSONArray {
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
            if (code !in 200..299) throw IllegalStateException("HTTP $code for $kind")

            val raw = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val rawItems = root.optJSONArray("items") ?: JSONArray()
            val items = JSONArray()

            for (i in 0 until rawItems.length()) {
                val wrapper = rawItems.optJSONObject(i) ?: continue
                val data = wrapper.optJSONObject("data") ?: wrapper
                if (!data.has("id")) data.put("id", wrapper.optString("id"))
                if (!data.has("category")) data.put("category", wrapper.optString("category"))
                if (!data.has("difficulty")) data.put("difficulty", wrapper.optInt("difficulty", 1))
                items.put(data)
            }

            if (items.length() > 0) {
                prefs.edit()
                    .putString(cacheKey(kind), items.toString())
                    .putString(versionKey(kind), root.optString("contentVersion"))
                    .putString(KEY_SOURCE, SOURCE_BACKEND)
                    .apply()
            }
            return items
        } finally {
            c.disconnect()
        }
    }

    private fun rememberError(error: Throwable) {
        prefs.edit()
            .putString(KEY_LAST_ERROR, error.message ?: error.javaClass.simpleName)
            .apply()
    }

    private fun cacheKey(kind: String) = "content_" + kind
    private fun versionKey(kind: String) = "version_" + kind

    companion object {
        private const val BASE_URL = "https://api-production-c853.up.railway.app"
        private const val PREFS = "lone_rider_live_content"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_ERROR = "last_sync_error"
        private const val KEY_SOURCE = "content_source"
        private const val SOURCE_BACKEND = "BACKEND"
        private const val SOURCE_LOCAL = "LOCAL FALLBACK"

        val SUPPORTED_KINDS = listOf(
            "trivia",
            "kids_trivia",
            "kids_game",
            "guess_who",
            "spelling_bee",
            "english_lesson",
            "game_config",
            "name_that_sound",
            "story_adventure",
            "ai_english",
            "word_chain"
        )
    }
}
