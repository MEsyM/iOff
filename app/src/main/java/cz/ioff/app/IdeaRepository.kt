package cz.ioff.app

data class ParkedIdea(val timestamp: Long, val text: String)

object IdeaCodec {
    fun parse(raw: String): List<ParkedIdea> = raw.lineSequence()
        .mapNotNull { line ->
            val split = line.indexOf('|')
            if (split <= 0 || split == line.lastIndex) return@mapNotNull null
            val timestamp = line.substring(0, split).toLongOrNull() ?: return@mapNotNull null
            val text = line.substring(split + 1).trim()
            if (text.isBlank()) null else ParkedIdea(timestamp, text)
        }.toList()

    fun prepend(raw: String, timestamp: Long, text: String): String {
        val clean = text.trim().replace('\n', ' ')
        if (clean.isBlank()) return raw
        return "$timestamp|$clean\n$raw".trimEnd()
    }
}
