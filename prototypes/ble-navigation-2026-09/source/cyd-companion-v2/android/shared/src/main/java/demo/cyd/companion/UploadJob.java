package demo.cyd.companion;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
public class UploadJob extends JobService {
 static final int ID=42;
 static synchronized void schedule(Context c){if(Config.endpoint(c).isEmpty())return;
  try{JobScheduler j=c.getSystemService(JobScheduler.class);if(j.getPendingJob(ID)==null)j.schedule(new JobInfo.Builder(ID,new ComponentName(c,UploadJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(2000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
  catch(RuntimeException e){Config.get(c).edit().putString("upload","JobScheduler: "+e).apply();}
 }
 final ExecutorService executor=Executors.newSingleThreadExecutor();volatile int generation;
 @Override public boolean onStartJob(JobParameters p){int token=++generation;
  if(Journal.firstComponent.isEmpty())Journal.event(this,"UPLOAD_JOB","ENTRY","System upload job; not a BLE wake");
  // Do not log every upload: logging schedules another upload and would self-wake forever.
  executor.execute(()->{boolean retry=EventUpload.drain(this);if(token==generation)jobFinished(p,retry);});return true;
 }
 @Override public boolean onStopJob(JobParameters p){generation++;return true;}
 @Override public void onDestroy(){generation++;executor.shutdownNow();super.onDestroy();}
}
