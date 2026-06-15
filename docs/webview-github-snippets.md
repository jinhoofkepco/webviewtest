# Android WebView GitHub Snippet Collection

수집 기준: 실제 GitHub 저장소/이슈에서 반복적으로 쓰이는 성공 패턴을 골라 이 앱에 맞게 축약했습니다. 아래 코드는 원문 복붙이 아니라 테스트베드용으로 재작성한 패턴입니다.

## Source Map

| Area | Source |
| --- | --- |
| Chromium WebView samples: WebRTC, file input, JS interface, touch, fullscreen | https://github.com/googlearchive/chromium-webview-samples |
| WebView camera/mic permission request | https://github.com/googlesamples/android-PermissionRequest |
| File upload handler with camera, multiple selection, FileProvider | https://github.com/mgks/android-webview-file-handler |
| Upload/download WebView template | https://github.com/adriancs2/android.webview.upload.download |
| Legacy plus current file chooser handling | https://github.com/delight-im/Android-AdvancedWebView |
| JavaScript click/event bridge sample | https://github.com/voghDev/JSEventCapture |
| HTML5 fullscreen video WebChromeClient | https://github.com/hanksudo/android-webview-youtube-fullscreen |
| Blob download workaround discussion | https://github.com/pichillilorenzo/flutter_inappwebview/issues/2212 |
| Android getUserMedia prompt failure case | https://github.com/react-native-webview/react-native-webview/issues/2854 |
| Android blob URL callback gap case | https://github.com/react-native-webview/react-native-webview/issues/3445 |
| Jetpack WebKit latest APIs | https://developer.android.com/jetpack/androidx/releases/webkit |

## 1. Baseline WebView Settings

```java
WebSettings s = webView.getSettings();
s.setJavaScriptEnabled(true);
s.setDomStorageEnabled(true);
s.setDatabaseEnabled(true);
s.setMediaPlaybackRequiresUserGesture(false);
s.setUseWideViewPort(true);
s.setLoadWithOverviewMode(true);
s.setAllowFileAccess(true);
s.setAllowContentAccess(true);
CookieManager.getInstance().setAcceptCookie(true);
CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
WebView.setWebContentsDebuggingEnabled(true);
```

Use for: Google/ChatGPT pages, prompt injection, text observation, media tests.

## 2. Address Bar Mode

```java
void openTarget(String url, boolean showAddressBar) {
    addressRow.setVisibility(showAddressBar ? View.VISIBLE : View.GONE);
    addressInput.setText(url);
    webView.loadUrl(url);
}
```

Use for: "주소창 보임" and "주소창 없음" button modes.

## 3. File Attach Through `onShowFileChooser`

```java
private ValueCallback<Uri[]> pendingFileCallback;

webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public boolean onShowFileChooser(
            WebView view,
            ValueCallback<Uri[]> callback,
            FileChooserParams params
    ) {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
        }
        pendingFileCallback = callback;
        Intent intent = params.createIntent();
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
        startActivityForResult(intent, REQ_FILE_CHOOSER);
        return true;
    }
});
```

Use for: ChatGPT attachment picker, generic `<input type=file>`.

## 4. Return File Picker Result

```java
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != REQ_FILE_CHOOSER || pendingFileCallback == null) return;
    Uri[] result = null;
    if (resultCode == RESULT_OK && data != null) {
        if (data.getClipData() != null) {
            int n = data.getClipData().getItemCount();
            result = new Uri[n];
            for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
        } else if (data.getData() != null) {
            result = new Uri[] { data.getData() };
        }
    }
    pendingFileCallback.onReceiveValue(result);
    pendingFileCallback = null;
}
```

Use for: single/multiple file attachment tests.

## 5. Click Attach Button From Shortcut

```java
String js =
    "(function(){"
  + "const input=document.querySelector('input[type=\"file\"]');"
  + "if(input){input.click();return 'file input';}"
  + "const el=[...document.querySelectorAll('button,[role=\"button\"],a,input')]"
  + ".find(x=>/attach|upload|file|첨부|파일|업로드/i.test(x.innerText||x.ariaLabel||x.title||x.value||''));"
  + "if(el){el.click();return 'attach-like';}"
  + "return 'none';"
  + "})();";
webView.evaluateJavascript(js, result -> log(result));
```

Use for: `Ctrl+U` style attachment automation.

## 6. HTTP/HTTPS Download

```java
webView.setDownloadListener((url, userAgent, disposition, mime, len) -> {
    String name = URLUtil.guessFileName(url, disposition, mime);
    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
    req.setTitle(name);
    req.setMimeType(mime);
    req.addRequestHeader("User-Agent", userAgent);
    String cookie = CookieManager.getInstance().getCookie(url);
    if (cookie != null) req.addRequestHeader("Cookie", cookie);
    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
});
```

Use for: direct PDF/image/zip link download.

