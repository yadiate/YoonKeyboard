package com.example.eightwayime.ime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.example.eightwayime.MainActivity;
import com.example.eightwayime.SettingsStore;
import com.example.eightwayime.hangul.GestureCalibration;
import com.example.eightwayime.hangul.HangulComposer;

public class EightWayInputMethodService extends InputMethodService
        implements KeyboardActionListener, KeyboardSurfaceView.GestureTraceListener {
    private final HangulComposer composer = new HangulComposer();
    private KeyboardSurfaceView keyboardView;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private SettingsStore.Snapshot settings;
    private String currentEditorPackage = "";
    private boolean currentEditorIsPassword;

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
        settings = SettingsStore.load(this);
        if (keyboardView != null) {
            keyboardView.setSettings(settings);
        }
        composer.reset();
    }

    @Override
    public void onFinishInput() {
        commitComposingText();
        currentEditorPackage = "";
        currentEditorIsPassword = false;
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
                commitTextIfNeeded(composer.inputConsonant(key.consonant));
                refreshComposingText();
                break;
            case HANGUL_VOWEL:
                commitTextIfNeeded(composer.inputVowel(key.vowelIndex));
                refreshComposingText();
                break;
            case CHARACTER:
                commitComposingText();
                commitText(key.outputText);
                if (mode == KeyboardMode.ENGLISH && keyboardView != null && keyboardView.isShift()) {
                    keyboardView.setShift(false);
                }
                break;
            case MODE_HANGUL:
                switchMode(KeyboardMode.HANGUL);
                break;
            case MODE_ENGLISH:
                switchMode(KeyboardMode.ENGLISH);
                break;
            case MODE_SYMBOLS:
                switchMode(KeyboardMode.SYMBOLS);
                break;
            case MODE_NUMBERS:
                switchMode(KeyboardMode.NUMBERS);
                break;
            case SYMBOL_PAGE_PREV:
                if (keyboardView != null) {
                    keyboardView.showPreviousSymbolPage();
                }
                break;
            case SYMBOL_PAGE_NEXT:
                if (keyboardView != null) {
                    keyboardView.showNextSymbolPage();
                }
                break;
            case SHIFT:
                if (keyboardView != null) {
                    keyboardView.setShift(!keyboardView.isShift());
                }
                break;
            case DELETE:
                handleBackspace();
                break;
            case SPACE:
                commitComposingText();
                commitText(" ");
                break;
            case ENTER:
                commitComposingText();
                sendEnter();
                break;
            case HIDE_KEYBOARD:
                commitComposingText();
                requestHideSelf(0);
                break;
            case SETTINGS:
                openSettings();
                break;
            case USEFUL_SENTENCE:
                commitStoredText(SettingsStore.firstNonEmptySentence(this));
                break;
            case MY_INFO:
                commitStoredText(SettingsStore.firstNonEmptyMyInfo(this));
                break;
            case CLIPBOARD_CONTEXT:
                showClipboardContext();
                break;
            case CLIPBOARD_PASTE:
                pasteFromClipboard();
                if (keyboardView != null) {
                    keyboardView.hideClipboardContext();
                }
                break;
            case CLIPBOARD_CLOSE:
                if (keyboardView != null) {
                    keyboardView.hideClipboardContext();
                }
                break;
            case MOVE_LEFT:
                moveCursor(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case MOVE_RIGHT:
                moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
            case NO_OP:
            case HANGUL_VOWEL_PAD:
                break;
        }
    }

    @Override
    public void onGesture(KeySpec key, Integer vowelIndex) {
        playKeySound();
        vibrate();
        if (key.type == KeySpec.Type.HANGUL_CONSONANT) {
            commitTextIfNeeded(composer.inputConsonant(key.consonant));
        }
        commitTextIfNeeded(composer.inputVowel(vowelIndex));
        refreshComposingText();
    }

    @Override
    public void onGestureTrace(KeyboardSurfaceView.GestureTrace trace) {
        if (!shouldCollectCalibrationTrace(trace)) {
            return;
        }
        String target = SettingsStore.gestureCalibrationSessionTarget(this);
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || target == null || target.isEmpty()) {
            return;
        }
        CharSequence beforeCursor = inputConnection.getTextBeforeCursor(256, 0);
        int index = beforeCursor == null ? 0 : beforeCursor.length();
        if (index < 0 || index >= target.length()) {
            return;
        }
        char expectedChar = target.charAt(index);
        if (!GestureCalibration.isHangulSyllable(expectedChar)) {
            return;
        }
        if (GestureCalibration.leadingIndex(expectedChar) != trace.key.consonant.leadingIndex()) {
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

    private boolean shouldCollectCalibrationTrace(KeyboardSurfaceView.GestureTrace trace) {
        return trace != null
                && trace.key != null
                && trace.key.type == KeySpec.Type.HANGUL_CONSONANT
                && trace.key.consonant != null
                && getPackageName().equals(currentEditorPackage)
                && SettingsStore.isGestureCalibrationSessionActive(this);
    }

    private void switchMode(KeyboardMode nextMode) {
        commitComposingText();
        mode = nextMode;
        if (keyboardView != null) {
            keyboardView.setMode(nextMode);
        }
    }

    private void handleBackspace() {
        if (composer.backspace()) {
            refreshComposingText();
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
        keyboardView.showClipboardContext(currentEditorIsPassword ? "보안 입력란" : clipboardText());
    }

    private void pasteFromClipboard() {
        commitComposingText();
        InputConnection inputConnection = getCurrentInputConnection();
        boolean handledByEditor = inputConnection != null
                && inputConnection.performContextMenuAction(android.R.id.paste);
        if (handledByEditor) {
            return;
        }
        commitTextIfNeeded(clipboardText());
    }

    private String clipboardText() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return "";
        }
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            return "";
        }
        CharSequence text = clipData.getItemAt(0).coerceToText(this);
        return text == null ? "" : text.toString();
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
