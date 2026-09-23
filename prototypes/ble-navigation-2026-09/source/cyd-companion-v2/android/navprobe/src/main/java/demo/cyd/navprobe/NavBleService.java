package demo.cyd.navprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Explicitly started by the user; keeps the BLE link while Maps is foreground. */
public final class NavBleService extends Service {
    static final String ACTION_STOP = "demo.cyd.navprobe.STOP_BLE";
    private static final String CHANNEL = "nav_ble_link";
    private static volatile NavBleService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<byte[]> frames = new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writer;
    private CaptureStore.Snapshot latest;
    private boolean scanning, writing, dirty, stopping;
    private int sequence;

    static void offer(CaptureStore.Snapshot shot) {
        NavBleService service = instance;
        if (service != null) service.main.post(() -> {
            if (service == instance) {
                service.latest = shot;
                service.dirty = true;
                service.enqueueLatest();
            }
        });
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Передача навигации по BLE", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Навигация на круглом дисплее")
                .setContentText("Идёт поиск или передача по BLE")
                .setContentIntent(open).setOngoing(true).build();
        startForeground(20, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        CaptureStore.bleStatus = "Поиск MotoNav-Round";
        CaptureStore.changed();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        main.post(this::startScan);
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        stopping = true;
        stopScan();
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); }
            catch (SecurityException ignored) { }
            gatt = null;
        }
        writer = null;
        instance = null;
        CaptureStore.bleConnected = false;
        CaptureStore.bleStatus = "BLE выключен";
        CaptureStore.changed();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void startScan() {
        if (stopping || scanning || gatt != null) return;
        try {
            BluetoothManager manager = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) { status("Включите Bluetooth"); return; }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { status("BLE-сканер недоступен"); return; }
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString(NavPacket.SERVICE)).build();
            scanner.startScan(List.of(filter), new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            scanning = true;
            status("Поиск MotoNav-Round");
            main.postDelayed(() -> {
                if (scanning) {
                    stopScan();
                    status("Плата не найдена; повтор поиска");
                    main.postDelayed(this::startScan, 3000);
                }
            }, 12000);
        } catch (SecurityException error) {
            status("Нет разрешения Bluetooth");
        }
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try { if (scanner != null) scanner.stopScan(scanCallback); }
        catch (SecurityException ignored) { }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            main.post(() -> {
                if (!scanning || stopping) return;
                stopScan();
                try {
                    BluetoothDevice device = result.getDevice();
                    status("Подключение к " + device.getAddress());
                    gatt = device.connectGatt(NavBleService.this, false, gattCallback,
                            BluetoothDevice.TRANSPORT_LE);
                } catch (SecurityException error) { status("Нет разрешения Bluetooth"); }
            });
        }
        @Override public void onScanFailed(int code) {
            main.post(() -> {
                stopScan();
                status("Ошибка BLE-сканирования: " + code);
                if (!stopping) main.postDelayed(NavBleService.this::startScan, 3000);
            });
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection, int status,
                                                       int newState) {
            main.post(() -> {
                if (stopping || connection != gatt) return;
                if (status == BluetoothGatt.GATT_SUCCESS &&
                        newState == BluetoothProfile.STATE_CONNECTED) {
                    NavBleService.this.status("Поиск BLE-сервиса");
                    try { connection.discoverServices(); }
                    catch (SecurityException error) { failed("Нет разрешения Bluetooth"); }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED ||
                        status != BluetoothGatt.GATT_SUCCESS) failed("BLE отключён: " + status);
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt connection, int status) {
            main.post(() -> {
                if (stopping || connection != gatt) return;
                BluetoothGattService service = status == BluetoothGatt.GATT_SUCCESS ?
                        connection.getService(UUID.fromString(NavPacket.SERVICE)) : null;
                writer = service == null ? null :
                        service.getCharacteristic(UUID.fromString(NavPacket.WRITE));
                if (writer == null) { failed("Сервис NavBLE не найден"); return; }
                CaptureStore.bleConnected = true;
                NavBleService.this.status("Плата подключена");
                latest = CaptureStore.current;
                dirty = true;
                enqueueLatest();
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt connection,
                                                     BluetoothGattCharacteristic characteristic,
                                                     int status) {
            main.post(() -> {
                if (connection != gatt || !writing) return;
                writing = false;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failed("Ошибка записи BLE: " + status);
                    return;
                }
                byte[] accepted = frames.poll();
                if (accepted != null && accepted[0] == 'E') {
                    CaptureStore.bleTransfers++;
                    NavBleService.this.status("Данные записаны по GATT");
                }
                writeNext();
            });
        }
    };

    private void enqueueLatest() {
        if (!dirty || writer == null || writing || !frames.isEmpty()) return;
        dirty = false;
        frames.addAll(NavPacket.from(latest, ++sequence));
        writeNext();
    }

    private void writeNext() {
        if (writer == null || gatt == null || writing) return;
        byte[] frame = frames.peek();
        if (frame == null) { enqueueLatest(); return; }
        try {
            int result = gatt.writeCharacteristic(writer, frame,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (result == BluetoothStatusCodes.SUCCESS) writing = true;
            else failed("Запись BLE не началась: " + result);
        } catch (SecurityException error) { failed("Нет разрешения Bluetooth"); }
    }

    private void failed(String message) {
        status(message);
        CaptureStore.bleConnected = false;
        writer = null;
        frames.clear();
        writing = false;
        if (gatt != null) {
            try { gatt.close(); }
            catch (SecurityException ignored) { }
            gatt = null;
        }
        if (!stopping) main.postDelayed(this::startScan, 3000);
    }

    private void status(String message) {
        CaptureStore.bleStatus = message;
        CaptureStore.changed();
    }
}
