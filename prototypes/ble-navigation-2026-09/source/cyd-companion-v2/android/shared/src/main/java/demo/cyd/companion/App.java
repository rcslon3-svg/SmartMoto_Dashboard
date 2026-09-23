package demo.cyd.companion;
import android.app.Application;
public class App extends Application {
 @Override public void onCreate(){super.onCreate();CrashCapture.install(this); Journal.init(this); Journal.event(this,"PROCESS","CREATED","Application.onCreate; cause not yet known");CrashCapture.recover(this);}
}