## 7. Blob Download Via JS Bridge

```java
String js =
    "(async()=>{"
  + "const r=await fetch(blobUrl);"
  + "const b=await r.blob();"
  + "const reader=new FileReader();"
  + "reader.onloadend=()=>AndroidBridge.saveBase64(reader.result, suggestedName, b.type);"
  + "reader.readAsDataURL(b);"
  + "})().catch(e=>AndroidBridge.log(String(e)));";
webView.evaluateJavascript(js, null);
```

```java
@JavascriptInterface
public void saveBase64(String dataUrl, String name, String mime) {
    byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
    // Save with MediaStore Downloads on Android 10+.
}
```

Use for: ChatGPT-style generated files or `blob:` PDF links that do not reach `DownloadManager`.

### 7-A. Download Mission Strategy Rotation

이번 `RUN DL x3` 미션에는 아래 세 조합을 넣었습니다. 공통 원칙은 다운로드 버튼 클릭을 먼저 시도하고, 실패하면 페이지 안의 `blob:`, `data:`, 이미지 URL, 마지막 응답 텍스트를 순서대로 native 저장소에 넘기는 것입니다.

| Batch | Attach snippet | Prompt target | Download snippet |
| --- | --- | --- | --- |
| 1 | attach menu에서 photo/image 항목 우선 | 최신 사진 1장 기반 새 그림 생성 | DOM download control 클릭, 이미지/blob/data URL 추출 |
| 2 | 직접 `input[type=file]` 클릭 | 긴 한국어 본문 생성 | assistant 텍스트를 native bridge로 `.txt` 저장 |
| 3 | attach menu에서 file 항목 우선 | 내려받을 수 있는 txt 파일 요청 | 다운로드 링크/blob bridge, 실패 시 broad save/copy/share 버튼 탐색 |

```java
webView.addJavascriptInterface(new Object() {
    @JavascriptInterface
    public void saveBase64File(String dataUrl, String name, String mime) {
        // Decode data URL and save through MediaStore Downloads.
    }

    @JavascriptInterface
    public void requestHttpDownload(String url, String name, String mime) {
        // Enqueue http/https URLs through DownloadManager with cookies.
    }
}, "AndroidWebViewTestBed");
```

```javascript
async function saveAnyUrl(url, name, mime) {
  if (url.startsWith("blob:")) {
    const blob = await fetch(url).then(r => r.blob());
    const reader = new FileReader();
    reader.onloadend = () => AndroidWebViewTestBed.saveBase64File(reader.result, name, blob.type || mime);
    reader.readAsDataURL(blob);
    return "blob-save-started";
  }
  if (url.startsWith("data:")) {
    AndroidWebViewTestBed.saveBase64File(url, name, mime);
    return "data-save-started";
  }
  if (/^https?:/i.test(url)) {
    AndroidWebViewTestBed.requestHttpDownload(url, name, mime);
    return "http-download-requested";
  }
  return "unsupported-url";
}
```

Why this shape:

- GitHub WebView examples tend to solve normal `http/https` downloads with `DownloadListener` + `DownloadManager`.
- ChatGPT/file-generation style pages often expose `blob:` links that do not reliably reach Android's `DownloadListener`, so the JS bridge path is needed.
- Image generation surfaces do not always expose a clear download link, so saving the largest visible generated image URL is a useful fallback.
- Text-file missions must filter out attach/upload controls before looking for download buttons; otherwise labels like `파일 추가 및 기타` can be mistaken for a file download.
- If a response times out but already contains assistant text or a download hint, the download probe should still run. Image generation sometimes leaves only short text like "완성했습니다. 다운로드" before the normal stable detector is satisfied.
- For ChatGPT download URLs, prefer the `fn` query parameter before `URLUtil.guessFileName`; otherwise generated `.txt` files can be saved as generic `content.txt`.

## 8. Prompt Injection

```java
String js =
    "(function(){"
  + "const text='...';"
  + "const el=document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
  + "if(!el)return 'no editor';"
  + "el.focus();"
  + "if('value' in el){el.value=text;el.dispatchEvent(new Event('input',{bubbles:true}));}"
  + "else{el.textContent=text;el.dispatchEvent(new InputEvent('input',{bubbles:true,data:text,inputType:'insertText'}));}"
  + "return 'ok';"
  + "})();";
webView.evaluateJavascript(js, result -> log(result));
```

Use for: memo text to ChatGPT prompt box.

## 9. Text Change Detection

```java
String js =
    "(function(){"
  + "if(window.__observerInstalled)return;"
  + "window.__observerInstalled=true;"
  + "let last='';"
  + "function send(){"
  + " const text=document.body ? document.body.innerText : '';"
  + " if(text!==last){last=text;AndroidBridge.onTextChanged(text.slice(0,3000));}"
  + "}"
  + "new MutationObserver(()=>{clearTimeout(window.__t);window.__t=setTimeout(send,400);})"
  + ".observe(document.documentElement,{subtree:true,childList:true,characterData:true});"
  + "send();"
  + "})();";
```

