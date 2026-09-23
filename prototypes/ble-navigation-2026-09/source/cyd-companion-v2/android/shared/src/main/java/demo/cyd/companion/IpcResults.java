package demo.cyd.companion;
import android.os.Parcel;
import android.os.ResultReceiver;
/** Flatten an app-private receiver subclass into the framework's portable Binder wrapper. */
final class IpcResults {
 static ResultReceiver portable(ResultReceiver receiver){
  Parcel p=Parcel.obtain();
  try{receiver.writeToParcel(p,0);p.setDataPosition(0);return ResultReceiver.CREATOR.createFromParcel(p);}
  finally{p.recycle();}
 }
 private IpcResults(){}
}
