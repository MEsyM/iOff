package cz.ioff.app

import org.junit.Assert.*
import org.junit.Test

class IdeaCodecTest {
    @Test fun prependAndParseRoundTrip() {
        val raw = IdeaCodec.prepend("", 123L, "  Build this later  ")
        val ideas = IdeaCodec.parse(raw)
        assertEquals(1, ideas.size)
        assertEquals(123L, ideas[0].timestamp)
        assertEquals("Build this later", ideas[0].text)
    }

    @Test fun newestIdeaIsFirstAndMalformedRowsAreIgnored() {
        val raw = IdeaCodec.prepend(IdeaCodec.prepend("broken\n", 1L, "first"), 2L, "second")
        val ideas = IdeaCodec.parse(raw)
        assertEquals(listOf("second", "first"), ideas.map { it.text })
    }

    @Test fun blankIdeaDoesNotMutateStorage() {
        assertEquals("1|existing", IdeaCodec.prepend("1|existing", 2L, "   "))
    }
}
