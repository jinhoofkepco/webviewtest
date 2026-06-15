# WebView Download Mission Report

Date: 2026-06-16 KST

## What Was Added

- Added `RUN DL x3` mission beside `RUN PHOTO x6`.
- Each run uploads the latest 3 photos, one photo per batch.
- Rotates three attachment/download strategies:
  - Batch 1: `photo-menu` attach, ask ChatGPT to redraw the photo, save generated image.
  - Batch 2: direct `input[type=file]` attach, ask for long text, save assistant text through native Base64 bridge.
  - Batch 3: `file-menu` attach, ask for downloadable `webview_download_test.txt`, click/download the generated text link.
- Added thick mission logging for attach, prompt, send, response stability, download probe, saved/enqueued downloads, and crashes.
- Added `work/download-mission-runner.ps1` for repeated self-running ADB tests.

## Snippets Selected

| Area | Applied Pattern |
| --- | --- |
| File upload | `WebChromeClient.onShowFileChooser` plus automatic `Uri[]` delivery |
| Menu attach | DOM target probing plus native coordinate tap fallback |
| Direct attach | `input[type=file]` click fallback |
| HTTP download | `DownloadListener` + `DownloadManager` with cookie/user-agent headers |
| Blob/data download | JS fetch/FileReader/Base64 bridge into native Downloads |
| Text save | Assistant text converted to `data:text/plain;base64` and saved via bridge |
| Stability detection | Snapshot polling for assistant text, stop button, upload status, and no-change window |

The key source ideas are documented in `docs/webview-github-snippets.md`.

## Test Rounds

| Round | Output | Result | Notes |
| --- | --- | --- | --- |
| 1 | `outputs/download-mission-live-20260616-075634` | 3 batches completed, 1 native save, 2 download enqueues, crash 0 | Exposed that text-file mission could grab an image download candidate first. |
| 2 | `outputs/download-mission-after-text-filter-20260616-080534` | 3 batches completed, text-file link correctly clicked, crash 0 | Exposed runner pull parser bug and attach-button false positive during download probing. |
| 3 | `outputs/download-mission-final-verify-20260616-081400` | 3 batches completed, 2 stable + 1 timeout, crash 0 | Exposed that timeout responses with download hints should still run download probing. |
| 4 | `outputs/download-mission-timeout-download-20260616-082326` | 3/3 stable, 1 native save, 2 download enqueues, crash 0 | Final verified run after filters and timeout-probe fix. |

## Final Verified Run

Mission: `20260616-082341`

Output folder: `outputs/download-mission-timeout-download-20260616-082326`

| Batch | Strategy | Finish | Download Result |
| --- | --- | --- | --- |
| 1 | `redrawImage_photoMenu_downloadProbe` | stable | `http-download-requested:webview-redrawImage_photoMenu_downloadProbe-batch-1.png` |
| 2 | `longText_fileInput_nativeTextSave` | stable | `assistant-text-save-started:3191` |
| 3 | `downloadableText_fileMenu_blobBridge` | stable | `clicked-download-button:webview_download_test.txt` |

Summary:

- Batch finished: 3/3
- Stable batches: 3/3
- Timeout: 0
- Download saved by native bridge: 1
- Download enqueued by `DownloadManager`: 2
- Crash/ANR signals: 0
- File chooser requested/delivered: 3/3

## Fixes Made During Testing

- Text strategies now skip image-source extraction so they do not save uploaded/generated images by accident.
- Download probe filters out non-download controls such as `파일 추가`, attach/upload/camera buttons, research/search menu items.
- Timeout responses now still run download probing when assistant text or download hints exist.
- Runner now parses `events.jsonl.payload` correctly and pulls ADB files without mojibake.
- HTTP downloads now prefer ChatGPT's `fn` query parameter before falling back to `URLUtil.guessFileName`, so generated text filenames are better preserved.

## Current Artifacts

- APK: `outputs/webview-testbed-debug.apk`
- Source ZIP: `outputs/WebViewTestBed-source.zip`
- Final run summary: `outputs/download-mission-timeout-download-20260616-082326/summary.md`
- Final run events: `outputs/download-mission-timeout-download-20260616-082326/cycle-01-20260616-082341-events.jsonl`
- Final run responses: `outputs/download-mission-timeout-download-20260616-082326/cycle-01-20260616-082341-responses.md`

Note: the final APK includes the filename-preservation patch and built successfully. ADB disconnected after that build, so that last filename-only patch was build-verified but not re-run on-device.
