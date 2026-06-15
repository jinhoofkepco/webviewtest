# WebView Test Bed

Android WebView 기능을 빠르게 테스트하기 위한 네이티브 Java 앱입니다.

첫 화면은 흰색 메모장처럼 동작하고, 하단에는 직사각형 버튼 3개가 있습니다.

- `GOOGLE + URL`: 주소창이 보이는 `https://www.google.com`
- `GPT + URL`: 주소창이 보이는 `https://chatgpt.com`
- `GPT CLEAN`: 주소창 없이 보이는 `https://chatgpt.com`

## 포함된 WebView 테스트 장치

- WebView JavaScript, DOM storage, cookie, media playback 설정
- 주소창 표시/숨김 전환
- `<input type="file">` 파일 선택 처리
- 최신 사진 30장을 MediaStore에서 읽어 5장씩 6회 ChatGPT에 첨부하는 자동 미션
- 최신 사진 3장을 1장씩 올리며 이미지 생성/긴 텍스트/native 저장/다운로드 링크/blob 저장을 돌리는 다운로드 미션
- HTTP/HTTPS 다운로드는 `DownloadManager`로 저장
- `blob:` 다운로드는 JS bridge로 Base64 변환 후 Downloads에 저장
- `PermissionRequest` 기반 마이크/카메라 권한 처리
- `MutationObserver` 기반 페이지 텍스트 변화 감지
- `WebChromeClient` 콘솔 로그와 진행률 표시
- HTML5 fullscreen custom view 처리
- 하단 버튼이 Android system navigation bar에 깔리지 않도록 window inset 적용

## 단축키

하드웨어 키보드 또는 ADB key event 테스트용입니다.

- `Ctrl+1`: Google + 주소창
- `Ctrl+2`: ChatGPT + 주소창
- `Ctrl+3`: ChatGPT 주소창 없음
- `Ctrl+Enter`: 메모장의 텍스트를 현재 WebView의 입력창에 주입
- `Ctrl+U`: 페이지의 file input 또는 첨부류 버튼 클릭 시도
- `Ctrl+R`: 현재 WebView 새로고침
- `Ctrl+L`: 주소창 표시 및 포커스
- `Ctrl+P`: GPT 사진 미션 시작
- `Ctrl+S`: GPT 사진 미션 중지

## GPT 사진 미션

`RUN PHOTO x6`를 누르면 다음 작업을 자동으로 시도합니다.

1. 사진 권한을 확인하고 최신순 사진 30장을 읽습니다.
2. ChatGPT WebView를 열고, 에디터가 보일 때까지 기다립니다.
3. 사진 5장씩 6번 첨부합니다.
4. 각 배치마다 "각 사진을 두 문장씩 묘사" 프롬프트를 넣고 전송합니다.
5. 응답 텍스트가 12초 동안 변하지 않고 중지 버튼/업로드 상태가 사라지면 멈춘 것으로 판정합니다.
6. 각 배치의 사진 목록, 프롬프트, 응답 스냅샷, 안정화 시간을 내부 저장소에 기록합니다.

미션은 한 ChatGPT 대화에 계속 누적해서 실행합니다. WebView가 무거워지며 튕기는지 보려는 테스트라서 일부러 새 대화를 열지 않습니다.

내부 로그 위치는 앱 상태줄에 표시되는 `gpt_photo_missions/<timestamp>`입니다. 앱 안의 `EXPORT LOG` 버튼을 누르면 `responses.md`와 `events.jsonl`을 합친 텍스트가 Downloads에 저장됩니다.

필요한 피지컬 도움:

- Android 사진 권한 요청이 뜨면 전체 사진 접근을 허용해야 30장 테스트가 됩니다.
- ChatGPT 로그인 화면이 나오면 WebView 안에서 직접 로그인한 뒤 `RUN PHOTO x6`를 다시 누르면 됩니다.

## GPT 다운로드 미션

`RUN DL x3`를 누르면 최신 사진 3장을 한 장씩 올리면서 다운로드 계열 동작을 테스트합니다.

1. 최신 사진 1장으로 새 그림을 그려 달라고 요청하고, 이미지 다운로드 버튼/link/blob/data/http 저장을 순서대로 시도합니다.
2. 다음 사진 1장으로 1600자 이상의 긴 한국어 설명을 요청하고, 응답 텍스트를 native bridge로 `.txt` 저장합니다.
3. 마지막 사진 1장으로 내려받을 수 있는 `webview_download_test.txt` 생성을 요청하고, 다운로드 링크 또는 `blob:` bridge 저장을 시도합니다.

각 배치의 첨부 방식은 `photo-menu`, `file-input`, `file-menu`를 돌아가며 씁니다. 다운로드 단계는 `download_phase_start`, `download_probe`, `download_probe_result`, `download_saved`, `download_enqueued`, `download_phase_finish` 이벤트로 두껍게 기록됩니다.

반복 실행은 다음 스크립트로 돌릴 수 있습니다.

```powershell
.\work\download-mission-runner.ps1 -Cycles 3 -RestMinutes 3 -CycleTimeoutMinutes 70
```

## 빌드와 설치

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.webviewtestbed/.MainActivity
```

## 참고 문서

- WebView/GitHub 스니펫 수집: `docs/webview-github-snippets.md`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

주의: 이 앱은 임의 웹사이트에 `addJavascriptInterface`를 붙이는 테스트베드입니다. 실제 배포 앱에서는 도메인 제한, bridge 최소화, 민감 권한 UX를 따로 설계해야 합니다.
