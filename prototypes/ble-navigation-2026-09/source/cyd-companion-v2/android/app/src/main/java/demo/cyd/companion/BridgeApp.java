package demo.cyd.companion;

import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Process-local idle deadline. System BLE observation is deliberately left registered. */
public class BridgeApp extends App {
 private final Handler main = new Handler(Looper.getMainLooper());
 private long disconnectedAt;
 private boolean connected;
 static BridgeApp instance;
 @Override public void onCreate() {
  super.onCreate(); instance=this; disconnectedAt=SystemClock.elapsedRealtime();
  Journal.event(this,"IDLE_EXIT","ARMED","No GATT connection; timeout=60000ms");
  main.postDelayed(check,1000);
 }
 static void connection(boolean value) {
  BridgeApp app=instance;
  if(app==null || app.connected==value)return;
  app.connected=value;
  if(!value)app.disconnectedAt=SystemClock.elapsedRealtime();
  Journal.event(app,"IDLE_EXIT",value?"CANCELLED":"ARMED",value?"GATT connected":"GATT disconnected; timeout=60000ms");
 }
 private final Runnable check=new Runnable(){public void run(){
  long elapsed=SystemClock.elapsedRealtime()-disconnectedAt;
  if(!connected && elapsed>=60000){
   Journal.event(BridgeApp.this,"IDLE_EXIT","COMMIT","disconnectedMs="+elapsed+"; observation unchanged; not force-stop");
   terminateIdleProcess();
   return;
  }
  main.postDelayed(this,1000);
 }};
 protected void terminateIdleProcess(){
  for(ActivityManager.AppTask task:getSystemService(ActivityManager.class).getAppTasks())task.finishAndRemoveTask();
  android.os.Process.killProcess(android.os.Process.myPid());
 }
}
