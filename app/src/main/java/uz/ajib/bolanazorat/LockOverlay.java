package uz.ajib.bolanazorat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * Qop-qora to'liq ekran oynasi — telefon o'chgandek ko'rinadi.
 * Boshqa ilovalar ustida turadi va barcha teginishlarni yutadi.
 * Yashirin chiqish: chap yuqori burchakka 4 soniya ichida 8 marta tez tegilsa — qulf ochiladi.
 */
public final class LockOverlay {
    private static View view;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private LockOverlay() {}

    public static boolean canDraw(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    /** Qora ekranni ko'rsatadi (agar allaqachon turgan bo'lsa — hech narsa qilmaydi). */
    public static void ensure(Context c) {
        Context app = c.getApplicationContext();
        H.post(() -> add(app));
    }

    public static void remove() {
        H.post(LockOverlay::rm);
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void add(Context c) {
        if (view != null) return;
        if (!canDraw(c)) return;
        WindowManager wm = c.getSystemService(WindowManager.class);
        if (wm == null) return;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.OPAQUE);
        p.gravity = Gravity.TOP | Gravity.START;
        p.screenBrightness = 0.01f; // ekran yorug'ligini eng pastga — o'chgandek
        if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        final View v = new View(c);
        v.setBackgroundColor(Color.BLACK);
        v.setKeepScreenOn(true);

        final long[] taps = new long[8];
        final int[] idx = {0};
        v.setOnTouchListener((vv, e) -> {
            if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
            float x = e.getRawX(), y = e.getRawY();
            int w = vv.getWidth() > 0 ? vv.getWidth() : 1080;
            int h = vv.getHeight() > 0 ? vv.getHeight() : 2000;
            if (x < w * 0.18f && y < h * 0.14f) { // faqat chap yuqori burchak
                long now = System.currentTimeMillis();
                taps[idx[0] % taps.length] = now;
                idx[0]++;
                if (idx[0] >= taps.length) {
                    long oldest = taps[idx[0] % taps.length];
                    if (now - oldest <= 4000) {
                        // yashirin chiqish — qulfni ochadi
                        Prefs.setLocked(c, false);
                        remove();
                    }
                }
            }
            return true; // barcha teginishlarni yutadi
        });

        try {
            wm.addView(v, p);
            view = v;
        } catch (Exception ignored) {}
    }

    private static void rm() {
        if (view == null) return;
        try {
            View v = view;
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (Exception ignored) {}
        view = null;
    }
}
