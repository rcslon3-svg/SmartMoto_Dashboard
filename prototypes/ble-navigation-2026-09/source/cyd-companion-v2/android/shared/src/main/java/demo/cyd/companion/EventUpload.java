package demo.cyd.companion;
import android.content.Context;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;
/** Fast delivery while alive, persisted JobScheduler delivery for recovery. */
final class EventUpload {
 static final Handler handler=new Handler(Looper.getMainLooper());
 static final ExecutorService worker=Executors.newSingleThreadExecutor();
 static boolean scheduled;static final Object uploadLock=new Object();
 static synchronized void request(Context c){if(scheduled||Config.endpoint(c).isEmpty())return;scheduled=true;Context app=c.getApplicationContext();handler.postDelayed(()->worker.execute(()->{boolean more=drain(app);synchronized(EventUpload.class){scheduled=false;}if(more)handler.postDelayed(()->request(app),30000);}),2000);}
 static boolean drain(Context c){synchronized(uploadLock){try{
  for(int n=0;n<20;n++){JSONArray a=Journal.batch(c);if(a.length()==0)return false;long last=a.getJSONObject(a.length()-1).getLong("seq");JSONObject response=Http.call(Config.endpoint(c)+"/api/v2/events",new JSONObject().put("schema",2).put("installation",Journal.install).put("events",a));if(response.getLong("ack")!=last)throw new IllegalStateException("Acknowledgement mismatch");Journal.acknowledge(c,last);Config.get(c).edit().putString("upload","Отправлено до #"+last+" · "+java.time.Instant.now()).apply();}
  return Journal.batch(c).length()>0;
 }catch(Exception e){Config.get(c).edit().putString("upload","Не доставлено; журнал сохранён: "+e).apply();return true;}}}
}
