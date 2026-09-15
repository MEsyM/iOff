package cz.ioff.app

import android.content.SharedPreferences

object TestReset {
 fun resetIdeas(p:SharedPreferences)=p.edit().remove("ideas").apply()
 fun resetExperimentKeepIdeas(p:SharedPreferences){val ideas=p.getString("ideas","")?:"";p.edit().clear().putString("ideas",ideas).putInt("day",1).apply()}
 fun resetDay(p:SharedPreferences,day:Int){val d=day.coerceIn(1,7);val e=p.edit();listOf("mins","urges","sessions","focusSum","shield","bypasses","distracting","opens","notifications").forEach{e.remove(MetricKeys.experiment(d,it))};e.remove("daily_life_$d").remove("one_thing_$d").remove("shutdown_finished_$d").remove("shutdown_stole_$d").remove("shutdown_tomorrow_$d").remove("shutdown_done_$d").apply()}
 fun resetAll(p:SharedPreferences)=p.edit().clear().putInt("day",1).apply()
}
