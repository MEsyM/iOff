package cz.ioff.app

import cz.ioff.app.domain.focus.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMathTest {
    @Test fun sessionCarriesExperimentDay() {
        val session = FocusSession("1", 1, 2, 4, 60, 42, 3, 8, "goal", "done", true)
        assertEquals(4, session.experimentDay)
        assertEquals(42, session.actualMinutes)
        assertTrue(session.interrupted)
    }

    @Test fun aggregationCanSeparateDays() {
        val sessions = listOf(
            FocusSession("1", 0, 0, 1, 60, 60, 1, 8, "", "", false),
            FocusSession("2", 0, 0, 2, 60, 30, 2, 7, "", "", true)
        )
        assertEquals(60, sessions.filter { it.experimentDay == 1 }.sumOf { it.actualMinutes })
        assertEquals(30, sessions.filter { it.experimentDay == 2 }.sumOf { it.actualMinutes })
    }
}
