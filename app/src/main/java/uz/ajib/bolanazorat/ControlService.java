package uz.ajib.bolanazorat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Doim ishlab turadigan xizmat: Telegram'dan ota-ona buyruqlarini oladi,
 * himoya o'chirilsa yoki vaqt tugasa ota-onaga xabar beradi.
 */
public class ControlService extends Service {
    private static final String CHANNEL = "nazorat";
    private static final int NOTIF_ID = 1;

    private static final String BTN_BLOCK = "🚫 YouTube blok";
    private static final String BTN_ALLOW = "✅ YouTube ruxsat";
    private static final String BTN_30 = "⏱ 30 daqiqa";
    private static final String BTN_60 = "⏱ 1 soat";
    private static final String BTN_LOCK = "🔌 Telefonni o'chirish";
    private static final String BTN_UNLOCK = "🔆 Telefonni yoqish";
    private static final String BTN_GAMES_OFF = "🎮 O'yinlar blok";
    private static final String BTN_GAMES_ON = "🎮 O'yinlar ruxsat";
    private static final String BTN_STATUS = "📊 Holat";

    private static final String KEYBOARD = "{\"keyboard\":["
            + "[\"" + BTN_LOCK + "\",\"" + BTN_UNLOCK + "\"],"
            + "[\"" + BTN_BLOCK + "\",\"" + BTN_ALLOW + "\"],"
            + "[\"" + BTN_30 + "\",\"" + BTN_60 + "\"],"
            + "[\"" + BTN_GAMES_OFF + "\",\"" + BTN_GAMES_ON + "\"],"
            + "[\"" + BTN_STATUS + "\"]],\"resize_keyboard\":true}";

    private volatile boolean running;
    private Thread worker;
    private final Map<Long, Integer> wrongCode = new HashMap<>();

