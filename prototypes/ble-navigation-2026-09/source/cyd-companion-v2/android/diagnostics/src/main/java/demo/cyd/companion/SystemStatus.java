package demo.cyd.companion;
import android.content.*;
import android.content.pm.*;
import org.json.*;
/** Queries system PackageManager, never the bridge process. No hidden APIs or reflection. */
final class SystemStatus {
 static final String BRIDGE="demo.cyd.companion";static String previous="";
 static String read(Context c,boolean record){
  JSONObject data=new JSONObject();StringBuilder display=new StringBuilder();
  try{PackageManager pm=c.getPackageManager();PackageInfo info=pm.getPackageInfo(BRIDGE,PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));ApplicationInfo a=pm.getApplicationInfo(BRIDGE,PackageManager.ApplicationInfoFlags.of(0));
   boolean stopped=(a.flags&ApplicationInfo.FLAG_STOPPED)!=0;
   data.put("installed",true).put("version",info.versionName).put("lastUpdate",info.lastUpdateTime).put("enabled",a.enabled).put("stopped",stopped).put("suspended",(a.flags&ApplicationInfo.FLAG_SUSPENDED)!=0).put("enabledSetting",pm.getApplicationEnabledSetting(BRIDGE)).put("matchingSignature",pm.checkSignatures(BRIDGE,c.getPackageName())==PackageManager.SIGNATURE_MATCH);
   display.append("Системный пакет моста: ").append(info.versionName).append("\nВключён: ").append(a.enabled?"да":"нет").append("\nFLAG_STOPPED: ").append(stopped?"ДА — пакет остановлен":"нет").append("\n");
   JSONObject permissions=new JSONObject();for(String permission:new String[]{"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT","android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE","android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND"}){boolean granted=pm.checkPermission(permission,BRIDGE)==PackageManager.PERMISSION_GRANTED;permissions.put(permission,granted);display.append(permission.substring(permission.lastIndexOf('.')+1)).append(": ").append(granted?"выдано":"не выдано").append('\n');}data.put("permissions",permissions);
   ComponentName component=new ComponentName(BRIDGE,BRIDGE+".PresenceService");ServiceInfo service=pm.getServiceInfo(component,PackageManager.ComponentInfoFlags.of(0));data.put("cdmServiceDeclared",true).put("cdmServiceEnabled",service.enabled).put("cdmServicePermission",service.permission).put("cdmComponentSetting",pm.getComponentEnabledSetting(component));
   display.append("Сервис CDM объявлен: да; включён: ").append(service.enabled?"да":"нет").append('\n');
  }catch(PackageManager.NameNotFoundException e){display.append("Пакет моста или его компонент не найден.\n");try{data.put("lookupError",e.toString());}catch(JSONException ignored){}}
  catch(Exception e){display.append("Ошибка чтения системы: ").append(e).append('\n');try{data.put("error",e.toString());}catch(JSONException ignored){}}
  display.append("Регистрация наблюдения CDM другого приложения: публичный API Android 13 её не раскрывает.\nСнимок привязок от моста — ниже; это состояние на время снимка, не текущее чтение из диагноста.\n");
  display.append(Config.get(c).getString("cdm_snapshot","Снимок CDM ещё не получен."));
  String json=data.toString();if(record&&!json.equals(previous)){previous=json;Journal.event(c,"OBSERVER_SYSTEM_QUERY","PACKAGE_STATE",json);}return display.toString();
 }
}
