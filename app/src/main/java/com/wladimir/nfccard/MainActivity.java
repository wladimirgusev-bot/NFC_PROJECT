package com.wladimir.nfccard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "nfc_card";
    private static final String KEY_NAME = "name";
    private static final String KEY_JOB = "job";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HAS_PROFILE = "has_profile";

    private EditText nameInput;
    private EditText jobInput;
    private EditText phoneInput;
    private EditText emailInput;

    private FrameLayout contentHost;
    private LinearLayout editBar;
    private TextView status;
    private ScrollView editScroll;

    private boolean cardMode = false;

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
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            try {
                cardEmulation = CardEmulation.getInstance(nfcAdapter);
                hceService = new ComponentName(this, NdefHceService.class);
            } catch (Exception ignored) {
            }
        }

        buildShell();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean hasProfile = prefs.getBoolean(KEY_HAS_PROFILE, prefs.contains(KEY_NAME));

        if (hasProfile) {
            showCardScreen();
        } else {
            showEditScreen();
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(18));
        root.setBackgroundColor(Color.rgb(11, 16, 32));

        TextView title = text("NFC BUSINESS CARD", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text("Цифровая визитка для передачи контакта по NFC", 13,
                Color.rgb(148, 163, 184));
        LinearLayout.LayoutParams subtitleLp = lp(-1, -2);
        subtitleLp.setMargins(0, dp(5), 0, dp(14));
        root.addView(subtitle, subtitleLp);

        contentHost = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(contentHost, contentLp);

        editBar = new LinearLayout(this);
        editBar.setOrientation(LinearLayout.HORIZONTAL);
        editBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        editBar.setVisibility(View.GONE);

        TextView editButton = text("✎", 28, Color.WHITE);
        editButton.setGravity(Gravity.CENTER);
        editButton.setContentDescription("Редактировать визитку");
        editButton.setBackground(roundRect(Color.rgb(30, 41, 59), 22));
        editButton.setClickable(true);
        editButton.setFocusable(true);

        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        editBar.addView(editButton, editLp);

        root.addView(editBar, lp(-1, dp(52)));

        editButton.setOnClickListener(v -> {
            disableShareSession();
            showEditScreen();
        });

        setContentView(root);
    }

    private void showEditScreen() {
        cardMode = false;
        editBar.setVisibility(View.GONE);
        contentHost.removeAllViews();

        editScroll = new ScrollView(this);
        editScroll.setFillViewport(false);
        editScroll.setVerticalScrollBarEnabled(true);
        editScroll.setClipToPadding(false);
        editScroll.setPadding(0, 0, 0, dp(180));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(4), 0, dp(24));
        editScroll.addView(page);

        TextView heading = text("Данные визитки", 24, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        page.addView(heading, lp(-1, -2));

        TextView hint = text("Заполните поля и нажмите «Сохранить».", 14,
                Color.rgb(148, 163, 184));
        LinearLayout.LayoutParams hintLp = lp(-1, -2);
        hintLp.setMargins(0, dp(5), 0, dp(16));
        page.addView(hint, hintLp);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(18), dp(20), dp(20));
        form.setBackground(roundRect(Color.WHITE, 22));
        page.addView(form, lp(-1, -2));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        nameInput = labeledField(
                form,
                "ФИО",
                "Введите ФИО",
                prefs.getString(KEY_NAME, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );

        jobInput = labeledField(
                form,
                "Должность",
                "Введите должность",
                prefs.getString(KEY_JOB, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );

        phoneInput = labeledField(
                form,
                "Телефон",
                "Введите номер телефона",
                prefs.getString(KEY_PHONE, ""),
                InputType.TYPE_CLASS_PHONE
        );

        emailInput = labeledField(
                form,
                "E-mail",
                "Введите адрес электронной почты",
                prefs.getString(KEY_EMAIL, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        Button save = new Button(this);
        save.setText("СОХРАНИТЬ");
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setTextColor(Color.WHITE);
        save.setBackground(roundRect(Color.rgb(37, 99, 235), 16));

        LinearLayout.LayoutParams saveLp = lp(-1, dp(56));
        saveLp.setMargins(0, dp(18), 0, 0);
        page.addView(save, saveLp);

        save.setOnClickListener(v -> {
            if (saveData()) {
                hideKeyboard();
                Toast.makeText(this, "Визитка сохранена", Toast.LENGTH_SHORT).show();
                showCardScreen();
            }
        });

        contentHost.addView(editScroll);
    }

    private void showCardScreen() {
        cardMode = true;
        editScroll = null;
        editBar.setVisibility(View.VISIBLE);
        contentHost.removeAllViews();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        String name = prefs.getString(KEY_NAME, "");
        String job = prefs.getString(KEY_JOB, "");
        String phone = prefs.getString(KEY_PHONE, "");
        String email = prefs.getString(KEY_EMAIL, "");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(0, dp(10), 0, dp(18));
        scroll.addView(page);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(24));
        card.setBackground(roundRect(Color.WHITE, 24));

        LinearLayout.LayoutParams cardLp = lp(-1, -2);
        cardLp.setMargins(0, dp(4), 0, 0);
        page.addView(card, cardLp);

        TextView initials = text(getInitials(name), 24, Color.WHITE);
        initials.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        initials.setBackground(roundRect(Color.rgb(37, 99, 235), 34));
        card.addView(initials, new LinearLayout.LayoutParams(dp(68), dp(68)));

        TextView nameView = text(name, 28, Color.rgb(15, 23, 42));
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = lp(-1, -2);
        nameLp.setMargins(0, dp(20), 0, 0);
        card.addView(nameView, nameLp);

        if (!job.isEmpty()) {
            TextView jobView = text(job, 18, Color.rgb(71, 85, 105));
            LinearLayout.LayoutParams jobLp = lp(-1, -2);
            jobLp.setMargins(0, dp(6), 0, 0);
            card.addView(jobView, jobLp);
        }

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(226, 232, 240));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(22), 0, dp(18));
        card.addView(divider, divLp);

        if (!phone.isEmpty()) {
            TextView phoneView = text("☎  " + phone, 16, Color.rgb(30, 41, 59));
            card.addView(phoneView, lp(-1, -2));
        }

        if (!email.isEmpty()) {
            TextView emailView = text("✉  " + email, 16, Color.rgb(37, 99, 235));
            LinearLayout.LayoutParams emailLp = lp(-1, -2);
            emailLp.setMargins(0, dp(10), 0, 0);
            card.addView(emailView, emailLp);
        }

        Button share = new Button(this);
        share.setText("ПЕРЕДАТЬ ПО NFC");
        share.setTextSize(16);
        share.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        share.setTextColor(Color.WHITE);
        share.setBackground(roundRect(Color.rgb(37, 99, 235), 16));

        LinearLayout.LayoutParams shareLp = lp(-1, dp(58));
        shareLp.setMargins(0, dp(18), 0, 0);
        page.addView(share, shareLp);

        status = text("Нажмите кнопку и приложите другой телефон", 13,
                Color.rgb(148, 163, 184));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = lp(-1, -2);
        statusLp.setMargins(dp(8), dp(12), dp(8), 0);
        page.addView(status, statusLp);

        share.setOnClickListener(v -> enableShare());

        contentHost.addView(scroll);
        setPreferredHceService();
    }

    private EditText labeledField(
            LinearLayout parent,
            String labelText,
            String hintText,
            String value,
            int inputType
    ) {
        TextView label = text(labelText, 14, Color.rgb(51, 65, 85));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        LinearLayout.LayoutParams labelLp = lp(-1, -2);
        labelLp.setMargins(0, dp(10), 0, dp(6));
        parent.addView(label, labelLp);

        EditText input = new EditText(this);
        input.setHint(hintText);
        input.setText(value);
        input.setTextSize(16);
        input.setTextColor(Color.rgb(15, 23, 42));
        input.setHintTextColor(Color.rgb(148, 163, 184));
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(dp(14), 0, dp(14), 0);

        GradientDrawable bg = roundRect(Color.rgb(248, 250, 252), 12);
        bg.setStroke(dp(1), Color.rgb(203, 213, 225));
        input.setBackground(bg);

        parent.addView(input, lp(-1, dp(54)));

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollInputIntoView(input);
            }
        });

        input.setOnClickListener(v -> scrollInputIntoView(input));

        return input;
    }

    private void scrollInputIntoView(View input) {
        if (editScroll == null) {
            return;
        }

        editScroll.postDelayed(() -> {
            if (editScroll == null) {
                return;
            }

            Rect rect = new Rect();
            input.getDrawingRect(rect);
            editScroll.offsetDescendantRectToMyCoords(input, rect);

            int target = Math.max(0, rect.top - dp(70));
            editScroll.smoothScrollTo(0, target);
        }, 280);
    }

    private boolean saveData() {
        String name = nameInput.getText().toString().trim();
        String job = jobInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Введите ФИО");
            nameInput.requestFocus();
            scrollInputIntoView(nameInput);
            return false;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, name)
                .putString(KEY_JOB, job)
                .putString(KEY_PHONE, phone)
                .putString(KEY_EMAIL, email)
                .putBoolean(KEY_HAS_PROFILE, true)
                .putBoolean(KEY_ENABLED, false)
                .apply();

        return true;
    }

    private void enableShare() {
        if (nfcAdapter == null) {
            Toast.makeText(this, "На этом телефоне NFC не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            if (status != null) {
                status.setText("Включите NFC в системных настройках");
            }
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

        if (status != null) {
            status.setText("Готово • приложите другой телефон");
        }

        Toast.makeText(this, "NFC-визитка активна", Toast.LENGTH_SHORT).show();
    }

    private void disableShareSession() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, false)
                .apply();

        if (cardEmulation != null) {
            try {
                cardEmulation.unsetPreferredService(this);
            } catch (Exception ignored) {
            }
        }
    }

    private void setPreferredHceService() {
        if (!cardMode
                || nfcAdapter == null
                || !nfcAdapter.isEnabled()
                || cardEmulation == null
                || hceService == null) {
            return;
        }

        try {
            cardEmulation.setPreferredService(this, hceService);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cardMode) {
            setPreferredHceService();
        }
    }

    @Override
    protected void onPause() {
        disableShareSession();
        super.onPause();
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }

        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }

        focused.clearFocus();
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "NFC";
        }

        String[] parts = name.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
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

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
