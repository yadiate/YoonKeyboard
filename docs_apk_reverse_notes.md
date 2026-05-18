# APK Reverse Notes

Source APK:

```text
C:\Users\yadia\Downloads\윤키보드_base.apk
```

Observed package:

```text
com.narae.keyboard.palbangmigeul
```

Key Manifest facts:

- App version: `1.6.0.1`, version code `1601`.
- Min SDK: `9`, target SDK: `29`.
- IME service: `com.narae.keyboard.palbangmigeul.SoftKeyboard`.
- Activities: `Settings`, `Help`, `SpeechRecog`, `UsefulSentences`.
- Permissions: `VIBRATE`, `WAKE_LOCK`, `INTERNET`, `com.android.vending.CHECK_LICENSE`.

Feature shape inferred from resources and DEX symbols:

- Korean input modes: `윤키보드`, `2벌식`, and a landscape hybrid mode.
- English input modes: `윤키보드`, `Qwerty`, and a landscape hybrid mode.
- Number modes: phone keypad and computer keypad.
- Theme/skin system with 22 skins.
- Font/icon sets including Gothic, Batang, Gyeonggi Cheonnyeon, Jeju Gothic variants.
- Gesture sensitivity / double-tap speed settings.
- Popups for symbols, emoticons, useful sentences, my info, and password snippets.
- Speech recognition trigger via `android.speech.action.RECOGNIZE_SPEECH`.

Implementation decision for this new app:

- Recreate the core IME behavior in original Java source.
- Avoid reusing original drawable resources or decompiled source.
- Skip license checks and network behavior.
- Avoid password snippet storage in the prototype for privacy reasons.
