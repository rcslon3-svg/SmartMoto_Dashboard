package demo.cyd.companion;
import android.content.*;
public class ObserverRecovery extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){Journal.event(c,"OBSERVER_SYSTEM","RECEIVED",i.getAction());UploadJob.schedule(c);}
}
