# WebView Voice Mission Prep

Date: 2026-06-16 KST

## Review Update

The voice test should not reuse the old heavy text-observer path as the main signal. The first-pass design separates the probes:

- Audio/output signal: frequent but cheap media-element RMS probe using `captureStream()` + `AnalyserNode`.
- Text completion signal: low-frequency assistant text probe, reusing the bottom-up last-assistant scan pattern.
- Mic control: native `AudioManager.setMicrophoneMute`, captured `MediaStreamTrack.enabled`, and DOM mute button probing.
- Cleanup: close the injected `AudioContext` and reset mic state when the mission stops or finishes.

For voice missions, the page-level `MutationObserver` text bridge is not injected, because it can call `document.body.innerText` repeatedly during active UI updates.

## Implemented For 1st Test

- Added `RUN VOICE x1`, `MIC MUTE`, `MIC OPEN`, and `VOICE DIAG` controls.
- Added a thin green voice level bar under the status text.
- Added microphone permission preflight for `RECORD_AUDIO`.
- Added a voice mission logger kind: `voice-x1`.
- Added a mic stream hook around `navigator.mediaDevices.getUserMedia`.
- Added a voice-mode button probe and readiness diagnostics.
- Added a prompt-send flow from the memo field, with a default Korean voice test prompt when memo is empty.
- Added a turn monitor:
  - audio probe every 150 ms,
  - assistant text probe about every 1000 ms,
  - finish on sustained audio quiet, text-only stable fallback, or timeout.
- Added manual and cleanup mic mute/open paths.

## First Test Checklist

1. Launch the installed debug app.
2. Confirm the ChatGPT WebView is still logged in.
3. Leave the memo empty for the default prompt, or type a custom prompt.
4. Press `RUN VOICE x1`.
5. Watch whether ChatGPT voice UI opens.
6. Watch the green level bar when assistant audio plays.
7. During playback, press `MIC MUTE` and `MIC OPEN` once if we want to verify manual mic control.
8. After completion, export or pull the mission log from the app files.

## Current Limits

- The first test is text-prompt voice output only. Attachment plus voice should be a second pass after confirming that the voice UI and audio probe are stable.
- If ChatGPT voice mode hides or disables the normal text composer, the run records `voice_prompt_editor_not_found` and stops instead of forcing an unsafe UI path.
- The audio probe observes Web media elements. If ChatGPT renders audio through a path that does not expose `audio` or `video` elements to the page, the level bar may stay quiet even though native device audio is audible.

## Test Results

### First Attempt: `20260616-132301`

Result: failed usefully.

- Prompt injection and text completion worked.
- Voice button click did not find `Voice 시작`.
- The old readiness rule incorrectly accepted `mediaCount=2` as voice-open.
- It sent the prompt without actually entering voice mode and finished as `text-stable-no-audio`.

Fix after this attempt:

- Do not treat media element count alone as voice-mode readiness.
- Include `textContent`/ARIA label variants in voice button labels.
- Add composer-right geometry fallback so the rightmost `Voice 시작` button is clicked before sending.

### Second Attempt: `20260616-132606`

Result: success.

- `Voice 시작` clicked first.
- Voice readiness detected 700 ms later with labels including `음성으로 연결 중 취소` and `마이크 끄기`.
- Prompt injected and sent after voice mode was open.
- Audio output was detected from Web media with `maxRms` up to about `0.219`.
- Assistant text reached 80 chars and ended with `음성 테스트 완료!`.
- Turn finished by `audio-quiet` after 16.32 seconds.
- Manual mic mute worked:
  - native mic mute set to true,
  - captured mic tracks muted,
  - ChatGPT DOM button `마이크 끄기` clicked.

Artifacts:

- `outputs/voice-first-20260616-132301/`
- `outputs/voice-second-20260616-132606/summary.md`
- `outputs/voice-second-20260616-132606/events.jsonl`
- `outputs/voice-second-20260616-132606/logcat.txt`

## Build Artifact

- APK: `outputs/webviewtest-debug-voice-prep.apk`
- Build command: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
- Install command: `adb install -r outputs/webviewtest-debug-voice-prep.apk`

## Dual Voice Relay Tests

Goal: run two ChatGPT voice WebViews as a controlled text relay, while using voice output timing for handoff. The current scenario is a father persuading a playful daughter to study.

Implemented:

- Added `RUN DUO` with two WebViews using the shared logged-in ChatGPT session.
- Opens voice mode on both sides before the relay starts.
- Keeps both captured mic tracks muted with `MediaStreamTrack.enabled=false`.
- Blocks the run if mic capture is not observed, because side-specific mute depends on the getUserMedia hook.
- Avoids DOM mic-button toggles during normal mute control, because that can break voice sessions.
- Sends exact completed assistant text to the other side as the next prompt.
- Uses low-cost media RMS probing to detect assistant voice output.
- Logs `busy`, stop-button labels, sentence completion, text stability, audio level, and handoff reason.

Current completion rule:

- Text-only A handoff is disabled.
- A turn completes when assistant text is stable and the audio level stays under the adaptive quiet threshold for `100ms`.
- The adaptive threshold starts at `7%`.
- After the first turn, the app samples the last `1500ms` audio tail window, selects raw low-tail `P50`, adds `2%`, and uses that as the next quiet threshold.

Latest verified run: `20260616-155629`

| Turn | Side | Reason | Text stable | Sentence | Quiet for | Threshold | Last level | Tail chosen |
| --- | --- | --- | ---: | --- | ---: | ---: | ---: | ---: |
| 1 | A | `text-stable-audio-quiet-100ms` | 4216ms | true | 109ms | 12.868% | 1.435% | 10.868% |
| 2 | B | `text-stable-audio-quiet-100ms` | 1788ms | true | 109ms | 12.868% | 11.331% | 9.840% |
| 3 | A | `text-stable-audio-quiet-100ms` | 215ms | true | 110ms | 12.868% | 10.482% | 10.836% |
| 4 | B | `text-stable-audio-quiet-100ms` | 1505ms | true | 108ms | 12.868% | 11.271% | 11.271% |

Finding:

- The visible low tail plateau is real in probe data. It appeared around `10%..12%`, not around the old quiet-run floor of `1.435%`.
- Using tail `P90 + 2%` produced a `13.689%` threshold and was too permissive.
- Using tail `P50 + 2%` produced a `12.868%` threshold and completed all four turns.

Remaining follow-up:

- Turn 3 completed after one sentence with only `215ms` of text stability. To avoid treating inter-sentence pauses as final completion, require a minimum text-stable floor such as `650ms` even when `sentenceComplete=true`.
