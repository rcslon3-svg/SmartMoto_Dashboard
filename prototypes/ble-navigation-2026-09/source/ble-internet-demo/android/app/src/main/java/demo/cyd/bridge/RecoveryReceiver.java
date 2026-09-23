package demo.cyd.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Normal boot is delivered after the first unlock; preferences are then available. */
public class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Startup.start(context, "Перезагрузка телефона");
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Startup.record(context, "Обновление APK: мост и наблюдение не перезапускаются, чтобы сохранить состояние для диагностики");
        }
    }
}
