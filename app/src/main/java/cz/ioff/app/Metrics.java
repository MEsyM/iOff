package cz.ioff.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Metrics {
    private Metrics() {}
    public static String calendarDate(long millis) { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(millis)); }
    public static String dayKey(String metric, int experimentDay) { return metric + "_d" + Math.max(1, Math.min(7, experimentDay)); }
    public static String calendarKey(String metric, long millis) { return metric + "_" + calendarDate(millis); }
    public static int safeDay(int day) { return Math.max(1, Math.min(7, day)); }
    public static int actualMinutes(long start, long end, int planned, boolean interrupted) {
        int elapsed=(int)Math.max(0,(end-start)/60000L);
        return interrupted ? elapsed : Math.max(elapsed,planned);
    }
}
