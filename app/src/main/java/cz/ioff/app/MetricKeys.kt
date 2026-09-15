package cz.ioff.app
object MetricKeys { fun experiment(day:Int, key:String)="exp_${day.coerceIn(1,7)}_$key"; fun calendar(date:String,key:String)="cal_${date}_$key" }
