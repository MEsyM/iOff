package cz.ioff.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class MetricsTest {
 @Test public void experimentDaysNeverShareKeys(){assertNotEquals(Metrics.dayKey("mins",1),Metrics.dayKey("mins",2));}
 @Test public void dayIsClamped(){assertEquals(1,Metrics.safeDay(-4));assertEquals(7,Metrics.safeDay(99));assertEquals(4,Metrics.safeDay(4));}
 @Test public void completedSessionGetsPlannedMinutes(){assertEquals(60,Metrics.actualMinutes(0,59*60000L,60,false));}
 @Test public void interruptedSessionGetsElapsedMinutes(){assertEquals(12,Metrics.actualMinutes(0,12*60000L,60,true));}
 @Test public void metricNamesRemainIsolated(){assertNotEquals(Metrics.dayKey("mins",3),Metrics.dayKey("urges",3));}
}
