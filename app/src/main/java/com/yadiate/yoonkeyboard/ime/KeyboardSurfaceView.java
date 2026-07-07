package com.yadiate.yoonkeyboard.ime;

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

import com.yadiate.yoonkeyboard.SettingsStore;
import com.yadiate.yoonkeyboard.hangul.Consonant;
import com.yadiate.yoonkeyboard.hangul.GestureVowelMapper;
import com.yadiate.yoonkeyboard.hangul.HangulComposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyboardSurfaceView extends View {
    private static final float HANGUL_LEFT_KEY_WEIGHT = 1.1f;
    private static final float HANGUL_RIGHT_KEY_WEIGHT = 1.45f;
    private static final float FOLD_SPLIT_CENTER_GAP_WEIGHT = 2.6f;
    private static final float SYMBOL_SIDE_KEY_WEIGHT = HANGUL_LEFT_KEY_WEIGHT;
    private static final float NUMBER_SIDE_KEY_WEIGHT = HANGUL_LEFT_KEY_WEIGHT;
    private static final int DEFAULT_KEYBOARD_BODY_DP = 252;
    private static final int TALL_KEYBOARD_BODY_DP = 310;
    private static final int GESTURE_START_SLOP_DP = 10;
    private static final int INVALID_POINTER_ID = -1;
    private static final int DEBUG_TOUCH_MARK_LIMIT = 80;
    private static final float LEFT_KEY_HIT_SCORE_SCALE = 0.72f;
    private static final float RIGHT_KEY_HIT_SCORE_SCALE = 1.08f;
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
    private final List<DeferredPointerTap> deferredPointerTaps = new ArrayList<>();
    private final List<DebugTouchMark> debugTouchMarks = new ArrayList<>();
    private final Set<Integer> ignoredPointerIds = new HashSet<>();
    private final GestureVowelMapper gestureMapper;
    private SettingsStore.Snapshot settings;
    private KeyboardActionListener listener;
    private GestureTraceListener gestureTraceListener;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private boolean shift;
    private boolean capsLock;
    private int symbolPage;
    private int bottomSystemInset;
    private boolean bottomSafeInsetEnabled = true;
    private boolean clipboardContextVisible;
    private String clipboardContextPreview = "";
    private final RectF clipboardCloseRect = new RectF();
    private KeyBounds pressedKey;
    private Runnable longPressRunnable;
    private Runnable deleteRepeatRunnable;
    private Runnable keyPreviewRunnable;
    private boolean longPressFired;
    private boolean keyPreviewVisible;
    private boolean gestureDragStarted;
    private boolean pressedKeyHandledOnTouchDown;
    private boolean gesturePreviewVisible;
    private boolean gesturePreviewHapticPlayed;
    private String gesturePreviewLabel = "";
    private int activePointerId = INVALID_POINTER_ID;
    private float touchDownX;
    private float touchDownY;
    private boolean debugTouchActive;
    private float debugTouchX;
    private float debugTouchY;

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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (settings != null && settings.foldSplitKeyboardEnabled() && w != oldw) {
            buildRows();
            invalidate();
        }
    }

    public void setSettings(SettingsStore.Snapshot settings) {
        this.settings = settings;
        applyGestureSettings();
        setBackgroundColor(settings.theme.background);
        if (!debugTouchOverlayEnabled()) {
            debugTouchMarks.clear();
            debugTouchActive = false;
        }
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
        gestureMapper.setStrokeLengths(settings.shortStrokeMm(), settings.derivationShortStrokeMm(),
                settings.longStrokeMm());
        gestureMapper.setBlockYeoUpToYe(settings.blockYeoUpToYe);
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
        this.capsLock = false;
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
        if (shift) {
            this.capsLock = false;
        }
        buildRows();
        invalidate();
    }

    public void toggleShift() {
        if (capsLock) {
            capsLock = false;
            shift = false;
        } else {
            shift = !shift;
        }
        buildRows();
        invalidate();
    }

    public void enableCapsLock() {
        capsLock = true;
        shift = false;
        buildRows();
        invalidate();
    }

    public void consumeOneShotShift() {
        if (!shift || capsLock) {
            return;
        }
        shift = false;
        buildRows();
        invalidate();
    }

    public KeyboardMode getMode() {
        return mode;
    }

    public boolean isShift() {
        return shift;
    }

    public boolean isCapsLock() {
        return capsLock;
    }

    private boolean isUppercaseMode() {
        return shift || capsLock;
    }

    public RectF currentConsonantKeyRect(Consonant consonant) {
        KeyBounds keyBounds = findConsonantKey(consonant);
        return keyBounds == null ? null : new RectF(keyBounds.rect);
    }

    public String currentConsonantCalibrationKey(Consonant consonant) {
        KeyBounds keyBounds = findConsonantKey(consonant);
        return keyBounds == null || keyBounds.key == null ? "" : keyBounds.key.calibrationKey;
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
        int bodyHeightDp = mode != KeyboardMode.SYMBOLS && rowCount >= 5
                ? TALL_KEYBOARD_BODY_DP
                : DEFAULT_KEYBOARD_BODY_DP;
        int desiredHeight = Math.round(dp(bodyHeightDp) * heightScale)
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
        if (clipboardContextVisible) {
            drawClipboardPanel(canvas);
            return;
        }
        float top = gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight;
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
        drawDebugTouchOverlay(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ignoredPointerIds.clear();
                deferredPointerTaps.clear();
                activePointerId = event.getPointerId(event.getActionIndex());
                beginKeyTouch(event.getX(event.getActionIndex()), event.getY(event.getActionIndex()),
                        event.getEventTime());
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                int newPointerIndex = event.getActionIndex();
                int newPointerId = event.getPointerId(newPointerIndex);
                if (shouldTransferSpaceTouchTo(event.getX(newPointerIndex), event.getY(newPointerIndex))) {
                    ignoreActivePointer();
                    commitPressedKeyAsTap();
                    activePointerId = newPointerId;
                    beginKeyTouch(event.getX(newPointerIndex), event.getY(newPointerIndex), event.getEventTime());
                } else if (shouldFocusPointer(event.getX(newPointerIndex), event.getY(newPointerIndex))) {
                    ignoreActivePointer();
                    cancelKeyTouch();
                    activePointerId = newPointerId;
                    beginKeyTouch(event.getX(newPointerIndex), event.getY(newPointerIndex), event.getEventTime());
                } else {
                    deferPointerTapIfNeeded(newPointerId,
                            event.getX(newPointerIndex), event.getY(newPointerIndex));
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                int movePointerIndex = activePointerIndex(event);
                if (movePointerIndex < 0) {
                    return true;
                }
                float moveX = event.getX(movePointerIndex);
                float moveY = event.getY(movePointerIndex);
                moveDebugTouch(moveX, moveY);
                if (pressedKey == null && actionableKeyAt(moveX, moveY) != null) {
                    beginKeyTouch(moveX, moveY, event.getEventTime());
                    return true;
                }
                boolean dragStarted = canStartDragGesture(pressedKey)
                        && (movedBeyondGestureStartSlop(moveX, moveY)
                        || historicalMovedBeyondGestureStartSlop(event, movePointerIndex));
                if (dragStarted) {
                    gestureDragStarted = true;
                    cancelScheduledLongPress();
                    cancelScheduledKeyPreview();
                    keyPreviewVisible = false;
                    if (!gesturePreviewVisible) {
                        showInitialGesturePreviewIfNeeded(pressedKey);
                    }
                    invalidate();
                }
                if (pressedKey == null || !pressedKey.clipboard) {
                    appendHistoricalGesturePointsIfNeeded(event, movePointerIndex);
                    gesturePoints.add(new GestureVowelMapper.Point(moveX, moveY, event.getEventTime()));
                }
                if (gesturePreviewVisible && pressedKey != null && !pressedKey.clipboard) {
                    updateGesturePreview(pressedKey);
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int pointerUpIndex = event.getActionIndex();
                int pointerUpId = event.getPointerId(pointerUpIndex);
                if (pointerUpId == activePointerId) {
                    appendHistoricalGesturePointsIfNeeded(event, pointerUpIndex);
                    endDebugTouch(event.getX(pointerUpIndex), event.getY(pointerUpIndex));
                    finishKeyTouch(event.getX(pointerUpIndex), event.getY(pointerUpIndex), event.getEventTime());
                    activePointerId = INVALID_POINTER_ID;
                    ignoredPointerIds.remove(pointerUpId);
                    if (!promoteRemainingPointer(event, pointerUpIndex)) {
                        flushReleasedDeferredPointerTaps();
                    }
                } else {
                    ignoredPointerIds.remove(pointerUpId);
                    markDeferredPointerReleased(pointerUpId);
                }
                return true;
            case MotionEvent.ACTION_UP:
                int upPointerIndex = activePointerIndex(event);
                if (upPointerIndex < 0) {
                    upPointerIndex = event.getActionIndex();
                }
                appendHistoricalGesturePointsIfNeeded(event, upPointerIndex);
                endDebugTouch(event.getX(upPointerIndex), event.getY(upPointerIndex));
                finishKeyTouch(event.getX(upPointerIndex), event.getY(upPointerIndex), event.getEventTime());
                activePointerId = INVALID_POINTER_ID;
                ignoredPointerIds.clear();
                flushReleasedDeferredPointerTaps();
                deferredPointerTaps.clear();
                return true;
            case MotionEvent.ACTION_CANCEL:
                activePointerId = INVALID_POINTER_ID;
                ignoredPointerIds.clear();
                deferredPointerTaps.clear();
                cancelDebugTouch();
                cancelKeyTouch();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void beginKeyTouch(float x, float y, long eventTime) {
        beginKeyTouch(x, y, eventTime, null);
    }

    private void beginKeyTouch(float x, float y, long eventTime, DeferredPointerTap deferredTap) {
        requestParentTouchIntercept(false);
        beginDebugTouch(x, y);
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        longPressFired = false;
        keyPreviewVisible = false;
        gestureDragStarted = false;
        pressedKeyHandledOnTouchDown = false;
        clearGesturePreview();
        touchDownX = x;
        touchDownY = y;
        gesturePoints.clear();
        if (clipboardContextVisible) {
            pressedKey = findClipboardKey(x, y);
            if (!isActionableKey(pressedKey)) {
                pressedKey = null;
            }
        } else {
            pressedKey = deferredTap == null ? findKey(x, y) : deferredTap.keyBounds;
            if (!isActionableKey(pressedKey)) {
                pressedKey = null;
            }
            if (pressedKey != null) {
                gesturePoints.add(new GestureVowelMapper.Point(x, y, eventTime));
                pressedKeyHandledOnTouchDown = deferredTap != null && deferredTap.handledOnTouchDown;
                if (!pressedKeyHandledOnTouchDown) {
                    pressedKeyHandledOnTouchDown = handleKeyTouchDownImmediately(pressedKey);
                }
                scheduleKeyPreviewIfNeeded(pressedKey);
                scheduleLongPressIfNeeded(pressedKey);
            }
        }
        invalidate();
    }

    private void finishKeyTouch(float x, float y, long eventTime) {
        requestParentTouchIntercept(true);
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        KeyBounds releasedKey = pressedKey;
        if (longPressFired) {
            longPressFired = false;
            pressedKey = null;
            pressedKeyHandledOnTouchDown = false;
            keyPreviewVisible = false;
            gestureDragStarted = false;
            clearGesturePreview();
            invalidate();
            gesturePoints.clear();
            return;
        }
        if (releasedKey == null || listener == null) {
            pressedKey = null;
            pressedKeyHandledOnTouchDown = false;
            keyPreviewVisible = false;
            gestureDragStarted = false;
            clearGesturePreview();
            invalidate();
            gesturePoints.clear();
            return;
        }
        if (releasedKey.clipboard) {
            pressedKey = null;
            pressedKeyHandledOnTouchDown = false;
            keyPreviewVisible = false;
            gestureDragStarted = false;
            clearGesturePreview();
            invalidate();
            gesturePoints.clear();
            listener.onKey(releasedKey.key);
            return;
        }
        gesturePoints.add(new GestureVowelMapper.Point(x, y, eventTime));
        boolean keyHandledOnTouchDown = pressedKeyHandledOnTouchDown;
        boolean gestureCandidate = gestureDragStarted
                || movedBeyondGestureStartSlop(x, y)
                || gesturePathMovedBeyondStartSlop();
        String spaceSymbol = gestureCandidate ? spaceSymbolForGesture(releasedKey) : null;
        Integer vowel = gestureCandidate && spaceSymbol == null
                ? gestureMapper.map(gesturePoints, releasedKey.key.consonant, releasedKey.key.calibrationKey)
                : null;
        List<GestureVowelMapper.Point> tracedPoints = new ArrayList<>(gesturePoints);
        notifyConsonantTouch(releasedKey);
        boolean playCommitHaptic = !gesturePreviewHapticPlayed;
        if (spaceSymbol == null && shouldHandleGesture(releasedKey.key, vowel)) {
            boolean finalPreviewChanged = updateGesturePreview(releasedKey, vowel);
            playCommitHaptic = playCommitHaptic && !finalPreviewChanged;
        }
        pressedKey = null;
        pressedKeyHandledOnTouchDown = false;
        keyPreviewVisible = false;
        gestureDragStarted = false;
        clearGesturePreview();
        invalidate();
        if (spaceSymbol != null) {
            gesturePoints.clear();
            listener.onKey(KeySpec.character(spaceSymbol, spaceSymbol));
        } else if (shouldHandleGesture(releasedKey.key, vowel)) {
            notifyGestureTrace(releasedKey, vowel, tracedPoints);
            gesturePoints.clear();
            listener.onGesture(releasedKey.key, vowel, playCommitHaptic, keyHandledOnTouchDown);
        } else {
            gesturePoints.clear();
            if (!keyHandledOnTouchDown) {
                listener.onKey(releasedKey.key);
            }
        }
    }

    private void cancelKeyTouch() {
        requestParentTouchIntercept(true);
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        longPressFired = false;
        keyPreviewVisible = false;
        gestureDragStarted = false;
        pressedKeyHandledOnTouchDown = false;
        clearGesturePreview();
        pressedKey = null;
        gesturePoints.clear();
        invalidate();
    }

    private boolean handleKeyTouchDownImmediately(KeyBounds keyBounds) {
        if (!shouldHandleKeyTouchDownImmediately(keyBounds)) {
            return false;
        }
        listener.onKeyTouchDown(keyBounds.key);
        return true;
    }

    private boolean shouldHandleKeyTouchDownImmediately(KeyBounds keyBounds) {
        return listener != null
                && keyBounds != null
                && !keyBounds.clipboard
                && keyBounds.key != null
                && (keyBounds.key.type == KeySpec.Type.DELETE
                || (mode == KeyboardMode.HANGUL
                && keyBounds.key.type == KeySpec.Type.HANGUL_CONSONANT));
    }

    private void commitPressedKeyAsTap() {
        KeyBounds keyBounds = pressedKey;
        if (keyBounds == null || listener == null) {
            cancelKeyTouch();
            return;
        }
        cancelScheduledLongPress();
        cancelScheduledKeyPreview();
        longPressFired = false;
        keyPreviewVisible = false;
        gestureDragStarted = false;
        boolean keyHandledOnTouchDown = pressedKeyHandledOnTouchDown;
        pressedKeyHandledOnTouchDown = false;
        clearGesturePreview();
        pressedKey = null;
        gesturePoints.clear();
        invalidate();
        if (!keyHandledOnTouchDown) {
            listener.onKey(keyBounds.key);
        }
    }

    private void ignoreActivePointer() {
        if (activePointerId != INVALID_POINTER_ID) {
            ignoredPointerIds.add(activePointerId);
        }
    }

    private boolean shouldFocusPointer(float x, float y) {
        return listener != null
                && pressedKey == null
                && actionableKeyAt(x, y) != null;
    }

    private boolean promoteRemainingPointer(MotionEvent event, int excludedPointerIndex) {
        int nextPointerIndex = promotablePointerIndex(event, excludedPointerIndex);
        if (nextPointerIndex < 0) {
            return false;
        }
        activePointerId = event.getPointerId(nextPointerIndex);
        DeferredPointerTap deferredTap = deferredPointerTap(activePointerId);
        removeDeferredPointerTap(activePointerId);
        beginKeyTouch(event.getX(nextPointerIndex), event.getY(nextPointerIndex),
                event.getEventTime(), deferredTap);
        return true;
    }

    private int promotablePointerIndex(MotionEvent event, int excludedPointerIndex) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == excludedPointerIndex) {
                continue;
            }
            int pointerId = event.getPointerId(i);
            if (ignoredPointerIds.contains(pointerId)) {
                continue;
            }
            if (actionableKeyAt(event.getX(i), event.getY(i)) != null) {
                return i;
            }
        }
        return -1;
    }

    private void deferPointerTapIfNeeded(int pointerId, float x, float y) {
        if (pointerId == activePointerId || ignoredPointerIds.contains(pointerId)) {
            return;
        }
        KeyBounds keyBounds = actionableKeyAt(x, y);
        if (!canDeferPointerTap(keyBounds)) {
            return;
        }
        removeDeferredPointerTap(pointerId);
        boolean handledOnTouchDown = handleKeyTouchDownImmediately(keyBounds);
        deferredPointerTaps.add(new DeferredPointerTap(pointerId, keyBounds, handledOnTouchDown));
    }

    private boolean canDeferPointerTap(KeyBounds keyBounds) {
        if (keyBounds == null || keyBounds.clipboard) {
            return false;
        }
        switch (keyBounds.key.type) {
            case HANGUL_CONSONANT:
            case HANGUL_VOWEL:
            case CHARACTER:
            case DELETE:
            case SPACE:
            case ENTER:
            case MOVE_LEFT:
            case MOVE_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private void markDeferredPointerReleased(int pointerId) {
        DeferredPointerTap tap = deferredPointerTap(pointerId);
        if (tap == null) {
            return;
        }
        tap.released = true;
        if (activePointerId == INVALID_POINTER_ID || pressedKey == null) {
            flushReleasedDeferredPointerTaps();
        }
    }

    private void flushReleasedDeferredPointerTaps() {
        if (listener == null || deferredPointerTaps.isEmpty()) {
            return;
        }
        List<DeferredPointerTap> taps = new ArrayList<>(deferredPointerTaps);
        deferredPointerTaps.clear();
        for (DeferredPointerTap tap : taps) {
            if (tap.released) {
                if (tap.handledOnTouchDown) {
                    continue;
                }
                if (shouldHandleKeyTouchDownImmediately(tap.keyBounds)) {
                    listener.onKeyTouchDown(tap.keyBounds.key);
                } else {
                    listener.onKey(tap.keyBounds.key);
                }
            } else {
                deferredPointerTaps.add(tap);
            }
        }
    }

    private DeferredPointerTap deferredPointerTap(int pointerId) {
        for (DeferredPointerTap tap : deferredPointerTaps) {
            if (tap.pointerId == pointerId) {
                return tap;
            }
        }
        return null;
    }

    private void removeDeferredPointerTap(int pointerId) {
        for (int i = deferredPointerTaps.size() - 1; i >= 0; i--) {
            if (deferredPointerTaps.get(i).pointerId == pointerId) {
                deferredPointerTaps.remove(i);
            }
        }
    }

    private boolean shouldTransferSpaceTouchTo(float x, float y) {
        if (pressedKey == null
                || listener == null
                || longPressFired
                || gestureDragStarted
                || pressedKey.clipboard
                || pressedKey.key.type != KeySpec.Type.SPACE) {
            return false;
        }
        KeyBounds nextKey = actionableKeyAt(x, y);
        return nextKey != null
                && !nextKey.clipboard
                && nextKey.key.type != KeySpec.Type.SPACE;
    }

    private KeyBounds actionableKeyAt(float x, float y) {
        KeyBounds keyBounds = clipboardContextVisible ? findClipboardKey(x, y) : findKey(x, y);
        return isActionableKey(keyBounds) ? keyBounds : null;
    }

    private int hitPriority(KeySpec key) {
        switch (key.type) {
            case HANGUL_CONSONANT:
            case HANGUL_VOWEL:
            case CHARACTER:
                return 0;
            case HANGUL_VOWEL_PAD:
            case SPACE:
            case ENTER:
            case DELETE:
                return 1;
            default:
                return 2;
        }
    }

    private boolean isActionableKey(KeyBounds keyBounds) {
        return keyBounds != null
                && keyBounds.key != null
                && keyBounds.key.type != KeySpec.Type.NO_OP;
    }

    private int activePointerIndex(MotionEvent event) {
        if (activePointerId == INVALID_POINTER_ID) {
            return -1;
        }
        return event.findPointerIndex(activePointerId);
    }

    private boolean shouldHandleGesture(KeySpec key, Integer vowel) {
        return vowel != null
                && mode == KeyboardMode.HANGUL
                && canStartVowelGesture(key);
    }

    private boolean canStartDragGesture(KeyBounds keyBounds) {
        if (keyBounds == null || keyBounds.clipboard || keyBounds.key == null) {
            return false;
        }
        return canStartVowelGesture(keyBounds.key)
                || keyBounds.key.type == KeySpec.Type.SPACE;
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

    private void notifyConsonantTouch(KeyBounds keyBounds) {
        if (gestureTraceListener == null
                || keyBounds == null
                || keyBounds.key == null
                || keyBounds.key.type != KeySpec.Type.HANGUL_CONSONANT
                || keyBounds.key.consonant == null) {
            return;
        }
        gestureTraceListener.onConsonantTouch(new ConsonantTouch(keyBounds.key.consonant,
                new RectF(keyBounds.rect), touchDownX, touchDownY));
    }

    private void showInitialGesturePreviewIfNeeded(KeyBounds keyBounds) {
        if (!canShowGesturePreview(keyBounds)) {
            return;
        }
        String label = gesturePreviewLabelFor(keyBounds.key, null);
        if (label.isEmpty()) {
            return;
        }
        gesturePreviewLabel = label;
        gesturePreviewVisible = true;
    }

    private boolean updateGesturePreview(KeyBounds keyBounds) {
        Integer vowel = gestureMapper.map(gesturePoints, keyBounds.key.consonant, keyBounds.key.calibrationKey);
        return updateGesturePreview(keyBounds, vowel);
    }

    private boolean updateGesturePreview(KeyBounds keyBounds, Integer vowel) {
        if (!canShowGesturePreview(keyBounds)) {
            return false;
        }
        String nextLabel = gesturePreviewLabelFor(keyBounds.key, vowel);
        if (nextLabel.isEmpty()) {
            return false;
        }
        if (!gesturePreviewVisible) {
            gesturePreviewLabel = nextLabel;
            gesturePreviewVisible = true;
            invalidate();
            return false;
        }
        if (nextLabel.equals(gesturePreviewLabel)) {
            return false;
        }
        gesturePreviewLabel = nextLabel;
        gesturePreviewHapticPlayed = true;
        if (listener != null) {
            listener.onGesturePreviewChanged(nextLabel);
        }
        invalidate();
        return true;
    }

    private boolean canShowGesturePreview(KeyBounds keyBounds) {
        return keyBounds != null
                && !keyBounds.clipboard
                && mode == KeyboardMode.HANGUL
                && canStartVowelGesture(keyBounds.key);
    }

    private String gesturePreviewLabelFor(KeySpec key, Integer vowel) {
        if (key.type == KeySpec.Type.HANGUL_CONSONANT) {
            if (vowel != null) {
                return HangulComposer.previewSyllable(key.consonant, vowel);
            }
            return key.consonant == null ? "" : key.consonant.label();
        }
        if (key.type == KeySpec.Type.HANGUL_VOWEL || key.type == KeySpec.Type.HANGUL_VOWEL_PAD) {
            return vowel == null ? primaryKeyLabel(key) : HangulComposer.compatVowel(vowel);
        }
        return "";
    }

    private void clearGesturePreview() {
        gesturePreviewVisible = false;
        gesturePreviewHapticPlayed = false;
        gesturePreviewLabel = "";
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
        if (pressedKeyHandledOnTouchDown
                && keyBounds.key.type == KeySpec.Type.HANGUL_CONSONANT
                && !canLongPressTopHint(keyBounds.key)) {
            return;
        }
        KeySpec key = keyBounds.key;
        longPressRunnable = () -> {
            if (!isPressedKeyStillActive(key) || listener == null) {
                return;
            }
            if (key.type == KeySpec.Type.DELETE) {
                startDeleteRepeat(key);
                return;
            }
            longPressFired = true;
            gesturePoints.clear();
            longPressRunnable = null;
            if (pressedKeyHandledOnTouchDown
                    && key.type == KeySpec.Type.HANGUL_CONSONANT
                    && canLongPressTopHint(key)) {
                listener.onKey(KeySpec.command("", KeySpec.Type.CANCEL_TOUCH_DOWN));
            }
            listener.onKey(longPressKeyFor(key));
            invalidate();
        };
        postDelayed(longPressRunnable, longPressDelayMs(key));
    }

    private boolean canLongPress(KeySpec key) {
        return key.type == KeySpec.Type.DELETE
                || canLongPressShiftLock(key)
                || canLongPressTopHint(key)
                || canLongPressClipboardContext(key);
    }

    private int longPressDelayMs(KeySpec key) {
        if (key.type == KeySpec.Type.DELETE) {
            return deleteRepeatStartMs();
        }
        return settings.longPressTimeoutMs();
    }

    private boolean canLongPressTopHint(KeySpec key) {
        if (!modeSupportsTopHintLongPress()) {
            return false;
        }
        if (key.hintTop == null || key.hintTop.trim().isEmpty()) {
            return false;
        }
        return key.type == KeySpec.Type.CHARACTER
                || key.type == KeySpec.Type.HANGUL_CONSONANT
                || key.type == KeySpec.Type.HANGUL_VOWEL;
    }

    private boolean modeSupportsTopHintLongPress() {
        return mode == KeyboardMode.HANGUL
                || mode == KeyboardMode.ENGLISH
                || mode == KeyboardMode.NUMBERS
                || mode == KeyboardMode.SYMBOLS;
    }

    private boolean canLongPressShiftLock(KeySpec key) {
        return mode == KeyboardMode.ENGLISH && key.type == KeySpec.Type.SHIFT;
    }

    private boolean canLongPressClipboardContext(KeySpec key) {
        return !clipboardContextVisible && mode == KeyboardMode.HANGUL && key.type == KeySpec.Type.SETTINGS;
    }

    private KeySpec longPressKeyFor(KeySpec key) {
        if (canLongPressShiftLock(key)) {
            return KeySpec.command("", KeySpec.Type.SHIFT_LOCK);
        }
        if (canLongPressClipboardContext(key)) {
            return KeySpec.command("", KeySpec.Type.CLIPBOARD_CONTEXT);
        }
        return KeySpec.character(key.hintTop, key.hintTop);
    }

    private void startDeleteRepeat(KeySpec key) {
        longPressFired = true;
        gesturePoints.clear();
        longPressRunnable = null;
        deleteRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPressedKeyStillActive(key) || listener == null) {
                    deleteRepeatRunnable = null;
                    return;
                }
                listener.onKey(key);
                postDelayed(this, deleteRepeatIntervalMs());
            }
        };
        postDelayed(deleteRepeatRunnable, deleteRepeatIntervalMs());
        invalidate();
    }

    private boolean isPressedKeyStillActive(KeySpec expectedKey) {
        if (pressedKey == null || pressedKey.key == null) {
            return false;
        }
        if (expectedKey.type == KeySpec.Type.DELETE) {
            return pressedKey.key.type == KeySpec.Type.DELETE;
        }
        return pressedKey.key == expectedKey;
    }

    private int deleteRepeatStartMs() {
        return settings == null
                ? SettingsStore.DEFAULT_DELETE_REPEAT_START_MS
                : settings.deleteRepeatStartMs();
    }

    private int deleteRepeatIntervalMs() {
        return settings == null
                ? SettingsStore.DEFAULT_DELETE_REPEAT_INTERVAL_MS
                : settings.deleteRepeatIntervalMs();
    }

    private void cancelScheduledLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
        if (deleteRepeatRunnable != null) {
            removeCallbacks(deleteRepeatRunnable);
            deleteRepeatRunnable = null;
        }
    }

    private void cancelScheduledKeyPreview() {
        if (keyPreviewRunnable != null) {
            removeCallbacks(keyPreviewRunnable);
            keyPreviewRunnable = null;
        }
    }

    private boolean movedBeyondGestureStartSlop(float x, float y) {
        float dx = x - touchDownX;
        float dy = y - touchDownY;
        return Math.hypot(dx, dy) > gestureStartSlopPx();
    }

    private boolean historicalMovedBeyondGestureStartSlop(MotionEvent event, int pointerIndex) {
        for (int i = 0; i < event.getHistorySize(); i++) {
            if (movedBeyondGestureStartSlop(
                    event.getHistoricalX(pointerIndex, i),
                    event.getHistoricalY(pointerIndex, i))) {
                return true;
            }
        }
        return false;
    }

    private boolean gesturePathMovedBeyondStartSlop() {
        for (GestureVowelMapper.Point point : gesturePoints) {
            if (movedBeyondGestureStartSlop(point.x, point.y)) {
                return true;
            }
        }
        return false;
    }

    private void appendHistoricalGesturePoints(MotionEvent event, int pointerIndex) {
        for (int i = 0; i < event.getHistorySize(); i++) {
            gesturePoints.add(new GestureVowelMapper.Point(
                    event.getHistoricalX(pointerIndex, i),
                    event.getHistoricalY(pointerIndex, i),
                    event.getHistoricalEventTime(i)));
        }
    }

    private void appendHistoricalGesturePointsIfNeeded(MotionEvent event, int pointerIndex) {
        if (pointerIndex < 0 || (pressedKey != null && pressedKey.clipboard)) {
            return;
        }
        appendHistoricalGesturePoints(event, pointerIndex);
    }

    private float gestureStartSlopPx() {
        float defaultSlop = dp(GESTURE_START_SLOP_DP);
        if (settings == null) {
            return defaultSlop;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float fallbackDpi = metrics.densityDpi > 0 ? metrics.densityDpi : metrics.density * 160f;
        float xDpi = reasonableDpi(metrics.xdpi) ? metrics.xdpi : fallbackDpi;
        float yDpi = reasonableDpi(metrics.ydpi) ? metrics.ydpi : fallbackDpi;
        float shortStrokePx = settings.shortStrokeMm() * Math.max(1f, Math.min(xDpi, yDpi) / 25.4f);
        return Math.max(dp(1), Math.min(defaultSlop, shortStrokePx));
    }

    private boolean reasonableDpi(float dpi) {
        return dpi >= 80f && dpi <= 900f;
    }

    private boolean debugTouchOverlayEnabled() {
        return settings != null && settings.debugTouchOverlay;
    }

    private void beginDebugTouch(float x, float y) {
        if (!debugTouchOverlayEnabled() || clipboardContextVisible) {
            return;
        }
        debugTouchActive = true;
        debugTouchX = x;
        debugTouchY = y;
        addDebugTouchMark(x, y, true);
    }

    private void moveDebugTouch(float x, float y) {
        if (!debugTouchOverlayEnabled() || !debugTouchActive) {
            return;
        }
        debugTouchX = x;
        debugTouchY = y;
        invalidate();
    }

    private void endDebugTouch(float x, float y) {
        if (!debugTouchOverlayEnabled()) {
            debugTouchActive = false;
            return;
        }
        if (debugTouchActive) {
            addDebugTouchMark(x, y, false);
        }
        debugTouchActive = false;
        invalidate();
    }

    private void cancelDebugTouch() {
        if (debugTouchOverlayEnabled() && debugTouchActive) {
            addDebugTouchMark(debugTouchX, debugTouchY, false);
        }
        debugTouchActive = false;
        invalidate();
    }

    private void addDebugTouchMark(float x, float y, boolean down) {
        debugTouchMarks.add(new DebugTouchMark(x, y, down));
        while (debugTouchMarks.size() > DEBUG_TOUCH_MARK_LIMIT) {
            debugTouchMarks.remove(0);
        }
        invalidate();
    }

    private void drawKeyPreview(Canvas canvas) {
        if (pressedKey == null || pressedKey.clipboard) {
            return;
        }
        boolean gesturePreview = gesturePreviewVisible && !gesturePreviewLabel.isEmpty();
        if (!gesturePreview && (!keyPreviewVisible || !canShowKeyPreview(pressedKey.key))) {
            return;
        }
        boolean auxiliaryPreview = !gesturePreview && hasAuxiliaryPreview(pressedKey.key);
        String label = gesturePreview ? gesturePreviewLabel : previewLabel(pressedKey.key);
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

    private void drawDebugTouchOverlay(Canvas canvas) {
        if (!debugTouchOverlayEnabled() || clipboardContextVisible) {
            return;
        }
        drawDebugHitBoxes(canvas);
        drawDebugTouchMarks(canvas);
    }

    private void drawDebugHitBoxes(Canvas canvas) {
        float gap = dp(4);
        float top = gap;
        float keyboardHeight = keyboardHeight();
        float rowHeight = (keyboardHeight - gap * (rows.size() + 1)) / rows.size();
        paint.setShader(null);
        paint.setTextAlign(Paint.Align.LEFT);
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
                if (key.type != KeySpec.Type.NO_OP) {
                    RectF hitRect = expandedKeyHitRect(touchRect, gap, key);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.argb(36, 255, 59, 48));
                    canvas.drawRoundRect(hitRect, dp(5), dp(5), paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1.2f));
                    paint.setColor(Color.argb(210, 255, 59, 48));
                    canvas.drawRoundRect(hitRect, dp(5), dp(5), paint);
                    paint.setStrokeWidth(dp(1f));
                    paint.setColor(Color.argb(170, 0, 122, 255));
                    canvas.drawRoundRect(touchRect, dp(5), dp(5), paint);
                    if (pressedKey != null && pressedKey.key == key) {
                        paint.setStrokeWidth(dp(3f));
                        paint.setColor(Color.argb(230, 52, 199, 89));
                        canvas.drawRoundRect(hitRect, dp(5), dp(5), paint);
                    }
                    String label = primaryKeyLabel(key);
                    if (!label.isEmpty()) {
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(Color.argb(225, 20, 20, 24));
                        paint.setTextSize(dp(9));
                        canvas.drawText(label, hitRect.left + dp(3), hitRect.top + dp(11), paint);
                    }
                }
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
    }

    private void drawDebugTouchMarks(Canvas canvas) {
        paint.setShader(null);
        paint.setStrokeWidth(dp(2f));
        for (int i = 0; i < debugTouchMarks.size(); i++) {
            DebugTouchMark mark = debugTouchMarks.get(i);
            float radius = mark.down ? dp(6f) : dp(8f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mark.down
                    ? Color.argb(150, 0, 122, 255)
                    : Color.argb(110, 255, 149, 0));
            canvas.drawCircle(mark.x, mark.y, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(mark.down
                    ? Color.argb(235, 0, 122, 255)
                    : Color.argb(235, 255, 149, 0));
            canvas.drawCircle(mark.x, mark.y, radius, paint);
            if (!mark.down) {
                canvas.drawLine(mark.x - radius, mark.y, mark.x + radius, mark.y, paint);
                canvas.drawLine(mark.x, mark.y - radius, mark.x, mark.y + radius, paint);
            }
        }
        if (debugTouchActive) {
            float radius = dp(12f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(80, 52, 199, 89));
            canvas.drawCircle(debugTouchX, debugTouchY, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3f));
            paint.setColor(Color.argb(240, 52, 199, 89));
            canvas.drawCircle(debugTouchX, debugTouchY, radius, paint);
        }
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
        drawShiftModeDot(canvas, key, rect);

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
        paint.setTextSize(dp(labelTextSizeDp(key)));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float centerY = rect.centerY();
        if (key.hintTop != null && !key.hintTop.isEmpty()) {
            centerY += dp(1);
        }
        float firstBaseline = centerY - lineHeight * (labelLines.length - 1) / 2f
                - (metrics.ascent + metrics.descent) / 2f;
        for (int i = 0; i < labelLines.length; i++) {
            canvas.drawText(labelLines[i], rect.centerX(), firstBaseline + lineHeight * i, paint);
        }
    }

    private void drawShiftModeDot(Canvas canvas, KeySpec key, RectF rect) {
        if (key.type != KeySpec.Type.SHIFT || (!shift && !capsLock)) {
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(capsLock ? Color.rgb(224, 57, 57) : Color.rgb(42, 176, 94));
        canvas.drawCircle(rect.right - dp(12), rect.top + dp(12), dp(4), paint);
    }

    private boolean isCompactLabel(String label) {
        String[] lines = label.split("\\n", -1);
        if (lines.length > 1) {
            return true;
        }
        return label.length() > 2;
    }

    private int labelTextSizeDp(KeySpec key) {
        if (isCompactLabel(key.label)) {
            return 12;
        }
        return key.type == KeySpec.Type.HANGUL_CONSONANT ? 22 : 24;
    }

    private void drawClipboardPanel(Canvas canvas) {
        SettingsStore.KeyboardTheme theme = settings.theme;
        float contentLeft = keyboardLeftInset();
        float contentWidth = keyboardContentWidth();
        RectF panel = new RectF(contentLeft + dp(10), dp(8),
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
        float top = gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight;
        float rowHeight = (rowAreaHeight - gap * (rows.size() + 1)) / rows.size();
        KeyBounds bestKey = null;
        float bestScore = Float.MAX_VALUE;
        int bestPriority = Integer.MAX_VALUE;
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
                if (key.type != KeySpec.Type.NO_OP) {
                    RectF hitRect = expandedKeyHitRect(touchRect, gap, key);
                    if (hitRect.contains(x, y)) {
                        float score = adjustedHitScore(x, y, hitRect, key);
                        int priority = hitPriority(key);
                        if (score < bestScore
                                || (Math.abs(score - bestScore) <= dp(3) * dp(3)
                                && priority < bestPriority)) {
                            bestScore = score;
                            bestPriority = priority;
                            bestKey = new KeyBounds(key, touchRect);
                        }
                    }
                }
                left += keyWidth + gap;
            }
            top += rowHeight + gap;
        }
        return bestKey;
    }

    private KeyBounds findConsonantKey(Consonant consonant) {
        if (consonant == null) {
            return null;
        }
        float gap = dp(4);
        float top = gap;
        float keyboardHeight = keyboardHeight();
        float rowAreaHeight = keyboardHeight;
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
                if (key.type == KeySpec.Type.HANGUL_CONSONANT && key.consonant == consonant) {
                    return new KeyBounds(key, spanRect(row, i, rect, rowHeight, gap, availableWidth, totalWeight));
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
                    true);
        }
        RectF grid = new RectF(keyboardLeftInset() + dp(10), dp(50),
                keyboardLeftInset() + keyboardContentWidth() - dp(10), keyboardHeight() - dp(8));
        layoutClipboardCards(grid);
        for (ClipboardCard card : clipboardCards) {
            if (card.rect.contains(x, y)) {
                return new KeyBounds(KeySpec.clipboardPaste("", card.clip.index, 1f), new RectF(card.rect),
                        true);
            }
        }
        return null;
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

    private RectF expandedKeyHitRect(RectF rect, float gap, KeySpec key) {
        float baseSlop = dp(hitboxBaseDp());
        float leftCut = Math.min(rect.width() * 0.18f, dp(hitboxLeftCutDp()));
        float topCut = Math.min(rect.height() * 0.18f, dp(hitboxTopCutDp()));
        float rightSlop = baseSlop + dp(hitboxRightExtraDp());
        float bottomSlop = baseSlop + dp(hitboxBottomExtraDp());
        if (isLeftTextKey(rect, key)) {
            rightSlop += dp(hitboxLeftKeyRightExtraDp());
        }
        float contentLeft = keyboardLeftInset();
        float contentRight = contentLeft + keyboardContentWidth();
        float keyboardTop = 0f;
        float keyboardBottom = keyboardHeight();
        RectF hitRect = new RectF(
                Math.max(contentLeft, rect.left + leftCut),
                Math.max(keyboardTop, rect.top + topCut),
                Math.min(contentRight, rect.right + rightSlop),
                Math.min(keyboardBottom, rect.bottom + bottomSlop));
        return hitRect;
    }

    private int hitboxBaseDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_BASE_DP : settings.hitboxBaseDp;
    }

    private int hitboxLeftCutDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_LEFT_CUT_DP : settings.hitboxLeftCutDp;
    }

    private int hitboxTopCutDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_TOP_CUT_DP : settings.hitboxTopCutDp;
    }

    private int hitboxRightExtraDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_RIGHT_EXTRA_DP : settings.hitboxRightExtraDp;
    }

    private int hitboxBottomExtraDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_BOTTOM_EXTRA_DP : settings.hitboxBottomExtraDp;
    }

    private int hitboxLeftKeyRightExtraDp() {
        return settings == null ? SettingsStore.DEFAULT_HITBOX_LEFT_KEY_RIGHT_EXTRA_DP
                : settings.hitboxLeftKeyRightExtraDp;
    }

    private float adjustedHitScore(float x, float y, RectF rect, KeySpec key) {
        float score = distanceToRectScore(x, y, rect);
        if (isLeftTextKey(rect, key)) {
            return score * LEFT_KEY_HIT_SCORE_SCALE;
        }
        if (isRightTextKey(rect, key)) {
            return score * RIGHT_KEY_HIT_SCORE_SCALE;
        }
        return score;
    }

    private float distanceToRectScore(float x, float y, RectF rect) {
        float dx = 0f;
        if (x < rect.left) {
            dx = rect.left - x;
        } else if (x > rect.right) {
            dx = x - rect.right;
        }
        float dy = 0f;
        if (y < rect.top) {
            dy = rect.top - y;
        } else if (y > rect.bottom) {
            dy = y - rect.bottom;
        }
        return dx * dx + dy * dy;
    }

    private boolean isLeftTextKey(RectF rect, KeySpec key) {
        return isTextInputKey(key) && rect.centerX() < keyboardTextCenterX();
    }

    private boolean isRightTextKey(RectF rect, KeySpec key) {
        return isTextInputKey(key) && rect.centerX() > keyboardTextCenterX();
    }

    private boolean isTextInputKey(KeySpec key) {
        return key.type == KeySpec.Type.HANGUL_CONSONANT
                || key.type == KeySpec.Type.HANGUL_VOWEL
                || key.type == KeySpec.Type.CHARACTER;
    }

    private float keyboardTextCenterX() {
        return keyboardLeftInset() + keyboardContentWidth() * 0.5f;
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
        if (useFoldSplitHangulLayout()) {
            buildGestureHangulFoldSplitRows(
                    row1First, row1Second, row1Third, row1Fourth, row1Fifth,
                    row2First, row2Second, row2Third, row2Fourth, row2Fifth,
                    row3First, row3Second, row3Third, row3Fourth);
            return;
        }
        float leftKeyWeight = hangulLeftKeyWeight();
        float rightKeyWeight = hangulRightKeyWeight();
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, leftKeyWeight),
                gestureConsonantKey(row1First, 0, 0, "1"),
                gestureConsonantKey(row1Second, 0, 1, "2"),
                gestureConsonantKey(row1Third, 0, 2, "3"),
                gestureConsonantKey(row1Fourth, 0, 3, "4"),
                gestureConsonantKey(row1Fifth, 0, 4, "5"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, rightKeyWeight).withRowSpan(3)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, leftKeyWeight),
                gestureConsonantKey(row2First, 1, 0, "6"),
                gestureConsonantKey(row2Second, 1, 1, "7"),
                gestureConsonantKey(row2Third, 1, 2, "8"),
                gestureConsonantKey(row2Fourth, 1, 3, "9"),
                gestureConsonantKey(row2Fifth, 1, 4, "0"),
                KeySpec.spacer(rightKeyWeight)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, leftKeyWeight),
                gestureConsonantKey(row3First, 2, 0, null),
                gestureConsonantKey(row3Second, 2, 1, null),
                gestureConsonantKey(row3Third, 2, 2, null),
                gestureConsonantKey(row3Fourth, 2, 3, null),
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

    private void buildGestureHangulFoldSplitRows(
            Consonant row1First, Consonant row1Second, Consonant row1Third, Consonant row1Fourth, Consonant row1Fifth,
            Consonant row2First, Consonant row2Second, Consonant row2Third, Consonant row2Fourth, Consonant row2Fifth,
            Consonant row3First, Consonant row3Second, Consonant row3Third, Consonant row3Fourth) {
        float leftKeyWeight = Math.max(0.9f, hangulLeftKeyWeight());
        float rightKeyWeight = Math.max(1.15f, hangulRightKeyWeight());
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, leftKeyWeight),
                gestureConsonantKey(row1First, 0, 0, "1"),
                gestureConsonantKey(row1Second, 0, 1, "2"),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                gestureConsonantKey(row1Third, 0, 2, "3"),
                gestureConsonantKey(row1Fourth, 0, 3, "4"),
                gestureConsonantKey(row1Fifth, 0, 4, "5"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, rightKeyWeight).withRowSpan(3)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, leftKeyWeight),
                gestureConsonantKey(row2First, 1, 0, "6"),
                gestureConsonantKey(row2Second, 1, 1, "7"),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                gestureConsonantKey(row2Third, 1, 2, "8"),
                gestureConsonantKey(row2Fourth, 1, 3, "9"),
                gestureConsonantKey(row2Fifth, 1, 4, "0"),
                KeySpec.spacer(rightKeyWeight)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, leftKeyWeight),
                gestureConsonantKey(row3First, 2, 0, null),
                gestureConsonantKey(row3Second, 2, 1, null),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                gestureConsonantKey(row3Third, 2, 2, null),
                gestureConsonantKey(row3Fourth, 2, 3, null),
                KeySpec.vowelPad("모음", 1.0f),
                KeySpec.spacer(rightKeyWeight)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, leftKeyWeight),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.command("!\n? ㅡ .", KeySpec.Type.SPACE).withHints("~   ,", null).withColumnSpan(2),
                KeySpec.spacer(1.0f),
                KeySpec.command("Go", KeySpec.Type.ENTER).withColumnSpan(2),
                KeySpec.spacer(rightKeyWeight)));
    }

    private boolean useFoldSplitHangulLayout() {
        return useFoldSplitKeyboardLayout();
    }

    private boolean useFoldSplitKeyboardLayout() {
        return settings != null
                && settings.foldSplitKeyboardEnabled()
                && getWidth() >= dp(600);
    }

    private KeySpec gestureConsonantKey(Consonant consonant, int row, int column) {
        return gestureConsonantKey(consonant, row, column, null);
    }

    private KeySpec gestureConsonantKey(Consonant consonant, int row, int column, String fallbackTopHint) {
        return withHangulConsonantTopHint(
                KeySpec.consonant(consonant).withCalibrationKey(gestureCalibrationKey(row, column)),
                consonant,
                fallbackTopHint);
    }

    private String gestureCalibrationKey(int row, int column) {
        return "hangul_gesture_r" + row + "_c" + column;
    }

    private KeySpec qwertyConsonantKey(Consonant consonant, int row, int column) {
        return withHangulConsonantTopHint(
                KeySpec.consonant(consonant).withCalibrationKey("hangul_qwerty_r" + row + "_c" + column),
                consonant,
                null);
    }

    private KeySpec withHangulConsonantTopHint(KeySpec key, Consonant consonant, String fallbackTopHint) {
        String topHint = hangulConsonantTopHint(consonant, fallbackTopHint);
        return key.withHints(topHint, null);
    }

    private String hangulConsonantTopHint(Consonant consonant, String fallbackTopHint) {
        if (consonant == Consonant.KIEUK) {
            return "@";
        }
        if (consonant == Consonant.TIEUT) {
            return "&";
        }
        if (consonant == Consonant.CHIEUT) {
            return "-";
        }
        if (consonant == Consonant.PIEUP) {
            return "_";
        }
        return fallbackTopHint;
    }

    private float hangulLeftKeyWeight() {
        return settings == null ? HANGUL_LEFT_KEY_WEIGHT : settings.hangulLeftColumnWeight(5f);
    }

    private float hangulRightKeyWeight() {
        return settings == null ? HANGUL_RIGHT_KEY_WEIGHT : settings.hangulRightColumnWeight(5f);
    }

    private void buildHangulQwertyVerticalRows() {
        rows.add(row(
                qwertyConsonantKey(shift ? Consonant.SSANG_BIEUP : Consonant.BIEUP, 0, 0),
                qwertyConsonantKey(shift ? Consonant.SSANG_JIEUT : Consonant.JIEUT, 0, 1),
                qwertyConsonantKey(shift ? Consonant.SSANG_DIGEUT : Consonant.DIGEUT, 0, 2),
                qwertyConsonantKey(shift ? Consonant.SSANG_GIYEOK : Consonant.GIYEOK, 0, 3),
                qwertyConsonantKey(shift ? Consonant.SSANG_SIOT : Consonant.SIOT, 0, 4)));
        rows.add(row(
                KeySpec.vowel("ㅛ", HangulComposer.V_YO),
                KeySpec.vowel("ㅕ", HangulComposer.V_YEO),
                KeySpec.vowel("ㅑ", HangulComposer.V_YA),
                KeySpec.vowel("ㅐ", HangulComposer.V_AE),
                KeySpec.vowel("ㅔ", HangulComposer.V_E)));
        rows.add(row(
                qwertyConsonantKey(Consonant.MIEUM, 2, 0),
                qwertyConsonantKey(Consonant.NIEUN, 2, 1),
                qwertyConsonantKey(Consonant.IEUNG, 2, 2),
                qwertyConsonantKey(Consonant.RIEUL, 2, 3),
                qwertyConsonantKey(Consonant.HIEUT, 2, 4)));
        rows.add(row(
                KeySpec.vowel("ㅗ", HangulComposer.V_O),
                KeySpec.vowel("ㅓ", HangulComposer.V_EO),
                KeySpec.vowel("ㅏ", HangulComposer.V_A),
                KeySpec.vowel("ㅣ", HangulComposer.V_I),
                KeySpec.command("⌫", KeySpec.Type.DELETE)));
        rows.add(row(
                KeySpec.command(shift ? "SHIFT" : "shift", KeySpec.Type.SHIFT, 1.4f),
                qwertyConsonantKey(Consonant.KIEUK, 4, 1),
                qwertyConsonantKey(Consonant.TIEUT, 4, 2),
                qwertyConsonantKey(Consonant.CHIEUT, 4, 3),
                qwertyConsonantKey(Consonant.PIEUP, 4, 4),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, 1.1f),
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, 1.1f),
                KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS, 1.1f),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, 1.1f),
                KeySpec.command("space", KeySpec.Type.SPACE, 3.6f),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.1f)));
    }

    private void buildHangulQwertyRows() {
        rows.add(row(
                qwertyConsonantKey(Consonant.BIEUP, 0, 0),
                qwertyConsonantKey(Consonant.JIEUT, 0, 1),
                qwertyConsonantKey(Consonant.DIGEUT, 0, 2),
                qwertyConsonantKey(Consonant.GIYEOK, 0, 3),
                qwertyConsonantKey(Consonant.SIOT, 0, 4),
                KeySpec.vowel("ㅛ", HangulComposer.V_YO),
                KeySpec.vowel("ㅕ", HangulComposer.V_YEO),
                KeySpec.vowel("ㅑ", HangulComposer.V_YA),
                KeySpec.vowel("ㅐ", HangulComposer.V_AE),
                KeySpec.vowel("ㅔ", HangulComposer.V_E)));
        rows.add(row(
                qwertyConsonantKey(Consonant.MIEUM, 1, 0),
                qwertyConsonantKey(Consonant.NIEUN, 1, 1),
                qwertyConsonantKey(Consonant.IEUNG, 1, 2),
                qwertyConsonantKey(Consonant.RIEUL, 1, 3),
                qwertyConsonantKey(Consonant.HIEUT, 1, 4),
                KeySpec.vowel("ㅗ", HangulComposer.V_O),
                KeySpec.vowel("ㅓ", HangulComposer.V_EO),
                KeySpec.vowel("ㅏ", HangulComposer.V_A),
                KeySpec.vowel("ㅣ", HangulComposer.V_I)));
        rows.add(row(
                qwertyConsonantKey(Consonant.KIEUK, 2, 0),
                qwertyConsonantKey(Consonant.TIEUT, 2, 1),
                qwertyConsonantKey(Consonant.CHIEUT, 2, 2),
                qwertyConsonantKey(Consonant.PIEUP, 2, 3),
                KeySpec.vowel("ㅠ", HangulComposer.V_YU),
                KeySpec.vowel("ㅜ", HangulComposer.V_U),
                KeySpec.vowel("ㅡ", HangulComposer.V_EU),
                KeySpec.character(".,", "."),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(
                KeySpec.command("⚙", KeySpec.Type.SETTINGS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("ABC", KeySpec.Type.MODE_ENGLISH, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("space", KeySpec.Type.SPACE, 4.0f),
                KeySpec.command("←", KeySpec.Type.MOVE_LEFT),
                KeySpec.command("→", KeySpec.Type.MOVE_RIGHT),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private void buildEnglishRows() {
        buildEnglishQwertyRows();
    }

    private void buildEnglishQwertyRows() {
        if (useFoldSplitKeyboardLayout()) {
            buildEnglishQwertyFoldSplitRows();
            return;
        }
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
                KeySpec.command(isUppercaseMode() ? "SHIFT" : "shift", KeySpec.Type.SHIFT, 1.5f),
                qwertyKey("z", "<", "ㅋ"),
                qwertyKey("x", ">", "ㅌ"),
                qwertyKey("c", "[", "ㅊ"),
                qwertyKey("v", "]", "ㅍ"),
                qwertyKey("b", "{", "ㅠ"),
                qwertyKey("n", "}", "ㅜ"),
                qwertyKey("m", "\\", "ㅡ"),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE, 1.5f)));
        rows.add(row(
                KeySpec.command("ㄱㄴㄷ", KeySpec.Type.MODE_HANGUL, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE, 3.55f).withHints("~   ,", null),
                KeySpec.command("↵", KeySpec.Type.ENTER, 3.0f)));
    }

    private void buildEnglishQwertyFoldSplitRows() {
        rows.add(row(
                KeySpec.command("\uD55C\uAE00", KeySpec.Type.MODE_HANGUL, HANGUL_LEFT_KEY_WEIGHT),
                qwertyKey("q", "1", "\u3142"),
                qwertyKey("w", "2", "\u3148"),
                qwertyKey("e", "3", "\u3137"),
                qwertyKey("r", "4", "\u3131"),
                qwertyKey("t", "5", "\u3145"),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                qwertyKey("y", "6", "\u315B"),
                qwertyKey("u", "7", "\u3155"),
                qwertyKey("i", "8", "\u3151"),
                qwertyKey("o", "9", "\u3150"),
                qwertyKey("p", "0", "\u3154"),
                KeySpec.spacer(0.6f)));
        rows.add(row(
                KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS, HANGUL_LEFT_KEY_WEIGHT),
                qwertyKey("a", "`", "\u3141"),
                qwertyKey("s", "#", "\u3134"),
                qwertyKey("d", "$", "\u3147"),
                qwertyKey("f", "%", "\u3139"),
                qwertyKey("g", "(", "\u314E"),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                qwertyKey("h", ")", "\u3157"),
                qwertyKey("j", "'", "\u3153"),
                qwertyKey("k", "\"", "\u314F"),
                qwertyKey("l", "|", "\u3163"),
                KeySpec.spacer(1.6f)));
        rows.add(row(
                KeySpec.command("123", KeySpec.Type.MODE_NUMBERS, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command(isUppercaseMode() ? "SHIFT" : "shift", KeySpec.Type.SHIFT, HANGUL_LEFT_KEY_WEIGHT),
                qwertyKey("z", "<", "\u314B"),
                qwertyKey("x", ">", "\u314C"),
                qwertyKey("c", "[", "\u314A"),
                qwertyKey("v", "]", "\u314D"),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                qwertyKey("b", "{", "\u3160"),
                qwertyKey("n", "}", "\u315C"),
                qwertyKey("m", "\\", "\u3161"),
                KeySpec.spacer(2.5f)));
        rows.add(row(
                KeySpec.command("<", KeySpec.Type.MOVE_LEFT, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command(">", KeySpec.Type.MOVE_RIGHT, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.spacer(3.9f),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.command("space", KeySpec.Type.SPACE, 2.4f).withHints("~   ,", null),
                KeySpec.command("DEL\n\uC0AD\uC81C", KeySpec.Type.DELETE, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.command("Go", KeySpec.Type.ENTER, HANGUL_LEFT_KEY_WEIGHT),
                KeySpec.spacer(1.0f)));
    }

    private KeySpec qwertyKey(String value, String topHint, String bottomHint) {
        String text = isUppercaseMode() ? value.toUpperCase() : value;
        return KeySpec.character(text, text).withHints(topHint, bottomHint);
    }

    private void buildEnglishPalgeulRows() {
        rows.add(row(
                KeySpec.command("한글", KeySpec.Type.MODE_HANGUL, 1.1f),
                letter("a"), letter("f"), letter("k"), letter("p"), letter("u"),
                KeySpec.command("⌫", KeySpec.Type.DELETE, 1.1f)));
        rows.add(row(letter("b"), letter("g"), letter("l"), letter("q"), letter("v"), KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS)));
        rows.add(row(letter("c"), letter("h"), letter("m"), letter("r"), letter("w"),
                KeySpec.command(isUppercaseMode() ? "SHIFT" : "shift", KeySpec.Type.SHIFT)));
        rows.add(row(letter("d"), letter("i"), letter("n"), letter("s"), letter("x"), KeySpec.command("123", KeySpec.Type.MODE_NUMBERS)));
        rows.add(row(letter("e"), letter("j"), letter("o"), letter("t"), letter("y"), letter("z"),
                KeySpec.command("space", KeySpec.Type.SPACE, 2.2f),
                KeySpec.command("↵", KeySpec.Type.ENTER, 1.2f)));
    }

    private KeySpec letter(String value) {
        String text = isUppercaseMode() ? value.toUpperCase() : value;
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
        if (useFoldSplitKeyboardLayout()) {
            buildFoldSplitNumberRows();
            return;
        }
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
                KeySpec.character("7", "7").withHints("!", null),
                KeySpec.character("8", "8").withHints("@", null),
                KeySpec.character("9", "9").withHints("?", null),
                KeySpec.command("DEL\n←", KeySpec.Type.DELETE)));
        rows.add(row(
                KeySpec.command("#★♪", KeySpec.Type.MODE_SYMBOLS, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.command("!\n? ＿ .", KeySpec.Type.SPACE).withHints("~   ,", null),
                KeySpec.character("0", "0"),
                KeySpec.character(".", "."),
                KeySpec.command("↵", KeySpec.Type.ENTER)));
    }

    private void buildFoldSplitNumberRows() {
        rows.add(row(
                KeySpec.command("\uD55C\uAE00", KeySpec.Type.MODE_HANGUL, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.character("*", "*"),
                KeySpec.character("/", "/"),
                KeySpec.character("=", "="),
                KeySpec.spacer(0.2f),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.character("1", "1"),
                KeySpec.character("2", "2"),
                KeySpec.character("3", "3"),
                KeySpec.command("DEL\n\uC0AD\uC81C", KeySpec.Type.DELETE, NUMBER_SIDE_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("Abc", KeySpec.Type.MODE_ENGLISH, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.character("%", "%"),
                KeySpec.character("'", "'"),
                KeySpec.character("#", "#"),
                KeySpec.spacer(0.2f),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.character("4", "4"),
                KeySpec.character("5", "5"),
                KeySpec.character("6", "6"),
                KeySpec.command("Go", KeySpec.Type.ENTER, NUMBER_SIDE_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.command("#+=", KeySpec.Type.MODE_SYMBOLS, NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.character("!", "!"),
                KeySpec.character("@", "@"),
                KeySpec.character("?", "?"),
                KeySpec.spacer(0.2f),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.character("7", "7").withHints("!", null),
                KeySpec.character("8", "8").withHints("@", null),
                KeySpec.character("9", "9"),
                KeySpec.character("+", "+", NUMBER_SIDE_KEY_WEIGHT)));
        rows.add(row(
                KeySpec.spacer(NUMBER_SIDE_KEY_WEIGHT),
                KeySpec.command("space", KeySpec.Type.SPACE, 2.0f).withHints("~   ,", null),
                KeySpec.character(".", "."),
                KeySpec.spacer(0.2f),
                KeySpec.spacer(FOLD_SPLIT_CENTER_GAP_WEIGHT),
                KeySpec.spacer(1.0f),
                KeySpec.character("0", "0"),
                KeySpec.spacer(1.0f),
                KeySpec.character("-", "-", NUMBER_SIDE_KEY_WEIGHT)));
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
        void onConsonantTouch(ConsonantTouch touch);

        void onGestureTrace(GestureTrace trace);
    }

    public static class ConsonantTouch {
        public final Consonant actualConsonant;
        public final RectF actualKeyRect;
        public final float touchX;
        public final float touchY;

        ConsonantTouch(Consonant actualConsonant, RectF actualKeyRect, float touchX, float touchY) {
            this.actualConsonant = actualConsonant;
            this.actualKeyRect = actualKeyRect;
            this.touchX = touchX;
            this.touchY = touchY;
        }
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
        final boolean clipboard;

        KeyBounds(KeySpec key, RectF rect) {
            this(key, rect, false);
        }

        KeyBounds(KeySpec key, RectF rect, boolean clipboard) {
            this.key = key;
            this.rect = rect;
            this.clipboard = clipboard;
        }
    }

    private static class DebugTouchMark {
        final float x;
        final float y;
        final boolean down;

        DebugTouchMark(float x, float y, boolean down) {
            this.x = x;
            this.y = y;
            this.down = down;
        }
    }

    private static class DeferredPointerTap {
        final int pointerId;
        final KeyBounds keyBounds;
        final boolean handledOnTouchDown;
        boolean released;

        DeferredPointerTap(int pointerId, KeyBounds keyBounds, boolean handledOnTouchDown) {
            this.pointerId = pointerId;
            this.keyBounds = keyBounds;
            this.handledOnTouchDown = handledOnTouchDown;
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
