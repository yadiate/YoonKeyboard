package com.example.eightwayime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PAGE_BACKGROUND = Color.rgb(246, 246, 248);
    private static final int CARD_BACKGROUND = Color.WHITE;
    private static final int TEXT_PRIMARY = Color.rgb(20, 20, 24);
    private static final int TEXT_SECONDARY = Color.rgb(100, 104, 112);
    private static final int DIVIDER = Color.rgb(231, 232, 236);

    private SharedPreferences prefs;
    private LinearLayout root;
    private Runnable backAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        applySystemBars();
        buildSettingsScreen();
    }

    @Override
    public void onBackPressed() {
        if (backAction != null) {
            backAction.run();
            return;
        }
        super.onBackPressed();
    }

    private void applySystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(PAGE_BACKGROUND);
        window.setNavigationBarColor(PAGE_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void buildSettingsScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(PAGE_BACKGROUND);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(32));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        setContentView(scrollView);
        showMainPage(false, 0);
    }

    private void showMainPage(boolean animate, int direction) {
        backAction = null;
        root.removeAllViews();
        addHeader();
        addSystemSection();
        addKeyboardSection();
        addFeedbackSection();
        addSnippetSection();
        if (animate) {
            animatePageFrom(direction);
        }
    }

    private void showChoicePage(String title, String[] values, String key, int defaultValue, Runnable parentPage) {
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader(title, parentPage);
        addSectionTitle("옵션");
        LinearLayout card = addCard();
        int current = currentIndex(key, values, defaultValue);
        for (int i = 0; i < values.length; i++) {
            final int selected = i;
            addOptionRow(card, values[i], selected == current, () -> {
                SettingsStore.putInt(this, key, selected);
                parentPage.run();
            });
            if (i < values.length - 1) {
                addDivider(card);
            }
        }
        animatePageFrom(1);
    }

    private void showProgressPage(String title, String[] values, String key, int defaultValue, Runnable parentPage) {
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader(title, parentPage);
        addSectionTitle("단계");

        LinearLayout card = addCard();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView valueText = rowTitle("");
        valueText.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(valueText, matchWrap());

        TextView description = rowSummary("");
        description.setGravity(Gravity.CENTER_HORIZONTAL);
        description.setPadding(0, dp(6), 0, dp(14));
        content.addView(description, matchWrap());

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(values.length - 1);
        seekBar.setProgress(currentIndex(key, values, defaultValue));
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        updateProgressText(seekBar.getProgress(), values, valueText, description);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateProgressText(progress, values, valueText, description);
                SettingsStore.putInt(MainActivity.this, key, progress);
                if (SettingsStore.KEY_VIBRATE_LEVEL.equals(key)) {
                    SettingsStore.putBoolean(MainActivity.this, SettingsStore.KEY_VIBRATE_ON, progress > 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        animatePageFrom(1);
    }

    private void showStrokeLengthPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("획 길이", parentPage);

        addSectionTitle("사용자 정의");
        LinearLayout customCard = addCard();
        SettingsStore.putBoolean(this, SettingsStore.KEY_STROKE_CUSTOM, true);

        int strokeIndex = currentIndex(SettingsStore.KEY_STROKE_LENGTH, SettingsStore.STROKE_LENGTHS, 2);
        addStrokeMmRow(customCard, "짧은 획 길이",
                "방향이 바뀐 것으로 인정할 최소 움직임입니다.",
                SettingsStore.KEY_STROKE_SHORT_MM_TENTHS,
                SettingsStore.MIN_CUSTOM_SHORT_MM_TENTHS,
                SettingsStore.MAX_CUSTOM_SHORT_MM_TENTHS,
                SettingsStore.defaultShortStrokeMmTenths(strokeIndex));
        addDivider(customCard);
        addStrokeMmRow(customCard, "긴 획 길이",
                "ㅡ, ㅣ 같은 긴 획으로 인정할 움직임입니다.",
                SettingsStore.KEY_STROKE_LONG_MM_TENTHS,
                SettingsStore.MIN_CUSTOM_LONG_MM_TENTHS,
                SettingsStore.MAX_CUSTOM_LONG_MM_TENTHS,
                SettingsStore.defaultLongStrokeMmTenths(strokeIndex));
        animatePageFrom(1);
    }

    private void showTextListPage(String title, String keyPrefix, int count, Runnable parentPage) {
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader(title, parentPage);
        addSectionTitle("항목");

        LinearLayout card = addCard();
        EditText[] inputs = new EditText[count];
        for (int i = 0; i < count; i++) {
            EditText editText = new EditText(this);
            editText.setHint(title + " " + (i + 1));
            editText.setText(prefs.getString(keyPrefix + i, ""));
            editText.setSingleLine(false);
            editText.setMinLines(1);
            editText.setMaxLines(3);
            editText.setTextSize(15);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setPadding(dp(18), dp(10), dp(18), dp(10));
            card.addView(editText, matchWrap());
            inputs[i] = editText;
            if (i < count - 1) {
                addDivider(card);
            }
        }

        addSectionTitle("저장");
        LinearLayout saveCard = addCard();
        addActionRow(saveCard, "저장", "입력한 내용을 저장합니다.", () -> {
            for (int i = 0; i < inputs.length; i++) {
                SettingsStore.putString(this, keyPrefix + i, inputs[i].getText().toString());
            }
            Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show();
            parentPage.run();
        });
        animatePageFrom(1);
    }

    private void addHeader() {
        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("Gungsuh", Typeface.BOLD));
        title.setTextColor(TEXT_PRIMARY);
        title.setGravity(Gravity.START);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title, matchWrap());
    }

    private void addDetailHeader(String titleText, Runnable back) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(12), 0, dp(10));

        TextView backView = new TextView(this);
        backView.setText("‹");
        backView.setTextSize(38);
        backView.setTextColor(TEXT_PRIMARY);
        backView.setGravity(Gravity.CENTER);
        backView.setOnClickListener(v -> back.run());
        header.addView(backView, new LinearLayout.LayoutParams(dp(42), dp(54)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(TEXT_PRIMARY);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        root.addView(header, matchWrap());
    }

    private void addSystemSection() {
        addSectionTitle("키보드");
        LinearLayout card = addCard();
        addActionRow(card, "키보드 사용 설정", "Android 설정에서 윤키보드를 활성화합니다.",
                () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addDivider(card);
        addActionRow(card, "키보드 선택", "현재 입력창에서 사용할 키보드를 고릅니다.", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
    }

    private void addKeyboardSection() {
        addSectionTitle("자판");
        LinearLayout card = addCard();
        addChoiceRow(card, "스킨", SettingsStore.SKINS, SettingsStore.KEY_SKIN, 0,
                () -> showMainPage(true, -1));
    }

    private void addFeedbackSection() {
        addSectionTitle("입력");
        LinearLayout card = addCard();
        addStrokeLengthRow(card);
        addDivider(card);
        addChoiceRow(card, "쌍자음 인식 속도", SettingsStore.DOUBLE_TAP_TIMES,
                SettingsStore.KEY_DOUBLE_TAP_TIME, 1, () -> showMainPage(true, -1));

        addSectionTitle("피드백");
        LinearLayout feedbackCard = addCard();
        addSwitchRow(feedbackCard, "진동", "키를 누를 때 짧게 진동합니다.", SettingsStore.KEY_VIBRATE_ON, true);
        addDivider(feedbackCard);
        addProgressRow(feedbackCard, "진동 길이", SettingsStore.VIBRATE_LEVELS,
                SettingsStore.KEY_VIBRATE_LEVEL, 1, () -> showMainPage(true, -1));
        addDivider(feedbackCard);
        addSwitchRow(feedbackCard, "소리", "키 입력음을 재생합니다.", SettingsStore.KEY_SOUND_ON, false);
    }

    private void addSnippetSection() {
        addSectionTitle("문구");
        LinearLayout card = addCard();
        addTextListRow(card, "상용구", "저장된 문구 " + nonEmptyCount(SettingsStore.KEY_SENTENCE_PREFIX, 14) + "개",
                SettingsStore.KEY_SENTENCE_PREFIX, 14, () -> showMainPage(true, -1));
        addDivider(card);
        addTextListRow(card, "내정보", "저장된 항목 " + nonEmptyCount(SettingsStore.KEY_MY_INFO_PREFIX, 4) + "개",
                SettingsStore.KEY_MY_INFO_PREFIX, 4, () -> showMainPage(true, -1));
    }

    private void addSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(TEXT_SECONDARY);
        title.setGravity(Gravity.START);
        title.setPadding(dp(4), dp(22), 0, dp(8));
        root.addView(title, matchWrap());
    }

    private LinearLayout addCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD_BACKGROUND, 26));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(4));
        root.addView(card, params);
        return card;
    }

    private void addChoiceRow(LinearLayout card, String title, String[] values, String key, int defaultValue,
                              Runnable parentPage) {
        int index = currentIndex(key, values, defaultValue);
        TextView summary = addMenuRow(card, title, values[index], true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showChoicePage(title, values, key, defaultValue, parentPage));
    }

    private void addTextListRow(LinearLayout card, String title, String summary, String keyPrefix, int count,
                                Runnable parentPage) {
        TextView summaryView = addMenuRow(card, title, summary, true);
        View row = (View) summaryView.getTag();
        row.setOnClickListener(v -> showTextListPage(title, keyPrefix, count, parentPage));
    }

    private void addProgressRow(LinearLayout card, String title, String[] values, String key, int defaultValue,
                                Runnable parentPage) {
        int index = currentIndex(key, values, defaultValue);
        TextView summary = addMenuRow(card, title, values[index], true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showProgressPage(title, values, key, defaultValue, parentPage));
    }

    private void addStrokeLengthRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "획 길이", strokeLengthSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showStrokeLengthPage());
    }

    private void addActionRow(LinearLayout card, String title, String summary, Runnable action) {
        TextView summaryView = addMenuRow(card, title, summary, true);
        View row = (View) summaryView.getTag();
        row.setOnClickListener(v -> action.run());
    }

    private TextView addMenuRow(LinearLayout card, String title, String summary, boolean chevron) {
        LinearLayout row = rowContainer();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(rowTitle(title), matchWrap());
        TextView summaryView = rowSummary(summary);
        texts.addView(summaryView, matchWrap());
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        if (chevron) {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextSize(28);
            arrow.setTextColor(Color.rgb(160, 164, 172));
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        card.addView(row, matchWrap());
        summaryView.setTag(row);
        return summaryView;
    }

    private void addOptionRow(LinearLayout card, String title, boolean selected, Runnable action) {
        LinearLayout row = rowContainer();
        TextView titleView = rowTitle(title);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView check = new TextView(this);
        check.setText(selected ? "✓" : "");
        check.setTextSize(22);
        check.setTextColor(Color.rgb(36, 107, 253));
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> action.run());
        card.addView(row, matchWrap());
    }

    private void addSwitchRow(LinearLayout card, String title, String summary, String key, boolean defaultValue) {
        LinearLayout row = rowContainer();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(rowTitle(title), matchWrap());
        texts.addView(rowSummary(summary), matchWrap());
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> SettingsStore.putBoolean(this, key, isChecked));
        row.addView(toggle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        card.addView(row, matchWrap());
    }

    private void addStrokeMmRow(LinearLayout card, String title, String description, String key,
                                int minTenths, int maxTenths, int fallbackTenths) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(16));

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = rowTitle(title);
        topLine.addView(titleView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView valueView = rowTitle("");
        valueView.setGravity(Gravity.END);
        topLine.addView(valueView, new LinearLayout.LayoutParams(dp(74),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(topLine, matchWrap());

        TextView descriptionView = rowSummary(description);
        descriptionView.setPadding(0, dp(4), 0, dp(8));
        content.addView(descriptionView, matchWrap());

        SeekBar seekBar = new SeekBar(this);
        int current = prefs.getInt(key, fallbackTenths);
        current = key.equals(SettingsStore.KEY_STROKE_SHORT_MM_TENTHS)
                ? SettingsStore.boundedCustomShortStrokeMmTenths(current, fallbackTenths)
                : SettingsStore.boundedCustomLongStrokeMmTenths(current, fallbackTenths);
        seekBar.setMax((maxTenths - minTenths) / SettingsStore.CUSTOM_STROKE_STEP_TENTHS);
        seekBar.setProgress(strokeSeekProgress(current, minTenths));
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        updateStrokeMmText(valueView, strokeSeekValue(seekBar.getProgress(), minTenths, maxTenths));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = strokeSeekValue(progress, minTenths, maxTenths);
                SettingsStore.putInt(MainActivity.this, key, value);
                updateStrokeMmText(valueView, value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updateProgressText(int progress, String[] values, TextView valueText, TextView description) {
        valueText.setText(values[progress]);
        description.setText(vibrateDescription(progress));
    }

    private void updateStrokeMmText(TextView valueText, int tenths) {
        valueText.setText(SettingsStore.strokeMmLabel(tenths));
    }

    private int strokeSeekProgress(int tenths, int minTenths) {
        return Math.max(0, Math.round((tenths - minTenths) / (float) SettingsStore.CUSTOM_STROKE_STEP_TENTHS));
    }

    private int strokeSeekValue(int progress, int minTenths, int maxTenths) {
        int value = minTenths + progress * SettingsStore.CUSTOM_STROKE_STEP_TENTHS;
        return Math.max(minTenths, Math.min(value, maxTenths));
    }

    private String strokeLengthSummary() {
        int index = currentIndex(SettingsStore.KEY_STROKE_LENGTH, SettingsStore.STROKE_LENGTHS, 2);
        int shortStroke = SettingsStore.boundedCustomShortStrokeMmTenths(
                prefs.getInt(SettingsStore.KEY_STROKE_SHORT_MM_TENTHS,
                        SettingsStore.defaultShortStrokeMmTenths(index)),
                SettingsStore.defaultShortStrokeMmTenths(index));
        int longStroke = SettingsStore.boundedCustomLongStrokeMmTenths(
                prefs.getInt(SettingsStore.KEY_STROKE_LONG_MM_TENTHS,
                        SettingsStore.defaultLongStrokeMmTenths(index)),
                SettingsStore.defaultLongStrokeMmTenths(index));
        return "짧은 " + SettingsStore.strokeMmLabel(shortStroke)
                + " · 긴 " + SettingsStore.strokeMmLabel(longStroke);
    }

    private String vibrateDescription(int level) {
        switch (level) {
            case 0:
                return "키를 눌러도 진동하지 않습니다.";
            case 1:
                return "아주 짧게 한 번 울립니다.";
            case 2:
                return "짧고 가볍게 울립니다.";
            case 3:
                return "기본 세기로 울립니다.";
            case 4:
                return "조금 길게 울립니다.";
            case 5:
            default:
                return "가장 길게 울립니다.";
        }
    }

    private int currentIndex(String key, String[] values, int fallback) {
        int value = prefs.getInt(key, fallback);
        return value >= 0 && value < values.length ? value : fallback;
    }

    private int nonEmptyCount(String keyPrefix, int count) {
        int total = 0;
        for (int i = 0; i < count; i++) {
            String value = prefs.getString(keyPrefix + i, "");
            if (value != null && !value.trim().isEmpty()) {
                total++;
            }
        }
        return total;
    }

    private TextView rowTitle(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(16);
        textView.setTextColor(TEXT_PRIMARY);
        textView.setGravity(Gravity.START);
        return textView;
    }

    private TextView rowSummary(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(13);
        textView.setTextColor(TEXT_SECONDARY);
        textView.setGravity(Gravity.START);
        textView.setPadding(0, dp(3), 0, 0);
        return textView;
    }

    private LinearLayout rowContainer() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(18), dp(13), dp(14), dp(13));
        return row;
    }

    private void addDivider(LinearLayout card) {
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(0.5f)));
        params.setMargins(dp(18), 0, dp(18), 0);
        card.addView(divider, params);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void animatePageFrom(int direction) {
        root.post(() -> {
            float width = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
            root.setTranslationX(width * direction);
            root.animate().translationX(0).setDuration(180).start();
        });
    }
}
