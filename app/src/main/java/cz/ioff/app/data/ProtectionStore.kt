package cz.ioff.app.data

import android.content.SharedPreferences
import cz.ioff.app.domain.focus.ActiveFocus
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class ProtectionType { FOCUS, MORNING }
enum class ShieldEventType { BLOCKED, OVERRIDE, RETURNED_TO_FOCUS }

data class ProtectedApp(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean,
    val category: String
)

data class ProtectionSession(
    val id: String,
    val type: ProtectionType,
    val startedAt: Long,
    val plannedEndAt: Long,
    val goal: String,
    val active: Boolean
)

data class ShieldEvent(
    val timestamp: Long,
    val packageName: String,
    val type: ShieldEventType,
    val sessionId: String
)

class ProtectionStore(private val preferences: SharedPreferences) {
    fun protectedPackages(): Set<String> =
        (preferences.getStringSet(KEY_PROTECTED_APPS, null)?.toSet()
            ?: DEFAULT_APPS.mapTo(linkedSetOf()) { it.packageName })

    fun protectedApps(): List<ProtectedApp> {
        val enabled = protectedPackages()
        val labels = readLabels()
        return DEFAULT_APPS.map { app ->
            app.copy(
                displayName = labels.optString(app.packageName, app.displayName),
                enabled = app.packageName in enabled
            )
        }
    }

    fun isPackageProtected(packageName: String): Boolean = packageName in protectedPackages()

    fun setProtected(
        packageName: String,
        displayName: String,
        category: String = "Selected",
        enabled: Boolean
    ) {
        val packages = protectedPackages().toMutableSet()
        if (enabled) packages += packageName else packages -= packageName
        val labels = readLabels().put(packageName, displayName.take(80))
        val categories = readCategories().put(packageName, category.take(40))
        preferences.edit()
            .putStringSet(KEY_PROTECTED_APPS, HashSet(packages))
            .putString(KEY_LABELS, labels.toString())
            .putString(KEY_CATEGORIES, categories.toString())
            .apply()
    }

    fun displayNameForPackage(packageName: String): String {
        val stored = readLabels().optString(packageName)
        if (stored.isNotBlank()) return stored
        return DEFAULT_APPS.firstOrNull { it.packageName == packageName }?.displayName ?: "This app"
    }

    fun syncFocusSession(active: ActiveFocus) {
        writeSession(
            ProtectionSession(
                id = active.sessionId,
                type = ProtectionType.FOCUS,
                startedAt = active.startedAt,
                plannedEndAt = active.endsAt,
                goal = active.goal,
                active = true
            )
        )
    }

    fun clearFocusSession(sessionId: String? = null) {
        val current = readSession() ?: return
        if (current.type == ProtectionType.FOCUS && (sessionId == null || current.id == sessionId)) {
            preferences.edit().remove(KEY_SESSION).apply()
        }
    }

