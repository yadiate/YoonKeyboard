package com.yadiate.yoonkeyboard.ime;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;

import com.yadiate.yoonkeyboard.MainActivity;
import com.yadiate.yoonkeyboard.SettingsStore;
import com.yadiate.yoonkeyboard.hangul.Consonant;
import com.yadiate.yoonkeyboard.hangul.GestureCalibration;
import com.yadiate.yoonkeyboard.hangul.HangulComposer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EightWayInputMethodService extends InputMethodService
        implements KeyboardActionListener, KeyboardSurfaceView.GestureTraceListener {
    private final HangulComposer composer = new HangulComposer();
    private KeyboardSurfaceView keyboardView;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private SettingsStore.Snapshot settings;
    private String currentEditorPackage = "";
    private boolean currentEditorIsPassword;
    private int currentEditorImeOptions = EditorInfo.IME_ACTION_UNSPECIFIED;
    private int currentEditorActionId;
    private Consonant lastConsonantTap;
    private long lastConsonantTapTimeMs;

    @Override
    public View onCreateInputView() {
        settings = SettingsStore.load(this);
        keyboardView = new KeyboardSurfaceView(this);
        keyboardView.setListener(this);
        keyboardView.setGestureTraceListener(this);
        keyboardView.setSettings(settings);
        keyboardView.setMode(mode);
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentEditorPackage = attribute == null || attribute.packageName == null ? "" : attribute.packageName;
        currentEditorIsPassword = isPasswordInput(attribute);
        currentEditorImeOptions = attribute == null
                ? EditorInfo.IME_ACTION_UNSPECIFIED
                : attribute.imeOptions;
        currentEditorActionId = attribute == null ? 0 : attribute.actionId;
        settings = SettingsStore.load(this);
        if (keyboardView != null) {
            keyboardView.setSettings(settings);
        }
        composer.reset();
        resetDoubleConsonantTapState();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        settings = SettingsStore.load(this);
        if (keyboardView != null) {
            keyboardView.setSettings(settings);
        }
    }

    @Override
    public void onFinishInput() {
        commitComposingText();
        resetDoubleConsonantTapState();
        currentEditorPackage = "";
        currentEditorIsPassword = false;
        currentEditorImeOptions = EditorInfo.IME_ACTION_UNSPECIFIED;
        currentEditorActionId = 0;
        super.onFinishInput();
    }

    @Override
    public void onKey(KeySpec key) {
        if (key.type == KeySpec.Type.NO_OP || key.type == KeySpec.Type.HANGUL_VOWEL_PAD) {
            return;
        }
        playKeySound();
        vibrate();
        switch (key.type) {
            case HANGUL_CONSONANT:
                handleHangulConsonantTap(key.consonant);
                break;
            case HANGUL_VOWEL:
                resetDoubleConsonantTapState();
                commitTextIfNeeded(composer.inputVowel(key.vowelIndex));
                refreshComposingText();
                break;
            case CHARACTER:
                resetDoubleConsonantTapState();
                commitComposingText();
                commitText(key.outputText);
                if (mode == KeyboardMode.ENGLISH && keyboardView != null && keyboardView.isShift()) {
                    keyboardView.setShift(false);
                }
                break;
            case MODE_HANGUL:
                resetDoubleConsonantTapState();
                switchMode(KeyboardMode.HANGUL);
                break;
            case MODE_ENGLISH:
                resetDoubleConsonantTapState();
                switchMode(KeyboardMode.ENGLISH);
                break;
            case MODE_SYMBOLS:
                resetDoubleConsonantTapState();
                switchMode(KeyboardMode.SYMBOLS);
                break;
            case MODE_NUMBERS:
                resetDoubleConsonantTapState();
                switchMode(KeyboardMode.NUMBERS);
                break;
            case SYMBOL_PAGE_PREV:
                resetDoubleConsonantTapState();
                if (keyboardView != null) {
                    keyboardView.showPreviousSymbolPage();
                }
                break;
            case SYMBOL_PAGE_NEXT:
                resetDoubleConsonantTapState();
                if (keyboardView != null) {
                    keyboardView.showNextSymbolPage();
                }
                break;
            case SHIFT:
                resetDoubleConsonantTapState();
                if (keyboardView != null) {
                    keyboardView.setShift(!keyboardView.isShift());
                }
                break;
            case DELETE:
                resetDoubleConsonantTapState();
                handleBackspace();
                break;
            case SPACE:
                resetDoubleConsonantTapState();
                commitComposingText();
                commitText(" ");
                break;
            case ENTER:
                resetDoubleConsonantTapState();
                commitComposingText();
                sendEnter();
                break;
            case HIDE_KEYBOARD:
                resetDoubleConsonantTapState();
                commitComposingText();
                requestHideSelf(0);
                break;
            case SETTINGS:
                resetDoubleConsonantTapState();
                openSettings();
                break;
            case USEFUL_SENTENCE:
                resetDoubleConsonantTapState();
                commitStoredText(SettingsStore.firstNonEmptySentence(this));
                break;
            case MY_INFO:
                resetDoubleConsonantTapState();
                commitStoredText(SettingsStore.firstNonEmptyMyInfo(this));
                break;
            case CLIPBOARD_CONTEXT:
                resetDoubleConsonantTapState();
                showClipboardContext();
                break;
            case CLIPBOARD_PASTE:
                resetDoubleConsonantTapState();
                pasteClipboardItem(key.clipboardIndex);
                if (keyboardView != null) {
                    keyboardView.hideClipboardContext();
                }
                break;
            case CLIPBOARD_CLOSE:
                resetDoubleConsonantTapState();
                if (keyboardView != null) {
                    keyboardView.hideClipboardContext();
                }
                break;
            case MOVE_LEFT:
                resetDoubleConsonantTapState();
                moveCursor(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case MOVE_RIGHT:
                resetDoubleConsonantTapState();
                moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case NO_OP:
            case HANGUL_VOWEL_PAD:
                break;
        }
    }

    @Override
    public void onGesture(KeySpec key, Integer vowelIndex, boolean playCommitHaptic) {
        playKeySound();
        if (playCommitHaptic) {
            vibrate();
        }
        if (key.type == KeySpec.Type.HANGUL_CONSONANT) {
            boolean replacedWithDouble = replaceRecentConsonantWithDouble(key.consonant, SystemClock.uptimeMillis());
            if (!replacedWithDouble) {
                commitTextIfNeeded(composer.inputConsonant(key.consonant));
            }
        }
        resetDoubleConsonantTapState();
        commitTextIfNeeded(composer.inputVowel(vowelIndex));
        refreshComposingText();
    }

    @Override
    public void onGesturePreviewChanged(String previewText) {
        vibrate();
    }

    @Override
    public void onConsonantTouch(KeyboardSurfaceView.ConsonantTouch touch) {
        if (!shouldCollectCalibrationTouch(touch)) {
            return;
        }
        Consonant expectedConsonant = expectedCalibrationConsonant();
        if (expectedConsonant == null || keyboardView == null) {
            return;
        }
        RectF expectedRect = keyboardView.currentConsonantKeyRect(expectedConsonant);
        if (expectedRect == null || expectedRect.width() <= 1f || expectedRect.height() <= 1f) {
            return;
        }
        float xRatio = (touch.touchX - expectedRect.left) / expectedRect.width();
        float yRatio = (touch.touchY - expectedRect.top) / expectedRect.height();
        SettingsStore.appendGestureCalibrationSample(this,
                new GestureCalibration.Sample(expectedConsonant, touch.actualConsonant, xRatio, yRatio));
    }

    @Override
    public void onGestureTrace(KeyboardSurfaceView.GestureTrace trace) {
        if (!shouldCollectCalibrationTrace(trace)) {
            return;
        }
        Consonant expectedConsonant = expectedCalibrationConsonant();
        if (expectedConsonant == null || expectedConsonant != trace.key.consonant) {
            return;
        }
        char expectedChar = expectedCalibrationChar();
        if (!GestureCalibration.isHangulSyllable(expectedChar)) {
            return;
        }
        int expectedVowel = GestureCalibration.vowelIndex(expectedChar);
        GestureCalibration.DirectionClass directionClass = GestureCalibration.classForVowel(expectedVowel);
        if (directionClass == null) {
            return;
        }
        SettingsStore.appendGestureCalibrationSample(this,
                new GestureCalibration.Sample(trace.key.consonant, expectedVowel, directionClass, trace.motion));
    }

    private boolean shouldCollectCalibrationTouch(KeyboardSurfaceView.ConsonantTouch touch) {
        return touch != null
                && touch.actualConsonant != null
                && getPackageName().equals(currentEditorPackage)
                && SettingsStore.isGestureCalibrationSessionActive(this);
    }

    private boolean shouldCollectCalibrationTrace(KeyboardSurfaceView.GestureTrace trace) {
        return trace != null
                && trace.key != null
                && trace.key.type == KeySpec.Type.HANGUL_CONSONANT
                && trace.key.consonant != null
                && getPackageName().equals(currentEditorPackage)
                && SettingsStore.isGestureCalibrationSessionActive(this);
    }

    private Consonant expectedCalibrationConsonant() {
        char expectedChar = expectedCalibrationChar();
        if (!GestureCalibration.isHangulSyllable(expectedChar)) {
            return null;
        }
        return Consonant.fromLeadingIndex(GestureCalibration.leadingIndex(expectedChar));
    }

    private char expectedCalibrationChar() {
        String target = SettingsStore.gestureCalibrationSessionTarget(this);
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || target == null || target.isEmpty()) {
            return 0;
        }
        CharSequence beforeCursor = inputConnection.getTextBeforeCursor(256, 0);
        int index = beforeCursor == null ? 0 : beforeCursor.length();
        if (index < 0 || index >= target.length()) {
            return 0;
        }
        return target.charAt(index);
    }

    private void switchMode(KeyboardMode nextMode) {
        resetDoubleConsonantTapState();
        commitComposingText();
        mode = nextMode;
        if (keyboardView != null) {
            keyboardView.setMode(nextMode);
        }
    }

    private void handleHangulConsonantTap(Consonant consonant) {
        long now = SystemClock.uptimeMillis();
        if (replaceRecentConsonantWithDouble(consonant, now)) {
            resetDoubleConsonantTapState();
            refreshComposingText();
            return;
        }
        commitTextIfNeeded(composer.inputConsonant(consonant));
        refreshComposingText();
        lastConsonantTap = consonant;
        lastConsonantTapTimeMs = now;
    }

    private boolean replaceRecentConsonantWithDouble(Consonant consonant, long now) {
        Consonant replacement = consonant == null ? null : consonant.doubleTapVariant();
        if (replacement == null
                || lastConsonantTap != consonant
                || now - lastConsonantTapTimeMs > doubleConsonantTimeoutMs()) {
            return false;
        }
        if (composer.replaceSingleConsonant(consonant, replacement)) {
            return true;
        }
        String committed = composer.promoteCombinedFinalToDoubleInitial(consonant, replacement);
        if (committed == null) {
            return false;
        }
        commitTextIfNeeded(committed);
        return true;
    }

    private int doubleConsonantTimeoutMs() {
        return settings == null ? 380 : settings.doubleConsonantTimeoutMs();
    }

    private void resetDoubleConsonantTapState() {
        lastConsonantTap = null;
        lastConsonantTapTimeMs = 0L;
    }

    private void handleBackspace() {
        if (composer.backspace()) {
            refreshComposingTextAfterBackspace();
            return;
        }
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.deleteSurroundingText(1, 0);
        }
    }

    private void refreshComposingText() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) {
            return;
        }
        if (composer.hasComposingText()) {
            inputConnection.setComposingText(composer.getComposingText(), 1);
        } else {
            inputConnection.finishComposingText();
        }
    }

    private void refreshComposingTextAfterBackspace() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) {
            return;
        }
        if (composer.hasComposingText()) {
            inputConnection.setComposingText(composer.getComposingText(), 1);
            return;
        }
        inputConnection.setComposingText("", 1);
        inputConnection.finishComposingText();
    }

    private void commitComposingText() {
        commitTextIfNeeded(composer.commit());
    }

    private void commitTextIfNeeded(String text) {
        if (text != null && !text.isEmpty()) {
            commitText(text);
        }
    }

    private void commitText(String text) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.commitText(text, 1);
        }
    }

    private void sendEnter() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) {
            return;
        }
        if (currentEditorActionId != 0 && inputConnection.performEditorAction(currentEditorActionId)) {
            return;
        }
        int imeAction = currentEditorImeOptions & EditorInfo.IME_MASK_ACTION;
        if (imeAction != EditorInfo.IME_ACTION_NONE
                && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED
                && inputConnection.performEditorAction(imeAction)) {
            return;
        }
        sendKey(KeyEvent.KEYCODE_ENTER);
    }

    private void moveCursor(int keyCode) {
        commitComposingText();
        sendKey(keyCode);
    }

    private void sendKey(int keyCode) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void commitStoredText(String text) {
        if (text == null || text.trim().isEmpty()) {
            openSettings();
            return;
        }
        commitComposingText();
        commitText(text);
    }

    private void showClipboardContext() {
        if (keyboardView == null) {
            return;
        }
        if (currentEditorIsPassword) {
            keyboardView.showClipboardContext(new ArrayList<>(), "보안 입력란에서는 클립보드 미리보기를 숨깁니다.");
            return;
        }
        keyboardView.showClipboardContext(clipboardClips(), "클립보드가 비어 있습니다.");
    }

    private void pasteClipboardItem(int clipboardIndex) {
        commitComposingText();
        ClipData clipData = currentClipData();
        if (clipData == null || clipData.getItemCount() == 0) {
            return;
        }
        int itemIndex = clipboardIndex >= 0 && clipboardIndex < clipData.getItemCount() ? clipboardIndex : 0;
        ClipData.Item item = clipData.getItemAt(itemIndex);
        if (tryCommitContent(clipData, item)) {
            return;
        }
        CharSequence text = item.getText();
        if (text == null) {
            text = item.coerceToText(this);
        }
        commitTextIfNeeded(text == null ? "" : text.toString());
    }

    private boolean pasteFromClipboardAction() {
        InputConnection inputConnection = getCurrentInputConnection();
        return inputConnection != null && inputConnection.performContextMenuAction(android.R.id.paste);
    }

    private ClipData currentClipData() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return null;
        }
        return clipboardManager.getPrimaryClip();
    }

    private List<KeyboardSurfaceView.ClipboardClip> clipboardClips() {
        List<KeyboardSurfaceView.ClipboardClip> clips = new ArrayList<>();
        ClipData clipData = currentClipData();
        if (clipData == null) {
            return clips;
        }
        int count = Math.min(clipData.getItemCount(), 12);
        for (int i = 0; i < count; i++) {
            ClipData.Item item = clipData.getItemAt(i);
            boolean image = isImageItem(clipData, item);
            Bitmap thumbnail = image && item.getUri() != null ? loadThumbnail(item.getUri()) : null;
            String text = displayTextForItem(item, image);
            String typeLabel = image ? "이미지" : (item.getUri() != null ? "파일" : "텍스트");
            clips.add(new KeyboardSurfaceView.ClipboardClip(i, text, typeLabel, thumbnail, image));
        }
        return clips;
    }

    private String displayTextForItem(ClipData.Item item, boolean image) {
        CharSequence text = item.getText();
        if (text != null && text.length() > 0) {
            return text.toString();
        }
        if (item.getHtmlText() != null && !item.getHtmlText().isEmpty()) {
            return item.getHtmlText().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
        Uri uri = item.getUri();
        if (image) {
            return "이미지";
        }
        return uri == null ? "" : uri.toString();
    }

    private boolean tryCommitContent(ClipData clipData, ClipData.Item item) {
        Uri uri = item.getUri();
        if (uri == null || !isImageItem(clipData, item)) {
            return false;
        }
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            String[] mimeTypes = imageMimeTypes(clipData, uri);
            ClipDescription description = new ClipDescription("image", mimeTypes);
            InputContentInfo contentInfo = new InputContentInfo(uri, description, null);
            Bundle opts = new Bundle();
            if (inputConnection.commitContent(contentInfo,
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, opts)) {
                return true;
            }
        }
        return pasteFromClipboardAction();
    }

    private boolean isImageItem(ClipData clipData, ClipData.Item item) {
        ClipDescription description = clipData.getDescription();
        if (description != null) {
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                String mimeType = description.getMimeType(i);
                if (mimeType != null && mimeType.startsWith("image/")) {
                    return true;
                }
            }
        }
        Uri uri = item.getUri();
        if (uri == null) {
            return false;
        }
        try {
            String type = getContentResolver().getType(uri);
            return type != null && type.startsWith("image/");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String[] imageMimeTypes(ClipData clipData, Uri uri) {
        List<String> mimeTypes = new ArrayList<>();
        ClipDescription description = clipData.getDescription();
        if (description != null) {
            for (int i = 0; i < description.getMimeTypeCount(); i++) {
                String mimeType = description.getMimeType(i);
                if (mimeType != null && mimeType.startsWith("image/")) {
                    mimeTypes.add(mimeType);
                }
            }
        }
        try {
            String type = getContentResolver().getType(uri);
            if (type != null && type.startsWith("image/") && !mimeTypes.contains(type)) {
                mimeTypes.add(type);
            }
        } catch (Exception ignored) {
        }
        if (mimeTypes.isEmpty()) {
            mimeTypes.add("image/*");
        }
        return mimeTypes.toArray(new String[0]);
    }

    private Bitmap loadThumbnail(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds, 320, 220);
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private int sampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        int height = options.outHeight;
        int width = options.outWidth;
        while (height / inSampleSize > reqHeight * 2 || width / inSampleSize > reqWidth * 2) {
            inSampleSize *= 2;
        }
        return Math.max(1, inSampleSize);
    }

    private boolean isPasswordInput(EditorInfo attribute) {
        if (attribute == null) {
            return false;
        }
        int inputType = attribute.inputType;
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private void playKeySound() {
        if (settings == null || !settings.soundOn) {
            return;
        }
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f);
        }
    }

    private void vibrate() {
        if (settings == null || !settings.vibrateOn) {
            return;
        }
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        int duration = settings.vibrateDurationMs();
        if (duration <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(duration);
        }
    }
}
