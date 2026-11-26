package uz.ajib.bolanazorat;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

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

        // YouTube PiP (suzuvchi oyna) — Home'dan keyin ham chiqib qolsa, pauza qilamiz
        if (Prefs.ytBlockedNow(this) && !YOUTUBE.contains(pkg) && youtubePipOpen()) {
            pauseMedia();
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }

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

        // 4) Faqat o'chirish/administrator oynasidan himoya (oddiy Sozlamalarga tegmaydi)
        if (Prefs.protectNow(this)) {
            String me = getString(R.string.app_name).toLowerCase();
            boolean installer = pkg.equals("com.google.android.packageinstaller")
                    || pkg.equals("com.android.packageinstaller")
                    || pkg.equals("com.miui.packageinstaller");
            if (installer && windowHas(me, null)) {
                // «...ni o'chirmoqchimisiz?» tasdiq oynasi
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
            } else if (pkg.equals("com.android.settings") && windowHas(me, ADMIN_WORDS)) {
                // qurilma administratorini o'chirish sahifasi
                performGlobalAction(GLOBAL_ACTION_BACK);
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
        }
    }

    /** Administratorni o'chirish sahifasidagi so'zlar (ru/uz/en). */
    private static final String[] ADMIN_WORDS = {
            "администрир", "администратор", "деактив", "device admin",
            "administrator", "deactivate", "qurilma ma", "administratorini"};

    private void block(String what) {
        pauseMedia();                         // videoni pauza qiladi — PiP'ga o'tmaydi
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

    /** Ovoz fokusini olib, YouTube/boshqa pleyerni pauzaga tushiradi (PiP oynasini ham to'xtatadi). */
    private AudioFocusRequest focusReq;

    private void pauseMedia() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusReq == null) {
                    focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                            .build();
                }
                am.requestAudioFocus(focusReq);
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            }
        } catch (Exception ignored) {}
    }

    /** Boshqa oynalar orasida YouTube'ning PiP (suzuvchi) oynasi bormi. */
    private boolean youtubePipOpen() {
        try {
            List<AccessibilityWindowInfo> wins = getWindows();
            if (wins == null) return false;
            for (AccessibilityWindowInfo w : wins) {
                if (w == null) continue;
                AccessibilityNodeInfo r = w.getRoot();
                if (r != null && r.getPackageName() != null
                        && YOUTUBE.contains(r.getPackageName().toString())) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
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

    /**
     * Faol oynada {@code name} bor va (words==null yoki words'dan biri ham) bor-yo'qligini tekshiradi.
     * Shu bilan oddiy Sozlamalar sahifalariga tegmay, faqat o'chirish/admin oynasini aniqlaymiz.
     */
    private boolean windowHas(String name, String[] words) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        StringBuilder sb = new StringBuilder();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int visited = 0;
        while (!q.isEmpty() && visited < 600) {
            AccessibilityNodeInfo n = q.poll();
            visited++;
            if (n == null) continue;
            CharSequence t = n.getText();
            if (t != null) sb.append(t).append(' ');
            CharSequence d = n.getContentDescription();
            if (d != null) sb.append(d).append(' ');
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        String all = sb.toString().toLowerCase();
        if (!all.contains(name)) return false;
        if (words == null) return true;
        for (String w : words) if (all.contains(w)) return true;
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
