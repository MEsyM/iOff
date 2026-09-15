package cz.ioff.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesFocusRepositoryTest {
    private lateinit var repository: SharedPreferencesFocusRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("focus_repo_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = SharedPreferencesFocusRepository(prefs)
    }

    @Test fun startPersistsAndPreventsDuplicateActiveSession() = runBlocking {
        val first = repository.startFocus(" Ship v0.5 ", 60, 2, 1_000L, 3_601_000L)
        val second = repository.startFocus("other", 30, 2, 2_000L, 3_000L)
        assertEquals(first.sessionId, second.sessionId)
        assertEquals("Ship v0.5", repository.getActiveFocus()?.goal)
    }

    @Test fun urgeFinishAndCompletePersistResult() = runBlocking {
        repository.startFocus("Build", 60, 3, 0L, 3_600_000L)
        repository.incrementUrge()
        repository.incrementUrge()
        val finished = repository.finishActiveFocus(1_800_000L, true)
        assertEquals(30, finished.actualMinutes)
        assertEquals(2, finished.urges)
        assertTrue(finished.interrupted)
        assertNull(repository.getActiveFocus())
        repository.completeSession(finished.id, 8, " shipped ")
        val stored = repository.getSessionsForDay(3).single()
        assertEquals(8, stored.focusScore)
        assertEquals("shipped", stored.output)
        assertEquals(30, preferences().getInt("exp_3_mins", 0))
        repository.completeSession(finished.id, 9, "updated")
        assertEquals(30, preferences().getInt("exp_3_mins", 0))
    }

    @Test fun resetRemovesActiveAndHistory() = runBlocking {
        repository.startFocus("Build", 30, 1, 0L, 1_800_000L)
        repository.finishActiveFocus(1_800_000L, false)
        repository.resetSessions()
        assertNull(repository.getActiveFocus())
        assertTrue(repository.getSessionsForDay(1).isEmpty())
    }

    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("focus_repo_test", Context.MODE_PRIVATE)
}
