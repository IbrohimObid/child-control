package uz.ajib.bolanazorat;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Maxsus imkoniyatlar xizmati: ochilgan ilovani kuzatadi va
 * bloklanganini (YouTube, brauzerdagi youtube.com, ota-ona qo'shgan ilovalar) yopadi.
 */
public class BlockerService extends AccessibilityService {

    private static final Set<String> YOUTUBE = new HashSet<>(Arrays.asList(Prefs.YOUTUBE_PACKAGES));

    private static final Set<String> BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus",
            "com.sec.android.app.sbrowser", "com.opera.browser", "com.opera.mini.native",
            "com.opera.gx", "com.yandex.browser", "com.yandex.browser.lite", "ru.yandex.searchplugin",
            "com.microsoft.emmx", "com.brave.browser", "com.UCMobile.intl", "com.uc.browser.en",
            "com.mi.globalbrowser", "com.android.browser", "com.duckduckgo.mobile.android",
            "com.vivaldi.browser", "com.kiwibrowser.browser", "com.huawei.browser",
            "com.heytap.browser", "com.google.android.googlequicksearchbox"));

    /** Ilovani o'chirish/sozlash mumkin bo'lgan tizim oynalari. */
    private static final Set<String> SETTINGS = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.miui.securitycenter", "com.samsung.android.lool", "com.samsung.accessibility",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.coloros.safecenter", "com.huawei.systemmanager", "com.android.vending"));

    private static final String[] YT_URLS = {"youtube.com", "youtu.be", "youtube-nocookie"};

    private final java.util.HashMap<String, Boolean> gameCache = new java.util.HashMap<>();
    private long lastCheck;
    private long lastBlock;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ControlService.start(this);
        if (Prefs.locked(this)) LockOverlay.ensure(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 0) Soxta o'chirish rejimi — hamma narsadan ustun
        if (Prefs.locked(this)) {
            LockOverlay.ensure(this);
            return;
        } else {
            LockOverlay.remove();
        }

        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;

        int type = event.getEventType();
        long now = System.currentTimeMillis();
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastCheck < 800) return; // juda tez-tez tekshirmaymiz
        }
        lastCheck = now;

        // Barcha o'yinlarni bloklash
        if (Prefs.blockGames(this) && isGame(pkg)) {
            block(null);
            return;
        }

        // 1) YouTube ilovasi
        if (YOUTUBE.contains(pkg) && Prefs.ytBlockedNow(this)) {
            block("YouTube");
            return;
        }

        // 2) Ota-ona qo'shgan ilovalar
        if (Prefs.extraBlocked(this).contains(pkg)) {
            block(null);
            return;
        }

        // 3) Brauzerda youtube.com
        if (BROWSERS.contains(pkg) && Prefs.ytBlockedNow(this)) {
            if (urlBarHasYoutube()) {
                block("YouTube");
                return;
            }
        }

        // 4) Sozlamalarda shu ilovani o'chirishdan himoya
        if (SETTINGS.contains(pkg) && Prefs.protectNow(this)) {
            if (windowMentionsMe()) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }
    }

    private void block(String what) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        long now = System.currentTimeMillis();
        if (now - lastBlock < 1500) return;
        lastBlock = now;
        try {
            Intent i = new Intent(this, BlockedActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (what != null) i.putExtra("what", what);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    /** Brauzer manzil qatorida youtube bormi. */
    private boolean urlBarHasYoutube() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited < 400) {
            AccessibilityNodeInfo n = q.poll();
            visited++;
            if (n == null) continue;
            String id = n.getViewIdResourceName();
            CharSequence cls = n.getClassName();
            boolean urlLike = (id != null && (id.contains("url") || id.contains("location")
                    || id.contains("address") || id.contains("search_box") || id.contains("omnibox")))
                    || (cls != null && cls.toString().endsWith("EditText"));
            if (urlLike && containsYoutube(n.getText())) return true;
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return false;
    }

    private static boolean containsYoutube(CharSequence cs) {
        if (cs == null) return false;
        String s = cs.toString().toLowerCase();
        for (String u : YT_URLS) if (s.contains(u)) return true;
        return false;
    }

    /** Oynada shu ilova nomi ko'rinyaptimi (o'chirish, admin yoki maxsus imkoniyat sahifasi). */
    private boolean windowMentionsMe() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        String me = getString(R.string.app_name).toLowerCase();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited < 500) {
            AccessibilityNodeInfo n = q.poll();
            visited++;
            if (n == null) continue;
            CharSequence t = n.getText();
            if (t != null && t.toString().toLowerCase().contains(me)) return true;
            CharSequence d = n.getContentDescription();
            if (d != null && d.toString().toLowerCase().contains(me)) return true;
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return false;
    }

    /** Ilova o'yinmi (manifestdagi toifa bo'yicha). */
    private boolean isGame(String pkg) {
        Boolean cached = gameCache.get(pkg);
        if (cached != null) return cached;
        boolean g = false;
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                g = ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME;
            }
            // eski usul: FLAG_IS_GAME (eskirgan, lekin ba'zi o'yinlarda bor)
            if (!g) g = (ai.flags & android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0;
        } catch (Exception ignored) {}
        gameCache.put(pkg, g);
        return g;
    }

    @Override
    public void onInterrupt() {}
}
