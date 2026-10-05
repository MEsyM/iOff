package com.dualactionwindows.dawdrive

import android.content.Context
import android.text.Html
import android.util.Xml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NewsEngine(private val context: Context) {
    data class Source(
        val id: String,
        val name: String,
        val url: String,
        val language: String = "cs",
        val defaultEnabled: Boolean = true
    )

    data class Topic(
        val id: String,
        val name: String,
        val keywords: List<String>,
        val defaultEnabled: Boolean = false
    )

    data class Article(
        val sourceId: String,
        val sourceName: String,
        val title: String,
        val summary: String,
        val link: String,
        val publishedAt: Long,
        val language: String,
        val topics: Set<String>
    )

    data class DailyBriefing(
        val articles: List<Article>,
        val loadedAt: Long,
        val errors: List<String>
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val sources: List<Source> = listOf(
        Source("ct24", "ČT24", "https://ct24.ceskatelevize.cz/rss.xml"),
        Source("irozhlas", "iROZHLAS", "https://www.irozhlas.cz/rss/irozhlas"),
        Source("novinky", "Novinky.cz", "https://www.novinky.cz/rss2/"),
        Source("seznam_zpravy", "Seznam Zprávy", "https://www.seznamzpravy.cz/rss"),
        Source("npr_news_now", "NPR News Now", "https://feeds.npr.org/500005/podcast.xml", language = "en"),
        Source("irozhlas_domov", "iROZHLAS • Domov", "https://www.irozhlas.cz/rss/irozhlas/section/zpravy-domov", defaultEnabled = false),
        Source("irozhlas_svet", "iROZHLAS • Svět", "https://www.irozhlas.cz/rss/irozhlas/section/zpravy-svet", defaultEnabled = false),
        Source("irozhlas_ekonomika", "iROZHLAS • Ekonomika", "https://www.irozhlas.cz/rss/irozhlas/section/ekonomika", defaultEnabled = false),
        Source("irozhlas_sport", "iROZHLAS • Sport", "https://www.irozhlas.cz/rss/irozhlas/section/sport", defaultEnabled = false),
        Source("irozhlas_tech", "iROZHLAS • Věda a technologie", "https://www.irozhlas.cz/rss/irozhlas/section/veda-technologie", defaultEnabled = false),
        Source("bbc_world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", language = "en", defaultEnabled = false)
    )

    val topics: List<Topic> = listOf(
        Topic("top", "Hlavní zprávy", emptyList(), defaultEnabled = true),
        Topic("czech", "Česko", listOf("česko", "česk", "praha", "brno", "ostrava", "vláda", "sněmovna", "senát"), defaultEnabled = true),
        Topic("world", "Svět", listOf("svět", "usa", "ukraj", "rusko", "evropa", "eu ", "china", "čína", "israel", "gaza", "nato"), defaultEnabled = true),
        Topic("politics", "Politika", listOf("polit", "vláda", "premiér", "prezident", "volb", "parlament", "sněmovna", "senát", "minister"), defaultEnabled = true),
        Topic("economy", "Ekonomika", listOf("ekonom", "inflac", "hospodář", "hdp", "nezaměst", "rozpočet", "daně", "úrok"), defaultEnabled = true),
        Topic("finance", "Finance & trhy", listOf("burza", "akcie", "dluhopis", "bitcoin", "krypto", "invest", "trhy", "wall street", "nasdaq", "s&p"), defaultEnabled = false),
        Topic("business", "Byznys", listOf("firma", "firmy", "společnost", "podnik", "startup", "ceo", "obchod", "výrobce"), defaultEnabled = false),
        Topic("tech", "Technologie & AI", listOf("technolog", "ai ", "umělá inteligence", "openai", "google", "apple", "microsoft", "čip", "software", "robot"), defaultEnabled = true),
        Topic("science", "Věda", listOf("věda", "výzkum", "vědci", "vesmír", "nasa", "objev", "studie"), defaultEnabled = false),
        Topic("culture", "Kultura", listOf("film", "seriál", "hudb", "koncert", "divad", "kultura", "herec", "režisér", "festival"), defaultEnabled = false),
        Topic("sport", "Sport", listOf("sport", "tenis", "hokej", "formule", "f1", "olymp", "liga", "zápas"), defaultEnabled = true),
        Topic("football", "Fotbal", listOf("fotbal", "liga mistrů", "premier league", "champions league", "reprezentace", "sparta", "slavia", "plzeň"), defaultEnabled = true),
        Topic("cars", "Auta & mobilita", listOf("auto", "automobil", "elektromobil", "bmw", "tesla", "škoda", "volkswagen", "doprava", "motor"), defaultEnabled = false),
        Topic("health", "Zdraví", listOf("zdrav", "nemoc", "lékař", "nemocnice", "virus", "vakcín", "léčb", "medic"), defaultEnabled = false)
    )

    fun isEnabled(source: Source): Boolean =
        prefs.getBoolean(KEY_SOURCE_PREFIX + source.id, source.defaultEnabled)

    fun setEnabled(sourceId: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOURCE_PREFIX + sourceId, enabled).apply()
    }

    fun enabledSources(): List<Source> = sources.filter(::isEnabled)

    fun isTopicEnabled(topic: Topic): Boolean =
        prefs.getBoolean(KEY_TOPIC_PREFIX + topic.id, topic.defaultEnabled)

    fun setTopicEnabled(topicId: String, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TOPIC_PREFIX + topicId, enabled).apply()
    }

    fun enabledTopicIds(): Set<String> = topics.filter(::isTopicEnabled).map { it.id }.toSet()

    fun saveArticle(article: Article) {
        if (article.link.isBlank()) return
        val saved = prefs.getStringSet(KEY_SAVED_LINKS, emptySet()).orEmpty().toMutableSet()
        saved += article.link
        prefs.edit().putStringSet(KEY_SAVED_LINKS, saved).apply()
    }

    fun isSaved(article: Article): Boolean =
        article.link.isNotBlank() &&
            prefs.getStringSet(KEY_SAVED_LINKS, emptySet()).orEmpty().contains(article.link)

    fun dailyLimit(): Int = prefs.getInt(KEY_DAILY_LIMIT, 10).coerceIn(5, 20)

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, limit.coerceIn(5, 20)).apply()
    }

    fun loadDailyBriefing(): DailyBriefing {
        val all = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        enabledSources().forEach { source ->
            try {
                all += fetch(source)
            } catch (t: Throwable) {
                errors += source.name + ": " + (t.message ?: t.javaClass.simpleName)
                DawDebugLog.log(context, "NEWS_SOURCE_ERROR", source.name + " " + (t.message ?: ""))
            }
        }

        val enabledTopics = enabledTopicIds()
        val useTopicFilter = enabledTopics.any { it != "top" }
        val includeTop = "top" in enabledTopics
        val seen = HashSet<String>()
        val cutoff = System.currentTimeMillis() - 36L * 60L * 60L * 1000L
        val recent = all.filter { it.publishedAt >= cutoff }
        val pool = (if (recent.isNotEmpty()) recent else all).sortedByDescending { it.publishedAt }

        val topicFiltered = if (useTopicFilter) {
            pool.filter { article -> article.topics.any(enabledTopics::contains) }
        } else {
            pool
        }

        val mixed = buildList {
            if (includeTop) {
                addAll(pool.take(TOP_STORY_SAFETY_COUNT))
            }
            addAll(topicFiltered)
            if (isEmpty()) addAll(pool)
        }

        val selected = mixed
            .filter { article ->
                val key = normalizeTitle(article.title)
                key.isNotBlank() && seen.add(key)
            }
            .take(dailyLimit())

        return DailyBriefing(selected, System.currentTimeMillis(), errors)
    }

    fun loadFullArticle(article: Article): String {
        if (article.link.isBlank()) return article.summary

        val doc = Jsoup.connect(article.link)
            .userAgent("LoneRider/1.14 (+Android)")
            .timeout(12000)
            .followRedirects(true)
            .get()

        val selectors = listOf(
            "article p",
            "main article p",
            "main p",
            "[role=main] p",
            ".article-body p",
            ".article__body p",
            ".article-content p",
            ".story-body p",
            ".storytext p"
        )

        var paragraphs: List<String> = emptyList()
        for (selector in selectors) {
            val candidate = cleanParagraphs(doc.select(selector).toList())
            if (candidate.sumOf { it.length } >= 500) {
                paragraphs = candidate
                break
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs = cleanParagraphs(doc.select("p").toList())
        }

        val text = paragraphs
            .distinct()
            .joinToString("\n\n")
            .trim()
            .take(MAX_ARTICLE_CHARS)

        return text.ifBlank { article.summary }
    }

    private fun cleanParagraphs(elements: List<Element>): List<String> =
        elements.mapNotNull { element ->
            val text = clean(element.text())
            val lower = text.lowercase(Locale.ROOT)
            when {
                text.length < 45 -> null
                lower.contains("přihlaste se") -> null
                lower.contains("předplatné") -> null
                lower.contains("subscribe") -> null
                lower.contains("sign in") -> null
                lower.contains("cookies") -> null
                lower.contains("reklama") -> null
                lower.contains("advertisement") -> null
                else -> text
            }
        }

    private fun fetch(source: Source): List<Article> {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LoneRider/1.14 (+Android)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("HTTP " + status)
            connection.inputStream.buffered().use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, null)
                return parseFeed(parser, source)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFeed(parser: XmlPullParser, source: Source): List<Article> {
        val result = mutableListOf<Article>()
        var event = parser.eventType
        var inItem = false
        var title = ""
        var summary = ""
        var link = ""
        var date = ""

        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT)
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (name == "item" || name == "entry") {
                        inItem = true
                        title = ""
                        summary = ""
                        link = ""
                        date = ""
                    } else if (inItem) {
                        when (name) {
                            "title" -> title = safeNextText(parser)
                            "description", "summary", "content", "content:encoded" ->
                                if (summary.isBlank()) summary = safeNextText(parser)
                            "pubdate", "published", "updated", "dc:date" ->
                                if (date.isBlank()) date = safeNextText(parser)
                            "link" -> {
                                val href = parser.getAttributeValue(null, "href")
                                link = href ?: safeNextText(parser)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if ((name == "item" || name == "entry") && inItem) {
                        val cleanTitle = clean(title)
                        val cleanSummary = clean(summary).ifBlank { cleanTitle }
                        if (cleanTitle.isNotBlank()) {
                            result += Article(
                                sourceId = source.id,
                                sourceName = source.name,
                                title = cleanTitle,
                                summary = cleanSummary,
                                link = link.trim(),
                                publishedAt = parseDate(date),
                                language = source.language,
                                topics = classifyTopics(cleanTitle + " " + cleanSummary)
                            )
                        }
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun classifyTopics(text: String): Set<String> {
        val normalized = text.lowercase(Locale.ROOT)
        return topics
            .filter { it.id != "top" && it.keywords.any(normalized::contains) }
            .map { it.id }
            .toSet()
    }

    private fun safeNextText(parser: XmlPullParser): String =
        try {
            parser.nextText()
        } catch (_: Throwable) {
            ""
        }

    private fun clean(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        return decoded
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeTitle(value: String): String =
        clean(value)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in formats) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw.trim())?.time ?: continue
            } catch (_: Throwable) {
            }
        }
        return System.currentTimeMillis()
    }

    companion object {
        private const val PREFS = "lone_rider_news"
        private const val KEY_SOURCE_PREFIX = "source_"
        private const val KEY_TOPIC_PREFIX = "topic_"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_SAVED_LINKS = "saved_links"
        private const val MAX_ARTICLE_CHARS = 24000
        private const val TOP_STORY_SAFETY_COUNT = 3
    }
}
