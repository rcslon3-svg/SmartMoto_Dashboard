package demo.cyd.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;
import java.time.Instant;
import java.util.concurrent.*;

/** Explicit diagnostic session. Never sends commands to the bridge. */
public class ObserverRemoteService extends Service {
 final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 final Handler main=new Handler(Looper.getMainLooper());
 volatile boolean closed; boolean started;
 final Runnable deadline=()->finish("SESSION_ENDED","Diagnostic session reached one hour");
 @Override public void onCreate(){super.onCreate();
  NotificationManager manager=getSystemService(NotificationManager.class);
  manager.createNotificationChannel(new NotificationChannel("observer_remote","Связь диагноста с компьютером",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,71,new Intent(this,ObserverActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Notification notification=new Notification.Builder(this,"observer_remote").setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("CYD: диагностика с компьютера")
   .setContentText("Передача состояния и приём запросов отчёта. Мост не запускается.").setContentIntent(open).setOngoing(true).build();
  startForeground(71,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(!started){started=true;Journal.event(this,"OBSERVER_REMOTE","SESSION_STARTED","Passive report channel; one-hour session; bridge not contacted");
   main.postDelayed(deadline,3600000);worker.scheduleWithFixedDelay(this::tick,0,5,TimeUnit.SECONDS);}
  return START_NOT_STICKY;
 }
 void tick(){
  if(closed)return;
  try{
   String endpoint=Config.endpoint(this);if(endpoint.isEmpty())throw new IllegalStateException("Server address is empty");
   EventUpload.drain(this);
   JSONObject heartbeat=new JSONObject().put("session",Journal.session).put("pid",android.os.Process.myPid()).put("utc",Instant.now().toString())
    .put("system",SystemStatus.read(this,false)).put("lastBridge",Config.get(this).getString("last_bridge_event","No bridge events"))
    .put("uploadAck",Config.get(this).getLong("ack",0));
   JSONObject payload=new JSONObject().put("installation",Journal.install).put("heartbeat",heartbeat);
   String result=Config.get(this).getString("remote_result","");if(!result.isEmpty())payload.put("result",new JSONObject(result));
   JSONObject response=Http.call(endpoint+"/api/v2/observer/poll",payload);
   if(closed)return;
   Config.get(this).edit().putString("remote_status","Компьютер ответил: "+Instant.now()).apply();
   JSONObject command=response.optJSONObject("command");
   if(command==null||command.getString("id").equals(Config.get(this).getString("remote_last_command","")))return;
   String id=command.getString("id");JSONObject answer=new JSONObject().put("id",id);
   if(!command.getString("type").equals("REPORT")){answer.put("outcome","ERROR").put("detail","Unsupported command");}
   else {
    // A retry may repeat a passive snapshot after a crash, but cannot start the bridge.
    String record=Journal.event(this,"OBSERVER_REMOTE","REPORT",new JSONObject().put("commandId",id).put("system",SystemStatus.read(this,false))
     .put("lastBridge",Config.get(this).getString("last_bridge_event","")).put("bridgeContacted",false).toString());
    answer.put("outcome","REPORT_SAVED").put("detail","Local report seq="+new JSONObject(record).getLong("seq")+"; delivery is confirmed separately by uploadAck; bridge not contacted");
   }
   Config.get(this).edit().putString("remote_last_command",id).putString("remote_result",answer.toString()).commit();
   EventUpload.drain(this);
  }catch(Exception e){Config.get(this).edit().putString("remote_status","Нет подтверждения связи: "+e).apply();}
 }
 void finish(String type,String detail){Journal.event(this,"OBSERVER_REMOTE",type,detail);stopSelf();}
 @Override public void onTimeout(int startId,int fgsType){finish("SYSTEM_TIMEOUT","Android stopped the diagnostic foreground session");}
 @Override public void onDestroy(){closed=true;main.removeCallbacks(deadline);worker.shutdownNow();Config.get(this).edit().putString("remote_status","Сеанс связи с компьютером остановлен").apply();Journal.event(this,"OBSERVER_REMOTE","SESSION_DESTROYED","No bridge contact");super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return null;}
}
