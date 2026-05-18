package com.example.eightwayime.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import com.example.eightwayime.SettingsStore;
import com.example.eightwayime.hangul.Consonant;
import com.example.eightwayime.hangul.GestureVowelMapper;
import com.example.eightwayime.hangul.HangulComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KeyboardSurfaceView extends View {
    private static final float HANGUL_LEFT_KEY_WEIGHT = 1.1f;
    private static final float HANGUL_RIGHT_KEY_WEIGHT = 1.45f;
    private static final String[] TOOLBAR_LABELS = {"‹", "☺", "GIF", "▣", "⚙", "", "•••"};
    private static final float[] TOOLBAR_WEIGHTS = {0.8f, 1.1f, 1.35f, 1.1f, 1.1f, 0.22f, 1.0f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RowLayout> rows = new ArrayList<>();
    private final List<GestureVowelMapper.Point> gesturePoints = new ArrayList<>();
    private final GestureVowelMapper gestureMapper;
    private SettingsStore.Snapshot settings;
    private KeyboardActionListener listener;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private boolean shift;
    private int bottomSystemInset;
    private KeyBounds pressedKey;

    public KeyboardSurfaceView(Context context) {
        super(context);
        settings = SettingsStore.load(context);
        gestureMapper = new GestureVowelMapper(getResources().getDisplayMetrics());
        applyGestureSettings();
        setBackgroundColor(settings.theme.background);
        setFitsSystemWindows(false);
        setOnApplyWindowInsetsListener((view, insets) -> {
            int nextBottomInset = bottomInsetFrom(insets);
            if (bottomSystemInset != nextBottomInset) {
                bottomSystemInset = nextBottomInset;
                requestLayout();
                invalidate();
            }
            return insets;
        });
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int rowCount = Math.max(4, rows.size());
        int desiredHeight = toolbarHeight()
                + dp(rowCount >= 5 ? 310 : (mode == KeyboardMode.SYMBOLS ? 268 : 252))
                + bottomSafeInset();
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
        float toolbarHeight = toolbarHeight();
        drawToolbar(canvas, toolbarHeight);
        float top = toolbarHeight + gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight - toolbarHeight;
        float rowHeight = (rowAreaHeight - gap * (rows.size() + 1)) / rows.size();
        paint.setTextAlign(Paint.Align.CENTER);

        for (RowLayout row : rows) {
            float totalWeight = row.totalWeight();
            float left = gap;
            float availableWidth = getWidth() - gap * (row.keys.size() + 1);
            for (int i = 0; i < row.keys.size(); i++) {
                KeySpec key = row.keys.get(i);
                float keyWidth = availableWidth * (key.weight / totalWeight);
                RectF rect = new RectF(left, top, left + keyWidth, top + rowHeight);
                boolean pressed = pressedKey != null && pressedKey.key == key;
                drawKey(canvas, key, spanRect(row, i, rect, rowHeight, gap, availableWidth, totalWeight), pressed);
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gesturePoints.clear();
                pressedKey = findToolbarKey(event.getX(), event.getY());
                if (pressedKey == null) {
                    pressedKey = findKey(event.getX(), event.getY());
                    gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressedKey == null || !pressedKey.toolbar) {
                    gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                KeyBounds releasedKey = pressedKey;
                pressedKey = null;
                invalidate();
                if (releasedKey == null || listener == null) {
                    gesturePoints.clear();
                    return true;
                }
                if (releasedKey.toolbar) {
                    gesturePoints.clear();
                    listener.onKey(releasedKey.key);
                    return true;
                }
                gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY()));
                String spaceSymbol = spaceSymbolForGesture(releasedKey);
                Integer vowel = spaceSymbol == null ? gestureMapper.map(gesturePoints) : null;
                gesturePoints.clear();
                if (spaceSymbol != null) {
                    listener.onKey(KeySpec.character(spaceSymbol, spaceSymbol));
                } else if (shouldHandleGesture(releasedKey.key, vowel)) {
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

    private boolean shouldHandleGesture(KeySpec key, Integer vowel) {
        return vowel != null
                && mode == KeyboardMode.HANGUL
                && canStartVowelGesture(key);
    }

    private boolean canStartVowelGesture(KeySpec key) {
        return key.type == KeySpec.Type.HANGUL_CONSONANT
                || key.type == KeySpec.Type.HANGUL_VOWEL
                || key.type == KeySpec.Type.HANGUL_VOWEL_PAD;
    }

    private void drawKey(Canvas canvas, KeySpec key, RectF rect, boolean pressed) {
        if (key.type == KeySpec.Type.NO_OP && key.label.isEmpty()) {
            return;
        }
        boolean special = key.type != KeySpec.Type.CHARACTER
                && key.type != KeySpec.Type.HANGUL_CONSONANT
                && key.type != KeySpec.Type.HANGUL_VOWEL
                && key.type != KeySpec.Type.HANGUL_VOWEL_PAD
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
        if (key.type == KeySpec.Type.SPACE) {
            drawSpacePadKey(canvas, rect, theme);
            return;
        }
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

    private void drawToolbar(Canvas canvas, float toolbarHeight) {
        SettingsStore.KeyboardTheme theme = settings.theme;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.background);
        canvas.drawRect(0, 0, getWidth(), toolbarHeight, paint);

        float totalWeight = toolbarTotalWeight();
        float left = 0f;
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < TOOLBAR_LABELS.length; i++) {
            float width = getWidth() * (TOOLBAR_WEIGHTS[i] / totalWeight);
            float centerX = left + width / 2f;
            if (i == 5) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, dp(1)));
                paint.setColor(theme.stroke);
                float dividerX = centerX;
                canvas.drawLine(dividerX, dp(12), dividerX, toolbarHeight - dp(12), paint);
            } else if (i == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.keyNormal);
                canvas.drawCircle(centerX, toolbarHeight / 2f, Math.min(dp(22), toolbarHeight * 0.38f), paint);
                paint.setColor(theme.text);
                paint.setTextSize(dp(34));
                drawCenteredText(canvas, TOOLBAR_LABELS[i], centerX - dp(1), toolbarHeight / 2f);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.hint);
                paint.setTextSize(i == 2 ? dp(18) : dp(25));
                paint.setFakeBoldText(i == 2);
                drawCenteredText(canvas, TOOLBAR_LABELS[i], centerX, toolbarHeight / 2f);
                paint.setFakeBoldText(false);
            }
            left += width;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(0.5f)));
        paint.setColor(theme.stroke);
        canvas.drawLine(0, toolbarHeight - dp(0.5f), getWidth(), toolbarHeight - dp(0.5f), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSpacePadKey(Canvas canvas, RectF rect, SettingsStore.KeyboardTheme theme) {
        float symbolSize = Math.min(dp(24), rect.height() * 0.34f);
        float topY = rect.top + rect.height() * 0.27f;
        float bottomY = rect.top + rect.height() * 0.73f;
        float leftX = rect.left + rect.width() * 0.22f;
        float rightX = rect.right - rect.width() * 0.22f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.text);
        paint.setTextSize(symbolSize);
        drawCenteredText(canvas, "!", leftX, topY);
        drawCenteredText(canvas, "~", rect.centerX(), topY);
        drawCenteredText(canvas, ",", rightX, topY);
        drawCenteredText(canvas, "?", leftX, bottomY);
        drawCenteredText(canvas, ".", rightX, bottomY);

        float iconY = bottomY + rect.height() * 0.02f;
        float halfWidth = Math.min(rect.width() * 0.12f, dp(28));
        float rise = Math.max(dp(5), rect.height() * 0.10f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, dp(2)));
        paint.setColor(theme.text);
        canvas.drawLine(rect.centerX() - halfWidth, iconY, rect.centerX() + halfWidth, iconY, paint);
        canvas.drawLine(rect.centerX() - halfWidth, iconY, rect.centerX() - halfWidth, iconY - rise, paint);
        canvas.drawLine(rect.centerX() + halfWidth, iconY, rect.centerX() + halfWidth, iconY - rise, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCenteredText(Canvas canvas, String text, float x, float centerY) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, x, baseline, paint);
    }

    private KeyBounds findKey(float x, float y) {
        float gap = dp(4);
        float top = toolbarHeight() + gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight - toolbarHeight();
        float rowHeight = (rowAreaHeight - gap * (rows.size() + 1)) / rows.size();
        for (RowLayout row : rows) {
            float totalWeight = row.totalWeight();
            float left = gap;
            float availableWidth = getWidth() - gap * (row.keys.size() + 1);
            for (int i = 0; i < row.keys.size(); i++) {
                KeySpec key = row.keys.get(i);
                float keyWidth = availableWidth * (key.weight / totalWeight);
                RectF rect = new RectF(left, top, left + keyWidth, top + rowHeight);
                RectF touchRect = spanRect(row, i, rect, rowHeight, gap, availableWidth, totalWeight);
                if (touchRect.contains(x, y)) {
                    return new KeyBounds(key, touchRect);
                }
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
        return null;
    }

    private KeyBounds findToolbarKey(float x, float y) {
        float toolbarHeight = toolbarHeight();
        if (y < 0 || y > toolbarHeight) {
            return null;
        }
        float totalWeight = toolbarTotalWeight();
        float left = 0f;
        for (int i = 0; i < TOOLBAR_LABELS.length; i++) {
            float width = getWidth() * (TOOLBAR_WEIGHTS[i] / totalWeight);
            RectF rect = new RectF(left, 0, left + width, toolbarHeight);
            if (rect.contains(x, y)) {
                return new KeyBounds(toolbarKeyForIndex(i), rect, true);
            }
            left += width;
        }
        return null;
    }

    private KeySpec toolbarKeyForIndex(int index) {
        switch (index) {
            case 0:
                return KeySpec.command("", KeySpec.Type.HIDE_KEYBOARD);
            case 1:
                return KeySpec.command("", KeySpec.Type.MODE_SYMBOLS);
            case 3:
                return KeySpec.command("", KeySpec.Type.USEFUL_SENTENCE);
            case 4:
                return KeySpec.command("", KeySpec.Type.SETTINGS);
            default:
                return KeySpec.command("", KeySpec.Type.NO_OP);
        }
    }

    private float toolbarTotalWeight() {
        float total = 0f;
        for (float weight : TOOLBAR_WEIGHTS) {
            total += weight;
        }
        return total;
    }

    private int toolbarHeight() {
        return dp(52);
    }

    private float keyboardHeight() {
        return Math.max(dp(80), getHeight() - bottomSafeInset());
    }

    private float keyboardBottom(float gap) {
        return keyboardHeight() - gap;
    }

    private int bottomSafeInset() {
        return Math.max(bottomSystemInset + dp(8), dp(48));
    }

    private int bottomInsetFrom(WindowInsets insets) {
        if (insets == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets navigationBars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars());
            Insets tappableElement = insets.getInsetsIgnoringVisibility(WindowInsets.Type.tappableElement());
            return Math.max(navigationBars.bottom, tappableElement.bottom);
        }
        return Math.max(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
    }

    private RectF spanRect(RowLayout row, int index, RectF rect, float rowHeight, float gap,
                           float availableWidth, float totalWeight) {
        KeySpec key = row.keys.get(index);
        if (key.rowSpan <= 1 && key.columnSpan <= 1) {
            return rect;
        }
        float right = rect.right;
        int lastColumn = Math.min(row.keys.size() - 1, index + key.columnSpan - 1);
        for (int i = index + 1; i <= lastColumn; i++) {
            KeySpec nextKey = row.keys.get(i);
            right += gap + availableWidth * (nextKey.weight / totalWeight);
        }
        float bottom = rect.bottom + (key.rowSpan - 1) * (rowHeight + gap);
        return new RectF(rect.left, rect.top, right, Math.min(bottom, keyboardBottom(gap)));
    }

    private String spaceSymbolForGesture(KeyBounds keyBounds) {
        if (keyBounds.key.type != KeySpec.Type.SPACE || gesturePoints.size() < 2) {
            return null;
        }
        GestureVowelMapper.Point start = gesturePoints.get(0);
        GestureVowelMapper.Point end = gesturePoints.get(gesturePoints.size() - 1);
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float minDistance = Math.max(dp(10), Math.min(keyBounds.rect.width(), keyBounds.rect.height()) * 0.18f);
        if (Math.hypot(dx, dy) < minDistance) {
            return null;
        }

        float relativeX = clamp((end.x - keyBounds.rect.left) / keyBounds.rect.width(), 0f, 1f);
        float relativeY = clamp((end.y - keyBounds.rect.top) / keyBounds.rect.height(), 0f, 1f);
        if (relativeY < 0.5f) {
            if (relativeX < 0.34f) {
                return "!";
            }
            if (relativeX > 0.66f) {
                return ",";
            }
            return "~";
        }
        if (relativeX < 0.34f) {
            return "?";
        }
        if (relativeX > 0.66f) {
            return ".";
        }
        return null;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
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
                buildHangulQwertyRows();
                break;
            case SettingsStore.HANGUL_TYPE_YUN_VERTICAL:
            default:
                buildYunKeyboardVerticalRows();
                break;
        }
    }

    private void buildYunKeyboardVerticalRows() {
        buildGestureHangulPortraitRows(
                Consonant.KIEUK, Consonant.GIYEOK, Consonant.SIOT, Consonant.JIEUT, Consonant.CHIEUT,
                Consonant.HIEUT, Consonant.NIEUN, Consonant.IEUNG, Consonant.RIEUL, Consonant.MIEUM,
                Consonant.TIEUT, Consonant.DIGEUT, Consonant.BIEUP, Consonant.PIEUP);
    }

    private void buildYunKeyboardHorizontalRows() {
        buildYunKeyboardVerticalRows();
    }

    private void buildTwoBeolsikRows() {
        buildGestureHangulPortraitRows(
                Consonant.BIEUP, Consonant.JIEUT, Consonant.DIGEUT, Consonant.GIYEOK, Consonant.SIOT,
                Consonant.MIEUM, Consonant.NIEUN, Consonant.IEUNG, Consonant.RIEUL, Consonant.HIEUT,
                Consonant.KIEUK, Consonant.TIEUT, Consonant.CHIEUT, Consonant.PIEUP);
    }

    private void buildGestureHangulPortraitRows(
            Consonant row1First, Consonant row1Second, Consonant row1Third, Consonant row1Fourth, Consonant row1Fifth,
            Consonant row2First, Consonant row2Second, Consonant row2Third, Consonant row2Fourth, Consonant row2Fifth,
            Consonant row3First, Consonant row3Second, Consonant row3Third, Consonant row3Fourth) {
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.consonant(row1First).withHints("1", null),
                KeySpec.consonant(row1Second).withHints("2", null),
                KeySpec.consonant(row1Third).withHints("3", null),
                KeySpec.consonant(row1Fourth).withHints("4", null),
                KeySpec.consonant(row1Fifth).withHints("5", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, HANGUL_RIGHT_KEY_WEIGHT).withRowSpan(3)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.consonant(row2First).withHints("6", null),
                KeySpec.consonant(row2Second).withHints("7", null),
                KeySpec.consonant(row2Third).withHints("8", null),
                KeySpec.consonant(row2Fourth).withHints("9", null),
                KeySpec.consonant(row2Fifth).withHints("0", null),
                KeySpec.spacer(HANGUL_RIGHT_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.consonant(row3First).withHints("내번호/메일", null),
                KeySpec.consonant(row3Second).withHints(".?!", null),
                KeySpec.consonant(row3Third).withHints("♥", null),
                KeySpec.consonant(row3Fourth).withHints("^^", null),
                KeySpec.vowelPad("모음", 1.0f).withHints("상용구", null),
                KeySpec.spacer(HANGUL_RIGHT_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE).withHints("~   ,", null).withColumnSpan(2),
                KeySpec.spacer(1.0f),
                KeySpec.command("Go", KeySpec.Type.ENTER).withColumnSpan(2),
                KeySpec.spacer(HANGUL_RIGHT_KEY_WEIGHT)));
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
                KeySpec.consonant(Consonant.BIEUP),
                KeySpec.consonant(Consonant.JIEUT),
                KeySpec.consonant(Consonant.DIGEUT),
                KeySpec.consonant(Consonant.GIYEOK),
                KeySpec.consonant(Consonant.SIOT),
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
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.consonant(Consonant.KIEUK),
                KeySpec.consonant(Consonant.TIEUT),
                KeySpec.consonant(Consonant.CHIEUT),
                KeySpec.consonant(Consonant.PIEUP),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU),
                KeySpec.character(".,", "."),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.05f),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.05f),
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.05f),
                KeySpec.command("space", KeySpec.Type.SPACE, 4.0f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private void buildEnglishRows() {
        buildEnglishQwertyRows();
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
                KeySpec.spacer(0.5f),
                qwertyKey("a", "`", "ㅁ"),
                qwertyKey("s", "#", "ㄴ"),
                qwertyKey("d", "$", "ㅇ"),
                qwertyKey("f", "%", "ㄹ"),
                qwertyKey("g", "(", "ㅎ"),
                qwertyKey("h", ")", "ㅗ"),
                qwertyKey("j", "'", "ㅓ"),
                qwertyKey("k", "\"", "ㅏ"),
                qwertyKey("l", "|", "ㅣ"),
                KeySpec.spacer(0.5f)));
        rows.add(row(
                KeySpec.command("⇧\nA", KeySpec.Type.SHIFT, 1.5f),
                qwertyKey("z", "<", "ㅋ"),
                qwertyKey("x", ">", "ㅌ"),
                qwertyKey("c", "[", "ㅊ"),
                qwertyKey("v", "]", "ㅍ"),
                qwertyKey("b", "{", "ㅠ"),
                qwertyKey("n", "}", "ㅜ"),
                qwertyKey("m", "\\", "ㅡ"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.5f)));
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, 1.15f),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.15f),
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, 1.15f),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 3.55f).withHints("~   ,", null),
                KeySpec.command("↵", KeySpec.Type.ENTER, 3.0f)));
    }

    private KeySpec qwertyKey(String value, String topHint, String bottomHint) {
        String label = value.toUpperCase();
        String outputText = shift ? value.toUpperCase() : value;
        return KeySpec.character(label, outputText).withHints(topHint, bottomHint);
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
        final boolean toolbar;

        KeyBounds(KeySpec key, RectF rect) {
            this(key, rect, false);
        }

        KeyBounds(KeySpec key, RectF rect, boolean toolbar) {
            this.key = key;
            this.rect = rect;
            this.toolbar = toolbar;
        }
    }
}
