package demo.cyd.companion;
import android.bluetooth.le.*;
import android.content.*;
import android.os.SystemClock;
import java.util.ArrayList;
public class ScanReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){
  int error=i.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE,0);if(error!=0){Journal.event(c,"PI_SCAN","ERROR","code="+error);return;}
  ArrayList<ScanResult> results=i.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,ScanResult.class);
  if(results==null||results.isEmpty()){Journal.event(c,"PI_SCAN","EMPTY","No scan results in delivery");return;}
  try{ScanResult latest=null;for(ScanResult r:results)if(r.getDevice().getAddress().equalsIgnoreCase(Config.address(c))&&(latest==null||r.getTimestampNanos()>latest.getTimestampNanos()))latest=r;
   if(latest==null)return;
   long age=(SystemClock.elapsedRealtimeNanos()-latest.getTimestampNanos())/1000000;
   if(age<0||age>15000){Journal.event(c,"PI_SCAN","STALE_BATCH","ageMs="+age+"; no GATT wake");return;}
   // Avoid logging every advertisement. The first delivery in each process is always recorded.
   if(LinkService.ready&&SystemClock.elapsedRealtime()-last<60000)return;
   if(!LinkService.ready&&SystemClock.elapsedRealtime()-last<10000)return;
   last=SystemClock.elapsedRealtime();Monitoring.wake(c,"PI_SCAN","packets="+results.size()+" rssi="+latest.getRssi()+" newestAgeMs="+age);
  }catch(SecurityException e){Journal.event(c,"PI_SCAN","PERMISSION_ERROR",e.toString());}
 }
 static long last=-60000;
}
