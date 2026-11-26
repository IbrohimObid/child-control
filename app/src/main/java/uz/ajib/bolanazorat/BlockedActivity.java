package uz.ajib.bolanazorat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** "Bloklangan" ekrani. */
public class BlockedActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String what = getIntent().getStringExtra("what");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#1E88E5"));
        int p = dp(32);
        root.setPadding(p, p, p, p);

        TextView icon = new TextView(this);
        icon.setText("🛡");
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView title = new TextView(this);
        title.setText((what != null ? what : "Bu ilova") + " hozir yopiq");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, dp(8));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Ota-onang bu ilovani hozircha yopib qo'ygan. Ruxsat kerak bo'lsa, ulardan so'ra 🙂");
        sub.setTextColor(Color.WHITE);
        sub.setTextSize(17);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        Button ok = new Button(this);
        ok.setText("Tushunarli");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(32);
        ok.setLayoutParams(lp);
        ok.setOnClickListener(v -> goHome());
        root.addView(ok);

        setContentView(root);
    }

    @Override
    public void onBackPressed() { goHome(); }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
