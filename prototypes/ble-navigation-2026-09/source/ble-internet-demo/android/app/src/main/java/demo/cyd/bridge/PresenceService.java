package demo.cyd.bridge;
import android.companion.*;
public class PresenceService extends CompanionDeviceService {
 @Override public void onCreate(){super.onCreate();Startup.record(this,"Android создал CompanionDeviceService; PID="+android.os.Process.myPid());}
 @Override public void onDestroy(){Startup.record(this,"Android закрыл CompanionDeviceService");super.onDestroy();}
 private void appeared(String address){var p=getSharedPreferences("bridge",0);if(address.equalsIgnoreCase(p.getString("address",""))){AutostartTest.nearby=true;if("awaiting_return".equals(p.getString("test_phase",""))&&p.getInt("test_exit_pid",0)!=android.os.Process.myPid()){Startup.record(this,"ТЕСТ: CDM запустил новый процесс PID="+android.os.Process.myPid()+"; BLE-соединение ещё проверяется");p.edit().putString("test_phase","process_started").commit();}p.edit().putBoolean("presence_known",true).putBoolean("presence_near",true).commit();Startup.start(this,"BLE: плата появилась");}}
 private void disappeared(String address){var p=getSharedPreferences("bridge",0);if(address.equalsIgnoreCase(p.getString("address",""))){AutostartTest.nearby=false;p.edit().putBoolean("presence_known",true).putBoolean("presence_near",false).commit();Startup.record(this,"BLE: плата исчезла — подтверждено CDM");}}
 @Override public void onDeviceAppeared(String address){appeared(address);}
 @android.annotation.TargetApi(33) @Override public void onDeviceAppeared(AssociationInfo info){if(info.getDeviceMacAddress()!=null)appeared(info.getDeviceMacAddress().toString());}
 @Override public void onDeviceDisappeared(String address){disappeared(address);}
 @android.annotation.TargetApi(33) @Override public void onDeviceDisappeared(AssociationInfo info){if(info.getDeviceMacAddress()!=null)disappeared(info.getDeviceMacAddress().toString());}
}
