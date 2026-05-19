package com.yadiate.yoonkeyboard.ime;

public interface KeyboardActionListener {
    void onKey(KeySpec key);

    void onGesture(KeySpec key, Integer vowelIndex, boolean playCommitHaptic);

    void onGesturePreviewChanged(String previewText);
}
