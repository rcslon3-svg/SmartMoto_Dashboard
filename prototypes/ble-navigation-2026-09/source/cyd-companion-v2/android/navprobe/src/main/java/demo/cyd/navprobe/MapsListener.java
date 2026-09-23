package demo.cyd.navprobe;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

public final class MapsListener extends NotificationListenerService {
    private static MapsListener instance;

    static void refreshIfConnected() { if (instance != null) instance.refreshActive(); }

    @Override public void onListenerConnected() {
        instance = this;
        CaptureStore.connected = true;
        refreshActive();
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        CaptureStore.connected = false;
        CaptureStore.current = null;
        NavBleService.offer(null);
        CaptureStore.status = "Служба отключена системой";
        CaptureStore.changed();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && CaptureStore.MAPS.equals(sbn.getPackageName())) refreshActive();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !CaptureStore.MAPS.equals(sbn.getPackageName())) return;
        if (CaptureStore.current != null && sbn.getKey().equals(CaptureStore.current.key)) {
            refreshActive();
        }
    }

    private void refreshActive() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            StatusBarNotification best = null;
            if (active != null) for (StatusBarNotification item : active) {
                if (!CaptureStore.MAPS.equals(item.getPackageName())) continue;
                if (best == null || (item.isOngoing() && !best.isOngoing()) ||
                    (item.isOngoing() == best.isOngoing() && item.getPostTime() > best.getPostTime())) best = item;
            }
            if (best != null) capture(best);
            else {
                CaptureStore.current = null;
                NavBleService.offer(null);
                CaptureStore.status = "Активных уведомлений Google Maps нет";
                CaptureStore.changed();
            }
        } catch (RuntimeException error) {
            CaptureStore.status = "Ошибка чтения активных уведомлений: " + error.getClass().getSimpleName();
            CaptureStore.changed();
        }
    }

    private void capture(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        CaptureStore.Snapshot shot = new CaptureStore.Snapshot();
        shot.key = sbn.getKey();
        shot.id = sbn.getId();
        shot.channel = notification.getChannelId();
        shot.ongoing = sbn.isOngoing();
        shot.eventTime = DateFormat.getDateTimeInstance().format(new Date(System.currentTimeMillis()));
        Bundle extras = notification.extras;
        if (extras != null) {
            shot.title = value(extras, Notification.EXTRA_TITLE);
            shot.text = value(extras, Notification.EXTRA_TEXT);
            shot.subText = value(extras, Notification.EXTRA_SUB_TEXT);
            shot.bigText = value(extras, Notification.EXTRA_BIG_TEXT);
            shot.summary = value(extras, Notification.EXTRA_SUMMARY_TEXT);
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) shot.textLines = TextUtils.join(" | ", lines);
            shot.extraDump = dumpExtras(extras);
        }
        try {
            if (notification.getLargeIcon() != null)
                shot.largeIcon = bitmap(notification.getLargeIcon().loadDrawable(this));
        } catch (RuntimeException ignored) { }
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Context mapsContext = this;
        try { mapsContext = createPackageContext(CaptureStore.MAPS, 0); }
        catch (PackageManager.NameNotFoundException error) {
            errors.append("Контекст Google Maps недоступен\n");
        }
        inspect("contentView", notification.contentView, mapsContext, output, errors, shot);
        inspect("bigContentView", notification.bigContentView, mapsContext, output, errors, shot);
        if (notification.contentView == null && notification.bigContentView == null) {
            try {
                Notification.Builder builder = Notification.Builder.recoverBuilder(mapsContext, notification);
                inspect("generated contentView", builder.createContentView(), mapsContext, output, errors, shot);
                inspect("generated bigContentView", builder.createBigContentView(), mapsContext, output, errors, shot);
            } catch (RuntimeException error) {
                errors.append("recoverBuilder: ").append(error.getClass().getSimpleName()).append('\n');
            }
        }
        shot.remoteText = output.toString();
        shot.remoteStatus = errors.length() == 0 ? "Обработка RemoteViews завершена" : errors.toString();
        CaptureStore.current = shot;
        NavBleService.offer(shot);
        CaptureStore.updates++;
        CaptureStore.status = "Получено уведомление Google Maps";
        CaptureStore.changed();
    }

    private static String value(Bundle extras, String key) {
        CharSequence text = extras.getCharSequence(key);
        return text == null ? null : text.toString();
    }

    private static String dumpExtras(Bundle extras) {
        StringBuilder output = new StringBuilder();
        ArrayList<String> keys = new ArrayList<>(extras.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            try {
                Object item = extras.get(key);
                output.append(key).append(" = ");
                if (item instanceof CharSequence || item instanceof Number || item instanceof Boolean)
                    output.append(item);
                else if (item instanceof CharSequence[])
                    output.append(TextUtils.join(" | ", (CharSequence[]) item));
                else if (item instanceof Bitmap)
                    output.append("Bitmap ").append(((Bitmap) item).getWidth()).append('x').append(((Bitmap) item).getHeight());
                else output.append(item == null ? "null" : "[" + item.getClass().getSimpleName() + "]");
                output.append('\n');
            } catch (RuntimeException error) {
                output.append(key).append(" = [ошибка: ").append(error.getClass().getSimpleName()).append("]\n");
            }
        }
        return output.toString();
    }

    private void inspect(String label, RemoteViews remote, Context mapsContext, StringBuilder output,
                         StringBuilder errors, CaptureStore.Snapshot shot) {
        if (remote == null) return;
        try {
            View root = remote.apply(mapsContext, null);
            output.append(label).append(":\n");
            walk(root, output, shot, 0);
        } catch (RuntimeException error) {
            errors.append(label).append(": ").append(error.getClass().getSimpleName()).append('\n');
        }
    }

    private void walk(View view, StringBuilder output, CaptureStore.Snapshot shot, int depth) {
        if (depth > 12 || output.length() > 6000) return;
        String name = resourceName(view);
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.length() > 0)
                output.append(name).append(" = ").append(value).append('\n');
        }
        if (view instanceof ImageView && shot.remoteIcon == null &&
                (name.contains("right_icon") || name.contains("large_icon"))) {
            shot.remoteIcon = bitmap(((ImageView) view).getDrawable());
            if (shot.remoteIcon != null) shot.remoteIconName = name;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && i < 100; i++)
                walk(group.getChildAt(i), output, shot, depth + 1);
        }
    }

    private String resourceName(View view) {
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return "view#" + view.getId(); }
    }

    private static Bitmap bitmap(Drawable drawable) {
        if (drawable == null) return null;
        int width = Math.min(256, Math.max(1, drawable.getIntrinsicWidth()));
        int height = Math.min(256, Math.max(1, drawable.getIntrinsicHeight()));
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return result;
    }
}
