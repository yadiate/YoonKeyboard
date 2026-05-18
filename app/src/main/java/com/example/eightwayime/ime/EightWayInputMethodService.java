package com.example.eightwayime.ime;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.example.eightwayime.MainActivity;
import com.example.eightwayime.SettingsStore;
import com.example.eightwayime.hangul.HangulComposer;

public class EightWayInputMethodService extends InputMethodService implements KeyboardActionListener {
    private final HangulComposer composer = new HangulComposer();
    private KeyboardSurfaceView keyboardView;
    private KeyboardMode mode = KeyboardMode.HANGUL;
    private SettingsStore.Snapshot settings;

    @Override
    public View onCreateInputView() {
        settings = SettingsStore.load(this);
        keyboardView = new KeyboardSurfaceView(this);
        keyboardView.setListener(this);
        keyboardView.setSettings(settings);
        keyboardView.setMode(mode);
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        settings = SettingsStore.load(this);
        if (keyboardView != null) {
            keyboardView.setSettings(settings);
        }
        composer.reset();
    }

    @Override
    public void onFinishInput() {
        commitComposingText();
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
