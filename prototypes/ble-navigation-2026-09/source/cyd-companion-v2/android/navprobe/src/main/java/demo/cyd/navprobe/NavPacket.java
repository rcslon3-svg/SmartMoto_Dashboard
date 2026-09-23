package demo.cyd.navprobe;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The board protocol deliberately fits the default BLE ATT payload (20 bytes). */
final class NavPacket {
    static final String SERVICE = "a4aa19e0-c1ec-4f71-96fe-b5750060cc01";
    static final String WRITE = "a4aa19e0-c1ec-4f71-96fe-b5750060cc02";
    private static final Pattern DISTANCE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(км|km|м|m)(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static List<byte[]> from(CaptureStore.Snapshot shot, int sequence) {
        String turn = shot == null ? null : distance(shot.title);
        String remaining = shot == null ? null : distance(shot.subText);
        if (turn == null || remaining == null) return List.of(new byte[] {'C'});

        Bitmap bitmap = shot.largeIcon != null ? shot.largeIcon : shot.remoteIcon;
        byte[] icon = bitmap == null ? null : monoIcon(bitmap);
        byte[] a = turn.getBytes(StandardCharsets.US_ASCII);
        byte[] b = remaining.getBytes(StandardCharsets.US_ASCII);
        byte seq = (byte) sequence;
        byte[] header = new byte[5 + a.length + b.length];
        header[0] = 'N';
        header[1] = seq;
        header[2] = (byte) (icon == null ? 0 : 1);
        header[3] = (byte) a.length;
        header[4] = (byte) b.length;
        System.arraycopy(a, 0, header, 5, a.length);
        System.arraycopy(b, 0, header, 5 + a.length, b.length);
        List<byte[]> frames = new ArrayList<>();
        frames.add(header);
        if (icon != null) for (int chunk = 0; chunk < 18; chunk++) {
            byte[] frame = new byte[20];
            frame[0] = 'I';
            frame[1] = seq;
            frame[2] = (byte) chunk;
            System.arraycopy(icon, chunk * 16, frame, 4, 16);
            frames.add(frame);
        }
        frames.add(new byte[] {'E', seq});
        return frames;
    }

    private static String distance(String source) {
        if (source == null) return null;
        Matcher matcher = DISTANCE.matcher(source);
        if (!matcher.find()) return null;
        String number = matcher.group(1).replace(',', '.');
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        String result = number + (unit.equals("км") || unit.equals("km") ? "km" : "m");
        return result.length() <= 7 ? result : null;
    }

    private static byte[] monoIcon(Bitmap source) {
        Bitmap scaled = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scaled);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new Rect(0, 0, 48, 48), paint);
        byte[] bits = new byte[288];
        int ink = 0;
        for (int y = 0; y < 48; y++) for (int x = 0; x < 48; x++) {
            int pixel = scaled.getPixel(x, y);
            int alpha = pixel >>> 24;
            int luminance = (((pixel >>> 16) & 255) * 3 +
                    ((pixel >>> 8) & 255) * 6 + (pixel & 255)) / 10;
            if (alpha >= 64 && luminance >= 100) {
                bits[y * 6 + x / 8] |= (byte) (0x80 >>> (x % 8));
                ink++;
            }
        }
        scaled.recycle();
        // Avoid transferring a blank image or an opaque rectangular background.
        return ink >= 10 && ink <= 1800 ? bits : null;
    }

    private NavPacket() { }
}
