package com.example.webviewtestbed;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "WebViewTestBed";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_WEB_PERMISSION = 1002;
    private static final int REQ_MEDIA_PERMISSION = 1003;
    private static final int MISSION_BATCH_SIZE = 5;
    private static final int MISSION_BATCH_COUNT = 6;
    private static final int DOWNLOAD_MISSION_BATCH_SIZE = 1;
    private static final int DOWNLOAD_MISSION_BATCH_COUNT = 3;
    private static final int ATTACH_MAX_ATTEMPTS = 4;
    private static final int DOWNLOAD_MAX_ATTEMPTS = 4;
    private static final long ATTACH_RETRY_WAIT_MS = 4500L;
    private static final long ATTACH_MENU_PROBE_DELAY_MS = 850L;
    private static final long UPLOAD_SETTLE_MS = 14000L;
    private static final long RESPONSE_STABLE_MS = 12000L;
    private static final long RESPONSE_TIMEOUT_MS = 210000L;
    private static final long DOWNLOAD_PROBE_WAIT_MS = 6500L;
    private static final String MISSION_KIND_PHOTO = "photo-x6";
    private static final String MISSION_KIND_DOWNLOAD = "download-x3";

    private EditText memoEditor;
    private EditText addressInput;
    private LinearLayout addressRow;
    private TextView statusText;
    private WebView webView;
    private FrameLayout customViewHost;

    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingPermissionRequest;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Uri> autoUploadUris = new ArrayList<>();
    private boolean autoUploadArmed;
    private boolean autoUploadDelivered;
    private boolean missionRunning;
    private int missionBatchIndex;
    private ArrayList<PhotoInfo> missionPhotos = new ArrayList<>();
    private BatchRun currentBatch;
    private MissionLogger missionLogger;
    private Snapshot lastMissionSnapshot;
    private String latestMissionExportPath = "";
    private String activeMissionKind = MISSION_KIND_PHOTO;
    private int downloadEventCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppContextHolder.filesDir = getFilesDir();
        WebView.setWebContentsDebuggingEnabled(true);
        buildLayout();
        configureWebView();
        status("Ready. Pick a target below.");
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        int basePadding = dp(10);
        root.setPadding(basePadding, basePadding, basePadding, basePadding);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                view.setPadding(
                        basePadding + insets.getSystemWindowInsetLeft(),
                        basePadding + insets.getSystemWindowInsetTop(),
                        basePadding + insets.getSystemWindowInsetRight(),
                        basePadding + insets.getSystemWindowInsetBottom()
                );
                return insets;
            });
        }
        setContentView(root);
        root.requestApplyInsets();

        memoEditor = new EditText(this);
        memoEditor.setGravity(Gravity.TOP | Gravity.START);
        memoEditor.setHint("memo...");
        memoEditor.setMinLines(4);
        memoEditor.setTextColor(Color.rgb(20, 20, 20));
        memoEditor.setTextSize(15);
        memoEditor.setSingleLine(false);
        memoEditor.setBackgroundColor(Color.WHITE);
        memoEditor.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(memoEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(132)
        ));

        LinearLayout missionRow = new LinearLayout(this);
        missionRow.setOrientation(LinearLayout.HORIZONTAL);
        missionRow.setGravity(Gravity.CENTER);
        missionRow.setPadding(0, dp(4), 0, dp(2));
        root.addView(missionRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        Button runMissionButton = makeButton("RUN PHOTO x6");
        runMissionButton.setOnClickListener(v -> startPhotoMission());
        missionRow.addView(runMissionButton, new LinearLayout.LayoutParams(0, dp(40), 0.95f));

        Button runDownloadMissionButton = makeButton("RUN DL x3");
        runDownloadMissionButton.setOnClickListener(v -> startDownloadMission());
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(0, dp(40), 0.78f);
        downloadParams.setMargins(dp(6), 0, 0, 0);
        missionRow.addView(runDownloadMissionButton, downloadParams);

        Button stopMissionButton = makeButton("STOP");
        stopMissionButton.setOnClickListener(v -> stopPhotoMission("manual-stop"));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(40), 0.45f);
        stopParams.setMargins(dp(6), 0, dp(6), 0);
        missionRow.addView(stopMissionButton, stopParams);

        Button exportMissionButton = makeButton("EXPORT LOG");
        exportMissionButton.setOnClickListener(v -> exportLatestMissionLog());
        missionRow.addView(exportMissionButton, new LinearLayout.LayoutParams(0, dp(40), 0.72f));

        addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setVisibility(View.GONE);
        addressRow.setPadding(0, dp(6), 0, dp(6));
        root.addView(addressRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setTextSize(14);
        addressInput.setSelectAllOnFocus(true);
        addressInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setBackgroundColor(Color.rgb(246, 246, 246));
        addressInput.setPadding(dp(10), 0, dp(10), 0);
        addressInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enterKey) {
                loadUrlFromAddressBar();
                return true;
            }
            return false;
        });
        addressRow.addView(addressInput, new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        ));

        Button goButton = makeButton("GO");
        goButton.setOnClickListener(v -> loadUrlFromAddressBar());
        addressRow.addView(goButton, new LinearLayout.LayoutParams(dp(64), dp(44)));

        customViewHost = new FrameLayout(this);
        customViewHost.setBackgroundColor(Color.BLACK);
        customViewHost.setVisibility(View.GONE);
        root.addView(customViewHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        statusText = new TextView(this);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setTextColor(Color.rgb(70, 70, 70));
        statusText.setTextSize(12);
        statusText.setPadding(0, dp(5), 0, dp(5));
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        root.addView(buttonRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
        ));

        Button googleButton = makeButton("GOOGLE + URL");
        googleButton.setOnClickListener(v -> openTarget("https://www.google.com", true));
        buttonRow.addView(googleButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button chatUrlButton = makeButton("GPT + URL");
        chatUrlButton.setOnClickListener(v -> openTarget("https://chatgpt.com", true));
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        middleParams.setMargins(dp(8), 0, dp(8), 0);
        buttonRow.addView(chatUrlButton, middleParams);

        Button chatCleanButton = makeButton("GPT CLEAN");
        chatCleanButton.setOnClickListener(v -> openTarget("https://chatgpt.com", false));
        buttonRow.addView(chatCleanButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        webView.addJavascriptInterface(new TestBridge(), "AndroidWebViewTestBed");
        webView.setWebViewClient(new TestWebViewClient());
        webView.setWebChromeClient(new TestChromeClient());
        webView.setDownloadListener(new TestDownloadListener());
    }

    private void openTarget(String url, boolean showAddressBar) {
        addressRow.setVisibility(showAddressBar ? View.VISIBLE : View.GONE);
        addressInput.setText(url);
        webView.loadUrl(url);
        status("Loading " + url);
    }

    private void loadUrlFromAddressBar() {
        String input = addressInput.getText().toString().trim();
        if (input.isEmpty()) {
            return;
        }
        String url = input.contains("://") ? input : "https://" + input;
        addressInput.setText(url);
        webView.loadUrl(url);
        status("Loading " + url);
    }

    public void injectPrompt(String prompt) {
        webView.evaluateJavascript(buildPromptInjectionJs(prompt), value -> status("Prompt inject: " + value));
    }

    private void triggerFileInputClick() {
        webView.evaluateJavascript(buildAttachClickJs("manual-file-input"), value -> status("Upload shortcut: " + value));
    }

    public void tapWebView(float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        webView.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        webView.dispatchTouchEvent(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0));
        status(String.format(Locale.US, "Tapped webview %.0f, %.0f", x, y));
    }

    private void startPhotoMission() {
        if (missionRunning) {
            status("Photo mission is already running.");
            return;
        }
        if (!hasPhotoPermission()) {
            status("Requesting photo permission. Choose all photos for the full 6x5 test.");
            requestPhotoPermission();
            return;
        }

        ArrayList<PhotoInfo> photos = queryLatestPhotos(MISSION_BATCH_SIZE * MISSION_BATCH_COUNT);
        if (photos.size() < MISSION_BATCH_SIZE * MISSION_BATCH_COUNT) {
            status("Need 30 readable photos, found " + photos.size() + ". Check photo permission.");
            try {
                MissionLogger shortLogger = new MissionLogger();
                shortLogger.log("mission_blocked", new JSONObject()
                        .put("reason", "not_enough_photos")
                        .put("found", photos.size())
                        .put("needed", MISSION_BATCH_SIZE * MISSION_BATCH_COUNT));
                shortLogger.close();
                latestMissionExportPath = shortLogger.runDir.getAbsolutePath();
            } catch (Exception error) {
                Log.e(TAG, "Could not write blocked mission log", error);
            }
            return;
        }

        activeMissionKind = MISSION_KIND_PHOTO;
        downloadEventCounter = 0;
        missionRunning = true;
        missionBatchIndex = 0;
        missionPhotos = photos;
        lastMissionSnapshot = null;
        currentBatch = null;
        missionLogger = new MissionLogger();
        latestMissionExportPath = missionLogger.runDir.getAbsolutePath();
        missionLog("mission_start", json()
                .put("photoCount", photos.size())
                .put("batchSize", MISSION_BATCH_SIZE)
                .put("batchCount", MISSION_BATCH_COUNT)
                .put("runDir", latestMissionExportPath));
        for (int i = 0; i < photos.size(); i++) {
            PhotoInfo photo = photos.get(i);
            missionLog("photo_selected", json()
                    .put("index", i + 1)
                    .put("name", photo.name)
                    .put("uri", photo.uri.toString())
                    .put("dateMillis", photo.dateMillis)
                    .put("mime", photo.mimeType));
        }
        status("Photo mission started: " + latestMissionExportPath);
        openTarget("https://chatgpt.com", false);
        mainHandler.postDelayed(() -> waitForChatReady(0), 6500);
    }

    private void startDownloadMission() {
        if (missionRunning) {
            status("Mission is already running.");
            return;
        }
        if (!hasPhotoPermission()) {
            status("Requesting photo permission. Choose all photos for the download test.");
            requestPhotoPermission();
            return;
        }

        ArrayList<PhotoInfo> photos = queryLatestPhotos(DOWNLOAD_MISSION_BATCH_SIZE * DOWNLOAD_MISSION_BATCH_COUNT);
        if (photos.size() < DOWNLOAD_MISSION_BATCH_SIZE * DOWNLOAD_MISSION_BATCH_COUNT) {
            status("Need 3 readable photos, found " + photos.size() + ". Check photo permission.");
            try {
                MissionLogger shortLogger = new MissionLogger(MISSION_KIND_DOWNLOAD);
                shortLogger.log("mission_blocked", new JSONObject()
                        .put("reason", "not_enough_photos")
                        .put("found", photos.size())
                        .put("needed", DOWNLOAD_MISSION_BATCH_SIZE * DOWNLOAD_MISSION_BATCH_COUNT));
                shortLogger.close();
                latestMissionExportPath = shortLogger.runDir.getAbsolutePath();
            } catch (Exception error) {
                Log.e(TAG, "Could not write blocked mission log", error);
            }
            return;
        }

        activeMissionKind = MISSION_KIND_DOWNLOAD;
        downloadEventCounter = 0;
        missionRunning = true;
        missionBatchIndex = 0;
        missionPhotos = photos;
        lastMissionSnapshot = null;
        currentBatch = null;
        missionLogger = new MissionLogger(MISSION_KIND_DOWNLOAD);
        latestMissionExportPath = missionLogger.runDir.getAbsolutePath();
        missionLog("mission_start", json()
                .put("kind", activeMissionKind)
                .put("photoCount", photos.size())
                .put("batchSize", DOWNLOAD_MISSION_BATCH_SIZE)
                .put("batchCount", DOWNLOAD_MISSION_BATCH_COUNT)
                .put("runDir", latestMissionExportPath)
                .put("snippetPlan", "file-chooser, DownloadListener, blob-to-base64 bridge, DOM download probe"));
        for (int i = 0; i < photos.size(); i++) {
            PhotoInfo photo = photos.get(i);
            missionLog("photo_selected", json()
                    .put("index", i + 1)
                    .put("name", photo.name)
                    .put("uri", photo.uri.toString())
                    .put("dateMillis", photo.dateMillis)
                    .put("mime", photo.mimeType));
        }
        status("Download mission started: " + latestMissionExportPath);
        openTarget("https://chatgpt.com", false);
        mainHandler.postDelayed(() -> waitForChatReady(0), 6500);
    }

    private void stopPhotoMission(String reason) {
        if (!missionRunning) {
            status("No mission running.");
            return;
        }
        missionLog("mission_stop", json().put("reason", reason).put("batchIndex", missionBatchIndex));
        missionRunning = false;
        autoUploadArmed = false;
        autoUploadUris.clear();
        currentBatch = null;
        if (missionLogger != null) {
            missionLogger.close();
            missionLogger = null;
        }
        status("Photo mission stopped: " + reason);
    }

    private void waitForChatReady(int attempt) {
        if (!missionRunning) {
            return;
        }
        captureSnapshot(snapshot -> {
            missionLog("chat_ready_probe", snapshot.toJson().put("attempt", attempt));
            if (snapshot.editorExists) {
                runNextMissionBatch();
                return;
            }
            if (attempt >= 24) {
                missionLog("mission_blocked", json()
                        .put("reason", "chat_editor_not_found")
                        .put("hint", "Log in to ChatGPT in the WebView, then press the mission button again."));
                stopPhotoMission("chat-editor-not-found");
                return;
            }
            status("Waiting for ChatGPT editor/login... " + (attempt + 1));
            mainHandler.postDelayed(() -> waitForChatReady(attempt + 1), 5000);
        });
    }

    private void runNextMissionBatch() {
        if (!missionRunning) {
            return;
        }
        int totalBatches = isDownloadMission() ? DOWNLOAD_MISSION_BATCH_COUNT : MISSION_BATCH_COUNT;
        int batchSize = isDownloadMission() ? DOWNLOAD_MISSION_BATCH_SIZE : MISSION_BATCH_SIZE;
        if (missionBatchIndex >= totalBatches) {
            missionLog("mission_complete", json()
                    .put("kind", activeMissionKind)
                    .put("completedBatches", missionBatchIndex)
                    .put("runDir", latestMissionExportPath));
            missionRunning = false;
            if (missionLogger != null) {
                missionLogger.close();
                missionLogger = null;
            }
            status("Photo mission complete. Internal log: " + latestMissionExportPath);
            return;
        }

        int start = missionBatchIndex * batchSize;
        ArrayList<PhotoInfo> batchPhotos = new ArrayList<>(missionPhotos.subList(start, start + batchSize));
        BatchStrategy strategy = isDownloadMission()
                ? downloadStrategyForBatch(missionBatchIndex)
                : strategyForBatch(missionBatchIndex);
        String prompt = isDownloadMission()
                ? downloadPromptForBatch(missionBatchIndex, batchPhotos)
                : null;
        currentBatch = prompt == null
                ? new BatchRun(missionBatchIndex + 1, batchPhotos, strategy)
                : new BatchRun(missionBatchIndex + 1, batchPhotos, strategy, prompt);
        missionBatchIndex++;
        missionLog("batch_start", currentBatch.toJson());
        status("Batch " + currentBatch.number + "/" + totalBatches + ": " + strategy.name);

        if (strategy.promptBeforeAttach) {
            injectMissionPrompt(currentBatch.prompt, result -> {
                missionLog("prompt_injected", json().put("batch", currentBatch.number).put("result", result));
                if (!isPromptInjectedResult(result)) {
                    failCurrentBatch("prompt-not-injected", json().put("result", result));
                    return;
                }
                mainHandler.postDelayed(() -> attachCurrentBatch(0), 1200);
            });
        } else {
            attachCurrentBatch(0);
        }
    }

    private void attachCurrentBatch(int attempt) {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        autoUploadUris.clear();
        for (PhotoInfo photo : currentBatch.photos) {
            autoUploadUris.add(photo.uri);
        }
        autoUploadArmed = true;
        autoUploadDelivered = false;
        String attachMode = currentBatch.strategy.attachMode;
        String attemptMode = attempt == 0 ? attachMode : attachMode + "-retry-" + attempt;
        String js = buildAttachClickJs(attemptMode);
        missionLog("attach_attempt", json()
                .put("batch", currentBatch.number)
                .put("attempt", attempt)
                .put("mode", attemptMode)
                .put("uriCount", autoUploadUris.size()));
        webView.evaluateJavascript(buildAttachTargetJs(attempt, attachMode), value -> {
            TargetPoint target = parseTargetPoint(value);
            missionLog("attach_target_probe", json()
                    .put("batch", currentBatch == null ? -1 : currentBatch.number)
                    .put("attempt", attempt)
                    .put("raw", value)
                    .put("target", target.toJson()));
            if (target.found) {
                tapTargetPoint(target, "attach_target_tapped", attempt);
                probeAttachMenuAfterTap(attempt, attachMode, js);
                return;
            }
            webView.evaluateJavascript(js, result -> missionLog("attach_js_fallback_result", json()
                    .put("batch", currentBatch == null ? -1 : currentBatch.number)
                    .put("attempt", attempt)
                    .put("result", result)));
            probeAttachMenuAfterTap(attempt, attachMode, js);
        });
    }

    private void tapTargetPoint(TargetPoint target, String event, int attempt) {
        float x = target.xCss / Math.max(1f, target.viewportWidth) * webView.getWidth();
        float y = target.yCss / Math.max(1f, target.viewportHeight) * webView.getHeight();
        tapWebView(x, y);
        missionLog(event, json()
                .put("batch", currentBatch == null ? -1 : currentBatch.number)
                .put("attempt", attempt)
                .put("x", x)
                .put("y", y)
                .put("target", target.toJson()));
    }

    private void probeAttachMenuAfterTap(int attempt, String attachMode, String fallbackJs) {
        mainHandler.postDelayed(() -> {
            if (!missionRunning || currentBatch == null) {
                return;
            }
            if (autoUploadDelivered) {
                missionLog("attach_menu_probe_skipped", json()
                        .put("batch", currentBatch.number)
                        .put("attempt", attempt)
                        .put("reason", "file-chooser-already-delivered"));
                waitAfterAttachAttempt(attempt);
                return;
            }
            webView.evaluateJavascript(buildUploadMenuTargetJs(attachMode, attempt), value -> {
                TargetPoint target = parseTargetPoint(value);
                missionLog("attach_menu_probe", json()
                        .put("batch", currentBatch == null ? -1 : currentBatch.number)
                        .put("attempt", attempt)
                        .put("mode", attachMode)
                        .put("raw", value)
                        .put("target", target.toJson()));
                if (target.found) {
                    tapTargetPoint(target, "attach_menu_tapped", attempt);
                    waitAfterAttachAttempt(attempt);
                    return;
                }
                webView.evaluateJavascript(fallbackJs, result -> missionLog("attach_menu_js_fallback_result", json()
                        .put("batch", currentBatch == null ? -1 : currentBatch.number)
                        .put("attempt", attempt)
                        .put("result", result)));
                waitAfterAttachAttempt(attempt);
            });
        }, ATTACH_MENU_PROBE_DELAY_MS);
    }

    private void waitAfterAttachAttempt(int attempt) {
        mainHandler.postDelayed(() -> {
            if (!missionRunning || currentBatch == null) {
                return;
            }
            if (!autoUploadDelivered && attempt < ATTACH_MAX_ATTEMPTS - 1) {
                attachCurrentBatch(attempt + 1);
                return;
            }
            if (!autoUploadDelivered) {
                autoUploadArmed = false;
                failCurrentBatch("attach-not-delivered", json()
                        .put("attempts", ATTACH_MAX_ATTEMPTS)
                        .put("uriCount", autoUploadUris.size()));
                return;
            }
            autoUploadArmed = false;
            mainHandler.postDelayed(this::afterAttachmentSettled, UPLOAD_SETTLE_MS);
        }, ATTACH_RETRY_WAIT_MS);
    }

    private void afterAttachmentSettled() {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        missionLog("upload_settle_wait_done", json()
                .put("batch", currentBatch.number)
                .put("delivered", autoUploadDelivered)
                .put("settleMs", UPLOAD_SETTLE_MS));
        if (!autoUploadDelivered) {
            failCurrentBatch("attach-not-delivered-after-settle", json()
                    .put("batch", currentBatch.number));
            return;
        }
        if (!currentBatch.strategy.promptBeforeAttach) {
            injectMissionPrompt(currentBatch.prompt, result -> {
                missionLog("prompt_injected", json().put("batch", currentBatch.number).put("result", result));
                if (!isPromptInjectedResult(result)) {
                    failCurrentBatch("prompt-not-injected", json().put("result", result));
                    return;
                }
                mainHandler.postDelayed(() -> sendCurrentBatch(0, currentBatch.strategy.sendMode), 1400);
            });
        } else {
            mainHandler.postDelayed(() -> sendCurrentBatch(0, currentBatch.strategy.sendMode), 1400);
        }
    }

    private void sendCurrentBatch(int attempt, String sendMode) {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        captureSnapshot(snapshot -> {
            if (!missionRunning || currentBatch == null) {
                return;
            }
            if (attempt == 0) {
                currentBatch.preSendSnapshot = snapshot;
            }
            missionLog("send_probe", snapshot.toJson()
                    .put("batch", currentBatch.number)
                    .put("attempt", attempt)
                    .put("mode", sendMode));
            String js = buildSendJs(sendMode);
            webView.evaluateJavascript(js, result -> {
                missionLog("send_attempt", json()
                        .put("batch", currentBatch.number)
                        .put("attempt", attempt)
                        .put("mode", sendMode)
                        .put("result", result));
                boolean clickedSend = result != null && result.contains("clicked-send-button");
                boolean enterDispatched = result != null && result.contains("enter-dispatched");
                if (!clickedSend && attempt < 18) {
                    String nextMode = (enterDispatched || attempt >= 5) ? "button" : sendMode;
                    long retryDelayMs = enterDispatched ? 1200L : 3000L;
                    mainHandler.postDelayed(() -> sendCurrentBatch(attempt + 1, nextMode), retryDelayMs);
                    return;
                }
                if (!clickedSend) {
                    failCurrentBatch("send-not-started", json()
                            .put("attempts", attempt + 1)
                            .put("mode", sendMode)
                            .put("lastResult", result));
                    return;
                }
                currentBatch.sentAtMs = System.currentTimeMillis();
                currentBatch.lastResponseChangeAtMs = currentBatch.sentAtMs;
                currentBatch.lastResponseText = "";
                mainHandler.postDelayed(() -> monitorResponseStable(0), 3500);
            });
        });
    }

    private void monitorResponseStable(int poll) {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        captureSnapshot(snapshot -> {
            if (!missionRunning || currentBatch == null) {
                return;
            }
            lastMissionSnapshot = snapshot;
            String responseText = snapshot.lastAssistantText;
            if (responseText.isEmpty()) {
                responseText = snapshot.bodyTail;
            }
            if (!responseText.equals(currentBatch.lastResponseText)) {
                currentBatch.lastResponseText = responseText;
                currentBatch.lastResponseChangeAtMs = System.currentTimeMillis();
                missionLog("response_changed", snapshot.toJson()
                        .put("batch", currentBatch.number)
                        .put("poll", poll)
                        .put("responseChars", responseText.length()));
            } else if (poll % 5 == 0) {
                missionLog("response_poll", snapshot.toJson()
                        .put("batch", currentBatch.number)
                        .put("poll", poll)
                        .put("stableForMs", System.currentTimeMillis() - currentBatch.lastResponseChangeAtMs));
            }

            long now = System.currentTimeMillis();
            long stableFor = now - currentBatch.lastResponseChangeAtMs;
            long elapsed = now - currentBatch.sentAtMs;
            boolean assistantCountAdvanced = snapshot.assistantCount > currentBatch.preAssistantCount();
            boolean hasNewAssistant = (assistantCountAdvanced && snapshot.lastAssistantLen > 20)
                    || snapshot.lastAssistantLen > currentBatch.preAssistantLen() + 20
                    || responseText.length() > currentBatch.preBodyTailLen() + 20;
            boolean stable = hasNewAssistant
                    && stableFor >= RESPONSE_STABLE_MS
                    && !snapshot.stopVisible
                    && !snapshot.uploadingVisible;

            if (stable || elapsed >= RESPONSE_TIMEOUT_MS) {
                finishCurrentBatch(stable ? "stable" : "timeout", snapshot, stableFor, elapsed);
                return;
            }
            mainHandler.postDelayed(() -> monitorResponseStable(poll + 1), 2200);
        });
    }

    private void finishCurrentBatch(String reason, Snapshot snapshot, long stableFor, long elapsed) {
        if (currentBatch == null) {
            return;
        }
        currentBatch.finishedAtMs = System.currentTimeMillis();
        currentBatch.finishReason = reason;
        currentBatch.finalSnapshot = snapshot;
        missionLog("batch_finish", currentBatch.toJson()
                .put("reason", reason)
                .put("stableForMs", stableFor)
                .put("elapsedMs", elapsed)
                .put("finalAssistantChars", snapshot.lastAssistantLen));
        if (isDownloadMission() && ("stable".equals(reason) || shouldProbeDownloadAfterTimeout(snapshot))) {
            currentBatch.downloadStartCounter = downloadEventCounter;
            missionLog("download_phase_start", json()
                    .put("batch", currentBatch.number)
                    .put("strategy", currentBatch.strategy.name)
                    .put("finishReason", reason)
                    .put("downloadStartCounter", currentBatch.downloadStartCounter)
                    .put("snippetOrder", "download DOM click -> image/blob extraction -> assistant text save -> broad button click"));
            status("Batch " + currentBatch.number + " finished (" + reason + "). Probing download.");
            mainHandler.postDelayed(() -> attemptBatchDownload(0), 2500);
            return;
        }
        completeCurrentBatchAndContinue();
    }

    private boolean shouldProbeDownloadAfterTimeout(Snapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String text = (snapshot.lastAssistantText + "\n" + snapshot.bodyTail).toLowerCase(Locale.ROOT);
        return snapshot.lastAssistantLen > 0
                || text.contains("download")
                || text.contains("save")
                || text.contains("다운로드")
                || text.contains("받을")
                || text.contains("저장")
                || text.contains("webview_download_test");
    }

    private void attemptBatchDownload(int attempt) {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        String js = buildDownloadProbeJs(attempt, currentBatch.strategy.name, currentBatch.number);
        missionLog("download_probe", json()
                .put("batch", currentBatch.number)
                .put("attempt", attempt)
                .put("eventCounterBefore", downloadEventCounter));
        webView.evaluateJavascript(js, result -> {
            missionLog("download_probe_result", json()
                    .put("batch", currentBatch == null ? -1 : currentBatch.number)
                    .put("attempt", attempt)
                    .put("result", result)
                    .put("eventCounterAfterProbe", downloadEventCounter));
            mainHandler.postDelayed(() -> checkBatchDownloadResult(attempt, result), DOWNLOAD_PROBE_WAIT_MS);
        });
    }

    private void checkBatchDownloadResult(int attempt, String result) {
        if (!missionRunning || currentBatch == null) {
            return;
        }
        boolean observed = downloadEventCounter > currentBatch.downloadStartCounter;
        if (observed || attempt >= DOWNLOAD_MAX_ATTEMPTS - 1) {
            currentBatch.downloadAtMs = System.currentTimeMillis();
            currentBatch.downloadStrategy = "attempt-" + attempt;
            currentBatch.downloadResult = observed
                    ? "observed: " + result
                    : "not-observed: " + result;
            missionLog("download_phase_finish", currentBatch.toJson()
                    .put("observed", observed)
                    .put("attempt", attempt)
                    .put("downloadEventCounter", downloadEventCounter));
            completeCurrentBatchAndContinue();
            return;
        }
        mainHandler.postDelayed(() -> attemptBatchDownload(attempt + 1), 1800);
    }

    private void completeCurrentBatchAndContinue() {
        if (currentBatch == null) {
            return;
        }
        if (missionLogger != null) {
            missionLogger.appendResponse(currentBatch);
        }
        status("Batch " + currentBatch.number + " done: " + currentBatch.finishReason);
        currentBatch = null;
        mainHandler.postDelayed(this::runNextMissionBatch, 5500);
    }

    private void failCurrentBatch(String reason, JSONObject details) {
        if (!missionRunning || currentBatch == null) {
            stopPhotoMission(reason);
            return;
        }
        captureSnapshot(snapshot -> {
            if (currentBatch == null) {
                stopPhotoMission(reason);
                return;
            }
            currentBatch.finishedAtMs = System.currentTimeMillis();
            currentBatch.finishReason = reason;
            currentBatch.finalSnapshot = snapshot;
            missionLog("batch_failed", currentBatch.toJson()
                    .put("reason", reason)
                    .put("details", details == null ? new JSONObject() : details)
                    .put("finalBodyChars", snapshot.bodyLen)
                    .put("finalAssistantChars", snapshot.lastAssistantLen));
            if (missionLogger != null) {
                missionLogger.appendResponse(currentBatch);
            }
            stopPhotoMission(reason);
        });
    }

    private void injectMissionPrompt(String prompt, ValueCallback<String> callback) {
        webView.evaluateJavascript(buildPromptInjectionJs(prompt), callback);
    }

    private boolean isPromptInjectedResult(String result) {
        return result != null && result.contains("prompt-injected");
    }

    private String buildPromptInjectionJs(String prompt) {
        String escaped = jsTemplate(prompt);
        return "(function(){"
                + "const text=`" + escaped + "`;"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "function pickEditor(){const selectors=['#prompt-textarea','[data-testid=\"composer-text-input\"]','textarea','[contenteditable=\"true\"]','[role=\"textbox\"]'];for(const q of selectors){const list=[...document.querySelectorAll(q)].filter(visible);if(list.length){return list[list.length-1];}}return null;}"
                + "function fire(el,type){try{el.dispatchEvent(new InputEvent(type,{bubbles:true,cancelable:true,inputType:'insertText',data:text}));}catch(e){el.dispatchEvent(new Event(type,{bubbles:true,cancelable:true}));}}"
                + "const el=pickEditor();"
                + "if(!el){return JSON.stringify({status:'no-editor'});}"
                + "el.focus();"
                + "if('value' in el){let proto=Object.getPrototypeOf(el);let desc=null;while(proto&&!desc){desc=Object.getOwnPropertyDescriptor(proto,'value');proto=Object.getPrototypeOf(proto);}if(desc&&desc.set){desc.set.call(el,text);}else{el.value=text;}fire(el,'beforeinput');fire(el,'input');el.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "else{const sel=window.getSelection();const range=document.createRange();el.innerHTML='';range.selectNodeContents(el);range.collapse(true);sel.removeAllRanges();sel.addRange(range);fire(el,'beforeinput');let inserted=false;try{inserted=document.execCommand&&document.execCommand('insertText',false,text);}catch(e){}if(!inserted){el.textContent=text;}fire(el,'input');el.dispatchEvent(new KeyboardEvent('keyup',{key:' ',bubbles:true}));}"
                + "const now=('value' in el)?el.value:(el.innerText||el.textContent||'');"
                + "const ok=now.length>0&&(now.indexOf(text.slice(0,Math.min(30,text.length)))>=0||text.indexOf(now.slice(0,Math.min(30,now.length)))>=0);"
                + "return JSON.stringify({status:ok?'prompt-injected':'prompt-empty',tag:el.tagName,role:el.getAttribute('role')||'',textLen:text.length,editorTextLen:now.length});"
                + "})();";
    }

    private String buildAttachClickJs(String mode) {
        String escapedMode = jsString(mode);
        return "(function(){"
                + "const mode='" + escapedMode + "';"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}"
                + "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const words=/attach|upload|file|paperclip|photo|image|add|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uCD94\\uAC00/i;"
                + "const bad=/skip|content|login|sign in|voice|mic|dictation|extension|open image|\\bimage\\s+\\d+\\s*\\/\\s*\\d+\\b|\\.(jpe?g|png|webp|gif)\\b|\\uC774\\uBBF8\\uC9C0\\s*\\d+\\s*\\/\\s*\\d+|\\uC5F4\\uAE30\\s*:|\\uCF58\\uD150\\uCE20|\\uAC74\\uB108\\uB6F0|\\uB85C\\uADF8\\uC778|\\uD655\\uC7A5|\\uC74C\\uC131|\\uB9C8\\uC774\\uD06C/i;"
                + "const inputs=[...document.querySelectorAll('input[type=\"file\"]')];"
                + "if((mode.indexOf('direct-input')>=0||mode.indexOf('file-input')>=0)&&inputs.length){inputs[inputs.length-1].click();return 'clicked-file-input-js';}"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitem\"],label')].filter(visible).filter(el=>!el.disabled);"
                + "let hit=controls.find(el=>words.test(label(el))&&!bad.test(label(el)));"
                + "if(!hit){"
                + " const editor=document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
                + " if(editor){const er=editor.getBoundingClientRect();hit=controls.filter(el=>!el.disabled).map(el=>({el,r:el.getBoundingClientRect()}))"
                + " .filter(x=>x.r.top<er.bottom+100&&x.r.bottom>er.top-120&&x.r.left>=0&&x.r.left<er.left+220&&!bad.test(label(x.el))).sort((a,b)=>a.r.left-b.r.left)[0]?.el;}"
                + "}"
                + "if(hit){hit.click();return 'clicked-attach-control';}"
                + "if(inputs.length){inputs[inputs.length-1].click();return 'clicked-file-input-js-fallback';}"
                + "return 'no-attach-control';"
                + "})();";
    }

    private String buildAttachTargetJs(int attempt, String mode) {
        String escapedMode = jsString(mode);
        return "(function(){"
                + "const attempt=" + attempt + ";"
                + "const mode='" + escapedMode + "';"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}"
                + "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const words=/attach|upload|file|paperclip|photo|image|add|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uCD94\\uAC00/i;"
                + "const bad=/skip|content|login|sign in|voice|mic|dictation|extension|open image|\\bimage\\s+\\d+\\s*\\/\\s*\\d+\\b|\\.(jpe?g|png|webp|gif)\\b|\\uC774\\uBBF8\\uC9C0\\s*\\d+\\s*\\/\\s*\\d+|\\uC5F4\\uAE30\\s*:|\\uCF58\\uD150\\uCE20|\\uAC74\\uB108\\uB6F0|\\uB85C\\uADF8\\uC778|\\uD655\\uC7A5|\\uC74C\\uC131|\\uB9C8\\uC774\\uD06C/i;"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],label')].filter(visible).filter(el=>!el.disabled);"
                + "const editor=document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
                + "const rows=controls.map(el=>({el,r:el.getBoundingClientRect(),label:label(el)})).filter(x=>x.r.top>=0&&!bad.test(x.label)).sort((a,b)=>a.r.top-b.r.top||a.r.left-b.r.left);"
                + "const wordRows=rows.filter(x=>words.test(x.label));"
                + "let nearRows=[];"
                + "if(editor){const er=editor.getBoundingClientRect();nearRows=rows.filter(x=>x.r.top<er.bottom+110&&x.r.bottom>er.top-140&&x.r.left>=0&&x.r.left<er.left+240&&x.r.right<er.left+320);}"
                + "let hit=null;"
                + "if(attempt===0){hit=(nearRows.find(x=>words.test(x.label))||nearRows[0]||wordRows[0]);}"
                + "else if(attempt===1){hit=(wordRows[0]||nearRows.find(x=>words.test(x.label))||nearRows[0]);}"
                + "else{hit=(wordRows[0]||nearRows[0]);}"
                + "function simple(x){return {tag:x.el.tagName,label:x.label.slice(0,90),x:Math.round(x.r.left),y:Math.round(x.r.top),w:Math.round(x.r.width),h:Math.round(x.r.height)};}"
                + "const candidates=(wordRows.length?wordRows:nearRows.length?nearRows:rows).slice(0,10).map(simple);"
                + "if(!hit){return JSON.stringify({phase:'primary',found:false,vw:window.innerWidth,vh:window.innerHeight,candidates:candidates});}"
                + "const r=hit.r;"
                + "return JSON.stringify({phase:'primary',found:true,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight,label:hit.label,tag:hit.el.tagName,candidates:candidates});"
                + "})();";
    }

    private String buildUploadMenuTargetJs(String mode, int attempt) {
        String escapedMode = jsString(mode);
        return "(function(){"
                + "const mode='" + escapedMode + "';"
                + "const attempt=" + attempt + ";"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||'').trim();}"
                + "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "function norm(s){return (s||'').replace(/\\s+/g,' ').trim();}"
                + "function host(el){return el.closest('button,[role=\"button\"],[role=\"menuitem\"],label,[tabindex]')||el;}"
                + "const words=/upload|file|photo|image|browse|computer|device|\\uCCA8\\uBD80|\\uD30C\\uC77C|\\uC5C5\\uB85C\\uB4DC|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uAE30\\uAE30/i;"
                + "const photoExact=/^(photo|photos|image|images|add photos?|upload photo|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0)$/i;"
                + "const fileExact=/^(file|files|browse files?|upload file|upload from computer|\\uD30C\\uC77C)$/i;"
                + "const bad=/skip|content|login|sign in|voice|mic|dictation|extension|open image|\\bimage\\s+\\d+\\s*\\/\\s*\\d+\\b|\\.(jpe?g|png|webp|gif)\\b|\\uC774\\uBBF8\\uC9C0\\s*\\d+\\s*\\/\\s*\\d+|\\uC5F4\\uAE30\\s*:|\\uCF58\\uD150\\uCE20|\\uAC74\\uB108\\uB6F0|\\uB85C\\uADF8\\uC778|\\uD655\\uC7A5|\\uC74C\\uC131|\\uB9C8\\uC774\\uD06C/i;"
                + "const raw=[...document.querySelectorAll('button,[role=\"button\"],[role=\"menuitem\"],label,[tabindex],div,li,span')];"
                + "const seen=new Set();"
                + "const rows=[];"
                + "for(const rawEl of raw){const el=host(rawEl);if(seen.has(el)||!visible(el)||el.disabled){continue;}seen.add(el);const l=norm(label(rawEl)||label(el));const r=el.getBoundingClientRect();const compact=r.width>=16&&r.height>=16&&r.width<=420&&r.height<=120&&l.length>0&&l.length<=90;if(r.top>=0&&!bad.test(l)&&(compact||photoExact.test(l)||fileExact.test(l))){rows.push({el,r,label:l,rawTag:rawEl.tagName});}}"
                + "rows.sort((a,b)=>a.r.top-b.r.top||a.r.left-b.r.left);"
                + "const photoRows=rows.filter(x=>photoExact.test(x.label));"
                + "const fileRows=rows.filter(x=>fileExact.test(x.label));"
                + "const uploadRows=rows.filter(x=>words.test(x.label)&&!/\\uD30C\\uC77C\\s*\\uCD94\\uAC00\\s*\\uBC0F\\s*\\uAE30\\uD0C0|add files? and more/i.test(x.label));"
                + "let hit=null;"
                + "if(mode.indexOf('file-menu')>=0){hit=fileRows[0]||photoRows[0]||uploadRows[0]||null;}"
                + "else if(mode.indexOf('photo-menu')>=0){hit=photoRows[0]||fileRows[0]||uploadRows[0]||null;}"
                + "else{hit=photoRows[0]||fileRows[0]||uploadRows[0]||null;}"
                + "function simple(x){return {tag:x.el.tagName,rawTag:x.rawTag,label:x.label.slice(0,90),x:Math.round(x.r.left),y:Math.round(x.r.top),w:Math.round(x.r.width),h:Math.round(x.r.height)};}"
                + "const candidates=(photoRows.concat(fileRows).concat(uploadRows).concat(rows)).slice(0,14).map(simple);"
                + "const inputCount=document.querySelectorAll('input[type=\"file\"]').length;"
                + "if(!hit){return JSON.stringify({phase:'menu',found:false,vw:window.innerWidth,vh:window.innerHeight,inputCount:inputCount,candidates:candidates});}"
                + "const r=hit.r;"
                + "return JSON.stringify({phase:'menu',found:true,x:(r.left+r.right)/2,y:(r.top+r.bottom)/2,vw:window.innerWidth,vh:window.innerHeight,label:hit.label,tag:hit.el.tagName,rawTag:hit.rawTag,inputCount:inputCount,candidates:candidates});"
                + "})();";
    }

    private TargetPoint parseTargetPoint(String value) {
        try {
            JSONObject object = new JSONObject(unwrapJsString(value));
            return TargetPoint.fromJson(object);
        } catch (Exception error) {
            missionLog("target_parse_error", json().put("raw", value).put("error", error.getMessage()));
            return new TargetPoint();
        }
    }

    private String buildSendJs(String mode) {
        String escapedMode = jsString(mode);
        return "(function(){"
                + "const mode='" + escapedMode + "';"
                + "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const editor=document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
                + "if(mode==='enter'&&editor){editor.focus();editor.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',which:13,keyCode:13,bubbles:true,cancelable:true}));editor.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',which:13,keyCode:13,bubbles:true,cancelable:true}));return 'enter-dispatched';}"
                + "const buttons=[...document.querySelectorAll('button')].filter(visible);"
                + "let btn=buttons.find(b=>!b.disabled&&(b.getAttribute('data-testid')==='send-button'||/send|보내기|전송/i.test(b.innerText||b.ariaLabel||b.title||b.getAttribute('aria-label')||'')));"
                + "if(!btn){btn=buttons.filter(b=>!b.disabled).map(b=>({b,r:b.getBoundingClientRect(),label:(b.innerText||b.ariaLabel||b.title||b.getAttribute('aria-label')||'')}))"
                + ".filter(x=>x.r.bottom>window.innerHeight*0.55&&x.r.right>window.innerWidth*0.55).sort((a,b)=>b.r.right-a.r.right)[0]?.b;}"
                + "if(btn){btn.click();return 'clicked-send-button';}"
                + "return 'no-enabled-send';"
                + "})();";
    }

    private String buildDownloadProbeJs(int attempt, String strategyName, int batchNumber) {
        String safeStrategy = jsString(strategyName);
        String baseName = "webview-" + safeFileName(strategyName, "download") + "-batch-" + batchNumber;
        String safeBaseName = jsString(baseName);
        return "(function(){"
                + "const attempt=" + attempt + ";"
                + "const strategy='" + safeStrategy + "';"
                + "const baseName='" + safeBaseName + "';"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.value||el.getAttribute('aria-label')||el.download||'').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "function extMime(kind){if(strategy.indexOf('redrawImage')>=0){return 'image/png';}return 'text/plain';}"
                + "function assistantText(){const nodes=[...document.querySelectorAll('[data-message-author-role=\"assistant\"], article')];const texts=nodes.map(n=>(n.innerText||'').trim()).filter(Boolean);return texts.length?texts[texts.length-1]:(document.body?document.body.innerText:'');}"
                + "function dataText(text){return 'data:text/plain;base64,'+btoa(unescape(encodeURIComponent(text||'')));}"
                + "function saveUrl(url,name,mime){try{if(!url){return 'no-url';}if(url.startsWith('blob:')){fetch(url).then(r=>r.blob()).then(b=>{const reader=new FileReader();reader.onloadend=()=>AndroidWebViewTestBed.saveBase64File(reader.result,name,b.type||mime||'application/octet-stream');reader.readAsDataURL(b);}).catch(e=>AndroidWebViewTestBed.onEvent('download-blob-error',String(e)));return 'blob-save-started:'+name;}if(url.startsWith('data:')){AndroidWebViewTestBed.saveBase64File(url,name,mime||'application/octet-stream');return 'data-save-started:'+name;}if(/^https?:/i.test(url)){AndroidWebViewTestBed.requestHttpDownload(url,name,mime||'application/octet-stream');return 'http-download-requested:'+name;}return 'unsupported-url:'+url.slice(0,32);}catch(e){AndroidWebViewTestBed.onEvent('download-save-url-error',String(e));return 'save-url-error:'+e;}}"
                + "const wantsImage=strategy.indexOf('redrawImage')>=0;"
                + "const downloadWords=wantsImage?/download|save|export|open image|view image|\\uB2E4\\uC6B4\\uB85C\\uB4DC|\\uC800\\uC7A5|\\uBC1B\\uAE30|\\uB0B4\\uBCF4\\uB0B4\\uAE30|\\uC774\\uBBF8\\uC9C0\\s*\\uC5F4\\uAE30/i:/download|save|export|txt|text|file|\\uB2E4\\uC6B4\\uB85C\\uB4DC|\\uC800\\uC7A5|\\uBC1B\\uAE30|\\uB0B4\\uBCF4\\uB0B4\\uAE30|\\uD30C\\uC77C|\\uD14D\\uC2A4\\uD2B8/i;"
                + "const imageControl=/open image|view image|\\uC774\\uBBF8\\uC9C0\\s*\\uC5F4\\uAE30|\\.(png|jpe?g|webp|gif)(\\?|#|$)/i;"
                + "const nonDownloadControl=/add files?|attach|upload|camera|\\uD30C\\uC77C\\s*\\uCD94\\uAC00|\\uCCA8\\uBD80|\\uC5C5\\uB85C\\uB4DC|\\uCE74\\uBA54\\uB77C|\\uC0AC\\uC9C4$|\\uC774\\uBBF8\\uC9C0\\s*\\uB9CC\\uB4E4\\uAE30|\\uC2EC\\uCE35\\s*\\uB9AC\\uC11C\\uCE58|\\uC6F9\\s*\\uAC80\\uC0C9/i;"
                + "if(attempt===0){"
                + " const nodes=[...document.querySelectorAll('a,button,[role=\"button\"],[download]')].filter(visible).map(el=>({el,txt:label(el),href:el.href||el.getAttribute('href')||'',r:el.getBoundingClientRect()})).filter(x=>!nonDownloadControl.test(x.txt));"
                + " const link=nodes.find(x=>x.href&&!( !wantsImage&&imageControl.test((x.txt||'')+' '+x.href) )&&(x.el.download||/^blob:|^data:|\\.(png|jpe?g|webp|gif|txt|md|pdf)(\\?|#|$)/i.test(x.href)||downloadWords.test(x.txt)));"
                + " if(link){const name=(link.el.download||baseName+(wantsImage?'.png':'.txt')); if(/^blob:|^data:/i.test(link.href)||/\\.(png|jpe?g|webp|gif|txt|md|pdf)(\\?|#|$)/i.test(link.href)){return saveUrl(link.href,name,extMime(strategy));} link.el.click(); return 'clicked-download-link:'+link.txt.slice(0,60);}"
                + " const btn=nodes.find(x=>!x.href&&downloadWords.test(x.txt)&&!( !wantsImage&&imageControl.test(x.txt) ));"
                + " if(btn){btn.el.click();return 'clicked-download-button:'+btn.txt.slice(0,60);}"
                + " return 'no-download-control';"
                + "}"
                + "if(attempt===1){"
                + " if(!wantsImage){return 'skip-image-source-for-text-strategy';}"
                + " const imgs=[...document.images].filter(visible).map(img=>({img,r:img.getBoundingClientRect(),src:img.currentSrc||img.src||'',area:img.naturalWidth*img.naturalHeight})).filter(x=>x.area>9000&&!/avatar|icon|logo/i.test(x.src)).sort((a,b)=>b.area-a.area);"
                + " if(imgs.length){return saveUrl(imgs[0].src,baseName+'.png','image/png');}"
                + " const links=[...document.querySelectorAll('a')].map(a=>a.href||'').filter(h=>/^blob:|^data:image|\\.(png|jpe?g|webp|gif)(\\?|#|$)/i.test(h));"
                + " if(links.length){return saveUrl(links[0],baseName+'.png','image/png');}"
                + " return 'no-image-source';"
                + "}"
                + "if(attempt===2){"
                + " const text='Strategy: '+strategy+'\\n\\n'+assistantText();"
                + " AndroidWebViewTestBed.saveBase64File(dataText(text),baseName+'.txt','text/plain');"
                + " return 'assistant-text-save-started:'+text.length;"
                + "}"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],a')].filter(visible).map(el=>({el,txt:label(el),href:el.href||el.getAttribute('href')||'',r:el.getBoundingClientRect()}));"
                + "const broad=controls.find(x=>!nonDownloadControl.test(x.txt)&&/download|save|copy|share|more|\\uB2E4\\uC6B4\\uB85C\\uB4DC|\\uC800\\uC7A5|\\uBCF5\\uC0AC|\\uACF5\\uC720|\\uB354\\s*\\uB9CE\\uC740/i.test(x.txt));"
                + "if(broad){if(broad.href){return saveUrl(broad.href,baseName+'.bin','application/octet-stream');}broad.el.click();return 'clicked-broad-control:'+broad.txt.slice(0,60);}"
                + "return 'no-broad-control';"
                + "})();";
    }

    private void captureSnapshot(SnapshotCallback callback) {
        webView.evaluateJavascript(buildSnapshotJs(), value -> callback.onSnapshot(parseSnapshot(value)));
    }

    private String buildSnapshotJs() {
        return "(function(){"
                + "function text(el){return (el&&el.innerText?el.innerText:'').trim();}"
                + "function visible(el){const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const bodyText=document.body?document.body.innerText:'';"
                + "const assistants=[...document.querySelectorAll('[data-message-author-role=\"assistant\"], article')].map(text).filter(Boolean);"
                + "const lastAssistant=assistants.length?assistants[assistants.length-1]:'';"
                + "const editor=!!document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
                + "const buttons=[...document.querySelectorAll('button')].filter(visible);"
                + "const stopVisible=buttons.some(b=>/stop|중지|정지/i.test(b.innerText||b.ariaLabel||b.title||b.getAttribute('aria-label')||''));"
                + "const uploading=/uploading|첨부 중|업로드|upload failed|파일을 처리/i.test(bodyText);"
                + "return JSON.stringify({href:location.href,title:document.title,bodyLen:bodyText.length,bodyTail:bodyText.slice(-2400),assistantCount:assistants.length,lastAssistant:lastAssistant,lastAssistantLen:lastAssistant.length,editorExists:editor,stopVisible:stopVisible,uploadingVisible:uploading,ts:Date.now()});"
                + "})();";
    }

    private Snapshot parseSnapshot(String value) {
        try {
            String jsonText = unwrapJsString(value);
            JSONObject object = new JSONObject(jsonText);
            return Snapshot.fromJson(object);
        } catch (Exception error) {
            missionLog("snapshot_parse_error", json()
                    .put("raw", value)
                    .put("error", error.getMessage()));
            return new Snapshot();
        }
    }

    private BatchStrategy strategyForBatch(int zeroIndex) {
        switch (zeroIndex % 6) {
            case 0:
                return new BatchStrategy("fileInput_promptAfter_button", "file-input", false, "button");
            case 1:
                return new BatchStrategy("photoMenu_promptAfter_button", "photo-menu", false, "button");
            case 2:
                return new BatchStrategy("fileInput_promptBefore_button", "file-input", true, "button");
            case 3:
                return new BatchStrategy("fileMenu_promptBefore_button", "file-menu", true, "button");
            case 4:
                return new BatchStrategy("fileInput_promptAfter_enterThenFallback", "file-input", false, "enter");
            default:
                return new BatchStrategy("directInput_promptAfter_enterThenFallback", "direct-input-js", false, "enter");
        }
    }

    private boolean isDownloadMission() {
        return MISSION_KIND_DOWNLOAD.equals(activeMissionKind);
    }

    private BatchStrategy downloadStrategyForBatch(int zeroIndex) {
        switch (zeroIndex % 3) {
            case 0:
                return new BatchStrategy("redrawImage_photoMenu_downloadProbe", "photo-menu", false, "button");
            case 1:
                return new BatchStrategy("longText_fileInput_nativeTextSave", "file-input", false, "button");
            default:
                return new BatchStrategy("downloadableText_fileMenu_blobBridge", "file-menu", false, "button");
        }
    }

    private String downloadPromptForBatch(int zeroIndex, ArrayList<PhotoInfo> photos) {
        String photoName = photos.isEmpty() ? "latest photo" : photos.get(0).name;
        switch (zeroIndex % 3) {
            case 0:
                return "첨부한 최신 사진 1장(" + photoName + ")을 참고해서 원본과 다른 새 그림을 생성해 주세요. "
                        + "사진의 핵심 주제와 분위기는 살리되 그대로 복사하지 말고 새 일러스트처럼 다시 그려 주세요. "
                        + "완성 이미지가 나오면 제가 받을 수 있도록 다운로드 버튼이나 다운로드 링크를 제공해 주세요. "
                        + "설명은 짧게 하고, 이미지를 받는 동작을 WebView 자동화가 테스트할 수 있게 해 주세요.";
            case 1:
                return "첨부한 최신 사진 1장(" + photoName + ")을 보고 한국어로 긴 글을 작성해 주세요. "
                        + "사진 속 대상, 배경, 글자, 추정되는 상황, 활용 아이디어를 포함해서 1600자 이상으로 길게 써 주세요. "
                        + "파일을 만들 필요는 없고 본문에 충분히 긴 텍스트로 답해 주세요. "
                        + "WebView 자동화는 응답 텍스트를 감지한 뒤 자체 다운로드 저장 전략을 테스트합니다.";
            default:
                return "첨부한 최신 사진 1장(" + photoName + ")을 바탕으로 한국어 설명문을 1600자 이상 작성해 주세요. "
                        + "그리고 그 텍스트를 내려받을 수 있도록 txt 파일이나 다운로드 링크를 만들어 주세요. "
                        + "파일명은 webview_download_test.txt 로 해 주세요. "
                        + "가능하면 ChatGPT 화면 안에서 다운로드 버튼이나 링크가 보이게 해 주세요.";
        }
    }

    private boolean hasPhotoPermission() {
        if (Build.VERSION.SDK_INT >= 34) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPhotoPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 34) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        requestPermissions(permissions.toArray(new String[0]), REQ_MEDIA_PERMISSION);
    }

    private ArrayList<PhotoInfo> queryLatestPhotos(int limit) {
        ArrayList<PhotoInfo> photos = new ArrayList<>();
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE
        };
        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC, "
                + MediaStore.Images.Media.DATE_ADDED + " DESC, "
                + MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor == null) {
                missionLog("photo_query_empty_cursor", json());
                return photos;
            }
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
            int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED);
            int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
            while (cursor.moveToNext() && photos.size() < limit) {
                long id = cursor.getLong(idCol);
                long dateTaken = cursor.getLong(dateTakenCol);
                long dateAdded = cursor.getLong(dateAddedCol) * 1000L;
                long dateModified = cursor.getLong(dateModifiedCol) * 1000L;
                long dateMillis = dateTaken > 0 ? dateTaken : Math.max(dateAdded, dateModified);
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                photos.add(new PhotoInfo(
                        uri,
                        cursor.getString(nameCol),
                        cursor.getString(mimeCol),
                        dateMillis
                ));
            }
        } catch (Exception error) {
            missionLog("photo_query_error", json().put("error", error.getMessage()));
            Log.e(TAG, "Photo query failed", error);
        }
        return photos;
    }

    private void exportLatestMissionLog() {
        if (missionLogger != null) {
            missionLogger.flush();
        }
        File sourceDir;
        if (latestMissionExportPath == null || latestMissionExportPath.isEmpty()) {
            sourceDir = newestMissionDir();
        } else {
            sourceDir = new File(latestMissionExportPath);
        }
        if (sourceDir == null || !sourceDir.exists()) {
            status("No mission log to export.");
            return;
        }
        try {
            String name = "webview-photo-mission-" + sourceDir.getName() + ".txt";
            String content = readFile(new File(sourceDir, "responses.md"))
                    + "\n\n--- events.jsonl ---\n"
                    + readFile(new File(sourceDir, "events.jsonl"));
            Uri uri = writeTextToDownloads(name, content, "text/plain");
            status("Mission log exported: " + uri);
            Toast.makeText(this, "Mission log exported to Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            status("Export failed: " + error.getMessage());
            Log.e(TAG, "Mission log export failed", error);
        }
    }

    private Uri writeTextToDownloads(String fileName, String text, String mimeType) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        }
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Could not create download file");
        }
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalStateException("Could not open output stream");
            }
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return uri;
    }

    private File newestMissionDir() {
        File root = new File(getFilesDir(), "gpt_photo_missions");
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            return null;
        }
        File newest = dirs[0];
        for (File dir : dirs) {
            if (dir.lastModified() > newest.lastModified()) {
                newest = dir;
            }
        }
        return newest;
    }

    private String readFile(File file) throws Exception {
        if (!file.exists()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private void missionLog(String event, JSONObject payload) {
        Log.d(TAG, "MISSION " + event + " " + payload);
        if (missionLogger != null) {
            missionLogger.log(event, payload);
        }
    }

    private SafeJsonObject json() {
        return new SafeJsonObject();
    }

    private String jsTemplate(String text) {
        return text == null ? "" : text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String jsString(String text) {
        return text == null ? "" : text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private String unwrapJsString(String value) throws JSONException {
        if (value == null || "null".equals(value)) {
            return "{}";
        }
        return new JSONArray("[" + value + "]").getString(0);
    }

    private void injectTextObserver() {
        String js =
                "(function(){"
                        + "if(window.__androidWtbObserver){return;}"
                        + "window.__androidWtbObserver=true;"
                        + "let last='';"
                        + "function send(){"
                        + " const body=document.body;"
                        + " if(!body){return;}"
                        + " const text=body.innerText||'';"
                        + " if(text!==last){last=text;AndroidWebViewTestBed.onTextChanged(text.slice(0,3000));}"
                        + "}"
                        + "new MutationObserver(function(){clearTimeout(window.__androidWtbTimer);window.__androidWtbTimer=setTimeout(send,400);})"
                        + ".observe(document.documentElement,{subtree:true,childList:true,characterData:true});"
                        + "send();"
                        + "})();";
        webView.evaluateJavascript(js, null);
    }

    private String safeFileName(String name, String fallback) {
        String source = (name == null || name.trim().isEmpty()) ? fallback : name.trim();
        String safe = source.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
        while (safe.contains("..")) {
            safe = safe.replace("..", ".");
        }
        if (safe.isEmpty()) {
            safe = fallback == null || fallback.isEmpty() ? "download" : fallback;
        }
        return safe;
    }

    private void recordDownloadEvent(String event, JSONObject payload) {
        downloadEventCounter++;
        SafeJsonObject line = json()
                .put("downloadEventCounter", downloadEventCounter)
                .put("batch", currentBatch == null ? -1 : currentBatch.number)
                .put("strategy", currentBatch == null ? "" : currentBatch.strategy.name);
        if (payload != null) {
            JSONArray names = payload.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String name = names.optString(i, "");
                    line.put(name, payload.opt(name));
                }
            }
        }
        missionLog(event, line);
    }

    private void saveBlobUrl(String blobUrl, String suggestedName, String mimeType) {
        String safeMime = mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType;
        String safeName = safeFileName(suggestedName, "webview-blob-" + System.currentTimeMillis());
        String js =
                "(async function(){"
                        + "const response=await fetch('" + blobUrl.replace("'", "\\'") + "');"
                        + "const blob=await response.blob();"
                        + "const reader=new FileReader();"
                        + "reader.onloadend=function(){AndroidWebViewTestBed.saveBase64File(reader.result,'" + safeName + "','" + safeMime + "');};"
                        + "reader.readAsDataURL(blob);"
                        + "})().catch(function(error){AndroidWebViewTestBed.onEvent('blob-download-error', String(error));});";
        webView.evaluateJavascript(js, null);
    }

    private void saveBlobUrl(String blobUrl, String mimeType) {
        saveBlobUrl(blobUrl, "", mimeType);
    }

    private String resolveDownloadFileName(String url, String contentDisposition, String mimeType) {
        String fromQuery = "";
        try {
            Uri uri = Uri.parse(url);
            fromQuery = uri.getQueryParameter("fn");
            if (fromQuery == null || fromQuery.trim().isEmpty()) {
                fromQuery = uri.getQueryParameter("filename");
            }
        } catch (Exception ignored) {
            fromQuery = "";
        }
        if (fromQuery != null && !fromQuery.trim().isEmpty()) {
            return safeFileName(fromQuery, "webview-download-" + System.currentTimeMillis());
        }
        return safeFileName(URLUtil.guessFileName(url, contentDisposition, mimeType),
                "webview-download-" + System.currentTimeMillis());
    }

    private void downloadHttpUrl(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String fileName = resolveDownloadFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription(url);
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent == null ? "" : userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            long downloadId = manager.enqueue(request);
            recordDownloadEvent("download_enqueued", json()
                    .put("url", url)
                    .put("fileName", fileName)
                    .put("mimeType", mimeType == null ? "" : mimeType)
                    .put("downloadId", downloadId));
            status("Download started: " + fileName);
        } catch (Exception error) {
            status("Download failed: " + error.getMessage());
            Log.e(TAG, "Download failed", error);
            missionLog("download_enqueue_failed", json()
                    .put("url", url)
                    .put("error", error.getMessage()));
        }
    }

    private Uri writeBase64ToDownloads(String dataUrl, String suggestedName, String mimeType) throws Exception {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Invalid data URL");
        }
        String header = dataUrl.substring(0, comma);
        String base64Payload = dataUrl.substring(comma + 1);
        String resolvedMime = mimeType == null || mimeType.isEmpty()
                ? header.replace("data:", "").split(";")[0]
                : mimeType;
        if (resolvedMime.isEmpty()) {
            resolvedMime = "application/octet-stream";
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime);
        String fileName = safeFileName(suggestedName, "webview-download-" + System.currentTimeMillis());
        if (extension != null && !fileName.endsWith("." + extension)) {
            fileName += "." + extension;
        }
        byte[] bytes = Base64.decode(base64Payload.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        }

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("Could not create download row");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalStateException("Could not open output stream");
            }
            output.write(bytes);
        }
        return uri;
    }

    private void requestAndroidPermissionsForResources(String[] resources) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        ArrayList<String> permissions = new ArrayList<>();
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                    && !permissions.contains(Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    && !permissions.contains(Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQ_WEB_PERMISSION);
        }
    }

    private boolean hasNeededAndroidPermissions(String[] resources) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String resource : resources) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MEDIA_PERMISSION) {
            if (hasPhotoPermission()) {
                status("Photo permission granted. Starting mission.");
                mainHandler.postDelayed(this::startPhotoMission, 300);
            } else {
                status("Photo permission denied. Mission cannot read latest photos.");
            }
            return;
        }
        if (requestCode == REQ_WEB_PERMISSION && pendingPermissionRequest != null) {
            PermissionRequest request = pendingPermissionRequest;
            pendingPermissionRequest = null;
            if (hasNeededAndroidPermissions(request.getResources())) {
                request.grant(request.getResources());
                status("Web permission granted.");
            } else {
                request.deny();
                status("Web permission denied.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE_CHOOSER || fileChooserCallback == null) {
            return;
        }
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) {
                    result[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
        status(result == null ? "File chooser canceled." : "File chosen.");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.isCtrlPressed()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_1:
                    openTarget("https://www.google.com", true);
                    return true;
                case KeyEvent.KEYCODE_2:
                    openTarget("https://chatgpt.com", true);
                    return true;
                case KeyEvent.KEYCODE_3:
                    openTarget("https://chatgpt.com", false);
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                    injectPrompt(memoEditor.getText().toString());
                    return true;
                case KeyEvent.KEYCODE_U:
                    triggerFileInputClick();
                    return true;
                case KeyEvent.KEYCODE_R:
                    webView.reload();
                    status("Reloading.");
                    return true;
                case KeyEvent.KEYCODE_L:
                    addressRow.setVisibility(View.VISIBLE);
                    addressInput.requestFocus();
                    addressInput.selectAll();
                    return true;
                case KeyEvent.KEYCODE_P:
                    startPhotoMission();
                    return true;
                case KeyEvent.KEYCODE_S:
                    stopPhotoMission("ctrl-s");
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(20, 20, 20));
        button.setBackgroundColor(Color.rgb(232, 232, 232));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private void status(String message) {
        Log.d(TAG, message);
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        customViewHost.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        customViewHost.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        status("Custom fullscreen view shown.");
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        customViewHost.removeView(customView);
        customViewHost.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        customViewCallback.onCustomViewHidden();
        customView = null;
        customViewCallback = null;
        status("Custom fullscreen view hidden.");
    }

    private class TestWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                status("No app for " + uri);
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            addressInput.setText(url);
            injectTextObserver();
            status("Loaded " + url);
        }
    }

    private class TestChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            JSONArray acceptTypes = new JSONArray();
            if (fileChooserParams != null && fileChooserParams.getAcceptTypes() != null) {
                for (String type : fileChooserParams.getAcceptTypes()) {
                    acceptTypes.put(type);
                }
            }
            missionLog("file_chooser_requested", json()
                    .put("autoArmed", autoUploadArmed)
                    .put("autoUriCount", autoUploadUris.size())
                    .put("mode", fileChooserParams == null ? -1 : fileChooserParams.getMode())
                    .put("captureEnabled", fileChooserParams != null && fileChooserParams.isCaptureEnabled())
                    .put("acceptTypes", acceptTypes));
            if (autoUploadArmed && !autoUploadUris.isEmpty()) {
                Uri[] uris = autoUploadUris.toArray(new Uri[0]);
                filePathCallback.onReceiveValue(uris);
                autoUploadDelivered = true;
                autoUploadArmed = false;
                missionLog("auto_file_chooser_delivered", json()
                        .put("batch", currentBatch == null ? -1 : currentBatch.number)
                        .put("count", uris.length));
                status("Auto-attached " + uris.length + " photos.");
                return true;
            }
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
            }
            fileChooserCallback = filePathCallback;
            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception error) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }
            if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(intent, REQ_FILE_CHOOSER);
                status("File chooser opened.");
            } catch (ActivityNotFoundException error) {
                fileChooserCallback = null;
                filePathCallback.onReceiveValue(null);
                status("No file chooser found.");
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (hasNeededAndroidPermissions(request.getResources())) {
                    request.grant(request.getResources());
                    status("Granted web permission.");
                } else {
                    pendingPermissionRequest = request;
                    requestAndroidPermissionsForResources(request.getResources());
                    status("Requesting Android permission.");
                }
            });
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            missionLog("web_console", json()
                    .put("message", consoleMessage.message())
                    .put("source", consoleMessage.sourceId())
                    .put("line", consoleMessage.lineNumber())
                    .put("level", String.valueOf(consoleMessage.messageLevel())));
            status("Console: " + consoleMessage.message());
            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress < 100) {
                status("Loading " + newProgress + "%");
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            showCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private class TestDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            if (url != null && url.startsWith("blob:")) {
                saveBlobUrl(url, mimeType);
            } else {
                downloadHttpUrl(url, userAgent, contentDisposition, mimeType);
            }
        }
    }

    private class TestBridge {
        @JavascriptInterface
        public void onTextChanged(String text) {
            String compact = text == null ? "" : text.replace('\n', ' ').trim();
            if (compact.length() > 90) {
                compact = compact.substring(0, 90);
            }
            final String preview = compact;
            String message = "Text changed: " + compact;
            runOnUiThread(() -> {
                missionLog("bridge_text_changed", json()
                        .put("chars", text == null ? 0 : text.length())
                        .put("preview", preview));
                status(message);
            });
        }

        @JavascriptInterface
        public void onEvent(String kind, String payload) {
            runOnUiThread(() -> {
                missionLog("bridge_event", json().put("kind", kind).put("payload", payload));
                status(kind + ": " + payload);
            });
        }

        @JavascriptInterface
        public void saveBase64File(String dataUrl, String suggestedName, String mimeType) {
            try {
                Uri uri = writeBase64ToDownloads(dataUrl, suggestedName, mimeType);
                runOnUiThread(() -> {
                    recordDownloadEvent("download_saved", json()
                            .put("uri", uri.toString())
                            .put("suggestedName", suggestedName == null ? "" : suggestedName)
                            .put("mimeType", mimeType == null ? "" : mimeType)
                            .put("source", "base64-bridge"));
                    status("Blob saved: " + uri);
                    Toast.makeText(MainActivity.this, "Blob saved to Downloads", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                Log.e(TAG, "Blob save failed", error);
                runOnUiThread(() -> {
                    missionLog("download_save_failed", json()
                            .put("suggestedName", suggestedName == null ? "" : suggestedName)
                            .put("mimeType", mimeType == null ? "" : mimeType)
                            .put("error", error.getMessage()));
                    status("Blob save failed: " + error.getMessage());
                });
            }
        }

        @JavascriptInterface
        public void requestHttpDownload(String url, String suggestedName, String mimeType) {
            runOnUiThread(() -> {
                String safeName = safeFileName(suggestedName, "webview-http-" + System.currentTimeMillis());
                String disposition = "attachment; filename=\"" + safeName + "\"";
                missionLog("download_http_requested", json()
                        .put("url", url == null ? "" : url)
                        .put("suggestedName", safeName)
                        .put("mimeType", mimeType == null ? "" : mimeType));
                downloadHttpUrl(url, "", disposition, mimeType);
            });
        }

        @JavascriptInterface
        public void requestBlobDownload(String url, String suggestedName, String mimeType) {
            runOnUiThread(() -> {
                missionLog("download_blob_requested", json()
                        .put("url", url == null ? "" : url)
                        .put("suggestedName", suggestedName == null ? "" : suggestedName)
                        .put("mimeType", mimeType == null ? "" : mimeType));
                saveBlobUrl(url, suggestedName, mimeType);
            });
        }
    }

    private interface SnapshotCallback {
        void onSnapshot(Snapshot snapshot);
    }

    private static class PhotoInfo {
        final Uri uri;
        final String name;
        final String mimeType;
        final long dateMillis;

        PhotoInfo(Uri uri, String name, String mimeType, long dateMillis) {
            this.uri = uri;
            this.name = name == null ? "(unnamed)" : name;
            this.mimeType = mimeType == null ? "image/*" : mimeType;
            this.dateMillis = dateMillis;
        }

        SafeJsonObject toJson() {
            return new SafeJsonObject()
                    .put("uri", uri.toString())
                    .put("name", name)
                    .put("mimeType", mimeType)
                    .put("dateMillis", dateMillis);
        }
    }

    private static class BatchStrategy {
        final String name;
        final String attachMode;
        final boolean promptBeforeAttach;
        final String sendMode;

        BatchStrategy(String name, String attachMode, boolean promptBeforeAttach, String sendMode) {
            this.name = name;
            this.attachMode = attachMode;
            this.promptBeforeAttach = promptBeforeAttach;
            this.sendMode = sendMode;
        }

        SafeJsonObject toJson() {
            return new SafeJsonObject()
                    .put("name", name)
                    .put("attachMode", attachMode)
                    .put("promptBeforeAttach", promptBeforeAttach)
                    .put("sendMode", sendMode);
        }
    }

    private static class BatchRun {
        final int number;
        final ArrayList<PhotoInfo> photos;
        final BatchStrategy strategy;
        final String prompt;
        final long startedAtMs = System.currentTimeMillis();
        long sentAtMs;
        long lastResponseChangeAtMs;
        long finishedAtMs;
        long downloadAtMs;
        int downloadStartCounter;
        String lastResponseText = "";
        String finishReason = "";
        String downloadResult = "";
        String downloadStrategy = "";
        Snapshot preSendSnapshot;
        Snapshot finalSnapshot;

        BatchRun(int number, ArrayList<PhotoInfo> photos, BatchStrategy strategy) {
            this(number, photos, strategy, null);
        }

        BatchRun(int number, ArrayList<PhotoInfo> photos, BatchStrategy strategy, String customPrompt) {
            this.number = number;
            this.photos = photos;
            this.strategy = strategy;
            if (customPrompt == null) {
                this.prompt = "첨부한 사진 5장을 최신순 기준으로 1번부터 5번까지 보세요. "
                        + "각 사진이 무엇인지 한국어로 정확히 두 문장씩 묘사해 주세요. "
                        + "각 항목은 '사진 1:'처럼 시작하고, 확실하지 않으면 추측이라고 밝혀 주세요. "
                        + "이 요청은 WebView 자동화 안정성 테스트의 " + number + "/6 배치입니다.";
            } else {
                this.prompt = customPrompt;
            }
        }

        int preAssistantLen() {
            return preSendSnapshot == null ? 0 : preSendSnapshot.lastAssistantLen;
        }

        int preAssistantCount() {
            return preSendSnapshot == null ? 0 : preSendSnapshot.assistantCount;
        }

        int preBodyTailLen() {
            return preSendSnapshot == null ? 0 : preSendSnapshot.bodyTail.length();
        }

        SafeJsonObject toJson() {
            JSONArray photoArray = new JSONArray();
            for (PhotoInfo photo : photos) {
                photoArray.put(photo.toJson());
            }
            return new SafeJsonObject()
                    .put("batch", number)
                    .put("strategy", strategy.toJson())
                    .put("prompt", prompt)
                    .put("startedAtMs", startedAtMs)
                    .put("sentAtMs", sentAtMs)
                    .put("lastResponseChangeAtMs", lastResponseChangeAtMs)
                    .put("finishedAtMs", finishedAtMs)
                    .put("finishReason", finishReason)
                    .put("downloadAtMs", downloadAtMs)
                    .put("downloadStartCounter", downloadStartCounter)
                    .put("downloadStrategy", downloadStrategy)
                    .put("downloadResult", downloadResult)
                    .put("photos", photoArray)
                    .put("preSendSnapshot", preSendSnapshot == null ? JSONObject.NULL : preSendSnapshot.toJson())
                    .put("finalSnapshot", finalSnapshot == null ? JSONObject.NULL : finalSnapshot.toJson());
        }
    }

    private static class Snapshot {
        String href = "";
        String title = "";
        String bodyTail = "";
        String lastAssistantText = "";
        int bodyLen;
        int assistantCount;
        int lastAssistantLen;
        boolean editorExists;
        boolean stopVisible;
        boolean uploadingVisible;
        long pageTs;

        static Snapshot fromJson(JSONObject object) {
            Snapshot snapshot = new Snapshot();
            snapshot.href = object.optString("href", "");
            snapshot.title = object.optString("title", "");
            snapshot.bodyLen = object.optInt("bodyLen", 0);
            snapshot.bodyTail = object.optString("bodyTail", "");
            snapshot.assistantCount = object.optInt("assistantCount", 0);
            snapshot.lastAssistantText = object.optString("lastAssistant", "");
            snapshot.lastAssistantLen = object.optInt("lastAssistantLen", snapshot.lastAssistantText.length());
            snapshot.editorExists = object.optBoolean("editorExists", false);
            snapshot.stopVisible = object.optBoolean("stopVisible", false);
            snapshot.uploadingVisible = object.optBoolean("uploadingVisible", false);
            snapshot.pageTs = object.optLong("ts", 0L);
            return snapshot;
        }

        SafeJsonObject toJson() {
            return new SafeJsonObject()
                    .put("href", href)
                    .put("title", title)
                    .put("bodyLen", bodyLen)
                    .put("bodyTail", bodyTail)
                    .put("assistantCount", assistantCount)
                    .put("lastAssistantLen", lastAssistantLen)
                    .put("lastAssistantPreview", preview(lastAssistantText, 500))
                    .put("editorExists", editorExists)
                    .put("stopVisible", stopVisible)
                    .put("uploadingVisible", uploadingVisible)
                    .put("pageTs", pageTs);
        }

        private static String preview(String text, int max) {
            if (text == null) {
                return "";
            }
            return text.length() <= max ? text : text.substring(0, max);
        }
    }

    private static class TargetPoint {
        boolean found;
        float xCss;
        float yCss;
        float viewportWidth = 1f;
        float viewportHeight = 1f;
        String label = "";
        String tag = "";

        static TargetPoint fromJson(JSONObject object) {
            TargetPoint point = new TargetPoint();
            point.found = object.optBoolean("found", false);
            point.xCss = (float) object.optDouble("x", 0.0);
            point.yCss = (float) object.optDouble("y", 0.0);
            point.viewportWidth = (float) Math.max(1.0, object.optDouble("vw", 1.0));
            point.viewportHeight = (float) Math.max(1.0, object.optDouble("vh", 1.0));
            point.label = object.optString("label", "");
            point.tag = object.optString("tag", "");
            return point;
        }

        SafeJsonObject toJson() {
            return new SafeJsonObject()
                    .put("found", found)
                    .put("xCss", xCss)
                    .put("yCss", yCss)
                    .put("viewportWidth", viewportWidth)
                    .put("viewportHeight", viewportHeight)
                    .put("label", label)
                    .put("tag", tag);
        }
    }

    private static class MissionLogger {
        final File runDir;
        private BufferedWriter eventsWriter;
        private BufferedWriter responsesWriter;

        MissionLogger() {
            this(MISSION_KIND_PHOTO);
        }

        MissionLogger(String missionKind) {
            String runId = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            runDir = new File(AppContextHolder.filesDir, "gpt_photo_missions/" + runId);
            if (!runDir.exists() && !runDir.mkdirs()) {
                Log.e(TAG, "Could not create mission dir: " + runDir);
            }
            try {
                eventsWriter = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(new File(runDir, "events.jsonl"), true),
                        StandardCharsets.UTF_8
                ));
                responsesWriter = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(new File(runDir, "responses.md"), true),
                        StandardCharsets.UTF_8
                ));
                responsesWriter.write("# GPT Mission " + runId + "\n\n");
                responsesWriter.write("- Kind: `" + missionKind + "`\n");
                responsesWriter.write("Internal path: `" + runDir.getAbsolutePath() + "`\n\n");
                responsesWriter.flush();
            } catch (Exception error) {
                Log.e(TAG, "Could not open mission log writers", error);
            }
        }

        void log(String event, JSONObject payload) {
            if (eventsWriter == null) {
                return;
            }
            try {
                JSONObject line = new SafeJsonObject()
                        .put("timeMs", System.currentTimeMillis())
                        .put("event", event)
                        .put("payload", payload == null ? new JSONObject() : payload);
                eventsWriter.write(line.toString());
                eventsWriter.write('\n');
                eventsWriter.flush();
            } catch (Exception error) {
                Log.e(TAG, "Could not write mission event", error);
            }
        }

        void appendResponse(BatchRun batch) {
            if (responsesWriter == null) {
                return;
            }
            try {
                responsesWriter.write("## Batch " + batch.number + "\n\n");
                responsesWriter.write("- Strategy: `" + batch.strategy.name + "`\n");
                responsesWriter.write("- Finish: `" + batch.finishReason + "`\n");
                responsesWriter.write("- Sent at: `" + batch.sentAtMs + "`\n");
                responsesWriter.write("- Last text change at: `" + batch.lastResponseChangeAtMs + "`\n");
                responsesWriter.write("- Finished at: `" + batch.finishedAtMs + "`\n");
                if (!batch.downloadResult.isEmpty() || !batch.downloadStrategy.isEmpty()) {
                    responsesWriter.write("- Download strategy: `" + batch.downloadStrategy + "`\n");
                    responsesWriter.write("- Download result: `" + batch.downloadResult + "`\n");
                    responsesWriter.write("- Download at: `" + batch.downloadAtMs + "`\n");
                }
                responsesWriter.write("- Photos:\n");
                for (int i = 0; i < batch.photos.size(); i++) {
                    PhotoInfo photo = batch.photos.get(i);
                    responsesWriter.write("  - " + (i + 1) + ". `" + photo.name + "` `" + photo.uri + "` `" + photo.dateMillis + "`\n");
                }
                responsesWriter.write("\nPrompt:\n\n```text\n" + batch.prompt + "\n```\n\n");
                String response = batch.finalSnapshot == null ? batch.lastResponseText : batch.finalSnapshot.lastAssistantText;
                if (response == null || response.trim().isEmpty()) {
                    response = batch.lastResponseText;
                }
                responsesWriter.write("Response snapshot:\n\n```text\n" + response + "\n```\n\n");
                responsesWriter.flush();
            } catch (Exception error) {
                Log.e(TAG, "Could not write mission response", error);
            }
        }

        void flush() {
            try {
                if (eventsWriter != null) {
                    eventsWriter.flush();
                }
                if (responsesWriter != null) {
                    responsesWriter.flush();
                }
            } catch (Exception error) {
                Log.e(TAG, "Could not flush mission logs", error);
            }
        }

        void close() {
            try {
                if (eventsWriter != null) {
                    eventsWriter.close();
                }
                if (responsesWriter != null) {
                    responsesWriter.close();
                }
            } catch (Exception error) {
                Log.e(TAG, "Could not close mission logs", error);
            }
        }
    }

    private static class SafeJsonObject extends JSONObject {
        @Override
        public SafeJsonObject put(String name, boolean value) {
            try {
                super.put(name, value);
            } catch (JSONException ignored) {
            }
            return this;
        }

        @Override
        public SafeJsonObject put(String name, double value) {
            try {
                super.put(name, value);
            } catch (JSONException ignored) {
            }
            return this;
        }

        @Override
        public SafeJsonObject put(String name, int value) {
            try {
                super.put(name, value);
            } catch (JSONException ignored) {
            }
            return this;
        }

        @Override
        public SafeJsonObject put(String name, long value) {
            try {
                super.put(name, value);
            } catch (JSONException ignored) {
            }
            return this;
        }

        @Override
        public SafeJsonObject put(String name, Object value) {
            try {
                super.put(name, value);
            } catch (JSONException ignored) {
            }
            return this;
        }
    }

    private static class AppContextHolder {
        static File filesDir;
    }
}