Use for: answer streaming, login page transitions, button state changes.

## 10. Native Coordinate Click

```java
void tapWebView(WebView webView, float x, float y) {
    long now = SystemClock.uptimeMillis();
    webView.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
    webView.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0));
}
```

Use for: absolute tap tests. Prefer JS selectors when possible; coordinates break across zoom/layout changes.

## 11. Page Click Capture Through JS Bridge

```java
webView.addJavascriptInterface(new Object() {
    @JavascriptInterface public void clicked(String label) {
        Log.d("WebViewTest", label);
    }
}, "AndroidBridge");

webView.evaluateJavascript(
    "document.addEventListener('click',e=>AndroidBridge.clicked((e.target.innerText||e.target.id||e.target.tagName).slice(0,120)),true)",
    null
);
```

Use for: detecting whether a web button was really clicked.

## 12. Camera/Microphone PermissionRequest

```java
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        runOnUiThread(() -> {
            if (hasAndroidRuntimePermissions(request.getResources())) {
                request.grant(request.getResources());
            } else {
                pendingPermissionRequest = request;
                requestRuntimePermissionsFor(request.getResources());
            }
        });
    }
});
```

Use for: `navigator.mediaDevices.getUserMedia`, ChatGPT voice, WebRTC test pages.

## 13. Audio Signal Detection From Page

Native Android WebView does not expose "page is currently producing audible sound" as a simple callback. Practical test options:

```java
String js =
    "(function(){"
  + "const els=[...document.querySelectorAll('audio,video')];"
  + "return els.map((m,i)=>({i,paused:m.paused,muted:m.muted,time:m.currentTime,src:m.currentSrc}));"
  + "})();";
webView.evaluateJavascript(js, result -> log(result));
```

Use for: HTML audio/video elements. For WebAudio or remote app voice, add Android-level audio focus/logcat observation later.

## 14. HTML5 Fullscreen Video

```java
public void onShowCustomView(View view, CustomViewCallback callback) {
    fullscreenHost.addView(view, MATCH_PARENT_PARAMS);
    fullscreenHost.setVisibility(View.VISIBLE);
    webView.setVisibility(View.GONE);
    customViewCallback = callback;
}

public void onHideCustomView() {
    fullscreenHost.removeAllViews();
    fullscreenHost.setVisibility(View.GONE);
    webView.setVisibility(View.VISIBLE);
    customViewCallback.onCustomViewHidden();
}
```

Use for: video fullscreen, screen sharing preview, embedded media controls.

## 15. Console and Navigation Logging

```java
webView.setWebChromeClient(new WebChromeClient() {
    @Override
    public boolean onConsoleMessage(ConsoleMessage msg) {
        Log.d("WebConsole", msg.message());
        return true;
    }
});

webView.setWebViewClient(new WebViewClient() {
    @Override
    public void onPageFinished(WebView view, String url) {
        injectTextObserver();
    }
});
```

Use for: spotting JS errors and knowing when to inject observers.

## 16. Local Test Pages With WebViewAssetLoader

For controlled fixtures, use `androidx.webkit:webkit`. As of 2026-06-15, Android Developers lists stable `1.16.0`.

```gradle
dependencies {
    implementation "androidx.webkit:webkit:1.16.0"
}
```

```java
WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
        .build();

webView.setWebViewClient(new WebViewClientCompat() {
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return loader.shouldInterceptRequest(request.getUrl());
    }
});

webView.loadUrl("https://appassets.androidplatform.net/assets/test.html");
```

Use for: reproducible upload/download/audio/text-change pages without relying on Google/ChatGPT UI changes.

## Next Snippets To Add

## 17. Automated Photo Batch Upload Pattern

This testbed now uses the file chooser callback as the automation bridge:

```java
if (autoUploadArmed && !autoUploadUris.isEmpty()) {
    filePathCallback.onReceiveValue(autoUploadUris.toArray(new Uri[0]));
    return true;
}
```

The page still has to open its file input normally. The native app only replaces the final picker result with a known batch of MediaStore image `Uri`s.

## 18. Response Stop Detection Pattern

The stable response detector combines three signals:

- `MutationObserver` sends coarse body text changes into Android.
- Periodic snapshots read the last assistant message and its character length.
- The batch is considered stopped only after the assistant text is stable for 12 seconds and visible stop/upload indicators are gone.

This is intentionally conservative because heavy ChatGPT pages can pause briefly during image processing.

## Next Snippets To Add

- A local fixture page with textarea, file input, blob download, audio tag, WebAudio oscillator, MutationObserver target.
- ADB command recipes for shortcut testing.
- Chrome DevTools remote debugging workflow for this package.
- Audio-output detection via Android `AudioManager` or `dumpsys audio` observation.
