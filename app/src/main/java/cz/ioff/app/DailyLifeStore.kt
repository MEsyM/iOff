package cz.ioff.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SportEntry(val id: Long, val activity: String, val minutes: Int)
data class DailyLife(val noDrugs:Boolean,val noWeed:Boolean,val noAlcohol:Boolean,val sex:Boolean,val sports:List<SportEntry>)

class DailyLifeStore private constructor(private val p:SharedPreferences){
 constructor(context:Context):this(context.getSharedPreferences("ioff",0))
 companion object { fun from(p:SharedPreferences)=DailyLifeStore(p) }
 private fun key(day:Int)="daily_life_${day.coerceIn(1,7)}"
 fun load(day:Int):DailyLife=try{val o=JSONObject(p.getString(key(day),"{}")?:"{}");val a=o.optJSONArray("sports")?:JSONArray();DailyLife(o.optBoolean("noDrugs"),o.optBoolean("noWeed"),o.optBoolean("noAlcohol"),o.optBoolean("sex"),(0 until a.length()).map{i->val x=a.getJSONObject(i);SportEntry(x.optLong("id"),x.optString("activity"),x.optInt("minutes"))})}catch(_:Exception){DailyLife(false,false,false,false,emptyList())}
 fun save(day:Int,x:DailyLife){val a=JSONArray();x.sports.forEach{a.put(JSONObject().put("id",it.id).put("activity",it.activity).put("minutes",it.minutes))};p.edit().putString(key(day),JSONObject().put("noDrugs",x.noDrugs).put("noWeed",x.noWeed).put("noAlcohol",x.noAlcohol).put("sex",x.sex).put("sports",a).toString()).apply()}
 fun addSport(day:Int,activity:String,minutes:Int){val clean=activity.trim();if(clean.isBlank())return;val x=load(day);save(day,x.copy(sports=x.sports+SportEntry(System.currentTimeMillis(),clean,minutes.coerceAtLeast(0))))}
 fun deleteSport(day:Int,id:Long){val x=load(day);save(day,x.copy(sports=x.sports.filterNot{it.id==id}))}
}
