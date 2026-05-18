# 윤키보드

Clean-room Android keyboard prototype based on the feature shape of the provided APK.

## What is implemented

- Android `InputMethodService` registration.
- Launcher/settings screen with buttons for Android keyboard enablement and picker.
- Custom keyboard view drawn in Java.
- 윤키보드 Korean consonant keyboard.
- Eight-direction vowel gesture input:
  - right: ㅏ
  - left: ㅓ
  - up: ㅗ
  - down: ㅜ
  - down-right: ㅑ
  - up-left: ㅕ
  - up-right: ㅛ
  - down-left: ㅠ
  - long horizontal: ㅡ
  - long vertical: ㅣ
  - supported compound gestures include ㅐ, ㅔ, ㅒ, ㅖ, ㅘ, ㅙ, ㅚ, ㅝ, ㅞ, ㅟ, ㅢ.
- Hangul syllable composer with basic final consonant and compound final handling.
- English, symbol, and number modes.
- Settings screen based on the analyzed APK feature set:
  - skin/theme selection
  - Korean keyboard selection with 윤키보드/2벌식 and vertical/horizontal variants
  - English and number keyboard type selection
  - stroke length sensitivity
  - double-consonant timing setting placeholder
  - vibration and sound toggles
  - useful sentence and personal info snippets

## How to run

Open this folder in Android Studio:

```text
C:\Users\yadia\Documents\Codex\2026-05-18\apk\EightWayIme
```

Then build and install the `app` module. After installing:

1. Open the app.
2. Tap "키보드 사용 설정 열기" and enable 윤키보드.
3. Tap "키보드 선택창 열기" and select 윤키보드.

## Notes

This project does not copy original APK code, drawable assets, license checks, or private data behavior. It recreates the interaction model as a new implementation.
