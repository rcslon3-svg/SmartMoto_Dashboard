package demo.cyd.companion;
import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Keep an uncaught Java exception for the next start without touching a possibly locked DB. */
final class CrashCapture {
 static void install(Context c){
  Thread.UncaughtExceptionHandler prior=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{String stack=Log.getStackTraceString(error);JSONObject data=new JSONObject().put("utc",java.time.Instant.now().toString()).put("pid",android.os.Process.myPid()).put("thread",thread.getName()).put("stack",stack.substring(0,Math.min(stack.length(),12000)));
    try(var out=c.openFileOutput("pending-java-crash.json",Context.MODE_PRIVATE)){out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
   }catch(Throwable ignored){}
   if(prior!=null)prior.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
  });
 }
 static void recover(Context c){if(!c.getFileStreamPath("pending-java-crash.json").exists())return;
  try(var in=c.openFileInput("pending-java-crash.json")){String saved=new String(in.readNBytes(65536),StandardCharsets.UTF_8);Journal.event(c,"JAVA_CRASH","PREVIOUS_PROCESS",saved);c.deleteFile("pending-java-crash.json");}catch(Exception e){Journal.event(c,"JAVA_CRASH","RECOVERY_ERROR",e.toString());}
 }
}
