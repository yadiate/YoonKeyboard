package com.example.eightwayime.ime;

public interface KeyboardActionListener {
    void onKey(KeySpec key);

    void onGesture(KeySpec key, Integer vowelIndex);
}
