package demo.cyd.companion;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,application=BridgeIdleTest.TestApp.class,manifest=Config.NONE)
public class BridgeIdleTest {
 public static class TestApp extends BridgeApp {
  boolean terminated;
  @Override public void onCreate(){Journal.instance=null;Journal.firstComponent="";super.onCreate();}
  @Override protected void terminateIdleProcess(){terminated=true;}
 }
 TestApp app(){return (TestApp)RuntimeEnvironment.getApplication();}
 void advance(int seconds){ShadowLooper.idleMainLooper(seconds,TimeUnit.SECONDS);}
 @Test public void absentBoardExitsAtMinute(){TestApp a=app();advance(59);assertFalse(a.terminated);advance(1);assertTrue(a.terminated);}
 @Test public void connectedBoardPreventsExitAndDisconnectStartsNewMinute(){TestApp a=app();advance(50);BridgeApp.connection(true);advance(120);assertFalse(a.terminated);BridgeApp.connection(false);advance(59);assertFalse(a.terminated);advance(1);assertTrue(a.terminated);}
 @Test public void repeatedDisconnectedEventsDoNotExtendDeadline(){TestApp a=app();advance(40);BridgeApp.connection(false);advance(20);assertTrue(a.terminated);}
 @Test public void reconnectionCancelsPendingExit(){TestApp a=app();BridgeApp.connection(true);BridgeApp.connection(false);advance(59);BridgeApp.connection(true);advance(120);assertFalse(a.terminated);}
 @Test public void phoneTimeKeepsServerTextSeparate(){assertEquals("\rS|9|2|0 / 1 / 2|Hello|23.59.59\n",Protocol.response(9,2,new long[]{0,1,2},"Hello","23.59.59"));assertThrows(IllegalArgumentException.class,()->Protocol.response(1,0,new long[]{0,0,0},"x","24.00.00"));}
}
