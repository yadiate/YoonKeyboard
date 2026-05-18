package com.example.eightwayime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

import com.example.eightwayime.hangul.GestureCalibration;

import java.util.List;

public class SettingsStore {
    public static final String PREF_NAME = "eight_way_ime_settings";

    public static final String KEY_SKIN = "keyboard_skin_type";
    public static final String KEY_STROKE_LENGTH = "stroke_length";
    public static final String KEY_STROKE_CUSTOM = "stroke_custom_enabled";
    public static final String KEY_STROKE_SHORT_MM_TENTHS = "stroke_short_mm_tenths";
    public static final String KEY_STROKE_LONG_MM_TENTHS = "stroke_long_mm_tenths";
    public static final String KEY_DOUBLE_TAP_TIME = "double_tap_time";
    public static final String KEY_VIBRATE_ON = "vibrate_on";
    public static final String KEY_VIBRATE_LEVEL = "vibrate_level";
    public static final String KEY_SOUND_ON = "sound_on";
    public static final String KEY_KEYBOARD_WIDTH_PERCENT = "keyboard_width_percent";
    public static final String KEY_KEYBOARD_HEIGHT_PERCENT = "keyboard_height_percent";
    public static final String KEY_KEYBOARD_LEFT_PERCENT = "keyboard_left_percent";
    public static final String KEY_KEYBOARD_RIGHT_PERCENT = "keyboard_right_percent";
    public static final String KEY_KEYBOARD_TOP_PERCENT = "keyboard_top_percent";
    public static final String KEY_KEYBOARD_BOTTOM_PERCENT = "keyboard_bottom_percent";
    public static final String KEY_KEYBOARD_LAYOUT_LEFT_PERCENT = "keyboard_layout_left_percent";
    public static final String KEY_KEYBOARD_LAYOUT_RIGHT_PERCENT = "keyboard_layout_right_percent";
    public static final String KEY_GESTURE_CALIBRATION_ENABLED = "gesture_calibration_enabled";
    public static final String KEY_GESTURE_CALIBRATION_PROFILE = "gesture_calibration_profile";
    public static final String KEY_GESTURE_CALIBRATION_SESSION_ACTIVE = "gesture_calibration_session_active";
    public static final String KEY_GESTURE_CALIBRATION_SESSION_TARGET = "gesture_calibration_session_target";
    public static final String KEY_GESTURE_CALIBRATION_SESSION_LINE = "gesture_calibration_session_line";
    public static final String KEY_GESTURE_CALIBRATION_SESSION_SAMPLES = "gesture_calibration_session_samples";
    public static final String KEY_SENTENCE_PREFIX = "sentence";
    public static final String KEY_MY_INFO_PREFIX = "myinfo";

    public static final int HANGUL_TYPE_YUN_VERTICAL = 0;
    public static final int HANGUL_TYPE_TWO_BEOLSIK_VERTICAL = 1;
    public static final int HANGUL_TYPE_YUN_HORIZONTAL = 2;
    public static final int HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL = 3;
    public static final int SKIN_LIGHT = 0;
    public static final int SKIN_DARK = 1;
    public static final int SKIN_SYSTEM = 2;

    public static final String[] SKINS = {"화이트", "블랙", "시스템 설정 따라가기"};
    public static final String[] STROKE_LENGTHS = {"1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] VIBRATE_LEVELS = {"꺼짐", "1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] DOUBLE_TAP_TIMES = {"1-짧게", "2-보통", "3-길게"};
    public static final int MIN_CUSTOM_SHORT_MM_TENTHS = 20;
    public static final int MAX_CUSTOM_SHORT_MM_TENTHS = 80;
    public static final int MIN_CUSTOM_LONG_MM_TENTHS = 90;
    public static final int MAX_CUSTOM_LONG_MM_TENTHS = 260;
    public static final int CUSTOM_STROKE_STEP_TENTHS = 5;
    public static final int MIN_KEYBOARD_WIDTH_PERCENT = 72;
    public static final int MAX_KEYBOARD_WIDTH_PERCENT = 100;
    public static final int MIN_KEYBOARD_HEIGHT_PERCENT = 82;
    public static final int MAX_KEYBOARD_HEIGHT_PERCENT = 128;
    public static final int DEFAULT_KEYBOARD_WIDTH_PERCENT = 100;
    public static final int DEFAULT_KEYBOARD_HEIGHT_PERCENT = 100;
    public static final int MIN_KEYBOARD_EDGE_SPAN_PERCENT = 56;
    public static final int DEFAULT_KEYBOARD_LEFT_PERCENT = 0;
    public static final int DEFAULT_KEYBOARD_RIGHT_PERCENT = 100;
    public static final int DEFAULT_KEYBOARD_TOP_PERCENT = 0;
    public static final int DEFAULT_KEYBOARD_BOTTOM_PERCENT = 100;
    public static final int MIN_KEYBOARD_LAYOUT_SIDE_PERCENT = 8;
    public static final int MIN_KEYBOARD_LAYOUT_CENTER_PERCENT = 44;
    public static final int DEFAULT_KEYBOARD_LAYOUT_LEFT_PERCENT = 15;
    public static final int DEFAULT_KEYBOARD_LAYOUT_RIGHT_PERCENT = 81;

