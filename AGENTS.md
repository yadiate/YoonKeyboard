# AGENTS.md

이 문서는 `EightWayIme` 프로젝트를 이어서 작업하는 에이전트를 위한 인수인계 메모다.

## 프로젝트 개요

- 목표: 제공된 `팔방미글_base.apk`의 기능 형태를 분석해, 새 안드로이드 키보드 앱을 클린룸 방식으로 구현한다.
- 현재 프로젝트 위치: `C:\Users\yadia\Documents\Codex\2026-05-18\apk\EightWayIme`
- 원본 APK 위치: `C:\Users\yadia\Downloads\팔방미글_base.apk`
- GitHub 저장소명과 앱 문서 일부에서는 프로젝트명을 `YoonKeyboard`/`윤키보드`로 사용 중이다.
- 원본 APK 코드, 리소스, 이미지, 라이선스 로직은 복사하지 않는다. 분석한 UX/기능 구조만 참고한다.

## Git 상태

- 브랜치: `main`
- 원격: `origin` = `https://github.com/yadiate/YoonKeyboard.git`
- 최신 푸시 커밋:
  - `6dfb865` `Improve vertical two-beolsik drag input`
  - `860553f` `Restore Korean keyboard layouts`
  - `9eba96c` `Initial YoonKeyboard implementation`
- 2026-05-19 기준 `HEAD`와 `origin/main`은 `6dfb8657e98eef2db400cc8682b9815b417e8a7a`로 일치했다.

## 원본 APK 분석 요약

- 원본 패키지: `com.narae.keyboard.palbangmigeul`
- 원본 버전: `1.6.0.1`, versionCode `1601`
- 원본 IME 서비스: `com.narae.keyboard.palbangmigeul.SoftKeyboard`
- 원본 주요 화면: `Settings`, `Help`, `SpeechRecog`, `UsefulSentences`
- 원본 권한: `INTERNET`, `CHECK_LICENSE`, `WAKE_LOCK`, `VIBRATE`
- 기능 형태:
  - 한글 입력: 윤키보드, 2벌식, 윤키보드+2벌식 가로 모드
  - 영어 입력: 윤키보드, QWERTY, 윤키보드+QWERTY 가로 모드
  - 숫자 입력: 전화기패드, 컴퓨터패드
  - 스킨 22종, 폰트/아이콘 스타일, 획 민감도, 쌍자음 타이밍, 소리/진동/음성 설정
  - 유용한 문장, 내정보, 비밀번호 문구 계열 기능
- 윤키보드 핵심 제스처:
  - 오른쪽: ㅏ
  - 왼쪽: ㅓ
  - 위: ㅗ
  - 아래: ㅜ
  - 대각선: ㅑ/ㅕ/ㅛ/ㅠ
  - 긴 가로: ㅡ
  - 긴 세로: ㅣ
  - 복합 제스처로 ㅐ/ㅔ/ㅒ/ㅖ/ㅘ/ㅙ/ㅚ/ㅞ/ㅟ/ㅝ/ㅢ 계열 처리

## APK 분석/추출 절차

원본 APK를 다시 확인해야 할 때는 클린룸 원칙을 유지한다. 원본 코드, 이미지, 리소스를 새 앱에 복사하지 말고, 패키지 구조·레이아웃 이름·동작 흐름·상수 형태만 참고한다.

PowerShell 기준 기본 작업 폴더:

```powershell
$root = "C:\Users\yadia\Documents\Codex\2026-05-18\apk\EightWayIme"
$src = "C:\Users\yadia\Downloads\팔방미글_base.apk"
$work = Join-Path $root ".codex_apk_analysis"
$buildTools = "C:\Users\yadia\AppData\Local\Android\Sdk\build-tools\35.0.0"

New-Item -ItemType Directory -Force -Path $work | Out-Null
Copy-Item -LiteralPath $src -Destination (Join-Path $work "palbang_base.apk") -Force
```

APK 기본 정보와 AndroidManifest 확인:

```powershell
& "$buildTools\aapt.exe" dump badging (Join-Path $work "palbang_base.apk")
& "$buildTools\aapt.exe" dump permissions (Join-Path $work "palbang_base.apk")
& "$buildTools\aapt.exe" dump xmltree (Join-Path $work "palbang_base.apk") AndroidManifest.xml
```

