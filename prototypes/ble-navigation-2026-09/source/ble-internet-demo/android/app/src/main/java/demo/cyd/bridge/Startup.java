package demo.cyd.bridge;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Recovery is opt-in through the existing enabled preference; Stop stays stopped. */
final class Startup {
    static synchronized void record(Context context, String event) {
        var p = context.getSharedPreferences("bridge", 0);
        String stamp = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.ROOT).format(new Date());
        String log = stamp + " · " + event + "\n" + p.getString("startup_log", "");
        // Persist before a receiver returns or the process is terminated.
        p.edit().putString("startup_log", log.substring(0, Math.min(2400, log.length()))).commit();
    }

    static boolean observe(Context context) {
        var p = context.getSharedPreferences("bridge", 0);
        String address = p.getString("address", "");
        if (address.isEmpty()) return false;
        try {
            var manager = context.getSystemService(CompanionDeviceManager.class);
            if (manager.getAssociations().stream().noneMatch(address::equalsIgnoreCase)) {
                throw new IllegalStateException("Нет привязки CDM для выбранной платы; Bluetooth-пара её не заменяет");
            }
            // Android 13 ignores startObserving when the association is already marked active.
            // Explicitly re-arm after process death/update so a stale active flag is insufficient.
            manager.stopObservingDevicePresence(address);
            manager.startObservingDevicePresence(address);
            record(context, "Наблюдение CDM перезапущено; ожидается событие от Android");
            return true;
        } catch (Exception e) {
            record(context, "Не удалось включить наблюдение: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    static void start(Context context, String source) {
        var p = context.getSharedPreferences("bridge", 0);
        record(context, "Событие запуска: " + source);
        if (AutostartTest.active) {
            record(context, "ТЕСТ: запуск моста отложен до окончания проверки исчезновения");
            return;
        }
        if (!p.getBoolean("enabled", false)) {
            record(context, "Пропущено: автозапуск выключен пользователем");
            return;
        }
        if (p.getString("address", "").isEmpty() || p.getString("url", "").isEmpty()) {
            record(context, "Пропущено: не сохранены плата или адрес сервера");
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            record(context, "Пропущено: нет разрешений Bluetooth");
            return;
        }
        try {
            var adapter = context.getSystemService(BluetoothManager.class).getAdapter();
            // Bluetooth may still be turning on at boot. The service handles that retry.
            if (adapter != null && adapter.isEnabled()
                    && adapter.getRemoteDevice(p.getString("address", "")).getBondState() != BluetoothDevice.BOND_BONDED) {
                record(context, "Нет Bluetooth-пары: нужно завершить системное сопряжение");
                p.edit().putString("status", "Сначала создайте Bluetooth-пару и дождитесь статуса «сопряжено»").apply();
                return;
            }
        } catch (SecurityException e) {
            record(context, "Разрешение Bluetooth отозвано");
            return;
        }
        // A failed presence registration must not prevent explicit boot recovery.
        if (!source.startsWith("BLE")) observe(context);
        try {
            context.startForegroundService(new Intent(context, BridgeService.class).putExtra("startup_source", source));
        } catch (RuntimeException e) {
            record(context, "Android отклонил запуск: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            p.edit().putString("status", "Автозапуск отклонён Android; см. журнал ниже").apply();
        }
    }
}
