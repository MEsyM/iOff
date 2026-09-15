package cz.ioff.app.data

import android.content.SharedPreferences
import cz.ioff.app.domain.focus.ActiveFocus
import cz.ioff.app.domain.focus.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SharedPreferencesFocusRepository(private val p: SharedPreferences) : FocusRepository {
    private val activeFlow = MutableStateFlow(readActive())
    private val sessionsFlow = MutableStateFlow(readSessions())

    override fun observeActiveFocus(): Flow<ActiveFocus?> = activeFlow
    override suspend fun getActiveFocus(): ActiveFocus? = readActive().also { activeFlow.value = it }

    override suspend fun startFocus(goal:String, plannedMinutes:Int, experimentDay:Int, startedAt:Long, endsAt:Long):ActiveFocus {
        readActive()?.let { return it }
        val x=ActiveFocus(UUID.randomUUID().toString(),startedAt,endsAt,experimentDay,plannedMinutes,goal.trim(),0)
        p.edit().putString(KEY_ACTIVE,activeJson(x).toString()).commit()
        activeFlow.value=x
        return x
    }

    override suspend fun incrementUrge() {
        val x=readActive()?:return
        val n=x.copy(urges=x.urges+1)
        p.edit().putString(KEY_ACTIVE,activeJson(n).toString()).commit();activeFlow.value=n
    }

    override suspend fun finishActiveFocus(endedAt:Long, interrupted:Boolean):FocusSession {
        val x=readActive()?:error("No active focus session")
        val actual=(((endedAt-x.startedAt).coerceAtLeast(0L))/60000L).toInt().coerceAtMost(x.plannedMinutes)
        val s=FocusSession(x.sessionId,x.startedAt,endedAt,x.experimentDay,x.plannedMinutes,actual,x.urges,null,x.goal,"",interrupted)
        val all=readSessions()+s
        writeSessions(all);p.edit().remove(KEY_ACTIVE).commit();activeFlow.value=null;sessionsFlow.value=all
        return s
    }

    override suspend fun completeSession(sessionId:String, focusScore:Int, output:String) {
        val all=readSessions().map { if(it.id==sessionId) it.copy(focusScore=focusScore.coerceIn(1,10),output=output.trim()) else it }
        writeSessions(all);sessionsFlow.value=all
    }
    override fun observeSessions():Flow<List<FocusSession>> = sessionsFlow
    override suspend fun getSessionsForDay(experimentDay:Int)=readSessions().filter{it.experimentDay==experimentDay}
    override suspend fun clearCurrentFocus(){p.edit().remove(KEY_ACTIVE).commit();activeFlow.value=null}
    override suspend fun resetSessions(){p.edit().remove(KEY_ACTIVE).remove(KEY_SESSIONS).commit();activeFlow.value=null;sessionsFlow.value=emptyList()}

    private fun readActive():ActiveFocus?=try{p.getString(KEY_ACTIVE,null)?.let{val o=JSONObject(it);ActiveFocus(o.getString("id"),o.getLong("start"),o.getLong("end"),o.getInt("day"),o.getInt("planned"),o.getString("goal"),o.optInt("urges"))}}catch(_:Exception){null}
    private fun activeJson(x:ActiveFocus)=JSONObject().put("id",x.sessionId).put("start",x.startedAt).put("end",x.endsAt).put("day",x.experimentDay).put("planned",x.plannedMinutes).put("goal",x.goal).put("urges",x.urges)
    private fun readSessions():List<FocusSession>=try{val a=JSONArray(p.getString(KEY_SESSIONS,"[]")?:"[]");(0 until a.length()).map{i->val o=a.getJSONObject(i);FocusSession(o.getString("id"),o.getLong("start"),o.optLong("end").takeIf{it>0},o.getInt("day"),o.getInt("planned"),o.getInt("actual"),o.getInt("urges"),o.optInt("score").takeIf{it>0},o.getString("goal"),o.optString("output"),o.optBoolean("interrupted"))}}catch(_:Exception){emptyList()}
    private fun writeSessions(xs:List<FocusSession>){val a=JSONArray();xs.forEach{x->a.put(JSONObject().put("id",x.id).put("start",x.startedAt).put("end",x.endedAt?:0).put("day",x.experimentDay).put("planned",x.plannedMinutes).put("actual",x.actualMinutes).put("urges",x.urges).put("score",x.focusScore?:0).put("goal",x.goal).put("output",x.output).put("interrupted",x.interrupted))};p.edit().putString(KEY_SESSIONS,a.toString()).commit()}
    companion object { private const val KEY_ACTIVE="focus_active_v2";private const val KEY_SESSIONS="focus_sessions_v2" }
}
