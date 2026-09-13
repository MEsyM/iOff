package cz.ioff.app

import org.junit.Assert.*
import org.junit.Test

class AttentionEngineTest {
    @Test fun scoreRewardsDeepWorkAndFocus() {
        val weak = AttentionEngine.score(AttentionSnapshot(10,4.0,8,0,3,90))
        val strong = AttentionEngine.score(AttentionSnapshot(100,9.0,1,3,0,5))
        assertTrue(strong > weak)
        assertTrue(strong in 0..100)
    }

    @Test fun reclaimedTimeNeverNegative() {
        assertEquals(30, AttentionEngine.reclaimedMinutes(30,60))
        assertEquals(0, AttentionEngine.reclaimedMinutes(90,60))
    }

    @Test fun intelligenceNeedsEnoughEvidence() {
        assertNull(AttentionEngine.insight(emptyList()))
    }
}
