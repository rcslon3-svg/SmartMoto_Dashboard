package demo.cyd.companion;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
public class RecoveryReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){String action=i.getAction();Journal.event(c,"SYSTEM_BROADCAST","RECEIVED",action);
  Diagnostics.once(c,"SYSTEM_BROADCAST");
  if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)&&i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)==BluetoothAdapter.STATE_ON))Monitoring.arm(c,"RECOVERY");
 }
}
