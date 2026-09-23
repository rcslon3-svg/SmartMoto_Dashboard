package demo.cyd.companion;
import android.content.*;
import android.os.*;
final class ObserverControl {
 static IBinder watched;static IBinder.DeathRecipient death;static String status="Команда завершения ещё не отправлялась";static boolean pending;
 static Intent command(String value){return new Intent().setClassName("demo.cyd.companion","demo.cyd.companion.BridgeCommandReceiver").addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("command",value);}
 static void terminate(Context context){if(pending)throw new IllegalStateException("Предыдущая команда ещё выполняется");Context c=context.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());pending=true;status="Ожидается ответ моста";
  Journal.event(c,"OBSERVER_CONTROL","TERMINATE_REQUEST","Explicit command can start bridge to terminate it; subsequent observation sends no requests");
  Runnable deadline=()->{if(pending){pending=false;status="За 10 секунд подтверждение смерти процесса не получено. Успех не установлен.";Journal.event(c,"OBSERVER_CONTROL","NO_DEATH_CONFIRMATION",status);}};main.postDelayed(deadline,10000);
  ResultReceiver reply=new ResultReceiver(main){@Override protected void onReceiveResult(int code,Bundle b){if(!pending||code!=1)return;
   try{if(watched!=null&&death!=null)watched.unlinkToDeath(death,0);watched=b.getBinder("token");if(watched==null)throw new IllegalStateException("No process token");String session=b.getString("session");int pid=b.getInt("pid");
    Journal.event(c,"OBSERVER_CONTROL","BRIDGE_IDENTIFIED","pid="+pid+" session="+session+" firstComponent="+b.getString("firstComponent"));
    death=()->{pending=false;status="Смерть процесса моста подтверждена Android (Binder), PID "+pid+". Наблюдаю новые события без обращения к мосту.";Journal.event(c,"OBSERVER_CONTROL","BRIDGE_PROCESS_DIED","pid="+pid+" session="+session);main.removeCallbacks(deadline);};
    watched.linkToDeath(death,0);ResultReceiver ack=b.getParcelable("ack",ResultReceiver.class);if(ack==null)throw new IllegalStateException("No acknowledgement receiver");status="Наблюдение смерти процесса установлено; команда подтверждена";ack.send(1,Bundle.EMPTY);
   }catch(Exception e){pending=false;status="Завершение не подтверждено: "+e;Journal.event(c,"OBSERVER_CONTROL","HANDSHAKE_ERROR",e.toString());}
  }};
  c.sendBroadcast(command("TERMINATE").putExtra("reply",IpcResults.portable(reply)),EventRelay.PERMISSION);
 }
}
