package cz.ioff.app.system

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DndControllerContractTest {
    @Test fun enterAndExitOwnOnlyTheirChanges() = runBlocking {
        val dnd = FakeDndController(access = true)
        dnd.enterFocusMode()
        assertTrue(dnd.owned)
        assertTrue(dnd.focusMode)
        dnd.exitFocusMode()
        assertFalse(dnd.owned)
        assertFalse(dnd.focusMode)
    }

    @Test fun noAccessDoesNotClaimOwnership() = runBlocking {
        val dnd = FakeDndController(access = false)
        dnd.enterFocusMode()
        assertFalse(dnd.owned)
        assertFalse(dnd.focusMode)
    }

    @Test fun recoveryReappliesOnlyOwnedFocus() = runBlocking {
        val dnd = FakeDndController(access = true)
        dnd.restoreFocusModeIfRequired()
        assertFalse(dnd.focusMode)
        dnd.owned = true
        dnd.restoreFocusModeIfRequired()
        assertTrue(dnd.focusMode)
    }

    @Test fun restoreIfOwnedLeavesUserDndAloneWhenNotOwned() = runBlocking {
        val dnd = FakeDndController(access = true).apply { focusMode = true }
        dnd.restoreIfOwned()
        assertTrue(dnd.focusMode)
    }

    private class FakeDndController(private val access:Boolean):DndController {
        var owned=false
        var focusMode=false
        override fun hasAccess()=access
        override suspend fun enterFocusMode(){if(access && !owned){owned=true;focusMode=true}}
        override suspend fun exitFocusMode()=restoreIfOwned()
        override suspend fun restoreFocusModeIfRequired(){if(access && owned)focusMode=true}
        override suspend fun restoreIfOwned(){if(owned){focusMode=false;owned=false}}
    }
}
