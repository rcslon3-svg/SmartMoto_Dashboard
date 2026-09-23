package demo.cyd.companion;
import android.app.Activity;
import android.content.*;
import org.json.*;
public class MirrorReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){try{
  if(Journal.firstComponent.isEmpty())Journal.event(c,"MIRROR_RECEIVER","ENTRY","Bridge pushed an event; no request sent to bridge");
  String install=i.getStringExtra("installation"),raw=i.getStringExtra("event");if(install==null||raw==null||raw.length()>65536)throw new IllegalArgumentException("invalid relay");
  if(Config.endpoint(c).isEmpty()){String endpoint=i.getStringExtra("endpoint");if(endpoint!=null&&!endpoint.isEmpty())Config.get(c).edit().putString("endpoint",Config.validateEndpoint(endpoint)).commit();}
  accept(c,install,new JSONObject(raw));
  if(isOrderedBroadcast())setResultCode(Activity.RESULT_OK);
 }catch(Exception e){Journal.event(c,"MIRROR_RECEIVER","ERROR",e.toString());if(isOrderedBroadcast())setResultCode(Activity.RESULT_CANCELED);}}
 static void accept(Context c,String install,JSONObject event)throws JSONException {
  java.util.UUID.fromString(install);long highest=Journal.mirroredSequence(c,install);Journal.mirror(c,install,event);
  // Replay must not replace the live state with old rows, including already deduplicated rows.
  if(event.getLong("seq")<highest)return;
  if(event.optString("type").equals("SNAPSHOT")){JSONObject snapshot=new JSONObject(event.optString("detail"));Config.get(c).edit().putString("cdm_snapshot","Снимок моста: "+event.optString("utc")+"\n"+snapshot.optJSONArray("associations")+"\nИсточник: системный getMyAssociations() в процессе моста. rawSystemObject — исходный диагностический текст Android.").apply();}
  Config.get(c).edit().putString("last_bridge_event",event.optString("utc")+" · "+event.optString("source")+" / "+event.optString("type")+" · PID "+event.optInt("pid")).apply();
 }
}
