package cz.ioff.app
import org.junit.Assert.*;import org.junit.Test
class SessionMathTest{@Test fun sessionCarriesExperimentDay(){val s=Session(1,2,4,60,42,3,8,"goal","done",true);assertEquals(4,s.experimentDay);assertEquals(42,s.actual);assertTrue(s.interrupted)}@Test fun aggregationCanSeparateDays(){val xs=listOf(Session(0,0,1,60,60,1,8,"","",false),Session(0,0,2,60,30,2,7,"","",true));assertEquals(60,xs.filter{it.experimentDay==1}.sumOf{it.actual});assertEquals(30,xs.filter{it.experimentDay==2}.sumOf{it.actual})}}
