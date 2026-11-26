package uz.ajib.bolanazorat;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Sozlash oynasi. Sozlangandan keyin faqat PIN bilan ochiladi. */
public class MainActivity extends Activity {

    private LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, p * 2, p, p);
        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.isConfigured(this)) showSetup();
        else if (!Prefs.isUnlocked(this)) showPin();
        else showPanel();
    }

    // ---------------------------------------------------------------- 1. Birinchi sozlash

    private void showSetup() {
        box.removeAllViews();
        title("Bola Nazorati — sozlash");
        text("1) Telegram'da @BotFather'ga kiring, /newbot buyrug'i bilan yangi bot yarating va tokenni shu yerga qo'ying.\n"
                + "2) PIN o'ylab toping (4–8 raqam). Bola bilmasin.");
        EditText token = input("Bot token (123456:ABC...)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText pin = input("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        EditText pin2 = input("PIN (takror)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        TextView status = text("");

        button("Saqlash", v -> {
            String t = token.getText().toString().trim();
            String p1 = pin.getText().toString().trim();
            if (!t.matches("\\d+:[\\w-]{30,}")) { status.setText("Token noto'g'ri ko'rinyapti."); return; }
            if (!p1.matches("\\d{4,8}")) { status.setText("PIN 4–8 ta raqam bo'lsin."); return; }
            if (!p1.equals(pin2.getText().toString().trim())) { status.setText("PINlar mos emas."); return; }
            v.setEnabled(false);
            status.setText("Bot tekshirilmoqda...");
            new Thread(() -> {
                try {
                    String name = new Telegram(t).getMeUsername();
                    runOnUiThread(() -> {
                        Prefs.saveSetup(this, t, p1);
                        Prefs.clearOwners(this);
                        Prefs.setBotName(this, name);
                        Prefs.unlockForSetup(this);
                        ControlService.start(this);
                        showPanel();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        v.setEnabled(true);
                        status.setText("Xato: " + e.getMessage() + "\nInternet va tokenni tekshiring.");
                    });
                }
            }).start();
        });
    }

    // ---------------------------------------------------------------- 2. PIN

    private void showPin() {
        box.removeAllViews();
        title("🛡 Ota-ona nazorati");
        text("Sozlamalarga kirish uchun PIN kiriting.");
        EditText pin = input("PIN", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        button("Kirish", v -> {
            if (pin.getText().toString().trim().equals(Prefs.pin(this))) {
                Prefs.unlockForSetup(this);
                showPanel();
            } else {
                pin.setText("");
                Toast.makeText(this, "PIN noto'g'ri", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---------------------------------------------------------------- 3. Panel

    private void showPanel() {
        box.removeAllViews();
        title("🛡 Bola Nazorati");

        String bot = Prefs.botName(this);
        text("Ota-ona telefonida (yoki istalgan qurilmada) Telegram'ni oching, @" + bot
                + " botiga kiring va yozing:\n\n"
                + "/ulan " + Prefs.connectCode(this) + "\n\n"
                + "🔑 Ulanish kodi: " + Prefs.connectCode(this) + "  (bir nechta qurilma ulanishi mumkin)\n"
                + "Ulangan qurilmalar: " + Prefs.owners(this).size() + " ta").setTextIsSelectable(true);

        section("Quyidagilarning hammasini yoqing:");

        if (Build.VERSION.SDK_INT >= 33) {
            boolean notif = checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            step(notif, "Bildirishnomalar", "Ruxsat berish",
                    v -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1));
        }

        boolean acc = Prefs.isAccessibilityOn(this);
        if (Build.VERSION.SDK_INT >= 33 && !acc) {
            text("⚠️ Android 13+ da avval: Ilova haqida → yuqoridagi ⋮ menyu → «Cheklangan sozlamalarga ruxsat berish». "
                    + "Shundan keyin Maxsus imkoniyatlar yoqiladi.");
            button("Ilova haqida oynasini ochish", v -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }
        step(acc, "Maxsus imkoniyatlar → Bola Nazorati (YouTube'ni bloklaydi)", "Ochish",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        step(overlay, "Boshqa ilovalar ustida ko'rsatish (qora ekran / «o'chirish» uchun)", "Yoqish",
                v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));

        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        step(dpm.isAdminActive(admin), "Qurilma administratori (o'chirishdan himoya)", "Yoqish", v -> {
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_desc));
            startActivity(i);
        });

        PowerManager pm = getSystemService(PowerManager.class);
        step(pm.isIgnoringBatteryOptimizations(getPackageName()), "Batareya tejashdan chiqarish (fonda o'chmasin)", "Ruxsat",
                v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                });

        text("Xiaomi/Redmi, Huawei, Oppo telefonlarida «Avtozapusk» (Autostart) ruxsatini ham qo'lda yoqing.");

        section("Tayyor bo'lgach:");
        button("🔒 Qulflash va chiqish", v -> {
            Prefs.lockNow(this);
            finish();
        });
        button("Bot yoki PINni o'zgartirish", v -> {
            Prefs.saveSetup(this, "", "");
            Prefs.clearOwners(this);
            stopService(new Intent(this, ControlService.class));
            showSetup();
        });
    }

    // ---------------------------------------------------------------- UI yordamchilar

    private void step(boolean done, String label, String btn, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView t = new TextView(this);
        t.setText((done ? "✅ " : "❌ ") + label);
        t.setTextSize(16);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(t);
        if (!done) {
            Button b = new Button(this);
            b.setText(btn);
            b.setOnClickListener(l);
            row.addView(b);
        }
        box.addView(row);
    }

    private void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#1E88E5"));
        t.setPadding(0, 0, 0, dp(12));
        box.addView(t);
    }

    private void section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        box.addView(t);
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        box.addView(t);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        box.addView(e);
        return e;
    }

    private void button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        box.addView(b);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
