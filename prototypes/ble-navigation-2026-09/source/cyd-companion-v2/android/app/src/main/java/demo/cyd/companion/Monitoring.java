package demo.cyd.companion;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import java.util.List;

final class Monitoring {
 static PendingIntent scanIntent(Context c){return PendingIntent.getBroadcast(c,8,new Intent(c,ScanReceiver.class).setAction("demo.cyd.companion.SCAN"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);}
 static boolean permissions(Context c){return c.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 static boolean associated(Context c){String a=Config.address(c);return c.getSystemService(CompanionDeviceManager.class).getMyAssociations().stream().anyMatch(i->i.getDeviceMacAddress()!=null&&i.getDeviceMacAddress().toString().equalsIgnoreCase(a));}
 static void arm(Context c,String source){
  Journal.event(c,source,"ARM_REQUEST","Restoring subscriptions; does not start GATT");
  if(!Config.enabled(c)||!permissions(c)||Config.address(c).isEmpty()){Journal.event(c,source,"ARM_BLOCKED","disabled, missing Bluetooth permission or address");return;}
  try{if(!associated(c)){Journal.event(c,source,"ASSOCIATION_MISSING","Select board in system companion chooser");return;}
   c.getSystemService(CompanionDeviceManager.class).startObservingDevicePresence(Config.address(c));
   Journal.event(c,source,"CDM_OBSERVE_ACCEPTED","API returned; this is not evidence of a radio detection");
  }catch(Exception e){Journal.event(c,source,"CDM_OBSERVE_ERROR",e.toString());}
  try{BluetoothAdapter a=c.getSystemService(BluetoothManager.class).getAdapter();BluetoothLeScanner scanner=a==null?null:a.getBluetoothLeScanner();if(scanner==null)throw new IllegalStateException("Bluetooth scanner unavailable");
   int result=scanner.startScan(List.of(new ScanFilter.Builder().setDeviceAddress(Config.address(c)).build()),new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setReportDelay(3000).build(),scanIntent(c));
   Journal.event(c,source,"PI_SCAN_REGISTRATION","result="+result+" (0=accepted, 1=already started); not evidence of delivery");
  }catch(SecurityException e){Journal.event(c,source,"PI_SCAN_PERMISSION_ERROR",e.toString());}catch(Exception e){Journal.event(c,source,"PI_SCAN_ERROR",e.toString());}
 }
 static void disable(Context c){Config.get(c).edit().putBoolean("enabled",false).commit();
  try{if(!Config.address(c).isEmpty())c.getSystemService(CompanionDeviceManager.class).stopObservingDevicePresence(Config.address(c));}catch(Exception e){Journal.event(c,"USER","CDM_STOP_ERROR",e.toString());}
  try{BluetoothAdapter a=c.getSystemService(BluetoothManager.class).getAdapter();if(a!=null&&a.getBluetoothLeScanner()!=null)a.getBluetoothLeScanner().stopScan(scanIntent(c));}catch(SecurityException e){Journal.event(c,"USER","SCAN_STOP_PERMISSION_ERROR",e.toString());}catch(Exception e){Journal.event(c,"USER","SCAN_STOP_ERROR",e.toString());}
  c.stopService(new Intent(c,LinkService.class));Journal.event(c,"USER","DISABLED","Queued wake events will be ignored");
 }
 static void wake(Context c,String source,String detail){
  Journal.event(c,source,"WAKE",detail);
  Diagnostics.once(c,source);
  if(!Config.enabled(c)||!permissions(c)||Config.endpoint(c).isEmpty()||Config.address(c).isEmpty()){Journal.event(c,source,"LINK_BLOCKED","disabled or incomplete configuration");return;}
  if(Config.get(c).getBoolean("observe_only",false)){Journal.event(c,source,"OBSERVE_ONLY","GATT intentionally not requested; diagnostic mode");return;}
  try{if(!associated(c)){Journal.event(c,source,"LINK_BLOCKED","No companion association");return;}
   c.startForegroundService(new Intent(c,LinkService.class).putExtra("source",source));
  }catch(Exception e){Journal.event(c,source,"FGS_START_REJECTED",e.toString());}
 }
}

