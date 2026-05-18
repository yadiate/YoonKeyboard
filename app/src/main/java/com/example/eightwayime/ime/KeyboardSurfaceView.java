package com.example.eightwayime.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
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
    private static final float SYMBOL_SIDE_KEY_WEIGHT = 1.05f;
    private static final float NUMBER_SIDE_KEY_WEIGHT = 1.05f;
    private static final String[] TOOLBAR_LABELS = {"☺", "GIF", "", "⚙", "", "⋮"};
    private static final float[] TOOLBAR_WEIGHTS = {1.1f, 1.35f, 1.1f, 1.1f, 0.22f, 1.0f};
    private static final String[][] SYMBOL_PAGES = {
            {"~", "!", "@", "#", "$", "%", "^", "&", "*", "+", "-", "_", "=", "?", "/", "|", "\\", "'", "`", "´", "\"", "‘", "’", "“", "”", ";"},
            {"<", ">", "(", ")", "[", "]", "{", "}", ":", ",", ".", "…", "·", "•", "°", "¿", "¡", "§", "¶", "※", "№", "©", "®", "™", "℠", "℗"},
            {"±", "×", "÷", "≠", "≈", "≡", "≤", "≥", "∞", "√", "∑", "∏", "∫", "∂", "∆", "∇", "∴", "∵", "∈", "∉", "⊂", "⊃", "∪", "∩", "∧", "∨"},
            {"¢", "£", "€", "¥", "₩", "₹", "₽", "₫", "₴", "₿", "¤", "‰", "℃", "℉", "㎜", "㎝", "㎞", "㎡", "㎥", "㎎", "㎏", "㎖", "ℓ", "㏄", "㎐", "㏈"},
            {"←", "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙", "⇐", "⇒", "⇑", "⇓", "⇔", "↩", "↪", "↵", "⇧", "⌫", "⌦", "⌘", "⌥", "⌃", "⌂", "⌧"},
            {"★", "☆", "♥", "♡", "♣", "♧", "♦", "♢", "♠", "♤", "●", "○", "■", "□", "▲", "△", "▼", "▽", "◆", "◇", "◎", "◉", "◌", "◍", "◐", "◑"},
            {"♪", "♫", "♬", "♭", "♮", "♯", "☀", "☁", "☂", "☃", "☄", "☾", "☽", "☎", "☏", "☑", "☒", "✓", "✔", "✕", "✖", "✚", "✜", "✦", "✧", "✪"},
            {"α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ", "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω", "Σ", "Ω"}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RowLayout> rows = new ArrayList<>();
    private final List<GestureVowelMapper.Point> gesturePoints = new ArrayList<>();
    private final List<ClipboardClip> clipboardClips = new ArrayList<>();
    private final List<ClipboardCard> clipboardCards = new ArrayList<>();
    private final GestureVowelMapper gestureMapper;
    private SettingsStore.Snapshot settings;
    private KeyboardActionListener listener;
    private GestureTraceListener gestureTraceListener;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private boolean shift;
    private int symbolPage;
    private int bottomSystemInset;
    private boolean bottomSafeInsetEnabled = true;
    private boolean clipboardContextVisible;
    private String clipboardContextPreview = "";
    private final RectF clipboardCloseRect = new RectF();
    private KeyBounds pressedKey;
    private Runnable longPressRunnable;
    private Runnable keyPreviewRunnable;
    private boolean longPressFired;
    private boolean keyPreviewVisible;
    private float touchDownX;
    private float touchDownY;

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

    public void setGestureTraceListener(GestureTraceListener gestureTraceListener) {
        this.gestureTraceListener = gestureTraceListener;
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

    public void setBottomSafeInsetEnabled(boolean enabled) {
        if (bottomSafeInsetEnabled == enabled) {
            return;
        }
        bottomSafeInsetEnabled = enabled;
        requestLayout();
        invalidate();
    }

    public void showClipboardContext(String previewText) {
        List<ClipboardClip> clips = new ArrayList<>();
        if (previewText != null && !previewText.trim().isEmpty()) {
            clips.add(new ClipboardClip(0, previewText, "텍스트", null, false));
        }
        showClipboardContext(clips, previewText);
    }

    public void showClipboardContext(List<ClipboardClip> clips, String emptyMessage) {
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        keyPreviewVisible = false;
        clipboardContextVisible = true;
        clipboardContextPreview = emptyMessage == null ? "" : emptyMessage;
        clipboardClips.clear();
        if (clips != null) {
            clipboardClips.addAll(clips);
        }
        buildRows();
        requestLayout();
        invalidate();
    }

    public void hideClipboardContext() {
        if (!clipboardContextVisible) {
            return;
        }
        clipboardContextVisible = false;
        clipboardContextPreview = "";
        clipboardClips.clear();
        clipboardCards.clear();
        clipboardCloseRect.setEmpty();
        buildRows();
        requestLayout();
        invalidate();
    }

    private void applyGestureSettings() {
        gestureMapper.setDisplayMetrics(getResources().getDisplayMetrics());
        gestureMapper.setStrokeLengths(settings.shortStrokeMm(), settings.longStrokeMm());
        gestureMapper.setCalibrationProfile(settings.gestureCalibrationEnabled
                ? settings.gestureCalibrationProfile
                : null);
    }

    public void setMode(KeyboardMode mode) {
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        keyPreviewVisible = false;
        if (this.mode != mode && mode == KeyboardMode.SYMBOLS) {
            symbolPage = 0;
        }
        clipboardContextVisible = false;
        clipboardContextPreview = "";
        clipboardClips.clear();
        clipboardCards.clear();
        clipboardCloseRect.setEmpty();
        this.mode = mode;
        this.shift = false;
        buildRows();
        requestLayout();
        invalidate();
    }

    public void showPreviousSymbolPage() {
        changeSymbolPage(-1);
    }

    public void showNextSymbolPage() {
        changeSymbolPage(1);
    }

    private void changeSymbolPage(int direction) {
        if (mode != KeyboardMode.SYMBOLS) {
            return;
        }
        int pageCount = SYMBOL_PAGES.length;
        symbolPage = (symbolPage + direction + pageCount) % pageCount;
        buildRows();
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
        float heightScale = settings == null ? 1f : settings.keyboardHeightScale();
        int desiredHeight = toolbarHeight()
                + Math.round(dp(rowCount >= 5 ? 310 : (mode == KeyboardMode.SYMBOLS ? 268 : 252)) * heightScale)
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
        if (clipboardContextVisible) {
            drawClipboardPanel(canvas, toolbarHeight);
            return;
        }
        float top = toolbarHeight + gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight - toolbarHeight;
        float rowHeight = (rowAreaHeight - gap * (rows.size() + 1)) / rows.size();
        paint.setTextAlign(Paint.Align.CENTER);

        for (RowLayout row : rows) {
            float totalWeight = row.totalWeight();
            float contentLeft = keyboardLeftInset();
            float left = contentLeft + gap;
            float availableWidth = keyboardContentWidth() - gap * (row.keys.size() + 1);
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
        drawKeyPreview(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                requestParentTouchIntercept(false);
                cancelScheduledLongPress();
                cancelScheduledKeyPreview();
                longPressFired = false;
                keyPreviewVisible = false;
                touchDownX = event.getX();
                touchDownY = event.getY();
                gesturePoints.clear();
                pressedKey = findToolbarKey(event.getX(), event.getY());
                if (pressedKey == null) {
                    if (clipboardContextVisible) {
                        pressedKey = findClipboardKey(event.getX(), event.getY());
                    } else {
                        pressedKey = findKey(event.getX(), event.getY());
                        gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY(), event.getEventTime()));
                        scheduleKeyPreviewIfNeeded(pressedKey);
                        scheduleLongPressIfNeeded(pressedKey);
                    }
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (movedBeyondLongPressSlop(event.getX(), event.getY())) {
                    cancelScheduledLongPress();
                    cancelScheduledKeyPreview();
                    keyPreviewVisible = false;
                    invalidate();
                }
                if (pressedKey == null || (!pressedKey.toolbar && !pressedKey.clipboard)) {
                    gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY(), event.getEventTime()));
                }
                return true;
            case MotionEvent.ACTION_UP:
                requestParentTouchIntercept(true);
                cancelScheduledLongPress();
                cancelScheduledKeyPreview();
                KeyBounds releasedKey = pressedKey;
                pressedKey = null;
                keyPreviewVisible = false;
                invalidate();
                if (longPressFired) {
                    longPressFired = false;
                    gesturePoints.clear();
                    return true;
                }
                if (releasedKey == null || listener == null) {
                    gesturePoints.clear();
                    return true;
                }
                if (releasedKey.toolbar) {
                    gesturePoints.clear();
                    listener.onKey(releasedKey.key);
                    return true;
                }
                if (releasedKey.clipboard) {
                    gesturePoints.clear();
                    listener.onKey(releasedKey.key);
                    return true;
                }
                gesturePoints.add(new GestureVowelMapper.Point(event.getX(), event.getY(), event.getEventTime()));
                String spaceSymbol = spaceSymbolForGesture(releasedKey);
                Integer vowel = spaceSymbol == null ? gestureMapper.map(gesturePoints, releasedKey.key.consonant) : null;
                List<GestureVowelMapper.Point> tracedPoints = new ArrayList<>(gesturePoints);
                if (spaceSymbol != null) {
                    gesturePoints.clear();
                    listener.onKey(KeySpec.character(spaceSymbol, spaceSymbol));
                } else if (shouldHandleGesture(releasedKey.key, vowel)) {
                    notifyGestureTrace(releasedKey, vowel, tracedPoints);
                    gesturePoints.clear();
                    listener.onGesture(releasedKey.key, vowel);
                } else {
                    gesturePoints.clear();
                    listener.onKey(releasedKey.key);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                requestParentTouchIntercept(true);
                cancelScheduledLongPress();
                cancelScheduledKeyPreview();
                longPressFired = false;
                keyPreviewVisible = false;
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

    private void notifyGestureTrace(KeyBounds keyBounds, Integer vowel, List<GestureVowelMapper.Point> points) {
        if (gestureTraceListener == null || points.size() < 2) {
            return;
        }
        gestureTraceListener.onGestureTrace(new GestureTrace(keyBounds.key, new RectF(keyBounds.rect),
                vowel, gestureMapper.trace(points)));
    }

    private void requestParentTouchIntercept(boolean allowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allowIntercept);
        }
    }

    private void scheduleKeyPreviewIfNeeded(KeyBounds keyBounds) {
        if (keyBounds == null || !canShowKeyPreview(keyBounds.key)) {
            return;
        }
        KeySpec key = keyBounds.key;
        keyPreviewRunnable = () -> {
            if (pressedKey == null || pressedKey.key != key) {
                return;
            }
            keyPreviewVisible = true;
            keyPreviewRunnable = null;
            invalidate();
        };
        postDelayed(keyPreviewRunnable, Math.max(80, settings.longPressTimeoutMs() / 2));
    }

    private void scheduleLongPressIfNeeded(KeyBounds keyBounds) {
        if (keyBounds == null || listener == null || !canLongPress(keyBounds.key)) {
            return;
        }
        KeySpec key = keyBounds.key;
        longPressRunnable = () -> {
            if (pressedKey == null || pressedKey.key != key || listener == null) {
                return;
            }
            longPressFired = true;
            gesturePoints.clear();
            longPressRunnable = null;
            listener.onKey(longPressKeyFor(key));
            invalidate();
        };
        postDelayed(longPressRunnable, settings.longPressTimeoutMs());
    }

    private boolean canLongPress(KeySpec key) {
        return canLongPressTopHint(key) || canLongPressClipboardContext(key);
    }

    private boolean canLongPressTopHint(KeySpec key) {
        if (mode != KeyboardMode.HANGUL && mode != KeyboardMode.ENGLISH) {
            return false;
        }
        if (key.hintTop == null || key.hintTop.isEmpty()) {
            return false;
        }
        return key.type == KeySpec.Type.CHARACTER
                || key.type == KeySpec.Type.HANGUL_CONSONANT
                || key.type == KeySpec.Type.HANGUL_VOWEL;
    }

    private boolean canLongPressClipboardContext(KeySpec key) {
        return !clipboardContextVisible && key.type == KeySpec.Type.SPACE;
    }

    private KeySpec longPressKeyFor(KeySpec key) {
        if (canLongPressClipboardContext(key)) {
            return KeySpec.command("", KeySpec.Type.CLIPBOARD_CONTEXT);
        }
        return KeySpec.character(key.hintTop, key.hintTop);
    }

    private void cancelScheduledLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void cancelScheduledKeyPreview() {
        if (keyPreviewRunnable != null) {
            removeCallbacks(keyPreviewRunnable);
            keyPreviewRunnable = null;
        }
    }

    private boolean movedBeyondLongPressSlop(float x, float y) {
        float dx = x - touchDownX;
        float dy = y - touchDownY;
        return Math.hypot(dx, dy) > dp(10);
    }

    private void drawKeyPreview(Canvas canvas) {
        if (!keyPreviewVisible || pressedKey == null || pressedKey.toolbar || !canShowKeyPreview(pressedKey.key)) {
            return;
        }
        boolean auxiliaryPreview = hasAuxiliaryPreview(pressedKey.key);
        String label = previewLabel(pressedKey.key);
        if (label.isEmpty()) {
            return;
        }

        SettingsStore.KeyboardTheme theme = settings.theme;
        RectF keyRect = pressedKey.rect;
        float previewWidth = Math.max(auxiliaryPreview ? dp(66) : dp(58), keyRect.width() * 1.18f);
        float previewHeight = Math.max(auxiliaryPreview ? dp(68) : dp(58), keyRect.height() * 1.12f);
        float centerX = keyRect.centerX();
        float left = clamp(centerX - previewWidth / 2f, dp(4), getWidth() - previewWidth - dp(4));
        float bottom = keyRect.top - dp(7);
        float top = bottom - previewHeight;
        if (top < dp(3)) {
            top = dp(3);
            bottom = top + previewHeight;
        }
        RectF bubbleRect = new RectF(left, top, left + previewWidth, bottom);

        drawRaisedKeyBackground(canvas, bubbleRect, theme.keyNormal, false, dp(9));

        Path tail = new Path();
        float tailCenter = clamp(keyRect.centerX(), bubbleRect.left + dp(12), bubbleRect.right - dp(12));
        tail.moveTo(tailCenter - dp(10), bubbleRect.bottom - dp(1));
        tail.lineTo(tailCenter + dp(10), bubbleRect.bottom - dp(1));
        tail.lineTo(keyRect.centerX(), Math.min(keyRect.top + dp(8), bubbleRect.bottom + dp(18)));
        tail.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(darkenColor(theme.keyNormal, 0.05f));
        canvas.drawPath(tail, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(0.75f)));
        paint.setColor(theme.stroke);
        canvas.drawRoundRect(bubbleRect, dp(9), dp(9), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(theme.text);
        paint.setTextSize(dp(label.length() > 1 ? 28 : 38));
        drawCenteredText(canvas, label, bubbleRect.centerX(), bubbleRect.centerY());
    }

    private boolean canShowKeyPreview(KeySpec key) {
        switch (key.type) {
            case CHARACTER:
            case HANGUL_CONSONANT:
            case HANGUL_VOWEL:
            case HANGUL_VOWEL_PAD:
                return true;
            default:
                return false;
        }
    }

    private String previewLabel(KeySpec key) {
        if (hasAuxiliaryPreview(key)) {
            return key.hintTop.trim();
        }
        return primaryKeyLabel(key);
    }

    private boolean hasAuxiliaryPreview(KeySpec key) {
        return canLongPressTopHint(key) && key.hintTop != null && !key.hintTop.trim().isEmpty();
    }

    private String primaryKeyLabel(KeySpec key) {
        String[] lines = key.label.split("\\n", -1);
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                return line.trim();
            }
        }
        return "";
    }

    private void drawRaisedKeyBackground(Canvas canvas, RectF rect, int baseColor, boolean pressed, float radius) {
        SettingsStore.KeyboardTheme theme = settings.theme;
        float shadowOffset = pressed ? dp(0.5f) : dp(2.2f);
        float shadowInset = dp(0.5f);
        RectF shadowRect = new RectF(rect.left + shadowInset, rect.top + shadowOffset,
                rect.right - shadowInset, rect.bottom + shadowOffset);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? adjustAlpha(theme.shadow, 80) : adjustAlpha(theme.shadow, 138));
        canvas.drawRoundRect(shadowRect, radius, radius, paint);

        int topColor = lightenColor(baseColor, pressed ? 0.03f : 0.09f);
        int bottomColor = darkenColor(baseColor, pressed ? 0.07f : 0.035f);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                topColor, bottomColor, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(0.7f)));
        paint.setColor(adjustAlpha(lightenColor(baseColor, 0.18f), pressed ? 64 : 112));
        RectF highlightRect = new RectF(rect.left + dp(0.8f), rect.top + dp(0.8f),
                rect.right - dp(0.8f), rect.bottom - dp(0.8f));
        canvas.drawRoundRect(highlightRect, radius - dp(1), radius - dp(1), paint);

        paint.setStrokeWidth(Math.max(1f, dp(0.55f)));
        paint.setColor(theme.stroke);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
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

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
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

        int keyColor = key.type == KeySpec.Type.ENTER
                ? theme.enterKey
                : (special ? theme.keySpecial : theme.keyNormal);
        drawRaisedKeyBackground(canvas, rect, pressed ? theme.keyPressed : keyColor, pressed, dp(8));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(key.type == KeySpec.Type.ENTER
                ? theme.enterText
                : (special ? theme.hint : theme.text));
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
        float contentLeft = keyboardLeftInset();
        float contentWidth = keyboardContentWidth();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.background);
        canvas.drawRect(contentLeft, 0, contentLeft + contentWidth, toolbarHeight, paint);

        float totalWeight = toolbarTotalWeight();
        float left = contentLeft;
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < TOOLBAR_LABELS.length; i++) {
            float width = contentWidth * (TOOLBAR_WEIGHTS[i] / totalWeight);
            float centerX = left + width / 2f;
            if (i == 4) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, dp(1)));
                paint.setColor(theme.stroke);
                float dividerX = centerX;
                canvas.drawLine(dividerX, dp(12), dividerX, toolbarHeight - dp(12), paint);
            } else if (i == 2) {
                drawClipboardIcon(canvas, centerX, toolbarHeight / 2f, theme.hint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(theme.hint);
                boolean gif = "GIF".equals(TOOLBAR_LABELS[i]);
                paint.setTextSize(gif ? dp(18) : dp(25));
                paint.setFakeBoldText(gif);
                drawCenteredText(canvas, TOOLBAR_LABELS[i], centerX, toolbarHeight / 2f);
                paint.setFakeBoldText(false);
            }
            left += width;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(0.5f)));
        paint.setColor(theme.stroke);
        canvas.drawLine(contentLeft, toolbarHeight - dp(0.5f),
                contentLeft + contentWidth, toolbarHeight - dp(0.5f), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawClipboardIcon(Canvas canvas, float centerX, float centerY, int color) {
        float width = dp(19);
        float height = dp(22);
        RectF board = new RectF(centerX - width / 2f, centerY - height / 2f + dp(1),
                centerX + width / 2f, centerY + height / 2f + dp(1));
        RectF clip = new RectF(centerX - dp(6), board.top - dp(3),
                centerX + dp(6), board.top + dp(5));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.8f, dp(1.8f)));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        canvas.drawRoundRect(board, dp(3), dp(3), paint);
        canvas.drawRoundRect(clip, dp(3), dp(3), paint);

        float lineLeft = board.left + dp(5);
        float lineRight = board.right - dp(5);
        float lineY = board.top + dp(10);
        paint.setStrokeWidth(Math.max(1.4f, dp(1.4f)));
        canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint);
        canvas.drawLine(lineLeft, lineY + dp(6), lineRight, lineY + dp(6), paint);

        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawClipboardPanel(Canvas canvas, float toolbarHeight) {
        SettingsStore.KeyboardTheme theme = settings.theme;
        float contentLeft = keyboardLeftInset();
        float contentWidth = keyboardContentWidth();
        RectF panel = new RectF(contentLeft + dp(10), toolbarHeight + dp(8),
                contentLeft + contentWidth - dp(10), keyboardHeight() - dp(8));

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(true);
        paint.setTextSize(dp(19));
        paint.setColor(theme.text);
        drawBaselineText(canvas, "클립보드", panel.left, panel.top + dp(22));
        paint.setFakeBoldText(false);

        float closeWidth = dp(58);
        clipboardCloseRect.set(panel.right - closeWidth, panel.top, panel.right, panel.top + dp(34));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(dp(14));
        paint.setColor(theme.hint);
        drawBaselineText(canvas, "닫기", clipboardCloseRect.right, clipboardCloseRect.centerY());

        RectF grid = new RectF(panel.left, panel.top + dp(42), panel.right, panel.bottom);
        layoutClipboardCards(grid);
        if (clipboardClips.isEmpty()) {
            drawEmptyClipboard(canvas, grid, theme);
            return;
        }
        for (ClipboardCard card : clipboardCards) {
            drawClipboardCard(canvas, card, theme);
        }
    }

    private void drawEmptyClipboard(Canvas canvas, RectF grid, SettingsStore.KeyboardTheme theme) {
        RectF emptyRect = new RectF(grid.left, grid.top, grid.right, Math.min(grid.bottom, grid.top + dp(96)));
        drawRaisedKeyBackground(canvas, emptyRect, theme.keyNormal, false, dp(10));
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(15));
        paint.setColor(theme.hint);
        String text = clipboardContextPreview == null || clipboardContextPreview.trim().isEmpty()
                ? "클립보드가 비어 있습니다."
                : clipboardContextPreview.trim();
        drawCenteredText(canvas, text, emptyRect.centerX(), emptyRect.centerY());
    }

    private void drawClipboardCard(Canvas canvas, ClipboardCard card, SettingsStore.KeyboardTheme theme) {
        boolean pressed = pressedKey != null && pressedKey.clipboard
                && pressedKey.key.clipboardIndex == card.clip.index;
        drawRaisedKeyBackground(canvas, card.rect, pressed ? theme.keyPressed : theme.keyNormal, pressed, dp(10));
        RectF inner = new RectF(card.rect.left + dp(10), card.rect.top + dp(10),
                card.rect.right - dp(10), card.rect.bottom - dp(10));

        paint.setShader(null);
        if (card.clip.thumbnail != null) {
            float imageHeight = Math.min(inner.height() - dp(28), card.rect.width() * 0.62f);
            RectF imageRect = new RectF(inner.left, inner.top, inner.right, inner.top + imageHeight);
            Path clipPath = new Path();
            clipPath.addRoundRect(imageRect, dp(8), dp(8), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawBitmap(card.clip.thumbnail, null, imageRect, paint);
            canvas.restore();
            inner.top = imageRect.bottom + dp(8);
        } else if (card.clip.image) {
            RectF iconRect = new RectF(inner.left, inner.top, inner.right, inner.top + dp(54));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(231, 236, 242));
            canvas.drawRoundRect(iconRect, dp(8), dp(8), paint);
            drawImagePlaceholder(canvas, iconRect, theme.hint);
            inner.top = iconRect.bottom + dp(8);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);
        paint.setTextSize(dp(12));
        paint.setColor(theme.hint);
        drawBaselineText(canvas, card.clip.typeLabel, inner.left, inner.top + dp(8));

        paint.setTextSize(dp(card.clip.image ? 13 : 18));
        paint.setColor(theme.text);
        String text = card.clip.displayText == null || card.clip.displayText.trim().isEmpty()
                ? (card.clip.image ? "이미지" : "텍스트")
                : card.clip.displayText.trim().replaceAll("\\s+", " ");
        RectF textRect = new RectF(inner.left, inner.top + dp(18), inner.right, inner.bottom);
        drawWrappedText(canvas, text, textRect, card.clip.image ? 2 : 4);
    }

    private void drawImagePlaceholder(Canvas canvas, RectF rect, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.8f, dp(1.8f)));
        paint.setColor(color);
        float left = rect.centerX() - dp(20);
        float top = rect.centerY() - dp(14);
        RectF imageBox = new RectF(left, top, left + dp(40), top + dp(28));
        canvas.drawRoundRect(imageBox, dp(4), dp(4), paint);
        canvas.drawCircle(imageBox.left + dp(10), imageBox.top + dp(9), dp(3), paint);
        Path mountain = new Path();
        mountain.moveTo(imageBox.left + dp(6), imageBox.bottom - dp(5));
        mountain.lineTo(imageBox.left + dp(17), imageBox.bottom - dp(15));
        mountain.lineTo(imageBox.left + dp(27), imageBox.bottom - dp(7));
        mountain.lineTo(imageBox.right - dp(5), imageBox.bottom - dp(18));
        canvas.drawPath(mountain, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawWrappedText(Canvas canvas, String text, RectF rect, int maxLines) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = (metrics.descent - metrics.ascent) * 1.15f;
        String remaining = text;
        float y = rect.top - metrics.ascent;
        int lines = 0;
        while (!remaining.isEmpty() && lines < maxLines && y <= rect.bottom) {
            int count = paint.breakText(remaining, true, rect.width(), null);
            if (count <= 0) {
                break;
            }
            int breakAt = count;
            if (count < remaining.length()) {
                int space = remaining.lastIndexOf(' ', count);
                if (space > 0) {
                    breakAt = space;
                }
            }
            String line = remaining.substring(0, breakAt).trim();
            int nextStart = breakAt;
            if (nextStart < remaining.length() && remaining.charAt(nextStart) == ' ') {
                nextStart++;
            }
            remaining = remaining.substring(nextStart).trim();
            if (lines == maxLines - 1 && !remaining.isEmpty()) {
                while (paint.measureText(line + "…") > rect.width() && line.length() > 1) {
                    line = line.substring(0, line.length() - 1);
                }
                line += "…";
            }
            canvas.drawText(line, rect.left, y, paint);
            y += lineHeight;
            lines++;
        }
    }

    private void drawBaselineText(Canvas canvas, String text, float x, float centerY) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, x, baseline, paint);
    }

    private void layoutClipboardCards(RectF grid) {
        clipboardCards.clear();
        if (clipboardClips.isEmpty()) {
            return;
        }
        float gap = dp(10);
        int columns = clipboardClips.size() == 1 ? 1 : (grid.width() >= dp(420) ? 3 : 2);
        float cardWidth = (grid.width() - gap * (columns - 1)) / columns;
        float[] columnBottoms = new float[columns];
        for (int i = 0; i < columns; i++) {
            columnBottoms[i] = grid.top;
        }
        for (ClipboardClip clip : clipboardClips) {
            int column = shortestColumn(columnBottoms);
            float left = grid.left + column * (cardWidth + gap);
            float top = columnBottoms[column];
            float height = clipboardCardHeight(clip, cardWidth);
            if (top + height > grid.bottom && !clipboardCards.isEmpty()) {
                continue;
            }
            RectF rect = new RectF(left, top, left + cardWidth, Math.min(grid.bottom, top + height));
            clipboardCards.add(new ClipboardCard(rect, clip));
            columnBottoms[column] = rect.bottom + gap;
        }
    }

    private int shortestColumn(float[] columnBottoms) {
        int column = 0;
        for (int i = 1; i < columnBottoms.length; i++) {
            if (columnBottoms[i] < columnBottoms[column]) {
                column = i;
            }
        }
        return column;
    }

    private float clipboardCardHeight(ClipboardClip clip, float width) {
        if (clip.thumbnail != null || clip.image) {
            return Math.max(dp(112), width * 0.72f + dp(46));
        }
        int length = clip.displayText == null ? 0 : clip.displayText.trim().length();
        if (length <= 18) {
            return dp(82);
        }
        if (length <= 70) {
            return dp(116);
        }
        return dp(152);
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
            float contentLeft = keyboardLeftInset();
            float left = contentLeft + gap;
            float availableWidth = keyboardContentWidth() - gap * (row.keys.size() + 1);
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

    private KeyBounds findClipboardKey(float x, float y) {
        if (clipboardCloseRect.contains(x, y)) {
            return new KeyBounds(KeySpec.command("", KeySpec.Type.CLIPBOARD_CLOSE), new RectF(clipboardCloseRect),
                    false, true);
        }
        RectF grid = new RectF(keyboardLeftInset() + dp(10), toolbarHeight() + dp(50),
                keyboardLeftInset() + keyboardContentWidth() - dp(10), keyboardHeight() - dp(8));
        layoutClipboardCards(grid);
        for (ClipboardCard card : clipboardCards) {
            if (card.rect.contains(x, y)) {
                return new KeyBounds(KeySpec.clipboardPaste("", card.clip.index, 1f), new RectF(card.rect),
                        false, true);
            }
        }
        return null;
    }

    private KeyBounds findToolbarKey(float x, float y) {
        float toolbarHeight = toolbarHeight();
        if (y < 0 || y > toolbarHeight) {
            return null;
        }
        float totalWeight = toolbarTotalWeight();
        float left = keyboardLeftInset();
        float contentWidth = keyboardContentWidth();
        for (int i = 0; i < TOOLBAR_LABELS.length; i++) {
            float width = contentWidth * (TOOLBAR_WEIGHTS[i] / totalWeight);
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
                return KeySpec.command("", KeySpec.Type.MODE_SYMBOLS);
            case 2:
                return KeySpec.command("", KeySpec.Type.CLIPBOARD_CONTEXT);
            case 3:
            case 5:
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

    private float keyboardLeftInset() {
        if (settings == null) {
            return 0f;
        }
        return Math.max(0f, getWidth() * settings.keyboardLeftInsetScale());
    }

    private float keyboardContentWidth() {
        if (settings == null) {
            return getWidth();
        }
        float left = keyboardLeftInset();
        float right = Math.min(getWidth(), getWidth() * settings.keyboardRightEdgeScale());
        return Math.max(dp(160), right - left);
    }

    private float keyboardBottom(float gap) {
        return keyboardHeight() - gap;
    }

    private int bottomSafeInset() {
        if (!bottomSafeInsetEnabled) {
            return 0;
        }
        return bottomSystemInset;
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
        if (clipboardContextVisible) {
            return;
        }
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
        float leftKeyWeight = hangulLeftKeyWeight();
        float rightKeyWeight = hangulRightKeyWeight();
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, leftKeyWeight),
                KeySpec.consonant(row1First).withHints("1", null),
                KeySpec.consonant(row1Second).withHints("2", null),
                KeySpec.consonant(row1Third).withHints("3", null),
                KeySpec.consonant(row1Fourth).withHints("4", null),
                KeySpec.consonant(row1Fifth).withHints("5", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, rightKeyWeight).withRowSpan(3)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, leftKeyWeight),
                KeySpec.consonant(row2First).withHints("6", null),
                KeySpec.consonant(row2Second).withHints("7", null),
                KeySpec.consonant(row2Third).withHints("8", null),
                KeySpec.consonant(row2Fourth).withHints("9", null),
                KeySpec.consonant(row2Fifth).withHints("0", null),
                KeySpec.spacer(rightKeyWeight)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, leftKeyWeight),
                KeySpec.consonant(row3First),
                KeySpec.consonant(row3Second),
                KeySpec.consonant(row3Third),
                KeySpec.consonant(row3Fourth),
                KeySpec.vowelPad("모음", 1.0f),
                KeySpec.spacer(rightKeyWeight)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, leftKeyWeight),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE).withHints("~   ,", null).withColumnSpan(2),
                KeySpec.spacer(1.0f),
                KeySpec.command("Go", KeySpec.Type.ENTER).withColumnSpan(2),
                KeySpec.spacer(rightKeyWeight)));
    }

    private float hangulLeftKeyWeight() {
        return settings == null ? HANGUL_LEFT_KEY_WEIGHT : settings.hangulLeftColumnWeight(5f);
    }

    private float hangulRightKeyWeight() {
        return settings == null ? HANGUL_RIGHT_KEY_WEIGHT : settings.hangulRightColumnWeight(5f);
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
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, SYMBOL_SIDE_KEY_WEIGHT).withRowSpan(2),
                symbolKey(0),
                symbolKey(1),
                symbolKey(2),
                symbolKey(3),
                symbolKey(4),
                symbolKey(5),
                symbolKey(6),
                KeySpec.command(symbolPageLabel(), KeySpec.Type.NO_OP, SYMBOL_SIDE_KEY_WEIGHT).withRowSpan(2)));
        rows.add(row(
                KeySpec.spacer(SYMBOL_SIDE_KEY_WEIGHT),
                symbolKey(7),
                symbolKey(8),
                symbolKey(9),
                symbolKey(10),
                symbolKey(11),
                symbolKey(12),
                symbolKey(13),
                KeySpec.spacer(SYMBOL_SIDE_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, SYMBOL_SIDE_KEY_WEIGHT).withRowSpan(2),
                symbolKey(14),
                symbolKey(15),
                symbolKey(16),
                symbolKey(17),
                symbolKey(18),
                symbolKey(19),
                symbolKey(20),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, SYMBOL_SIDE_KEY_WEIGHT).withRowSpan(2)));
        rows.add(row(
                KeySpec.spacer(SYMBOL_SIDE_KEY_WEIGHT),
                symbolKey(21),
                symbolKey(22),
                symbolKey(23),
                symbolKey(24),
                symbolKey(25),
                KeySpec.command("<", KeySpec.Type.SYMBOL_PAGE_PREV),
                KeySpec.command(">", KeySpec.Type.SYMBOL_PAGE_NEXT),
                KeySpec.spacer(SYMBOL_SIDE_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, SYMBOL_SIDE_KEY_WEIGHT),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 4.0f).withHints("~   ,", null),
                KeySpec.command("Go", KeySpec.Type.ENTER, 1.2f)));
    }

    private KeySpec symbolKey(int index) {
        String value = symbolValue(index);
        if (value.isEmpty()) {
            return KeySpec.spacer(1f);
        }
        return KeySpec.character(value, value);
    }

    private String symbolValue(int index) {
        String[] page = SYMBOL_PAGES[symbolPage];
        if (index < 0 || index >= page.length) {
            return "";
        }
        return page[index];
    }

    private String symbolPageLabel() {
        return (symbolPage + 1) + "\n/\n" + SYMBOL_PAGES.length;
    }

    private void buildNumberRows() {
        buildImageNumberRows();
    }

    private void buildImageNumberRows() {
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, NUMBER_SIDE_KEY_WEIGHT).withRowSpan(2),
                KeySpec.character("1", "1").withHints("*", null),
                KeySpec.character("2", "2").withHints("/", null),
                KeySpec.character("3", "3").withHints("=", null),
                KeySpec.character("-", "-")));
        rows.add(row(
                KeySpec.spacer(NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.character("4", "4").withHints("%", null),
                KeySpec.character("5", "5").withHints("'", null),
                KeySpec.character("6", "6").withHints("#", null),
                KeySpec.character("+", "+")));
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.character("7", "7").withHints("(", null),
                KeySpec.character("8", "8").withHints(")", null),
                KeySpec.character("9", "9").withHints("?", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE).withHints("~   ,", null),
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

    public interface GestureTraceListener {
        void onGestureTrace(GestureTrace trace);
    }

    public static class GestureTrace {
        public final KeySpec key;
        public final RectF keyRect;
        public final Integer mappedVowel;
        public final GestureVowelMapper.Trace motion;

        GestureTrace(KeySpec key, RectF keyRect, Integer mappedVowel, GestureVowelMapper.Trace motion) {
            this.key = key;
            this.keyRect = keyRect;
            this.mappedVowel = mappedVowel;
            this.motion = motion;
        }
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
        final boolean clipboard;

        KeyBounds(KeySpec key, RectF rect) {
            this(key, rect, false, false);
        }

        KeyBounds(KeySpec key, RectF rect, boolean toolbar) {
            this(key, rect, toolbar, false);
        }

        KeyBounds(KeySpec key, RectF rect, boolean toolbar, boolean clipboard) {
            this.key = key;
            this.rect = rect;
            this.toolbar = toolbar;
            this.clipboard = clipboard;
        }
    }

    public static class ClipboardClip {
        public final int index;
        public final String displayText;
        public final String typeLabel;
        public final Bitmap thumbnail;
        public final boolean image;

        public ClipboardClip(int index, String displayText, String typeLabel, Bitmap thumbnail, boolean image) {
            this.index = index;
            this.displayText = displayText == null ? "" : displayText;
            this.typeLabel = typeLabel == null || typeLabel.trim().isEmpty() ? "클립" : typeLabel;
            this.thumbnail = thumbnail;
            this.image = image;
        }
    }

    private static class ClipboardCard {
        final RectF rect;
        final ClipboardClip clip;

        ClipboardCard(RectF rect, ClipboardClip clip) {
            this.rect = rect;
            this.clip = clip;
        }
    }
}
