package com.wladimir.nfccard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "nfc_card";
    private static final String KEY_NAME = "name";
    private static final String KEY_JOB = "job";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ENABLED = "enabled";

    private EditText nameInput;
    private EditText jobInput;
    private EditText phoneInput;
    private EditText emailInput;
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
        if (nfcAdapter != null) {
            cardEmulation = CardEmulation.getInstance(nfcAdapter);
            hceService = new ComponentName(this, NdefHceService.class);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(Color.rgb(11, 16, 32));

        TextView title = text("NFC BUSINESS CARD", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text("Введите данные, которые будут передаваться по NFC", 14, Color.rgb(148, 163, 184));
        LinearLayout.LayoutParams subLp = lp(-1, -2);
        subLp.setMargins(0, dp(6), 0, dp(20));
        root.addView(subtitle, subLp);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(20), dp(20), dp(20));
        form.setBackground(roundRect(Color.WHITE, 22));
        root.addView(form, lp(-1, -2));

        nameInput = field(form, "ФИО", prefs.getString(KEY_NAME, "Wladimir Gusev"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        jobInput = field(form, "Должность", prefs.getString(KEY_JOB, "Lead Project Manager"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        phoneInput = field(form, "Телефон", prefs.getString(KEY_PHONE, "+7 (926) 610-10-36"),
                InputType.TYPE_CLASS_PHONE);

        emailInput = field(form, "E-mail", prefs.getString(KEY_EMAIL, "Vladimir.Gusev@rp.medical.canon"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        Button save = new Button(this);
        save.setText("СОХРАНИТЬ");
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setTextColor(Color.WHITE);
        save.setBackground(roundRect(Color.rgb(71, 85, 105), 16));
        LinearLayout.LayoutParams saveLp = lp(-1, dp(54));
        saveLp.setMargins(0, dp(20), 0, 0);
        root.addView(save, saveLp);

        Button share = new Button(this);
        share.setText("ПЕРЕДАВАТЬ ПО NFC");
        share.setTextSize(16);
        share.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        share.setTextColor(Color.WHITE);
        share.setBackground(roundRect(Color.rgb(37, 99, 235), 16));
        LinearLayout.LayoutParams shareLp = lp(-1, dp(58));
        shareLp.setMargins(0, dp(12), 0, 0);
        root.addView(share, shareLp);

        status = text("NFC-визитка не активирована", 13, Color.rgb(148, 163, 184));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lp(-1, -2);
        statusLp.setMargins(0, dp(14), 0, 0);
        root.addView(status, statusLp);

        save.setOnClickListener(v -> {
            if (saveData()) {
                Toast.makeText(this, "Данные сохранены", Toast.LENGTH_SHORT).show();
            }
        });

        share.setOnClickListener(v -> {
            if (saveData()) {
                enableShare();
            }
        });

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setPreferredHceService();
    }

    @Override
    protected void onPause() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        if (cardEmulation != null) {
            try {
                cardEmulation.unsetPreferredService(this);
            } catch (Exception ignored) {
            }
        }
        super.onPause();
    }

    private EditText field(LinearLayout parent, String hint, String value, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(16);
        input.setTextColor(Color.rgb(15, 23, 42));
        input.setHintTextColor(Color.rgb(148, 163, 184));
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), dp(4), dp(14), dp(4));

        GradientDrawable bg = roundRect(Color.rgb(248, 250, 252), 12);
        bg.setStroke(dp(1), Color.rgb(203, 213, 225));
        input.setBackground(bg);

        LinearLayout.LayoutParams p = lp(-1, dp(58));
        p.setMargins(0, dp(8), 0, dp(4));
        parent.addView(input, p);
        return input;
    }

    private boolean saveData() {
        String name = nameInput.getText().toString().trim();
        String job = jobInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Введите ФИО");
            nameInput.requestFocus();
            return false;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, name)
                .putString(KEY_JOB, job)
                .putString(KEY_PHONE, phone)
                .putString(KEY_EMAIL, email)
                .apply();

        return true;
    }

    private void enableShare() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "На этом телефоне NFC не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            status.setText("Включите NFC в системных настройках");
            try {
                startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            }
            return;
        }

        setPreferredHceService();

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, true)
                .apply();

        status.setText("Готово • приложите другой телефон");
        Toast.makeText(this, "NFC-визитка активна", Toast.LENGTH_SHORT).show();
    }

    private void setPreferredHceService() {
        if (nfcAdapter == null || !nfcAdapter.isEnabled() || cardEmulation == null || hceService == null) {
            return;
        }

        try {
            cardEmulation.setPreferredService(this, hceService);
        } catch (Exception ignored) {
        }
    }

    private TextView text(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
