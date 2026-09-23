package demo.cyd.companion;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.UUID;

/** Each insert is committed before the component continues. Never stores only the last report. */
final class Journal extends SQLiteOpenHelper {
 static Journal instance; static final String session=UUID.randomUUID().toString();
 static String install; static int boot; static String firstComponent="";
 Journal(Context c){super(c,"events.db",null,1);}
 static synchronized void init(Context c){if(instance!=null)return;instance=new Journal(c.getApplicationContext());
  install=Config.get(c).getString("installation","");if(install.isEmpty()){install=UUID.randomUUID().toString();Config.get(c).edit().putString("installation",install).commit();}
  boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
 }
 @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL)");db.execSQL("CREATE TABLE mirror_seen(key TEXT PRIMARY KEY)");}
 @Override public void onUpgrade(SQLiteDatabase db,int a,int b){throw new IllegalStateException("No migration");}
 static synchronized String event(Context c,String source,String type,String detail){
  init(c);String json; try{
   if(firstComponent.isEmpty()&&!source.equals("PROCESS")&&!source.equals("IDLE_EXIT"))firstComponent=source;
   JSONObject o=new JSONObject().put("app",c.getPackageName()).put("utc",java.time.Instant.now().toString()).put("elapsedMs",SystemClock.elapsedRealtime()).put("boot",boot).put("pid",android.os.Process.myPid()).put("session",session).put("firstComponent",firstComponent).put("source",source).put("type",type).put("detail",detail==null?"":detail).put("address",Config.address(c));
   ContentValues v=new ContentValues();v.put("body",o.toString());long seq=instance.getWritableDatabase().insertOrThrow("events",null,v);json=o.put("seq",seq).toString();
  }catch(JSONException e){throw new IllegalStateException(e);}
  // Only the independent observer uploads. A bridge upload job would itself restart the bridge
  // after termination and contaminate the BLE-wakeup experiment.
  if(c.getPackageName().equals("demo.cyd.diagnostics")){UploadJob.schedule(c);EventUpload.request(c);}
  EventRelay.emit(c,json);
  return json;
 }
 static synchronized void mirror(Context c,String installation,JSONObject received)throws JSONException {
  init(c);SQLiteDatabase db=instance.getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("key",installation+":"+received.getLong("seq"));if(db.insertWithOnConflict("mirror_seen",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1){event(c,"MIRROR",received.getString("source")+" / "+received.getString("type"),new JSONObject().put("installation",installation).put("event",received).toString());}db.setTransactionSuccessful();}finally{db.endTransaction();}
 }
 static synchronized long lastSequence(Context c){init(c);try(Cursor cur=instance.getReadableDatabase().rawQuery("SELECT COALESCE(MAX(seq),0) FROM events",null)){cur.moveToFirst();return cur.getLong(0);}}
 static synchronized long mirroredSequence(Context c,String installation){init(c);try(Cursor cur=instance.getReadableDatabase().rawQuery("SELECT COALESCE(MAX(CAST(substr(key,38) AS INTEGER)),0) FROM mirror_seen WHERE key LIKE ?",new String[]{installation+":%"})){cur.moveToFirst();return cur.getLong(0);}}
 static synchronized JSONArray archivePage(Context c,long after,long through)throws JSONException {
  init(c);JSONArray page=new JSONArray();int bytes=0;
  try(Cursor cur=instance.getReadableDatabase().rawQuery("SELECT seq,body FROM events WHERE seq>? AND seq<=? ORDER BY seq LIMIT 24",new String[]{""+after,""+through})){
   while(cur.moveToNext()){JSONObject event=new JSONObject(cur.getString(1)).put("seq",cur.getLong(0));int size=event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(page.length()>0&&bytes+size>90000)break;page.put(event);bytes+=size;}
  }return page;
 }
 static synchronized JSONArray batch(Context c) throws JSONException {
  init(c);JSONArray a=new JSONArray();long ack=Config.get(c).getLong("ack",0);
  int size=0;try(Cursor cur=instance.getReadableDatabase().rawQuery("SELECT seq,body FROM events WHERE seq>? ORDER BY seq LIMIT 80",new String[]{""+ack})){while(cur.moveToNext()){JSONObject e=new JSONObject(cur.getString(1)).put("seq",cur.getLong(0));int bytes=e.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(size+bytes>95000&&a.length()>0)break;a.put(e);size+=bytes;}}return a;
 }
 static synchronized void acknowledge(Context c,long seq){
  if(seq<=Config.get(c).getLong("ack",0))return;
  Config.get(c).edit().putLong("ack",seq).commit();
  instance.getWritableDatabase().delete("events","seq < ?",new String[]{""+Math.max(0,seq-3000)});
 }
 static synchronized String recent(Context c){init(c);StringBuilder s=new StringBuilder();
  try(Cursor cur=instance.getReadableDatabase().rawQuery("SELECT seq,body FROM events ORDER BY seq DESC LIMIT 45",null)){while(cur.moveToNext())try{JSONObject o=new JSONObject(cur.getString(1));boolean mirrored=o.optString("source").equals("MIRROR");if(mirrored)o=new JSONObject(o.getString("detail")).getJSONObject("event");s.append(mirrored?"МОСТ #":"ДИАГНОСТ #").append(mirrored?o.optLong("seq"):cur.getLong(0)).append(' ').append(o.optString("utc")).append("\n").append(o.optString("source")).append(" · ").append(o.optString("type")).append(" · PID ").append(o.optInt("pid")).append(" · boot ").append(o.optInt("boot")).append("\n").append(o.optString("detail")).append("\n\n");}catch(JSONException ignored){}}
  return s.toString();
 }
}
