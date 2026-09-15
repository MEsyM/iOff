package cz.ioff.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cz.ioff.app.IdeaState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IOffRepositoryTest {
    private lateinit var repository: IOffRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("ioff_repository_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        repository = IOffRepository(preferences)
    }

    @Test fun oneThingAndShutdownCarryTomorrowForward() {
        repository.saveOneThing("Ship today", 1)
        repository.saveShutdown(ShutdownEntry("Done", "Tabs", "Ship tomorrow"), 1)
        assertEquals("Ship today", repository.oneThing(1))
        assertEquals("Ship tomorrow", repository.oneThing(2))
        assertTrue(repository.shutdown(1).completed)
    }

    @Test fun ideaLifecyclePersists() {
        repository.parkIdea("A useful idea")
        val idea = repository.ideas().single()
        repository.setIdeaState(idea, IdeaState.DO)
        assertEquals(IdeaState.DO, repository.ideas().single().state)
        repository.deleteIdea(repository.ideas().single())
        assertTrue(repository.ideas().isEmpty())
    }
}
