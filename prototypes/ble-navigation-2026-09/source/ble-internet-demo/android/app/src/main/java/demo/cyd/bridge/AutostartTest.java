package demo.cyd.bridge;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Waits for a real CDM disappearance before ending this process. */
final class AutostartTest {
    static boolean active;
    static Boolean nearby;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Runnable check;
    private static boolean sawPresence;
    private static long deadline;

    static void cancel(Context context) {
        if (!active) return;
        active = false;
        main.removeCallbacks(check);
        Startup.record(context, "ТЕСТ отменён");
    }

    static void begin(Context context, Runnable closeWindow) {
        if (active) return;
        Context app = context.getApplicationContext();
        active = true;
        nearby = null;
        sawPresence = false;
        deadline = SystemClock.elapsedRealtime() + 60000;
        Startup.record(app, "ТЕСТ: подготовка наблюдения; Android " + android.os.Build.VERSION.RELEASE
                + " / API " + android.os.Build.VERSION.SDK_INT + " / " + android.os.Build.MANUFACTURER
                + " " + android.os.Build.MODEL);
        app.getSharedPreferences("bridge", 0).edit()
                .putString("test_phase", "waiting_presence")
                .putString("status", "Тест: включите ESP32 и оставьте включённой. Проверяется получение события CDM (до 60 секунд).").commit();
        app.stopService(new Intent(app, BridgeService.class));
        if (!Startup.observe(app)) {
            fail(app, "Не удалось восстановить наблюдение CDM. Причина записана в журнале.");
            return;
        }
        check = () -> {
            if (!active) return;
            if (!sawPresence && Boolean.TRUE.equals(nearby)) {
                sawPresence = true;
                deadline = SystemClock.elapsedRealtime() + 300000;
                Startup.record(app, "ТЕСТ: событие появления получено. Теперь выключите ESP32");
                app.getSharedPreferences("bridge", 0).edit()
                        .putString("test_phase", "waiting_absence")
                        .putString("status", "Тест: выключите ESP32. После подтверждения отсутствия нажмите «Плата выключена — завершить приложение».").commit();
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                fail(app, sawPresence ? "CDM не сообщил об исчезновении за 5 минут. Тест остановлен; процесс не завершён."
                        : "CDM не сообщил о появлении за 60 секунд. Тест остановлен; процесс не завершён.");
                return;
            }
            if (!sawPresence || BridgeService.running || !Boolean.FALSE.equals(nearby)) {
                var p = app.getSharedPreferences("bridge", 0);
                if ("ready_to_exit".equals(p.getString("test_phase", ""))) {
                    p.edit().putString("test_phase", "waiting_absence")
                            .putString("status", "Тест: плата снова обнаружена. Выключите ESP32 и дождитесь подтверждения отсутствия.").commit();
                }
                main.postDelayed(check, 250);
                return;
            }
            var p = app.getSharedPreferences("bridge", 0);
            if (!"ready_to_exit".equals(p.getString("test_phase", ""))) {
                Startup.record(app, "ТЕСТ: CDM сообщил об отсутствии; ожидается ручное подтверждение выключенного питания");
                p.edit().putString("test_phase", "ready_to_exit")
                        .putString("status", "Тест: CDM сообщил об отсутствии. Убедитесь, что ESP32 выключена, и нажмите «Плата выключена — завершить приложение».").commit();
            }
            main.postDelayed(check, 250);
        };
        main.post(check);
    }

    static boolean confirmExit(Context app, Runnable closeWindow) {
        if (!active || !sawPresence || BridgeService.running || !Boolean.FALSE.equals(nearby)
                || !"ready_to_exit".equals(app.getSharedPreferences("bridge", 0).getString("test_phase", ""))) return false;
        main.removeCallbacks(check);
        Startup.record(app, "ТЕСТ: пользователь подтвердил выключенное питание; завершение PID=" + android.os.Process.myPid());
        app.getSharedPreferences("bridge", 0).edit()
                    .putInt("test_exit_pid", android.os.Process.myPid())
                    .putString("test_phase", "awaiting_return")
                    .putString("status", "Тест: процесс завершён. Включите ESP32.").commit();
            closeWindow.run();
            android.os.Process.killProcess(android.os.Process.myPid());
        return true;
    }

    private static void fail(Context context, String reason) {
        active = false;
        Startup.record(context, "ТЕСТ: " + reason);
        context.getSharedPreferences("bridge", 0).edit().putString("test_phase", "failed")
                .putString("status", reason).commit();
    }
}