리소스/레이아웃 이름 찾기:

```powershell
& "$buildTools\aapt.exe" list (Join-Path $work "palbang_base.apk") | rg "res/xml|godic|hangul|qwerty|numbers|phone|popup"
& "$buildTools\aapt.exe" dump xmltree (Join-Path $work "palbang_base.apk") res/xml/godic_hangul_a.xml
& "$buildTools\aapt.exe" dump xmltree (Join-Path $work "palbang_base.apk") res/xml/godic_hangul_qwerty_land.xml
```

DEX를 텍스트로 덤프해서 클래스명, 메서드명, 상수 흐름을 찾는 방법:

```powershell
tar -xf (Join-Path $work "palbang_base.apk") -C $work classes.dex
& "$buildTools\dexdump.exe" -d (Join-Path $work "classes.dex") > (Join-Path $work "classes_dexdump.txt")
rg -n "SoftKeyboard|HangulCodeAutomata|VowelPatternRecog|SHORT_LINE_THRESH|LONG_LINE_THRESH|patternMatching" `
  (Join-Path $work "classes_dexdump.txt")
```

현재 남아 있는 분석 산출물:

- `.codex_apk_analysis\palbang_base.apk`: 원본 APK 복사본.
- `.codex_apk_analysis\classes.dex`: 원본 APK에서 꺼낸 DEX.
- `.codex_apk_analysis\classes_dexdump.txt`: `dexdump -d` 결과. `HangulCodeAutomata`, `VowelPatternRecog`, `Settings`, `SoftKeyboard` 등을 검색할 때 사용했다.
- `docs_apk_reverse_notes.md`: 원본 패키지/권한/화면/기능 형태 요약.

새 앱 APK 산출과 검증은 아래 스크립트를 우선 사용한다:

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-ime-sim.ps1
powershell -ExecutionPolicy Bypass -File tools\build-test-apks.ps1
```