    public static void start(Context c) {
        if (!Prefs.isConfigured(c)) return;
        Intent i = new Intent(c, ControlService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
            // Fondan ishga tushirish taqiqlangan bo'lsa, keyingi imkoniyatda qayta urinamiz
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Ota-ona nazorati",
                NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Ota-ona nazorati yoqilgan")
                .setContentText("Bu telefonni ota-onang boshqaradi")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.isConfigured(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Prefs.locked(this)) LockOverlay.ensure(this);
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop, "telegram-loop");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ------------------------------------------------------------------

    private void loop() {
        Telegram tg = new Telegram(Prefs.token(this));
        tg.deleteWebhook();
        boolean lastAccessibility = Prefs.isAccessibilityOn(this);
        boolean lastBlockedNow = Prefs.ytBlockedNow(this);
        boolean lastAdmin = isAdmin();
        notifyOwners(tg, "📱 Telefon nazorati ishga tushdi.\n" + statusText());

        while (running) {
            try {
                JSONArray updates = tg.getUpdates(Prefs.offset(this), 25);
                for (int i = 0; i < updates.length(); i++) {
                    JSONObject u = updates.getJSONObject(i);
                    Prefs.setOffset(this, u.getLong("update_id") + 1);
                    JSONObject msg = u.optJSONObject("message");
                    if (msg == null) continue;
                    long chatId = msg.getJSONObject("chat").getLong("id");
                    String text = msg.optString("text", "").trim();
                    if (!text.isEmpty()) handle(tg, chatId, text);
                }
            } catch (Exception e) {
                sleep(5000); // internet yo'q yoki xato — biroz kutamiz
            }

            // Himoya o'chirildimi?
            boolean acc = Prefs.isAccessibilityOn(this);
            if (acc != lastAccessibility) {
                notifyOwners(tg, acc
                        ? "✅ Bloklash xizmati qayta yoqildi."
                        : "⚠️ DIQQAT: bolaning telefonida bloklash xizmati (Maxsus imkoniyatlar) O'CHIRILDI! Hozir bloklash ishlamayapti.");
                lastAccessibility = acc;
            }

            // Qurilma administratori o'chirildimi?
            boolean adm = isAdmin();
            if (lastAdmin && !adm) {
                notifyOwners(tg, "⚠️ DIQQAT: ilovaning o'chirishdan himoyasi (qurilma administratori) o'chirildi!");
            }
            lastAdmin = adm;

            // Vaqtinchalik ruxsat tugadimi?
            boolean blockedNow = Prefs.ytBlockedNow(this);
            if (blockedNow && !lastBlockedNow && Prefs.allowUntil(this) > 0) {
                notifyOwners(tg, "⏱ Ruxsat vaqti tugadi — YouTube yana bloklandi.");
            }
            lastBlockedNow = blockedNow;
        }
    }

    private void handle(Telegram tg, long chatId, String text) {
        String cmd = text;
        String arg = "";
        int sp = text.indexOf(' ');
        if (text.startsWith("/") && sp > 0) {
            cmd = text.substring(0, sp);
            arg = text.substring(sp + 1).trim();
        }
        int at = cmd.indexOf('@'); // /holat@bot_nomi
        if (cmd.startsWith("/") && at > 0) cmd = cmd.substring(0, at);

        boolean isOwner = Prefs.owners(this).contains(String.valueOf(chatId));

        if (cmd.equals("/start") || cmd.equals("/ulan")) {
            if (isOwner) {
                tg.send(chatId, "Siz allaqachon ulangansiz.\n\n" + help(), KEYBOARD);
                return;
            }
            int wrong = wrongCode.containsKey(chatId) ? wrongCode.get(chatId) : 0;
            if (wrong >= 5) return; // kod tanlashdan himoya
            boolean ok = !arg.isEmpty()
                    && (arg.equals(Prefs.connectCode(this)) || arg.equals(Prefs.pin(this)));
            if (ok) {
                Prefs.addOwner(this, chatId);
                wrongCode.remove(chatId);
                notifyOwners(tg, "➕ Yangi qurilma ulandi. Jami: " + Prefs.owners(this).size() + " ta.");
                tg.send(chatId, "✅ Ulandingiz! Endi bu telefonni shu yerdan boshqarasiz.\n\n"
                        + statusText() + "\n\n" + help(), KEYBOARD);
            } else {
                wrongCode.put(chatId, wrong + 1);
                tg.send(chatId, "Ulanish uchun yozing:  /ulan KOD\n(KOD — ilovadagi «Ulanish kodi»)", null);
            }
            return;
        }

        if (!isOwner) return; // begonalarga javob bermaymiz

        switch (cmd) {
            // ---- Butun telefon (soxta o'chirish) ----
            case BTN_LOCK:
            case "/lock":
            case "/ochir":
                if (!LockOverlay.canDraw(this)) {
                    tg.send(chatId, "⚠️ «Boshqa ilovalar ustida ko'rsatish» ruxsati berilmagan. "
                            + "Bola telefonidagi ilovada uni yoqing.", KEYBOARD);
                    break;
                }
                Prefs.setLocked(this, true);
                LockOverlay.ensure(this);
                tg.send(chatId, "🔌 Telefon o'chirildi (qora ekran). Bola uchun o'chib qolgandek.\n"
                        + "Yoqish uchun: " + BTN_UNLOCK, KEYBOARD);
                break;

            case BTN_UNLOCK:
            case "/unlock":
            case "/yoq":
                Prefs.setLocked(this, false);
                LockOverlay.remove();
                tg.send(chatId, "🔆 Telefon yoqildi.", KEYBOARD);
                break;

            // ---- YouTube ----
            case BTN_BLOCK:
            case "/yt_off":
                Prefs.setYtBlocked(this, true);
                tg.send(chatId, "🚫 YouTube bloklandi.", KEYBOARD);
                break;

            case BTN_ALLOW:
            case "/yt_on":
                if (!arg.isEmpty()) {
                    allowMinutes(tg, chatId, arg);
                } else {
                    Prefs.setYtBlocked(this, false);
                    tg.send(chatId, "✅ YouTube'ga ruxsat berildi (muddatsiz).", KEYBOARD);
                }
                break;

            case BTN_30:
                allowMinutes(tg, chatId, "30");
                break;

            case BTN_60:
                allowMinutes(tg, chatId, "60");
                break;

            // ---- O'yinlar ----
            case BTN_GAMES_OFF:
            case "/oyin_off":
                Prefs.setBlockGames(this, true);
                tg.send(chatId, "🎮 Barcha o'yinlar bloklandi.\n"
                        + "Eslatma: ba'zi o'yinlar toifasini bildirmaydi — ularni /block bilan qo'shing.", KEYBOARD);
                break;

            case BTN_GAMES_ON:
            case "/oyin_on":
                Prefs.setBlockGames(this, false);
                tg.send(chatId, "✅ O'yinlarga ruxsat berildi.", KEYBOARD);
                break;

            // ---- Holat / yordam ----
            case BTN_STATUS:
            case "/holat":
                tg.send(chatId, statusText(), KEYBOARD);
                break;

            // ---- Boshqa ilovalar ----
            case "/block": {
                if (arg.isEmpty()) { tg.send(chatId, "Misol: /block com.zhiliaoapp.musically", null); break; }
                Set<String> s = Prefs.extraBlocked(this);
                s.add(arg);
                Prefs.setExtraBlocked(this, s);
                tg.send(chatId, "🚫 Bloklandi: " + label(arg) + " (" + arg + ")", KEYBOARD);
                break;
            }

            case "/unblock": {
                Set<String> s = Prefs.extraBlocked(this);
                if (s.remove(arg)) {
                    Prefs.setExtraBlocked(this, s);
                    tg.send(chatId, "✅ Blokdan chiqarildi: " + label(arg), KEYBOARD);
                } else {
                    tg.send(chatId, "Bu ilova ro'yxatda yo'q. Ro'yxat: /royxat", KEYBOARD);
                }
                break;
            }

            case "/royxat": {
                Set<String> s = Prefs.extraBlocked(this);
                if (s.isEmpty()) { tg.send(chatId, "Qo'shimcha bloklangan ilova yo'q.", KEYBOARD); break; }
                StringBuilder sb = new StringBuilder("Qo'shimcha bloklangan ilovalar:\n");
                for (String p : s) sb.append("• ").append(label(p)).append(" — /unblock ").append(p).append('\n');
                tg.send(chatId, sb.toString(), KEYBOARD);
                break;
            }

            case "/ilovalar":
                tg.send(chatId, installedApps(), KEYBOARD);
                break;

            // ---- Qurilmalar / kod ----
            case "/kod":
                if (arg.equalsIgnoreCase("yangi")) {
                    Prefs.newConnectCode(this);
                    notifyOwners(tg, "🔁 Ulanish kodi yangilandi. Eski kod endi ishlamaydi.");
                }
                tg.send(chatId, "🔑 Ulanish kodi: " + Prefs.connectCode(this)
                        + "\nYangi qurilmada botga yozing: /ulan " + Prefs.connectCode(this)
                        + "\nKodni yangilash: /kod yangi", KEYBOARD);
                break;

            case "/qurilmalar":
                tg.send(chatId, "Ulangan qurilmalar: " + Prefs.owners(this).size() + " ta.\n"
                        + "Hammasini o'chirish: /uz_hammasi", KEYBOARD);
                break;

            case "/uz_hammasi":
                Prefs.clearOwners(this);
                Prefs.addOwner(this, chatId); // o'zini qoldiramiz
                tg.send(chatId, "✅ Boshqa qurilmalar uzildi. Faqat shu qurilma qoldi.", KEYBOARD);
                break;

            // ---- Himoya ----
            case "/himoya_off":
                Prefs.setProtect(this, false);
                tg.send(chatId, "🔓 Sozlamalar himoyasi o'chirildi.\nQayta yoqish: /himoya_on", KEYBOARD);
                break;

            case "/himoya_on":
                Prefs.setProtect(this, true);
                tg.send(chatId, "🔒 Sozlamalar himoyasi yoqildi.", KEYBOARD);
                break;

            default:
                tg.send(chatId, help(), KEYBOARD);
        }
    }

    private void allowMinutes(Telegram tg, long chatId, String arg) {
        int m;
        try { m = Integer.parseInt(arg.replaceAll("[^0-9]", "")); } catch (Exception e) { m = 0; }
        if (m <= 0 || m > 24 * 60) { tg.send(chatId, "Daqiqani yozing, masalan: /yt_on 45", KEYBOARD); return; }
        Prefs.allowFor(this, m);
        tg.send(chatId, "✅ YouTube'ga " + m + " daqiqa ruxsat berildi (" + time(Prefs.allowUntil(this))
                + " gacha). Keyin o'zi bloklanadi.", KEYBOARD);
    }

    private String statusText() {
        StringBuilder sb = new StringBuilder("📊 Holat\n");
        sb.append("Telefon: ").append(Prefs.locked(this) ? "🔌 o'chirilgan (qora ekran)" : "✅ ochiq").append('\n');
        if (!Prefs.ytBlocked(this)) {
            sb.append("YouTube: ✅ ruxsat berilgan\n");
        } else if (!Prefs.ytBlockedNow(this)) {
            sb.append("YouTube: ⏱ ").append(time(Prefs.allowUntil(this))).append(" gacha ruxsat\n");
        } else {
            sb.append("YouTube: 🚫 bloklangan\n");
        }
        sb.append("O'yinlar: ").append(Prefs.blockGames(this) ? "🚫 bloklangan" : "✅ ruxsat").append('\n');
        sb.append("Bloklash xizmati: ").append(Prefs.isAccessibilityOn(this) ? "✅ ishlayapti" : "⚠️ O'CHIQ").append('\n');
        boolean admin = isAdmin();
        sb.append("O'chirishdan himoya: ").append(admin ? "✅" : "❌").append('\n');
        BatteryManager bm = getSystemService(BatteryManager.class);
        if (bm != null) sb.append("Batareya: ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%\n");
        int extra = Prefs.extraBlocked(this).size();
        if (extra > 0) sb.append("Qo'shimcha bloklangan: ").append(extra).append(" ta (/royxat)\n");
        sb.append("Ulangan qurilmalar: ").append(Prefs.owners(this).size()).append(" ta\n");
        return sb.toString().trim();
    }

    private String help() {
        return "Buyruqlar:\n"
                + "/lock — telefonni o'chirish (qora ekran)\n"
                + "/unlock — telefonni yoqish\n"
                + "/yt_off — YouTube bloklash\n"
                + "/yt_on — YouTube ruxsat (muddatsiz)\n"
                + "/yt_on 45 — 45 daqiqaga ruxsat\n"
                + "/oyin_off — barcha o'yinlarni bloklash\n"
                + "/oyin_on — o'yinlarga ruxsat\n"
                + "/holat — telefon holati\n"
                + "/ilovalar — o'rnatilgan ilovalar\n"
                + "/block paket — boshqa ilovani bloklash\n"
                + "/unblock paket — blokdan chiqarish\n"
                + "/royxat — bloklangan ilovalar\n"
                + "/kod — qurilma ulash kodini ko'rish\n"
                + "/qurilmalar — ulangan qurilmalar\n"
                + "/himoya_off, /himoya_on — sozlamalar himoyasi";
    }

    private String installedApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(launcher, 0);
        List<String> lines = new ArrayList<>();
        for (ResolveInfo ri : list) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            String line = ai.loadLabel(pm) + "\n/block " + ai.packageName;
            if (!lines.contains(line)) lines.add(line);
        }
        Collections.sort(lines, String.CASE_INSENSITIVE_ORDER);
        return "O'rnatilgan ilovalar (bloklash uchun buyruqni nusxalang):\n\n" + String.join("\n\n", lines);
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private boolean isAdmin() {
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(new ComponentName(this, AdminReceiver.class));
    }

    private void notifyOwners(Telegram tg, String text) {
        for (String id : Prefs.owners(this)) {
            try { tg.send(Long.parseLong(id), text, KEYBOARD); } catch (Exception ignored) {}
        }
    }

    private static String time(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
