package com.yadiate.yoonkeyboard.ime;

public interface KeyboardActionListener {
    void onKeyTouchDown(KeySpec key);

    void onKey(KeySpec key);

    void onGesture(KeySpec key, Integer vowelIndex, boolean playCommitHaptic, boolean keyHandledOnTouchDown);

    void onGesturePreviewChanged(String previewText);
}
