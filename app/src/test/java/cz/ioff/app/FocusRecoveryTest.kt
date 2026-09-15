package cz.ioff.app

import cz.ioff.app.data.FocusRepository
import cz.ioff.app.domain.focus.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FocusRecoveryTest {
    @Test fun noSession()=runBlocking{assertTrue(DefaultFocusRecovery(FakeRepo(null)).recover(1000) is FocusRecoveryResult.Nothing)}
    @Test fun activeSessionResumes()=runBlocking{val x=active(2000);val r=DefaultFocusRecovery(FakeRepo(x)).recover(1000);assertTrue(r is FocusRecoveryResult.Resume)}
    @Test fun expiredSessionCompletes()=runBlocking{val x=active(1000);val r=DefaultFocusRecovery(FakeRepo(x)).recover(1001);assertTrue(r is FocusRecoveryResult.CompleteExpired)}
    private fun active(end:Long)=ActiveFocus("id",0,end,1,60,"Ship v0.5",2)
}
private class FakeRepo(private var active:ActiveFocus?):FocusRepository{
 override fun observeActiveFocus():Flow<ActiveFocus?> = flowOf(active)
 override suspend fun getActiveFocus()=active
 override suspend fun startFocus(goal:String,plannedMinutes:Int,experimentDay:Int,startedAt:Long,endsAt:Long)=error("unused")
 override suspend fun incrementUrge(){}
 override suspend fun finishActiveFocus(endedAt:Long,interrupted:Boolean)=error("unused")
 override suspend fun completeSession(sessionId:String,focusScore:Int,output:String){}
 override fun observeSessions():Flow<List<FocusSession>> = flowOf(emptyList())
 override suspend fun getSessionsForDay(experimentDay:Int)=emptyList<FocusSession>()
 override suspend fun clearCurrentFocus(){active=null}
 override suspend fun resetSessions(){active=null}
}
