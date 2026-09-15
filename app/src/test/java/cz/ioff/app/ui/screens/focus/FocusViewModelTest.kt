package cz.ioff.app.ui.screens.focus

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import cz.ioff.app.data.FocusRepository
import cz.ioff.app.domain.focus.*
import cz.ioff.app.system.DndController
import cz.ioff.app.util.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()

    @Before fun before(){ Dispatchers.setMain(dispatcher) }
    @After fun after(){ Dispatchers.resetMain() }

    @Test fun blankGoalDoesNotStart() = runTest(dispatcher) {
        val repo=FakeRepo(); val vm=vm(repo)
        dispatcher.scheduler.runCurrent(); vm.setGoal("   "); vm.startFocus(); dispatcher.scheduler.runCurrent()
        assertEquals(FocusPhase.SETUP, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.error)
        assertNull(repo.active)
    }

    @Test fun startPersistsAndEntersActiveState() = runTest(dispatcher) {
        val repo=FakeRepo(); val dnd=FakeDnd(); val vm=vm(repo,dnd)
        dispatcher.scheduler.runCurrent(); vm.setGoal("Ship"); vm.setDuration(30); vm.startFocus(); dispatcher.scheduler.runCurrent()
        assertEquals(FocusPhase.ACTIVE,vm.uiState.value.phase)
        assertEquals("Ship",repo.active?.goal)
        assertEquals(1,dnd.entered)
    }

    @Test fun recoveryResumesExistingSession() = runTest(dispatcher) {
        val repo=FakeRepo().apply{active=ActiveFocus("x",1000,61_000,1,1,"Recover",2)}
        val dnd=FakeDnd(); val vm=vm(repo,dnd,FakeClock(10_000))
        dispatcher.scheduler.runCurrent()
        assertEquals(FocusPhase.ACTIVE,vm.uiState.value.phase)
        assertEquals(51_000,vm.uiState.value.remainingMillis)
        assertEquals(1,dnd.restoredFocus)
    }

    @Test fun expiredRecoveryCompletesAtPlannedEnd() = runTest(dispatcher) {
        val repo=FakeRepo().apply{active=ActiveFocus("x",0,60_000,1,1,"Done",0)}
        val dnd=FakeDnd(); val vm=vm(repo,dnd,FakeClock(120_000))
        dispatcher.scheduler.runCurrent()
        assertEquals(FocusPhase.COMPLETING,vm.uiState.value.phase)
        assertEquals(60_000,vm.uiState.value.completedSession?.endedAt)
        assertEquals(1,dnd.exited)
    }

    @Test fun explicitEndIsInterruptedAndResultCanBeSaved() = runTest(dispatcher) {
        val repo=FakeRepo(); val vm=vm(repo,clock=FakeClock(0))
        dispatcher.scheduler.runCurrent(); vm.setGoal("Output");vm.startFocus();dispatcher.scheduler.runCurrent()
        vm.endFocus();dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.completedSession?.interrupted == true)
        vm.saveCompletedSession(9," shipped ");dispatcher.scheduler.runCurrent()
        assertEquals(9,repo.sessions.single().focusScore);assertEquals("shipped",repo.sessions.single().output)
        assertEquals(FocusPhase.SETUP,vm.uiState.value.phase)
    }

    private fun vm(repo:FakeRepo,dnd:FakeDnd=FakeDnd(),clock:FakeClock=FakeClock(1_000)) = FocusViewModel(repo,DefaultFocusRecovery(repo),dnd,clock,{1})

    private class FakeClock(var value:Long):Clock{override fun now()=value}
    private class FakeDnd:DndController{
        var entered=0;var exited=0;var restoredFocus=0;var restored=0
        override fun hasAccess()=true
        override suspend fun enterFocusMode(){entered++}
        override suspend fun exitFocusMode(){exited++}
        override suspend fun restoreFocusModeIfRequired(){restoredFocus++}
        override suspend fun restoreIfOwned(){restored++}
    }
    private class FakeRepo:FocusRepository{
        var active:ActiveFocus?=null;val sessions=mutableListOf<FocusSession>();private val af=MutableStateFlow<ActiveFocus?>(null);private val sf=MutableStateFlow<List<FocusSession>>(emptyList())
        override fun observeActiveFocus():Flow<ActiveFocus?> = af
        override suspend fun getActiveFocus()=active
        override suspend fun startFocus(goal:String,plannedMinutes:Int,experimentDay:Int,startedAt:Long,endsAt:Long):ActiveFocus { active?.let{return it};return ActiveFocus("id",startedAt,endsAt,experimentDay,plannedMinutes,goal,0).also{active=it;af.value=it} }
        override suspend fun incrementUrge(){active=active?.copy(urges=(active?.urges?:0)+1);af.value=active}
        override suspend fun finishActiveFocus(endedAt:Long,interrupted:Boolean):FocusSession{val a=active?:error("none");val s=FocusSession(a.sessionId,a.startedAt,endedAt,a.experimentDay,a.plannedMinutes,((endedAt-a.startedAt).coerceAtLeast(0)/60000).toInt().coerceAtMost(a.plannedMinutes),a.urges,null,a.goal,"",interrupted);sessions+=s;active=null;af.value=null;sf.value=sessions.toList();return s}
        override suspend fun completeSession(sessionId:String,focusScore:Int,output:String){val i=sessions.indexOfFirst{it.id==sessionId};sessions[i]=sessions[i].copy(focusScore=focusScore,output=output.trim());sf.value=sessions.toList()}
        override fun observeSessions():Flow<List<FocusSession>> = sf
        override suspend fun getSessionsForDay(experimentDay:Int)=sessions.filter{it.experimentDay==experimentDay}
        override suspend fun clearCurrentFocus(){active=null;af.value=null}
        override suspend fun resetSessions(){active=null;sessions.clear();af.value=null;sf.value=emptyList()}
    }
}
