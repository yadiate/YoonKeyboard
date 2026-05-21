package com.yadiate.yoonkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import com.yadiate.yoonkeyboard.hangul.GestureCalibration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SettingsStore {
    public static final String PREF_NAME = "eight_way_ime_settings";

    public static final String KEY_SKIN = "keyboard_skin_type";
    public static final String KEY_HANGUL_TYPE = "hangul_keyboard_type";
    public static final String KEY_STROKE_LENGTH = "stroke_length";
    public static final String KEY_STROKE_CUSTOM = "stroke_custom_enabled";
    public static final String KEY_STROKE_SHORT_MM_TENTHS = "stroke_short_mm_tenths";
    public static final String KEY_STROKE_DERIVATION_SHORT_MM_TENTHS = "stroke_derivation_short_mm_tenths";
    public static final String KEY_STROKE_LONG_MM_TENTHS = "stroke_long_mm_tenths";
    private static final String KEY_STROKE_MM_SCALE_VERSION = "stroke_mm_scale_version";
    public static final String KEY_DOUBLE_TAP_TIME = "double_tap_time";
    public static final String KEY_DOUBLE_TAP_TIMEOUT_MS = "double_tap_timeout_ms";
    public static final String KEY_DELETE_REPEAT_START_MS = "delete_repeat_start_ms";
    public static final String KEY_DELETE_REPEAT_INTERVAL_MS = "delete_repeat_interval_ms";
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
    public static final String KEY_FOLD_KEYBOARD_MODE = "fold_keyboard_mode";
    public static final String KEY_HITBOX_BASE_DP = "hitbox_base_dp";
    public static final String KEY_HITBOX_LEFT_CUT_DP = "hitbox_left_cut_dp";
    public static final String KEY_HITBOX_TOP_CUT_DP = "hitbox_top_cut_dp";
    public static final String KEY_HITBOX_RIGHT_EXTRA_DP = "hitbox_right_extra_dp";
    public static final String KEY_HITBOX_BOTTOM_EXTRA_DP = "hitbox_bottom_extra_dp";
    public static final String KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP = "hitbox_left_key_right_extra_dp";
    public static final String KEY_DEBUG_TOUCH_OVERLAY = "debug_touch_overlay";
    public static final String KEY_BLOCK_YEO_UP_TO_YE = "block_yeo_up_to_ye";
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
    public static final int HANGUL_LAYOUT_YUN = 0;
    public static final int HANGUL_LAYOUT_TWO_BEOLSIK = 1;
    public static final int FOLD_KEYBOARD_MODE_BASIC = 0;
    public static final int FOLD_KEYBOARD_MODE_SPLIT = 1;
    public static final int SKIN_LIGHT = 0;
    public static final int SKIN_DARK = 1;
    public static final int SKIN_SYSTEM = 2;

    public static final String[] HANGUL_TYPES = {"윤키보드", "2벌식"};
    public static final String[] SKINS = {"화이트", "블랙", "시스템 설정 따라가기"};
    public static final String[] STROKE_LENGTHS = {"1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] VIBRATE_LEVELS = {"꺼짐", "1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] DOUBLE_TAP_TIMES = {"1-짧게", "2-보통", "3-길게"};
    public static final int MIN_CUSTOM_SHORT_MM_TENTHS = 50;
    public static final int MAX_CUSTOM_SHORT_MM_TENTHS = 800;
    public static final int MIN_CUSTOM_LONG_MM_TENTHS = 500;
    public static final int MAX_CUSTOM_LONG_MM_TENTHS = 2600;
    public static final int MIN_CUSTOM_STROKE_GAP_MM_TENTHS = 100;
    public static final int CUSTOM_STROKE_STEP_TENTHS = 25;
    public static final int MIN_DOUBLE_TAP_TIMEOUT_MS = 80;
    public static final int MAX_DOUBLE_TAP_TIMEOUT_MS = 640;
    public static final int DOUBLE_TAP_TIMEOUT_STEP_MS = 20;
    public static final int DEFAULT_DOUBLE_TAP_TIMEOUT_MS = 200;
    public static final int MIN_DELETE_REPEAT_START_MS = 60;
    public static final int MAX_DELETE_REPEAT_START_MS = 600;
    public static final int DELETE_REPEAT_START_STEP_MS = 20;
    public static final int DEFAULT_DELETE_REPEAT_START_MS = 180;
    public static final int MIN_DELETE_REPEAT_INTERVAL_MS = 16;
    public static final int MAX_DELETE_REPEAT_INTERVAL_MS = 120;
    public static final int DELETE_REPEAT_INTERVAL_STEP_MS = 4;
    public static final int DEFAULT_DELETE_REPEAT_INTERVAL_MS = 24;
    public static final int MIN_KEYBOARD_WIDTH_PERCENT = 72;
    public static final int MAX_KEYBOARD_WIDTH_PERCENT = 100;
    public static final int MIN_KEYBOARD_HEIGHT_PERCENT = 82;
    public static final int MAX_KEYBOARD_HEIGHT_PERCENT = 128;
    public static final int DEFAULT_KEYBOARD_WIDTH_PERCENT = 100;
    public static final int DEFAULT_KEYBOARD_HEIGHT_PERCENT = 88;
    public static final int MIN_KEYBOARD_EDGE_SPAN_PERCENT = 56;
    public static final int DEFAULT_KEYBOARD_LEFT_PERCENT = 0;
    public static final int DEFAULT_KEYBOARD_RIGHT_PERCENT = 100;
    public static final int DEFAULT_KEYBOARD_TOP_PERCENT = 12;
    public static final int DEFAULT_KEYBOARD_BOTTOM_PERCENT = 100;
    public static final int MIN_KEYBOARD_LAYOUT_SIDE_PERCENT = 8;
    public static final int MIN_KEYBOARD_LAYOUT_CENTER_PERCENT = 44;
    public static final int DEFAULT_KEYBOARD_LAYOUT_LEFT_PERCENT = 10;
    public static final int DEFAULT_KEYBOARD_LAYOUT_RIGHT_PERCENT = 86;
    public static final int MIN_HITBOX_BASE_DP = 0;
    public static final int MAX_HITBOX_BASE_DP = 18;
    public static final int DEFAULT_HITBOX_BASE_DP = 0;
    public static final int MIN_HITBOX_EDGE_DP = 0;
    public static final int MAX_HITBOX_EDGE_DP = 16;
    public static final int DEFAULT_HITBOX_LEFT_CUT_DP = 3;
    public static final int DEFAULT_HITBOX_TOP_CUT_DP = 3;
    public static final int DEFAULT_HITBOX_RIGHT_EXTRA_DP = 7;
    public static final int DEFAULT_HITBOX_BOTTOM_EXTRA_DP = 8;
    public static final int DEFAULT_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP = 0;
    public static final String[] FOLD_KEYBOARD_MODES = {"\uAE30\uBCF8\uBAA8\uB4DC", "\uBD84\uD560\uBAA8\uB4DC"};

    private static final int DEFAULT_SKIN = 1;
    private static final int DEFAULT_HANGUL_LAYOUT = HANGUL_LAYOUT_TWO_BEOLSIK;
    private static final int DEFAULT_FOLD_KEYBOARD_MODE = FOLD_KEYBOARD_MODE_SPLIT;
    private static final int DEFAULT_STROKE_LENGTH = 2;
    private static final int DEFAULT_DOUBLE_TAP_TIME = 0;
    private static final int DEFAULT_VIBRATE_LEVEL = 1;
    private static final int STROKE_MM_SCALE_VERSION = 2;
    private static final int LEGACY_STROKE_MM_SCALE_MULTIPLIER = 10;
    private static final String OPTIONS_EXPORT_PREFIX = "YoonKeyboardOptions:";
    private static final String OPTIONS_EXPORT_APP_ID = "com.yadiate.yoonkeyboard";
    private static final String OPTIONS_EXPORT_TYPE = "options";
    private static final int OPTIONS_EXPORT_VERSION = 1;
    private static final int[] DEFAULT_SHORT_STROKE_MM_TENTHS = {225, 275, 200, 450, 575};
    private static final int[] DEFAULT_DERIVATION_SHORT_STROKE_MM_TENTHS = {300, 375, 525, 600, 725};
    private static final int[] DEFAULT_LONG_STROKE_MM_TENTHS = {900, 1100, 875, 1650, 2000};
    private static final String DEFAULT_GESTURE_CALIBRATION_PROFILE =
            "{\"version\":1,\"samples\":28,\"global\":{\"samples\":28,\"directions\":{\"TL\":"
                    + "{\"angle\":-127.35093688964844,\"tolerance\":26.12126922607422,"
                    + "\"minDistance\":1.9250000715255737,\"samples\":3}},\"touch\":"
                    + "{\"x\":0.11602714657783508,\"y\":0.12739700078964233,"
                    + "\"slop\":0.2800000011920929,\"samples\":20}},\"consonants\":"
                    + "{\"hangul_gesture_r1_c4\":{\"samples\":1,\"directions\":{\"TL\":"
                    + "{\"angle\":-128.58839416503906,\"tolerance\":22,"
                    + "\"minDistance\":1.7000000476837158,\"samples\":2}}},"
                    + "\"hangul_gesture_r1_c1\":{\"samples\":28,\"directions\":{\"TL\":"
                    + "{\"angle\":-127.35093688964844,\"tolerance\":26.12126922607422,"
                    + "\"minDistance\":1.9250000715255737,\"samples\":3}},\"touch\":"
                    + "{\"x\":0.11602714657783508,\"y\":0.12739700078964233,"
                    + "\"slop\":0.2800000011920929,\"samples\":20}},"
                    + "\"hangul_gesture_r0_c1\":{\"samples\":1,\"directions\":{\"BL\":"
                    + "{\"angle\":129.15306091308594,\"tolerance\":19,"
                    + "\"minDistance\":1,\"samples\":2}}},"
                    + "\"hangul_gesture_r1_c2\":{\"samples\":1,\"directions\":{\"TR\":"
                    + "{\"angle\":-33.56829071044922,\"tolerance\":22,"
                    + "\"minDistance\":1,\"samples\":2},\"TL\":"
                    + "{\"angle\":-139.47450256347656,\"tolerance\":22,"
                    + "\"minDistance\":1,\"samples\":2}}}}}";
    private static final String[] IMPORT_INT_KEYS = {
            KEY_SKIN,
            KEY_HANGUL_TYPE,
            KEY_STROKE_LENGTH,
            KEY_STROKE_SHORT_MM_TENTHS,
            KEY_STROKE_DERIVATION_SHORT_MM_TENTHS,
            KEY_STROKE_LONG_MM_TENTHS,
            KEY_DOUBLE_TAP_TIME,
            KEY_DOUBLE_TAP_TIMEOUT_MS,
            KEY_DELETE_REPEAT_START_MS,
            KEY_DELETE_REPEAT_INTERVAL_MS,
            KEY_VIBRATE_LEVEL,
            KEY_KEYBOARD_WIDTH_PERCENT,
            KEY_KEYBOARD_HEIGHT_PERCENT,
            KEY_KEYBOARD_LEFT_PERCENT,
            KEY_KEYBOARD_RIGHT_PERCENT,
            KEY_KEYBOARD_TOP_PERCENT,
            KEY_KEYBOARD_BOTTOM_PERCENT,
            KEY_KEYBOARD_LAYOUT_LEFT_PERCENT,
            KEY_KEYBOARD_LAYOUT_RIGHT_PERCENT,
            KEY_FOLD_KEYBOARD_MODE,
            KEY_HITBOX_BASE_DP,
            KEY_HITBOX_LEFT_CUT_DP,
            KEY_HITBOX_TOP_CUT_DP,
            KEY_HITBOX_RIGHT_EXTRA_DP,
            KEY_HITBOX_BOTTOM_EXTRA_DP,
            KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP
    };
    private static final String[] IMPORT_BOOLEAN_KEYS = {
            KEY_STROKE_CUSTOM,
            KEY_VIBRATE_ON,
            KEY_SOUND_ON,
            KEY_DEBUG_TOUCH_OVERLAY,
            KEY_BLOCK_YEO_UP_TO_YE,
            KEY_GESTURE_CALIBRATION_ENABLED
    };
    private static final String[] IMPORT_STRING_KEYS = {
            KEY_GESTURE_CALIBRATION_PROFILE
    };

    private SettingsStore() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Snapshot load(Context context) {
        SharedPreferences prefs = prefs(context);
        ensureStrokeMmScaleVersion(prefs);
        Snapshot snapshot = new Snapshot();
        snapshot.skinIndex = bounded(prefs.getInt(KEY_SKIN, DEFAULT_SKIN), SKINS.length, DEFAULT_SKIN);
        snapshot.hangulLayoutIndex = bounded(prefs.getInt(KEY_HANGUL_TYPE, DEFAULT_HANGUL_LAYOUT),
                HANGUL_TYPES.length, DEFAULT_HANGUL_LAYOUT);
        snapshot.hangulTypeIndex = hangulTypeForOrientation(context, snapshot.hangulLayoutIndex);
        snapshot.strokeLengthIndex = bounded(prefs.getInt(KEY_STROKE_LENGTH, DEFAULT_STROKE_LENGTH), STROKE_LENGTHS.length, DEFAULT_STROKE_LENGTH);
        snapshot.customStrokeLength = true;
        int defaultShortStroke = defaultShortStrokeMmTenths(snapshot.strokeLengthIndex);
        int defaultDerivationShortStroke = defaultDerivationShortStrokeMmTenths(snapshot.strokeLengthIndex);
        int defaultLongStroke = defaultLongStrokeMmTenths(snapshot.strokeLengthIndex);
        snapshot.longStrokeMmTenths = boundedCustomLongStrokeMmTenths(
                prefs.getInt(KEY_STROKE_LONG_MM_TENTHS, defaultLongStroke),
                defaultLongStroke);
        snapshot.shortStrokeMmTenths = boundedCustomShortStrokeMmTenths(
                prefs.getInt(KEY_STROKE_SHORT_MM_TENTHS, defaultShortStroke),
                snapshot.longStrokeMmTenths,
                defaultShortStroke);
        int derivationFallback = defaultDerivationShortStrokeMmTenths(
                defaultDerivationShortStroke, snapshot.shortStrokeMmTenths, snapshot.longStrokeMmTenths);
        snapshot.derivationShortStrokeMmTenths = boundedCustomDerivationShortStrokeMmTenths(
                prefs.getInt(KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, derivationFallback),
                snapshot.shortStrokeMmTenths,
                snapshot.longStrokeMmTenths,
                derivationFallback);
        snapshot.doubleTapTimeIndex = bounded(prefs.getInt(KEY_DOUBLE_TAP_TIME, DEFAULT_DOUBLE_TAP_TIME), DOUBLE_TAP_TIMES.length, DEFAULT_DOUBLE_TAP_TIME);
        snapshot.doubleTapTimeoutMs = boundedDoubleTapTimeoutMs(
                prefs.getInt(KEY_DOUBLE_TAP_TIMEOUT_MS, legacyDoubleTapTimeoutMs(prefs)));
        snapshot.deleteRepeatStartMs = boundedDeleteRepeatStartMs(
                prefs.getInt(KEY_DELETE_REPEAT_START_MS, DEFAULT_DELETE_REPEAT_START_MS));
        snapshot.deleteRepeatIntervalMs = boundedDeleteRepeatIntervalMs(
                prefs.getInt(KEY_DELETE_REPEAT_INTERVAL_MS, DEFAULT_DELETE_REPEAT_INTERVAL_MS));
        snapshot.vibrateOn = prefs.getBoolean(KEY_VIBRATE_ON, true);
        snapshot.vibrateLevelIndex = bounded(prefs.getInt(KEY_VIBRATE_LEVEL, DEFAULT_VIBRATE_LEVEL), VIBRATE_LEVELS.length, DEFAULT_VIBRATE_LEVEL);
        snapshot.soundOn = prefs.getBoolean(KEY_SOUND_ON, false);
        snapshot.debugTouchOverlay = prefs.getBoolean(KEY_DEBUG_TOUCH_OVERLAY, false);
        snapshot.blockYeoUpToYe = prefs.getBoolean(KEY_BLOCK_YEO_UP_TO_YE, true);
        snapshot.foldPhone = isFoldPhone(context);
        snapshot.foldKeyboardModeIndex = bounded(prefs.getInt(KEY_FOLD_KEYBOARD_MODE, DEFAULT_FOLD_KEYBOARD_MODE),
                FOLD_KEYBOARD_MODES.length, DEFAULT_FOLD_KEYBOARD_MODE);
        snapshot.gestureCalibrationEnabled = prefs.getBoolean(KEY_GESTURE_CALIBRATION_ENABLED, true);
        snapshot.gestureCalibrationJson = prefs.getString(KEY_GESTURE_CALIBRATION_PROFILE,
                DEFAULT_GESTURE_CALIBRATION_PROFILE);
        snapshot.gestureCalibrationProfile = GestureCalibration.Profile.fromJson(snapshot.gestureCalibrationJson);
        snapshot.gestureCalibrationSampleCount = snapshot.gestureCalibrationProfile.totalSamples();
        loadKeyboardEdges(prefs, snapshot);
        loadKeyboardLayout(prefs, snapshot);
        loadHitbox(prefs, snapshot);
        snapshot.theme = themeFor(context, snapshot.skinIndex);
        return snapshot;
    }

    public static void putInt(Context context, String key, int value) {
        SharedPreferences prefs = prefs(context);
        ensureStrokeMmScaleVersion(prefs);
        if (KEY_STROKE_SHORT_MM_TENTHS.equals(key)
                || KEY_STROKE_DERIVATION_SHORT_MM_TENTHS.equals(key)
                || KEY_STROKE_LONG_MM_TENTHS.equals(key)) {
            int strokeIndex = bounded(prefs.getInt(KEY_STROKE_LENGTH, DEFAULT_STROKE_LENGTH),
                    STROKE_LENGTHS.length, DEFAULT_STROKE_LENGTH);
            int defaultShortStroke = defaultShortStrokeMmTenths(strokeIndex);
            int defaultDerivationShortStroke = defaultDerivationShortStrokeMmTenths(strokeIndex);
            int defaultLongStroke = defaultLongStrokeMmTenths(strokeIndex);
            int longStroke = KEY_STROKE_LONG_MM_TENTHS.equals(key)
                    ? boundedCustomLongStrokeMmTenths(value, defaultLongStroke)
                    : boundedCustomLongStrokeMmTenths(
                            prefs.getInt(KEY_STROKE_LONG_MM_TENTHS, defaultLongStroke),
                            defaultLongStroke);
            int shortStroke = KEY_STROKE_SHORT_MM_TENTHS.equals(key)
                    ? boundedCustomShortStrokeMmTenths(value, longStroke, defaultShortStroke)
                    : boundedCustomShortStrokeMmTenths(
                            prefs.getInt(KEY_STROKE_SHORT_MM_TENTHS, defaultShortStroke),
                            longStroke,
                            defaultShortStroke);
            int derivationFallback = defaultDerivationShortStrokeMmTenths(
                    defaultDerivationShortStroke, shortStroke, longStroke);
            int derivationShortStroke = KEY_STROKE_DERIVATION_SHORT_MM_TENTHS.equals(key)
                    ? boundedCustomDerivationShortStrokeMmTenths(value, shortStroke, longStroke, derivationFallback)
                    : boundedCustomDerivationShortStrokeMmTenths(
                            prefs.getInt(KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, derivationFallback),
                            shortStroke,
                            longStroke,
                            derivationFallback);
            prefs.edit()
                    .putInt(KEY_STROKE_LONG_MM_TENTHS, longStroke)
                    .putInt(KEY_STROKE_SHORT_MM_TENTHS, shortStroke)
                    .putInt(KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, derivationShortStroke)
                    .apply();
            return;
        }
        if (KEY_DELETE_REPEAT_START_MS.equals(key)) {
            prefs.edit().putInt(key, boundedDeleteRepeatStartMs(value)).apply();
            return;
        }
        if (KEY_DOUBLE_TAP_TIMEOUT_MS.equals(key)) {
            prefs.edit().putInt(key, boundedDoubleTapTimeoutMs(value)).apply();
            return;
        }
        if (KEY_DELETE_REPEAT_INTERVAL_MS.equals(key)) {
            prefs.edit().putInt(key, boundedDeleteRepeatIntervalMs(value)).apply();
            return;
        }
        if (isHitboxKey(key)) {
            prefs.edit().putInt(key, boundedHitboxDp(key, value)).apply();
            return;
        }
        if (KEY_FOLD_KEYBOARD_MODE.equals(key)) {
            prefs.edit().putInt(key,
                    bounded(value, FOLD_KEYBOARD_MODES.length, DEFAULT_FOLD_KEYBOARD_MODE)).apply();
            return;
        }
        prefs.edit().putInt(key, value).apply();
    }

    public static void putBoolean(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    public static void putString(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value).apply();
    }

    public static String exportOptions(Context context) {
        SharedPreferences prefs = prefs(context);
        ensureStrokeMmScaleVersion(prefs);
        Snapshot snapshot = load(context);
        JSONObject root = new JSONObject();
        JSONObject options = new JSONObject();
        try {
            root.put("app", OPTIONS_EXPORT_APP_ID);
            root.put("type", OPTIONS_EXPORT_TYPE);
            root.put("version", OPTIONS_EXPORT_VERSION);
            putOption(options, KEY_SKIN, snapshot.skinIndex);
            putOption(options, KEY_HANGUL_TYPE, snapshot.hangulLayoutIndex);
            putOption(options, KEY_STROKE_LENGTH, snapshot.strokeLengthIndex);
            putOption(options, KEY_STROKE_CUSTOM, snapshot.customStrokeLength);
            putOption(options, KEY_STROKE_SHORT_MM_TENTHS, snapshot.shortStrokeMmTenths);
            putOption(options, KEY_STROKE_DERIVATION_SHORT_MM_TENTHS, snapshot.derivationShortStrokeMmTenths);
            putOption(options, KEY_STROKE_LONG_MM_TENTHS, snapshot.longStrokeMmTenths);
            putOption(options, KEY_DOUBLE_TAP_TIME, snapshot.doubleTapTimeIndex);
            putOption(options, KEY_DOUBLE_TAP_TIMEOUT_MS, snapshot.doubleTapTimeoutMs);
            putOption(options, KEY_DELETE_REPEAT_START_MS, snapshot.deleteRepeatStartMs);
            putOption(options, KEY_DELETE_REPEAT_INTERVAL_MS, snapshot.deleteRepeatIntervalMs);
            putOption(options, KEY_VIBRATE_ON, snapshot.vibrateOn);
            putOption(options, KEY_VIBRATE_LEVEL, snapshot.vibrateLevelIndex);
            putOption(options, KEY_SOUND_ON, snapshot.soundOn);
            putOption(options, KEY_KEYBOARD_WIDTH_PERCENT,
                    boundedKeyboardWidthPercent(snapshot.keyboardRightPercent - snapshot.keyboardLeftPercent));
            putOption(options, KEY_KEYBOARD_HEIGHT_PERCENT,
                    boundedKeyboardHeightPercent(snapshot.keyboardBottomPercent - snapshot.keyboardTopPercent));
            putOption(options, KEY_KEYBOARD_LEFT_PERCENT, snapshot.keyboardLeftPercent);
            putOption(options, KEY_KEYBOARD_RIGHT_PERCENT, snapshot.keyboardRightPercent);
            putOption(options, KEY_KEYBOARD_TOP_PERCENT, snapshot.keyboardTopPercent);
            putOption(options, KEY_KEYBOARD_BOTTOM_PERCENT, snapshot.keyboardBottomPercent);
            putOption(options, KEY_KEYBOARD_LAYOUT_LEFT_PERCENT, snapshot.keyboardLayoutLeftPercent);
            putOption(options, KEY_KEYBOARD_LAYOUT_RIGHT_PERCENT, snapshot.keyboardLayoutRightPercent);
            putOption(options, KEY_FOLD_KEYBOARD_MODE, snapshot.foldKeyboardModeIndex);
            putOption(options, KEY_HITBOX_BASE_DP, snapshot.hitboxBaseDp);
            putOption(options, KEY_HITBOX_LEFT_CUT_DP, snapshot.hitboxLeftCutDp);
            putOption(options, KEY_HITBOX_TOP_CUT_DP, snapshot.hitboxTopCutDp);
            putOption(options, KEY_HITBOX_RIGHT_EXTRA_DP, snapshot.hitboxRightExtraDp);
            putOption(options, KEY_HITBOX_BOTTOM_EXTRA_DP, snapshot.hitboxBottomExtraDp);
            putOption(options, KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP, snapshot.hitboxLeftKeyRightExtraDp);
            putOption(options, KEY_DEBUG_TOUCH_OVERLAY, snapshot.debugTouchOverlay);
            putOption(options, KEY_BLOCK_YEO_UP_TO_YE, snapshot.blockYeoUpToYe);
            putOption(options, KEY_GESTURE_CALIBRATION_ENABLED, snapshot.gestureCalibrationEnabled);
            putOption(options, KEY_GESTURE_CALIBRATION_PROFILE, snapshot.gestureCalibrationJson);
            root.put("options", options);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return OPTIONS_EXPORT_PREFIX + root.toString();
    }

    public static int importOptions(Context context, String payload) {
        JSONObject options = parseExportedOptions(payload);
        SharedPreferences.Editor editor = prefs(context).edit();
        int imported = 0;
        for (String key : IMPORT_INT_KEYS) {
            if (copyImportedInt(options, editor, key)) {
                imported++;
            }
        }
        for (String key : IMPORT_BOOLEAN_KEYS) {
            if (copyImportedBoolean(options, editor, key)) {
                imported++;
            }
        }
        for (String key : IMPORT_STRING_KEYS) {
            if (copyImportedString(options, editor, key)) {
                imported++;
            }
        }
        if (imported == 0) {
            throw new IllegalArgumentException("No options found");
        }
        editor.putInt(KEY_STROKE_MM_SCALE_VERSION, STROKE_MM_SCALE_VERSION)
                .putBoolean(KEY_GESTURE_CALIBRATION_SESSION_ACTIVE, false)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_TARGET)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_LINE)
                .remove(KEY_GESTURE_CALIBRATION_SESSION_SAMPLES);
        if (!editor.commit()) {
            throw new IllegalStateException("Option import failed");
        }
        return imported;
    }

    private static void putOption(JSONObject options, String key, int value) throws JSONException {
        options.put(key, value);
    }

    private static void putOption(JSONObject options, String key, boolean value) throws JSONException {
        options.put(key, value);
    }

    private static void putOption(JSONObject options, String key, String value) throws JSONException {
        options.put(key, value == null ? "" : value);
    }

    private static JSONObject parseExportedOptions(String payload) {
        String text = payload == null ? "" : payload.trim();
        if (text.startsWith(OPTIONS_EXPORT_PREFIX)) {
            text = text.substring(OPTIONS_EXPORT_PREFIX.length()).trim();
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Clipboard is empty");
        }
        try {
            JSONObject root = new JSONObject(text);
            if (root.has("app") && !OPTIONS_EXPORT_APP_ID.equals(root.optString("app"))) {
                throw new IllegalArgumentException("Not YoonKeyboard options");
            }
            if (root.has("type") && !OPTIONS_EXPORT_TYPE.equals(root.optString("type"))) {
                throw new IllegalArgumentException("Not option data");
            }
            JSONObject options = root.optJSONObject("options");
            return options == null ? root : options;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Clipboard is not option data", e);
        }
    }

    private static boolean copyImportedInt(JSONObject options, SharedPreferences.Editor editor, String key) {
        if (!options.has(key)) {
            return false;
        }
        Object value = options.opt(key);
        if (value instanceof Number) {
            editor.putInt(key, ((Number) value).intValue());
            return true;
        }
        if (value instanceof String) {
            try {
                editor.putInt(key, Integer.parseInt(((String) value).trim()));
                return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static boolean copyImportedBoolean(JSONObject options, SharedPreferences.Editor editor, String key) {
        if (!options.has(key)) {
            return false;
        }
        Object value = options.opt(key);
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
            return true;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                editor.putBoolean(key, Boolean.parseBoolean(text));
                return true;
            }
        }
        return false;
    }

    private static boolean copyImportedString(JSONObject options, SharedPreferences.Editor editor, String key) {
        if (!options.has(key)) {
            return false;
        }
        Object value = options.opt(key);
        if (value == null || value == JSONObject.NULL) {
            editor.putString(key, "");
        } else {
            editor.putString(key, String.valueOf(value));
        }
        return true;
    }

    private static void ensureStrokeMmScaleVersion(SharedPreferences prefs) {
        if (prefs.getInt(KEY_STROKE_MM_SCALE_VERSION, 1) >= STROKE_MM_SCALE_VERSION) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        migrateLegacyStrokeMmValue(prefs, editor, KEY_STROKE_SHORT_MM_TENTHS);
        migrateLegacyStrokeMmValue(prefs, editor, KEY_STROKE_DERIVATION_SHORT_MM_TENTHS);
        migrateLegacyStrokeMmValue(prefs, editor, KEY_STROKE_LONG_MM_TENTHS);
        editor.putInt(KEY_STROKE_MM_SCALE_VERSION, STROKE_MM_SCALE_VERSION).apply();
    }

    private static void migrateLegacyStrokeMmValue(SharedPreferences prefs, SharedPreferences.Editor editor,
                                                   String key) {
        if (!prefs.contains(key)) {
            return;
        }
        editor.putInt(key, prefs.getInt(key, 0) * LEGACY_STROKE_MM_SCALE_MULTIPLIER);
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

    public static int defaultDerivationShortStrokeMmTenths(int strokeLengthIndex) {
        int index = bounded(strokeLengthIndex, DEFAULT_DERIVATION_SHORT_STROKE_MM_TENTHS.length,
                DEFAULT_STROKE_LENGTH);
        return DEFAULT_DERIVATION_SHORT_STROKE_MM_TENTHS[index];
    }

    public static int defaultDerivationShortStrokeMmTenths(int presetDefault, int firstShortStrokeMmTenths,
                                                           int longStrokeMmTenths) {
        int desired = Math.max(presetDefault, firstShortStrokeMmTenths + CUSTOM_STROKE_STEP_TENTHS);
        int max = maxCustomShortStrokeMmTenths(longStrokeMmTenths);
        return boundedCustomShortStrokeMmTenths(Math.min(desired, max), longStrokeMmTenths,
                Math.min(Math.max(desired, MIN_CUSTOM_SHORT_MM_TENTHS), max));
    }

    public static int defaultLongStrokeMmTenths(int strokeLengthIndex) {
        int index = bounded(strokeLengthIndex, DEFAULT_LONG_STROKE_MM_TENTHS.length, DEFAULT_STROKE_LENGTH);
        return DEFAULT_LONG_STROKE_MM_TENTHS[index];
    }

    public static int boundedCustomShortStrokeMmTenths(int value, int fallback) {
        return boundedStrokeRange(value, MIN_CUSTOM_SHORT_MM_TENTHS, MAX_CUSTOM_SHORT_MM_TENTHS,
                clamped(fallback, MIN_CUSTOM_SHORT_MM_TENTHS, MAX_CUSTOM_SHORT_MM_TENTHS));
    }

    public static int boundedCustomShortStrokeMmTenths(int value, int longStrokeMmTenths, int fallback) {
        int max = maxCustomShortStrokeMmTenths(longStrokeMmTenths);
        int safeFallback = boundedStrokeRange(fallback, MIN_CUSTOM_SHORT_MM_TENTHS, max,
                Math.min(Math.max(fallback, MIN_CUSTOM_SHORT_MM_TENTHS), max));
        return boundedStrokeRange(value, MIN_CUSTOM_SHORT_MM_TENTHS, max, safeFallback);
    }

    public static int boundedCustomDerivationShortStrokeMmTenths(int value, int firstShortStrokeMmTenths,
                                                                 int longStrokeMmTenths, int fallback) {
        int min = minCustomDerivationShortStrokeMmTenths(firstShortStrokeMmTenths, longStrokeMmTenths);
        int max = maxCustomShortStrokeMmTenths(longStrokeMmTenths);
        int safeFallback = boundedStrokeRange(fallback, min, max,
                Math.min(Math.max(fallback, min), max));
        return boundedStrokeRange(value, min, max, safeFallback);
    }

    public static int minCustomDerivationShortStrokeMmTenths(int firstShortStrokeMmTenths,
                                                             int longStrokeMmTenths) {
        int max = maxCustomShortStrokeMmTenths(longStrokeMmTenths);
        return Math.min(max, Math.max(MIN_CUSTOM_SHORT_MM_TENTHS,
                firstShortStrokeMmTenths + CUSTOM_STROKE_STEP_TENTHS));
    }

    public static int boundedCustomLongStrokeMmTenths(int value, int fallback) {
        return boundedStrokeRange(value, MIN_CUSTOM_LONG_MM_TENTHS, MAX_CUSTOM_LONG_MM_TENTHS,
                clamped(fallback, MIN_CUSTOM_LONG_MM_TENTHS, MAX_CUSTOM_LONG_MM_TENTHS));
    }

    public static int maxCustomShortStrokeMmTenths(int longStrokeMmTenths) {
        int maxByGap = longStrokeMmTenths - MIN_CUSTOM_STROKE_GAP_MM_TENTHS;
        return Math.max(MIN_CUSTOM_SHORT_MM_TENTHS, Math.min(MAX_CUSTOM_SHORT_MM_TENTHS, maxByGap));
    }

    private static int clamped(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int boundedStrokeRange(int value, int min, int max, int fallback) {
        int safeFallback = clamped(snappedStrokeMmHundredths(fallback), min, max);
        int snappedValue = snappedStrokeMmHundredths(value);
        return snappedValue >= min && snappedValue <= max ? snappedValue : safeFallback;
    }

    private static int snappedStrokeMmHundredths(int value) {
        return Math.round(value / (float) CUSTOM_STROKE_STEP_TENTHS) * CUSTOM_STROKE_STEP_TENTHS;
    }

    public static int boundedDeleteRepeatStartMs(int value) {
        return boundedRepeatMs(value, MIN_DELETE_REPEAT_START_MS, MAX_DELETE_REPEAT_START_MS,
                DELETE_REPEAT_START_STEP_MS, DEFAULT_DELETE_REPEAT_START_MS);
    }

    public static int boundedDoubleTapTimeoutMs(int value) {
        return boundedRepeatMs(value, MIN_DOUBLE_TAP_TIMEOUT_MS, MAX_DOUBLE_TAP_TIMEOUT_MS,
                DOUBLE_TAP_TIMEOUT_STEP_MS, DEFAULT_DOUBLE_TAP_TIMEOUT_MS);
    }

    public static int boundedDeleteRepeatIntervalMs(int value) {
        return boundedRepeatMs(value, MIN_DELETE_REPEAT_INTERVAL_MS, MAX_DELETE_REPEAT_INTERVAL_MS,
                DELETE_REPEAT_INTERVAL_STEP_MS, DEFAULT_DELETE_REPEAT_INTERVAL_MS);
    }

    public static int boundedHitboxDp(String key, int value) {
        return boundedRange(value, hitboxMinDp(key), hitboxMaxDp(key), hitboxDefaultDp(key));
    }

    public static int hitboxMinDp(String key) {
        return KEY_HITBOX_BASE_DP.equals(key) ? MIN_HITBOX_BASE_DP : MIN_HITBOX_EDGE_DP;
    }

    public static int hitboxMaxDp(String key) {
        return KEY_HITBOX_BASE_DP.equals(key) ? MAX_HITBOX_BASE_DP : MAX_HITBOX_EDGE_DP;
    }

    public static int hitboxDefaultDp(String key) {
        if (KEY_HITBOX_LEFT_CUT_DP.equals(key)) {
            return DEFAULT_HITBOX_LEFT_CUT_DP;
        }
        if (KEY_HITBOX_TOP_CUT_DP.equals(key)) {
            return DEFAULT_HITBOX_TOP_CUT_DP;
        }
        if (KEY_HITBOX_RIGHT_EXTRA_DP.equals(key)) {
            return DEFAULT_HITBOX_RIGHT_EXTRA_DP;
        }
        if (KEY_HITBOX_BOTTOM_EXTRA_DP.equals(key)) {
            return DEFAULT_HITBOX_BOTTOM_EXTRA_DP;
        }
        if (KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP.equals(key)) {
            return DEFAULT_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP;
        }
        return DEFAULT_HITBOX_BASE_DP;
    }

    private static int boundedRepeatMs(int value, int min, int max, int step, int fallback) {
        int safeFallback = clamped(snappedRepeatMs(fallback, min, step), min, max);
        int snappedValue = clamped(snappedRepeatMs(value, min, step), min, max);
        return value >= min && value <= max ? snappedValue : safeFallback;
    }

    private static int legacyDoubleTapTimeoutMs(SharedPreferences prefs) {
        int[] values = {DEFAULT_DOUBLE_TAP_TIMEOUT_MS, 380, 520};
        int index = bounded(prefs.getInt(KEY_DOUBLE_TAP_TIME, DEFAULT_DOUBLE_TAP_TIME),
                values.length, DEFAULT_DOUBLE_TAP_TIME);
        return values[index];
    }

    private static int snappedRepeatMs(int value, int min, int step) {
        return min + Math.round((value - min) / (float) step) * step;
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

    private static void loadHitbox(SharedPreferences prefs, Snapshot snapshot) {
        snapshot.hitboxBaseDp = boundedHitboxDp(KEY_HITBOX_BASE_DP,
                prefs.getInt(KEY_HITBOX_BASE_DP, DEFAULT_HITBOX_BASE_DP));
        snapshot.hitboxLeftCutDp = boundedHitboxDp(KEY_HITBOX_LEFT_CUT_DP,
                prefs.getInt(KEY_HITBOX_LEFT_CUT_DP, DEFAULT_HITBOX_LEFT_CUT_DP));
        snapshot.hitboxTopCutDp = boundedHitboxDp(KEY_HITBOX_TOP_CUT_DP,
                prefs.getInt(KEY_HITBOX_TOP_CUT_DP, DEFAULT_HITBOX_TOP_CUT_DP));
        snapshot.hitboxRightExtraDp = boundedHitboxDp(KEY_HITBOX_RIGHT_EXTRA_DP,
                prefs.getInt(KEY_HITBOX_RIGHT_EXTRA_DP, DEFAULT_HITBOX_RIGHT_EXTRA_DP));
        snapshot.hitboxBottomExtraDp = boundedHitboxDp(KEY_HITBOX_BOTTOM_EXTRA_DP,
                prefs.getInt(KEY_HITBOX_BOTTOM_EXTRA_DP, DEFAULT_HITBOX_BOTTOM_EXTRA_DP));
        snapshot.hitboxLeftKeyRightExtraDp = boundedHitboxDp(KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP,
                prefs.getInt(KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP, DEFAULT_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP));
    }

    public static void resetHitbox(Context context) {
        prefs(context).edit()
                .putInt(KEY_HITBOX_BASE_DP, DEFAULT_HITBOX_BASE_DP)
                .putInt(KEY_HITBOX_LEFT_CUT_DP, DEFAULT_HITBOX_LEFT_CUT_DP)
                .putInt(KEY_HITBOX_TOP_CUT_DP, DEFAULT_HITBOX_TOP_CUT_DP)
                .putInt(KEY_HITBOX_RIGHT_EXTRA_DP, DEFAULT_HITBOX_RIGHT_EXTRA_DP)
                .putInt(KEY_HITBOX_BOTTOM_EXTRA_DP, DEFAULT_HITBOX_BOTTOM_EXTRA_DP)
                .putInt(KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP, DEFAULT_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP)
                .apply();
    }

    private static boolean isHitboxKey(String key) {
        return KEY_HITBOX_BASE_DP.equals(key)
                || KEY_HITBOX_LEFT_CUT_DP.equals(key)
                || KEY_HITBOX_TOP_CUT_DP.equals(key)
                || KEY_HITBOX_RIGHT_EXTRA_DP.equals(key)
                || KEY_HITBOX_BOTTOM_EXTRA_DP.equals(key)
                || KEY_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP.equals(key);
    }

    public static String strokeMmLabel(int hundredths) {
        if (hundredths % 100 == 0) {
            return (hundredths / 100) + "mm";
        }
        int whole = hundredths / 100;
        int fraction = Math.abs(hundredths % 100);
        if (fraction % 10 == 0) {
            return whole + "." + (fraction / 10) + "mm";
        }
        return whole + "." + (fraction < 10 ? "0" : "") + fraction + "mm";
    }

    private static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static int hangulTypeForOrientation(Context context, int hangulLayoutIndex) {
        if (hangulLayoutIndex == HANGUL_LAYOUT_TWO_BEOLSIK) {
            return isLandscape(context) ? HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL : HANGUL_TYPE_TWO_BEOLSIK_VERTICAL;
        }
        return isLandscape(context) ? HANGUL_TYPE_YUN_HORIZONTAL : HANGUL_TYPE_YUN_VERTICAL;
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

    public static boolean isFoldPhone(Context context) {
        String identity = ((Build.MANUFACTURER == null ? "" : Build.MANUFACTURER) + " "
                + (Build.BRAND == null ? "" : Build.BRAND) + " "
                + (Build.MODEL == null ? "" : Build.MODEL) + " "
                + (Build.DEVICE == null ? "" : Build.DEVICE) + " "
                + (Build.PRODUCT == null ? "" : Build.PRODUCT)).toLowerCase();
        if (identity.contains("fold")) {
            return true;
        }
        if (identity.contains("samsung") && identity.contains("sm-f9")) {
            return true;
        }
        return false;
    }

    public static class Snapshot {
        public int skinIndex;
        public int hangulLayoutIndex;
        public int hangulTypeIndex;
        public int strokeLengthIndex;
        public boolean customStrokeLength;
        public int shortStrokeMmTenths;
        public int derivationShortStrokeMmTenths;
        public int longStrokeMmTenths;
        public int doubleTapTimeIndex;
        public int doubleTapTimeoutMs;
        public int deleteRepeatStartMs;
        public int deleteRepeatIntervalMs;
        public boolean vibrateOn;
        public int vibrateLevelIndex;
        public boolean soundOn;
        public boolean debugTouchOverlay;
        public boolean blockYeoUpToYe;
        public boolean foldPhone;
        public int foldKeyboardModeIndex;
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
        public int hitboxBaseDp;
        public int hitboxLeftCutDp;
        public int hitboxTopCutDp;
        public int hitboxRightExtraDp;
        public int hitboxBottomExtraDp;
        public int hitboxLeftKeyRightExtraDp;
        public KeyboardTheme theme;

        public int vibrateDurationMs() {
            int[] values = {0, 6, 10, 14, 22, 34};
            return values[bounded(vibrateLevelIndex, values.length, DEFAULT_VIBRATE_LEVEL)];
        }

        public int longPressTimeoutMs() {
            int[] values = {350, 500, 700};
            return values[bounded(doubleTapTimeIndex, values.length, DEFAULT_DOUBLE_TAP_TIME)];
        }

        public int doubleConsonantTimeoutMs() {
            return boundedDoubleTapTimeoutMs(doubleTapTimeoutMs);
        }

        public int deleteRepeatStartMs() {
            return boundedDeleteRepeatStartMs(deleteRepeatStartMs);
        }

        public int deleteRepeatIntervalMs() {
            return boundedDeleteRepeatIntervalMs(deleteRepeatIntervalMs);
        }

        public float shortStrokeMm() {
            return shortStrokeMmTenths / 100f;
        }

        public float derivationShortStrokeMm() {
            return derivationShortStrokeMmTenths / 100f;
        }

        public float longStrokeMm() {
            return longStrokeMmTenths / 100f;
        }

        public boolean foldSplitKeyboardEnabled() {
            return foldPhone && foldKeyboardModeIndex == FOLD_KEYBOARD_MODE_SPLIT;
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
