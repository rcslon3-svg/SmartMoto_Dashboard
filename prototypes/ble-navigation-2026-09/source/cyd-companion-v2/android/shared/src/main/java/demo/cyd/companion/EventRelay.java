package demo.cyd.companion;
import android.app.Activity;
import android.content.*;
import android.os.*;
/** Push only. The observer never queries a bridge provider/service during observation. */
final class EventRelay {
 static final String PERMISSION="demo.cyd.companion.DIAGNOSTICS";
 static Intent intent(Context c,String event){return new Intent().setClassName("demo.cyd.diagnostics","demo.cyd.companion.MirrorReceiver").putExtra("installation",Journal.install).putExtra("event",event).putExtra("endpoint",Config.endpoint(c));}
 static void emit(Context c,String event){if(!c.getPackageName().equals("demo.cyd.companion"))return;try{c.sendBroadcast(intent(c,event),PERMISSION);}catch(RuntimeException ignored){/* Durable local journal remains authoritative. */}}
 static void barrier(Context c,String event,Runnable success,Runnable failure){
  c.sendOrderedBroadcast(intent(c,event),PERMISSION,new BroadcastReceiver(){@Override public void onReceive(Context context,Intent i){if(getResultCode()==Activity.RESULT_OK)success.run();else failure.run();}},new Handler(Looper.getMainLooper()),Activity.RESULT_CANCELED,null,null);
 }
}
