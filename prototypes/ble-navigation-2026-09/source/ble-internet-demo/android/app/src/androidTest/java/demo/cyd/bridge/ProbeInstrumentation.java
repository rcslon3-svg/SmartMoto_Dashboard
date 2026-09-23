package demo.cyd.bridge;

import android.app.Instrumentation;
import android.companion.CompanionDeviceManager;
import android.os.Bundle;

/** Emulator-only fixture. Ships in the test APK, never in the user's APK. */
public class ProbeInstrumentation extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { args = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            var context = getTargetContext();
            var preferences = context.getSharedPreferences("bridge", 0);
            String address = "68:09:47:58:FA:E6";
            preferences.edit().putString("address", address)
                    .putString("url", "http://10.0.2.2:8787")
                    .putBoolean("enabled", "true".equals(args.getString("enabled")))
                    .putString("startup_log", "").commit();
            if ("true".equals(args.getString("fixedtest"))) {
                runOnMainSync(() -> AutostartTest.begin(context, () -> {}));
                for (int n=0; n<140; n++) {
                    Thread.sleep(500);
                    if ("ready_to_exit".equals(preferences.getString("test_phase", ""))) {
                        Thread.sleep(1500);
                        if (!AutostartTest.active) throw new IllegalStateException("Exited before confirmation");
                        runOnMainSync(() -> {
                            Startup.record(context, "PROBE: still alive after absence; now pressing explicit confirmation");
                            if (!AutostartTest.confirmExit(context, () -> {})) throw new IllegalStateException("Confirmation rejected");
                        });
                    }
                }
                throw new IllegalStateException("Test did not terminate after disappearance");
            }
            context.getSystemService(CompanionDeviceManager.class).startObservingDevicePresence(address);
            result.putString("stream", "Fixture ready: observing saved association; enabled="
                    + preferences.getBoolean("enabled", false) + "\n");
            if ("true".equals(args.getString("selfkill"))) {
                preferences.edit().putBoolean("probe_ready", true).commit();
                Thread.sleep(12000);
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
            finish(0, result);
        } catch (Exception e) {
            result.putString("stream", e.toString());
            finish(1, result);
        }
    }
}
