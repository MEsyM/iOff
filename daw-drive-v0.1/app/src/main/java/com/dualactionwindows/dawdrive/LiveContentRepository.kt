package com.dualactionwindows.dawdrive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline-first live content cache.
 *
 * Source priority:
 *  1. Published Lone Rider backend content
 *  2. Last successful local cache
 *  3. Existing bundled hardcoded banks
 *
 * Game engines keep their current behavior; only the question source changes.
 */
object LiveContentRepository {

    private const val PREFS = "lone_rider_live_content"
    private const val KEY_VERSION = "content_version"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_PREFIX = "kind_"
    private const val BASE_URL = "https://api-production-c853.up.railway.app"
    private const val MIN_SYNC_INTERVAL_MS = 15L * 60L * 1000L

    private val kinds = listOf(
        "trivia",
        "kids_trivia",
        "kids_game",
        "guess_who",
        "spelling_bee",
        "english_lesson",
        "game_config"
    )

    @Volatile
    private var appContext: Context? = null

    private val initialized = AtomicBoolean(false)
    private val syncing = AtomicBoolean(false)

    @Volatile
    private var triviaCache: List<TriviaQuestionBank.LocalizedQuestion> = emptyList()

    @Volatile
    private var kidsTriviaCache: List<KidsTriviaQuestionBank.Question> = emptyList()

    @Volatile
    private var kidsGameCache: List<KidsTriviaEngine.Question> = emptyList()

    @Volatile
    private var guessWhoCache: List<GuessWhoEngine.Person> = emptyList()

    @Volatile
    private var spellingCache: List<SpellingBeeEngine.Word> = emptyList()

