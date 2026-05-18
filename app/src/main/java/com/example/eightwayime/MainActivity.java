package com.example.eightwayime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.example.eightwayime.hangul.Consonant;
import com.example.eightwayime.hangul.GestureCalibration;
import com.example.eightwayime.ime.KeyboardMode;
import com.example.eightwayime.ime.KeyboardSurfaceView;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int PAGE_BACKGROUND = Color.rgb(246, 246, 248);
    private static final int CARD_BACKGROUND = Color.WHITE;
    private static final int TEXT_PRIMARY = Color.rgb(20, 20, 24);
    private static final int TEXT_SECONDARY = Color.rgb(100, 104, 112);
    private static final int DIVIDER = Color.rgb(231, 232, 236);
    private static final GestureCalibration.DirectionClass[] EDITABLE_DIAGONALS = {
            GestureCalibration.DirectionClass.TOP_RIGHT,
            GestureCalibration.DirectionClass.TOP_LEFT,
            GestureCalibration.DirectionClass.BOTTOM_RIGHT,
            GestureCalibration.DirectionClass.BOTTOM_LEFT
    };
    private static final String[] CALIBRATION_SENTENCES = {
            "가녀린 고양이는 교실 위 창가에 조용히 앉아",
            "나무 그늘 아래 누나는 겨울 여행 이야기를 들려주어",
            "다정한 도윤이는 마당에서 작은 유리병을 주워",
            "라디오 소리에 루비는 놀라며 마루 위로 올라가",
            "마을 어귀 모퉁이에는 오래된 우편함이 보여",
            "분주한 보라는 병원 옆 식당에서 국수를 먹어",
            "사려 깊은 수연이는 새벽에 종이를 조용히 버려",
            "아영이는 우유와 야채를 사려고 시장으로 걸어가",
            "자주 웃는 지우는 종이 위에 여러 가지 무늬를 그려",
            "차가운 차창 너머로 초여름 비구름이 천천히 흘러",
            "카페 구석의 커다란 쿠션 위에 키 작은 아이가 쉬어",
            "타이어 자국을 따라 태윤이는 터널 입구로 달려",
            "파란 파도 위로 표류하던 배가 평화롭게 항구에 닿아",
            "하얀 하늘 아래 혜진이는 호수 주변을 한참 걸어",
            "겨우 일어난 규리는 교과서 여백에 그림을 남겨",
            "대문으로 돌아온 도현이는 온화한 차를 권해",
            "보라는 노을 아래 푸른 풀을 바라보아",
            "수지는 넘어간 의자를 세우고 종이를 붙여",
            "지우는 국수 냄새가 퍼지자 자리에서 일어나",
            "굽은 길 끝에서 윤호는 외로운 은행나무를 오래 보아"
    };

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

    private void showKeyboardSizePage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("크기", parentPage);

        KeyboardSizePreviewView preview = new KeyboardSizePreviewView(this, parentPage);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(0, dp(10), 0, dp(8));
        TextView positionButton = sizeModeButton("위치 조절", true);
        TextView layoutButton = sizeModeButton("레이아웃 조절", false);
        modeRow.addView(positionButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams layoutButtonParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        layoutButtonParams.setMargins(dp(8), 0, 0, 0);
        modeRow.addView(layoutButton, layoutButtonParams);
        root.addView(modeRow, matchWrap());

        TextView description = new TextView(this);
        description.setText("크기를 조절하려면 키보드 측면에 있는 핸들을 움직이세요.");
        description.setTextSize(17);
        description.setTextColor(Color.rgb(72, 74, 80));
        description.setLineSpacing(dp(4), 1.0f);
        description.setPadding(0, 0, 0, dp(8));
        root.addView(description, matchWrap());

        positionButton.setOnClickListener(v -> {
            preview.setAdjustmentMode(KeyboardSizePreviewView.MODE_POSITION);
            setSizeModeSelected(positionButton, true);
            setSizeModeSelected(layoutButton, false);
            description.setText("크기를 조절하려면 키보드 측면에 있는 핸들을 움직이세요.");
        });
        layoutButton.setOnClickListener(v -> {
            preview.setAdjustmentMode(KeyboardSizePreviewView.MODE_LAYOUT);
            setSizeModeSelected(positionButton, false);
            setSizeModeSelected(layoutButton, true);
            description.setText("레이아웃 비율을 조절하려면 키보드 안쪽의 조절선을 좌우로 움직이세요.");
        });

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(380));
        root.addView(preview, previewParams);
        animatePageFrom(1);
    }

    private TextView sizeModeButton(String label, boolean selected) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setClickable(true);
        setSizeModeSelected(button, selected);
        return button;
    }

    private void setSizeModeSelected(TextView button, boolean selected) {
        GradientDrawable background = rounded(selected ? Color.rgb(32, 118, 255) : Color.WHITE, 14);
        background.setStroke(Math.max(1, dp(1)), selected ? Color.rgb(32, 118, 255) : Color.rgb(210, 216, 224));
        button.setBackground(background);
        button.setTextColor(selected ? Color.WHITE : TEXT_PRIMARY);
    }

    private void showCalibrationPage() {
        SettingsStore.endGestureCalibrationSession(this);
        hideSoftKeyboardFromCurrentFocus();
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("보정", parentPage);

        addSectionTitle("개인 맞춤");
        LinearLayout card = addCard();
        addSwitchRow(card, "보정 사용", "수집한 손 움직임으로 대각선과 긴 획을 판단합니다.",
                SettingsStore.KEY_GESTURE_CALIBRATION_ENABLED, false);
        addDivider(card);
        addActionRow(card, "보정 시작", "20개 문장을 따라 치며 자음별 획 기준을 학습합니다.",
                this::showCalibrationPracticePage);
        addDivider(card);
        addActionRow(card, "보정값 초기화", "저장된 대각선과 긴 획 기준을 지웁니다.", () -> {
            SettingsStore.clearGestureCalibration(this);
            showCalibrationPage();
        });

        addSectionTitle("상태");
        LinearLayout statusCard = addCard();
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        boolean hasCalibrationProfile = snapshot.gestureCalibrationProfile != null
                && snapshot.gestureCalibrationProfile.hasData();
        String state = snapshot.gestureCalibrationSampleCount > 0
                ? "수집된 획 " + snapshot.gestureCalibrationSampleCount + "개"
                : (hasCalibrationProfile ? "수동 보정값이 있습니다." : "아직 수집된 획이 없습니다.");
        TextView statusSummary = addMenuRow(statusCard, "현재 보정값", state, true);
        View statusRow = (View) statusSummary.getTag();
        statusRow.setOnClickListener(v -> showCalibrationValueEditorPage());
        animatePageFrom(1);
    }

    private void showCalibrationValueEditorPage() {
        SettingsStore.endGestureCalibrationSession(this);
        hideSoftKeyboardFromCurrentFocus();
        Runnable parentPage = this::showCalibrationPage;
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("현재 보정값", parentPage);

        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        boolean hasSavedProfile = snapshot.gestureCalibrationProfile != null
                && snapshot.gestureCalibrationProfile.hasData();
        final GestureCalibration.Profile savedProfile = hasSavedProfile
                ? copyCalibrationProfile(snapshot.gestureCalibrationProfile)
                : new GestureCalibration.Profile();
        final GestureCalibration.Profile[] editProfile = {
                copyCalibrationProfile(snapshot.gestureCalibrationProfile)
        };
        float fallbackShortMm = snapshot.shortStrokeMm();
        float fallbackLongMm = snapshot.longStrokeMm();
        Consonant initialConsonant = Consonant.GIYEOK;
        CalibrationAngleEditorView angleView = new CalibrationAngleEditorView(this, editProfile[0],
                initialConsonant, fallbackShortMm, fallbackLongMm, null);
        CalibrationKeyboardPickerView keyboardPickerView = new CalibrationKeyboardPickerView(this, editProfile[0],
                initialConsonant, angleView.selectedDirection, (consonant, cycleDirection) -> {
            if (cycleDirection) {
                angleView.selectNextDirection();
            }
            angleView.setSelectedConsonant(consonant);
        });
        angleView.setKeyboardPickerView(keyboardPickerView);

        LinearLayout.LayoutParams angleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(292));
        angleParams.setMargins(0, dp(2), 0, dp(10));
        root.addView(angleView, angleParams);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        addCalibrationActionButton(actionRow, "기본값으로 초기화", true, false, () -> {
            editProfile[0] = new GestureCalibration.Profile();
            angleView.setProfile(editProfile[0]);
            keyboardPickerView.setProfile(editProfile[0]);
        });
        addCalibrationActionButton(actionRow, "보정값으로 초기화", hasSavedProfile, false, () -> {
            editProfile[0] = copyCalibrationProfile(savedProfile);
            angleView.setProfile(editProfile[0]);
            keyboardPickerView.setProfile(editProfile[0]);
        });
        addCalibrationActionButton(actionRow, "저장", true, true, () -> {
            GestureCalibration.Profile profileToSave = editProfile[0] == null
                    ? new GestureCalibration.Profile()
                    : editProfile[0];
            boolean hasProfileData = profileToSave.hasData();
            if (hasProfileData) {
                SettingsStore.putString(MainActivity.this, SettingsStore.KEY_GESTURE_CALIBRATION_PROFILE,
                        profileToSave.toJson());
                SettingsStore.putBoolean(MainActivity.this, SettingsStore.KEY_GESTURE_CALIBRATION_ENABLED, true);
            } else {
                SettingsStore.clearGestureCalibration(MainActivity.this);
            }
            showCalibrationPage();
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, 0, 0, dp(12));
        root.addView(actionRow, actionParams);

        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(306));
        root.addView(keyboardPickerView, keyboardParams);
        animatePageFrom(1);
    }

    private void addCalibrationActionButton(LinearLayout row, String label, boolean enabled,
                                            boolean primary, Runnable action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setMaxLines(2);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setEnabled(enabled);
        button.setClickable(enabled);
        if (enabled && action != null) {
            button.setOnClickListener(v -> action.run());
        }

        GradientDrawable background = rounded(
                primary ? Color.rgb(32, 118, 255)
                        : (enabled ? Color.WHITE : Color.rgb(231, 234, 238)),
                14);
        if (!primary) {
            background.setStroke(Math.max(1, dp(0.8f)),
                    enabled ? Color.rgb(210, 216, 224) : Color.rgb(224, 227, 232));
        }
        button.setBackground(background);
        button.setTextColor(primary ? Color.WHITE
                : (enabled ? TEXT_PRIMARY : Color.rgb(142, 148, 156)));
        button.setAlpha(enabled ? 1f : 0.62f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) {
            params.setMargins(dp(7), 0, 0, 0);
        }
        row.addView(button, params);
    }

    private GestureCalibration.Profile copyCalibrationProfile(GestureCalibration.Profile profile) {
        if (profile == null) {
            return new GestureCalibration.Profile();
        }
        return GestureCalibration.Profile.fromJson(profile.toJson());
    }

    private void showCalibrationPracticePage() {
        Runnable parentPage = this::showCalibrationPage;
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("보정 시작", parentPage);

        CalibrationProgressView progressView = new CalibrationProgressView(this);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.setMargins(0, dp(6), 0, dp(16));
        root.addView(progressView, progressParams);

        TextView promptView = new TextView(this);
        promptView.setTextSize(22);
        promptView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        promptView.setTextColor(TEXT_PRIMARY);
        promptView.setLineSpacing(dp(5), 1.0f);
        promptView.setPadding(dp(4), dp(4), dp(4), dp(10));
        root.addView(promptView, matchWrap());

        EditText inputView = new EditText(this);
        inputView.setTextSize(20);
        inputView.setTextColor(TEXT_PRIMARY);
        inputView.setHintTextColor(Color.rgb(170, 174, 182));
        inputView.setHint("여기를 눌러 입력하세요");
        inputView.setSingleLine(true);
        inputView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputView.setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        inputView.setPrivateImeOptions("com.example.eightwayime.CALIBRATION");
        inputView.setMinHeight(dp(64));
        inputView.setGravity(Gravity.CENTER_VERTICAL);
        inputView.setPadding(dp(16), dp(12), dp(16), dp(12));
        inputView.setBackground(rounded(Color.rgb(255, 255, 255), 18));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.setMargins(0, 0, 0, dp(10));
        root.addView(inputView, inputParams);

        TextView hintView = rowSummary("입력칸을 누르면 실제 윤키보드가 올라옵니다. 문장이 맞으면 자동으로 다음 줄로 넘어갑니다.");
        hintView.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(hintView, matchWrap());

        ActualCalibrationSession session = new ActualCalibrationSession(progressView, promptView, inputView, hintView);
        inputView.addTextChangedListener(session);
        inputView.setOnClickListener(v -> showSoftKeyboard(inputView));
        inputView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showSoftKeyboard(inputView);
            }
        });
        session.refresh();
        animatePageFrom(1);
        inputView.postDelayed(() -> {
            inputView.requestFocus();
            showSoftKeyboard(inputView);
        }, 240);
    }

    private void showCalibrationCompletePage(int sampleCount) {
        Runnable parentPage = this::showCalibrationPage;
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("보정 완료", parentPage);

        addSectionTitle("완료");
        LinearLayout card = addCard();
        addMenuRow(card, "학습된 획", sampleCount + "개", false);
        addDivider(card);
        addActionRow(card, "다시 보정", "문장을 다시 입력해 기준을 새로 만듭니다.", this::showCalibrationPracticePage);
        addDivider(card);
        addActionRow(card, "설정으로 돌아가기", "보정 메뉴로 돌아갑니다.", this::showCalibrationPage);
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
        addDivider(card);
        addKeyboardSizeRow(card);
    }

    private void addFeedbackSection() {
        addSectionTitle("입력");
        LinearLayout card = addCard();
        addStrokeLengthRow(card);
        addDivider(card);
        addCalibrationRow(card);
        addDivider(card);
        addChoiceRow(card, "길게 누르기 시간", SettingsStore.DOUBLE_TAP_TIMES,
                SettingsStore.KEY_DOUBLE_TAP_TIME, 1, () -> showMainPage(true, -1));

        addSectionTitle("피드백");
        LinearLayout feedbackCard = addCard();
        addSwitchRow(feedbackCard, "진동", "키를 누를 때 짧게 진동합니다.", SettingsStore.KEY_VIBRATE_ON, true);
        addDivider(feedbackCard);
        addInlineVibrateProgressRow(feedbackCard);
        addDivider(feedbackCard);
        addSwitchRow(feedbackCard, "소리", "키 입력음을 재생합니다.", SettingsStore.KEY_SOUND_ON, false);
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

    private void addProgressRow(LinearLayout card, String title, String[] values, String key, int defaultValue,
                                Runnable parentPage) {
        int index = currentIndex(key, values, defaultValue);
        TextView summary = addMenuRow(card, title, values[index], true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showProgressPage(title, values, key, defaultValue, parentPage));
    }

    private void addInlineVibrateProgressRow(LinearLayout card) {
        int index = currentIndex(SettingsStore.KEY_VIBRATE_LEVEL, SettingsStore.VIBRATE_LEVELS, 1);
        TextView summary = addMenuRow(card, "진동 길이", SettingsStore.VIBRATE_LEVELS[index], true);
        View row = (View) summary.getTag();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), 0, dp(18), dp(16));
        panel.setVisibility(View.GONE);

        TextView description = rowSummary(vibrateDescription(index));
        description.setPadding(0, 0, 0, dp(8));
        panel.addView(description, matchWrap());

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(SettingsStore.VIBRATE_LEVELS.length - 1);
        seekBar.setProgress(index);
        panel.addView(seekBar, matchWrap());
        card.addView(panel, matchWrap());

        row.setOnClickListener(v -> {
            boolean nextVisible = panel.getVisibility() != View.VISIBLE;
            panel.setVisibility(nextVisible ? View.VISIBLE : View.GONE);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_VIBRATE_LEVEL, progress);
                SettingsStore.putBoolean(MainActivity.this, SettingsStore.KEY_VIBRATE_ON, progress > 0);
                summary.setText(SettingsStore.VIBRATE_LEVELS[progress]);
                description.setText(vibrateDescription(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void addStrokeLengthRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "획 길이", strokeLengthSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showStrokeLengthPage());
    }

    private void addKeyboardSizeRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "크기 조절", keyboardSizeSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showKeyboardSizePage());
    }

    private void addCalibrationRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "보정", calibrationSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showCalibrationPage());
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

    private String keyboardSizeSummary() {
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        return "좌 " + snapshot.keyboardLeftPercent + "% · 우 " + (100 - snapshot.keyboardRightPercent)
                + "% · 상 " + snapshot.keyboardTopPercent + "% · 하 "
                + (100 - snapshot.keyboardBottomPercent) + "% · 레이아웃 "
                + snapshot.keyboardLayoutLeftPercent + "/"
                + (snapshot.keyboardLayoutRightPercent - snapshot.keyboardLayoutLeftPercent) + "/"
                + (100 - snapshot.keyboardLayoutRightPercent);
    }

    private String calibrationSummary() {
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        if (snapshot.gestureCalibrationSampleCount <= 0) {
            if (snapshot.gestureCalibrationProfile != null && snapshot.gestureCalibrationProfile.hasData()) {
                return (snapshot.gestureCalibrationEnabled ? "사용 중" : "꺼짐") + " · 수동 보정값";
            }
            return "대각선과 긴 획을 사용자 손버릇에 맞춥니다.";
        }
        String enabled = snapshot.gestureCalibrationEnabled ? "사용 중" : "꺼짐";
        return enabled + " · 획 " + snapshot.gestureCalibrationSampleCount + "개";
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

    private int totalCalibrationCharacters() {
        int total = 0;
        for (String sentence : CALIBRATION_SENTENCES) {
            total += sentence.length();
        }
        return total;
    }

    private int calibrationCharactersBeforeLine(int lineIndex) {
        int total = 0;
        for (int i = 0; i < lineIndex && i < CALIBRATION_SENTENCES.length; i++) {
            total += CALIBRATION_SENTENCES[i].length();
        }
        return total;
    }

    private int matchingPrefixLength(String expected, String actual) {
        int count = Math.min(expected.length(), actual.length());
        for (int i = 0; i < count; i++) {
            if (expected.charAt(i) != actual.charAt(i)) {
                return i;
            }
        }
        return count;
    }

    private class CalibrationProgressView extends View {
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private float progress;
        private int lineIndex;
        private int sampleCount;

        CalibrationProgressView(Context context) {
            super(context);
            setMinimumHeight(dp(64));
        }

        void setState(float progress, int lineIndex, int sampleCount) {
            this.progress = Math.max(0f, Math.min(progress, 1f));
            this.lineIndex = lineIndex;
            this.sampleCount = sampleCount;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, dp(64));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float height = getHeight();
            trackRect.set(0, dp(28), getWidth(), dp(44));

            progressPaint.setStyle(Paint.Style.FILL);
            progressPaint.setColor(Color.rgb(227, 231, 236));
            canvas.drawRoundRect(trackRect, dp(8), dp(8), progressPaint);

            float fillRight = trackRect.left + trackRect.width() * progress;
            if (fillRight > trackRect.left + dp(4)) {
                RectF fill = new RectF(trackRect.left, trackRect.top, fillRight, trackRect.bottom);
                progressPaint.setShader(new LinearGradient(fill.left, fill.top, fill.right, fill.bottom,
                        Color.rgb(32, 118, 255), Color.rgb(38, 197, 167), Shader.TileMode.CLAMP));
                canvas.drawRoundRect(fill, dp(8), dp(8), progressPaint);
                progressPaint.setShader(null);
            }

            float dotX = Math.max(trackRect.left + dp(8), Math.min(fillRight, trackRect.right - dp(8)));
            progressPaint.setColor(Color.WHITE);
            canvas.drawCircle(dotX, trackRect.centerY(), dp(5), progressPaint);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(dp(1.5f));
            progressPaint.setColor(Color.rgb(32, 118, 255));
            canvas.drawCircle(dotX, trackRect.centerY(), dp(6.5f), progressPaint);

            progressPaint.setStyle(Paint.Style.FILL);
            progressPaint.setTextAlign(Paint.Align.LEFT);
            progressPaint.setTypeface(Typeface.DEFAULT_BOLD);
            progressPaint.setTextSize(dp(14));
            progressPaint.setColor(TEXT_PRIMARY);
            drawProgressText(canvas, "진행 " + (lineIndex + 1) + " / " + CALIBRATION_SENTENCES.length,
                    0, dp(13));

            progressPaint.setTextAlign(Paint.Align.RIGHT);
            progressPaint.setTypeface(Typeface.DEFAULT);
            progressPaint.setTextSize(dp(13));
            progressPaint.setColor(TEXT_SECONDARY);
            int percent = Math.round(progress * 100f);
            drawProgressText(canvas, percent + "% · 획 " + sampleCount + "개", getWidth(), dp(13));
        }

        private void drawProgressText(Canvas canvas, String text, float x, float centerY) {
            Paint.FontMetrics metrics = progressPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, progressPaint);
        }
    }

    private void showSoftKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideSoftKeyboard(EditText editText) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
    }

    private void hideSoftKeyboardFromCurrentFocus() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private class ActualCalibrationSession implements TextWatcher {
        private final CalibrationProgressView progressView;
        private final TextView promptView;
        private final EditText inputView;
        private final TextView hintView;
        private int lineIndex;
        private boolean advancing;
        private boolean editingProgrammatically;

        ActualCalibrationSession(CalibrationProgressView progressView, TextView promptView, EditText inputView,
                                 TextView hintView) {
            this.progressView = progressView;
            this.promptView = promptView;
            this.inputView = inputView;
            this.hintView = hintView;
            SettingsStore.startGestureCalibrationSession(MainActivity.this, currentSentence(), lineIndex);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (editingProgrammatically) {
                return;
            }
            refresh();
        }

        void refresh() {
            String sentence = currentSentence();
            String visible = inputView.getText().toString();
            promptView.setText(sentence);

            int prefix = matchingPrefixLength(sentence, visible);
            if (visible.length() > prefix) {
                hintView.setText("다른 부분이 있습니다. 지우고 이어서 다시 입력하세요.");
                inputView.setTextColor(Color.rgb(190, 83, 32));
            } else {
                hintView.setText("입력칸을 누르면 실제 윤키보드가 올라옵니다. 문장이 맞으면 자동으로 다음 줄로 넘어갑니다.");
                inputView.setTextColor(TEXT_PRIMARY);
            }

            int completedChars = calibrationCharactersBeforeLine(lineIndex) + prefix;
            progressView.setState(completedChars / (float) totalCalibrationCharacters(), lineIndex,
                    SettingsStore.gestureCalibrationSessionSampleCount(MainActivity.this));

            if (!advancing && visible.equals(sentence)) {
                advancing = true;
                root.postDelayed(this::advanceLine, 260);
            }
        }

        private void advanceLine() {
            if (!inputView.getText().toString().equals(currentSentence())) {
                advancing = false;
                refresh();
                return;
            }
            lineIndex++;
            advancing = false;
            if (lineIndex >= CALIBRATION_SENTENCES.length) {
                saveCalibration();
                return;
            }
            SettingsStore.updateGestureCalibrationSession(MainActivity.this, currentSentence(), lineIndex);
            editingProgrammatically = true;
            inputView.setText("");
            editingProgrammatically = false;
            inputView.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.restartInput(inputView);
                }
                showSoftKeyboard(inputView);
            });
            refresh();
        }

        private void saveCalibration() {
            SettingsStore.Snapshot snapshot = SettingsStore.load(MainActivity.this);
            List<GestureCalibration.Sample> samples = SettingsStore.loadGestureCalibrationSamples(MainActivity.this);
            GestureCalibration.Profile profile = GestureCalibration.train(samples,
                    snapshot.shortStrokeMm(), snapshot.longStrokeMm());
            SettingsStore.putString(MainActivity.this, SettingsStore.KEY_GESTURE_CALIBRATION_PROFILE,
                    profile.toJson());
            SettingsStore.putBoolean(MainActivity.this, SettingsStore.KEY_GESTURE_CALIBRATION_ENABLED, true);
            SettingsStore.endGestureCalibrationSession(MainActivity.this);
            hideSoftKeyboard(inputView);
            showCalibrationCompletePage(samples.size());
        }

        private String currentSentence() {
            return CALIBRATION_SENTENCES[Math.min(lineIndex, CALIBRATION_SENTENCES.length - 1)];
        }
    }

    private interface ManualAngleChangeListener {
        void onManualAngleChanged(Consonant consonant,
                                  GestureCalibration.DirectionClass directionClass,
                                  float angleDegrees);
    }

    private interface CalibrationKeySelectListener {
        void onKeySelected(Consonant consonant, boolean cycleDirection);
    }

    private class CalibrationAngleEditorView extends View {
        private final Paint anglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF circleBounds = new RectF();
        private final Path handlePath = new Path();
        private final ManualAngleChangeListener angleChangeListener;
        private final float fallbackShortMm;
        private final float fallbackLongMm;
        private GestureCalibration.Profile profile;
        private CalibrationKeyboardPickerView keyboardPickerView;
        private Consonant selectedConsonant;
        private GestureCalibration.DirectionClass selectedDirection = GestureCalibration.DirectionClass.TOP_RIGHT;
        private float centerX;
        private float centerY;
        private float radius;
        private boolean draggingHandle;

        CalibrationAngleEditorView(Context context, GestureCalibration.Profile profile, Consonant selectedConsonant,
                                   float fallbackShortMm, float fallbackLongMm,
                                   ManualAngleChangeListener angleChangeListener) {
            super(context);
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            this.selectedConsonant = selectedConsonant;
            this.fallbackShortMm = fallbackShortMm;
            this.fallbackLongMm = fallbackLongMm;
            this.angleChangeListener = angleChangeListener;
        }

        void setKeyboardPickerView(CalibrationKeyboardPickerView keyboardPickerView) {
            this.keyboardPickerView = keyboardPickerView;
        }

        void setProfile(GestureCalibration.Profile profile) {
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            if (keyboardPickerView != null) {
                keyboardPickerView.setProfile(this.profile);
            }
            invalidate();
        }

        void setSelectedConsonant(Consonant consonant) {
            if (consonant == null) {
                return;
            }
            selectedConsonant = consonant;
            if (keyboardPickerView != null) {
                keyboardPickerView.setSelection(selectedConsonant, selectedDirection);
            }
            invalidate();
        }

        void selectNextDirection() {
            int index = Arrays.asList(EDITABLE_DIAGONALS).indexOf(selectedDirection);
            selectedDirection = EDITABLE_DIAGONALS[(index + 1 + EDITABLE_DIAGONALS.length) % EDITABLE_DIAGONALS.length];
            if (keyboardPickerView != null) {
                keyboardPickerView.setSelection(selectedConsonant, selectedDirection);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            cardRect.set(0, 0, getWidth(), getHeight());
            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(Color.WHITE);
            canvas.drawRoundRect(cardRect, dp(24), dp(24), anglePaint);

            centerX = getWidth() / 2f;
            centerY = dp(166);
            radius = Math.min(getWidth() * 0.36f, dp(96));
            circleBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

            drawAngleHeader(canvas);
            drawAngleCircle(canvas);
            drawDirectionLines(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    GestureCalibration.DirectionClass hitDirection = hitDirection(event.getX(), event.getY());
                    if (hitDirection != null) {
                        selectedDirection = hitDirection;
                        draggingHandle = true;
                        updateAngleFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingHandle) {
                        updateAngleFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    draggingHandle = false;
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        private void drawAngleHeader(Canvas canvas) {
            anglePaint.setShader(null);
            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setTextAlign(Paint.Align.LEFT);
            anglePaint.setTypeface(Typeface.DEFAULT_BOLD);
            anglePaint.setTextSize(dp(18));
            anglePaint.setColor(TEXT_PRIMARY);
            canvas.drawText(selectedConsonant.label() + " 키", dp(20), dp(32), anglePaint);

            anglePaint.setTypeface(Typeface.DEFAULT);
            anglePaint.setTextSize(dp(13));
            anglePaint.setColor(TEXT_SECONDARY);
            int samples = profile == null ? 0 : profile.consonantSamples(selectedConsonant);
            canvas.drawText("샘플 " + samples + "개 · " + directionName(selectedDirection)
                    + " " + Math.round(angleFor(selectedDirection)) + "°", dp(20), dp(55), anglePaint);
        }

        private void drawAngleCircle(Canvas canvas) {
            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(Color.rgb(246, 248, 251));
            canvas.drawCircle(centerX, centerY, radius, anglePaint);

            anglePaint.setStyle(Paint.Style.STROKE);
            anglePaint.setStrokeWidth(dp(1));
            anglePaint.setColor(Color.rgb(226, 230, 236));
            canvas.drawCircle(centerX, centerY, radius, anglePaint);
            canvas.drawCircle(centerX, centerY, radius * 0.58f, anglePaint);
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, anglePaint);
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, anglePaint);

            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(Color.rgb(138, 145, 155));
            anglePaint.setTextSize(dp(11));
            anglePaint.setTextAlign(Paint.Align.CENTER);
            drawAngleText(canvas, "0°", centerX + radius + dp(18), centerY);
            drawAngleText(canvas, "-90°", centerX, centerY - radius - dp(14));
            drawAngleText(canvas, "90°", centerX, centerY + radius + dp(14));
        }

        private void drawDirectionLines(Canvas canvas) {
            for (GestureCalibration.DirectionClass directionClass : EDITABLE_DIAGONALS) {
                boolean selected = directionClass == selectedDirection;
                float angle = angleFor(directionClass);
                float startRadius = radius * 0.34f;
                float endRadius = radius * (selected ? 1.13f : 1.03f);
                float startX = pointX(angle, startRadius);
                float startY = pointY(angle, startRadius);
                float endX = pointX(angle, endRadius);
                float endY = pointY(angle, endRadius);

                anglePaint.setStyle(Paint.Style.STROKE);
                anglePaint.setStrokeWidth(selected ? dp(4) : dp(2));
                anglePaint.setStrokeCap(Paint.Cap.ROUND);
                anglePaint.setColor(selected ? Color.rgb(32, 118, 255) : Color.rgb(94, 151, 225));
                canvas.drawLine(startX, startY, endX, endY, anglePaint);
                anglePaint.setStrokeCap(Paint.Cap.BUTT);

                drawPointer(canvas, angle, endX, endY, selected);

                anglePaint.setStyle(Paint.Style.FILL);
                anglePaint.setTextSize(dp(11));
                anglePaint.setTextAlign(Paint.Align.CENTER);
                anglePaint.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                anglePaint.setColor(selected ? TEXT_PRIMARY : TEXT_SECONDARY);
                drawAngleText(canvas, shortDirectionName(directionClass),
                        pointX(angle, radius * 1.34f), pointY(angle, radius * 1.34f));
                anglePaint.setTypeface(Typeface.DEFAULT);
            }
        }

        private void drawAngleText(Canvas canvas, String text, float x, float centerY) {
            Paint.FontMetrics metrics = anglePaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, anglePaint);
        }

        private void drawPointer(Canvas canvas, float angleDegrees, float x, float y, boolean selected) {
            float angle = (float) Math.toRadians(angleDegrees);
            float size = selected ? dp(11) : dp(8);
            float backX = x - size * (float) Math.cos(angle);
            float backY = y - size * (float) Math.sin(angle);
            float sideAngle = angle + (float) Math.PI / 2f;

            handlePath.reset();
            handlePath.moveTo(x + size * 0.46f * (float) Math.cos(angle),
                    y + size * 0.46f * (float) Math.sin(angle));
            handlePath.lineTo(backX + size * 0.56f * (float) Math.cos(sideAngle),
                    backY + size * 0.56f * (float) Math.sin(sideAngle));
            handlePath.lineTo(backX - size * 0.56f * (float) Math.cos(sideAngle),
                    backY - size * 0.56f * (float) Math.sin(sideAngle));
            handlePath.close();

            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(selected ? Color.rgb(32, 118, 255) : Color.rgb(120, 166, 224));
            canvas.drawPath(handlePath, anglePaint);
        }

        private GestureCalibration.DirectionClass hitDirection(float x, float y) {
            GestureCalibration.DirectionClass nearest = null;
            float nearestDistance = Float.MAX_VALUE;
            for (GestureCalibration.DirectionClass directionClass : EDITABLE_DIAGONALS) {
                float angle = angleFor(directionClass);
                float endX = pointX(angle, radius * 1.1f);
                float endY = pointY(angle, radius * 1.1f);
                float distance = (float) Math.hypot(x - endX, y - endY);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = directionClass;
                }
            }
            if (nearestDistance <= dp(34)) {
                return nearest;
            }
            if (Math.hypot(x - centerX, y - centerY) <= radius * 1.22f) {
                return nearestDirectionForAngle((float) Math.toDegrees(Math.atan2(y - centerY, x - centerX)));
            }
            return null;
        }

        private GestureCalibration.DirectionClass nearestDirectionForAngle(float angle) {
            GestureCalibration.DirectionClass nearest = selectedDirection;
            float nearestDiff = Float.MAX_VALUE;
            for (GestureCalibration.DirectionClass directionClass : EDITABLE_DIAGONALS) {
                float diff = Math.abs(GestureCalibration.angleDiff(angle, defaultAngleFor(directionClass)));
                if (diff < nearestDiff) {
                    nearestDiff = diff;
                    nearest = directionClass;
                }
            }
            return nearest;
        }

        private void updateAngleFromTouch(float x, float y) {
            float angle = (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
            angle = clampAngleFor(selectedDirection, angle);
            if (profile == null) {
                profile = new GestureCalibration.Profile();
            }
            profile.setDirectionAngle(selectedConsonant, selectedDirection, angle,
                    fallbackShortMm, fallbackLongMm);
            if (angleChangeListener != null) {
                angleChangeListener.onManualAngleChanged(selectedConsonant, selectedDirection, angle);
            }
            if (keyboardPickerView != null) {
                keyboardPickerView.setProfile(profile);
                keyboardPickerView.setSelection(selectedConsonant, selectedDirection);
                keyboardPickerView.invalidate();
            }
            invalidate();
        }

        private float angleFor(GestureCalibration.DirectionClass directionClass) {
            GestureCalibration.DirectionProfile directionProfile =
                    profile == null ? null : profile.directionProfile(selectedConsonant, directionClass);
            return directionProfile == null ? defaultAngleFor(directionClass) : directionProfile.centerAngle;
        }

        private float pointX(float angleDegrees, float pointRadius) {
            return centerX + pointRadius * (float) Math.cos(Math.toRadians(angleDegrees));
        }

        private float pointY(float angleDegrees, float pointRadius) {
            return centerY + pointRadius * (float) Math.sin(Math.toRadians(angleDegrees));
        }
    }

    private class CalibrationKeyboardPickerView extends View {
        private final Paint keyboardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<CalibrationKeyCell> cells = new java.util.ArrayList<>();
        private final CalibrationKeySelectListener keySelectListener;
        private GestureCalibration.Profile profile;
        private Consonant selectedConsonant;
        private GestureCalibration.DirectionClass selectedDirection;

        CalibrationKeyboardPickerView(Context context, GestureCalibration.Profile profile, Consonant selectedConsonant,
                                      GestureCalibration.DirectionClass selectedDirection,
                                      CalibrationKeySelectListener keySelectListener) {
            super(context);
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            this.selectedConsonant = selectedConsonant;
            this.selectedDirection = selectedDirection;
            this.keySelectListener = keySelectListener;
        }

        void setProfile(GestureCalibration.Profile profile) {
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            invalidate();
        }

        void setSelection(Consonant consonant, GestureCalibration.DirectionClass directionClass) {
            selectedConsonant = consonant;
            selectedDirection = directionClass;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            buildCells();
            keyboardPaint.setStyle(Paint.Style.FILL);
            keyboardPaint.setColor(Color.rgb(222, 229, 233));
            canvas.drawRoundRect(new RectF(0, 0, getWidth(), getHeight()), dp(10), dp(10), keyboardPaint);

            for (CalibrationKeyCell cell : cells) {
                drawCalibrationKey(canvas, cell);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) {
                return true;
            }
            for (CalibrationKeyCell cell : cells) {
                if (cell.consonant != null && cell.rect.contains(event.getX(), event.getY())) {
                    boolean cycleDirection = cell.consonant == selectedConsonant;
                    selectedConsonant = cell.consonant;
                    if (keySelectListener != null) {
                        keySelectListener.onKeySelected(cell.consonant, cycleDirection);
                    }
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        private void buildCells() {
            cells.clear();
            float gap = dp(5);
            float top = dp(10);
            float rowHeight = (getHeight() - gap * 5f - dp(20)) / 4f;
            float[] weights = {1.05f, 1f, 1f, 1f, 1f, 1f, 1.45f};
            Object[][] rows = {
                    {"Abc", Consonant.KIEUK, Consonant.GIYEOK, Consonant.SIOT, Consonant.JIEUT, Consonant.CHIEUT, "DEL"},
                    {"#★♪", Consonant.HIEUT, Consonant.NIEUN, Consonant.IEUNG, Consonant.RIEUL, Consonant.MIEUM, ""},
                    {"123", Consonant.TIEUT, Consonant.DIGEUT, Consonant.BIEUP, Consonant.PIEUP, "모음", ""},
                    {"⚙", "←", "→", "space", "space", "Go", ""}
            };

            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                float left = gap;
                float totalWeight = 0f;
                for (float weight : weights) {
                    totalWeight += weight;
                }
                float availableWidth = getWidth() - gap * (weights.length + 1);
                for (int column = 0; column < rows[rowIndex].length; column++) {
                    float width = availableWidth * (weights[column] / totalWeight);
                    RectF rect = new RectF(left, top, left + width, top + rowHeight);
                    Object value = rows[rowIndex][column];
                    if (value instanceof Consonant) {
                        Consonant consonant = (Consonant) value;
                        cells.add(new CalibrationKeyCell(rect, consonant.label(), consonant));
                    } else {
                        cells.add(new CalibrationKeyCell(rect, String.valueOf(value), null));
                    }
                    left += width + gap;
                }
                top += rowHeight + gap;
            }
        }

        private void drawCalibrationKey(Canvas canvas, CalibrationKeyCell cell) {
            boolean selectable = cell.consonant != null;
            boolean selected = selectable && cell.consonant == selectedConsonant;
            boolean calibrated = selectable && hasAnyDiagonal(cell.consonant);
            int keyColor = selectable ? Color.rgb(249, 251, 252) : Color.rgb(204, 214, 220);
            if (selected) {
                keyColor = Color.rgb(232, 241, 255);
            }

            keyboardPaint.setStyle(Paint.Style.FILL);
            keyboardPaint.setColor(keyColor);
            canvas.drawRoundRect(cell.rect, dp(8), dp(8), keyboardPaint);
            keyboardPaint.setStyle(Paint.Style.STROKE);
            keyboardPaint.setStrokeWidth(selected ? dp(2.2f) : Math.max(1f, dp(0.6f)));
            keyboardPaint.setColor(selected ? Color.rgb(32, 118, 255) : Color.rgb(191, 200, 207));
            canvas.drawRoundRect(cell.rect, dp(8), dp(8), keyboardPaint);

            if (calibrated) {
                keyboardPaint.setStyle(Paint.Style.FILL);
                keyboardPaint.setColor(Color.rgb(38, 197, 167));
                canvas.drawCircle(cell.rect.right - dp(10), cell.rect.top + dp(10), dp(3.5f), keyboardPaint);
            }

            keyboardPaint.setStyle(Paint.Style.FILL);
            keyboardPaint.setTextAlign(Paint.Align.CENTER);
            keyboardPaint.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            keyboardPaint.setTextSize(dp(selectable ? 24 : 14));
            keyboardPaint.setColor(selectable ? TEXT_PRIMARY : Color.rgb(112, 122, 130));
            drawKeyboardText(canvas, cell.label, cell.rect.centerX(), cell.rect.centerY());
            keyboardPaint.setTypeface(Typeface.DEFAULT);
        }

        private boolean hasAnyDiagonal(Consonant consonant) {
            if (profile == null) {
                return false;
            }
            for (GestureCalibration.DirectionClass directionClass : EDITABLE_DIAGONALS) {
                if (profile.directionProfile(consonant, directionClass) != null) {
                    return true;
                }
            }
            return false;
        }

        private void drawKeyboardText(Canvas canvas, String text, float x, float centerY) {
            Paint.FontMetrics metrics = keyboardPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, keyboardPaint);
        }
    }

    private static class CalibrationKeyCell {
        final RectF rect;
        final String label;
        final Consonant consonant;

        CalibrationKeyCell(RectF rect, String label, Consonant consonant) {
            this.rect = rect;
            this.label = label;
            this.consonant = consonant;
        }
    }

    private float defaultAngleFor(GestureCalibration.DirectionClass directionClass) {
        switch (directionClass) {
            case TOP_RIGHT:
                return -45f;
            case TOP_LEFT:
                return -135f;
            case BOTTOM_RIGHT:
                return 45f;
            case BOTTOM_LEFT:
                return 135f;
            default:
                return 0f;
        }
    }

    private float clampAngleFor(GestureCalibration.DirectionClass directionClass, float angle) {
        switch (directionClass) {
            case TOP_RIGHT:
                return Math.max(-82f, Math.min(angle, -8f));
            case TOP_LEFT:
                return Math.max(-172f, Math.min(angle, -98f));
            case BOTTOM_RIGHT:
                return Math.max(8f, Math.min(angle, 82f));
            case BOTTOM_LEFT:
                return Math.max(98f, Math.min(angle, 172f));
            default:
                return angle;
        }
    }

    private String directionName(GestureCalibration.DirectionClass directionClass) {
        switch (directionClass) {
            case TOP_RIGHT:
                return "우상";
            case TOP_LEFT:
                return "좌상";
            case BOTTOM_RIGHT:
                return "우하";
            case BOTTOM_LEFT:
                return "좌하";
            default:
                return "";
        }
    }

    private String shortDirectionName(GestureCalibration.DirectionClass directionClass) {
        switch (directionClass) {
            case TOP_RIGHT:
                return "TR";
            case TOP_LEFT:
                return "TL";
            case BOTTOM_RIGHT:
                return "BR";
            case BOTTOM_LEFT:
                return "BL";
            default:
                return "";
        }
    }

    private class KeyboardSizePreviewView extends FrameLayout {
        private static final int MODE_POSITION = 0;
        private static final int MODE_LAYOUT = 1;
        private static final int HANDLE_NONE = 0;
        private static final int HANDLE_LEFT = 1;
        private static final int HANDLE_RIGHT = 2;
        private static final int HANDLE_TOP = 3;
        private static final int HANDLE_BOTTOM = 4;
        private static final int HANDLE_TOP_LEFT = 5;
        private static final int HANDLE_TOP_RIGHT = 6;
        private static final int HANDLE_BOTTOM_LEFT = 7;
        private static final int HANDLE_BOTTOM_RIGHT = 8;
        private static final int HANDLE_LAYOUT_LEFT = 9;
        private static final int HANDLE_LAYOUT_RIGHT = 10;
        private static final int BUTTON_NONE = 0;
        private static final int BUTTON_RESET = 1;
        private static final int BUTTON_DONE = 2;

        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF keyboardRect = new RectF();
        private final RectF keyboardChildRect = new RectF();
        private final RectF resetRect = new RectF();
        private final RectF doneRect = new RectF();
        private final Runnable doneAction;
        private final KeyboardSurfaceView keyboardView;
        private int leftPercent;
        private int rightPercent;
        private int topPercent;
        private int bottomPercent;
        private int layoutLeftPercent;
        private int layoutRightPercent;
        private int adjustmentMode = MODE_POSITION;
        private int activeHandle = HANDLE_NONE;
        private int activeButton = BUTTON_NONE;

        KeyboardSizePreviewView(Context context, Runnable doneAction) {
            super(context);
            this.doneAction = doneAction;
            setWillNotDraw(false);
            setClipChildren(false);
            reloadSize();
            keyboardView = new KeyboardSurfaceView(context);
            keyboardView.setMode(KeyboardMode.HANGUL);
            keyboardView.setSettings(previewKeyboardSettings());
            keyboardView.setBottomSafeInsetEnabled(false);
            keyboardView.setEnabled(false);
            keyboardView.setClickable(false);
            addView(keyboardView, new FrameLayout.LayoutParams(1, 1));
        }

        void setAdjustmentMode(int adjustmentMode) {
            this.adjustmentMode = adjustmentMode;
            activeButton = BUTTON_NONE;
            activeHandle = HANDLE_NONE;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
            computeKeyboardRect(previewBounds(width, height));
            keyboardView.measure(
                    MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(keyboardChildRect.width())), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(keyboardChildRect.height())), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            computeKeyboardRect(previewBounds());
            keyboardView.layout(Math.round(keyboardChildRect.left), Math.round(keyboardChildRect.top),
                    Math.round(keyboardChildRect.right), Math.round(keyboardChildRect.bottom));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(PAGE_BACKGROUND);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            computeKeyboardRect(previewBounds());
            super.dispatchDraw(canvas);
            drawResizeChrome(canvas);
            drawPreviewButtons(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    computeKeyboardRect(previewBounds());
                    activeButton = buttonAt(event.getX(), event.getY());
                    activeHandle = activeButton == BUTTON_NONE ? currentModeHandleAt(event.getX(), event.getY()) : HANDLE_NONE;
                    if (activeButton != BUTTON_NONE || activeHandle != HANDLE_NONE) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (activeHandle != HANDLE_NONE) {
                        if (adjustmentMode == MODE_LAYOUT) {
                            updateLayoutFromHandle(event.getX());
                        } else {
                            updateSizeFromHandle(event.getX(), event.getY());
                        }
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (activeButton == BUTTON_RESET && resetRect.contains(event.getX(), event.getY())) {
                        resetSize();
                    } else if (activeButton == BUTTON_DONE && doneRect.contains(event.getX(), event.getY())) {
                        doneAction.run();
                    }
                    activeButton = BUTTON_NONE;
                    activeHandle = HANDLE_NONE;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    activeButton = BUTTON_NONE;
                    activeHandle = HANDLE_NONE;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        private void reloadSize() {
            SettingsStore.Snapshot snapshot = SettingsStore.load(MainActivity.this);
            leftPercent = snapshot.keyboardLeftPercent;
            rightPercent = snapshot.keyboardRightPercent;
            topPercent = snapshot.keyboardTopPercent;
            bottomPercent = snapshot.keyboardBottomPercent;
            layoutLeftPercent = snapshot.keyboardLayoutLeftPercent;
            layoutRightPercent = snapshot.keyboardLayoutRightPercent;
        }

        private void saveSize() {
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_LEFT_PERCENT, leftPercent);
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_RIGHT_PERCENT, rightPercent);
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_TOP_PERCENT, topPercent);
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_BOTTOM_PERCENT, bottomPercent);
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_LAYOUT_LEFT_PERCENT, layoutLeftPercent);
            SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_KEYBOARD_LAYOUT_RIGHT_PERCENT, layoutRightPercent);
            keyboardView.setSettings(previewKeyboardSettings());
        }

        private void resetSize() {
            if (adjustmentMode == MODE_LAYOUT) {
                layoutLeftPercent = SettingsStore.DEFAULT_KEYBOARD_LAYOUT_LEFT_PERCENT;
                layoutRightPercent = SettingsStore.DEFAULT_KEYBOARD_LAYOUT_RIGHT_PERCENT;
            } else {
                leftPercent = SettingsStore.DEFAULT_KEYBOARD_LEFT_PERCENT;
                rightPercent = SettingsStore.DEFAULT_KEYBOARD_RIGHT_PERCENT;
                topPercent = SettingsStore.DEFAULT_KEYBOARD_TOP_PERCENT;
                bottomPercent = SettingsStore.DEFAULT_KEYBOARD_BOTTOM_PERCENT;
            }
            saveSize();
            requestLayout();
            invalidate();
        }

        private SettingsStore.Snapshot previewKeyboardSettings() {
            SettingsStore.Snapshot snapshot = SettingsStore.load(MainActivity.this);
            snapshot.keyboardLeftPercent = SettingsStore.DEFAULT_KEYBOARD_LEFT_PERCENT;
            snapshot.keyboardRightPercent = SettingsStore.DEFAULT_KEYBOARD_RIGHT_PERCENT;
            snapshot.keyboardTopPercent = SettingsStore.DEFAULT_KEYBOARD_TOP_PERCENT;
            snapshot.keyboardBottomPercent = SettingsStore.DEFAULT_KEYBOARD_BOTTOM_PERCENT;
            snapshot.keyboardLayoutLeftPercent = layoutLeftPercent;
            snapshot.keyboardLayoutRightPercent = layoutRightPercent;
            return snapshot;
        }

        private RectF previewBounds() {
            return previewBounds(getWidth(), getHeight());
        }

        private RectF previewBounds(int width, int height) {
            return new RectF(dp(16), dp(6), width - dp(16), height - dp(92));
        }

        private void computeKeyboardRect(RectF bounds) {
            keyboardRect.set(
                    bounds.left + bounds.width() * leftPercent / 100f,
                    bounds.top + bounds.height() * topPercent / 100f,
                    bounds.left + bounds.width() * rightPercent / 100f,
                    bounds.top + bounds.height() * bottomPercent / 100f);
            keyboardChildRect.set(keyboardRect);
        }

        private void drawResizeChrome(Canvas canvas) {
            int accent = Color.rgb(20, 145, 245);
            RectF bounds = previewBounds();

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(1.4f));
            previewPaint.setColor(Color.rgb(176, 184, 192));
            canvas.drawRect(bounds, previewPaint);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(Color.argb(28, 20, 145, 245));
            canvas.drawRect(new RectF(bounds.left, bounds.top, keyboardRect.left, bounds.bottom), previewPaint);
            canvas.drawRect(new RectF(keyboardRect.right, bounds.top, bounds.right, bounds.bottom), previewPaint);
            canvas.drawRect(new RectF(keyboardRect.left, bounds.top, keyboardRect.right, keyboardRect.top), previewPaint);
            canvas.drawRect(new RectF(keyboardRect.left, keyboardRect.bottom, keyboardRect.right, bounds.bottom), previewPaint);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(1));
            previewPaint.setColor(Color.argb(125, 20, 145, 245));
            canvas.drawLine(bounds.left, keyboardRect.top, bounds.right, keyboardRect.top, previewPaint);
            canvas.drawLine(bounds.left, keyboardRect.bottom, bounds.right, keyboardRect.bottom, previewPaint);
            canvas.drawLine(keyboardRect.left, bounds.top, keyboardRect.left, bounds.bottom, previewPaint);
            canvas.drawLine(keyboardRect.right, bounds.top, keyboardRect.right, bounds.bottom, previewPaint);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(2));
            previewPaint.setColor(accent);
            canvas.drawRect(keyboardRect, previewPaint);
            previewPaint.setStyle(Paint.Style.FILL);
            drawHandle(canvas, keyboardRect.centerX(), keyboardRect.top);
            drawHandle(canvas, keyboardRect.centerX(), keyboardRect.bottom);
            drawHandle(canvas, keyboardRect.left, keyboardRect.centerY());
            drawHandle(canvas, keyboardRect.right, keyboardRect.centerY());

            drawEdgePercentLabels(canvas, bounds);
        }

        private void drawEdgePercentLabels(Canvas canvas, RectF bounds) {
            drawPercentLabel(canvas, percentLabel("상", topPercent),
                    keyboardRect.centerX(), keyboardRect.top + dp(18), Gravity.CENTER);
            drawPercentLabel(canvas, percentLabel("하", 100 - bottomPercent),
                    keyboardRect.centerX(), keyboardRect.bottom + dp(18), Gravity.CENTER);
            drawPercentLabel(canvas, percentLabel("좌", leftPercent),
                    keyboardRect.left, keyboardRect.centerY() + dp(18), Gravity.CENTER);
            drawPercentLabel(canvas, percentLabel("우", 100 - rightPercent),
                    keyboardRect.right, keyboardRect.centerY() + dp(18), Gravity.CENTER);
        }

        private String percentLabel(String edge, int percent) {
            return edge + " " + percent + "%";
        }

        private void drawPercentLabel(Canvas canvas, String label, float centerX, float centerY, int gravity) {
            previewPaint.setTextSize(dp(11));
            previewPaint.setTextAlign(Paint.Align.CENTER);
            float textWidth = previewPaint.measureText(label);
            float x = Math.max(textWidth / 2f + dp(2),
                    Math.min(centerX, getWidth() - textWidth / 2f - dp(2)));
            float y = Math.max(dp(10), Math.min(centerY, getHeight() - dp(10)));
            previewPaint.setFakeBoldText(true);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(2));
            previewPaint.setColor(Color.argb(210, 255, 255, 255));
            drawPreviewText(canvas, label, x, y);
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setStrokeWidth(1f);
            previewPaint.setColor(Color.rgb(20, 112, 205));
            drawPreviewText(canvas, label, x, y);
            previewPaint.setFakeBoldText(false);
        }

        private void drawPreviewButtons(Canvas canvas) {
            RectF bounds = previewBounds();
            float buttonHeight = dp(36);
            float buttonTop = Math.min(bounds.bottom + dp(34), getHeight() - buttonHeight - dp(10));
            float buttonWidth = Math.min(dp(124), bounds.width() * 0.35f);
            float gap = dp(18);
            float centerX = bounds.centerX();
            resetRect.set(centerX - buttonWidth - gap / 2f, buttonTop,
                    centerX - gap / 2f, buttonTop + buttonHeight);
            doneRect.set(centerX + gap / 2f, buttonTop,
                    centerX + buttonWidth + gap / 2f, buttonTop + buttonHeight);
            drawButton(canvas, resetRect, "초기화", activeButton == BUTTON_RESET);
            drawButton(canvas, doneRect, "완료", activeButton == BUTTON_DONE);
        }

        private void drawButton(Canvas canvas, RectF rect, String label, boolean pressed) {
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(pressed ? Color.rgb(220, 222, 226) : Color.rgb(238, 239, 241));
            canvas.drawRoundRect(rect, dp(18), dp(18), previewPaint);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(1));
            previewPaint.setColor(Color.rgb(196, 199, 204));
            canvas.drawRoundRect(rect, dp(18), dp(18), previewPaint);
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setTextAlign(Paint.Align.CENTER);
            previewPaint.setTextSize(dp(15));
            previewPaint.setColor(TEXT_PRIMARY);
            drawPreviewText(canvas, label, rect.centerX(), rect.centerY());
        }

        private void drawHandle(Canvas canvas, float centerX, float centerY) {
            previewPaint.setColor(Color.rgb(20, 145, 245));
            canvas.drawRoundRect(new RectF(centerX - dp(16), centerY - dp(5),
                    centerX + dp(16), centerY + dp(5)), dp(5), dp(5), previewPaint);
        }

        private void drawPreviewText(Canvas canvas, String text, float x, float centerY) {
            Paint.FontMetrics metrics = previewPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, previewPaint);
        }

        private int buttonAt(float x, float y) {
            if (resetRect.contains(x, y)) {
                return BUTTON_RESET;
            }
            if (doneRect.contains(x, y)) {
                return BUTTON_DONE;
            }
            return BUTTON_NONE;
        }

        private int handleAt(float x, float y) {
            float hit = dp(32);
            if (distance(x, y, keyboardRect.left, keyboardRect.top) <= hit) {
                return HANDLE_TOP_LEFT;
            }
            if (distance(x, y, keyboardRect.right, keyboardRect.top) <= hit) {
                return HANDLE_TOP_RIGHT;
            }
            if (distance(x, y, keyboardRect.left, keyboardRect.bottom) <= hit) {
                return HANDLE_BOTTOM_LEFT;
            }
            if (distance(x, y, keyboardRect.right, keyboardRect.bottom) <= hit) {
                return HANDLE_BOTTOM_RIGHT;
            }
            if (Math.abs(y - keyboardRect.top) <= hit && x >= keyboardRect.left && x <= keyboardRect.right) {
                return HANDLE_TOP;
            }
            if (Math.abs(y - keyboardRect.bottom) <= hit && x >= keyboardRect.left && x <= keyboardRect.right) {
                return HANDLE_BOTTOM;
            }
            if (Math.abs(x - keyboardRect.left) <= hit && y >= keyboardRect.top && y <= keyboardRect.bottom) {
                return HANDLE_LEFT;
            }
            if (Math.abs(x - keyboardRect.right) <= hit && y >= keyboardRect.top && y <= keyboardRect.bottom) {
                return HANDLE_RIGHT;
            }
            return HANDLE_NONE;
        }

        private void updateSizeFromHandle(float x, float y) {
            RectF bounds = previewBounds();
            int xPercent = percentForPosition(x, bounds.left, bounds.width());
            int yPercent = percentForPosition(y, bounds.top, bounds.height());
            if (activeHandle == HANDLE_LEFT || activeHandle == HANDLE_TOP_LEFT
                    || activeHandle == HANDLE_BOTTOM_LEFT) {
                leftPercent = SettingsStore.boundedKeyboardLeftPercent(xPercent, rightPercent);
            }
            if (activeHandle == HANDLE_RIGHT || activeHandle == HANDLE_TOP_RIGHT
                    || activeHandle == HANDLE_BOTTOM_RIGHT) {
                rightPercent = SettingsStore.boundedKeyboardRightPercent(xPercent, leftPercent);
            }
            if (activeHandle == HANDLE_TOP || activeHandle == HANDLE_TOP_LEFT
                    || activeHandle == HANDLE_TOP_RIGHT) {
                topPercent = SettingsStore.boundedKeyboardTopPercent(yPercent, bottomPercent);
            }
            if (activeHandle == HANDLE_BOTTOM || activeHandle == HANDLE_BOTTOM_LEFT
                    || activeHandle == HANDLE_BOTTOM_RIGHT) {
                bottomPercent = SettingsStore.boundedKeyboardBottomPercent(yPercent, topPercent);
            }
            saveSize();
            requestLayout();
            invalidate();
        }

        private int percentForPosition(float value, float start, float size) {
            int percent = Math.round(((value - start) / size) * 100f);
            return Math.max(0, Math.min(100, percent));
        }

        private float distance(float x1, float y1, float x2, float y2) {
            float dx = x1 - x2;
            float dy = y1 - y2;
            return (float) Math.hypot(dx, dy);
        }
    }
}