`tools\build-test-apks.ps1`는 현재 패키지 APK와 레거시 패키지 제거용 APK를 `dist\`에 복사하고 SHA256을 출력한다. 산출 APK를 확인할 때는:

```powershell
& "$buildTools\aapt.exe" dump badging dist\YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.34.apk
$env:JAVA_HOME = "C:\Users\yadia\AppData\Local\Programs\Rider\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "$buildTools\apksigner.bat" verify --verbose dist\YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.34.apk
Get-FileHash -Algorithm SHA256 dist\YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.34.apk
```

실기기 설치는 기기가 `adb devices`에 잡힌 뒤:

```powershell
powershell -ExecutionPolicy Bypass -File tools\install-current.ps1 -RemoveLegacy -SetIme
```

## 현재 구현 상태

- Android `InputMethodService` 기반 키보드 앱.
- 설정/런처 화면에서 키보드 활성화 화면과 입력기 선택 화면을 열 수 있다.
- Java로 직접 그리는 커스텀 키보드 뷰가 있다.
- 한글 조합기와 팔방향 모음 제스처 입력을 구현했다.
- 설정 화면에 원본 APK에서 확인한 항목을 반영했다.
  - 스킨 22종
  - 한글/영어/숫자 키보드 타입
  - 한글/영어/숫자 폰트 스타일
  - 획 길이 민감도
  - 쌍자음 타이밍 항목
  - 진동/소리/음성 토글
  - 유용한 문장 14개
  - 내정보 4개
- 키보드가 설정값을 일부 반영한다.
  - 스킨 색상
  - 윤키보드/2벌식 한글 배열과 세로/가로 선택
  - QWERTY/간이 팔방식 영어 배열
  - 전화기패드/컴퓨터패드 숫자 배열
  - 진동/소리 설정
  - `문구`, `내정보` 키는 첫 번째 비어 있지 않은 저장 문구를 입력한다.
- 2026-05-19에 한글 화면의 드래그 모음 입력을 복원했다.
  - 윤키보드 모드와 2벌식 모드 모두 지원한다.
  - 자음 키에서 드래그하면 `자음+모음`을 입력한다.
  - 모음 키나 `모음` 전환 키에서 드래그하면 모음만 입력한다.
  - 손가락 흔들림으로 단순 드래그가 누락되지 않도록 전체 이동 방향 fallback을 추가했다.
  - 2벌식 세로 화면의 짧은 드래그도 잡히도록 시작점-끝점 기반 fallback 기준을 더 낮췄다.
- 2벌식 세로 드래그 입력 관련 주의:
  - `KeyboardSurfaceView.shouldHandleGesture()`에서 한글 모드 전체의 제스처를 허용해야 한다.
  - 예전처럼 윤키보드 모드만 허용하는 `usesGestureVowels()` 조건을 되살리면 2벌식 드래그가 다시 막힌다.
  - `GestureVowelMapper.mapOverallDirection()`은 실제 폰의 짧고 촘촘한 드래그 이벤트를 잡기 위한 보강이다.
- 2026-05-19에 원본 APK의 `godic_hangul_*` XML을 기준으로 한글 레이아웃을 다시 맞췄다.
  - 윤키보드/2벌식 세로 화면은 원본처럼 삭제 키를 첫 행 오른쪽에 둔다.
  - 2벌식 가로 화면은 원본 `hangul_qwerty_land`처럼 자음+모음 한 화면 배열을 사용한다.
- 2026-05-20에 보정 입력을 문장형에서 오타 글자 기반 계획형으로 바꿨다.
  - 예: `현`을 넣으면 `현` 10회, 받침 제거형 `혀` 5회, 간섭 글자 `허` 5회와 `호` 5회를 자동으로 입력 과제로 만든다.
  - 사용자가 과제 중 틀리게 입력해도 경고하지 않고, 해당 획을 의도 글자 샘플로 저장한다.
- 2026-05-20에 쌍자음 입력 대기시간을 ms 프로그래스 바로 바꿨다.
  - `SettingsStore.KEY_DOUBLE_TAP_TIMEOUT_MS`에 80~640ms 값을 저장한다.
  - 쌍자음 변환은 이제 같은 자음이 설정 시간 안에 다시 눌린 경우에만 실행된다.
- 2026-05-20에 터치 판정 설정 화면에 비주얼 키보드 프리뷰를 추가했다.
  - 파란 면은 실제 키 영역, 빨간 선/면은 현재 설정값 기준 터치 인식 영역이다.
  - 히트박스 슬라이더를 움직이면 프리뷰가 즉시 다시 그려진다.
- 개인정보 이슈 때문에 비밀번호 문구 저장 기능은 구현하지 않았다.

## 주요 파일

- `app/src/main/AndroidManifest.xml`: IME 서비스 등록 및 앱 권한.
- `app/build.gradle`: SDK, 패키지명, 버전, 서명 설정.
- `app/src/main/java/com/example/eightwayime/MainActivity.java`: 설정 화면.
- `app/src/main/java/com/example/eightwayime/SettingsStore.java`: 설정 저장/읽기.
- `app/src/main/java/com/example/eightwayime/ime/EightWayInputMethodService.java`: 입력기 서비스.
- `app/src/main/java/com/example/eightwayime/ime/KeyboardSurfaceView.java`: 키보드 그리기 및 터치 처리.
- `app/src/main/java/com/example/eightwayime/hangul/HangulComposer.java`: 한글 조합.
- `app/src/main/java/com/example/eightwayime/hangul/GestureVowelMapper.java`: 제스처를 모음으로 매핑.
- `docs_apk_reverse_notes.md`: APK 역기획 메모.
- `README.md`: 실행 방법과 구현 요약.

## 빌드 환경

- Android SDK: `C:\Users\yadia\AppData\Local\Android\Sdk`
- Gradle: `C:\Users\yadia\AppData\Local\Gradle\gradle-8.9`
- Java: `C:\Users\yadia\AppData\Local\Programs\Rider\jbr`
- 설치된 주요 SDK:
  - `platform-tools 37.0.0`
  - `platforms;android-35`
  - `build-tools;34.0.0`
  - `build-tools;35.0.0`
- 프로젝트에는 Gradle Wrapper가 생성되어 있다.

## 빌드 명령

PowerShell에서 프로젝트 루트 기준:

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

현재 APK 출력:

```text
app\build\outputs\apk\release\app-release.apk
app\build\outputs\bundle\release\app-release.aab
```

현재 빌드 정보:

- applicationId: `com.yadiate.yoonkeyboard`
- namespace: `com.yadiate.yoonkeyboard`
- versionCode: `36`
- versionName: `0.2.34`
- minSdk: `21`
- targetSdk: `35`
- compileSdk: `35`

현재 APK SHA256:

```text
707377A48F3F8483676212ADEEDADDD81D6EF7E339DB67D2415AFD3F1842141C
```

## 최근 검증

- `.\gradlew.bat assembleRelease bundleRelease` 성공.
- `apksigner verify --verbose app\build\outputs\apk\release\app-release.apk` 성공.
- 서명 결과:
  - v1: true
  - v2: true
  - v3/v3.1/v4: false
  - SourceStamp: false
- 현재 릴리즈 빌드는 로컬 `keystore.properties`가 있으면 `signing/yoonkeyboard-upload.jks`로 서명한다.

## 배포 상태

- Google Drive에 업로드된 APK는 이전 v2 빌드다. 최신 v6 로컬 빌드는 아직 Drive에 올리지 않았다.
- Google Drive 파일:
  - `YoonKeyboard-current-com.yadiate.yoonkeyboard-v0.2.34.apk`
  - https://drive.google.com/file/d/1BzDmEgNbvpB3vsTnbCxqjm5HJk5MjsKd/view?usp=drivesdk
- Google Drive 폴더:
  - `Codex Builds`
  - https://drive.google.com/drive/folders/1WkjdIEY8qJrw1Kx_AKc2VNpcGnN0imoL
- 이전 빌드들은 Gmail로 `yadiate@gmail.com`에 보낸 적이 있다.

## 보안/설치 이슈

- 직접 APK를 Drive/Gmail/파일앱으로 설치하면 최신 Android와 Samsung One UI에서 보안 경고가 뜰 수 있다.
- 개발자 모드는 일반 APK 설치 경고를 근본적으로 없애지 않는다.
- 삼성 기기에서는 `출처를 알 수 없는 앱 설치`, `자동 차단`, Play Protect 경고가 설치를 막을 수 있다.
- 키보드 앱은 정상 앱이어도 Android가 입력 내용 접근 경고를 표시한다. 이 경고는 스토어 출시 후에도 완전히 없앨 수 없다.
- 현재 앱은 `VIBRATE` 권한만 사용한다. 출시 신뢰도를 위해 `INTERNET` 권한은 명시적 필요가 생기기 전까지 추가하지 않는다.

## 출시 준비 메모

- Google Play 신규 앱 출시는 APK가 아니라 `.aab`가 필요하다.
- 현재 release 서명은 로컬 `keystore.properties`의 `signing/yoonkeyboard-upload.jks`를 사용한다.
- 패키지명은 `com.yadiate.yoonkeyboard`로 변경했다.
- Play Console에 필요한 항목:
  - 앱 이름, 아이콘, 스크린샷
  - 개인정보처리방침 URL
  - Data Safety 작성
  - 키보드 앱 특성상 입력 데이터 저장/전송 여부 명확화
  - 내부 테스트 트랙 또는 비공개 테스트 트랙
- Galaxy Store 출시도 가능하다. Fold/One UI 사용자를 우선한다면 Samsung Seller Portal도 검토한다.
- 장기적으로 Android developer verification 흐름 때문에, Play 밖 APK 직접 배포는 점점 불편해질 수 있다.

## 다음 작업 후보

- `.aab` 빌드 태스크 확인 및 Play 내부 테스트용 번들 생성.
- 앱명, 아이콘 결정.
- 개인정보처리방침 작성.
- 키보드 설정 UI 다듬기와 깨진 문자열/표시 검수.
- Fold6 실기기에서 설치 오류가 계속 나면 `adb install` 로그로 정확한 실패 사유 확인.
- 한글 조합 예외, 쌍자음/겹받침, 제스처 민감도 실제 사용감 테스트.
