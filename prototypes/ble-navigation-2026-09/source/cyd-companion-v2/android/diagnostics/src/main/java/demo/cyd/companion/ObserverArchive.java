package demo.cyd.companion;
import android.content.*;
import android.os.*;
import org.json.*;

/** One acknowledged, size-bounded page at a time; archive end is fixed on the first page. */
final class ObserverArchive {
 static boolean active;
 static void start(Context context){if(active)throw new IllegalStateException("Archive transfer already running");active=true;new Transfer(context.getApplicationContext()).request();}
 static final class Transfer {
  final Context c;final Handler main=new Handler(Looper.getMainLooper());long after,through=-1;int count;String installation;boolean finished;
  final Runnable timeout=()->fail("No archive reply within 10 seconds; bridge may need updating");
  Transfer(Context context){c=context;Journal.event(c,"OBSERVER_ARCHIVE","STARTED","Explicit archive request; may start bridge");}
  void request(){if(finished)return;main.postDelayed(timeout,10000);
   ResultReceiver reply=new ResultReceiver(main){@Override protected void onReceiveResult(int code,Bundle b){if(finished)return;main.removeCallbacks(timeout);
    try{
     if(code!=1)throw new IllegalStateException(b.getString("error","Archive rejected"));
     String origin=b.getString("installation");java.util.UUID.fromString(origin);if(installation!=null&&!installation.equals(origin))throw new IllegalStateException("Archive installation changed");installation=origin;
     long bound=b.getLong("through"),next=b.getLong("after");if(bound<after||(through>=0&&bound!=through))throw new IllegalStateException("Archive bound changed");
     JSONArray page=new JSONArray(b.getString("events"));long cursor=after;
     for(int n=0;n<page.length();n++){JSONObject e=page.getJSONObject(n);long seq=e.getLong("seq");if(seq<=cursor||seq>bound)throw new IllegalStateException("Archive order invalid");cursor=seq;MirrorReceiver.accept(c,installation,e);count++;}
     if(next!=cursor||(!b.getBoolean("done")&&next<=after))throw new IllegalStateException("Archive cursor did not advance");
     after=next;through=bound;
     if(b.getBoolean("done")){finished=true;active=false;Journal.event(c,"OBSERVER_ARCHIVE","COMPLETED","installation="+installation+" through="+through+" records="+count+"; bridge contacted explicitly");}
     else main.postDelayed(Transfer.this::request,100);
    }catch(Exception e){fail(e.toString());}
   }};
   try{c.sendBroadcast(ObserverControl.command("ARCHIVE").putExtra("after",after).putExtra("through",through).putExtra("reply",IpcResults.portable(reply)),EventRelay.PERMISSION);}catch(Exception e){main.removeCallbacks(timeout);fail(e.toString());}
  }
  void fail(String error){if(finished)return;finished=true;active=false;main.removeCallbacks(timeout);Journal.event(c,"OBSERVER_ARCHIVE","FAILED","after="+after+" through="+through+" "+error);}
 }
}
