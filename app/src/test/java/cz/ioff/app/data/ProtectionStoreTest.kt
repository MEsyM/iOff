package cz.ioff.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cz.ioff.app.domain.focus.ActiveFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtectionStoreTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var store: ProtectionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("protection-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = ProtectionStore(preferences)
    }

    @Test
    fun defaultProtectedAppsCanBeChangedAndPersist() {
        assertTrue(store.isPackageProtected("com.instagram.android"))

        store.setProtected("com.instagram.android", "Instagram", "Social", false)
        store.setProtected("com.example.distractor", "Distractor", "Selected", true)

        val reloaded = ProtectionStore(preferences)
        assertFalse(reloaded.isPackageProtected("com.instagram.android"))
        assertTrue(reloaded.isPackageProtected("com.example.distractor"))
        assertEquals("Distractor", reloaded.displayNameForPackage("com.example.distractor"))
    }

    @Test
    fun focusProtectionSessionSurvivesStoreRecreation() {
        val active = ActiveFocus(
            sessionId = "session-1",
            startedAt = 1_000L,
            endsAt = 301_000L,
            experimentDay = 2,
            plannedMinutes = 5,
            goal = "Finish the proposal",
            urges = 1
        )
        store.syncFocusSession(active)

        val restored = ProtectionStore(preferences).activeSession(2_000L)
        assertNotNull(restored)
        assertEquals("session-1", restored?.id)
        assertEquals(ProtectionType.FOCUS, restored?.type)
        assertEquals("Finish the proposal", restored?.goal)

        store.clearFocusSession("session-1")
        assertEquals(null, ProtectionStore(preferences).activeSession(302_000L))
    }

    @Test
    fun shieldEventsAndOverrideWindowPersist() {
        val now = System.currentTimeMillis()
        store.appendEvent("com.instagram.android", ShieldEventType.BLOCKED, "focus-1", now - 100)
        store.appendEvent("com.instagram.android", ShieldEventType.OVERRIDE, "focus-1", now)
        store.setBypassUntil("com.instagram.android", now + 300_000L)

        val reloaded = ProtectionStore(preferences)
        assertEquals(2, reloaded.events().size)
        assertEquals(1, reloaded.overridesToday(now + 1))
        assertEquals(now + 300_000L, reloaded.bypassUntil("com.instagram.android"))
    }
}
