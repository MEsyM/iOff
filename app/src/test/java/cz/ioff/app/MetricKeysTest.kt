package cz.ioff.app
import org.junit.Assert.*;import org.junit.Test
class MetricKeysTest{@Test fun experimentDaysAreIsolated(){assertNotEquals(MetricKeys.experiment(1,"mins"),MetricKeys.experiment(2,"mins"));assertEquals("exp_1_mins",MetricKeys.experiment(1,"mins"));assertEquals("exp_7_focus",MetricKeys.experiment(7,"focus"))}@Test fun calendarAndExperimentNeverCollide(){assertNotEquals(MetricKeys.calendar("20260913","mins"),MetricKeys.experiment(1,"mins"))}}
