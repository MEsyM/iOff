package cz.ioff.app

enum class IdeaState { DO, LATER, DONE }
data class ParkedIdea(val timestamp: Long, val text: String, val state: IdeaState = IdeaState.LATER)

object IdeaCodec {
    fun parse(raw: String): List<ParkedIdea> = raw.lineSequence().mapNotNull { line ->
        val parts = line.split('|', limit = 3)
        if (parts.size < 2) return@mapNotNull null
        val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
        val hasState = parts.size == 3 && runCatching { IdeaState.valueOf(parts[1]) }.isSuccess
        val state = if (hasState) IdeaState.valueOf(parts[1]) else IdeaState.LATER
        val text = (if (hasState) parts[2] else parts.drop(1).joinToString("|")).trim()
        if (text.isBlank()) null else ParkedIdea(timestamp, text, state)
    }.toList()

    fun encode(ideas: List<ParkedIdea>): String = ideas.joinToString("\n") {
        "${it.timestamp}|${it.state.name}|${clean(it.text)}"
    }

    fun prepend(raw: String, timestamp: Long, text: String): String {
        val clean = clean(text); if (clean.isBlank()) return raw
        return encode(listOf(ParkedIdea(timestamp, clean)) + parse(raw))
    }

    fun update(raw: String, timestamp: Long, text: String): String {
        val clean = clean(text); if (clean.isBlank()) return raw
        return encode(parse(raw).map { if (it.timestamp == timestamp) it.copy(text = clean) else it })
    }

    fun setState(raw: String, timestamp: Long, state: IdeaState): String =
        encode(parse(raw).map { if (it.timestamp == timestamp) it.copy(state = state) else it })

    fun delete(raw: String, timestamp: Long): String = encode(parse(raw).filterNot { it.timestamp == timestamp })
    private fun clean(text: String) = text.trim().replace('\n', ' ')
}
