package demo.cyd.bridge;

import android.Manifest;
import android.app.ActivityManager;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** User-triggered scan of the selected address. Does not bind, connect, or re-register CDM. */
final class Diagnostics {
    static boolean running;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final JSONObject report = new JSONObject();
    private BluetoothLeScanner scanner;
    private int packets;
    private boolean finished;
    private final Runnable timeout = () -> finish("completed");
    private String address;

    private Diagnostics(Context context) { this.context = context.getApplicationContext(); }
    static void start(Context context) {
        if (running) return;
        running = true;
        new Diagnostics(context).collect();
    }
    private void put(String key, Object value) { try { report.put(key, value); } catch (JSONException ignored) {} }
    private void status(String text) {
        context.getSharedPreferences("bridge",0).edit().putString("diagnostic_status", text).apply();
    }
    private boolean granted(String permission) { return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private void collect() {
        var p = context.getSharedPreferences("bridge",0);
        address = p.getString("address", "");
        put("schema",1); put("createdAt", System.currentTimeMillis());
        put("device", Build.MANUFACTURER + " " + Build.MODEL);
        put("android", Build.VERSION.RELEASE); put("api",Build.VERSION.SDK_INT);
        put("bootCount",Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1));
        put("uptimeMs",SystemClock.elapsedRealtime()); put("address",address);
        put("enabledInApp",p.getBoolean("enabled",false));
        put("startupLogBeforeScan",p.getString("startup_log",""));
        put("bridgeServiceRunning",BridgeService.running);
        put("backgroundRestricted",context.getSystemService(ActivityManager.class).isBackgroundRestricted());
        put("scanPermission",granted(Manifest.permission.BLUETOOTH_SCAN));
        put("connectPermission",granted(Manifest.permission.BLUETOOTH_CONNECT));
        put("scanKind","Separate foreground application scan; NOT CDM internal scanner results");
        put("limitation","No packets does not prove board absent. Connected boards may not advertise. CDM internal scanning and Xiaomi autostart permission are not accessible through this diagnostic.");
        try {
            var cdm = context.getSystemService(CompanionDeviceManager.class);
            var associations = cdm.getAssociations();
            put("cdmAssociatedAddresses",new JSONArray(associations));
            put("selectedAddressAssociated",associations.stream().anyMatch(address::equalsIgnoreCase));
            if (Build.VERSION.SDK_INT>=33) {
                JSONArray details = new JSONArray();
                for (var a : cdm.getMyAssociations()) {
                    details.put(new JSONObject().put("id",a.getId()).put("address",String.valueOf(a.getDeviceMacAddress())));
                }
                put("cdmAssociations",details);
            }
        } catch (Exception e) { put("cdmReadError",e.toString()); }
        try {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    || context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                finish("Bluetooth permission missing"); return;
            }
            var manager = context.getSystemService(BluetoothManager.class);
            var adapter = manager.getAdapter();
            if (adapter == null) { finish("Bluetooth adapter unavailable"); return; }
            put("bluetoothState",adapter.getState());
            if (!BluetoothAdapter.checkBluetoothAddress(address)) { finish("No valid selected address"); return; }
            put("bondState",adapter.getRemoteDevice(address).getBondState());
            put("selectedGattConnected",manager.getConnectedDevices(BluetoothProfile.GATT).stream()
                    .anyMatch(d -> address.equalsIgnoreCase(d.getAddress())));
            if (!adapter.isEnabled()) { finish("Bluetooth disabled"); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { finish("BLE scanner unavailable"); return; }
            status("Диагностика: отдельный BLE-скан выбранной платы, 15 секунд…");
            scanner.startScan(List.of(new ScanFilter.Builder().setDeviceAddress(address).build()),
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);
            main.postDelayed(timeout,15000);
        } catch (SecurityException e) { finish("Permission revoked: " + e); }
        catch (Exception e) { finish(e.toString()); }
    }
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) { main.post(() -> record(result)); }
        @Override public void onBatchScanResults(List<ScanResult> results) { main.post(() -> { for (var r : results) record(r); }); }
        @Override public void onScanFailed(int code) { main.post(() -> finish("scanFailed="+code)); }
    };
    private void record(ScanResult result) {
        if (finished) return;
        packets++;
        put("lastRssi",result.getRssi()); put("lastScanTimestampNanos",result.getTimestampNanos());
        var record = result.getScanRecord();
        if (record != null) {
            put("advertisedName",record.getDeviceName());
            put("serviceUuids",String.valueOf(record.getServiceUuids()));
        }
    }
    private void finish(String outcome) {
        if (finished) return;
        finished = true;
        main.removeCallbacks(timeout);
        try { if (scanner != null && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) scanner.stopScan(callback); }
        catch (SecurityException e) { put("scanStopError",e.toString()); }
        catch (Exception e) { put("scanStopError",e.toString()); }
        put("scanOutcome",outcome); put("packetCount",packets);
        put("startupLogAfterScan",context.getSharedPreferences("bridge",0).getString("startup_log",""));
        final String json = report.toString();
        context.getSharedPreferences("bridge",0).edit().putString("diagnostic_report",json).commit();
        status("Диагностика: пакетов платы " + packets + ". Передаю отчёт компьютеру…");
        new Thread(() -> {
            String result;
            HttpURLConnection connection = null;
            try {
                String endpoint = context.getSharedPreferences("bridge",0).getString("url","");
                connection = (HttpURLConnection)new URL(endpoint+"/api/diagnostics").openConnection();
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000); connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                try (var out = connection.getOutputStream()) { out.write(json.getBytes(StandardCharsets.UTF_8)); }
                if (connection.getResponseCode()!=200) throw new Exception("HTTP "+connection.getResponseCode());
                result = "Диагностика передана компьютеру. Пакетов платы: " + packets + ". Результат сканирования: " + outcome;
            } catch (Exception e) {
                result = "Отчёт сохранён в приложении, отправка не удалась: " + e.getMessage();
            } finally { if (connection != null) connection.disconnect(); }
            String message = result;
            main.post(() -> { running=false; status(message); });
        },"cyd-diagnostics-upload").start();
    }
}
