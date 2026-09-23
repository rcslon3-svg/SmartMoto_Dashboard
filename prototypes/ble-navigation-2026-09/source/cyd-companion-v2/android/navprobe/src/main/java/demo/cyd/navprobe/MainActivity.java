package demo.cyd.navprobe;

import android.app.Activity;
import android.app.NotificationManager;
import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity implements CaptureStore.Observer {
    private LinearLayout body;
    private TextView state;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body);
        setContentView(scroll);

        heading("Данные уведомления Google Maps");
        label("Экран показывает только данные, полученные Android от уведомлений приложения Google Maps. Поля не являются официальным интерфейсом навигации.");
        Button access = new Button(this);
        access.setText("Открыть доступ к уведомлениям");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        body.addView(access);
        Button refresh = new Button(this);
        refresh.setText("Прочитать активные уведомления");
        refresh.setOnClickListener(v -> MapsListener.refreshIfConnected());
        body.addView(refresh);
        Button connect = new Button(this);
        connect.setText("Подключить круглый дисплей по BLE");
        connect.setOnClickListener(v -> connectBle());
        body.addView(connect);
        Button disconnect = new Button(this);
        disconnect.setText("Отключить BLE");
        disconnect.setOnClickListener(v -> stopService(new Intent(this, NavBleService.class)));
        body.addView(disconnect);
        state = label("");
    }

    @Override protected void onResume() {
        super.onResume();
        CaptureStore.add(this);
        MapsListener.refreshIfConnected();
        changed();
    }

    @Override protected void onPause() {
        CaptureStore.remove(this);
        super.onPause();
    }

    @Override public void changed() {
        runOnUiThread(this::render);
    }

    private void render() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean granted = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, MapsListener.class));
        state.setText("Доступ: " + (granted ? "разрешён" : "не разрешён") +
                "\nСлужба: " + (CaptureStore.connected ? "подключена" : "не подключена") +
                "\nСостояние: " + CaptureStore.status +
                "\nКруглый дисплей: " + CaptureStore.bleStatus +
                "\nЗавершено записей по GATT: " + CaptureStore.bleTransfers +
                "\nОбработано снимков: " + CaptureStore.updates);
        while (body.getChildCount() > 7) body.removeViewAt(7);
        CaptureStore.Snapshot shot = CaptureStore.current;
        if (shot == null) {
            label("После выдачи доступа запустите маршрут в Google Maps, затем вернитесь сюда. Если уведомление уже активно, нажмите «Прочитать активные уведомления».");
            return;
        }
        heading("Полученное уведомление");
        field("Время получения", shot.eventTime);
        field("ID / канал", shot.id + " / " + shot.channel);
        field("Постоянное уведомление", shot.ongoing ? "да" : "нет");
        heading("Android Notification.extras");
        field("TITLE", shot.title);
        field("TEXT", shot.text);
        field("SUB_TEXT", shot.subText);
        field("BIG_TEXT", shot.bigText);
        field("SUMMARY_TEXT", shot.summary);
        field("TEXT_LINES", shot.textLines);
        heading("Все ключи Notification.extras");
        field("Содержимое", shot.extraDump);
        heading("Изображение largeIcon");
        if (shot.largeIcon != null) picture(shot.largeIcon);
        else label("Не получено");
        heading("Текст из RemoteViews");
        field("Результат", shot.remoteText);
        field("Состояние чтения", shot.remoteStatus);
        heading("Изображение из RemoteViews");
        if (shot.remoteIcon != null) {
            field("Имя элемента", shot.remoteIconName);
            picture(shot.remoteIcon);
        } else label("Не получено");
        label("Изображения показаны на тёмной подложке без изменения их пикселей. Найденный значок не обязательно обозначает следующий манёвр.");
    }

    private void field(String title, String value) {
        label(title + ": " + (value == null || value.isEmpty() ? "—" : value));
    }

    private TextView heading(String text) {
        TextView view = label(text);
        view.setTextSize(22);
        view.setPadding(0, dp(12), 0, dp(4));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(16);
        view.setTextIsSelectable(true);
        view.setPadding(0, dp(4), 0, dp(4));
        body.addView(view);
        return view;
    }

    private void picture(android.graphics.Bitmap bitmap) {
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(35, 43, 52));
        background.setCornerRadius(dp(8));
        image.setBackground(background);
        body.addView(image, new LinearLayout.LayoutParams(dp(160), dp(160)));
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void connectBle() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT}, 10);
            return;
        }
        startForegroundService(new Intent(this, NavBleService.class));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                       int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 10) return;
        boolean granted = grantResults.length == 2;
        for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
        if (granted) connectBle();
        else { CaptureStore.bleStatus = "Нужны разрешения Bluetooth"; changed(); }
    }
}
