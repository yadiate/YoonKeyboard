package com.yadiate.yoonkeyboard.ime;

import com.yadiate.yoonkeyboard.hangul.Consonant;

public class KeySpec {
    public enum Type {
        CHARACTER,
        HANGUL_CONSONANT,
        HANGUL_VOWEL,
        HANGUL_VOWEL_PAD,
        MODE_HANGUL,
        MODE_ENGLISH,
        MODE_SYMBOLS,
        MODE_NUMBERS,
        SYMBOL_PAGE_PREV,
        SYMBOL_PAGE_NEXT,
        SHIFT,
        SHIFT_LOCK,
        DELETE,
        SPACE,
        ENTER,
        HIDE_KEYBOARD,
        SETTINGS,
        CLIPBOARD_CONTEXT,
        CLIPBOARD_PASTE,
        CLIPBOARD_CLOSE,
        CANCEL_TOUCH_DOWN,
        MOVE_LEFT,
        MOVE_RIGHT,
        NO_OP
    }

    public final String label;
    public final String hintTop;
    public final String hintBottom;
    public final Type type;
    public final String outputText;
    public final Consonant consonant;
    public final int vowelIndex;
    public final float weight;
    public final int rowSpan;
    public final int columnSpan;
    public final int clipboardIndex;
    public final String calibrationKey;

    private KeySpec(String label, Type type, String outputText, Consonant consonant, int vowelIndex, float weight) {
        this(label, null, null, type, outputText, consonant, vowelIndex, weight, 1, 1, -1, null);
    }

    private KeySpec(String label, String hintTop, String hintBottom, Type type, String outputText,
                    Consonant consonant, int vowelIndex, float weight, int rowSpan, int columnSpan,
                    int clipboardIndex, String calibrationKey) {
        this.label = label;
        this.hintTop = hintTop;
        this.hintBottom = hintBottom;
        this.type = type;
        this.outputText = outputText;
        this.consonant = consonant;
        this.vowelIndex = vowelIndex;
        this.weight = weight;
        this.rowSpan = Math.max(1, rowSpan);
        this.columnSpan = Math.max(1, columnSpan);
        this.clipboardIndex = clipboardIndex;
        this.calibrationKey = calibrationKey == null ? "" : calibrationKey;
    }

    public static KeySpec command(String label, Type type) {
        return new KeySpec(label, type, null, null, -1, 1f);
    }

    public static KeySpec command(String label, Type type, float weight) {
        return new KeySpec(label, type, null, null, -1, weight);
    }

    public static KeySpec character(String label, String outputText) {
        return new KeySpec(label, Type.CHARACTER, outputText, null, -1, 1f);
    }

    public static KeySpec character(String label, String outputText, float weight) {
        return new KeySpec(label, Type.CHARACTER, outputText, null, -1, weight);
    }

    public static KeySpec clipboardPaste(String label, int clipboardIndex, float weight) {
        return new KeySpec(label, null, null, Type.CLIPBOARD_PASTE, null, null, -1, weight, 1, 1,
                clipboardIndex, null);
    }

    public static KeySpec consonant(Consonant consonant) {
        return new KeySpec(consonant.label(), Type.HANGUL_CONSONANT, null, consonant, -1, 1f);
    }

    public static KeySpec consonant(Consonant consonant, float weight) {
        return new KeySpec(consonant.label(), Type.HANGUL_CONSONANT, null, consonant, -1, weight);
    }

    public static KeySpec vowel(String label, int vowelIndex) {
        return new KeySpec(label, Type.HANGUL_VOWEL, null, null, vowelIndex, 1f);
    }

    public static KeySpec vowelPad(String label, float weight) {
        return new KeySpec(label, Type.HANGUL_VOWEL_PAD, null, null, -1, weight);
    }

    public static KeySpec spacer(float weight) {
        return new KeySpec("", Type.NO_OP, null, null, -1, weight);
    }

    public KeySpec withHints(String hintTop, String hintBottom) {
        return new KeySpec(label, hintTop, hintBottom, type, outputText, consonant, vowelIndex, weight, rowSpan,
                columnSpan, clipboardIndex, calibrationKey);
    }

    public KeySpec withRowSpan(int rowSpan) {
        return new KeySpec(label, hintTop, hintBottom, type, outputText, consonant, vowelIndex, weight, rowSpan,
                columnSpan, clipboardIndex, calibrationKey);
    }

    public KeySpec withColumnSpan(int columnSpan) {
        return new KeySpec(label, hintTop, hintBottom, type, outputText, consonant, vowelIndex, weight, rowSpan,
                columnSpan, clipboardIndex, calibrationKey);
    }

    public KeySpec withCalibrationKey(String calibrationKey) {
        return new KeySpec(label, hintTop, hintBottom, type, outputText, consonant, vowelIndex, weight, rowSpan,
                columnSpan, clipboardIndex, calibrationKey);
    }
}