    @Volatile
    private var englishCache: List<EnglishLearningEngine.LessonItem> = emptyList()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            loadCachedContent()
        }
        syncAsync()
    }

    fun syncAsync(force: Boolean = false) {
        val context = appContext ?: return
        if (!syncing.compareAndSet(false, true)) return

        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
                if (!force && now - lastSync < MIN_SYNC_INTERVAL_MS) {
                    return@Thread
                }

                val manifest = fetchJson("/v1/content/manifest")
                val remoteVersion = manifest.optString("contentVersion").takeIf {
                    it.isNotBlank() && it != "null"
                } ?: run {
                    DawDebugLog.log(context, "CONTENT_SYNC_SKIP", "no published content")
                    prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                    return@Thread
                }

                val localVersion = prefs.getString(KEY_VERSION, null)
                if (!force && remoteVersion == localVersion) {
                    prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                    DawDebugLog.log(context, "CONTENT_SYNC_CURRENT", "version=" + remoteVersion)
                    return@Thread
                }

                val editor = prefs.edit()
                kinds.forEach { kind ->
                    val payload = fetchJson("/v1/content/" + kind)
                    editor.putString(KEY_PREFIX + kind, payload.toString())
                }
                editor
                    .putString(KEY_VERSION, remoteVersion)
                    .putLong(KEY_LAST_SYNC, now)
                    .apply()

                loadCachedContent()
                DawDebugLog.log(context, "CONTENT_SYNC_OK", "version=" + remoteVersion)
            } catch (t: Throwable) {
                DawDebugLog.log(
                    context,
                    "CONTENT_SYNC_ERROR",
                    t.javaClass.simpleName + ": " + (t.message ?: "unknown")
                )
            } finally {
                syncing.set(false)
            }
        }.apply {
            name = "LoneRiderContentSync"
            isDaemon = true
            start()
        }
    }

    fun currentVersion(): String? {
        val context = appContext ?: return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VERSION, null)
    }

    fun triviaQuestions(): List<TriviaQuestionBank.LocalizedQuestion> = triviaCache

    fun kidsTriviaQuestions(): List<KidsTriviaQuestionBank.Question> = kidsTriviaCache

    fun kidsGameQuestions(): List<KidsTriviaEngine.Question> = kidsGameCache

    fun guessWhoPeople(): List<GuessWhoEngine.Person> = guessWhoCache

    fun spellingWords(): List<SpellingBeeEngine.Word> = spellingCache

    fun englishLessons(): List<EnglishLearningEngine.LessonItem> = englishCache

    private fun loadCachedContent() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        triviaCache = parseTrivia(prefs.getString(KEY_PREFIX + "trivia", null))
        kidsTriviaCache = parseKidsTrivia(prefs.getString(KEY_PREFIX + "kids_trivia", null))
        kidsGameCache = parseKidsGame(prefs.getString(KEY_PREFIX + "kids_game", null))
        guessWhoCache = parseGuessWho(prefs.getString(KEY_PREFIX + "guess_who", null))
        spellingCache = parseSpelling(prefs.getString(KEY_PREFIX + "spelling_bee", null))
        englishCache = parseEnglish(prefs.getString(KEY_PREFIX + "english_lesson", null))

        DawDebugLog.log(
            context,
            "CONTENT_CACHE_LOADED",
            "v=" + (currentVersion() ?: "bundled") +
                " trivia=" + triviaCache.size +
                " kidsCz=" + kidsTriviaCache.size +
                " kidsGame=" + kidsGameCache.size +
                " guess=" + guessWhoCache.size +
                " spelling=" + spellingCache.size +
                " english=" + englishCache.size
        )
    }

    private fun fetchJson(path: String): JSONObject {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 8000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LoneRider-Android")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP " + code + " for " + path)
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTrivia(raw: String?): List<TriviaQuestionBank.LocalizedQuestion> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<TriviaQuestionBank.LocalizedQuestion>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper
                val localized = data.getJSONObject("localized")
                val en = localized.getJSONObject("en")
                val cs = localized.getJSONObject("cs")
                val categoryKey = wrapper.optString("category", data.optString("category"))
                val category = TriviaQuestionBank.Category.entries.firstOrNull {
                    it.key.equals(categoryKey, ignoreCase = true)
                } ?: return@runCatching

                result += TriviaQuestionBank.LocalizedQuestion(
                    id = wrapper.optString("id", data.getString("id")),
                    difficulty = wrapper.optInt("difficulty", data.optInt("difficulty", 1)),
                    category = category,
                    promptEn = en.getString("prompt"),
                    promptCs = cs.getString("prompt"),
                    answersEn = en.getJSONArray("answers").toStringList(),
                    answersCs = cs.getJSONArray("answers").toStringList(),
                    explanationEn = en.optString("explanation"),
                    explanationCs = cs.optString("explanation")
                )
            }
        }
        return result
    }

    private fun parseKidsTrivia(raw: String?): List<KidsTriviaQuestionBank.Question> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<KidsTriviaQuestionBank.Question>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper
                val categoryKey = wrapper.optString("category", data.optString("category"))
                val category = KidsTriviaQuestionBank.Category.entries.firstOrNull {
                    it.key.equals(categoryKey, ignoreCase = true)
                } ?: return@runCatching

                result += KidsTriviaQuestionBank.Question(
                    id = wrapper.optString("id", data.getString("id")),
                    category = category,
                    minAge = data.optInt("minAge", wrapper.optInt("difficulty", 6)),
                    maxAge = data.optInt("maxAge", 12),
                    prompt = data.getString("prompt"),
                    answers = data.getJSONArray("answers").toStringList(),
                    explanation = data.optString("explanation")
                )
            }
        }
        return result
    }

    private fun parseKidsGame(raw: String?): List<KidsTriviaEngine.Question> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<KidsTriviaEngine.Question>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper
                val localized = data.getJSONObject("localized")
                val en = localized.getJSONObject("en")
                val cs = localized.getJSONObject("cs")
                val categoryKey = wrapper.optString("category", data.optString("category"))
                val category = KidsTriviaEngine.Category.entries.firstOrNull {
                    it.key.equals(categoryKey, ignoreCase = true)
                } ?: return@runCatching

                result += KidsTriviaEngine.Question(
                    id = wrapper.optString("id", data.getString("id")),
                    difficulty = wrapper.optInt("difficulty", data.optInt("difficulty", 1)),
                    category = category,
                    promptEn = en.getString("prompt"),
                    promptCs = cs.getString("prompt"),
                    answersEn = en.getJSONArray("answers").toStringList(),
                    answersCs = cs.getJSONArray("answers").toStringList(),
                    explanationEn = en.optString("explanation"),
                    explanationCs = cs.optString("explanation")
                )
            }
        }
        return result
    }

    private fun parseGuessWho(raw: String?): List<GuessWhoEngine.Person> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<GuessWhoEngine.Person>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper
                val regionKey = data.optString("region", wrapper.optString("category"))
                val region = if (regionKey.equals("czech", true) || regionKey.equals("cz", true)) {
                    GuessWhoEngine.Region.CZECH
                } else {
                    GuessWhoEngine.Region.WORLD
                }
                val clues = data.getJSONObject("clues")

                result += GuessWhoEngine.Person(
                    id = wrapper.optString("id", data.getString("id")),
                    name = data.getString("name"),
                    aliases = data.optJSONArray("aliases")?.toStringList().orEmpty(),
                    region = region,
                    difficulty = wrapper.optInt("difficulty", data.optInt("difficulty", 1)),
                    cluesEn = clues.getJSONArray("en").toStringList(),
                    cluesCs = clues.getJSONArray("cs").toStringList()
                )
            }
        }
        return result
    }

    private fun parseSpelling(raw: String?): List<SpellingBeeEngine.Word> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<SpellingBeeEngine.Word>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper
                val hints = data.optJSONObject("hints")

                result += SpellingBeeEngine.Word(
                    id = wrapper.optString("id", data.getString("id")),
                    difficulty = wrapper.optInt("difficulty", data.optInt("difficulty", 1)),
                    word = data.getString("word"),
                    definitionEn = hints?.optString("en").orEmpty(),
                    definitionCs = hints?.optString("cs").orEmpty()
                )
            }
        }
        return result
    }

    private fun parseEnglish(raw: String?): List<EnglishLearningEngine.LessonItem> {
        val items = items(raw) ?: return emptyList()
        val result = ArrayList<EnglishLearningEngine.LessonItem>(items.length())
        for (i in 0 until items.length()) {
            runCatching {
                val wrapper = items.getJSONObject(i)
                val data = wrapper.optJSONObject("data") ?: wrapper

                val audience = when (data.optString("audience").lowercase()) {
                    "kid" -> EnglishLearningEngine.Audience.KID
                    else -> EnglishLearningEngine.Audience.ADULT
                }
                val type = when (data.optString("type").lowercase()) {
                    "vocabulary" -> EnglishLearningEngine.LessonType.VOCABULARY
                    "translation" -> EnglishLearningEngine.LessonType.TRANSLATION
                    "repeat" -> EnglishLearningEngine.LessonType.REPEAT
                    else -> EnglishLearningEngine.LessonType.PHRASE
                }

                result += EnglishLearningEngine.LessonItem(
                    id = wrapper.optString("id", data.getString("id")),
                    audience = audience,
                    levelRank = data.optInt("levelRank", wrapper.optInt("difficulty", 1)),
                    type = type,
                    topic = data.optString("topic", wrapper.optString("category")),
                    promptCs = data.getString("promptCs"),
                    promptEn = data.getString("promptEn"),
                    answers = data.getJSONArray("answers").toStringList(),
                    teaching = data.optString("teaching")
                )
            }
        }
        return result
    }

    private fun items(raw: String?): JSONArray? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JSONObject(raw).getJSONArray("items") }.getOrNull()
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotBlank()) add(value)
            }
        }
}