    private static final int DEFAULT_SKIN = 0;
    private static final int DEFAULT_STROKE_LENGTH = 2;
    private static final int DEFAULT_DOUBLE_TAP_TIME = 1;
    private static final int DEFAULT_VIBRATE_LEVEL = 1;
    private static final int[] DEFAULT_SHORT_STROKE_MM_TENTHS = {22, 28, 36, 46, 58};
    private static final int[] DEFAULT_LONG_STROKE_MM_TENTHS = {90, 110, 135, 165, 200};

    private SettingsStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Snapshot load(Context context) {
        SharedPreferences prefs = prefs(context);
        Snapshot snapshot = new Snapshot();
        snapshot.skinIndex = bounded(prefs.getInt(KEY_SKIN, DEFAULT_SKIN), SKINS.length, DEFAULT_SKIN);
        snapshot.hangulTypeIndex = isLandscape(context) ? HANGUL_TYPE_YUN_HORIZONTAL : HANGUL_TYPE_YUN_VERTICAL;
        snapshot.strokeLengthIndex = bounded(prefs.getInt(KEY_STROKE_LENGTH, DEFAULT_STROKE_LENGTH), STROKE_LENGTHS.length, DEFAULT_STROKE_LENGTH);
        snapshot.customStrokeLength = true;
        int defaultShortStroke = defaultShortStrokeMmTenths(snapshot.strokeLengthIndex);
        int defaultLongStroke = defaultLongStrokeMmTenths(snapshot.strokeLengthIndex);
        snapshot.shortStrokeMmTenths = boundedRange(prefs.getInt(KEY_STROKE_SHORT_MM_TENTHS, defaultShortStroke),
                MIN_CUSTOM_SHORT_MM_TENTHS, MAX_CUSTOM_SHORT_MM_TENTHS, defaultShortStroke);
        snapshot.longStrokeMmTenths = boundedRange(prefs.getInt(KEY_STROKE_LONG_MM_TENTHS, defaultLongStroke),
                MIN_CUSTOM_LONG_MM_TENTHS, MAX_CUSTOM_LONG_MM_TENTHS, defaultLongStroke);
        snapshot.doubleTapTimeIndex = bounded(prefs.getInt(KEY_DOUBLE_TAP_TIME, DEFAULT_DOUBLE_TAP_TIME), DOUBLE_TAP_TIMES.length, DEFAULT_DOUBLE_TAP_TIME);
        snapshot.vibrateOn = prefs.getBoolean(KEY_VIBRATE_ON, true);
        snapshot.vibrateLevelIndex = bounded(prefs.getInt(KEY_VIBRATE_LEVEL, DEFAULT_VIBRATE_LEVEL), VIBRATE_LEVELS.length, DEFAULT_VIBRATE_LEVEL);
        snapshot.soundOn = prefs.getBoolean(KEY_SOUND_ON, false);
        snapshot.gestureCalibrationEnabled = prefs.getBoolean(KEY_GESTURE_CALIBRATION_ENABLED, false);
        snapshot.gestureCalibrationJson = prefs.getString(KEY_GESTURE_CALIBRATION_PROFILE, "");
        snapshot.gestureCalibrationProfile = GestureCalibration.Profile.fromJson(snapshot.gestureCalibrationJson);
        snapshot.gestureCalibrationSampleCount = snapshot.gestureCalibrationProfile.totalSamples();
        loadKeyboardEdges(prefs, snapshot);
        loadKeyboardLayout(prefs, snapshot);
        snapshot.theme = themeFor(context, snapshot.skinIndex);
        return snapshot;
    }

