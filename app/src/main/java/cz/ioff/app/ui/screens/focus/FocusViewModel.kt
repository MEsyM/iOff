package cz.ioff.app.ui.screens.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.ioff.app.data.FocusRepository
import cz.ioff.app.domain.focus.*
import cz.ioff.app.system.DndController
import cz.ioff.app.util.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val phase:FocusPhase=FocusPhase.SETUP,
    val goal:String="",
    val selectedMinutes:Int=60,
    val sessionId:String?=null,
    val remainingMillis:Long=0,
    val urges:Int=0,
    val dndReady:Boolean=false,
    val completedSession:FocusSession?=null,
    val isLoading:Boolean=true,
    val error:String?=null
)

class FocusViewModel(
    private val repository:FocusRepository,
    private val recovery:FocusRecovery,
    private val dnd:DndController,
    private val clock:Clock,
    private val experimentDay:()->Int
):ViewModel(){
    private val _state=MutableStateFlow(FocusUiState(dndReady=dnd.hasAccess()))
    val uiState:StateFlow<FocusUiState> = _state.asStateFlow()
    private var timer:Job?=null
    init { recoverFocus() }

    fun setGoal(v:String){_state.update{it.copy(goal=v.take(200),error=null)}}
    fun setDuration(v:Int){if(v in setOf(30,60,90,120))_state.update{it.copy(selectedMinutes=v,error=null)}}

    fun startFocus(){
        if(_state.value.phase==FocusPhase.ACTIVE)return
        val goal=_state.value.goal.trim();if(goal.isBlank()){fail("What are you finishing?");return}
        viewModelScope.launch { runCatching {
            repository.getActiveFocus()?.let{resume(it);return@launch}
            val now=clock.now();val mins=_state.value.selectedMinutes
            val active=repository.startFocus(goal,mins,experimentDay(),now,now+mins*60000L)
            dnd.enterFocusMode();resume(active)
        }.onFailure(::error) }
    }

    fun recordUrge(){if(_state.value.phase!=FocusPhase.ACTIVE)return;viewModelScope.launch{runCatching{repository.incrementUrge();repository.getActiveFocus()?.let(::resume)}.onFailure(::error)}}
    fun endFocus(){if(_state.value.phase==FocusPhase.ACTIVE)finish(true)}

    fun saveCompletedSession(score:Int,output:String){val s=_state.value.completedSession?:return;if(score !in 1..10){fail("Choose focus quality from 1 to 10.");return};viewModelScope.launch{runCatching{repository.completeSession(s.id,score,output);_state.value=FocusUiState(selectedMinutes=_state.value.selectedMinutes,dndReady=dnd.hasAccess(),isLoading=false)}.onFailure(::error)}}

    fun recoverFocus(){timer?.cancel();viewModelScope.launch{_state.update{it.copy(isLoading=true)};runCatching{when(val r=recovery.recover(clock.now())){FocusRecoveryResult.Nothing->{dnd.restoreIfOwned();_state.value=FocusUiState(dndReady=dnd.hasAccess(),isLoading=false)};is FocusRecoveryResult.Resume->{dnd.restoreFocusModeIfRequired();resume(r.focus)};is FocusRecoveryResult.CompleteExpired->completeExpired(r.focus)}}.onFailure{runCatching{dnd.restoreIfOwned()};error(it)}}}

    private fun resume(x:ActiveFocus){val rem=(x.endsAt-clock.now()).coerceAtLeast(0);if(rem==0L){completeExpired(x);return};_state.value=FocusUiState(FocusPhase.ACTIVE,x.goal,x.plannedMinutes,x.sessionId,rem,x.urges,dnd.hasAccess(),null,false,null);startTimer(x.endsAt)}
    private fun finish(interrupted:Boolean){timer?.cancel();viewModelScope.launch{runCatching{val s=repository.finishActiveFocus(clock.now(),interrupted);dnd.exitFocusMode();_state.update{it.copy(phase=FocusPhase.COMPLETING,sessionId=null,remainingMillis=0,urges=s.urges,completedSession=s,isLoading=false)}}.onFailure(::error)}}
    private fun completeExpired(x:ActiveFocus){timer?.cancel();viewModelScope.launch{runCatching{val s=repository.finishActiveFocus(x.endsAt,false);dnd.exitFocusMode();_state.value=FocusUiState(FocusPhase.COMPLETING,x.goal,x.plannedMinutes,null,0,s.urges,dnd.hasAccess(),s,false,null)}.onFailure(::error)}}
    private fun startTimer(end:Long){timer?.cancel();timer=viewModelScope.launch{while(true){val rem=(end-clock.now()).coerceAtLeast(0);_state.update{it.copy(remainingMillis=rem)};if(rem==0L){finish(false);break};delay(minOf(1000L,rem))}}}
    private fun fail(m:String){_state.update{it.copy(error=m,isLoading=false)}}
    private fun error(t:Throwable){fail(t.message?:"Something went wrong.")}
    override fun onCleared(){timer?.cancel();super.onCleared()}
}
