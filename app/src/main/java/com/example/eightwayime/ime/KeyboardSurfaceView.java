package com.example.eightwayime.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.example.eightwayime.SettingsStore;
import com.example.eightwayime.hangul.Consonant;
import com.example.eightwayime.hangul.GestureVowelMapper;
import com.example.eightwayime.hangul.HangulComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KeyboardSurfaceView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RowLayout> rows = new ArrayList<>();
    private final List<GestureVowelMapper.Point> gesturePoints = new ArrayList<>();
    private final GestureVowelMapper gestureMapper;
    private SettingsStore.Snapshot settings;
    private KeyboardActionListener listener;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private boolean shift;
    private boolean hangulVowelPanel;
    private KeyBounds pressedKey;

    public KeyboardSurfaceView(Context context) {
        super(context);
        settings = SettingsStore.load(context);
        gestureMapper = new GestureVowelMapper(getResources().getDisplayMetrics());
        applyGestureSettings();
        setBackgroundColor(settings.theme.background);
        buildRows();
    }

    public void setListener(KeyboardActionListener listener) {
        this.listener = listener;
    }

    public void reloadSettings() {
        setSettings(SettingsStore.load(getContext()));
    }

    public void setSettings(SettingsStore.Snapshot settings) {
        this.settings = settings;
        if (!isTwoBeolsikHangul()) {
            hangulVowelPanel = false;
        }
        applyGestureSettings();
        setBackgroundColor(settings.theme.background);
        buildRows();
        requestLayout();
        invalidate();
    }

    private void applyGestureSettings() {
        gestureMapper.setDisplayMetrics(getResources().getDisplayMetrics());
        gestureMapper.setStrokeLengths(settings.shortStrokeMm(), settings.longStrokeMm());
    }

    public void setMode(KeyboardMode mode) {
        this.mode = mode;
        this.shift = false;
        if (mode != KeyboardMode.HANGUL) {
            hangulVowelPanel = false;
        }
        buildRows();
        requestLayout();
        invalidate();
    }

    public void setHangulVowelPanel(boolean enabled) {
        if (mode != KeyboardMode.HANGUL || !isTwoBeolsikHangul()) {
            return;
        }
        hangulVowelPanel = enabled;
        buildRows();
        requestLayout();
        invalidate();
    }

    public void setShift(boolean shift) {
        this.shift = shift;
        buildRows();
        invalidate();
    }

    public KeyboardMode getMode() {
        return mode;
    }

    public boolean isShift() {
        return shift;
    }

    private boolean isTwoBeolsikHangul() {
        return settings.hangulTypeIndex == SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_VERTICAL
                || settings.hangulTypeIndex == SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL;
    }

    private boolean usesGestureVowels() {
        return settings.hangulTypeIndex == SettingsStore.HANGUL_TYPE_YUN_VERTICAL
                || settings.hangulTypeIndex == SettingsStore.HANGUL_TYPE_YUN_HORIZONTAL;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int rowCount = Math.max(4, rows.size());
        int desiredHeight = dp(rowCount >= 5 ? 310 : (mode == KeyboardMode.SYMBOLS ? 268 : 252));
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSettings(SettingsStore.load(getContext()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float gap = dp(4);
        float top = gap;
        float rowHeight = (getHeight() - gap * (rows.size() + 1)) / rows.size();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(settings.typefaceForMode(mode.ordinal()));

        for (RowLayout row : rows) {
            float totalWeight = row.totalWeight();
            float left = gap;
            float availableWidth = getWidth() - gap * (row.keys.size() + 1);
            for (KeySpec key : row.keys) {
                float keyWidth = availableWidth * (key.weight / totalWeight);
                RectF rect = new RectF(left, top, left + keyWidth, top + rowHeight);
                boolean pressed = pressedKey != null && pressedKey.key == key;
                drawKey(canvas, key, rect, pressed);
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedKey = findKey(event.getX(), event.getY());
                gesturePoints.clear();
                gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                return true;
            case MotionEvent.ACTION_UP:
                gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                KeyBounds releasedKey = pressedKey;
                pressedKey = null;
                invalidate();
                if (releasedKey == null || listener == null) {
                    return true;
                }
                Integer vowel = gestureMapper.map(gesturePoints);
                if (vowel != null && mode == KeyboardMode.HANGUL && usesGestureVowels()
                        && releasedKey.key.type == KeySpec.Type.HANGUL_CONSONANT) {
                    listener.onGesture(releasedKey.key, vowel);
                } else {
                    listener.onKey(releasedKey.key);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                gesturePoints.clear();
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void drawKey(Canvas canvas, KeySpec key, RectF rect, boolean pressed) {
        if (key.type == KeySpec.Type.NO_OP && key.label.isEmpty()) {
            return;
        }
        boolean special = key.type != KeySpec.Type.CHARACTER
                && key.type != KeySpec.Type.HANGUL_CONSONANT
                && key.type != KeySpec.Type.HANGUL_VOWEL
                && key.type != KeySpec.Type.NO_OP;
        SettingsStore.KeyboardTheme theme = settings.theme;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? theme.keyPressed : (special ? theme.keySpecial : theme.keyNormal));
        canvas.drawRoundRect(rect, dp(6), dp(6), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, dp(0.5f)));
        paint.setColor(theme.stroke);
        canvas.drawRoundRect(rect, dp(6), dp(6), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(special ? theme.hint : theme.text);
        if (key.hintTop != null && !key.hintTop.isEmpty()) {
            paint.setTextSize(dp(9));
            Paint.FontMetrics hintMetrics = paint.getFontMetrics();
            float hintBaseline = rect.top + dp(10) - (hintMetrics.ascent + hintMetrics.descent) / 2f;
            canvas.drawText(key.hintTop, rect.centerX(), hintBaseline, paint);
        }
        if (key.hintBottom != null && !key.hintBottom.isEmpty()) {
            paint.setTextSize(dp(9));
            Paint.FontMetrics hintMetrics = paint.getFontMetrics();
            float hintBaseline = rect.bottom - dp(9) - (hintMetrics.ascent + hintMetrics.descent) / 2f;
            canvas.drawText(key.hintBottom, rect.centerX(), hintBaseline, paint);
        }

        String[] labelLines = key.label.split("\\n", -1);
        paint.setTextSize(dp(isCompactLabel(key.label) ? 12 : 24));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float centerY = rect.centerY();
        if (key.hintTop != null && !key.hintTop.isEmpty()) {
            centerY += dp(5);
        }
        float firstBaseline = centerY - lineHeight * (labelLines.length - 1) / 2f
                - (metrics.ascent + metrics.descent) / 2f;
        for (int i = 0; i < labelLines.length; i++) {
            canvas.drawText(labelLines[i], rect.centerX(), firstBaseline + lineHeight * i, paint);
        }
    }

    private boolean isCompactLabel(String label) {
        String[] lines = label.split("\\n", -1);
        if (lines.length > 1) {
            return true;
        }
        return label.length() > 2;
    }

    private KeyBounds findKey(float x, float y) {
        float gap = dp(4);
        float top = gap;
        float rowHeight = (getHeight() - gap * (rows.size() + 1)) / rows.size();
        for (RowLayout row : rows) {
            float totalWeight = row.totalWeight();
            float left = gap;
            float availableWidth = getWidth() - gap * (row.keys.size() + 1);
            for (KeySpec key : row.keys) {
                float keyWidth = availableWidth * (key.weight / totalWeight);
                RectF rect = new RectF(left, top, left + keyWidth, top + rowHeight);
                if (rect.contains(x, y)) {
                    return new KeyBounds(key, rect);
                }
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
        return null;
    }

    private void buildRows() {
        rows.clear();
        switch (mode) {
            case HANGUL:
                buildHangulRows();
                break;
            case ENGLISH:
                buildEnglishRows();
                break;
            case SYMBOLS:
                buildSymbolRows();
                break;
            case NUMBERS:
                buildNumberRows();
                break;
        }
    }

    private void buildHangulRows() {
        switch (settings.hangulTypeIndex) {
            case SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_VERTICAL:
                buildTwoBeolsikRows();
                break;
            case SettingsStore.HANGUL_TYPE_YUN_HORIZONTAL:
                buildYunKeyboardHorizontalRows();
                break;
            case SettingsStore.HANGUL_TYPE_TWO_BEOLSIK_HORIZONTAL:
                buildTwoBeolsikRows();
                break;
            case SettingsStore.HANGUL_TYPE_YUN_VERTICAL:
            default:
                buildYunKeyboardVerticalRows();
                break;
        }
    }

    private void buildYunKeyboardVerticalRows() {
        buildYunKeyboardImageRows();
    }

    private void buildYunKeyboardHorizontalRows() {
        buildYunKeyboardImageRows();
    }

    private void buildYunKeyboardImageRows() {
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.1f),
                KeySpec.consonant(Consonant.KIEUK).withHints("1", null),
                KeySpec.consonant(Consonant.GIYEOK).withHints("2", null),
                KeySpec.consonant(Consonant.SIOT).withHints("3", null),
                KeySpec.consonant(Consonant.JIEUT).withHints("4", null),
                KeySpec.consonant(Consonant.CHIEUT).withHints("5", null),
                KeySpec.spacer(1.1f)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.1f),
                KeySpec.consonant(Consonant.HIEUT).withHints("6", null),
                KeySpec.consonant(Consonant.NIEUN).withHints("7", null),
                KeySpec.consonant(Consonant.IEUNG).withHints("8", null),
                KeySpec.consonant(Consonant.RIEUL).withHints("9", null),
                KeySpec.command("모음", KeySpec.Type.NO_OP, 1.0f).withHints("0", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.1f),
                KeySpec.consonant(Consonant.TIEUT).withHints("내번호/메일", null),
                KeySpec.consonant(Consonant.DIGEUT).withHints(".?!", null),
                KeySpec.consonant(Consonant.MIEUM).withHints("♥", null),
                KeySpec.consonant(Consonant.BIEUP).withHints("^^", null),
                KeySpec.consonant(Consonant.PIEUP).withHints("상용구", null)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 3.0f).withHints("~   ,", null),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.1f)));
    }

    private void buildTwoBeolsikRows() {
        if (hangulVowelPanel) {
            buildTwoBeolsikVowelRows();
        } else {
            buildTwoBeolsikConsonantRows();
        }
    }

    private void buildTwoBeolsikConsonantRows() {
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, 1.1f),
                KeySpec.consonant(Consonant.BIEUP).withHints("1", null),
                KeySpec.consonant(Consonant.JIEUT).withHints("2", null),
                KeySpec.consonant(Consonant.DIGEUT).withHints("3", null),
                KeySpec.consonant(Consonant.GIYEOK).withHints("4", null),
                KeySpec.consonant(Consonant.SIOT).withHints("5", null),
                KeySpec.spacer(1.1f)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.1f),
                KeySpec.consonant(Consonant.MIEUM).withHints("6", null),
                KeySpec.consonant(Consonant.NIEUN).withHints("7", null),
                KeySpec.consonant(Consonant.IEUNG).withHints("8", null),
                KeySpec.consonant(Consonant.RIEUL).withHints("9", null),
                KeySpec.consonant(Consonant.HIEUT).withHints("0", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.1f),
                KeySpec.consonant(Consonant.KIEUK).withHints("내번호/메일", null),
                KeySpec.consonant(Consonant.TIEUT).withHints(".?!", null),
                KeySpec.consonant(Consonant.CHIEUT).withHints("♥", null),
                KeySpec.consonant(Consonant.PIEUP).withHints("^.^", null),
                KeySpec.command("모음", KeySpec.Type.HANGUL_VOWELS, 1.1f).withHints("상용구", null)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 3.0f).withHints("~   ,", null),
                KeySpec.command("Go", KeySpec.Type.ENTER, 1.1f)));
    }

    private void buildTwoBeolsikVowelRows() {
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, 1.1f),
                KeySpec.vowel("ㅛ", HangulComposer.V_YO).withHints("1", null),
                KeySpec.vowel("ㅕ", HangulComposer.V_YEO).withHints("2", null),
                KeySpec.vowel("ㅑ", HangulComposer.V_YA).withHints("3", null),
                KeySpec.vowel("ㅐ", HangulComposer.V_AE).withHints("4", null),
                KeySpec.vowel("ㅔ", HangulComposer.V_E).withHints("5", null),
                KeySpec.spacer(1.1f)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.1f),
                KeySpec.vowel("ㅗ", HangulComposer.V_O).withHints("6", null),
                KeySpec.vowel("ㅓ", HangulComposer.V_EO).withHints("7", null),
                KeySpec.vowel("ㅏ", HangulComposer.V_A).withHints("8", null),
                KeySpec.vowel("ㅣ", HangulComposer.V_I).withHints("9", null),
                KeySpec.vowel("ㅖ", HangulComposer.V_YE).withHints("0", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.1f),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU),
                KeySpec.vowel("ㅒ", HangulComposer.V_YAE),
                KeySpec.command("자음", KeySpec.Type.HANGUL_CONSONANTS, 1.1f).withHints("상용구", null)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 3.0f).withHints("~   ,", null),
                KeySpec.command("Go", KeySpec.Type.ENTER, 1.1f)));
    }

    private void buildHangulQwertyVerticalRows() {
        rows.add(row(
                KeySpec.consonant(shift ? Consonant.SSANG_BIEUP : Consonant.BIEUP),
                KeySpec.consonant(shift ? Consonant.SSANG_JIEUT : Consonant.JIEUT),
                KeySpec.consonant(shift ? Consonant.SSANG_DIGEUT : Consonant.DIGEUT),
                KeySpec.consonant(shift ? Consonant.SSANG_GIYEOK : Consonant.GIYEOK),
                KeySpec.consonant(shift ? Consonant.SSANG_SIOT : Consonant.SIOT)));
        rows.add(row(
                KeySpec.vowel("ㅛ", HangulComposer.V_YO),
                KeySpec.vowel("ㅕ", HangulComposer.V_YEO),
                KeySpec.vowel("ㅑ", HangulComposer.V_YA),
                KeySpec.vowel("ㅐ", HangulComposer.V_AE),
                KeySpec.vowel("ㅔ", HangulComposer.V_E)));
        rows.add(row(
                KeySpec.consonant(Consonant.MIEUM),
                KeySpec.consonant(Consonant.NIEUN),
                KeySpec.consonant(Consonant.IEUNG),
                KeySpec.consonant(Consonant.RIEUL),
                KeySpec.consonant(Consonant.HIEUT)));
        rows.add(row(
                KeySpec.vowel("ㅗ", HangulComposer.V_O),
                KeySpec.vowel("ㅓ", HangulComposer.V_EO),
                KeySpec.vowel("ㅏ", HangulComposer.V_A),
                KeySpec.vowel("ㅣ", HangulComposer.V_I),
                KeySpec.command("⌫", KeySpec.Type.DELETE)));
        rows.add(row(
                KeySpec.command(shift ? "SHIFT" : "shift", KeySpec.Type.SHIFT, 1.4f),
                KeySpec.consonant(Consonant.KIEUK),
                KeySpec.consonant(Consonant.TIEUT),
                KeySpec.consonant(Consonant.CHIEUT),
                KeySpec.consonant(Consonant.PIEUP),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU)));
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.1f),
                KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS, 1.1f),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.1f),
                KeySpec.command("메뉴", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.command("문구", KeySpec.Type.USEFUL_SENTENCE, 1.1f),
                KeySpec.command("space", KeySpec.Type.SPACE, 3.6f),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.1f)));
    }

    private void buildHangulQwertyRows() {
        rows.add(row(
                KeySpec.consonant(shift ? Consonant.SSANG_BIEUP : Consonant.BIEUP),
                KeySpec.consonant(shift ? Consonant.SSANG_JIEUT : Consonant.JIEUT),
                KeySpec.consonant(shift ? Consonant.SSANG_DIGEUT : Consonant.DIGEUT),
                KeySpec.consonant(shift ? Consonant.SSANG_GIYEOK : Consonant.GIYEOK),
                KeySpec.consonant(shift ? Consonant.SSANG_SIOT : Consonant.SIOT),
                KeySpec.vowel("ㅛ", HangulComposer.V_YO),
                KeySpec.vowel("ㅕ", HangulComposer.V_YEO),
                KeySpec.vowel("ㅑ", HangulComposer.V_YA),
                KeySpec.vowel("ㅐ", HangulComposer.V_AE),
                KeySpec.vowel("ㅔ", HangulComposer.V_E)));
        rows.add(row(
                KeySpec.consonant(Consonant.MIEUM),
                KeySpec.consonant(Consonant.NIEUN),
                KeySpec.consonant(Consonant.IEUNG),
                KeySpec.consonant(Consonant.RIEUL),
                KeySpec.consonant(Consonant.HIEUT),
                KeySpec.vowel("ㅗ", HangulComposer.V_O),
                KeySpec.vowel("ㅓ", HangulComposer.V_EO),
                KeySpec.vowel("ㅏ", HangulComposer.V_A),
                KeySpec.vowel("ㅣ", HangulComposer.V_I)));
        rows.add(row(
                KeySpec.command(shift ? "SHIFT" : "shift", KeySpec.Type.SHIFT, 1.4f),
                KeySpec.consonant(Consonant.KIEUK),
                KeySpec.consonant(Consonant.TIEUT),
                KeySpec.consonant(Consonant.CHIEUT),
                KeySpec.consonant(Consonant.PIEUP),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU),
                KeySpec.command("⌫", KeySpec.Type.DELETE, 1.4f)));
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.2f),
                KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS, 1.2f),
                KeySpec.command("문구", KeySpec.Type.USEFUL_SENTENCE, 1.2f),
                KeySpec.command("space", KeySpec.Type.SPACE, 4.0f),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private void buildEnglishRows() {
        boolean hybridLandscape = settings.englishTypeIndex == 2 && isLandscape();
        if (settings.englishTypeIndex == 1 || hybridLandscape) {
            buildEnglishQwertyRows();
        } else {
            buildEnglishPalgeulRows();
        }
    }

    private void buildEnglishQwertyRows() {
        rows.add(row(
                qwertyKey("q", "1", "ㅂ"),
                qwertyKey("w", "2", "ㅈ"),
                qwertyKey("e", "3", "ㄷ"),
                qwertyKey("r", "4", "ㄱ"),
                qwertyKey("t", "5", "ㅅ"),
                qwertyKey("y", "6", "ㅛ"),
                qwertyKey("u", "7", "ㅕ"),
                qwertyKey("i", "8", "ㅑ"),
                qwertyKey("o", "9", "ㅐ"),
                qwertyKey("p", "0", "ㅔ")));
        rows.add(row(
                qwertyKey("a", "+", "ㅁ"),
                qwertyKey("s", "-", "ㄴ"),
                qwertyKey("d", "*", "ㅇ"),
                qwertyKey("f", "/", "ㄹ"),
                qwertyKey("g", "=", "ㅎ"),
                qwertyKey("h", "&", "ㅗ"),
                qwertyKey("j", "^", "ㅓ"),
                qwertyKey("k", "@", "ㅏ"),
                qwertyKey("l", "~", "ㅣ")));
        rows.add(row(
                KeySpec.command(shift ? "SHIFT" : "shift", KeySpec.Type.SHIFT, 1.4f),
                qwertyKey("z", ":", "ㅋ"),
                qwertyKey("x", ";", "ㅌ"),
                qwertyKey("c", "-", "ㅊ"),
                qwertyKey("v", "'", "ㅍ"),
                qwertyKey("b", ".", "ㅠ"),
                qwertyKey("n", "?", "ㅜ"),
                qwertyKey("m", "!", "ㅡ"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.4f)));
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, 1.2f),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.2f),
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.2f),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 4.6f).withHints("~   ,", null),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private KeySpec qwertyKey(String value, String topHint, String bottomHint) {
        String text = shift ? value.toUpperCase() : value;
        return KeySpec.character(text, text).withHints(topHint, bottomHint);
    }

    private void buildEnglishPalgeulRows() {
        rows.add(row(
                KeySpec.command("한글", KeySpec.Type.MODE_HANGUL, 1.1f),
                letter("a"), letter("f"), letter("k"), letter("p"), letter("u"),
                KeySpec.command("⌫", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(letter("b"), letter("g"), letter("l"), letter("q"), letter("v"), KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS)));
        rows.add(row(letter("c"), letter("h"), letter("m"), letter("r"), letter("w"), KeySpec.command(shift ? "SHIFT" : "shift", KeySpec.Type.SHIFT)));
        rows.add(row(letter("d"), letter("i"), letter("n"), letter("s"), letter("x"), KeySpec.command("123", KeySpec.Type.MODE_NUMBERS)));
        rows.add(row(letter("e"), letter("j"), letter("o"), letter("t"), letter("y"), letter("z"),
                KeySpec.command("space", KeySpec.Type.SPACE, 2.2f),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private KeySpec letter(String value) {
        String text = shift ? value.toUpperCase() : value;
        return KeySpec.character(text, text);
    }

    private void buildSymbolRows() {
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, 1.05f),
                KeySpec.character("~", "~"),
                KeySpec.character("!", "!"),
                KeySpec.character("@", "@"),
                KeySpec.character("#", "#"),
                KeySpec.character("$", "$"),
                KeySpec.character("%", "%"),
                KeySpec.character("^", "^"),
                KeySpec.command("1\n/\n30", KeySpec.Type.NO_OP, 1.05f)));
        rows.add(row(
                KeySpec.spacer(1.05f),
                KeySpec.character("&", "&"),
                KeySpec.character("*", "*"),
                KeySpec.character("+", "+"),
                KeySpec.character("-", "-"),
                KeySpec.character("_", "_"),
                KeySpec.character("=", "="),
                KeySpec.character("?", "?"),
                KeySpec.spacer(1.05f)));
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.05f),
                KeySpec.character("/", "/"),
                KeySpec.character("|", "|"),
                KeySpec.character("\\", "\\"),
                KeySpec.character("'", "'"),
                KeySpec.character("`", "`"),
                KeySpec.character("´", "´"),
                KeySpec.character("\"", "\""),
                KeySpec.spacer(1.05f)));
        rows.add(row(
                KeySpec.spacer(1.05f),
                KeySpec.character("(", "("),
                KeySpec.character(")", ")"),
                KeySpec.character("“", "“"),
                KeySpec.character("”", "”"),
                KeySpec.character(";", ";"),
                KeySpec.character("<", "<"),
                KeySpec.character(">", ">"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.05f)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.05f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 4.0f).withHints("~   ,", null),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private void buildNumberRows() {
        if (settings.numberTypeIndex == 1) {
            buildComputerNumberRows();
        } else {
            buildPhoneNumberRows();
        }
    }

    private void buildPhoneNumberRows() {
        buildImageNumberRows();
    }

    private void buildComputerNumberRows() {
        buildImageNumberRows();
    }

    private void buildImageNumberRows() {
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, 1.05f),
                KeySpec.character("1", "1").withHints("*", null),
                KeySpec.character("2", "2").withHints("/", null),
                KeySpec.character("3", "3").withHints("=", null),
                KeySpec.character("-", "-")));
        rows.add(row(
                KeySpec.spacer(1.05f),
                KeySpec.character("4", "4").withHints("%", null),
                KeySpec.character("5", "5").withHints("'", null),
                KeySpec.character("6", "6").withHints("#", null),
                KeySpec.character("+", "+")));
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.05f),
                KeySpec.character("7", "7").withHints("(", null),
                KeySpec.character("8", "8").withHints(")", null),
                KeySpec.character("9", "9").withHints("?", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.05f),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 1.8f).withHints("~   ,", null),
                KeySpec.character("0", "0"),
                KeySpec.character(".", "."),
                KeySpec.command("↵", KeySpec.Type.ENTER)));
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private RowLayout row(KeySpec... keys) {
        RowLayout row = new RowLayout();
        row.keys.addAll(Arrays.asList(keys));
        return row;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class RowLayout {
        final List<KeySpec> keys = new ArrayList<>();

        float totalWeight() {
            float total = 0f;
            for (KeySpec key : keys) {
                total += key.weight;
            }
            return total;
        }
    }

    private static class KeyBounds {
        final KeySpec key;
        final RectF rect;

        KeyBounds(KeySpec key, RectF rect) {
            this.key = key;
            this.rect = rect;
        }
    }
}