    public static void putInt(Context context, String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    public static void putBoolean(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    public static void putString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value).apply();
    }

    public static void clearGestureCalibration(Context context) {
        prefs(context).edit()
                .remove(KEY_GESTURE_CALIBRATION_PROFILE)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES)
                .putBoolean(KEY_GESTURE_CALIBRATION_ENABLED, false)
                .putBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, false)
                .apply();
    }

    public static void startGestureCalibrationSession(Context context, String target, int lineIndex) {
        prefs(context).edit()
                .putBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, true)
                .putString(KEY_GESTURE_CALIBRATION_SESSION_TARGET, target == null ? "" : target)
                .putInt(KEY_GESTURE_CALIBRATION_SESSION_LINE, lineIndex)
                .putString(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES, "[]")
                .apply();
    }

    public static void updateGestureCalibrationSession(Context context, String target, int lineIndex) {
        prefs(context).edit()
                .putBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, true)
                .putString(KEY_GESTURE_CALIBRATION_SESSION_TARGET, target == null ? "" : target)
                .putInt(KEY_GESTURE_CALIBRATION_SESSION_LINE, lineIndex)
                .apply();
    }

    public static void endGestureCalibrationSession(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, false)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_TARGET)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_LINE)
                .apply();
    }

    public static boolean isGestureCalibrationSessionActive(Context context) {
        return prefs(context).getBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, false);
    }

    public static String gestureCalibrationSessionTarget(Context context) {
        return prefs(context).getString(KEY_GESTURE_CALIBRATION_SESSION_TARGET, "");
    }

    public static synchronized void appendGestureCalibrationSample(Context context,
                                                                  GestureCalibration.Sample sample) {
        if (sample == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        List<GestureCalibration.Sample> samples = GestureCalibration.samplesFromJson(
                prefs.getString(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES, "[]"));
        samples.add(sample);
        prefs.edit()
                .putString(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES,
                        GestureCalibration.samplesToJson(samples))
                .commit();
    }

    public static List<GestureCalibration.Sample> loadGestureCalibrationSamples(Context context) {
        return GestureCalibration.samplesFromJson(
                prefs(context).getString(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES, "[]"));
    }

    public static int gestureCalibrationSessionSampleCount(Context context) {
        return loadGestureCalibrationSamples(context).size();
    }

    public static String getSentence(Context context, int index) {
        return prefs(context).getString(KEY_SENTENCE_PREFIX + index, "");
    }

    public static String getMyInfo(Context context, int index) {
        return prefs(context).getString(KEY_MY_INFO_PREFIX + index, "");
    }

    public static String firstNonEmptySentence(Context context) {
        for (int i = 0; i < 14; i++) {
            String value = getSentence(context, i).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public static String firstNonEmptyMyInfo(Context context) {
        for (int i = 0; i < 4; i++) {
            String value = getMyInfo(context, i).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static int bounded(int value, int count, int fallback) {
        return value >= 0 && value < count ? value : fallback;
    }

    private static int boundedRange(int value, int min, int max, int fallback) {
        return value >= min && value <= max ? value : fallback;
    }

    public static int defaultShortStrokeMmTenths(int strokeLengthIndex) {
        int index = bounded(strokeLengthIndex, DEFAULT_SHORT_STROKE_MM_TENTHS.length, DEFAULT_STROKE_LENGTH);
        return DEFAULT_SHORT_STROKE_MM_TENTHS[index];
    }

    public static int defaultLongStrokeMmTenths(int strokeLengthIndex) {
        int index = bounded(strokeLengthIndex, DEFAULT_LONG_STROKE_MM_TENTHS.length, DEFAULT_STROKE_LENGTH);
        return DEFAULT_LONG_STROKE_MM_TENTHS[index];
    }

    public static int boundedCustomShortStrokeMmTenths(int value, int fallback) {
        return boundedRange(value, MIN_CUSTOM_SHORT_MM_TENTHS, MAX_CUSTOM_SHORT_MM_TENTHS, fallback);
    }

    public static int boundedCustomLongStrokeMmTenths(int value, int fallback) {
        return boundedRange(value, MIN_CUSTOM_LONG_MM_TENTHS, MAX_CUSTOM_LONG_MM_TENTHS, fallback);
    }

    public static int boundedKeyboardWidthPercent(int value) {
        return boundedRange(value, MIN_KEYBOARD_WIDTH_PERCENT, MAX_KEYBOARD_WIDTH_PERCENT,
                DEFAULT_KEYBOARD_WIDTH_PERCENT);
    }

    public static int boundedKeyboardHeightPercent(int value) {
        return boundedRange(value, MIN_KEYBOARD_HEIGHT_PERCENT, MAX_KEYBOARD_HEIGHT_PERCENT,
                DEFAULT_KEYBOARD_HEIGHT_PERCENT);
    }

    public static int boundedKeyboardLeftPercent(int value, int rightPercent) {
        return Math.max(0, Math.min(value, rightPercent - MIN_KEYBOARD_EDGE_SPAN_PERCENT));
    }

    public static int boundedKeyboardRightPercent(int value, int leftPercent) {
        return Math.min(100, Math.max(value, leftPercent + MIN_KEYBOARD_EDGE_SPAN_PERCENT));
    }

    public static int boundedKeyboardTopPercent(int value, int bottomPercent) {
        return Math.max(0, Math.min(value, bottomPercent - MIN_KEYBOARD_EDGE_SPAN_PERCENT));
    }

    public static int boundedKeyboardBottomPercent(int value, int topPercent) {
        return Math.min(100, Math.max(value, topPercent + MIN_KEYBOARD_EDGE_SPAN_PERCENT));
    }

    public static int boundedKeyboardLayoutLeftPercent(int value, int rightPercent) {
        return Math.max(MIN_KEYBOARD_LAYOUT_SIDE_PERCENT,
                Math.min(value, rightPercent - MIN_KEYBOARD_LAYOUT_CENTER_PERCENT));
    }

    public static int boundedKeyboardLayoutRightPercent(int value, int leftPercent) {
        return Math.min(100 - MIN_KEYBOARD_LAYOUT_SIDE_PERCENT,
                Math.max(value, leftPercent + MIN_KEYBOARD_LAYOUT_CENTER_PERCENT));
    }

    private static void loadKeyboardEdges(SharedPreferences prefs, Snapshot snapshot) {
        if (prefs.contains(KEY_KEYBOARD_LEFT_PERCENT)
                || prefs.contains(KEY_KEYBOARD_RIGHT_PERCENT)
                || prefs.contains(KEY_KEYBOARD_TOP_PERCENT)
                || prefs.contains(KEY_KEYBOARD_BOTTOM_PERCENT)) {
            snapshot.keyboardLeftPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_LEFT_PERCENT,
                    DEFAULT_KEYBOARD_LEFT_PERCENT), 0, 100, DEFAULT_KEYBOARD_LEFT_PERCENT);
            snapshot.keyboardRightPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_RIGHT_PERCENT,
                    DEFAULT_KEYBOARD_RIGHT_PERCENT), 0, 100, DEFAULT_KEYBOARD_RIGHT_PERCENT);
            snapshot.keyboardTopPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_TOP_PERCENT,
                    DEFAULT_KEYBOARD_TOP_PERCENT), 0, 100, DEFAULT_KEYBOARD_TOP_PERCENT);
            snapshot.keyboardBottomPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_BOTTOM_PERCENT,
                    DEFAULT_KEYBOARD_BOTTOM_PERCENT), 0, 100, DEFAULT_KEYBOARD_BOTTOM_PERCENT);
        } else {
            int widthPercent = boundedKeyboardWidthPercent(
                    prefs.getInt(KEY_KEYBOARD_WIDTH_PERCENT, DEFAULT_KEYBOARD_WIDTH_PERCENT));
            int heightPercent = boundedKeyboardHeightPercent(
                    prefs.getInt(KEY_KEYBOARD_HEIGHT_PERCENT, DEFAULT_KEYBOARD_HEIGHT_PERCENT));
            int horizontalInset = Math.max(0, (100 - widthPercent) / 2);
            snapshot.keyboardLeftPercent = horizontalInset;
            snapshot.keyboardRightPercent = 100 - horizontalInset;
            snapshot.keyboardTopPercent = Math.max(0, 100 - Math.min(heightPercent, 100));
            snapshot.keyboardBottomPercent = DEFAULT_KEYBOARD_BOTTOM_PERCENT;
        }
        snapshot.keyboardLeftPercent = boundedKeyboardLeftPercent(snapshot.keyboardLeftPercent,
                Math.max(snapshot.keyboardRightPercent, MIN_KEYBOARD_EDGE_SPAN_PERCENT));
        snapshot.keyboardRightPercent = boundedKeyboardRightPercent(snapshot.keyboardRightPercent,
                snapshot.keyboardLeftPercent);
        snapshot.keyboardTopPercent = boundedKeyboardTopPercent(snapshot.keyboardTopPercent,
                Math.max(snapshot.keyboardBottomPercent, MIN_KEYBOARD_EDGE_SPAN_PERCENT));
        snapshot.keyboardBottomPercent = boundedKeyboardBottomPercent(snapshot.keyboardBottomPercent,
                snapshot.keyboardTopPercent);
    }

    private static void loadKeyboardLayout(SharedPreferences prefs, Snapshot snapshot) {
        snapshot.keyboardLayoutLeftPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_LAYOUT_LEFT_PERCENT,
                DEFAULT_KEYBOARD_LAYOUT_LEFT_PERCENT), MIN_KEYBOARD_LAYOUT_SIDE_PERCENT,
                100 - MIN_KEYBOARD_LAYOUT_SIDE_PERCENT - MIN_KEYBOARD_LAYOUT_CENTER_PERCENT,
                DEFAULT_KEYBOARD_LAYOUT_LEFT_PERCENT);
        snapshot.keyboardLayoutRightPercent = boundedRange(prefs.getInt(KEY_KEYBOARD_LAYOUT_RIGHT_PERCENT,
                DEFAULT_KEYBOARD_LAYOUT_RIGHT_PERCENT),
                snapshot.keyboardLayoutLeftPercent + MIN_KEYBOARD_LAYOUT_CENTER_PERCENT,
                100 - MIN_KEYBOARD_LAYOUT_SIDE_PERCENT,
                DEFAULT_KEYBOARD_LAYOUT_RIGHT_PERCENT);
        snapshot.keyboardLayoutLeftPercent = boundedKeyboardLayoutLeftPercent(snapshot.keyboardLayoutLeftPercent,
                snapshot.keyboardLayoutRightPercent);
        snapshot.keyboardLayoutRightPercent = boundedKeyboardLayoutRightPercent(snapshot.keyboardLayoutRightPercent,
                snapshot.keyboardLayoutLeftPercent);
    }

    public static String strokeMmLabel(int tenths) {
        if (tenths % 10 == 0) {
            return (tenths / 10) + "mm";
        }
        return (tenths / 10) + "." + Math.abs(tenths % 10) + "mm";
    }

    private static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static KeyboardTheme themeFor(Context context, int index) {
        boolean dark = index == SKIN_DARK || (index == SKIN_SYSTEM && isSystemNightMode(context));
        int[] row = dark
                ? new int[]{0x252A2D, 0x464E52, 0x333B3F, 0x536065, 0xF5F7F8, 0xC9D0D4, 0x202629, 0x151A1D, 0x5B8FEF, 0xFFFFFF}
                : new int[]{0xDDE2E5, 0xFAFAFA, 0xC8D0D5, 0xE8EEF1, 0x202124, 0x5F666D, 0xC8CDD1, 0xAAB2B8, 0xD6DEE3, 0x202124};
        return new KeyboardTheme(
                Color.rgb((row[0] >> 16) & 0xff, (row[0] >> 8) & 0xff, row[0] & 0xff),
                Color.rgb((row[1] >> 16) & 0xff, (row[1] >> 8) & 0xff, row[1] & 0xff),
                Color.rgb((row[2] >> 16) & 0xff, (row[2] >> 8) & 0xff, row[2] & 0xff),
                Color.rgb((row[3] >> 16) & 0xff, (row[3] >> 8) & 0xff, row[3] & 0xff),
                Color.rgb((row[4] >> 16) & 0xff, (row[4] >> 8) & 0xff, row[4] & 0xff),
                Color.rgb((row[5] >> 16) & 0xff, (row[5] >> 8) & 0xff, row[5] & 0xff),
                Color.rgb((row[6] >> 16) & 0xff, (row[6] >> 8) & 0xff, row[6] & 0xff),
                Color.rgb((row[7] >> 16) & 0xff, (row[7] >> 8) & 0xff, row[7] & 0xff),
                Color.rgb((row[8] >> 16) & 0xff, (row[8] >> 8) & 0xff, row[8] & 0xff),
                Color.rgb((row[9] >> 16) & 0xff, (row[9] >> 8) & 0xff, row[9] & 0xff)
        );
    }

    private static boolean isSystemNightMode(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static class Snapshot {
        public int skinIndex;
        public int hangulTypeIndex;
        public int strokeLengthIndex;
        public boolean customStrokeLength;
        public int shortStrokeMmTenths;
        public int longStrokeMmTenths;
        public int doubleTapTimeIndex;
        public boolean vibrateOn;
        public int vibrateLevelIndex;
        public boolean soundOn;
        public boolean gestureCalibrationEnabled;
        public String gestureCalibrationJson;
        public GestureCalibration.Profile gestureCalibrationProfile;
        public int gestureCalibrationSampleCount;
        public int keyboardLeftPercent;
        public int keyboardRightPercent;
        public int keyboardTopPercent;
        public int keyboardBottomPercent;
        public int keyboardLayoutLeftPercent;
        public int keyboardLayoutRightPercent;
        public KeyboardTheme theme;

        public int vibrateDurationMs() {
            int[] values = {0, 6, 10, 14, 22, 34};
            return values[bounded(vibrateLevelIndex, values.length, DEFAULT_VIBRATE_LEVEL)];
        }

        public int longPressTimeoutMs() {
            int[] values = {350, 500, 700};
            return values[bounded(doubleTapTimeIndex, values.length, DEFAULT_DOUBLE_TAP_TIME)];
        }

        public float shortStrokeMm() {
            return shortStrokeMmTenths / 10f;
        }

        public float longStrokeMm() {
            return longStrokeMmTenths / 10f;
        }

        public float keyboardLeftInsetScale() {
            return keyboardLeftPercent / 100f;
        }

        public float keyboardRightEdgeScale() {
            return keyboardRightPercent / 100f;
        }

        public float keyboardHeightScale() {
            return Math.max(MIN_KEYBOARD_EDGE_SPAN_PERCENT, keyboardBottomPercent - keyboardTopPercent) / 100f;
        }

        public float hangulLeftColumnWeight(float centerWeight) {
            return layoutSideWeight(keyboardLayoutLeftPercent, centerWeight);
        }

        public float hangulRightColumnWeight(float centerWeight) {
            return layoutSideWeight(100 - keyboardLayoutRightPercent, centerWeight);
        }

        private float layoutSideWeight(int sidePercent, float centerWeight) {
            int centerPercent = Math.max(1, keyboardLayoutRightPercent - keyboardLayoutLeftPercent);
            return Math.max(0.35f, centerWeight * sidePercent / centerPercent);
        }
    }

    public static class KeyboardTheme {
        public final int background;
        public final int keyNormal;
        public final int keySpecial;
        public final int keyPressed;
        public final int text;
        public final int hint;
        public final int stroke;
        public final int shadow;
        public final int enterKey;
        public final int enterText;

        KeyboardTheme(int background, int keyNormal, int keySpecial, int keyPressed, int text, int hint,
                      int stroke, int shadow, int enterKey, int enterText) {
            this.background = background;
            this.keyNormal = keyNormal;
            this.keySpecial = keySpecial;
            this.keyPressed = keyPressed;
            this.text = text;
            this.hint = hint;
            this.stroke = stroke;
            this.shadow = shadow;
            this.enterKey = enterKey;
            this.enterText = enterText;
        }
    }
}
