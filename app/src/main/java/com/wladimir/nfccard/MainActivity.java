package com.wladimir.nfccard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String PREFS = "nfc_card";
    private static final String KEY_NAME = "name";
    private static final String KEY_JOB = "job";
    private static final String KEY_COMPANY = "company";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_HAS_PROFILE = "has_profile";
    private static final String KEY_EXTRA_FIELDS = "extra_fields";

    private static final String TYPE_PHONE = "phone";
    private static final String TYPE_EMAIL = "email";
    private static final String TYPE_ORG = "org";

    private EditText nameInput;
    private EditText jobInput;
    private EditText companyInput;
    private EditText phoneInput;
    private EditText emailInput;

    private FrameLayout contentHost;
    private LinearLayout editBar;
    private ScrollView editScroll;
    private LinearLayout editPage;
    private LinearLayout extraFieldsContainer;

    private final ArrayList<ExtraField> extraFields = new ArrayList<>();

    private static class ExtraField {
        String type;
        String value;
        EditText input;

        ExtraField(String type, String value) {
            this.type = type;
            this.value = value == null ? "" : value;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(11, 16, 32));
        getWindow().setNavigationBarColor(Color.rgb(11, 16, 32));
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );

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

        TextView title = text("QR BUSINESS CARD", 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, lp(-1, -2));

        TextView subtitle = text(
                "Цифровая визитка по QR-коду",
                13,
                Color.rgb(148, 163, 184)
        );
        LinearLayout.LayoutParams subtitleLp = lp(-1, -2);
        subtitleLp.setMargins(0, dp(5), 0, dp(14));
        root.addView(subtitle, subtitleLp);

        contentHost = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );
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

        editBar.addView(
                editButton,
                new LinearLayout.LayoutParams(dp(48), dp(48))
        );

        root.addView(editBar, lp(-1, dp(52)));
        editButton.setOnClickListener(v -> showEditScreen());

        setContentView(root);
    }

    private void showEditScreen() {
        editBar.setVisibility(View.GONE);
        contentHost.removeAllViews();

        loadExtraFields();

        editScroll = new ScrollView(this);
        editScroll.setFillViewport(true);
        editScroll.setVerticalScrollBarEnabled(true);
        editScroll.setSmoothScrollingEnabled(true);
        editScroll.setClipToPadding(false);
        editScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        editPage = new LinearLayout(this);
        editPage.setOrientation(LinearLayout.VERTICAL);
        editPage.setPadding(0, dp(4), 0, dp(24));
        editScroll.addView(
                editPage,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        TextView heading = text("Данные визитки", 24, Color.WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        editPage.addView(heading, lp(-1, -2));

        TextView hint = text(
                "Заполните поля и нажмите «Сохранить».",
                14,
                Color.rgb(148, 163, 184)
        );
        LinearLayout.LayoutParams hintLp = lp(-1, -2);
        hintLp.setMargins(0, dp(5), 0, dp(16));
        editPage.addView(hint, hintLp);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(18), dp(20), dp(20));
        form.setBackground(roundRect(Color.WHITE, 22));
        editPage.addView(form, lp(-1, -2));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        nameInput = labeledField(
                form,
                "ФИО",
                "Фамилия Имя Отчество",
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

        companyInput = labeledField(
                form,
                "Организация",
                "Введите название организации",
                prefs.getString(KEY_COMPANY, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
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

        extraFieldsContainer = new LinearLayout(this);
        extraFieldsContainer.setOrientation(LinearLayout.VERTICAL);
        form.addView(extraFieldsContainer, lp(-1, -2));
        renderExtraFields();

        Button addField = new Button(this);
        addField.setText("+ ДОБАВИТЬ ПОЛЕ");
        addField.setTextSize(14);
        addField.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addField.setTextColor(Color.rgb(37, 99, 235));
        GradientDrawable addBg = roundRect(Color.WHITE, 14);
        addBg.setStroke(dp(1), Color.rgb(37, 99, 235));
        addField.setBackground(addBg);

        LinearLayout.LayoutParams addLp = lp(-1, dp(52));
        addLp.setMargins(0, dp(16), 0, 0);
        form.addView(addField, addLp);

        addField.setOnClickListener(v -> showAddFieldDialog());

        Button save = new Button(this);
        save.setText("СОХРАНИТЬ");
        save.setTextSize(16);
        save.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        save.setTextColor(Color.WHITE);
        save.setBackground(roundRect(Color.rgb(37, 99, 235), 16));

        LinearLayout.LayoutParams saveLp = lp(-1, dp(56));
        saveLp.setMargins(0, dp(18), 0, 0);
        editPage.addView(save, saveLp);

        View bottomSpacer = new View(this);
        editPage.addView(bottomSpacer, lp(-1, dp(320)));

        save.setOnClickListener(v -> {
            if (saveData()) {
                hideKeyboard();
                Toast.makeText(this, "Визитка сохранена", Toast.LENGTH_SHORT).show();
                showCardScreen();
            }
        });

        contentHost.addView(
                editScroll,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        installKeyboardAwareScrolling();
    }

    private void showAddFieldDialog() {
        syncExtraValues();

        String[] items = {"Телефон", "E-mail", "Организация"};

        new AlertDialog.Builder(this)
                .setTitle("Добавить поле")
                .setItems(items, (dialog, which) -> {
                    String type;
                    if (which == 0) {
                        type = TYPE_PHONE;
                    } else if (which == 1) {
                        type = TYPE_EMAIL;
                    } else {
                        type = TYPE_ORG;
                    }

                    extraFields.add(new ExtraField(type, ""));
                    renderExtraFields();

                    if (!extraFields.isEmpty()) {
                        ExtraField added = extraFields.get(extraFields.size() - 1);
                        if (added.input != null) {
                            added.input.requestFocus();
                            ensureFieldVisible(added.input);
                            showKeyboard(added.input);
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renderExtraFields() {
        if (extraFieldsContainer == null) {
            return;
        }

        extraFieldsContainer.removeAllViews();

        int phoneNo = 2;
        int emailNo = 2;
        int orgNo = 2;

        for (int i = 0; i < extraFields.size(); i++) {
            final int index = i;
            ExtraField field = extraFields.get(i);

            String label;
            String hint;
            int inputType;

            if (TYPE_PHONE.equals(field.type)) {
                label = "Телефон " + phoneNo++;
                hint = "Введите номер телефона";
                inputType = InputType.TYPE_CLASS_PHONE;
            } else if (TYPE_EMAIL.equals(field.type)) {
                label = "E-mail " + emailNo++;
                hint = "Введите адрес электронной почты";
                inputType = InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            } else {
                label = "Организация " + orgNo++;
                hint = "Введите название организации";
                inputType = InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
            }

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView labelView = text(label, 14, Color.rgb(51, 65, 85));
            labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

            LinearLayout.LayoutParams labelLp =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            labelLp.setMargins(0, dp(10), 0, dp(6));
            header.addView(labelView, labelLp);

            TextView remove = text("✕", 20, Color.rgb(220, 38, 38));
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("Удалить поле " + label);
            remove.setClickable(true);
            remove.setFocusable(true);

            LinearLayout.LayoutParams removeLp =
                    new LinearLayout.LayoutParams(dp(40), dp(40));
            removeLp.setMargins(dp(8), dp(4), 0, 0);
            header.addView(remove, removeLp);

            extraFieldsContainer.addView(header, lp(-1, -2));

            EditText input = new EditText(this);
            input.setHint(hint);
            input.setText(field.value);
            input.setTextSize(16);
            input.setTextColor(Color.rgb(15, 23, 42));
            input.setHintTextColor(Color.rgb(148, 163, 184));
            input.setSingleLine(true);
            input.setInputType(inputType);
            input.setPadding(dp(14), 0, dp(14), 0);

            GradientDrawable bg =
                    roundRect(Color.rgb(248, 250, 252), 12);
            bg.setStroke(dp(1), Color.rgb(203, 213, 225));
            input.setBackground(bg);

            extraFieldsContainer.addView(input, lp(-1, dp(54)));
            field.input = input;

            input.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    ensureFieldVisible(input);
                }
            });

            input.setOnClickListener(v -> ensureFieldVisible(input));

            remove.setOnClickListener(v -> {
                syncExtraValues();
                if (index >= 0 && index < extraFields.size()) {
                    extraFields.remove(index);
                    renderExtraFields();
                }
            });
        }
    }

    private void syncExtraValues() {
        for (ExtraField field : extraFields) {
            if (field.input != null) {
                field.value = field.input.getText().toString().trim();
            }
        }
    }

    private void loadExtraFields() {
        extraFields.clear();

        String json = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_EXTRA_FIELDS, "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                String type = obj.optString("type", "");
                String value = obj.optString("value", "");

                if (TYPE_PHONE.equals(type)
                        || TYPE_EMAIL.equals(type)
                        || TYPE_ORG.equals(type)) {
                    extraFields.add(new ExtraField(type, value));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extrasToJson() {
        syncExtraValues();

        JSONArray array = new JSONArray();

        try {
            for (ExtraField field : extraFields) {
                JSONObject obj = new JSONObject();
                obj.put("type", field.type);
                obj.put("value", field.value);
                array.put(obj);
            }
        } catch (Exception ignored) {
        }

        return array.toString();
    }

    private void installKeyboardAwareScrolling() {
        if (editScroll == null) {
            return;
        }

        editScroll.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (editScroll == null) {
                            return;
                        }

                        Rect visible = new Rect();
                        editScroll.getWindowVisibleDisplayFrame(visible);

                        int screenHeight = editScroll.getRootView().getHeight();
                        int obscured = Math.max(0, screenHeight - visible.bottom);

                        boolean keyboardOpen = obscured > screenHeight * 0.15f;

                        int bottomPadding = keyboardOpen
                                ? Math.max(dp(24), obscured + dp(24))
                                : dp(24);

                        if (editScroll.getPaddingBottom() != bottomPadding) {
                            editScroll.setPadding(
                                    editScroll.getPaddingLeft(),
                                    editScroll.getPaddingTop(),
                                    editScroll.getPaddingRight(),
                                    bottomPadding
                            );
                        }

                        if (keyboardOpen) {
                            View focused = getCurrentFocus();
                            if (focused instanceof EditText) {
                                ensureFieldVisible(focused);
                            }
                        }
                    }
                }
        );
    }

    private void showCardScreen() {
        editScroll = null;
        editPage = null;
        extraFieldsContainer = null;
        editBar.setVisibility(View.VISIBLE);
        contentHost.removeAllViews();

        loadExtraFields();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        String name = prefs.getString(KEY_NAME, "");
        String job = prefs.getString(KEY_JOB, "");
        String company = prefs.getString(KEY_COMPANY, "");
        String phone = prefs.getString(KEY_PHONE, "");
        String email = prefs.getString(KEY_EMAIL, "");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(0, dp(8), 0, dp(24));
        scroll.addView(page);

        TextView qrTitle = text("Отсканируйте QR-код", 20, Color.WHITE);
        qrTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        qrTitle.setGravity(Gravity.CENTER);
        page.addView(qrTitle, lp(-1, -2));

        TextView qrSubtitle = text(
                "Откроется карточка контакта для сохранения",
                13,
                Color.rgb(148, 163, 184)
        );
        qrSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qrSubtitleLp = lp(-1, -2);
        qrSubtitleLp.setMargins(0, dp(4), 0, dp(12));
        page.addView(qrSubtitle, qrSubtitleLp);

        LinearLayout qrBox = new LinearLayout(this);
        qrBox.setOrientation(LinearLayout.VERTICAL);
        qrBox.setGravity(Gravity.CENTER);
        qrBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        qrBox.setBackground(roundRect(Color.WHITE, 22));

        ImageView qrView = new ImageView(this);
        Bitmap qrBitmap = buildQrBitmap(
                buildVCard(name, job, company, phone, email),
                dp(260)
        );

        if (qrBitmap != null) {
            qrView.setImageBitmap(qrBitmap);
        }

        qrBox.addView(
                qrView,
                new LinearLayout.LayoutParams(dp(260), dp(260))
        );
        page.addView(qrBox, lp(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(26), dp(24), dp(24));
        card.setBackground(roundRect(Color.WHITE, 24));

        LinearLayout.LayoutParams cardLp = lp(-1, -2);
        cardLp.setMargins(0, dp(18), 0, 0);
        page.addView(card, cardLp);

        TextView initials = text(getInitials(name), 24, Color.WHITE);
        initials.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        initials.setGravity(Gravity.CENTER);
        initials.setBackground(roundRect(Color.rgb(37, 99, 235), 34));
        card.addView(
                initials,
                new LinearLayout.LayoutParams(dp(68), dp(68))
        );

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

        if (!company.isEmpty()) {
            TextView companyView = text(company, 16, Color.rgb(37, 99, 235));
            companyView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams companyLp = lp(-1, -2);
            companyLp.setMargins(0, dp(6), 0, 0);
            card.addView(companyView, companyLp);
        }

        for (ExtraField field : extraFields) {
            if (TYPE_ORG.equals(field.type) && !field.value.isEmpty()) {
                TextView orgView = text(field.value, 16, Color.rgb(37, 99, 235));
                orgView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                LinearLayout.LayoutParams orgLp = lp(-1, -2);
                orgLp.setMargins(0, dp(6), 0, 0);
                card.addView(orgView, orgLp);
            }
        }

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(226, 232, 240));
        LinearLayout.LayoutParams divLp =
                new LinearLayout.LayoutParams(-1, dp(1));
        divLp.setMargins(0, dp(22), 0, dp(18));
        card.addView(divider, divLp);

        if (!phone.isEmpty()) {
            card.addView(
                    text("☎  " + phone, 16, Color.rgb(30, 41, 59)),
                    lp(-1, -2)
            );
        }

        for (ExtraField field : extraFields) {
            if (TYPE_PHONE.equals(field.type) && !field.value.isEmpty()) {
                TextView phoneView =
                        text("☎  " + field.value, 16, Color.rgb(30, 41, 59));
                LinearLayout.LayoutParams p = lp(-1, -2);
                p.setMargins(0, dp(10), 0, 0);
                card.addView(phoneView, p);
            }
        }

        if (!email.isEmpty()) {
            TextView emailView = text(
                    "✉  " + email,
                    16,
                    Color.rgb(37, 99, 235)
            );
            LinearLayout.LayoutParams emailLp = lp(-1, -2);
            emailLp.setMargins(0, dp(10), 0, 0);
            card.addView(emailView, emailLp);
        }

        for (ExtraField field : extraFields) {
            if (TYPE_EMAIL.equals(field.type) && !field.value.isEmpty()) {
                TextView emailView =
                        text("✉  " + field.value, 16, Color.rgb(37, 99, 235));
                LinearLayout.LayoutParams p = lp(-1, -2);
                p.setMargins(0, dp(10), 0, 0);
                card.addView(emailView, p);
            }
        }

        contentHost.addView(scroll);
    }

    private EditText labeledField(
            LinearLayout parent,
            String labelText,
            String hintText,
            String value,
            int inputType
    ) {
        TextView label = text(
                labelText,
                14,
                Color.rgb(51, 65, 85)
        );
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

        GradientDrawable bg =
                roundRect(Color.rgb(248, 250, 252), 12);
        bg.setStroke(dp(1), Color.rgb(203, 213, 225));
        input.setBackground(bg);

        parent.addView(input, lp(-1, dp(54)));

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                ensureFieldVisible(input);
            }
        });

        input.setOnClickListener(v -> ensureFieldVisible(input));

        return input;
    }

    private void ensureFieldVisible(View input) {
        if (editScroll == null || input == null) {
            return;
        }

        editScroll.post(() -> scrollFieldNow(input));
        editScroll.postDelayed(() -> scrollFieldNow(input), 350);
    }

    private void scrollFieldNow(View input) {
        if (editScroll == null || input == null) {
            return;
        }

        Rect rect = new Rect();
        input.getDrawingRect(rect);
        editScroll.offsetDescendantRectToMyCoords(input, rect);

        int target = Math.max(0, rect.top - dp(90));
        editScroll.smoothScrollTo(0, target);
    }

    private boolean saveData() {
        String name = nameInput.getText().toString().trim();
        String job = jobInput.getText().toString().trim();
        String company = companyInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();

        if (name.isEmpty()) {
            nameInput.setError("Введите ФИО");
            nameInput.requestFocus();
            ensureFieldVisible(nameInput);
            return false;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, name)
                .putString(KEY_JOB, job)
                .putString(KEY_COMPANY, company)
                .putString(KEY_PHONE, phone)
                .putString(KEY_EMAIL, email)
                .putString(KEY_EXTRA_FIELDS, extrasToJson())
                .putBoolean(KEY_HAS_PROFILE, true)
                .apply();

        return true;
    }

    private String buildVCard(
            String name,
            String job,
            String company,
            String phone,
            String email
    ) {
        String[] nameParts = splitRussianFullName(name);
        String familyName = nameParts[0];
        String givenName = nameParts[1];
        String additionalName = nameParts[2];

        StringBuilder vcard = new StringBuilder();

        vcard.append("BEGIN:VCARD\r\n");
        vcard.append("VERSION:3.0\r\n");

        vcard.append("FN:")
                .append(vcardEscape(name))
                .append("\r\n");

        vcard.append("N:")
                .append(vcardEscape(familyName))
                .append(";")
                .append(vcardEscape(givenName))
                .append(";")
                .append(vcardEscape(additionalName))
                .append(";;\r\n");

        if (company != null && !company.trim().isEmpty()) {
            vcard.append("ORG:")
                    .append(vcardEscape(company))
                    .append("\r\n");
        }

        for (ExtraField field : extraFields) {
            if (TYPE_ORG.equals(field.type) && !field.value.isEmpty()) {
                vcard.append("ORG:")
                        .append(vcardEscape(field.value))
                        .append("\r\n");
            }
        }

        if (job != null && !job.trim().isEmpty()) {
            vcard.append("TITLE:")
                    .append(vcardEscape(job))
                    .append("\r\n");
        }

        if (phone != null && !phone.trim().isEmpty()) {
            vcard.append("TEL;TYPE=CELL:")
                    .append(vcardEscape(phone))
                    .append("\r\n");
        }

        for (ExtraField field : extraFields) {
            if (TYPE_PHONE.equals(field.type) && !field.value.isEmpty()) {
                vcard.append("TEL;TYPE=CELL:")
                        .append(vcardEscape(field.value))
                        .append("\r\n");
            }
        }

        if (email != null && !email.trim().isEmpty()) {
            vcard.append("EMAIL;TYPE=INTERNET:")
                    .append(vcardEscape(email))
                    .append("\r\n");
        }

        for (ExtraField field : extraFields) {
            if (TYPE_EMAIL.equals(field.type) && !field.value.isEmpty()) {
                vcard.append("EMAIL;TYPE=INTERNET:")
                        .append(vcardEscape(field.value))
                        .append("\r\n");
            }
        }

        vcard.append("END:VCARD\r\n");

        return vcard.toString();
    }

    private String[] splitRussianFullName(String fullName) {
        String normalized = fullName == null
                ? ""
                : fullName.trim().replaceAll("\\s+", " ");

        if (normalized.isEmpty()) {
            return new String[] {"", "", ""};
        }

        String[] parts = normalized.split(" ");

        String family = parts.length > 0 ? parts[0] : "";
        String given = parts.length > 1 ? parts[1] : "";

        StringBuilder additional = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            if (additional.length() > 0) {
                additional.append(" ");
            }
            additional.append(parts[i]);
        }

        return new String[] {
                family,
                given,
                additional.toString()
        };
    }

    private String vcardEscape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace(";", "\\;")
                .replace(",", "\\,");
    }

    private Bitmap buildQrBitmap(
            String value,
            int sizePx
    ) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(
                    value,
                    BarcodeFormat.QR_CODE,
                    sizePx,
                    sizePx,
                    hints
            );

            Bitmap bitmap = Bitmap.createBitmap(
                    sizePx,
                    sizePx,
                    Bitmap.Config.ARGB_8888
            );

            for (int y = 0; y < sizePx; y++) {
                for (int x = 0; x < sizePx; x++) {
                    bitmap.setPixel(
                            x,
                            y,
                            matrix.get(x, y)
                                    ? Color.BLACK
                                    : Color.WHITE
                    );
                }
            }

            return bitmap;

        } catch (WriterException e) {
            return null;
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();

        if (focused == null) {
            return;
        }

        InputMethodManager imm =
                (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                    focused.getWindowToken(),
                    0
            );
        }

        focused.clearFocus();
    }

    private void showKeyboard(View view) {
        if (view == null) {
            return;
        }

        view.postDelayed(() -> {
            InputMethodManager imm =
                    (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "QR";
        }

        String[] parts = name.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0]
                    .substring(0, 1)
                    .toUpperCase();
        }

        return (
                parts[0].substring(0, 1)
                        + parts[parts.length - 1].substring(0, 1)
        ).toUpperCase();
    }

    private TextView text(
            String value,
            float sp,
            int color
    ) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.08f);
        return t;
    }

    private GradientDrawable roundRect(
            int color,
            int radiusDp
    ) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams lp(
            int w,
            int h
    ) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
