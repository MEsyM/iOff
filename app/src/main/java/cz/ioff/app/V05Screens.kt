package cz.ioff.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun V05Today(s: Store, onFocus:()->Unit, onIdeas:()->Unit) {
    val d=s.day(); var one by remember(d){ mutableStateOf(s.p.getString("one_thing_$d","")?:"") }
    val lifeStore=remember{ DailyLifeStore(s.pContext()) }; var life by remember(d){ mutableStateOf(lifeStore.load(d)) }
    val sessions=s.sessions().filter{it.experimentDay==d}; val n=s.metric("sessions",d)
    val snap=AttentionSnapshot(s.metric("mins",d),if(n==0)0.0 else s.metric("focusSum",d).toDouble()/n,s.metric("urges",d),s.metric("shield",d),s.metric("bypasses",d),s.metric("distracting",d))
    Page("Today") {
        Text("Today's One Thing",fontSize=14.sp,color=Muted); OutlinedTextField(one,{one=it;s.p.edit().putString("one_thing_$d",it).apply()},Modifier.fillMaxWidth(),placeholder={Text("The one result that makes today count")})
        Primary("Start Focus",enabled=one.isNotBlank()){s.p.edit().putString("focus_prefill",one.trim()).apply();onFocus()}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("ATTENTION","${AttentionEngine.score(snap)}",Modifier.weight(1f));Metric("DEEP WORK","${s.metric("mins",d)}m",Modifier.weight(1f))}
        Text("DAILY COMMITMENTS",color=Muted,fontSize=11.sp); DailyCheck("No drugs",life.noDrugs){life=life.copy(noDrugs=it);lifeStore.save(d,life)};DailyCheck("No weed",life.noWeed){life=life.copy(noWeed=it);lifeStore.save(d,life)};DailyCheck("No alcohol",life.noAlcohol){life=life.copy(noAlcohol=it);lifeStore.save(d,life)};DailyCheck("Sex",life.sex){life=life.copy(sex=it);lifeStore.save(d,life)}
        SportBox(d,life,lifeStore){life=lifeStore.load(d)}
        TextButton(onClick=onIdeas){Text("Idea Parking (${s.ideas().size})")}
        if(sessions.isNotEmpty()){Text("FOCUS",color=Muted,fontSize=11.sp);sessions.takeLast(3).reversed().forEach{SessionRow(it)}}
    }
}
@Composable private fun DailyCheck(label:String,value:Boolean,set:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Checkbox(value,set)}}
@Composable private fun SportBox(day:Int,life:DailyLife,store:DailyLifeStore,refresh:()->Unit){var activity by remember{mutableStateOf("")};var mins by remember{mutableStateOf("")};CardX{Text("SPORT",fontWeight=FontWeight.Bold);life.sports.forEach{x->Row(Modifier.fillMaxWidth()){Text("${x.activity} ${if(x.minutes>0)"· ${x.minutes}m" else ""}",Modifier.weight(1f));TextButton(onClick={store.deleteSport(day,x.id);refresh()}){Text("Delete")}}};OutlinedTextField(activity,{activity=it},Modifier.fillMaxWidth(),placeholder={Text("Activity")});OutlinedTextField(mins,{mins=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),placeholder={Text("Minutes")});Button(onClick={store.addSport(day,activity,mins.toIntOrNull()?:0);activity="";mins="";refresh()},enabled=activity.isNotBlank()){Text("+ Add activity")}}}

@Composable fun Shutdown(s:Store,onSaved:()->Unit){val d=s.day();var finished by remember{mutableStateOf(s.p.getString("shutdown_finished_$d","")?:"")};var stole by remember{mutableStateOf(s.p.getString("shutdown_stole_$d","")?:"")};var tomorrow by remember{mutableStateOf(s.p.getString("shutdown_tomorrow_$d","")?:"")};Page("Daily Shutdown"){Text("Take 1 minute to reflect.",color=Muted);OutlinedTextField(finished,{finished=it},Modifier.fillMaxWidth(),label={Text("What did I finish today?")});OutlinedTextField(stole,{stole=it},Modifier.fillMaxWidth(),label={Text("What stole my attention?")});OutlinedTextField(tomorrow,{tomorrow=it},Modifier.fillMaxWidth(),label={Text("Tomorrow's One Thing?")});Primary("Save & Close Day"){s.p.edit().putString("shutdown_finished_$d",finished).putString("shutdown_stole_$d",stole).putString("shutdown_tomorrow_$d",tomorrow).putBoolean("shutdown_done_$d",true).putString("one_thing_${(d+1).coerceAtMost(7)}",tomorrow).apply();onSaved()}}}

@Composable fun AttentionProgress(s:Store){val sessions=s.sessions();val n=s.total("sessions");val avg=if(n==0)0.0 else s.total("focusSum").toDouble()/n;val snap=AttentionSnapshot(s.total("mins"),avg,s.total("urges"),s.total("shield"),s.total("bypasses"),s.total("distracting"));Page("Progress"){Text("Attention Score",color=Muted);Text("${AttentionEngine.score(snap)}",fontSize=56.sp,fontWeight=FontWeight.Bold,color=Mint);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("FOCUS","${s.total("mins")}m",Modifier.weight(1f));Metric("SESSIONS","$n",Modifier.weight(1f))};AttentionEngine.insight(sessions)?.let{CardX{Text("INSIGHT",color=Mint);Text(it)}};(1..7).forEach{d->CardX{Text("Day $d · ${s.metric("mins",d)}m · ${s.metric("sessions",d)} sessions")}}}}
