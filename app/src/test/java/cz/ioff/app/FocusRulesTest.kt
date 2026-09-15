package cz.ioff.app

import org.junit.Assert.*
import org.junit.Test

class FocusRulesTest {
    @Test fun blankGoalCannotStart() { assertFalse(FocusRules.canStart("   ")) }
    @Test fun realGoalCanStart() { assertTrue(FocusRules.canStart("Finish proposal")) }
    @Test fun goalIsTrimmed() { assertEquals("Finish proposal", FocusRules.normalizedGoal("  Finish proposal  ")) }
    @Test fun durationIsSafe() { assertEquals(1, FocusRules.durationMinutes(0)); assertEquals(1440, FocusRules.durationMinutes(9999)) }
}
