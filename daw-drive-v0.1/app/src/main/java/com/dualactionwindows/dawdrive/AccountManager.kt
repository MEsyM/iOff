package com.dualactionwindows.dawdrive

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/**
 * Lightweight account + cloud progress client for the first Lone Rider account release.
 *
 * Tokens are kept in app-private SharedPreferences for this internal/test stream.
 * Production hardening can move refresh-token storage to Android Keystore-backed storage.
 */
class AccountManager(private val context: Context) {

    data class AccountState(
        val onboardingComplete: Boolean,
        val loggedIn: Boolean,
        val email: String?,
        val profileId: String?,
        val profileName: String?,
        val busy: Boolean = false,
        val message: String? = null,
        val lastSyncAt: Long = 0L
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(busy: Boolean = false, message: String? = null): AccountState =
        AccountState(
            onboardingComplete = prefs.getBoolean(KEY_ONBOARDING, false),
            loggedIn = !prefs.getString(KEY_ACCESS, null).isNullOrBlank(),
            email = prefs.getString(KEY_EMAIL, null),
            profileId = prefs.getString(KEY_PROFILE_ID, null),
            profileName = prefs.getString(KEY_PROFILE_NAME, null),
            busy = busy,
            message = message,
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L)
        )

    fun continueAsGuest(): AccountState {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
        return state(message = "Guest mode")
    }

    fun signUp(email: String, password: String, displayName: String, language: String, callback: (AccountState) -> Unit) {
        auth("/v1/auth/signup", email, password, displayName, language, callback)
    }

    fun login(email: String, password: String, language: String, callback: (AccountState) -> Unit) {
        auth("/v1/auth/login", email, password, null, language, callback)
    }

    fun logout(callback: (AccountState) -> Unit) {
        val refresh = prefs.getString(KEY_REFRESH, null)
        val access = prefs.getString(KEY_ACCESS, null)
        Thread {
            if (!refresh.isNullOrBlank() && !access.isNullOrBlank()) {
                runCatching {
                    request(
                        path = "/v1/auth/logout",
                        method = "POST",
                        token = access,
                        body = JSONObject().put("refresh_token", refresh)
                    )
                }
            }
            prefs.edit()
                .remove(KEY_ACCESS)
                .remove(KEY_REFRESH)
                .remove(KEY_EMAIL)
                .remove(KEY_PROFILE_ID)
                .remove(KEY_PROFILE_NAME)
                .putBoolean(KEY_ONBOARDING, true)
                .apply()
            callback(state(message = "Signed out"))
        }.start()
    }

    fun syncProgress(profile: TriviaGameEngine.Profile, callback: (AccountState) -> Unit) {
        val token = prefs.getString(KEY_ACCESS, null)
        val profileId = prefs.getString(KEY_PROFILE_ID, null)
        if (token.isNullOrBlank() || profileId.isNullOrBlank()) {
            callback(state(message = "Sign in to sync"))
            return
        }

        Thread {
            try {
                val event = JSONObject()
                    .put("event_id", UUID.randomUUID().toString())
                    .put("profile_id", profileId)
                    .put("game", "quick_trivia")
                    .put("event_type", "progress_snapshot")
                    .put("question_id", JSONObject.NULL)
                    .put("xp_delta", profile.xp)
                    .put("correct", JSONObject.NULL)
                    .put("occurred_at", Instant.now().toString())
                    .put(
                        "payload",
                        JSONObject()
                            .put("total_answered", profile.totalAnswered)
                            .put("total_correct", profile.totalCorrect)
                            .put("current_streak", profile.currentStreak)
                            .put("best_streak", profile.bestStreak)
                    )

                request(
                    path = "/v1/sync/events",
                    method = "POST",
                    token = token,
                    body = JSONObject().put("events", JSONArray().put(event))
                )
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                callback(state(message = "Cloud sync complete"))
            } catch (t: Throwable) {
                callback(state(message = "Sync failed: " + cleanError(t)))
            }
        }.start()
    }

    private fun auth(
        path: String,
        email: String,
        password: String,
        displayName: String?,
        language: String,
        callback: (AccountState) -> Unit
    ) {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || password.length < 6) {
            callback(state(message = "Enter a valid email and password (6+ characters)"))
            return
        }

        Thread {
            try {
                val response = request(
                    path = path,
                    method = "POST",
                    body = JSONObject()
                        .put("email", normalizedEmail)
                        .put("password", password)
                )
                val access = response.getString("access_token")
                val refresh = response.getString("refresh_token")
                prefs.edit()
                    .putString(KEY_ACCESS, access)
                    .putString(KEY_REFRESH, refresh)
                    .putString(KEY_EMAIL, normalizedEmail)
                    .putBoolean(KEY_ONBOARDING, true)
                    .apply()

                ensureProfile(access, displayName, language)
                registerDevice(access)
                callback(state(message = if (path.endsWith("signup")) "Account created" else "Signed in"))
            } catch (t: Throwable) {
                callback(state(message = cleanError(t)))
            }
        }.start()
    }

    private fun ensureProfile(token: String, requestedName: String?, language: String) {
        val existing = request("/v1/profiles", "GET", token)
        val list = existing.optJSONArray("_array")
        if (list != null && list.length() > 0) {
            val first = list.getJSONObject(0)
            prefs.edit()
                .putString(KEY_PROFILE_ID, first.getString("id"))
                .putString(KEY_PROFILE_NAME, first.optString("name", "Rider"))
                .apply()
            return
        }

        val fallbackName = prefs.getString(KEY_EMAIL, "Rider")
            ?.substringBefore("@")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?: "Rider"
        val name = requestedName?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName

        val created = request(
            "/v1/profiles",
            "POST",
            token,
            JSONObject()
                .put("name", name)
                .put("language", language)
                .put("age_group", JSONObject.NULL)
                .put("settings", JSONObject())
        )
        prefs.edit()
            .putString(KEY_PROFILE_ID, created.getString("id"))
            .putString(KEY_PROFILE_NAME, created.optString("name", name))
            .apply()
    }

    private fun registerDevice(token: String) {
        val installationId = prefs.getString(KEY_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
            }
        runCatching {
            request(
                "/v1/devices/current",
                "PUT",
                token,
                JSONObject()
                    .put("installation_id", installationId)
                    .put("platform", "android")
                    .put("device_name", Build.MANUFACTURER + " " + Build.MODEL)
                    .put("app_version", runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull() ?: "unknown")
            )
        }
    }

    /**
     * Backend sometimes returns a JSON array (/v1/profiles). Wrap arrays so callers can
     * use one return type without adding a networking dependency.
     */
    private fun request(
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = 10000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", "LoneRider-Android")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("detail") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            if (raw.isBlank()) return JSONObject()
            val trimmed = raw.trim()
            return if (trimmed.startsWith("[")) {
                JSONObject().put("_array", JSONArray(trimmed))
            } else {
                JSONObject(trimmed)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanError(t: Throwable): String =
        (t.message ?: "Network error").replace("java.lang.IllegalStateException: ", "")

    companion object {
        private const val BASE_URL = "https://api-production-c853.up.railway.app"
        private const val PREFS = "lone_rider_account"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EMAIL = "email"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_PROFILE_NAME = "profile_name"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_LAST_SYNC = "last_sync_at"
    }
}
