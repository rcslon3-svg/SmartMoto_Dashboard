package demo.cyd.companion;
import android.content.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Signature-protected cooperative termination, not force-stop and not a service binding. */
public class BridgeCommandReceiver extends BroadcastReceiver {
 static final Binder processToken=new Binder();static boolean terminating;
 @Override public void onReceive(Context c,Intent intent){
  String command=intent.getStringExtra("command");if(!"ARCHIVE".equals(command)||intent.getLongExtra("after",0)==0)Journal.event(c,"DIAGNOSTIC_COMMAND","RECEIVED",command);
  if("SNAPSHOT".equals(command)){Diagnostics.snapshot(c,"DIAGNOSTIC_COMMAND");return;}
  if("ARCHIVE".equals(command)){archive(c,intent);return;}
  if(!"TERMINATE".equals(command)||terminating)return;
  Diagnostics.snapshot(c,"DIAGNOSTIC_COMMAND");
  ResultReceiver observer;
  try{observer=intent.getParcelableExtra("reply",ResultReceiver.class);if(observer==null){Journal.event(c,"DIAGNOSTIC_COMMAND","INVALID_REPLY","ResultReceiver missing");return;}}
  catch(RuntimeException e){Journal.event(c,"DIAGNOSTIC_COMMAND","INVALID_REPLY",e.toString());return;}
  terminating=true;PendingResult pending=goAsync();Handler main=new Handler(Looper.getMainLooper());AtomicBoolean done=new AtomicBoolean();
  Runnable abort=()->{if(done.compareAndSet(false,true)){terminating=false;Journal.event(c,"DIAGNOSTIC_COMMAND","TERMINATION_ABORTED","No durable observer acknowledgement within command handshake");pending.finish();}};
  main.postDelayed(abort,7000);
  ResultReceiver acknowledgement=new ResultReceiver(main){@Override protected void onReceiveResult(int code,Bundle data){if(code!=1||done.get())return;
   String last=Journal.event(c,"DIAGNOSTIC_COMMAND","TERMINATION_COMMIT","pid="+android.os.Process.myPid()+" session="+Journal.session+"; observer subscribed to Binder death");
   EventRelay.barrier(c,last,()->{if(done.compareAndSet(false,true)){main.removeCallbacks(abort);pending.finish();android.os.Process.killProcess(android.os.Process.myPid());}},abort);
  }};
  Bundle b=new Bundle();b.putBinder("token",processToken);b.putParcelable("ack",IpcResults.portable(acknowledgement));b.putInt("pid",android.os.Process.myPid());b.putString("session",Journal.session);b.putString("firstComponent",Journal.firstComponent);observer.send(1,b);
 }
 void archive(Context c,Intent intent){
  ResultReceiver reply=null;
  try{
   reply=intent.getParcelableExtra("reply",ResultReceiver.class);if(reply==null)throw new IllegalArgumentException("Update diagnostic app: paged archive reply is required");
   long after=intent.getLongExtra("after",0),through=intent.getLongExtra("through",-1);if(after<0)throw new IllegalArgumentException("Negative archive cursor");if(through<0)through=Journal.lastSequence(c);
   if(through<after)throw new IllegalArgumentException("Invalid archive bound");
   org.json.JSONArray page=Journal.archivePage(c,after,through);long last=page.length()==0?after:page.getJSONObject(page.length()-1).getLong("seq");
   Bundle data=new Bundle();data.putString("installation",Journal.install);data.putString("events",page.toString());data.putLong("after",last);data.putLong("through",through);data.putBoolean("done",page.length()==0||last>=through);reply.send(1,data);
  }catch(Exception e){Journal.event(c,"DIAGNOSTIC_COMMAND","ARCHIVE_ERROR",e.toString());if(reply!=null){Bundle error=new Bundle();error.putString("error",e.toString());reply.send(2,error);}}
 }
}
