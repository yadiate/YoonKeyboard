package com.yadiate.yoonkeyboard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.ViewParent;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.yadiate.yoonkeyboard.hangul.Consonant;
import com.yadiate.yoonkeyboard.hangul.GestureCalibration;
import com.yadiate.yoonkeyboard.hangul.CalibrationPlanner;

import java.io.File;
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

    private SharedPreferences prefs;
    private LinearLayout root;
    private Runnable backAction;
    private File pendingUpdateApk;
    private GitHubReleaseUpdater.UpdateCandidate pendingUpdateCandidate;
    private boolean updateInProgress;
    private View updateDownloadRow;
    private TextView updateDownloadTitle;
    private TextView updateDownloadSummary;
    private TextView updateDownloadArrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = SettingsStore.prefs(this);
        applySystemBars();
        buildSettingsScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUpdateApk != null && GitHubReleaseUpdater.canRequestPackageInstalls(this)) {
            File apkFile = pendingUpdateApk;
            pendingUpdateApk = null;
            beginUpdateInstall(apkFile);
        }
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
        addUpdateSection();
        addBackupSection();
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

    private void showFoldModePage() {
        showChoicePage("\uD3F4\uB4DC \uBAA8\uB4DC",
                SettingsStore.FOLD_KEYBOARD_MODES,
                SettingsStore.KEY_FOLD_KEYBOARD_MODE,
                SettingsStore.FOLD_KEYBOARD_MODE_SPLIT,
                () -> showMainPage(true, -1));
    }

    private void showStrokeLengthPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("획 길이", parentPage);

        addSectionTitle("사용자 정의");
        LinearLayout customCard = addCard();
        SettingsStore.putBoolean(this, SettingsStore.KEY_STROKE_CUSTOM, true);

        SettingsStore.Snapshot strokeSnapshot = SettingsStore.load(this);
        int strokeIndex = strokeSnapshot.strokeLengthIndex;
        int defaultShortStroke = SettingsStore.defaultShortStrokeMmTenths(strokeIndex);
        int defaultDerivationShortStroke = SettingsStore.defaultDerivationShortStrokeMmTenths(strokeIndex);
        int defaultLongStroke = SettingsStore.defaultLongStrokeMmTenths(strokeIndex);
        int currentLongStroke = strokeSnapshot.longStrokeMmTenths;
        int currentShortStroke = strokeSnapshot.shortStrokeMmTenths;
        int currentDerivationShortStroke = strokeSnapshot.derivationShortStrokeMmTenths;
        SettingsStore.putInt(this, SettingsStore.KEY_STROKE_LONG_MM_TENTHS, currentLongStroke);
        SettingsStore.putInt(this, SettingsStore.KEY_STROKE_SHORT_MM_TENTHS, currentShortStroke);
        SettingsStore.putInt(this, SettingsStore.KEY_STROKE_DERIVATION_SHORT_MM_TENTHS,
                currentDerivationShortStroke);

        final StrokeMmControl[] shortControl = new StrokeMmControl[1];
        final StrokeMmControl[] derivationShortControl = new StrokeMmControl[1];
        shortControl[0] = addStrokeMmRow(customCard, "첫 획 길이",
                "첫 드래그에서 ㅗ, ㅏ, ㅜ, ㅓ와 대각선을 잡는 최소 움직임입니다.",
                SettingsStore.KEY_STROKE_SHORT_MM_TENTHS,
                SettingsStore.MIN_CUSTOM_SHORT_MM_TENTHS,
                SettingsStore.maxCustomShortStrokeMmTenths(currentLongStroke),
                currentShortStroke,
                value -> {
                    int longStroke = SettingsStore.boundedCustomLongStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_LONG_MM_TENTHS, currentLongStroke),
                            defaultLongStroke);
                    int firstShortStroke = SettingsStore.boundedCustomShortStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_SHORT_MM_TENTHS, value),
                            longStroke,
                            defaultShortStroke);
                    int fallback = SettingsStore.defaultDerivationShortStrokeMmTenths(
                            defaultDerivationShortStroke, firstShortStroke, longStroke);
                    int derivationShortStroke = SettingsStore.boundedCustomDerivationShortStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, fallback),
                            firstShortStroke,
                            longStroke,
                            fallback);
                    if (derivationShortControl[0] != null) {
                        derivationShortControl[0].setRangeAndValue(
                                SettingsStore.minCustomDerivationShortStrokeMmTenths(firstShortStroke, longStroke),
                                SettingsStore.maxCustomShortStrokeMmTenths(longStroke),
                                derivationShortStroke);
                    }
                });
        addDivider(customCard);
        derivationShortControl[0] = addStrokeMmRow(customCard, "파생 획 길이",
                "2차 이후 꺾임과 복합 모음을 인정할 최소 움직임입니다.",
                SettingsStore.KEY_STROKE_DERIVATION_SHORT_MM_TENTHS,
                SettingsStore.minCustomDerivationShortStrokeMmTenths(currentShortStroke, currentLongStroke),
                SettingsStore.maxCustomShortStrokeMmTenths(currentLongStroke),
                currentDerivationShortStroke,
                null);
        addDivider(customCard);
        addStrokeMmRow(customCard, "긴 획 길이",
                "ㅡ, ㅣ 같은 긴 획으로 인정할 움직임입니다.",
                SettingsStore.KEY_STROKE_LONG_MM_TENTHS,
                SettingsStore.MIN_CUSTOM_LONG_MM_TENTHS,
                SettingsStore.MAX_CUSTOM_LONG_MM_TENTHS,
                currentLongStroke,
                value -> {
                    int longStroke = SettingsStore.boundedCustomLongStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_LONG_MM_TENTHS, value),
                            defaultLongStroke);
                    int shortStroke = SettingsStore.boundedCustomShortStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_SHORT_MM_TENTHS, defaultShortStroke),
                            longStroke,
                            defaultShortStroke);
                    int fallback = SettingsStore.defaultDerivationShortStrokeMmTenths(
                            defaultDerivationShortStroke, shortStroke, longStroke);
                    int derivationShortStroke = SettingsStore.boundedCustomDerivationShortStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, fallback),
                            shortStroke,
                            longStroke,
                            fallback);
                    if (shortControl[0] != null) {
                        shortControl[0].setRangeAndValue(
                                SettingsStore.MIN_CUSTOM_SHORT_MM_TENTHS,
                                SettingsStore.maxCustomShortStrokeMmTenths(longStroke),
                                shortStroke);
                    }
                    if (derivationShortControl[0] != null) {
                        derivationShortControl[0].setRangeAndValue(
                                SettingsStore.minCustomDerivationShortStrokeMmTenths(shortStroke, longStroke),
                                SettingsStore.maxCustomShortStrokeMmTenths(longStroke),
                                derivationShortStroke);
                    }
                });
        animatePageFrom(1);
    }

    private void showDeleteRepeatPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("Delete 반복", parentPage);

        addSectionTitle("누르고 있을 때");
        LinearLayout card = addCard();
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        addDeleteRepeatMsRow(card, "반복 시작시간",
                "누른 뒤 이 시간이 지나면 연속 삭제로 들어갑니다.",
                SettingsStore.KEY_DELETE_REPEAT_START_MS,
                SettingsStore.MIN_DELETE_REPEAT_START_MS,
                SettingsStore.MAX_DELETE_REPEAT_START_MS,
                SettingsStore.DELETE_REPEAT_START_STEP_MS,
                snapshot.deleteRepeatStartMs);
        addDivider(card);
        addDeleteRepeatMsRow(card, "삭제 속도",
                "값이 작을수록 누르고 있을 때 더 빠르게 지워집니다.",
                SettingsStore.KEY_DELETE_REPEAT_INTERVAL_MS,
                SettingsStore.MIN_DELETE_REPEAT_INTERVAL_MS,
                SettingsStore.MAX_DELETE_REPEAT_INTERVAL_MS,
                SettingsStore.DELETE_REPEAT_INTERVAL_STEP_MS,
                snapshot.deleteRepeatIntervalMs);
        animatePageFrom(1);
    }

    private void showDoubleConsonantPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("쌍자음", parentPage);

        addSectionTitle("대기시간");
        LinearLayout card = addCard();
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        addDoubleTapTimeoutMsRow(card, "쌍자음 입력 대기시간",
                "같은 자음을 이 시간 안에 다시 누를 때만 ㄲ, ㄸ, ㅃ, ㅆ, ㅉ로 바꿉니다.",
                snapshot.doubleTapTimeoutMs);
        animatePageFrom(1);
    }

    private void showTypoGuardPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("\uC624\uD0C0\uB9C9\uAE30", parentPage);

        addSectionTitle("\uBAA8\uC74C \uC81C\uC2A4\uCC98");
        LinearLayout card = addCard();
        addCheckRow(card,
                "\u3155 \uC704\uB85C\uD558\uBA74\u3156 \uB9C9\uAE30",
                "\u3155\uC5D0\uC11C \uC704\uB85C \uD68D\uC744 \uB354 \uADF8\uC5C8\uC744 \uB54C \u3156\uB85C \uBC14\uB00C\uB294 \uAC83\uC744 \uB9C9\uC2B5\uB2C8\uB2E4. \uC544\uB798\uB85C \uB0B4\uB824 \u3156\uB97C \uB9CC\uB4DC\uB294 \uB3D9\uC791\uC740 \uC720\uC9C0\uD569\uB2C8\uB2E4.",
                SettingsStore.KEY_BLOCK_YEO_UP_TO_YE,
                true);
        animatePageFrom(1);
    }

    private void showHitboxPage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("터치 판정", parentPage);

        addSectionTitle("미리보기");
        HitboxPreviewView previewView = new HitboxPreviewView(this);
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.setMargins(0, dp(2), 0, dp(8));
        root.addView(previewView, previewParams);
        TextView previewHint = rowSummary("파란 면이 실제 키이고, 빨간 선/면이 터치 인식 영역입니다.");
        previewHint.setPadding(dp(4), 0, dp(4), dp(8));
        root.addView(previewHint, matchWrap());

        addSectionTitle("히트박스");
        LinearLayout card = addCard();
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        addHitboxDpRow(card, "기본 여유",
                "모든 키의 오른쪽과 아래쪽으로 기본 판정을 넓힙니다.",
                SettingsStore.KEY_HITBOX_BASE_DP, snapshot.hitboxBaseDp, previewView);
        addDivider(card);
        addHitboxDpRow(card, "좌측 축소",
                "키의 왼쪽 판정을 안쪽으로 줄입니다.",
                SettingsStore.KEY_HITBOX_LEFT_CUT_DP, snapshot.hitboxLeftCutDp, previewView);
        addDivider(card);
        addHitboxDpRow(card, "상단 축소",
                "키의 위쪽 판정을 안쪽으로 줄입니다.",
                SettingsStore.KEY_HITBOX_TOP_CUT_DP, snapshot.hitboxTopCutDp, previewView);
        addDivider(card);
        addHitboxDpRow(card, "우측 추가",
                "키의 오른쪽 판정을 더 넓힙니다.",
                SettingsStore.KEY_HITBOX_RIGHT_EXTRA_DP, snapshot.hitboxRightExtraDp, previewView);
        addDivider(card);
        addHitboxDpRow(card, "하단 추가",
                "키의 아래쪽 판정을 더 넓힙니다.",
                SettingsStore.KEY_HITBOX_BOTTOM_EXTRA_DP, snapshot.hitboxBottomExtraDp, previewView);
        addDivider(card);
        addHitboxDpRow(card, "왼쪽 키 보정",
                "왼쪽 키들이 오른쪽 방향 터치를 더 가져가게 합니다.",
                SettingsStore.KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP, snapshot.hitboxLeftKeyRightExtraDp, previewView);

        addSectionTitle("초기화");
        LinearLayout resetCard = addCard();
        addActionRow(resetCard, "기본값 복원", hitboxSummary(), () -> {
            SettingsStore.resetHitbox(this);
            showHitboxPage();
        });
        animatePageFrom(1);
    }

    private void showKeyboardSizePage() {
        Runnable parentPage = () -> showMainPage(true, -1);
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("크기와 레이아웃", parentPage);

        KeyboardSizePreviewView preview = new KeyboardSizePreviewView(this, parentPage);

        addSectionTitle("조절 모드");
        LinearLayout modeCard = addCard();
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        TextView positionButton = sizeModeButton("위치 조절", true);
        TextView layoutButton = sizeModeButton("레이아웃 조절", false);
        modeRow.addView(positionButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeRow.addView(layoutButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modeCard.addView(modeRow, matchWrap());

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
        GradientDrawable background = rounded(selected ? Color.rgb(32, 118, 255) : Color.rgb(244, 246, 249), 12);
        background.setStroke(Math.max(1, dp(1)), selected ? Color.rgb(32, 118, 255) : Color.rgb(218, 223, 230));
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
                SettingsStore.KEY_GESTURE_CALIBRATION_ENABLED, true);
        addDivider(card);
        addActionRow(card, "보정 시작", "오타나는 글자를 넣으면 목표 글자와 간섭 글자를 자동으로 만듭니다.",
                this::showCalibrationTargetPage);
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
        String initialCalibrationKey = calibrationKeyForConsonant(initialConsonant, snapshot.hangulTypeIndex);
        CalibrationAngleEditorView angleView = new CalibrationAngleEditorView(this, editProfile[0],
                initialConsonant, initialCalibrationKey, fallbackShortMm, fallbackLongMm, null);
        CalibrationKeyboardPickerView keyboardPickerView = new CalibrationKeyboardPickerView(this, editProfile[0],
                initialConsonant, initialCalibrationKey, angleView.selectedDirection, snapshot.hangulTypeIndex,
                (consonant, calibrationKey, cycleDirection) -> {
            if (cycleDirection) {
                angleView.selectNextDirection();
            }
            angleView.setSelectedKey(consonant, calibrationKey);
        });
        angleView.setKeyboardPickerView(keyboardPickerView);

        LinearLayout.LayoutParams angleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(292));
        angleParams.setMargins(0, dp(2), 0, dp(10));
        root.addView(angleView, angleParams);

        LinearLayout defaultRow = new LinearLayout(this);
        defaultRow.setOrientation(LinearLayout.HORIZONTAL);
        defaultRow.setGravity(Gravity.CENTER);
        addCalibrationActionButton(defaultRow, "개별 미설정 기본값", true, false, () -> {
            angleView.setSelectedKey(null, "");
            keyboardPickerView.setSelection(null, "", angleView.selectedDirection);
        });
        LinearLayout.LayoutParams defaultParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        defaultParams.setMargins(0, 0, 0, dp(8));
        root.addView(defaultRow, defaultParams);

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

    private void showCalibrationTargetPage() {
        SettingsStore.endGestureCalibrationSession(this);
        hideSoftKeyboardFromCurrentFocus();
        Runnable parentPage = this::showCalibrationPage;
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("보정 시작", parentPage);

        addSectionTitle("오타나는 글자");
        LinearLayout card = addCard();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.addView(content, matchWrap());

        TextView description = rowSummary("예: 현이 자꾸 틀리면 현을 입력하세요. 앱이 현 10번, 혀 5번, 허 5번, 호 5번처럼 필요한 비교 글자를 자동으로 만듭니다.");
        description.setPadding(0, 0, 0, dp(12));
        content.addView(description, matchWrap());

        EditText targetInput = new EditText(this);
        targetInput.setTextSize(24);
        targetInput.setTextColor(TEXT_PRIMARY);
        targetInput.setHintTextColor(Color.rgb(170, 174, 182));
        targetInput.setHint("예: 현");
        targetInput.setSingleLine(true);
        targetInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        targetInput.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        targetInput.setMinHeight(dp(64));
        targetInput.setGravity(Gravity.CENTER_VERTICAL);
        targetInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        targetInput.setBackground(rounded(Color.rgb(247, 248, 250), 16));
        content.addView(targetInput, matchWrap());

        TextView planPreview = rowSummary("보정 계획이 여기에 표시됩니다.");
        planPreview.setPadding(0, dp(12), 0, 0);
        content.addView(planPreview, matchWrap());

        targetInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCalibrationPlanPreview(s.toString(), planPreview);
            }
        });
        updateCalibrationPlanPreview(targetInput.getText().toString(), planPreview);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        addCalibrationActionButton(actionRow, "입력 보정 시작", true, true, () -> {
            try {
                CalibrationPlanner.Plan plan = CalibrationPlanner.build(targetInput.getText().toString());
                showCalibrationPracticePage(plan);
            } catch (IllegalArgumentException ex) {
                planPreview.setText("한글 완성 글자 한 개를 입력하세요. 예: 현");
                planPreview.setTextColor(Color.rgb(180, 80, 48));
            }
        });
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(actionRow, actionParams);

        animatePageFrom(1);
        targetInput.postDelayed(() -> {
            targetInput.requestFocus();
            showSoftKeyboard(targetInput);
        }, 220);
    }

    private void updateCalibrationPlanPreview(String text, TextView preview) {
        try {
            CalibrationPlanner.Plan plan = CalibrationPlanner.build(text);
            preview.setText("계획: " + plan.summary());
            preview.setTextColor(TEXT_SECONDARY);
        } catch (IllegalArgumentException ex) {
            preview.setText("오타나는 한글 한 글자를 입력하면 계획을 만듭니다.");
            preview.setTextColor(TEXT_SECONDARY);
        }
    }

    private void showCalibrationPracticePage(CalibrationPlanner.Plan plan) {
        Runnable parentPage = this::showCalibrationTargetPage;
        backAction = parentPage;
        root.removeAllViews();
        addDetailHeader("보정 입력", parentPage);

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
        inputView.setPrivateImeOptions("com.yadiate.yoonkeyboard.CALIBRATION");
        inputView.setMinHeight(dp(64));
        inputView.setGravity(Gravity.CENTER_VERTICAL);
        inputView.setPadding(dp(16), dp(12), dp(16), dp(12));
        inputView.setBackground(rounded(Color.rgb(255, 255, 255), 18));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.setMargins(0, 0, 0, dp(10));
        root.addView(inputView, inputParams);

        TextView hintView = rowSummary("틀리게 입력해도 경고하지 않고 그 획을 그대로 의도 글자의 샘플로 저장합니다.");
        hintView.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(hintView, matchWrap());

        ActualCalibrationSession session = new ActualCalibrationSession(plan, progressView, promptView, inputView,
                hintView);
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        addCalibrationActionButton(actionRow, "보정 지금 완성하기", true, true, session::finishNow);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.setMargins(0, 0, 0, dp(12));
        root.addView(actionRow, actionParams);

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
        addActionRow(card, "다시 보정", "오타나는 글자를 다시 고르고 기준을 새로 만듭니다.", this::showCalibrationTargetPage);
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
        addChoiceRow(card, "스킨", SettingsStore.SKINS, SettingsStore.KEY_SKIN, SettingsStore.SKIN_DARK,
                () -> showMainPage(true, -1));
        addDivider(card);
        addChoiceRow(card, "자음 레이아웃", SettingsStore.HANGUL_TYPES, SettingsStore.KEY_HANGUL_TYPE,
                SettingsStore.HANGUL_LAYOUT_TWO_BEOLSIK, () -> showMainPage(true, -1));
        if (SettingsStore.isFoldPhone(this)) {
            addDivider(card);
            addFoldModeRow(card);
        }
        addDivider(card);
        addKeyboardSizeRow(card);
        addDivider(card);
        addSwitchRow(card, "Debug", "Show hit boxes and touch points on the keyboard.",
                SettingsStore.KEY_DEBUG_TOUCH_OVERLAY, false);
    }

    private void addFeedbackSection() {
        addSectionTitle("입력");
        LinearLayout card = addCard();
        addStrokeLengthRow(card);
        addDivider(card);
        addTypoGuardRow(card);
        addDivider(card);
        addCalibrationRow(card);
        addDivider(card);
        addHitboxRow(card);
        addDivider(card);
        addDoubleConsonantRow(card);
        addDivider(card);
        addDeleteRepeatRow(card);

        addSectionTitle("피드백");
        LinearLayout feedbackCard = addCard();
        addSwitchRow(feedbackCard, "진동", "키를 누를 때 짧게 진동합니다.", SettingsStore.KEY_VIBRATE_ON, true);
        addDivider(feedbackCard);
        addInlineVibrateProgressRow(feedbackCard);
        addDivider(feedbackCard);
        addSwitchRow(feedbackCard, "소리", "키 입력음을 재생합니다.", SettingsStore.KEY_SOUND_ON, false);
    }

    private void addBackupSection() {
        addSectionTitle("\uC635\uC158 \uBC31\uC5C5");
        LinearLayout card = addCard();
        addActionRow(card,
                "\uC635\uC158 \uC800\uC7A5",
                "\uD604\uC7AC \uC635\uC158\uC744 \uD074\uB9BD\uBCF4\uB4DC\uC5D0 \uBCF5\uC0AC\uD569\uB2C8\uB2E4.",
                this::saveOptionsToClipboard);
        addDivider(card);
        addActionRow(card,
                "\uC635\uC158 \uBD88\uB7EC\uC624\uAE30",
                "\uD074\uB9BD\uBCF4\uB4DC\uC758 \uC635\uC158\uAC12\uC744 \uBCF5\uC6D0\uD569\uB2C8\uB2E4.",
                this::loadOptionsFromClipboard);
    }

    private void addUpdateSection() {
        addSectionTitle("\uC5C5\uB370\uC774\uD2B8");
        LinearLayout card = addCard();
        addActionRow(card,
                "\uBC84\uC804 \uD655\uC778",
                "\uD604\uC7AC " + currentVersionName() + ". GitHub \uCD5C\uC2E0 \uB9B4\uB9AC\uC988\uC640 \uBE44\uAD50\uD569\uB2C8\uB2E4.",
                this::checkGitHubReleaseUpdate);
        addDivider(card);
        addUpdateDownloadRow(card);
    }

    private String currentVersionName() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return versionName == null ? "\uC54C \uC218 \uC5C6\uC74C" : versionName;
        } catch (Exception ex) {
            return "\uC54C \uC218 \uC5C6\uC74C";
        }
    }

    private void addUpdateDownloadRow(LinearLayout card) {
        LinearLayout row = rowContainer();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        updateDownloadTitle = rowTitle("\uC5C5\uB370\uC774\uD2B8 \uB2E4\uC6B4\uB85C\uB4DC");
        texts.addView(updateDownloadTitle, matchWrap());
        updateDownloadSummary = rowSummary("\uBC84\uC804 \uD655\uC778 \uD6C4 \uC0AC\uC6A9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        texts.addView(updateDownloadSummary, matchWrap());
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        updateDownloadArrow = new TextView(this);
        updateDownloadArrow.setText("›");
        updateDownloadArrow.setTextSize(28);
        updateDownloadArrow.setGravity(Gravity.CENTER);
        row.addView(updateDownloadArrow, new LinearLayout.LayoutParams(dp(26), LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> startGitHubReleaseUpdate());
        card.addView(row, matchWrap());
        updateDownloadRow = row;
        setUpdateDownloadEnabled(null);
    }

    private void setUpdateDownloadEnabled(GitHubReleaseUpdater.UpdateCandidate candidate) {
        pendingUpdateCandidate = candidate;
        boolean enabled = candidate != null;
        int disabledTitle = Color.rgb(176, 180, 188);
        int disabledSummary = Color.rgb(188, 192, 200);
        int disabledArrow = Color.rgb(196, 200, 208);

        if (updateDownloadRow != null) {
            updateDownloadRow.setEnabled(enabled);
            updateDownloadRow.setClickable(true);
        }
        if (updateDownloadTitle != null) {
            updateDownloadTitle.setTextColor(enabled ? TEXT_PRIMARY : disabledTitle);
            updateDownloadTitle.setTypeface(Typeface.DEFAULT, enabled ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (updateDownloadSummary != null) {
            updateDownloadSummary.setTextColor(enabled ? TEXT_SECONDARY : disabledSummary);
            updateDownloadSummary.setText(enabled
                    ? candidate.releaseLabel + " APK\uB97C \uB0B4\uB824\uBC1B\uC544 \uC124\uCE58\uD569\uB2C8\uB2E4."
                    : "\uBC84\uC804 \uD655\uC778 \uD6C4 \uC0AC\uC6A9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.");
        }
        if (updateDownloadArrow != null) {
            updateDownloadArrow.setTextColor(enabled ? Color.rgb(36, 107, 253) : disabledArrow);
        }
    }

    private void checkGitHubReleaseUpdate() {
        if (updateInProgress) {
            showToast("\uC5C5\uB370\uC774\uD2B8\uB97C \uC774\uBBF8 \uD655\uC778 \uC911\uC785\uB2C8\uB2E4.");
            return;
        }
        updateInProgress = true;
        setUpdateDownloadEnabled(null);
        GitHubReleaseUpdater.checkLatestRelease(this, currentVersionName(), new GitHubReleaseUpdater.CheckCallback() {
            @Override
            public void onStatus(String message) {
                showToast(message);
            }

            @Override
            public void onChecked(GitHubReleaseUpdater.UpdateCandidate candidate, boolean updateAvailable) {
                updateInProgress = false;
                if (updateAvailable) {
                    setUpdateDownloadEnabled(candidate);
                    showToast(candidate.releaseLabel + " \uC5C5\uB370\uC774\uD2B8\uAC00 \uC788\uC2B5\uB2C8\uB2E4.");
                } else {
                    setUpdateDownloadEnabled(null);
                    showToast("\uCD5C\uC2E0\uBC84\uC804\uC785\uB2C8\uB2E4!");
                }
            }

            @Override
            public void onError(String message) {
                updateInProgress = false;
                setUpdateDownloadEnabled(null);
                showToast(message);
            }
        });
    }

    private void startGitHubReleaseUpdate() {
        if (pendingUpdateCandidate == null) {
            showToast("\uBA3C\uC800 \uBC84\uC804\uC744 \uD655\uC778\uD574 \uC8FC\uC138\uC694.");
            return;
        }
        if (updateInProgress) {
            showToast("\uC5C5\uB370\uC774\uD2B8 \uC791\uC5C5\uC774 \uC9C4\uD589 \uC911\uC785\uB2C8\uB2E4.");
            return;
        }
        updateInProgress = true;
        GitHubReleaseUpdater.downloadReleaseApk(this, pendingUpdateCandidate, new GitHubReleaseUpdater.Callback() {
            @Override
            public void onStatus(String message) {
                showToast(message);
            }

            @Override
            public void onDownloaded(File apkFile, String releaseLabel) {
                updateInProgress = false;
                pendingUpdateApk = apkFile;
                showToast(releaseLabel + " APK \uB2E4\uC6B4\uB85C\uB4DC\uAC00 \uC644\uB8CC\uB410\uC2B5\uB2C8\uB2E4.");
                beginUpdateInstall(apkFile);
            }

            @Override
            public void onError(String message) {
                updateInProgress = false;
                showToast(message);
            }
        });
    }

    private void beginUpdateInstall(File apkFile) {
        try {
            if (GitHubReleaseUpdater.startInstallOrOpenPermissionSettings(this, apkFile)) {
                pendingUpdateApk = null;
                showToast("\uC124\uCE58 \uD654\uBA74\uC744 \uC5F4\uC5C8\uC2B5\uB2C8\uB2E4.");
            } else {
                pendingUpdateApk = apkFile;
                showToast("APK \uC124\uCE58 \uAD8C\uD55C\uC744 \uD5C8\uC6A9\uD55C \uB4A4 \uC774 \uD654\uBA74\uC73C\uB85C \uB3CC\uC544\uC624\uC138\uC694.");
            }
        } catch (IllegalStateException ex) {
            pendingUpdateApk = null;
            showToast("\uC124\uCE58 \uD654\uBA74\uC744 \uC5F4 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
        }
    }

    private void saveOptionsToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showToast("\uD074\uB9BD\uBCF4\uB4DC\uB97C \uC0AC\uC6A9\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
            return;
        }
        String exportText = SettingsStore.exportOptions(this);
        clipboard.setPrimaryClip(ClipData.newPlainText("YoonKeyboard options", exportText));
        showToast("\uC635\uC158\uC744 \uD074\uB9BD\uBCF4\uB4DC\uC5D0 \uC800\uC7A5\uD588\uC2B5\uB2C8\uB2E4.");
    }

    private void loadOptionsFromClipboard() {
        CharSequence text = clipboardText();
        if (text == null || text.toString().trim().isEmpty()) {
            showToast("\uD074\uB9BD\uBCF4\uB4DC\uC5D0 \uBD88\uB7EC\uC62C \uC635\uC158\uAC12\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.");
            return;
        }
        try {
            SettingsStore.importOptions(this, text.toString());
            prefs = SettingsStore.prefs(this);
            showMainPage(true, -1);
            showToast("\uC635\uC158\uC744 \uBCF5\uC6D0\uD588\uC2B5\uB2C8\uB2E4.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showToast("\uD074\uB9BD\uBCF4\uB4DC\uC758 \uC635\uC158\uAC12\uC744 \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.");
        }
    }

    private CharSequence clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() <= 0) {
            return null;
        }
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        return item == null ? null : item.coerceToText(this);
    }

    private void showToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
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

    private void addTypoGuardRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "\uC624\uD0C0\uB9C9\uAE30", typoGuardSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showTypoGuardPage());
    }

    private void addKeyboardSizeRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "크기 조절", keyboardSizeSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showKeyboardSizePage());
    }

    private void addFoldModeRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "\uD3F4\uB4DC \uBAA8\uB4DC", foldModeSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showFoldModePage());
    }

    private void addCalibrationRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "보정", calibrationSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showCalibrationPage());
    }

    private void addHitboxRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "터치 판정", hitboxSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showHitboxPage());
    }

    private void addDoubleConsonantRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "쌍자음 대기시간", doubleConsonantSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showDoubleConsonantPage());
    }

    private void addDeleteRepeatRow(LinearLayout card) {
        TextView summary = addMenuRow(card, "Delete 반복", deleteRepeatSummary(), true);
        View row = (View) summary.getTag();
        row.setOnClickListener(v -> showDeleteRepeatPage());
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

    private void addCheckRow(LinearLayout card, String title, String summary, String key, boolean defaultValue) {
        LinearLayout row = rowContainer();

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.addView(rowTitle(title), matchWrap());
        texts.addView(rowSummary(summary), matchWrap());
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(prefs.getBoolean(key, defaultValue));
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                SettingsStore.putBoolean(this, key, isChecked));
        row.addView(checkBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));
        card.addView(row, matchWrap());
    }

    private interface StrokeMmChangeListener {
        void onChanged(int value);
    }

    private class StrokeMmControl {
        private final SeekBar seekBar;
        private final TextView valueView;
        private int minTenths;
        private int maxTenths;
        private boolean updating;

        StrokeMmControl(SeekBar seekBar, TextView valueView, int minTenths, int maxTenths) {
            this.seekBar = seekBar;
            this.valueView = valueView;
            this.minTenths = minTenths;
            this.maxTenths = maxTenths;
        }

        void setRangeAndValue(int minTenths, int maxTenths, int value) {
            updating = true;
            this.minTenths = minTenths;
            this.maxTenths = maxTenths;
            seekBar.setMax(Math.max(0, (maxTenths - minTenths) / SettingsStore.CUSTOM_STROKE_STEP_TENTHS));
            seekBar.setProgress(strokeSeekProgress(value, minTenths));
            updateStrokeMmText(valueView, strokeSeekValue(seekBar.getProgress(), minTenths, maxTenths));
            updating = false;
        }
    }

    private StrokeMmControl addStrokeMmRow(LinearLayout card, String title, String description, String key,
                                           int minTenths, int maxTenths, int currentTenths,
                                           StrokeMmChangeListener changeListener) {
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
        seekBar.setMax((maxTenths - minTenths) / SettingsStore.CUSTOM_STROKE_STEP_TENTHS);
        seekBar.setProgress(strokeSeekProgress(currentTenths, minTenths));
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        StrokeMmControl control = new StrokeMmControl(seekBar, valueView, minTenths, maxTenths);
        updateStrokeMmText(valueView, strokeSeekValue(seekBar.getProgress(), minTenths, maxTenths));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (control.updating) {
                    return;
                }
                int value = strokeSeekValue(progress, control.minTenths, control.maxTenths);
                SettingsStore.putInt(MainActivity.this, key, value);
                int savedValue = prefs.getInt(key, value);
                if (SettingsStore.KEY_STROKE_SHORT_MM_TENTHS.equals(key)) {
                    int strokeIndex = currentIndex(SettingsStore.KEY_STROKE_LENGTH, SettingsStore.STROKE_LENGTHS, 2);
                    int longStroke = SettingsStore.boundedCustomLongStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_LONG_MM_TENTHS,
                                    SettingsStore.defaultLongStrokeMmTenths(strokeIndex)),
                            SettingsStore.defaultLongStrokeMmTenths(strokeIndex));
                    savedValue = SettingsStore.boundedCustomShortStrokeMmTenths(savedValue, longStroke,
                            SettingsStore.defaultShortStrokeMmTenths(strokeIndex));
                } else if (SettingsStore.KEY_STROKE_DERIVATION_SHORT_MM_TENTHS.equals(key)) {
                    int strokeIndex = currentIndex(SettingsStore.KEY_STROKE_LENGTH, SettingsStore.STROKE_LENGTHS, 2);
                    int longStroke = SettingsStore.boundedCustomLongStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_LONG_MM_TENTHS,
                                    SettingsStore.defaultLongStrokeMmTenths(strokeIndex)),
                            SettingsStore.defaultLongStrokeMmTenths(strokeIndex));
                    int firstShortStroke = SettingsStore.boundedCustomShortStrokeMmTenths(
                            prefs.getInt(SettingsStore.KEY_STROKE_SHORT_MM_TENTHS,
                                    SettingsStore.defaultShortStrokeMmTenths(strokeIndex)),
                            longStroke,
                            SettingsStore.defaultShortStrokeMmTenths(strokeIndex));
                    int fallback = SettingsStore.defaultDerivationShortStrokeMmTenths(
                            SettingsStore.defaultDerivationShortStrokeMmTenths(strokeIndex),
                            firstShortStroke,
                            longStroke);
                    savedValue = SettingsStore.boundedCustomDerivationShortStrokeMmTenths(savedValue,
                            firstShortStroke, longStroke, fallback);
                } else if (SettingsStore.KEY_STROKE_LONG_MM_TENTHS.equals(key)) {
                    int strokeIndex = currentIndex(SettingsStore.KEY_STROKE_LENGTH, SettingsStore.STROKE_LENGTHS, 2);
                    savedValue = SettingsStore.boundedCustomLongStrokeMmTenths(savedValue,
                            SettingsStore.defaultLongStrokeMmTenths(strokeIndex));
                }
                control.setRangeAndValue(control.minTenths, control.maxTenths, savedValue);
                if (changeListener != null) {
                    changeListener.onChanged(savedValue);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return control;
    }

    private void addDeleteRepeatMsRow(LinearLayout card, String title, String description, String key,
                                      int minMs, int maxMs, int stepMs, int currentMs) {
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
        topLine.addView(valueView, new LinearLayout.LayoutParams(dp(82),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(topLine, matchWrap());

        TextView descriptionView = rowSummary(description);
        descriptionView.setPadding(0, dp(4), 0, dp(8));
        content.addView(descriptionView, matchWrap());

        int safeValue = boundedDeleteRepeatValue(key, currentMs);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax((maxMs - minMs) / stepMs);
        seekBar.setProgress(deleteRepeatSeekProgress(safeValue, minMs, stepMs));
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        updateDeleteRepeatMsText(valueView, deleteRepeatSeekValue(seekBar.getProgress(), minMs, maxMs, stepMs));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = deleteRepeatSeekValue(progress, minMs, maxMs, stepMs);
                SettingsStore.putInt(MainActivity.this, key, value);
                updateDeleteRepeatMsText(valueView, boundedDeleteRepeatValue(key, value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void addDoubleTapTimeoutMsRow(LinearLayout card, String title, String description, int currentMs) {
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
        topLine.addView(valueView, new LinearLayout.LayoutParams(dp(82),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(topLine, matchWrap());

        TextView descriptionView = rowSummary(description);
        descriptionView.setPadding(0, dp(4), 0, dp(8));
        content.addView(descriptionView, matchWrap());

        int minMs = SettingsStore.MIN_DOUBLE_TAP_TIMEOUT_MS;
        int maxMs = SettingsStore.MAX_DOUBLE_TAP_TIMEOUT_MS;
        int stepMs = SettingsStore.DOUBLE_TAP_TIMEOUT_STEP_MS;
        int safeValue = SettingsStore.boundedDoubleTapTimeoutMs(currentMs);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax((maxMs - minMs) / stepMs);
        seekBar.setProgress(doubleTapTimeoutSeekProgress(safeValue));
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        updateDoubleTapTimeoutMsText(valueView, doubleTapTimeoutSeekValue(seekBar.getProgress()));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = doubleTapTimeoutSeekValue(progress);
                SettingsStore.putInt(MainActivity.this, SettingsStore.KEY_DOUBLE_TAP_TIMEOUT_MS, value);
                updateDoubleTapTimeoutMsText(valueView, SettingsStore.boundedDoubleTapTimeoutMs(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void addHitboxDpRow(LinearLayout card, String title, String description, String key, int currentDp,
                                HitboxPreviewView previewView) {
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
        topLine.addView(valueView, new LinearLayout.LayoutParams(dp(72),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(topLine, matchWrap());

        TextView descriptionView = rowSummary(description);
        descriptionView.setPadding(0, dp(4), 0, dp(8));
        content.addView(descriptionView, matchWrap());

        int minDp = SettingsStore.hitboxMinDp(key);
        int maxDp = SettingsStore.hitboxMaxDp(key);
        int safeValue = SettingsStore.boundedHitboxDp(key, currentDp);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(maxDp - minDp);
        seekBar.setProgress(safeValue - minDp);
        content.addView(seekBar, matchWrap());
        card.addView(content, matchWrap());

        updateHitboxDpText(valueView, safeValue);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = SettingsStore.boundedHitboxDp(key, minDp + progress);
                SettingsStore.putInt(MainActivity.this, key, value);
                updateHitboxDpText(valueView, value);
                if (previewView != null) {
                    previewView.invalidate();
                }
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
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        return "첫 " + SettingsStore.strokeMmLabel(snapshot.shortStrokeMmTenths)
                + " · 파생 " + SettingsStore.strokeMmLabel(snapshot.derivationShortStrokeMmTenths)
                + " · 긴 " + SettingsStore.strokeMmLabel(snapshot.longStrokeMmTenths);
    }

    private String typoGuardSummary() {
        return prefs.getBoolean(SettingsStore.KEY_BLOCK_YEO_UP_TO_YE, true)
                ? "\u3155 \uC704\u2192\u3156 \uCC28\uB2E8"
                : "\uAEBC\uC9D0";
    }

    private String foldModeSummary() {
        int mode = currentIndex(SettingsStore.KEY_FOLD_KEYBOARD_MODE,
                SettingsStore.FOLD_KEYBOARD_MODES,
                SettingsStore.FOLD_KEYBOARD_MODE_SPLIT);
        return SettingsStore.FOLD_KEYBOARD_MODES[mode];
    }

    private int deleteRepeatSeekProgress(int value, int minMs, int stepMs) {
        return Math.max(0, Math.round((value - minMs) / (float) stepMs));
    }

    private int deleteRepeatSeekValue(int progress, int minMs, int maxMs, int stepMs) {
        int value = minMs + progress * stepMs;
        return Math.max(minMs, Math.min(value, maxMs));
    }

    private int doubleTapTimeoutSeekProgress(int value) {
        int minMs = SettingsStore.MIN_DOUBLE_TAP_TIMEOUT_MS;
        return Math.max(0, Math.round((value - minMs) / (float) SettingsStore.DOUBLE_TAP_TIMEOUT_STEP_MS));
    }

    private int doubleTapTimeoutSeekValue(int progress) {
        int minMs = SettingsStore.MIN_DOUBLE_TAP_TIMEOUT_MS;
        int maxMs = SettingsStore.MAX_DOUBLE_TAP_TIMEOUT_MS;
        int value = minMs + progress * SettingsStore.DOUBLE_TAP_TIMEOUT_STEP_MS;
        return Math.max(minMs, Math.min(value, maxMs));
    }

    private int boundedDeleteRepeatValue(String key, int value) {
        if (SettingsStore.KEY_DELETE_REPEAT_START_MS.equals(key)) {
            return SettingsStore.boundedDeleteRepeatStartMs(value);
        }
        return SettingsStore.boundedDeleteRepeatIntervalMs(value);
    }

    private void updateDeleteRepeatMsText(TextView valueText, int ms) {
        valueText.setText(ms + "ms");
    }

    private void updateDoubleTapTimeoutMsText(TextView valueText, int ms) {
        valueText.setText(ms + "ms");
    }

    private void updateHitboxDpText(TextView valueText, int valueDp) {
        valueText.setText(valueDp + "dp");
    }

    private String doubleConsonantSummary() {
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        return snapshot.doubleTapTimeoutMs + "ms 안에 두 번 누를 때만 변환";
    }

    private String deleteRepeatSummary() {
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        return "시작 " + snapshot.deleteRepeatStartMs + "ms · 간격 " + snapshot.deleteRepeatIntervalMs + "ms";
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

    private String hitboxSummary() {
        SettingsStore.Snapshot snapshot = SettingsStore.load(this);
        return "기본 " + snapshot.hitboxBaseDp + "dp · 좌/상 "
                + snapshot.hitboxLeftCutDp + "/" + snapshot.hitboxTopCutDp
                + "dp · 우/하 +" + snapshot.hitboxRightExtraDp + "/+"
                + snapshot.hitboxBottomExtraDp + "dp";
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

    private class HitboxPreviewView extends View {
        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF keyboardRect = new RectF();
        private final java.util.ArrayList<HitboxCell> cells = new java.util.ArrayList<>();

        HitboxPreviewView(Context context) {
            super(context);
            setMinimumHeight(dp(260));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, dp(260));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            SettingsStore.Snapshot snapshot = SettingsStore.load(MainActivity.this);
            SettingsStore.KeyboardTheme theme = snapshot.theme;
            buildHitboxCells(snapshot);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(theme.background);
            canvas.drawRoundRect(keyboardRect, dp(12), dp(12), previewPaint);

            for (HitboxCell cell : cells) {
                drawHitboxFill(canvas, cell);
            }
            for (HitboxCell cell : cells) {
                drawHitboxKey(canvas, cell, theme);
            }
            for (HitboxCell cell : cells) {
                drawHitboxStroke(canvas, cell);
            }
        }

        private void buildHitboxCells(SettingsStore.Snapshot snapshot) {
            cells.clear();
            keyboardRect.set(dp(8), dp(6), getWidth() - dp(8), getHeight() - dp(8));

            float gap = dp(4);
            float availableWidth = keyboardRect.width() - gap * 8f;
            float sideWeight = snapshot.hangulLeftColumnWeight(5f);
            float centerWeight = 1f;
            float rightWeight = snapshot.hangulRightColumnWeight(5f);
            float totalWeight = sideWeight + centerWeight * 5f + rightWeight;
            float sideWidth = availableWidth * sideWeight / totalWeight;
            float centerWidth = availableWidth * centerWeight / totalWeight;
            float rightWidth = availableWidth * rightWeight / totalWeight;
            float top = keyboardRect.top + gap;
            float rowHeight = (keyboardRect.height() - gap * 5f) / 4f;
            if (centerWidth <= 1f || rowHeight <= 1f) {
                return;
            }

            float left = keyboardRect.left + gap;
            String[] sideLabels = {"Abc", "#★♪", "123", "⚙"};
            for (int row = 0; row < sideLabels.length; row++) {
                RectF rect = new RectF(left, top + row * (rowHeight + gap),
                        left + sideWidth, top + row * (rowHeight + gap) + rowHeight);
                addHitboxCell(sideLabels[row], rect, false, true, false, snapshot);
            }

            float centerLeft = left + sideWidth + gap;
            Object[][] rows = calibrationKeyboardRows(snapshot.hangulTypeIndex);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 5; col++) {
                    Object value = rows[row][col + 1];
                    String label = previewLabel(value);
                    if (label.isEmpty()) {
                        continue;
                    }
                    RectF rect = new RectF(centerLeft + col * (centerWidth + gap),
                            top + row * (rowHeight + gap),
                            centerLeft + col * (centerWidth + gap) + centerWidth,
                            top + row * (rowHeight + gap) + rowHeight);
                    boolean textInput = value instanceof Consonant || "모음".equals(label);
                    addHitboxCell(label, rect, textInput, !textInput, false, snapshot);
                }
            }

            float bottomTop = top + 3f * (rowHeight + gap);
            addHitboxCell("←", new RectF(centerLeft, bottomTop,
                    centerLeft + centerWidth, bottomTop + rowHeight), false, true, false, snapshot);
            addHitboxCell("→", new RectF(centerLeft + centerWidth + gap, bottomTop,
                    centerLeft + centerWidth * 2f + gap, bottomTop + rowHeight),
                    false, true, false, snapshot);
            addHitboxCell("＿", new RectF(centerLeft + (centerWidth + gap) * 2f, bottomTop,
                    centerLeft + centerWidth * 5f + gap * 4f, bottomTop + rowHeight),
                    false, true, false, snapshot);

            float rightLeft = centerLeft + (centerWidth + gap) * 5f;
            addHitboxCell("DEL\n←", new RectF(rightLeft, top,
                    rightLeft + rightWidth, top + rowHeight * 3f + gap * 2f),
                    false, true, false, snapshot);
            addHitboxCell("Go", new RectF(rightLeft, bottomTop,
                    rightLeft + rightWidth, bottomTop + rowHeight), false, false, true, snapshot);
        }

        private void addHitboxCell(String label, RectF keyRect, boolean textInput, boolean special,
                                   boolean enter, SettingsStore.Snapshot snapshot) {
            RectF hitRect = hitRectFor(keyRect, textInput, snapshot);
            cells.add(new HitboxCell(label, keyRect, hitRect, textInput, special, enter));
        }

        private RectF hitRectFor(RectF rect, boolean textInput, SettingsStore.Snapshot snapshot) {
            float gap = dp(5);
            float baseSlop = dp(snapshot.hitboxBaseDp);
            float leftCut = Math.min(rect.width() * 0.18f, dp(snapshot.hitboxLeftCutDp));
            float topCut = Math.min(rect.height() * 0.18f, dp(snapshot.hitboxTopCutDp));
            float rightSlop = baseSlop + dp(snapshot.hitboxRightExtraDp);
            float bottomSlop = baseSlop + dp(snapshot.hitboxBottomExtraDp);
            if (textInput && rect.centerX() < keyboardRect.centerX()) {
                rightSlop += dp(snapshot.hitboxLeftKeyRightExtraDp);
            }
            return new RectF(
                    Math.max(keyboardRect.left, rect.left + leftCut),
                    Math.max(keyboardRect.top, rect.top + topCut),
                    Math.min(keyboardRect.right, rect.right + rightSlop),
                    Math.min(keyboardRect.bottom, rect.bottom + bottomSlop));
        }

        private void drawHitboxFill(Canvas canvas, HitboxCell cell) {
            previewPaint.setShader(null);
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(cell.textInput ? Color.argb(58, 255, 70, 56)
                    : Color.argb(38, 255, 126, 64));
            canvas.drawRoundRect(cell.hitRect, dp(8), dp(8), previewPaint);
        }

        private void drawHitboxKey(Canvas canvas, HitboxCell cell, SettingsStore.KeyboardTheme theme) {
            int keyColor = cell.enter ? theme.enterKey : (cell.special ? theme.keySpecial : theme.keyNormal);
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(keyColor);
            canvas.drawRoundRect(cell.keyRect, dp(8), dp(8), previewPaint);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(Math.max(1f, dp(0.6f)));
            previewPaint.setColor(theme.stroke);
            canvas.drawRoundRect(cell.keyRect, dp(8), dp(8), previewPaint);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setTextAlign(Paint.Align.CENTER);
            previewPaint.setTypeface(cell.textInput ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            previewPaint.setColor(cell.enter ? theme.enterText : (cell.special ? theme.hint : theme.text));
            String[] lines = cell.label.split("\\n", -1);
            previewPaint.setTextSize(dp(cell.textInput ? 21 : 13));
            Paint.FontMetrics metrics = previewPaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float firstBaseline = cell.keyRect.centerY() - lineHeight * (lines.length - 1) / 2f
                    - (metrics.ascent + metrics.descent) / 2f;
            for (int i = 0; i < lines.length; i++) {
                canvas.drawText(lines[i], cell.keyRect.centerX(), firstBaseline + lineHeight * i, previewPaint);
            }
            previewPaint.setTypeface(Typeface.DEFAULT);
        }

        private void drawHitboxStroke(Canvas canvas, HitboxCell cell) {
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(cell.textInput ? dp(2f) : dp(1.2f));
            previewPaint.setColor(cell.textInput ? Color.rgb(237, 61, 54) : Color.rgb(232, 112, 55));
            canvas.drawRoundRect(cell.hitRect, dp(8), dp(8), previewPaint);
        }

        private String previewLabel(Object value) {
            if (value instanceof Consonant) {
                return ((Consonant) value).label();
            }
            return value == null ? "" : String.valueOf(value);
        }
    }

    private static class HitboxCell {
        final String label;
        final RectF keyRect;
        final RectF hitRect;
        final boolean textInput;
        final boolean special;
        final boolean enter;

        HitboxCell(String label, RectF keyRect, RectF hitRect, boolean textInput, boolean special, boolean enter) {
            this.label = label == null ? "" : label;
            this.keyRect = keyRect;
            this.hitRect = hitRect;
            this.textInput = textInput;
            this.special = special;
            this.enter = enter;
        }
    }

    private class CalibrationProgressView extends View {
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private float progress;
        private int lineIndex;
        private int stepCount = 1;
        private int sampleCount;

        CalibrationProgressView(Context context) {
            super(context);
            setMinimumHeight(dp(64));
        }

        void setState(float progress, int lineIndex, int stepCount, int sampleCount) {
            this.progress = Math.max(0f, Math.min(progress, 1f));
            this.lineIndex = lineIndex;
            this.stepCount = Math.max(1, stepCount);
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
            drawProgressText(canvas, "진행 " + (lineIndex + 1) + " / " + stepCount,
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
        private final CalibrationPlanner.Plan plan;
        private final CalibrationProgressView progressView;
        private final TextView promptView;
        private final EditText inputView;
        private final TextView hintView;
        private int stepIndex;
        private boolean advancing;
        private boolean editingProgrammatically;
        private boolean finished;

        ActualCalibrationSession(CalibrationPlanner.Plan plan, CalibrationProgressView progressView,
                                 TextView promptView, EditText inputView, TextView hintView) {
            this.plan = plan;
            this.progressView = progressView;
            this.promptView = promptView;
            this.inputView = inputView;
            this.hintView = hintView;
            SettingsStore.startGestureCalibrationSession(MainActivity.this, currentTarget(), stepIndex);
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
            if (finished) {
                return;
            }
            CalibrationPlanner.Step step = currentStep();
            String target = currentTarget();
            String visible = inputView.getText().toString();
            promptView.setText(target);

            hintView.setText(step.purpose + ": " + step.text + " " + step.repeats
                    + "번 입력하세요. 틀려도 경고 없이 그 획을 샘플로 씁니다.");
            inputView.setTextColor(TEXT_PRIMARY);

            int completedChars = plan.completedCharactersBeforeStep(stepIndex)
                    + Math.min(visible.length(), target.length());
            progressView.setState(completedChars / (float) plan.totalCharacters(), stepIndex, plan.steps.size(),
                    SettingsStore.gestureCalibrationSessionSampleCount(MainActivity.this));

            if (!advancing && visible.length() >= target.length()) {
                advancing = true;
                root.postDelayed(this::advanceLine, 260);
            }
        }

        void finishNow() {
            if (finished) {
                return;
            }
            int sampleCount = SettingsStore.gestureCalibrationSessionSampleCount(MainActivity.this);
            if (sampleCount <= 0) {
                hintView.setText("아직 저장할 획이 없습니다. 한 글자 이상 드래그 입력한 뒤 완료할 수 있습니다.");
                inputView.setTextColor(Color.rgb(180, 80, 48));
                return;
            }
            saveCalibration();
        }

        private void advanceLine() {
            if (finished) {
                return;
            }
            if (inputView.getText().toString().length() < currentTarget().length()) {
                advancing = false;
                refresh();
                return;
            }
            stepIndex++;
            advancing = false;
            if (stepIndex >= plan.steps.size()) {
                saveCalibration();
                return;
            }
            SettingsStore.updateGestureCalibrationSession(MainActivity.this, currentTarget(), stepIndex);
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
            finished = true;
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

        private CalibrationPlanner.Step currentStep() {
            return plan.steps.get(Math.min(stepIndex, plan.steps.size() - 1));
        }

        private String currentTarget() {
            return currentStep().targetText();
        }
    }

    private interface ManualAngleChangeListener {
        void onManualAngleChanged(Consonant consonant,
                                  GestureCalibration.DirectionClass directionClass,
                                  float angleDegrees);
    }

    private interface CalibrationKeySelectListener {
        void onKeySelected(Consonant consonant, String calibrationKey, boolean cycleDirection);
    }

    private Object[][] calibrationKeyboardRows(int hangulTypeIndex) {
        if (isTwoBeolsikHangulType(hangulTypeIndex)) {
            return new Object[][]{
                    {"Abc", Consonant.BIEUP, Consonant.JIEUT, Consonant.DIGEUT, Consonant.GIYEOK, Consonant.SIOT, "DEL"},
                    {"#★♪", Consonant.MIEUM, Consonant.NIEUN, Consonant.IEUNG, Consonant.RIEUL, Consonant.HIEUT, ""},
                    {"123", Consonant.KIEUK, Consonant.TIEUT, Consonant.CHIEUT, Consonant.PIEUP, "모음", ""},
                    {"⚙", "←", "→", "space", "space", "Go", ""}
            };
        }
        return new Object[][]{
                {"Abc", Consonant.KIEUK, Consonant.GIYEOK, Consonant.SIOT, Consonant.JIEUT, Consonant.CHIEUT, "DEL"},
                {"#★♪", Consonant.HIEUT, Consonant.NIEUN, Consonant.IEUNG, Consonant.RIEUL, Consonant.MIEUM, ""},
                {"123", Consonant.TIEUT, Consonant.DIGEUT, Consonant.BIEUP, Consonant.PIEUP, "모음", ""},
                {"⚙", "←", "→", "space", "space", "Go", ""}
        };
    }

    private boolean isTwoBeolsikHangulType(int hangulTypeIndex) {
        return hangulTypeIndex == SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_VERTICAL
                || hangulTypeIndex == SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL;
    }

    private String calibrationKeyForConsonant(Consonant consonant, int hangulTypeIndex) {
        Object[][] rows = calibrationKeyboardRows(hangulTypeIndex);
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length; column++) {
                if (rows[row][column] == consonant) {
                    return gestureCalibrationKey(row, column - 1);
                }
            }
        }
        return "";
    }

    private String gestureCalibrationKey(int row, int column) {
        return "hangul_gesture_r" + row + "_c" + column;
    }

    private class CalibrationAngleEditorView extends View {
        private final Paint anglePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF circleBounds = new RectF();
        private final RectF rangeSliderRect = new RectF();
        private final Path handlePath = new Path();
        private final ManualAngleChangeListener angleChangeListener;
        private final float fallbackShortMm;
        private final float fallbackLongMm;
        private GestureCalibration.Profile profile;
        private CalibrationKeyboardPickerView keyboardPickerView;
        private Consonant selectedConsonant;
        private String selectedCalibrationKey;
        private GestureCalibration.DirectionClass selectedDirection = GestureCalibration.DirectionClass.TOP_RIGHT;
        private float centerX;
        private float centerY;
        private float radius;
        private boolean draggingHandle;
        private boolean draggingRange;

        CalibrationAngleEditorView(Context context, GestureCalibration.Profile profile, Consonant selectedConsonant,
                                   String selectedCalibrationKey, float fallbackShortMm, float fallbackLongMm,
                                   ManualAngleChangeListener angleChangeListener) {
            super(context);
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            this.selectedConsonant = selectedConsonant;
            this.selectedCalibrationKey = selectedCalibrationKey == null ? "" : selectedCalibrationKey;
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

        void setSelectedKey(Consonant consonant, String calibrationKey) {
            selectedConsonant = consonant;
            selectedCalibrationKey = calibrationKey == null ? "" : calibrationKey;
            if (keyboardPickerView != null) {
                keyboardPickerView.setSelection(selectedConsonant, selectedCalibrationKey, selectedDirection);
            }
            invalidate();
        }

        void selectNextDirection() {
            int index = Arrays.asList(EDITABLE_DIAGONALS).indexOf(selectedDirection);
            selectedDirection = EDITABLE_DIAGONALS[(index + 1 + EDITABLE_DIAGONALS.length) % EDITABLE_DIAGONALS.length];
            if (keyboardPickerView != null) {
                keyboardPickerView.setSelection(selectedConsonant, selectedCalibrationKey, selectedDirection);
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
            drawRangeSlider(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (rangeSliderRect.contains(event.getX(), event.getY())) {
                        draggingRange = true;
                        updateToleranceFromTouch(event.getX());
                        return true;
                    }
                    GestureCalibration.DirectionClass hitDirection = hitDirection(event.getX(), event.getY());
                    if (hitDirection != null) {
                        selectedDirection = hitDirection;
                        draggingHandle = true;
                        updateAngleFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingRange) {
                        updateToleranceFromTouch(event.getX());
                        return true;
                    }
                    if (draggingHandle) {
                        updateAngleFromTouch(event.getX(), event.getY());
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    draggingHandle = false;
                    draggingRange = false;
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
            String targetLabel = selectedConsonant == null ? "기본값" : selectedConsonant.label() + " 키";
            canvas.drawText(targetLabel, dp(20), dp(32), anglePaint);

            anglePaint.setTypeface(Typeface.DEFAULT);
            anglePaint.setTextSize(dp(13));
            anglePaint.setColor(TEXT_SECONDARY);
            int samples = profile == null ? 0 : profile.keySamples(selectedCalibrationKey, selectedConsonant);
            canvas.drawText("샘플 " + samples + "개 · " + directionName(selectedDirection)
                    + " " + Math.round(angleFor(selectedDirection)) + "°", dp(20), dp(55), anglePaint);
        }

        private void drawAngleCircle(Canvas canvas) {
            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(Color.rgb(246, 248, 251));
            canvas.drawCircle(centerX, centerY, radius, anglePaint);

            float selectedAngle = angleFor(selectedDirection);
            float selectedTolerance = toleranceFor(selectedDirection);
            RectF toleranceArc = new RectF(centerX - radius * 0.96f, centerY - radius * 0.96f,
                    centerX + radius * 0.96f, centerY + radius * 0.96f);
            anglePaint.setColor(Color.argb(34, 32, 118, 255));
            canvas.drawArc(toleranceArc, selectedAngle - selectedTolerance,
                    selectedTolerance * 2f, true, anglePaint);

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

        private void drawRangeSlider(Canvas canvas) {
            float left = dp(24);
            float right = getWidth() - dp(24);
            float centerY = getHeight() - dp(34);
            rangeSliderRect.set(left, centerY - dp(24), right, centerY + dp(24));
            float trackY = centerY + dp(8);
            float tolerance = toleranceFor(selectedDirection);
            float progress = (tolerance - GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES)
                    / (GestureCalibration.MAX_DIAGONAL_TOLERANCE_DEGREES
                    - GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES);
            progress = Math.max(0f, Math.min(1f, progress));
            float handleX = left + (right - left) * progress;

            anglePaint.setShader(null);
            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setTextAlign(Paint.Align.LEFT);
            anglePaint.setTypeface(Typeface.DEFAULT_BOLD);
            anglePaint.setTextSize(dp(12));
            anglePaint.setColor(TEXT_PRIMARY);
            canvas.drawText("Range +/-" + Math.round(tolerance) + " deg", left, centerY - dp(10), anglePaint);

            anglePaint.setStyle(Paint.Style.STROKE);
            anglePaint.setStrokeWidth(dp(5));
            anglePaint.setStrokeCap(Paint.Cap.ROUND);
            anglePaint.setColor(Color.rgb(220, 226, 234));
            canvas.drawLine(left, trackY, right, trackY, anglePaint);
            anglePaint.setColor(Color.rgb(32, 118, 255));
            canvas.drawLine(left, trackY, handleX, trackY, anglePaint);
            anglePaint.setStrokeCap(Paint.Cap.BUTT);

            anglePaint.setStyle(Paint.Style.FILL);
            anglePaint.setColor(Color.WHITE);
            canvas.drawCircle(handleX, trackY, dp(10), anglePaint);
            anglePaint.setStyle(Paint.Style.STROKE);
            anglePaint.setStrokeWidth(dp(2));
            anglePaint.setColor(Color.rgb(32, 118, 255));
            canvas.drawCircle(handleX, trackY, dp(10), anglePaint);
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
            profile.setDirectionAngle(selectedCalibrationKey, selectedConsonant, selectedDirection, angle,
                    fallbackShortMm, fallbackLongMm);
            if (angleChangeListener != null) {
                angleChangeListener.onManualAngleChanged(selectedConsonant, selectedDirection, angle);
            }
            if (keyboardPickerView != null) {
                keyboardPickerView.setProfile(profile);
                keyboardPickerView.setSelection(selectedConsonant, selectedCalibrationKey, selectedDirection);
                keyboardPickerView.invalidate();
            }
            invalidate();
        }

        private void updateToleranceFromTouch(float x) {
            if (profile == null) {
                profile = new GestureCalibration.Profile();
            }
            float span = Math.max(1f, rangeSliderRect.width());
            float progress = Math.max(0f, Math.min(1f, (x - rangeSliderRect.left) / span));
            float tolerance = GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES
                    + (GestureCalibration.MAX_DIAGONAL_TOLERANCE_DEGREES
                    - GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES) * progress;
            tolerance = Math.round(tolerance);
            profile.setDirectionTolerance(selectedCalibrationKey, selectedConsonant, selectedDirection, tolerance,
                    fallbackShortMm, fallbackLongMm);
            if (keyboardPickerView != null) {
                keyboardPickerView.setProfile(profile);
                keyboardPickerView.setSelection(selectedConsonant, selectedCalibrationKey, selectedDirection);
                keyboardPickerView.invalidate();
            }
            invalidate();
        }

        private float angleFor(GestureCalibration.DirectionClass directionClass) {
            GestureCalibration.DirectionProfile directionProfile =
                    profile == null ? null : profile.directionProfile(
                            selectedCalibrationKey, selectedConsonant, directionClass);
            return directionProfile == null ? defaultAngleFor(directionClass) : directionProfile.centerAngle;
        }

        private float toleranceFor(GestureCalibration.DirectionClass directionClass) {
            GestureCalibration.DirectionProfile directionProfile =
                    profile == null ? null : profile.directionProfile(
                            selectedCalibrationKey, selectedConsonant, directionClass);
            return directionProfile == null
                    ? GestureCalibration.DEFAULT_DIAGONAL_TOLERANCE_DEGREES
                    : Math.max(GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES,
                    Math.min(GestureCalibration.MAX_DIAGONAL_TOLERANCE_DEGREES, directionProfile.tolerance));
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
        private String selectedCalibrationKey;
        private GestureCalibration.DirectionClass selectedDirection;
        private final int hangulTypeIndex;

        CalibrationKeyboardPickerView(Context context, GestureCalibration.Profile profile, Consonant selectedConsonant,
                                      String selectedCalibrationKey,
                                      GestureCalibration.DirectionClass selectedDirection, int hangulTypeIndex,
                                      CalibrationKeySelectListener keySelectListener) {
            super(context);
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            this.selectedConsonant = selectedConsonant;
            this.selectedCalibrationKey = selectedCalibrationKey == null ? "" : selectedCalibrationKey;
            this.selectedDirection = selectedDirection;
            this.hangulTypeIndex = hangulTypeIndex;
            this.keySelectListener = keySelectListener;
        }

        void setProfile(GestureCalibration.Profile profile) {
            this.profile = profile == null ? new GestureCalibration.Profile() : profile;
            invalidate();
        }

        void setSelection(Consonant consonant, String calibrationKey,
                          GestureCalibration.DirectionClass directionClass) {
            selectedConsonant = consonant;
            selectedCalibrationKey = calibrationKey == null ? "" : calibrationKey;
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
                    boolean cycleDirection = cell.matches(selectedConsonant, selectedCalibrationKey);
                    selectedConsonant = cell.consonant;
                    selectedCalibrationKey = cell.calibrationKey;
                    if (keySelectListener != null) {
                        keySelectListener.onKeySelected(cell.consonant, cell.calibrationKey, cycleDirection);
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
            Object[][] rows = calibrationKeyboardRows(hangulTypeIndex);

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
                        cells.add(new CalibrationKeyCell(rect, consonant.label(), consonant,
                                gestureCalibrationKey(rowIndex, column - 1)));
                    } else {
                        cells.add(new CalibrationKeyCell(rect, String.valueOf(value), null, ""));
                    }
                    left += width + gap;
                }
                top += rowHeight + gap;
            }
        }

        private void drawCalibrationKey(Canvas canvas, CalibrationKeyCell cell) {
            boolean selectable = cell.consonant != null;
            boolean selected = selectable && cell.matches(selectedConsonant, selectedCalibrationKey);
            boolean calibrated = selectable && hasAnyCalibration(cell);
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

        private boolean hasAnyCalibration(CalibrationKeyCell cell) {
            if (profile == null) {
                return false;
            }
            if (profile.touchProfile(cell.calibrationKey, cell.consonant) != null) {
                return true;
            }
            for (GestureCalibration.DirectionClass directionClass : EDITABLE_DIAGONALS) {
                if (profile.directionProfile(cell.calibrationKey, cell.consonant, directionClass) != null) {
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
        final String calibrationKey;

        CalibrationKeyCell(RectF rect, String label, Consonant consonant, String calibrationKey) {
            this.rect = rect;
            this.label = label;
            this.consonant = consonant;
            this.calibrationKey = calibrationKey == null ? "" : calibrationKey;
        }

        boolean matches(Consonant selectedConsonant, String selectedCalibrationKey) {
            if (selectedCalibrationKey != null
                    && !selectedCalibrationKey.isEmpty()
                    && !calibrationKey.isEmpty()) {
                return calibrationKey.equals(selectedCalibrationKey);
            }
            return consonant == selectedConsonant;
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
        private final RectF resetRect = new RectF();
        private final RectF doneRect = new RectF();
        private final Runnable doneAction;
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
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            computeKeyboardRect(previewBounds());
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
            drawKeyboardPreview(canvas);
            drawResizeChrome(canvas);
            drawPreviewButtons(canvas);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            return onTouchEvent(event);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                computeKeyboardRect(previewBounds());
                return buttonAt(event.getX(), event.getY()) != BUTTON_NONE
                        || currentModeHandleAt(event.getX(), event.getY()) != HANDLE_NONE;
            }
            return activeButton != BUTTON_NONE || activeHandle != HANDLE_NONE;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    computeKeyboardRect(previewBounds());
                    activeButton = buttonAt(event.getX(), event.getY());
                    activeHandle = activeButton == BUTTON_NONE ? currentModeHandleAt(event.getX(), event.getY()) : HANDLE_NONE;
                    if (activeButton != BUTTON_NONE || activeHandle != HANDLE_NONE) {
                        requestParentTouchIntercept(true);
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
                    boolean shouldReset = activeButton == BUTTON_RESET && resetRect.contains(event.getX(), event.getY());
                    boolean shouldFinish = activeButton == BUTTON_DONE && doneRect.contains(event.getX(), event.getY());
                    activeButton = BUTTON_NONE;
                    activeHandle = HANDLE_NONE;
                    requestParentTouchIntercept(false);
                    invalidate();
                    if (shouldReset) {
                        resetSize();
                    } else if (shouldFinish) {
                        doneAction.run();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    activeButton = BUTTON_NONE;
                    activeHandle = HANDLE_NONE;
                    requestParentTouchIntercept(false);
                    invalidate();
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        private void requestParentTouchIntercept(boolean disallow) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow);
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
        }

        private void drawKeyboardPreview(Canvas canvas) {
            SettingsStore.KeyboardTheme theme = SettingsStore.load(MainActivity.this).theme;
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(theme.background);
            canvas.drawRoundRect(keyboardRect, dp(2), dp(2), previewPaint);

            float gap = dp(4);
            float leftLineX = layoutLineX(layoutLeftPercent);
            float rightLineX = layoutLineX(layoutRightPercent);
            float rowsTop = keyboardRect.top + gap;
            float rowHeight = (keyboardRect.bottom - rowsTop - gap * 4f) / 4f;
            if (rowHeight <= dp(8)) {
                return;
            }

            drawPreviewSideColumn(canvas, keyboardRect.left + gap, rowsTop,
                    Math.max(dp(30), leftLineX - keyboardRect.left - gap * 2f), rowHeight, gap, theme);
            drawPreviewCenterKeys(canvas, leftLineX + gap, rowsTop,
                    Math.max(dp(60), rightLineX - leftLineX - gap * 2f), rowHeight, gap, theme);
            drawPreviewRightColumn(canvas, rightLineX + gap, rowsTop,
                    Math.max(dp(30), keyboardRect.right - rightLineX - gap * 2f), rowHeight, gap, theme);
        }

        private void drawPreviewSideColumn(Canvas canvas, float left, float top, float width, float rowHeight,
                                           float gap, SettingsStore.KeyboardTheme theme) {
            String[] labels = {"Abc", "#★♪", "123", "⚙"};
            for (int i = 0; i < labels.length; i++) {
                RectF rect = new RectF(left, top + i * (rowHeight + gap),
                        left + width, top + i * (rowHeight + gap) + rowHeight);
                drawPreviewKey(canvas, rect, labels[i], theme, true, false);
            }
        }

        private void drawPreviewCenterKeys(Canvas canvas, float left, float top, float width, float rowHeight,
                                           float gap, SettingsStore.KeyboardTheme theme) {
            String[][] labels = {
                    {"ㅋ", "ㄱ", "ㅅ", "ㅈ", "ㅊ"},
                    {"ㅎ", "ㄴ", "ㅇ", "ㄹ", "ㅁ"},
                    {"ㅌ", "ㄷ", "ㅂ", "ㅍ", "모음"},
                    {"←", "→", "!\n? ＿ .", "", "Go"}
            };
            float colWidth = (width - gap * 4f) / 5f;
            for (int row = 0; row < labels.length; row++) {
                for (int col = 0; col < labels[row].length; col++) {
                    String label = labels[row][col];
                    if (label.isEmpty()) {
                        continue;
                    }
                    RectF rect = new RectF(left + col * (colWidth + gap), top + row * (rowHeight + gap),
                            left + col * (colWidth + gap) + colWidth, top + row * (rowHeight + gap) + rowHeight);
                    boolean special = row == 3;
                    drawPreviewKey(canvas, rect, label, theme, special, "Go".equals(label));
                }
            }
        }

        private void drawPreviewRightColumn(Canvas canvas, float left, float top, float width, float rowHeight,
                                            float gap, SettingsStore.KeyboardTheme theme) {
            RectF deleteRect = new RectF(left, top, left + width, top + rowHeight * 3f + gap * 2f);
            drawPreviewKey(canvas, deleteRect, "DEL\n←", theme, true, false);
            RectF enterRect = new RectF(left, top + 3f * (rowHeight + gap), left + width,
                    top + 3f * (rowHeight + gap) + rowHeight);
            drawPreviewKey(canvas, enterRect, "Go", theme, false, true);
        }

        private void drawPreviewKey(Canvas canvas, RectF rect, String label, SettingsStore.KeyboardTheme theme,
                                    boolean special, boolean enter) {
            if (rect.width() <= 1f || rect.height() <= 1f) {
                return;
            }
            int keyColor = enter ? theme.enterKey : (special ? theme.keySpecial : theme.keyNormal);
            previewPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    lightenColor(keyColor, 0.08f), darkenColor(keyColor, 0.04f), Shader.TileMode.CLAMP));
            previewPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, dp(7), dp(7), previewPaint);
            previewPaint.setShader(null);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(Math.max(1f, dp(0.6f)));
            previewPaint.setColor(theme.stroke);
            canvas.drawRoundRect(rect, dp(7), dp(7), previewPaint);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setTextAlign(Paint.Align.CENTER);
            previewPaint.setFakeBoldText(false);
            previewPaint.setColor(enter ? theme.enterText : (special ? theme.hint : theme.text));
            String[] lines = label.split("\\n", -1);
            int textSize = label.length() > 2 || lines.length > 1 ? 13 : 22;
            if ("모음".equals(label)) {
                textSize = 20;
            }
            previewPaint.setTextSize(dp(textSize));
            Paint.FontMetrics metrics = previewPaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            float firstBaseline = rect.centerY() - lineHeight * (lines.length - 1) / 2f
                    - (metrics.ascent + metrics.descent) / 2f;
            for (int i = 0; i < lines.length; i++) {
                drawPreviewText(canvas, lines[i], rect.centerX(), firstBaseline
                        + lineHeight * i + (metrics.ascent + metrics.descent) / 2f);
            }
        }

        private int lightenColor(int color, float amount) {
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            red += Math.round((255 - red) * amount);
            green += Math.round((255 - green) * amount);
            blue += Math.round((255 - blue) * amount);
            return Color.rgb(clampColor(red), clampColor(green), clampColor(blue));
        }

        private int darkenColor(int color, float amount) {
            int red = Math.round(Color.red(color) * (1f - amount));
            int green = Math.round(Color.green(color) * (1f - amount));
            int blue = Math.round(Color.blue(color) * (1f - amount));
            return Color.rgb(clampColor(red), clampColor(green), clampColor(blue));
        }

        private int clampColor(int value) {
            return Math.max(0, Math.min(255, value));
        }

        private void drawResizeChrome(Canvas canvas) {
            if (adjustmentMode == MODE_LAYOUT) {
                drawLayoutChrome(canvas);
                return;
            }
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

        private void drawLayoutChrome(Canvas canvas) {
            int accent = Color.rgb(20, 145, 245);
            RectF bounds = previewBounds();
            float leftLineX = layoutLineX(layoutLeftPercent);
            float rightLineX = layoutLineX(layoutRightPercent);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(1.4f));
            previewPaint.setColor(Color.rgb(176, 184, 192));
            canvas.drawRect(bounds, previewPaint);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(2));
            previewPaint.setColor(accent);
            canvas.drawRect(keyboardRect, previewPaint);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(Color.argb(34, 20, 145, 245));
            canvas.drawRect(keyboardRect.left, keyboardRect.top, leftLineX, keyboardRect.bottom, previewPaint);
            canvas.drawRect(rightLineX, keyboardRect.top, keyboardRect.right, keyboardRect.bottom, previewPaint);

            drawLayoutDivider(canvas, leftLineX, true);
            drawLayoutDivider(canvas, rightLineX, false);
            drawLayoutPercentLabels(canvas, leftLineX, rightLineX);
        }

        private void drawLayoutDivider(Canvas canvas, float x, boolean activeLeftDivider) {
            boolean active = activeHandle == (activeLeftDivider ? HANDLE_LAYOUT_LEFT : HANDLE_LAYOUT_RIGHT);
            int accent = active ? Color.rgb(0, 102, 230) : Color.rgb(20, 145, 245);
            float top = keyboardRect.top + dp(8);
            float bottom = keyboardRect.bottom - dp(8);

            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(active ? dp(3.2f) : dp(2.4f));
            previewPaint.setColor(accent);
            canvas.drawLine(x, top, x, bottom, previewPaint);

            float handleWidth = dp(52);
            float handleHeight = dp(26);
            RectF handle = new RectF(x - handleWidth / 2f, keyboardRect.centerY() - handleHeight / 2f,
                    x + handleWidth / 2f, keyboardRect.centerY() + handleHeight / 2f);
            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setColor(Color.WHITE);
            canvas.drawRoundRect(handle, dp(13), dp(13), previewPaint);
            previewPaint.setStyle(Paint.Style.STROKE);
            previewPaint.setStrokeWidth(dp(1.2f));
            previewPaint.setColor(accent);
            canvas.drawRoundRect(handle, dp(13), dp(13), previewPaint);

            previewPaint.setStyle(Paint.Style.FILL);
            previewPaint.setTextAlign(Paint.Align.CENTER);
            previewPaint.setTextSize(dp(18));
            previewPaint.setColor(accent);
            drawPreviewText(canvas, "↔", handle.centerX(), handle.centerY());
        }

        private void drawLayoutPercentLabels(Canvas canvas, float leftLineX, float rightLineX) {
            int leftSpan = layoutLeftPercent;
            int centerSpan = layoutRightPercent - layoutLeftPercent;
            int rightSpan = 100 - layoutRightPercent;
            float labelY = keyboardRect.bottom + dp(18);
            drawPercentLabel(canvas, "좌 " + leftSpan + "%", (keyboardRect.left + leftLineX) / 2f, labelY, Gravity.CENTER);
            drawPercentLabel(canvas, "중 " + centerSpan + "%", (leftLineX + rightLineX) / 2f, labelY, Gravity.CENTER);
            drawPercentLabel(canvas, "우 " + rightSpan + "%", (rightLineX + keyboardRect.right) / 2f, labelY, Gravity.CENTER);
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

        private int currentModeHandleAt(float x, float y) {
            if (adjustmentMode == MODE_LAYOUT) {
                return layoutHandleAt(x, y);
            }
            return handleAt(x, y);
        }

        private int layoutHandleAt(float x, float y) {
            if (y < keyboardRect.top - dp(12) || y > keyboardRect.bottom + dp(12)) {
                return HANDLE_NONE;
            }
            float hit = dp(34);
            float leftLineX = layoutLineX(layoutLeftPercent);
            float rightLineX = layoutLineX(layoutRightPercent);
            if (Math.abs(x - leftLineX) <= hit) {
                return HANDLE_LAYOUT_LEFT;
            }
            if (Math.abs(x - rightLineX) <= hit) {
                return HANDLE_LAYOUT_RIGHT;
            }
            if (keyboardRect.contains(x, y)) {
                return Math.abs(x - leftLineX) <= Math.abs(x - rightLineX)
                        ? HANDLE_LAYOUT_LEFT
                        : HANDLE_LAYOUT_RIGHT;
            }
            return HANDLE_NONE;
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

        private void updateLayoutFromHandle(float x) {
            int xPercent = percentForPosition(x, keyboardRect.left, keyboardRect.width());
            if (activeHandle == HANDLE_LAYOUT_LEFT) {
                layoutLeftPercent = SettingsStore.boundedKeyboardLayoutLeftPercent(xPercent, layoutRightPercent);
            } else if (activeHandle == HANDLE_LAYOUT_RIGHT) {
                layoutRightPercent = SettingsStore.boundedKeyboardLayoutRightPercent(xPercent, layoutLeftPercent);
            }
            saveSize();
            invalidate();
        }

        private float layoutLineX(int percent) {
            return keyboardRect.left + keyboardRect.width() * percent / 100f;
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
