package com.example.eightwayime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;

public class SettingsStore {
    public static final String PREF_NAME = "eight_way_ime_settings";

    public static final String KEY_SKIN = "keyboard_skin_type";
    public static final String KEY_HANGUL_TYPE = "keyboard_type_hangul";
    public static final String KEY_HANGUL_PORTRAIT_TYPE = "keyboard_type_hangul_portrait";
    public static final String KEY_HANGUL_LANDSCAPE_TYPE = "keyboard_type_hangul_landscape";
    public static final String KEY_ENGLISH_TYPE = "keyboard_type_eng";
    public static final String KEY_ENGLISH_PORTRAIT_TYPE = "keyboard_type_eng_portrait";
    public static final String KEY_ENGLISH_LANDSCAPE_TYPE = "keyboard_type_eng_landscape";
    public static final String KEY_NUMBER_TYPE = "keyboard_type_num";
    public static final String KEY_HANGUL_FONT = "keyboard_hangul_font";
    public static final String KEY_ENGLISH_FONT = "keyboard_eng_font";
    public static final String KEY_NUMBER_FONT = "keyboard_num_font";
    public static final String KEY_STROKE_LENGTH = "stroke_length";
    public static final String KEY_STROKE_CUSTOM = "stroke_custom_enabled";
    public static final String KEY_STROKE_SHORT_MM_TENTHS = "stroke_short_mm_tenths";
    public static final String KEY_STROKE_LONG_MM_TENTHS = "stroke_long_mm_tenths";
    public static final String KEY_DOUBLE_TAP_TIME = "double_tap_time";
    public static final String KEY_VIBRATE_ON = "vibrate_on";
    public static final String KEY_VIBRATE_LEVEL = "vibrate_level";
    public static final String KEY_SOUND_ON = "sound_on";
    public static final String KEY_SPEECH_ON = "speech_on";
    public static final String KEY_KEYPAD_UP_AFTER_SPEECH = "keypad_up_after_speech_recog";
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
    public static final String[] HANGUL_TYPES = {"윤키보드", "2벌식"};
    public static final String[] ENGLISH_TYPES = {"윤키보드", "Qwerty"};
    public static final String[] NUMBER_TYPES = {"전화기패드", "컴퓨터패드"};
    public static final String[] FONTS = {
            "고딕", "경기천년바탕검정", "경기천년바탕네온", "경기천년바탕흰색",
            "제주고딕검정", "제주고딕네온", "제주고딕민트", "제주고딕보라",
            "제주고딕오렌지", "제주고딕초록", "제주고딕파랑", "제주고딕흰색"
    };
    public static final String[] STROKE_LENGTHS = {"1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] VIBRATE_LEVELS = {"꺼짐", "1-아주짧게", "2-짧게", "3-보통", "4-길게", "5-아주길게"};
    public static final String[] DOUBLE_TAP_TIMES = {"1-짧게", "2-보통", "3-길게"};
    public static final int MIN_CUSTOM_SHORT_MM_TENTHS = 20;
    public static final int MAX_CUSTOM_SHORT_MM_TENTHS = 80;
    public static final int MIN_CUSTOM_LONG_MM_TENTHS = 90;
    public static final int MAX_CUSTOM_LONG_MM_TENTHS = 260;
    public static final int CUSTOM_STROKE_STEP_TENTHS = 5;

    private static final int DEFAULT_SKIN = 0;
    private static final int DEFAULT_HANGUL_TYPE = 0;
    private static final int DEFAULT_HANGUL_PORTRAIT_TYPE = 0;
    private static final int DEFAULT_HANGUL_LANDSCAPE_TYPE = 0;
    private static final int DEFAULT_ENGLISH_TYPE = 1;
    private static final int DEFAULT_ENGLISH_PORTRAIT_TYPE = 1;
    private static final int DEFAULT_ENGLISH_LANDSCAPE_TYPE = 1;
    private static final int DEFAULT_NUMBER_TYPE = 0;
    private static final int DEFAULT_FONT = 0;
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
        snapshot.hangulPortraitTypeIndex = loadOrientationType(prefs, KEY_HANGUL_PORTRAIT_TYPE, HANGUL_TYPES.length,
                defaultHangulPortraitType(prefs));
        snapshot.hangulLandscapeTypeIndex = loadOrientationType(prefs, KEY_HANGUL_LANDSCAPE_TYPE, HANGUL_TYPES.length,
                defaultHangulLandscapeType(prefs));
        snapshot.hangulTypeIndex = effectiveHangulType(context, snapshot.hangulPortraitTypeIndex,
                snapshot.hangulLandscapeTypeIndex);
        snapshot.englishPortraitTypeIndex = loadOrientationType(prefs, KEY_ENGLISH_PORTRAIT_TYPE, ENGLISH_TYPES.length,
                defaultEnglishPortraitType(prefs));
        snapshot.englishLandscapeTypeIndex = loadOrientationType(prefs, KEY_ENGLISH_LANDSCAPE_TYPE, ENGLISH_TYPES.length,
                defaultEnglishLandscapeType(prefs));
        snapshot.englishTypeIndex = isLandscape(context)
                ? snapshot.englishLandscapeTypeIndex
                : snapshot.englishPortraitTypeIndex;
        snapshot.numberTypeIndex = bounded(prefs.getInt(KEY_NUMBER_TYPE, DEFAULT_NUMBER_TYPE), NUMBER_TYPES.length, DEFAULT_NUMBER_TYPE);
        snapshot.hangulFontIndex = bounded(prefs.getInt(KEY_HANGUL_FONT, DEFAULT_FONT), FONTS.length, DEFAULT_FONT);
        snapshot.englishFontIndex = bounded(prefs.getInt(KEY_ENGLISH_FONT, DEFAULT_FONT), FONTS.length, DEFAULT_FONT);
        snapshot.numberFontIndex = bounded(prefs.getInt(KEY_NUMBER_FONT, DEFAULT_FONT), FONTS.length, DEFAULT_FONT);
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
        snapshot.speechOn = prefs.getBoolean(KEY_SPEECH_ON, false);
        snapshot.keypadUpAfterSpeech = prefs.getBoolean(KEY_KEYPAD_UP_AFTER_SPEECH, true);
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

    public static String strokeMmLabel(int tenths) {
        if (tenths % 10 == 0) {
            return (tenths / 10) + "mm";
        }
        return (tenths / 10) + "." + Math.abs(tenths % 10) + "mm";
    }

    private static int loadOrientationType(SharedPreferences prefs, String key, int count, int fallback) {
        return bounded(prefs.getInt(key, fallback), count, fallback);
    }

    private static int defaultHangulPortraitType(SharedPreferences prefs) {
        int legacy = prefs.getInt(KEY_HANGUL_TYPE, DEFAULT_HANGUL_TYPE);
        return legacy == HANGUL_TYPE_TWO_BEOLSIK_VERTICAL ? 1 : DEFAULT_HANGUL_PORTRAIT_TYPE;
    }

    private static int defaultHangulLandscapeType(SharedPreferences prefs) {
        int legacy = prefs.getInt(KEY_HANGUL_TYPE, DEFAULT_HANGUL_TYPE);
        return legacy == HANGUL_TYPE_TWO_BEOLSIK_VERTICAL || legacy == HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL ? 1
                : DEFAULT_HANGUL_LANDSCAPE_TYPE;
    }

    private static int defaultEnglishPortraitType(SharedPreferences prefs) {
        int legacy = prefs.getInt(KEY_ENGLISH_TYPE, DEFAULT_ENGLISH_TYPE);
        return legacy == 0 ? 0 : DEFAULT_ENGLISH_PORTRAIT_TYPE;
    }

    private static int defaultEnglishLandscapeType(SharedPreferences prefs) {
        int legacy = prefs.getInt(KEY_ENGLISH_TYPE, DEFAULT_ENGLISH_TYPE);
        return legacy == 0 ? 0 : DEFAULT_ENGLISH_LANDSCAPE_TYPE;
    }

    private static int effectiveHangulType(Context context, int portraitType, int landscapeType) {
        boolean useLandscape = isLandscape(context);
        int selected = useLandscape ? landscapeType : portraitType;
        if (selected == 1) {
            return useLandscape ? HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL : HANGUL_TYPE_TWO_BEOLSIK_VERTICAL;
        }
        return useLandscape ? HANGUL_TYPE_YUN_HORIZONTAL : HANGUL_TYPE_YUN_VERTICAL;
    }

    private static boolean isLandscape(Context context) {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static KeyboardTheme themeFor(Context context, int index) {
        boolean dark = index == SKIN_DARK || (index == SKIN_SYSTEM && isSystemNightMode(context));
        int[] row = dark
                ? new int[]{0x2D2D2F, 0x595A5C, 0x202123, 0x6A6B6D, 0xF2F2F2, 0xBFC1C4, 0x3D3E40}
                : new int[]{0xE5E7EA, 0xFFFFFF, 0xF8F9FA, 0xE0E3E7, 0x2B2D30, 0x7C828A, 0xC8CDD3};
        return new KeyboardTheme(
                Color.rgb((row[0] >> 16) & 0xff, (row[0] >> 8) & 0xff, row[0] & 0xff),
                Color.rgb((row[1] >> 16) & 0xff, (row[1] >> 8) & 0xff, row[1] & 0xff),
                Color.rgb((row[2] >> 16) & 0xff, (row[2] >> 8) & 0xff, row[2] & 0xff),
                Color.rgb((row[3] >> 16) & 0xff, (row[3] >> 8) & 0xff, row[3] & 0xff),
                Color.rgb((row[4] >> 16) & 0xff, (row[4] >> 8) & 0xff, row[4] & 0xff),
                Color.rgb((row[5] >> 16) & 0xff, (row[5] >> 8) & 0xff, row[5] & 0xff),
                Color.rgb((row[6] >> 16) & 0xff, (row[6] >> 8) & 0xff, row[6] & 0xff)
        );
    }

    private static boolean isSystemNightMode(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static class Snapshot {
        public int skinIndex;
        public int hangulTypeIndex;
        public int hangulPortraitTypeIndex;
        public int hangulLandscapeTypeIndex;
        public int englishTypeIndex;
        public int englishPortraitTypeIndex;
        public int englishLandscapeTypeIndex;
        public int numberTypeIndex;
        public int hangulFontIndex;
        public int englishFontIndex;
        public int numberFontIndex;
        public int strokeLengthIndex;
        public boolean customStrokeLength;
        public int shortStrokeMmTenths;
        public int longStrokeMmTenths;
        public int doubleTapTimeIndex;
        public boolean vibrateOn;
        public int vibrateLevelIndex;
        public boolean soundOn;
        public boolean speechOn;
        public boolean keypadUpAfterSpeech;
        public KeyboardTheme theme;

        public int vibrateDurationMs() {
            int[] values = {0, 6, 10, 14, 22, 34};
            return values[bounded(vibrateLevelIndex, values.length, DEFAULT_VIBRATE_LEVEL)];
        }

        public float shortStrokeMm() {
            return shortStrokeMmTenths / 10f;
        }

        public float longStrokeMm() {
            return longStrokeMmTenths / 10f;
        }

        public Typeface typefaceForMode(int modeOrdinal) {
            int fontIndex;
            if (modeOrdinal == 0) {
                fontIndex = hangulFontIndex;
            } else if (modeOrdinal == 3) {
                fontIndex = numberFontIndex;
            } else {
                fontIndex = englishFontIndex;
            }
            if (fontIndex >= 1 && fontIndex <= 3) {
                return Typeface.create(Typeface.SERIF, fontIndex == 1 ? Typeface.BOLD : Typeface.NORMAL);
            }
            if (fontIndex == 4 || fontIndex == 11) {
                return Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            return Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
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

        KeyboardTheme(int background, int keyNormal, int keySpecial, int keyPressed, int text, int hint, int stroke) {
            this.background = background;
            this.keyNormal = keyNormal;
            this.keySpecial = keySpecial;
            this.keyPressed = keyPressed;
            this.text = text;
            this.hint = hint;
            this.stroke = stroke;
        }
    }
}
