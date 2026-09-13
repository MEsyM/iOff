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

    fun encode(ideas: List<ParkedIdea>): String = ideas.joinToString("\n") {
        "${it.timestamp}|${clean(it.text)}"
    }

    fun prepend(raw: String, timestamp: Long, text: String): String {
        val clean = clean(text)
        if (clean.isBlank()) return raw
        return encode(listOf(ParkedIdea(timestamp, clean)) + parse(raw))
    }

    fun update(raw: String, timestamp: Long, text: String): String {
        val clean = clean(text)
        if (clean.isBlank()) return raw
        return encode(parse(raw).map { if (it.timestamp == timestamp) it.copy(text = clean) else it })
    }

    fun delete(raw: String, timestamp: Long): String =
        encode(parse(raw).filterNot { it.timestamp == timestamp })

    private fun clean(text: String) = text.trim().replace('\n', ' ')
}
