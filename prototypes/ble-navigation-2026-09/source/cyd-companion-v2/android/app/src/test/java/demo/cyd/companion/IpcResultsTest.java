package demo.cyd.companion;
import android.app.Application;
import android.os.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,application=Application.class,manifest=Config.NONE)
public class IpcResultsTest {
 private ClassLoader otherApp(){return new ClassLoader(ResultReceiver.class.getClassLoader()){
  @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
   if(name.startsWith("demo.cyd."))throw new ClassNotFoundException(name);
   return super.loadClass(name,resolve);
  }
 };}
 private ResultReceiver crossApp(ResultReceiver receiver){Parcel parcel=Parcel.obtain();try{
  parcel.writeParcelable(receiver,0);parcel.setDataPosition(0);
  return parcel.readParcelable(otherApp(),ResultReceiver.class);
 }finally{parcel.recycle();}}
 @Test public void privateSubclassCannotBeLoadedByOtherApk(){
  ResultReceiver original=new ResultReceiver(null){};
  assertThrows(BadParcelableException.class,()->crossApp(original));
 }
 @Test public void portableReceiverDeliversReplyWithoutSenderClasses(){
  int[] result={-1};ResultReceiver original=new ResultReceiver(new Handler(Looper.getMainLooper())){
   @Override protected void onReceiveResult(int code,Bundle data){result[0]=code+data.getInt("value");}
  };
  ResultReceiver wire=IpcResults.portable(original);assertEquals(ResultReceiver.class,wire.getClass());
  ResultReceiver remote=crossApp(wire);Bundle data=new Bundle();data.putInt("value",4);remote.send(7,data);ShadowLooper.idleMainLooper();assertEquals(11,result[0]);
 }
 @Test public void acknowledgementBundleCanBeReadWithoutBridgeClasses(){
  int[] result={0};ResultReceiver original=new ResultReceiver(new Handler(Looper.getMainLooper())){
   @Override protected void onReceiveResult(int code,Bundle data){result[0]=code;}
  };
  Bundle bundle=new Bundle();bundle.putBinder("token",new Binder());bundle.putParcelable("ack",IpcResults.portable(original));
  Parcel p=Parcel.obtain();try{p.writeBundle(bundle);p.setDataPosition(0);Bundle received=p.readBundle(otherApp());assertNotNull(received.getBinder("token"));received.getParcelable("ack",ResultReceiver.class).send(1,Bundle.EMPTY);ShadowLooper.idleMainLooper();assertEquals(1,result[0]);}finally{p.recycle();}
 }
}
