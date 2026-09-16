package com.wladimir.nfccard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private NfcAdapter nfcAdapter;
    private CardEmulation cardEmulation;
    private ComponentName hceService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(11, 16, 32));
        getWindow().setNavigationBarColor(Color.rgb(11, 16, 32));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        hceService = new ComponentName(this, NdefHceService.class);
        if (nfcAdapter != null) {
            try {
                cardEmulation = CardEmulation.getInstance(nfcAdapter);
            } catch (Exception ignored) {
                cardEmulation = null;
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(30));
        root.setBackgroundColor(Color.rgb(11, 16, 32));

        TextView label = text("DIGITAL BUSINESS CARD", 13, Color.rgb(147, 163, 184));
        label.setLetterSpacing(0.18f);
        root.addView(label, lp(-1, -2, 0));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(30), dp(26), dp(28));
        card.setBackground(roundRect(Color.WHITE, 26));
        LinearLayout.LayoutParams cardLp = lp(-1, -2, 0);
        cardLp.setMargins(0, dp(24), 0, 0);
        root.addView(card, cardLp);

        TextView initials = text("WG", 22, Color.WHITE);
        initials.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        initials.setBackground(roundRect(Color.rgb(35, 99, 235), 50));
        LinearLayout.LayoutParams initLp = new LinearLayout.LayoutParams(dp(66), dp(66));
        card.addView(initials, initLp);

        TextView name = text("Wladimir Gusev", 30, Color.rgb(15, 23, 42));
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = lp(-1, -2, 0);
        nameLp.setMargins(0, dp(22), 0, 0);
        card.addView(name, nameLp);

        TextView job = text("Lead Project Manager", 18, Color.rgb(51, 65, 85));
        LinearLayout.LayoutParams jobLp = lp(-1, -2, 0);
        jobLp.setMargins(0, dp(6), 0, 0);
        card.addView(job, jobLp);

        TextView company = text("ООО «АрПи Канон Медикал Системз»", 16, Color.rgb(71, 85, 105));
        LinearLayout.LayoutParams companyLp = lp(-1, -2, 0);
        companyLp.setMargins(0, dp(20), 0, 0);
        card.addView(company, companyLp);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(226, 232, 240));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(22), 0, dp(18));
        card.addView(divider, divLp);

        TextView phone = text("Тел.: +7 (926) 610-10-36", 16, Color.rgb(30, 41, 59));
        card.addView(phone, lp(-1, -2, 0));

        TextView email = text("Vladimir.Gusev@rp.medical.canon", 16, Color.rgb(35, 99, 235));
        LinearLayout.LayoutParams emailLp = lp(-1, -2, 0);
        emailLp.setMargins(0, dp(10), 0, 0);
        card.addView(email, emailLp);

        LinearLayout nfc = new LinearLayout(this);
        nfc.setOrientation(LinearLayout.VERTICAL);
        nfc.setGravity(Gravity.CENTER);
        nfc.setPadding(dp(20), dp(20), dp(20), dp(20));
        nfc.setBackground(roundRect(Color.rgb(37, 99, 235), 22));
        LinearLayout.LayoutParams nfcLp = lp(-1, -2, 0);
        nfcLp.setMargins(0, dp(24), 0, 0);
        root.addView(nfc, nfcLp);

        TextView nfcTitle = text("NFC  •  ПЕРЕДАТЬ КОНТАКТ", 17, Color.WHITE);
        nfcTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nfcTitle.setGravity(Gravity.CENTER);
        nfc.addView(nfcTitle, lp(-1, -2, 0));

        status = text("Нажмите сюда и приложите другой телефон", 14, Color.rgb(219, 234, 254));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lp(-1, -2, 0);
        statusLp.setMargins(0, dp(7), 0, 0);
        nfc.addView(status, statusLp);

        nfc.setOnClickListener(v -> enableShare());
        card.setOnClickListener(v -> enableShare());

        TextView note = text("Передаётся стандартная vCard. При открытой визитке NFC Business Card выбирается приоритетным сервисом автоматически.", 12, Color.rgb(148, 163, 184));
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = lp(-1, -2, 1);
        noteLp.setMargins(dp(8), dp(18), dp(8), 0);
        root.addView(note, noteLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && nfcAdapter.isEnabled()
                && getSharedPreferences("nfc_card", MODE_PRIVATE).getBoolean("enabled", false)) {
            preferThisService();
            if (status != null) {
                status.setText("Готово к передаче • приложите другой телефон");
            }
        }
    }

    @Override
    protected void onPause() {
        if (cardEmulation != null) {
            try {
                cardEmulation.unsetPreferredService(this);
            } catch (Exception ignored) {
            }
        }
        super.onPause();
    }

    private void enableShare() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "На этом телефоне NFC не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            status.setText("Включите NFC в открывшихся настройках");
            try {
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
            return;
        }
        getSharedPreferences("nfc_card", MODE_PRIVATE).edit().putBoolean("enabled", true).apply();
        preferThisService();
        status.setText("Готово к передаче • приложите другой телефон");
        Toast.makeText(this, "NFC Business Card выбрана автоматически", Toast.LENGTH_SHORT).show();
    }

    private void preferThisService() {
        if (cardEmulation != null && hceService != null) {
            try {
                cardEmulation.setPreferredService(this, hceService);
            } catch (Exception ignored) {
            }
        }
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