    fun activeSession(now: Long = System.currentTimeMillis()): ProtectionSession? {
        readSession()?.let { persisted ->
            if (persisted.active && persisted.plannedEndAt > now) return persisted
            preferences.edit().remove(KEY_SESSION).apply()
        }

        readLegacyFocus()?.let { focus ->
            if (focus.plannedEndAt > now) {
                writeSession(focus)
                return focus
            }
        }

        if (!preferences.getBoolean(KEY_MORNING_SHIELD, true)) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val start = preferences.getInt(KEY_MORNING_START, 360)
        val end = preferences.getInt(KEY_MORNING_UNTIL, 540)
        if (minute !in start until end) return null
        val dayId = String.format(
            "%04d%02d%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        val startOfWindow = now - (minute - start) * 60_000L - calendar.get(Calendar.SECOND) * 1_000L - calendar.get(Calendar.MILLISECOND)
        return ProtectionSession(
            id = "morning-$dayId",
            type = ProtectionType.MORNING,
            startedAt = startOfWindow,
            plannedEndAt = startOfWindow + (end - start) * 60_000L,
            goal = "Protect the morning",
            active = true
        )
    }

    fun appendEvent(
        packageName: String,
        type: ShieldEventType,
        sessionId: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val events = readEventArray()
        events.put(
            JSONObject()
                .put("time", timestamp)
                .put("package", packageName)
                .put("type", type.name)
                .put("session", sessionId)
        )
        val trimmed = JSONArray()
        val from = (events.length() - MAX_EVENTS).coerceAtLeast(0)
        for (index in from until events.length()) trimmed.put(events.getJSONObject(index))
        preferences.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
    }

    fun events(limit: Int = 100): List<ShieldEvent> {
        val array = readEventArray()
        val result = ArrayList<ShieldEvent>()
        val from = (array.length() - limit.coerceIn(1, MAX_EVENTS)).coerceAtLeast(0)
        for (index in from until array.length()) {
            runCatching {
                val item = array.getJSONObject(index)
                result += ShieldEvent(
                    timestamp = item.getLong("time"),
                    packageName = item.getString("package"),
                    type = ShieldEventType.valueOf(item.getString("type")),
                    sessionId = item.getString("session")
                )
            }
        }
        return result
    }

    fun overridesToday(now: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        return events(MAX_EVENTS).count { it.type == ShieldEventType.OVERRIDE && it.timestamp in start..now }
    }

    fun bypassUntil(packageName: String): Long =
        preferences.getLong("$KEY_BYPASS_PREFIX$packageName", 0L)

    fun setBypassUntil(packageName: String, until: Long) {
        preferences.edit().putLong("$KEY_BYPASS_PREFIX$packageName", until).apply()
    }

    private fun writeSession(session: ProtectionSession) {
        val json = JSONObject()
            .put("id", session.id)
            .put("type", session.type.name)
            .put("start", session.startedAt)
            .put("end", session.plannedEndAt)
            .put("goal", session.goal)
            .put("active", session.active)
        preferences.edit().putString(KEY_SESSION, json.toString()).apply()
    }

    private fun readSession(): ProtectionSession? = runCatching {
        val raw = preferences.getString(KEY_SESSION, null) ?: return null
        val json = JSONObject(raw)
        ProtectionSession(
            id = json.getString("id"),
            type = ProtectionType.valueOf(json.getString("type")),
            startedAt = json.getLong("start"),
            plannedEndAt = json.getLong("end"),
            goal = json.optString("goal"),
            active = json.optBoolean("active", true)
        )
    }.getOrNull()

    private fun readLegacyFocus(): ProtectionSession? = runCatching {
        val raw = preferences.getString(KEY_ACTIVE_FOCUS, null) ?: return null
        val json = JSONObject(raw)
        ProtectionSession(
            id = json.getString("id"),
            type = ProtectionType.FOCUS,
            startedAt = json.getLong("start"),
            plannedEndAt = json.getLong("end"),
            goal = json.optString("goal"),
            active = true
        )
    }.getOrNull()

    private fun readEventArray(): JSONArray = runCatching {
        JSONArray(preferences.getString(KEY_EVENTS, "[]") ?: "[]")
    }.getOrDefault(JSONArray())

    private fun readLabels(): JSONObject = runCatching {
        JSONObject(preferences.getString(KEY_LABELS, "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    private fun readCategories(): JSONObject = runCatching {
        JSONObject(preferences.getString(KEY_CATEGORIES, "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    companion object {
        private const val KEY_PROTECTED_APPS = "protected_apps_v1"
        private const val KEY_LABELS = "protected_app_labels_v1"
        private const val KEY_CATEGORIES = "protected_app_categories_v1"
        private const val KEY_SESSION = "protection_session_v1"
        private const val KEY_EVENTS = "shield_events_v1"
        private const val KEY_BYPASS_PREFIX = "shield_bypass_until_v1_"
        private const val KEY_ACTIVE_FOCUS = "focus_active_v2"
        private const val KEY_MORNING_SHIELD = "morning_shield"
        private const val KEY_MORNING_START = "morning_start"
        private const val KEY_MORNING_UNTIL = "morning_until"
        private const val MAX_EVENTS = 500

        @JvmField
        val DEFAULT_APPS = listOf(
            ProtectedApp("com.instagram.android", "Instagram", true, "Social"),
            ProtectedApp("com.zhiliaoapp.musically", "TikTok", true, "Social"),
            ProtectedApp("com.facebook.katana", "Facebook", true, "Social"),
            ProtectedApp("com.twitter.android", "X", true, "Social"),
            ProtectedApp("com.google.android.youtube", "YouTube", true, "Video"),
            ProtectedApp("com.reddit.frontpage", "Reddit", true, "Social"),
            ProtectedApp("com.snapchat.android", "Snapchat", true, "Social"),
            ProtectedApp("com.android.chrome", "Chrome", true, "Browser")
        )
    }
}
