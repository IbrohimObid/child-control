package uz.ajib.bolanazorat;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

/** Barcha sozlamalar shu yerda saqlanadi. */
public final class Prefs {
    private static final String FILE = "nazorat";

    /** YouTube va uning nusxalari. */
    public static final String[] YOUTUBE_PACKAGES = {
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.google.android.youtube.tv",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.vanced.android.youtube",
            "org.schabi.newpipe",
            "free.rm.skytube.oss",
            "com.github.libretube",
            "com.kapp.youtube.final"
    };

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String token(Context c) { return sp(c).getString("token", ""); }
    public static String pin(Context c) { return sp(c).getString("pin", ""); }
    public static boolean isConfigured(Context c) {
        return !token(c).isEmpty() && !pin(c).isEmpty();
    }
    public static void saveSetup(Context c, String token, String pin) {
        String code = token.isEmpty() ? "" : genCode();
        sp(c).edit().putString("token", token).putString("pin", pin)
                .putString("code", code).putLong("offset", 0).apply();
    }

    /** Boshqa qurilmalarni botga ulash uchun kod (PINdan alohida). */
    public static String connectCode(Context c) { return sp(c).getString("code", ""); }
    public static void newConnectCode(Context c) { sp(c).edit().putString("code", genCode()).apply(); }
    private static String genCode() {
        return String.valueOf(100000 + new java.util.Random().nextInt(900000));
    }

    /** Soxta o'chirish (qora ekran) rejimi. */
    public static boolean locked(Context c) { return sp(c).getBoolean("locked", false); }
    public static void setLocked(Context c, boolean v) { sp(c).edit().putBoolean("locked", v).apply(); }

    /** Barcha o'yinlarni bloklash. */
    public static boolean blockGames(Context c) { return sp(c).getBoolean("games", false); }
    public static void setBlockGames(Context c, boolean v) { sp(c).edit().putBoolean("games", v).apply(); }

    public static String botName(Context c) { return sp(c).getString("bot_name", ""); }
    public static void setBotName(Context c, String v) { sp(c).edit().putString("bot_name", v).apply(); }

    public static Set<String> owners(Context c) {
        return new HashSet<>(sp(c).getStringSet("owners", new HashSet<>()));
    }
    public static void addOwner(Context c, long chatId) {
        Set<String> s = owners(c);
        s.add(String.valueOf(chatId));
        sp(c).edit().putStringSet("owners", s).apply();
    }
    public static void clearOwners(Context c) {
        sp(c).edit().remove("owners").apply();
    }

    public static long offset(Context c) { return sp(c).getLong("offset", 0); }
    public static void setOffset(Context c, long v) { sp(c).edit().putLong("offset", v).apply(); }

    /** YouTube bloklanganmi (standart: ha). */
    public static boolean ytBlocked(Context c) { return sp(c).getBoolean("yt_blocked", true); }
    public static void setYtBlocked(Context c, boolean v) {
        sp(c).edit().putBoolean("yt_blocked", v).putLong("allow_until", 0).apply();
    }

    /** Vaqtinchalik ruxsat tugash vaqti (ms). 0 = vaqtinchalik ruxsat yo'q. */
    public static long allowUntil(Context c) { return sp(c).getLong("allow_until", 0); }
    public static void allowFor(Context c, int minutes) {
        sp(c).edit().putBoolean("yt_blocked", true)
                .putLong("allow_until", System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    /** Hozir YouTube yopilishi kerakmi. */
    public static boolean ytBlockedNow(Context c) {
        return ytBlocked(c) && System.currentTimeMillis() >= allowUntil(c);
    }

    public static Set<String> extraBlocked(Context c) {
        return new HashSet<>(sp(c).getStringSet("extra", new HashSet<>()));
    }
    public static void setExtraBlocked(Context c, Set<String> s) {
        sp(c).edit().putStringSet("extra", s).apply();
    }

    /** Sozlamalarda ilovani o'chirishdan himoya. */
    public static boolean protect(Context c) { return sp(c).getBoolean("protect", true); }
    public static void setProtect(Context c, boolean v) { sp(c).edit().putBoolean("protect", v).apply(); }

    /** PIN kiritilgandan keyin 10 daqiqa sozlash uchun himoya to'xtaydi. */
    public static void unlockForSetup(Context c) {
        sp(c).edit().putLong("unlock_until", System.currentTimeMillis() + 10 * 60_000L).apply();
    }
    public static void lockNow(Context c) { sp(c).edit().putLong("unlock_until", 0).apply(); }
    public static boolean isUnlocked(Context c) {
        return System.currentTimeMillis() <= sp(c).getLong("unlock_until", 0);
    }
    public static boolean protectNow(Context c) {
        return protect(c) && System.currentTimeMillis() > sp(c).getLong("unlock_until", 0);
    }

    public static boolean isAccessibilityOn(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(c, BlockerService.class);
        for (String s : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }
}
