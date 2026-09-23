package demo.cyd.companion;
import android.app.*;
import android.bluetooth.*;
import android.companion.*;
import android.content.*;
import android.os.*;
import org.json.*;
final class Diagnostics {
 static boolean captured;
 static void once(Context c,String source){if(!captured){captured=true;snapshot(c,source);}}
 static void snapshot(Context c,String source){try{
  JSONObject d=new JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("api",Build.VERSION.SDK_INT).put("fingerprint",Build.FINGERPRINT).put("enabled",Config.enabled(c)).put("observeOnly",Config.get(c).getBoolean("observe_only",false)).put("bluetoothPermissions",Monitoring.permissions(c)).put("backgroundRestrictedNow",c.getSystemService(ActivityManager.class).isBackgroundRestricted()).put("batteryExempt",c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName())).put("interactive",c.getSystemService(PowerManager.class).isInteractive()).put("linkReadyInThisProcess",LinkService.ready);
  JSONArray associations=new JSONArray();for(AssociationInfo a:c.getSystemService(CompanionDeviceManager.class).getMyAssociations())associations.put(new JSONObject().put("id",a.getId()).put("mac",String.valueOf(a.getDeviceMacAddress())).put("rawSystemObject",a.toString()));d.put("associations",associations).put("associationSource","CompanionDeviceManager.getMyAssociations: current caller package; rawSystemObject is diagnostic text, not a stable flag API");
  try{BluetoothAdapter a=c.getSystemService(BluetoothManager.class).getAdapter();d.put("btState",a==null?-1:a.getState());if(a!=null&&!Config.address(c).isEmpty())d.put("bond",a.getRemoteDevice(Config.address(c)).getBondState());}catch(SecurityException e){d.put("bluetoothReadError",e.toString());}
  Journal.event(c,source,"SNAPSHOT",d.toString());
  int prior=Config.get(c).getInt("last_seen_boot",-1);if(prior!=-1&&prior!=Journal.boot)Journal.event(c,source,"BOOT_COUNT_CHANGED","previous="+prior+" now="+Journal.boot+"; compare with recorded BOOT_COMPLETED; absence is not a cause");Config.get(c).edit().putInt("last_seen_boot",Journal.boot).commit();
  long last=Config.get(c).getLong("exit_timestamp",0),newest=last;for(ApplicationExitInfo e:c.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(c.getPackageName(),0,8))if(e.getTimestamp()>last){Journal.event(c,"ANDROID_EXIT_HISTORY","PREVIOUS_PROCESS","pid="+e.getPid()+" reason="+e.getReason()+" status="+e.getStatus()+" at="+e.getTimestamp()+" description="+e.getDescription());newest=Math.max(newest,e.getTimestamp());}Config.get(c).edit().putLong("exit_timestamp",newest).commit();
 }catch(Exception e){Journal.event(c,source,"SNAPSHOT_ERROR",e.toString());}}
}
