package demo.cyd.navprobe;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

final class CaptureStore {
    interface Observer { void changed(); }
    static final String MAPS = "com.google.android.apps.maps";
    static boolean connected;
    static String status = "Служба ещё не подключалась";
    static Snapshot current;
    static int updates;
    static boolean bleConnected;
    static String bleStatus = "BLE выключен";
    static int bleTransfers;
    private static final List<Observer> observers = new ArrayList<>();

    static final class Snapshot {
        String key, eventTime, title, text, subText, bigText, summary, textLines;
        String remoteText, remoteStatus, channel, extraDump;
        int id;
        boolean ongoing;
        Bitmap largeIcon, remoteIcon;
        String remoteIconName;
    }

    static void add(Observer observer) { if (!observers.contains(observer)) observers.add(observer); }
    static void remove(Observer observer) { observers.remove(observer); }
    static void changed() { for (Observer observer : new ArrayList<>(observers)) observer.changed(); }
    private CaptureStore() { }
}
