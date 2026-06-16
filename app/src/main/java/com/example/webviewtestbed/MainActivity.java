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
import android.media.AudioManager;
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
    private static final int REQ_VOICE_PERMISSION = 1004;
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
    private static final long VOICE_READY_TIMEOUT_MS = 20000L;
    private static final long VOICE_READY_POLL_MS = 700L;
    private static final long VOICE_TURN_POLL_MS = 150L;
    private static final long VOICE_AUDIO_START_WAIT_MS = 6000L;
    private static final long VOICE_AUDIO_GATE_MAX_WAIT_MS = 45000L;
    private static final long VOICE_TEXT_PROBE_INTERVAL_MS = 1000L;
    private static final long VOICE_TEXT_STABLE_MS = 1400L;
    private static final int VOICE_QUIET_STREAK_POLLS = 6;
    private static final int VOICE_MEDIA_AUDIO_RECENT_MS = 180;
    private static final int DUAL_VOICE_MAX_TURNS = 4;
    private static final long DUAL_VOICE_TURN_POLL_MS = 100L;
    private static final long DUAL_VOICE_TEXT_PROBE_INTERVAL_MS = 220L;
    private static final long DUAL_AUDIO_QUIET_TRANSFER_MS = 100L;
    private static final long DUAL_VOICE_TRANSFER_DELAY_MS = 0L;
    private static final double AUDIO_LEVEL_RMS_GAIN = 24.0;
    private static final double DUAL_AUDIO_INITIAL_QUIET_LEVEL_PERCENT = 7.0;
    private static final double DUAL_AUDIO_QUIET_MARGIN_LEVEL_PERCENT = 2.0;
    private static final double DUAL_AUDIO_MIN_QUIET_LEVEL_PERCENT = 2.0;
    private static final double DUAL_AUDIO_MAX_QUIET_LEVEL_PERCENT = 14.0;
    private static final long DUAL_AUDIO_TAIL_WINDOW_MS = 1500L;
    private static final double DUAL_AUDIO_TAIL_MIN_LEVEL_PERCENT = 0.25;
    private static final double DUAL_AUDIO_TAIL_MAX_LEVEL_PERCENT = 12.0;
    private static final double DUAL_AUDIO_TAIL_PERCENTILE = 0.90;
    private static final long DUAL_A_TEXT_HANDOFF_STABLE_MS = 650L;
    private static final boolean DUAL_A_TEXT_HANDOFF_ENABLED = false;
    private static final long DUAL_TEXT_HANDOFF_LINGER_MAX_MS = 8000L;
    private static final int DUAL_MIC_CAPTURE_MAX_ATTEMPTS = 10;
    private static final long DUAL_MIC_CAPTURE_POLL_MS = 500L;
    private static final String MISSION_KIND_PHOTO = "photo-x6";
    private static final String MISSION_KIND_DOWNLOAD = "download-x3";
    private static final String MISSION_KIND_VOICE = "voice-x1";
    private static final String MISSION_KIND_DUAL_VOICE = "dual-voice-x4";

    private EditText memoEditor;
    private EditText addressInput;
    private LinearLayout addressRow;
    private TextView statusText;
    private View voiceLevelBar;
    private WebView webView;
    private WebView dualWebView;
    private View dualVoiceLevelBar;
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
    private boolean voiceSawAudio;
    private boolean voiceLastAudioActive;
    private int voiceQuietStreak;
    private long voiceTurnStartedAtMs;
    private long voiceLastTextProbeAtMs;
    private long voiceLastTextChangeAtMs;
    private String voiceLastAssistantText = "";
    private boolean voiceNativeMicMuted;
    private boolean pendingDualVoiceStart;
    private DualVoiceSide dualSideA;
    private DualVoiceSide dualSideB;
    private int dualTurnIndex;
    private boolean dualTurnActive;
    private boolean dualAudioQuietThresholdCalibrated;
    private double dualAudioQuietThresholdLevelPercent;
    private double dualAudioQuietThresholdRms;

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

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);
        voiceRow.setGravity(Gravity.CENTER);
        voiceRow.setPadding(0, 0, 0, dp(3));
        root.addView(voiceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        Button runVoiceButton = makeButton("RUN VOICE x1");
        runVoiceButton.setOnClickListener(v -> startVoiceMission());
        voiceRow.addView(runVoiceButton, new LinearLayout.LayoutParams(0, dp(38), 1f));

        Button runDualVoiceButton = makeButton("RUN DUO");
        runDualVoiceButton.setOnClickListener(v -> startDualVoiceMission());
        LinearLayout.LayoutParams dualParams = new LinearLayout.LayoutParams(0, dp(38), 0.7f);
        dualParams.setMargins(dp(6), 0, 0, 0);
        voiceRow.addView(runDualVoiceButton, dualParams);

        Button micMuteButton = makeButton("MIC MUTE");
        micMuteButton.setOnClickListener(v -> setVoiceMicMuted(true, "manual-button"));
        LinearLayout.LayoutParams micMuteParams = new LinearLayout.LayoutParams(0, dp(38), 0.78f);
        micMuteParams.setMargins(dp(6), 0, 0, 0);
        voiceRow.addView(micMuteButton, micMuteParams);

        Button micOpenButton = makeButton("MIC OPEN");
        micOpenButton.setOnClickListener(v -> setVoiceMicMuted(false, "manual-button"));
        LinearLayout.LayoutParams micOpenParams = new LinearLayout.LayoutParams(0, dp(38), 0.78f);
        micOpenParams.setMargins(dp(6), 0, 0, 0);
        voiceRow.addView(micOpenButton, micOpenParams);

        Button voiceDiagButton = makeButton("VOICE DIAG");
        voiceDiagButton.setOnClickListener(v -> runVoiceDiagnostics("manual-button"));
        LinearLayout.LayoutParams voiceDiagParams = new LinearLayout.LayoutParams(0, dp(38), 0.85f);
        voiceDiagParams.setMargins(dp(6), 0, 0, 0);
        voiceRow.addView(voiceDiagButton, voiceDiagParams);

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

        dualWebView = new WebView(this);
        dualWebView.setBackgroundColor(Color.WHITE);
        dualWebView.setVisibility(View.GONE);
        root.addView(dualWebView, new LinearLayout.LayoutParams(
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

        voiceLevelBar = new View(this);
        voiceLevelBar.setBackgroundColor(Color.rgb(100, 180, 120));
        voiceLevelBar.setPivotX(0f);
        voiceLevelBar.setScaleX(0f);
        voiceLevelBar.setAlpha(0.25f);
        root.addView(voiceLevelBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(5)
        ));

        dualVoiceLevelBar = new View(this);
        dualVoiceLevelBar.setBackgroundColor(Color.rgb(90, 130, 210));
        dualVoiceLevelBar.setPivotX(0f);
        dualVoiceLevelBar.setScaleX(0f);
        dualVoiceLevelBar.setAlpha(0.22f);
        root.addView(dualVoiceLevelBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(5)
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
        configureWebViewSettings(webView);
        webView.addJavascriptInterface(new TestBridge("A"), "AndroidWebViewTestBed");
        webView.setWebViewClient(new TestWebViewClient());
        webView.setWebChromeClient(new TestChromeClient());
        webView.setDownloadListener(new TestDownloadListener());

        configureWebViewSettings(dualWebView);
        dualWebView.addJavascriptInterface(new TestBridge("B"), "AndroidWebViewTestBed");
        dualWebView.setWebViewClient(new DualWebViewClient("B"));
        dualWebView.setWebChromeClient(new DualChromeClient("B"));
        dualWebView.setDownloadListener(new TestDownloadListener());
    }

    private void configureWebViewSettings(WebView target) {
        WebSettings settings = target.getSettings();
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
            CookieManager.getInstance().setAcceptThirdPartyCookies(target, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
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

    private void startVoiceMission() {
        if (missionRunning) {
            status("Mission is already running.");
            return;
        }
        if (!hasRecordAudioPermission()) {
            status("Requesting microphone permission for voice test.");
            requestVoicePermission();
            return;
        }

        activeMissionKind = MISSION_KIND_VOICE;
        downloadEventCounter = 0;
        missionRunning = true;
        missionBatchIndex = 0;
        missionPhotos = new ArrayList<>();
        lastMissionSnapshot = null;
        currentBatch = null;
        resetVoiceState();
        missionLogger = new MissionLogger(MISSION_KIND_VOICE);
        latestMissionExportPath = missionLogger.runDir.getAbsolutePath();
        missionLog("mission_start", json()
                .put("kind", activeMissionKind)
                .put("runDir", latestMissionExportPath)
                .put("snippetPlan", "voice button probe, mic stream hook, low-cost media RMS probe, low-frequency assistant text probe, native+track mic mute"));
        status("Voice mission started: " + latestMissionExportPath);
        openTarget("https://chatgpt.com", false);
        mainHandler.postDelayed(() -> prepareVoiceMission(0), 6500);
    }

    private void prepareVoiceMission(int attempt) {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        webView.evaluateJavascript(buildMicCaptureHookJs(), hookResult -> {
            missionLog("voice_mic_hook", json()
                    .put("attempt", attempt)
                    .put("result", hookResult));
            webView.evaluateJavascript(buildVoiceButtonScript(), clickResult -> {
                missionLog("voice_button_click", json()
                        .put("attempt", attempt)
                        .put("result", clickResult));
                waitForVoiceReady(0);
            });
        });
    }

    private void waitForVoiceReady(int attempt) {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        webView.evaluateJavascript(buildVoiceReadyScript(), value -> {
            JSONObject ready = parseJsObject(value, "voice_ready_parse_error");
            boolean voiceOpen = ready.optBoolean("voiceOpen")
                    || ready.optBoolean("micVisible")
                    || ready.optBoolean("muteVisible")
                    || ready.optBoolean("listening");
            missionLog(attempt % 4 == 0 || voiceOpen ? "voice_ready_probe" : "voice_ready_poll", json()
                    .put("ready", ready)
                    .put("attempt", attempt)
                    .put("elapsedMs", attempt * VOICE_READY_POLL_MS));
            if (voiceOpen) {
                runVoiceDiagnostics("voice-ready");
                mainHandler.postDelayed(this::sendVoicePrompt, 700);
                return;
            }
            if (attempt * VOICE_READY_POLL_MS >= VOICE_READY_TIMEOUT_MS) {
                missionLog("mission_blocked", json()
                        .put("reason", "voice_mode_not_ready")
                        .put("lastReady", ready));
                runVoiceDiagnostics("voice-ready-timeout");
                stopPhotoMission("voice-mode-not-ready");
                return;
            }
            status("Waiting for ChatGPT voice UI... " + (attempt + 1));
            mainHandler.postDelayed(() -> waitForVoiceReady(attempt + 1), VOICE_READY_POLL_MS);
        });
    }

    private void sendVoicePrompt() {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        String prompt = voicePromptText();
        missionLog("voice_prompt_prepare", json()
                .put("chars", prompt.length())
                .put("preview", prompt.substring(0, Math.min(120, prompt.length()))));
        webView.evaluateJavascript(buildKeyboardNudgeScript(), nudgeResult -> {
            missionLog("voice_keyboard_nudge", json().put("result", nudgeResult));
            injectMissionPrompt(prompt, injectResult -> {
                missionLog("voice_prompt_injected", json().put("result", injectResult));
                if (!isPromptInjectedResult(injectResult)) {
                    missionLog("mission_blocked", json()
                            .put("reason", "voice_prompt_editor_not_found")
                            .put("injectResult", injectResult));
                    runVoiceDiagnostics("voice-prompt-inject-failed");
                    stopPhotoMission("voice-prompt-editor-not-found");
                    return;
                }
                webView.evaluateJavascript(buildSendJs("button"), sendResult -> {
                    missionLog("voice_send_attempt", json().put("result", sendResult));
                    if (sendResult == null || !sendResult.contains("clicked-send-button")) {
                        missionLog("mission_blocked", json()
                                .put("reason", "voice_prompt_send_not_started")
                                .put("sendResult", sendResult));
                        stopPhotoMission("voice-send-not-started");
                        return;
                    }
                    beginVoiceTurnMonitor();
                });
            });
        });
    }

    private void beginVoiceTurnMonitor() {
        resetVoiceState();
        voiceTurnStartedAtMs = System.currentTimeMillis();
        voiceLastTextChangeAtMs = voiceTurnStartedAtMs;
        missionLog("voice_turn_monitor_start", json()
                .put("audioPollMs", VOICE_TURN_POLL_MS)
                .put("textProbeIntervalMs", VOICE_TEXT_PROBE_INTERVAL_MS)
                .put("audioRecentMs", VOICE_MEDIA_AUDIO_RECENT_MS)
                .put("timeoutMs", VOICE_AUDIO_GATE_MAX_WAIT_MS));
        status("Voice monitor running.");
        monitorVoiceTurn(0);
    }

    private void monitorVoiceTurn(int poll) {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        webView.evaluateJavascript(buildMediaPlaybackProbeScript(), mediaValue -> {
            if (!missionRunning || !isVoiceMission()) {
                return;
            }
            JSONObject media = parseJsObject(mediaValue, "voice_media_parse_error");
            processVoiceMediaProbe(poll, media);
            long now = System.currentTimeMillis();
            if (poll == 0 || now - voiceLastTextProbeAtMs >= VOICE_TEXT_PROBE_INTERVAL_MS) {
                voiceLastTextProbeAtMs = now;
                webView.evaluateJavascript(buildVoiceResponseProbeScript(), textValue -> {
                    JSONObject text = parseJsObject(textValue, "voice_text_parse_error");
                    processVoiceTextProbe(poll, text);
                    finishOrContinueVoiceTurn(poll);
                });
            } else {
                finishOrContinueVoiceTurn(poll);
            }
        });
    }

    private void processVoiceMediaProbe(int poll, JSONObject media) {
        boolean active = media.optBoolean("recentActive");
        boolean hasProbe = media.optBoolean("hasProbe");
        int count = media.optInt("count");
        double rms = media.optDouble("maxRms", 0.0);
        updateVoiceLevel(rms, active, count);
        if (active) {
            voiceSawAudio = true;
            voiceQuietStreak = 0;
        } else if (voiceSawAudio) {
            voiceQuietStreak++;
        }
        if (active != voiceLastAudioActive || poll % 20 == 0) {
            missionLog(active != voiceLastAudioActive ? "voice_audio_state" : "voice_audio_poll", json()
                    .put("poll", poll)
                    .put("active", active)
                    .put("sawAudio", voiceSawAudio)
                    .put("quietStreak", voiceQuietStreak)
                    .put("hasProbe", hasProbe)
                    .put("mediaCount", count)
                    .put("maxRms", rms));
        }
        voiceLastAudioActive = active;
    }

    private void processVoiceTextProbe(int poll, JSONObject textProbe) {
        String text = textProbe.optString("lastAssistant", "");
        if (!text.equals(voiceLastAssistantText)) {
            voiceLastAssistantText = text;
            voiceLastTextChangeAtMs = System.currentTimeMillis();
            missionLog("voice_text_changed", json()
                    .put("probe", textProbe)
                    .put("poll", poll)
                    .put("chars", text.length()));
        } else if (poll % 14 == 0) {
            missionLog("voice_text_poll", json()
                    .put("probe", textProbe)
                    .put("poll", poll)
                    .put("stableForMs", System.currentTimeMillis() - voiceLastTextChangeAtMs));
        }
    }

    private void finishOrContinueVoiceTurn(int poll) {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - voiceTurnStartedAtMs;
        long textStableFor = now - voiceLastTextChangeAtMs;
        boolean audioComplete = voiceSawAudio && voiceQuietStreak >= VOICE_QUIET_STREAK_POLLS;
        boolean textOnlyComplete = !voiceSawAudio
                && elapsed >= VOICE_AUDIO_START_WAIT_MS
                && voiceLastAssistantText.length() > 20
                && textStableFor >= VOICE_TEXT_STABLE_MS;
        boolean timeout = elapsed >= VOICE_AUDIO_GATE_MAX_WAIT_MS;
        if (audioComplete || textOnlyComplete || timeout) {
            String reason = audioComplete ? "audio-quiet"
                    : textOnlyComplete ? "text-stable-no-audio"
                    : "timeout";
            finishVoiceMission(reason, json()
                    .put("poll", poll)
                    .put("elapsedMs", elapsed)
                    .put("sawAudio", voiceSawAudio)
                    .put("quietStreak", voiceQuietStreak)
                    .put("textChars", voiceLastAssistantText.length())
                    .put("textStableForMs", textStableFor));
            return;
        }
        mainHandler.postDelayed(() -> monitorVoiceTurn(poll + 1), VOICE_TURN_POLL_MS);
    }

    private void finishVoiceMission(String reason, JSONObject details) {
        if (!missionRunning || !isVoiceMission()) {
            return;
        }
        missionLog("voice_turn_finish", json()
                .put("reason", reason)
                .put("details", details));
        cleanupVoiceArtifacts("finish-" + reason);
        missionLog("mission_complete", json()
                .put("kind", activeMissionKind)
                .put("reason", reason)
                .put("runDir", latestMissionExportPath));
        missionRunning = false;
        currentBatch = null;
        if (missionLogger != null) {
            missionLogger.close();
            missionLogger = null;
        }
        status("Voice mission complete: " + reason);
    }

    private void startDualVoiceMission() {
        if (missionRunning) {
            status("Mission is already running.");
            return;
        }
        if (!hasRecordAudioPermission()) {
            pendingDualVoiceStart = true;
            status("Requesting microphone permission for dual voice test.");
            requestVoicePermission();
            return;
        }

        activeMissionKind = MISSION_KIND_DUAL_VOICE;
        missionRunning = true;
        missionBatchIndex = 0;
        currentBatch = null;
        lastMissionSnapshot = null;
        dualTurnIndex = 0;
        dualTurnActive = false;
        dualAudioQuietThresholdCalibrated = false;
        dualAudioQuietThresholdLevelPercent = DUAL_AUDIO_INITIAL_QUIET_LEVEL_PERCENT;
        dualAudioQuietThresholdRms = levelPercentToRms(dualAudioQuietThresholdLevelPercent);
        resetVoiceState();
        dualSideA = new DualVoiceSide("A", "아버지", "공부를 시키려고 설득하는 아버지", webView, voiceLevelBar);
        dualSideB = new DualVoiceSide("B", "딸", "놀고 싶어 하는 딸", dualWebView, dualVoiceLevelBar);
        dualWebView.setVisibility(View.VISIBLE);
        missionLogger = new MissionLogger(MISSION_KIND_DUAL_VOICE);
        latestMissionExportPath = missionLogger.runDir.getAbsolutePath();
        missionLog("mission_start", json()
                .put("kind", activeMissionKind)
                .put("runDir", latestMissionExportPath)
                .put("turns", DUAL_VOICE_MAX_TURNS)
                .put("scenario", "father persuades playful daughter to study; audio quiet adaptive threshold")
                .put("initialAudioQuietLevelPercent", dualAudioQuietThresholdLevelPercent)
                .put("initialAudioQuietRms", dualAudioQuietThresholdRms)
                .put("audioQuietMarginLevelPercent", DUAL_AUDIO_QUIET_MARGIN_LEVEL_PERCENT));
        status("Dual voice mission loading both windows.");
        webView.loadUrl("https://chatgpt.com");
        dualWebView.loadUrl("https://chatgpt.com");
        mainHandler.postDelayed(() -> prepareDualVoiceSide(dualSideA, () ->
                prepareDualVoiceSide(dualSideB, this::startDualConversation)), 7000);
    }

    private void prepareDualVoiceSide(DualVoiceSide side, Runnable readyCallback) {
        if (!missionRunning || !isDualVoiceMission() || side == null) {
            return;
        }
        side.view.evaluateJavascript(buildMicCaptureHookJs(), hookResult -> {
            missionLog("dual_mic_hook", json()
                    .put("side", side.id)
                    .put("result", hookResult));
            side.view.evaluateJavascript(buildVoiceButtonScript(), clickResult -> {
                missionLog("dual_voice_button_click", json()
                        .put("side", side.id)
                        .put("result", clickResult));
                waitForDualVoiceReady(side, 0, readyCallback);
            });
        });
    }

    private void waitForDualVoiceReady(DualVoiceSide side, int attempt, Runnable readyCallback) {
        if (!missionRunning || !isDualVoiceMission() || side == null) {
            return;
        }
        side.view.evaluateJavascript(buildVoiceReadyScript(), value -> {
            JSONObject ready = parseJsObject(value, "dual_voice_ready_parse_error");
            boolean voiceOpen = ready.optBoolean("voiceOpen")
                    || ready.optBoolean("micVisible")
                    || ready.optBoolean("muteVisible")
                    || ready.optBoolean("listening");
            missionLog(attempt % 4 == 0 || voiceOpen ? "dual_voice_ready_probe" : "dual_voice_ready_poll", json()
                    .put("side", side.id)
                    .put("ready", ready)
                    .put("attempt", attempt)
                    .put("elapsedMs", attempt * VOICE_READY_POLL_MS));
            if (voiceOpen) {
                waitForDualMicCaptured(side, 0, readyCallback);
                return;
            }
            if (attempt * VOICE_READY_POLL_MS >= VOICE_READY_TIMEOUT_MS) {
                missionLog("mission_blocked", json()
                        .put("reason", "dual_voice_mode_not_ready")
                        .put("side", side.id)
                        .put("lastReady", ready));
                stopPhotoMission("dual-voice-mode-not-ready-" + side.id);
                return;
            }
            status("Waiting dual voice " + side.id + "... " + (attempt + 1));
            mainHandler.postDelayed(() -> waitForDualVoiceReady(side, attempt + 1, readyCallback), VOICE_READY_POLL_MS);
        });
    }

    private void waitForDualMicCaptured(DualVoiceSide side, int attempt, Runnable readyCallback) {
        if (!missionRunning || !isDualVoiceMission() || side == null) {
            return;
        }
        side.view.evaluateJavascript(buildVoiceDiagnosticsScript(), value -> {
            JSONObject diag = parseJsObject(value, "dual_mic_diag_parse_error");
            int tracks = diag.optInt("micTracks");
            int live = diag.optInt("micLive");
            int enabled = diag.optInt("micEnabled");
            missionLog(tracks > 0 && live > 0 ? "dual_mic_capture_ready" : "dual_mic_capture_wait", json()
                    .put("side", side.id)
                    .put("attempt", attempt)
                    .put("micStreams", diag.optInt("micStreams"))
                    .put("micTracks", tracks)
                    .put("micLive", live)
                    .put("micEnabled", enabled)
                    .put("diag", diag));
            if (tracks > 0 && live > 0) {
                side.ready = true;
                setDualSideMicMuted(side, true, "ready-track-mute");
                if (readyCallback != null) {
                    mainHandler.postDelayed(readyCallback, 500);
                }
                return;
            }
            if (attempt >= DUAL_MIC_CAPTURE_MAX_ATTEMPTS) {
                missionLog("mission_blocked", json()
                        .put("reason", "dual_mic_stream_not_captured")
                        .put("side", side.id)
                        .put("attempts", attempt + 1)
                        .put("diag", diag));
                stopPhotoMission("dual-mic-stream-not-captured-" + side.id);
                return;
            }
            status("Waiting mic stream " + side.id + "... " + (attempt + 1));
            mainHandler.postDelayed(() -> waitForDualMicCaptured(side, attempt + 1, readyCallback), DUAL_MIC_CAPTURE_POLL_MS);
        });
    }

    private void startDualConversation() {
        if (!missionRunning || !isDualVoiceMission()) {
            return;
        }
        if (dualSideA == null || dualSideB == null || !dualSideA.ready || !dualSideB.ready) {
            missionLog("mission_blocked", json()
                    .put("reason", "dual_sides_not_ready")
                    .put("aReady", dualSideA != null && dualSideA.ready)
                    .put("bReady", dualSideB != null && dualSideB.ready));
            stopPhotoMission("dual-sides-not-ready");
            return;
        }
        missionLog("dual_conversation_start", json()
                .put("turns", DUAL_VOICE_MAX_TURNS)
                .put("firstSide", dualSideA.id)
                .put("audioPollMs", DUAL_VOICE_TURN_POLL_MS)
                .put("textProbeIntervalMs", DUAL_VOICE_TEXT_PROBE_INTERVAL_MS)
                .put("audioQuietTransferMs", DUAL_AUDIO_QUIET_TRANSFER_MS)
                .put("audioQuietThresholdLevelPercent", dualAudioQuietThresholdLevelPercent)
                .put("audioQuietThresholdRms", dualAudioQuietThresholdRms)
                .put("audioQuietThresholdCalibrated", dualAudioQuietThresholdCalibrated)
                .put("audioQuietMarginLevelPercent", DUAL_AUDIO_QUIET_MARGIN_LEVEL_PERCENT)
                .put("textHandoffEnabled", DUAL_A_TEXT_HANDOFF_ENABLED)
                .put("aTextHandoffStableMs", DUAL_A_TEXT_HANDOFF_STABLE_MS)
                .put("transferDelayMs", DUAL_VOICE_TRANSFER_DELAY_MS));
        sendDualVoiceTurn(dualSideA, dualFirstPrompt());
    }

    private void sendDualVoiceTurn(DualVoiceSide side, String prompt) {
        if (!missionRunning || !isDualVoiceMission() || side == null || dualTurnActive) {
            return;
        }
        dualTurnActive = true;
        side.resetTurn();
        side.turnGeneration++;
        setDualSideMicMuted(dualSideA, true, "turn-lock");
        setDualSideMicMuted(dualSideB, true, "turn-lock");
        missionLog("dual_turn_start", json()
                .put("turn", dualTurnIndex + 1)
                .put("side", side.id)
                .put("name", side.name)
                .put("promptChars", prompt.length())
                .put("promptPreview", prompt.substring(0, Math.min(180, prompt.length()))));
        side.view.evaluateJavascript(buildVoiceResponseProbeScript(), baselineValue -> {
            if (!missionRunning || !isDualVoiceMission() || side == null || !dualTurnActive) {
                return;
            }
            JSONObject baseline = parseJsObject(baselineValue, "dual_pre_turn_text_baseline_parse_error");
            side.preAssistantCount = baseline.optInt("assistantCount", 0);
            missionLog("dual_pre_turn_text_baseline", json()
                    .put("turn", dualTurnIndex + 1)
                    .put("side", side.id)
                    .put("assistantCount", side.preAssistantCount)
                    .put("lastAssistantLen", baseline.optInt("lastAssistantLen", 0))
                    .put("busy", baseline.optBoolean("busy"))
                    .put("busyLabels", optJsonArray(baseline, "busyLabels"))
                    .put("buttonCount", baseline.optInt("buttonCount", -1))
                    .put("sentenceComplete", baseline.optBoolean("sentenceComplete")));
            sendDualVoiceTurnAfterBaseline(side, prompt);
        });
    }

    private void sendDualVoiceTurnAfterBaseline(DualVoiceSide side, String prompt) {
        side.view.evaluateJavascript(buildKeyboardNudgeScript(), nudgeResult -> {
            missionLog("dual_keyboard_nudge", json()
                    .put("turn", dualTurnIndex + 1)
                    .put("side", side.id)
                    .put("result", nudgeResult));
            side.view.evaluateJavascript(buildPromptInjectionJs(prompt), injectResult -> {
                missionLog("dual_prompt_injected", json()
                        .put("turn", dualTurnIndex + 1)
                        .put("side", side.id)
                        .put("result", injectResult));
                if (!isPromptInjectedResult(injectResult)) {
                    missionLog("mission_blocked", json()
                            .put("reason", "dual_prompt_not_injected")
                            .put("side", side.id)
                            .put("result", injectResult));
                    stopPhotoMission("dual-prompt-not-injected-" + side.id);
                    return;
                }
                side.view.evaluateJavascript(buildSendJs("button"), sendResult -> {
                    missionLog("dual_send_attempt", json()
                            .put("turn", dualTurnIndex + 1)
                            .put("side", side.id)
                            .put("result", sendResult));
                    if (sendResult == null || !sendResult.contains("clicked-send-button")) {
                        missionLog("mission_blocked", json()
                                .put("reason", "dual_send_not_started")
                                .put("side", side.id)
                                .put("result", sendResult));
                        stopPhotoMission("dual-send-not-started-" + side.id);
                        return;
                    }
                    side.turnStartedAtMs = System.currentTimeMillis();
                    side.lastTextChangeAtMs = side.turnStartedAtMs;
                    monitorDualVoiceTurn(side, 0);
                });
            });
        });
    }

    private void monitorDualVoiceTurn(DualVoiceSide side, int poll) {
        if (!missionRunning || !isDualVoiceMission() || side == null || !dualTurnActive) {
            return;
        }
        side.view.evaluateJavascript(buildMediaPlaybackProbeScript(), mediaValue -> {
            if (!missionRunning || !isDualVoiceMission() || side == null || !dualTurnActive) {
                return;
            }
            JSONObject media = parseJsObject(mediaValue, "dual_voice_media_parse_error");
            processDualVoiceMediaProbe(side, poll, media);
            long now = System.currentTimeMillis();
            if (poll == 0 || now - side.lastTextProbeAtMs >= DUAL_VOICE_TEXT_PROBE_INTERVAL_MS) {
                side.lastTextProbeAtMs = now;
                side.view.evaluateJavascript(buildVoiceResponseProbeScript(), textValue -> {
                    JSONObject text = parseJsObject(textValue, "dual_voice_text_parse_error");
                    processDualVoiceTextProbe(side, poll, text);
                    finishOrContinueDualVoiceTurn(side, poll);
                });
            } else {
                finishOrContinueDualVoiceTurn(side, poll);
            }
        });
    }

    private void processDualVoiceMediaProbe(DualVoiceSide side, int poll, JSONObject media) {
        long now = System.currentTimeMillis();
        boolean recentActive = media.optBoolean("recentActive");
        boolean hasProbe = media.optBoolean("hasProbe");
        int count = media.optInt("count");
        double rms = media.optDouble("maxRms", 0.0);
        double levelPercent = rmsToLevelPercent(rms);
        double displayLevelPercent = audioDisplayLevelPercent(rms, recentActive);
        double thresholdLevelPercent = currentDualAudioQuietThresholdLevelPercent();
        double thresholdRms = currentDualAudioQuietThresholdRms();
        boolean active = rms > thresholdRms;
        side.lastAudioRms = rms;
        side.lastAudioLevelPercent = levelPercent;
        side.lastAudioDisplayLevelPercent = displayLevelPercent;
        boolean textStableNow = side.lastAssistantText.length() > 0
                && side.lastTextStableProbeAtMs > side.lastTextChangeAtMs;
        side.recordAudioSample(now, rms, levelPercent, displayLevelPercent, active, recentActive, textStableNow);
        updateDualVoiceLevel(side, rms, recentActive, count);
        if (active) {
            side.sawAudio = true;
            side.quietStreak = 0;
            side.lastAudioActiveAtMs = now;
            side.audioQuietStartedAtMs = 0L;
            side.audioQuietRunMaxRms = 0.0;
            side.audioQuietRunMaxLevelPercent = 0.0;
        } else if (side.sawAudio) {
            side.quietStreak++;
            if (side.audioQuietStartedAtMs == 0L) {
                side.audioQuietStartedAtMs = now;
                side.audioQuietRunMaxRms = rms;
                side.audioQuietRunMaxLevelPercent = levelPercent;
                missionLog("dual_audio_quiet_start", json()
                        .put("turn", dualTurnIndex + 1)
                        .put("side", side.id)
                        .put("poll", poll)
                        .put("maxRms", rms)
                        .put("levelPercent", levelPercent)
                        .put("displayLevelPercent", displayLevelPercent)
                        .put("thresholdRms", thresholdRms)
                        .put("thresholdLevelPercent", thresholdLevelPercent)
                        .put("thresholdCalibrated", dualAudioQuietThresholdCalibrated)
                        .put("lastAudioActiveAgoMs", side.lastAudioActiveAtMs == 0L ? -1 : now - side.lastAudioActiveAtMs));
            } else {
                side.audioQuietRunMaxRms = Math.max(side.audioQuietRunMaxRms, rms);
                side.audioQuietRunMaxLevelPercent = Math.max(side.audioQuietRunMaxLevelPercent, levelPercent);
            }
        }
        if (active != side.lastAudioActive || poll % 20 == 0) {
            missionLog(active != side.lastAudioActive ? "dual_audio_state" : "dual_audio_poll", json()
                    .put("turn", dualTurnIndex + 1)
                    .put("side", side.id)
                    .put("active", active)
                    .put("recentActive", recentActive)
                    .put("sawAudio", side.sawAudio)
                    .put("quietStreak", side.quietStreak)
                    .put("audioQuietForMs", side.audioQuietStartedAtMs == 0L ? 0L : now - side.audioQuietStartedAtMs)
                    .put("quietRunMaxRms", side.audioQuietRunMaxRms)
                    .put("quietRunMaxLevelPercent", side.audioQuietRunMaxLevelPercent)
                    .put("hasProbe", hasProbe)
                    .put("mediaCount", count)
                    .put("maxRms", rms)
                    .put("levelPercent", levelPercent)
                    .put("displayLevelPercent", displayLevelPercent)
                    .put("thresholdRms", thresholdRms)
                    .put("thresholdLevelPercent", thresholdLevelPercent)
                    .put("thresholdCalibrated", dualAudioQuietThresholdCalibrated)
                    .put("poll", poll));
        }
        side.lastAudioActive = active;
    }

    private void processDualVoiceTextProbe(DualVoiceSide side, int poll, JSONObject textProbe) {
        String text = textProbe.optString("lastAssistant", "");
        long now = System.currentTimeMillis();
        int assistantCount = textProbe.optInt("assistantCount", 0);
        JSONArray busyLabels = optJsonArray(textProbe, "busyLabels");
        int buttonCount = textProbe.optInt("buttonCount", -1);
        if (side.preAssistantCount >= 0 && assistantCount <= side.preAssistantCount) {
            side.lastTextBusy = textProbe.optBoolean("busy");
            side.lastBusyLabels = busyLabels;
            side.lastButtonCount = buttonCount;
            side.lastSentenceComplete = false;
            if (poll == 0 || poll % 14 == 0) {
                missionLog("dual_text_preexisting_ignored", json()
                        .put("turn", dualTurnIndex + 1)
                        .put("side", side.id)
                        .put("assistantCount", assistantCount)
                        .put("preAssistantCount", side.preAssistantCount)
                        .put("lastAssistantLen", text.length())
                        .put("busy", side.lastTextBusy)
                        .put("busyLabels", busyLabels)
                        .put("buttonCount", buttonCount)
                        .put("poll", poll));
            }
            return;
        }
        side.lastTextBusy = textProbe.optBoolean("busy");
        side.lastBusyLabels = busyLabels;
        side.lastButtonCount = buttonCount;
        side.lastSentenceComplete = textProbe.optBoolean("sentenceComplete");
        if (!text.equals(side.lastAssistantText)) {
            side.lastAssistantText = text;
            side.lastTextChangeAtMs = now;
            side.lastTextStableProbeAtMs = 0L;
            missionLog("dual_text_changed", json()
                    .put("turn", dualTurnIndex + 1)
                    .put("side", side.id)
                    .put("probe", textProbe)
                    .put("chars", text.length())
                    .put("busy", side.lastTextBusy)
                    .put("busyLabels", busyLabels)
                    .put("buttonCount", buttonCount)
                    .put("sentenceComplete", side.lastSentenceComplete)
                    .put("poll", poll));
        } else if (!text.isEmpty()) {
            boolean firstStableProbe = side.lastTextStableProbeAtMs <= side.lastTextChangeAtMs;
            side.lastTextStableProbeAtMs = now;
            if (firstStableProbe) {
                missionLog("dual_text_stable_confirmed", json()
                        .put("turn", dualTurnIndex + 1)
                        .put("side", side.id)
                        .put("chars", text.length())
                        .put("busy", side.lastTextBusy)
                        .put("busyLabels", busyLabels)
                        .put("buttonCount", buttonCount)
                        .put("sentenceComplete", side.lastSentenceComplete)
                        .put("stableForMs", now - side.lastTextChangeAtMs)
                        .put("poll", poll));
            }
        } else if (poll % 14 == 0) {
            missionLog("dual_text_poll", json()
                    .put("turn", dualTurnIndex + 1)
                    .put("side", side.id)
                    .put("probe", textProbe)
                    .put("busy", side.lastTextBusy)
                    .put("busyLabels", busyLabels)
                    .put("buttonCount", buttonCount)
                    .put("stableForMs", now - side.lastTextChangeAtMs)
                    .put("poll", poll));
        }
    }

    private JSONArray optJsonArray(JSONObject object, String name) {
        JSONArray array = object == null ? null : object.optJSONArray(name);
        return array == null ? new JSONArray() : array;
    }

    private void finishOrContinueDualVoiceTurn(DualVoiceSide side, int poll) {
        long now = System.currentTimeMillis();
        long elapsed = now - side.turnStartedAtMs;
        long textStableFor = now - side.lastTextChangeAtMs;
        long audioQuietFor = side.audioQuietStartedAtMs == 0L ? 0L : now - side.audioQuietStartedAtMs;
        boolean textStableConfirmed = side.lastAssistantText.length() > 0
                && side.lastTextStableProbeAtMs > side.lastTextChangeAtMs;
        boolean aTextCompleteHandoff = DUAL_A_TEXT_HANDOFF_ENABLED
                && side == dualSideA
                && textStableConfirmed
                && textStableFor >= DUAL_A_TEXT_HANDOFF_STABLE_MS
                && side.lastSentenceComplete;
        boolean audioTextReady = side.lastSentenceComplete || textStableFor >= VOICE_TEXT_STABLE_MS;
        boolean fastAudioComplete = side.sawAudio
                && side.audioQuietStartedAtMs > 0L
                && audioQuietFor >= DUAL_AUDIO_QUIET_TRANSFER_MS
                && textStableConfirmed
                && audioTextReady;
        boolean fallbackAudioComplete = side.sawAudio
                && side.quietStreak >= VOICE_QUIET_STREAK_POLLS
                && side.lastAssistantText.length() > 0
                && textStableFor >= VOICE_TEXT_STABLE_MS;
        boolean audioComplete = fastAudioComplete || fallbackAudioComplete;
        boolean textOnlyComplete = !side.sawAudio
                && elapsed >= VOICE_AUDIO_START_WAIT_MS
                && side.lastAssistantText.length() > 20
                && textStableFor >= VOICE_TEXT_STABLE_MS;
        boolean timeout = elapsed >= VOICE_AUDIO_GATE_MAX_WAIT_MS;
        if (aTextCompleteHandoff || audioComplete || textOnlyComplete || timeout) {
            String reason = aTextCompleteHandoff ? "a-text-complete-handoff"
                    : fastAudioComplete ? "text-stable-audio-quiet-100ms"
                    : fallbackAudioComplete ? "audio-quiet-fallback"
                    : textOnlyComplete ? "text-stable-no-audio"
                    : "timeout";
            AudioTailStats tailStats = summarizeDualAudioTail(side, now);
            if (fastAudioComplete || fallbackAudioComplete) {
                missionLog("dual_audio_tail_window", json()
                        .put("turn", dualTurnIndex + 1)
                        .put("side", side.id)
                        .put("reason", reason)
                        .put("poll", poll)
                        .put("audioQuietForMs", audioQuietFor)
                        .put("tail", audioTailStatsJson(tailStats)));
            }
            if (fastAudioComplete || fallbackAudioComplete) {
                calibrateDualAudioQuietThresholdIfNeeded(side, poll, reason, audioQuietFor, tailStats);
            }
            finishDualVoiceTurn(side, reason, json()
                    .put("poll", poll)
                    .put("elapsedMs", elapsed)
                    .put("sawAudio", side.sawAudio)
                    .put("quietStreak", side.quietStreak)
                    .put("audioQuietForMs", audioQuietFor)
                    .put("audioQuietThresholdMs", DUAL_AUDIO_QUIET_TRANSFER_MS)
                    .put("audioQuietThresholdRms", currentDualAudioQuietThresholdRms())
                    .put("audioQuietThresholdLevelPercent", currentDualAudioQuietThresholdLevelPercent())
                    .put("audioQuietThresholdCalibrated", dualAudioQuietThresholdCalibrated)
                    .put("quietRunMaxRms", side.audioQuietRunMaxRms)
                    .put("quietRunMaxLevelPercent", side.audioQuietRunMaxLevelPercent)
                    .put("tail", audioTailStatsJson(tailStats))
                    .put("lastAudioRms", side.lastAudioRms)
                    .put("lastAudioLevelPercent", side.lastAudioLevelPercent)
                    .put("lastAudioDisplayLevelPercent", side.lastAudioDisplayLevelPercent)
                    .put("textChars", side.lastAssistantText.length())
                    .put("textStableForMs", textStableFor)
                    .put("textStableConfirmed", textStableConfirmed)
                    .put("audioTextReady", audioTextReady)
                    .put("textStableProbeAgeMs", side.lastTextStableProbeAtMs == 0L ? -1L : now - side.lastTextStableProbeAtMs)
                    .put("textBusy", side.lastTextBusy)
                    .put("busyLabels", side.lastBusyLabels == null ? new JSONArray() : side.lastBusyLabels)
                    .put("buttonCount", side.lastButtonCount)
                    .put("sentenceComplete", side.lastSentenceComplete)
                    .put("textHandoffEnabled", DUAL_A_TEXT_HANDOFF_ENABLED)
                    .put("aTextHandoffStableMs", DUAL_A_TEXT_HANDOFF_STABLE_MS)
                    .put("audioActiveAtHandoff", side.lastAudioActive)
                    .put("textHandoff", aTextCompleteHandoff)
                    .put("fastHandoff", fastAudioComplete));
            return;
        }
        mainHandler.postDelayed(() -> monitorDualVoiceTurn(side, poll + 1), DUAL_VOICE_TURN_POLL_MS);
    }

    private double currentDualAudioQuietThresholdRms() {
        return dualAudioQuietThresholdRms > 0.0
                ? dualAudioQuietThresholdRms
                : levelPercentToRms(DUAL_AUDIO_INITIAL_QUIET_LEVEL_PERCENT);
    }

    private double currentDualAudioQuietThresholdLevelPercent() {
        return dualAudioQuietThresholdLevelPercent > 0.0
                ? dualAudioQuietThresholdLevelPercent
                : DUAL_AUDIO_INITIAL_QUIET_LEVEL_PERCENT;
    }

    private double rmsToLevelPercent(double rms) {
        return Math.max(0.0, Math.min(100.0, rms * AUDIO_LEVEL_RMS_GAIN * 100.0));
    }

    private double levelPercentToRms(double levelPercent) {
        return Math.max(0.0, levelPercent) / (AUDIO_LEVEL_RMS_GAIN * 100.0);
    }

    private double audioDisplayLevelPercent(double rms, boolean recentActive) {
        double scaled = Math.max(0.0, Math.min(1.0, rms * AUDIO_LEVEL_RMS_GAIN));
        if (recentActive) {
            scaled = Math.max(0.08, scaled);
        } else {
            scaled = scaled * 0.4;
        }
        return scaled * 100.0;
    }

    private double clampAudioQuietLevelPercent(double levelPercent) {
        return Math.max(DUAL_AUDIO_MIN_QUIET_LEVEL_PERCENT,
                Math.min(DUAL_AUDIO_MAX_QUIET_LEVEL_PERCENT, levelPercent));
    }

    private AudioTailStats summarizeDualAudioTail(DualVoiceSide side, long now) {
        if (side == null) {
            return new AudioTailStats();
        }
        long windowStart = Math.max(side.turnStartedAtMs, now - DUAL_AUDIO_TAIL_WINDOW_MS);
        ArrayList<Double> rawCandidates = new ArrayList<>();
        ArrayList<Double> displayCandidates = new ArrayList<>();
        AudioTailStats stats = new AudioTailStats();
        stats.windowMs = DUAL_AUDIO_TAIL_WINDOW_MS;
        stats.windowStartAgoMs = Math.max(0L, now - windowStart);
        for (AudioLevelSample sample : side.audioLevelSamples) {
            if (sample.timeMs < windowStart) {
                continue;
            }
            stats.sampleCount++;
            stats.rawMaxLevelPercent = Math.max(stats.rawMaxLevelPercent, sample.levelPercent);
            stats.displayMaxLevelPercent = Math.max(stats.displayMaxLevelPercent, sample.displayLevelPercent);
            if (sample.thresholdActive) {
                stats.thresholdActiveSamples++;
            }
            if (sample.recentActive) {
                stats.recentActiveSamples++;
            }
            if (sample.textStable) {
                stats.textStableSamples++;
            }
            if (sample.levelPercent <= 0.0) {
                stats.zeroSamples++;
            }
            if (sample.levelPercent >= DUAL_AUDIO_TAIL_MIN_LEVEL_PERCENT
                    && sample.levelPercent <= DUAL_AUDIO_TAIL_MAX_LEVEL_PERCENT) {
                stats.candidateCount++;
                rawCandidates.add(sample.levelPercent);
                displayCandidates.add(sample.displayLevelPercent);
            }
        }
        stats.rawP90LevelPercent = percentile(rawCandidates, DUAL_AUDIO_TAIL_PERCENTILE);
        stats.rawP50LevelPercent = percentile(rawCandidates, 0.50);
        stats.displayP90LevelPercent = percentile(displayCandidates, DUAL_AUDIO_TAIL_PERCENTILE);
        stats.displayP50LevelPercent = percentile(displayCandidates, 0.50);
        stats.chosenLevelPercent = stats.rawP50LevelPercent > 0.0
                ? stats.rawP50LevelPercent
                : side.audioQuietRunMaxLevelPercent;
        return stats;
    }

    private double percentile(ArrayList<Double> values, double percentile) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        values.sort(Double::compareTo);
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        index = Math.max(0, Math.min(values.size() - 1, index));
        return values.get(index);
    }

    private SafeJsonObject audioTailStatsJson(AudioTailStats stats) {
        if (stats == null) {
            stats = new AudioTailStats();
        }
        return json()
                .put("windowMs", stats.windowMs)
                .put("windowStartAgoMs", stats.windowStartAgoMs)
                .put("sampleCount", stats.sampleCount)
                .put("candidateCount", stats.candidateCount)
                .put("zeroSamples", stats.zeroSamples)
                .put("thresholdActiveSamples", stats.thresholdActiveSamples)
                .put("recentActiveSamples", stats.recentActiveSamples)
                .put("textStableSamples", stats.textStableSamples)
                .put("rawP50LevelPercent", stats.rawP50LevelPercent)
                .put("rawP90LevelPercent", stats.rawP90LevelPercent)
                .put("rawMaxLevelPercent", stats.rawMaxLevelPercent)
                .put("displayP50LevelPercent", stats.displayP50LevelPercent)
                .put("displayP90LevelPercent", stats.displayP90LevelPercent)
                .put("displayMaxLevelPercent", stats.displayMaxLevelPercent)
                .put("chosenLevelPercent", stats.chosenLevelPercent)
                .put("chosenStat", stats.rawP50LevelPercent > 0.0 ? "rawP50" : "quietRunMax")
                .put("tailMinLevelPercent", DUAL_AUDIO_TAIL_MIN_LEVEL_PERCENT)
                .put("tailMaxLevelPercent", DUAL_AUDIO_TAIL_MAX_LEVEL_PERCENT)
                .put("tailPercentile", DUAL_AUDIO_TAIL_PERCENTILE);
    }

    private void calibrateDualAudioQuietThresholdIfNeeded(
            DualVoiceSide side,
            int poll,
            String reason,
            long audioQuietFor,
            AudioTailStats tailStats
    ) {
        if (dualAudioQuietThresholdCalibrated || side == null) {
            return;
        }
        double measuredLevelPercent = tailStats == null ? 0.0 : tailStats.chosenLevelPercent;
        if (measuredLevelPercent <= 0.0) {
            measuredLevelPercent = side.audioQuietRunMaxLevelPercent;
        }
        if (measuredLevelPercent <= 0.0) {
            return;
        }
        double nextLevelPercent = clampAudioQuietLevelPercent(
                measuredLevelPercent + DUAL_AUDIO_QUIET_MARGIN_LEVEL_PERCENT);
        dualAudioQuietThresholdLevelPercent = nextLevelPercent;
        dualAudioQuietThresholdRms = levelPercentToRms(nextLevelPercent);
        dualAudioQuietThresholdCalibrated = true;
        missionLog("dual_audio_threshold_calibrated", json()
                .put("turn", dualTurnIndex + 1)
                .put("side", side.id)
                .put("reason", reason)
                .put("poll", poll)
                .put("audioQuietForMs", audioQuietFor)
                .put("measuredQuietRunMaxRms", side.audioQuietRunMaxRms)
                .put("measuredQuietRunMaxLevelPercent", side.audioQuietRunMaxLevelPercent)
                .put("measuredTailLevelPercent", measuredLevelPercent)
                .put("marginLevelPercent", DUAL_AUDIO_QUIET_MARGIN_LEVEL_PERCENT)
                .put("nextThresholdLevelPercent", dualAudioQuietThresholdLevelPercent)
                .put("nextThresholdRms", dualAudioQuietThresholdRms)
                .put("tail", audioTailStatsJson(tailStats)));
    }

    private void monitorDualTextHandoffSourceLinger(
            DualVoiceSide side,
            int finishedTurn,
            int turnGeneration,
            long startedAtMs,
            int poll,
            long quietStartedAtMs,
            boolean lastActive
    ) {
        if (!missionRunning || !isDualVoiceMission() || side == null || side.turnGeneration != turnGeneration) {
            return;
        }
        side.view.evaluateJavascript(buildMediaPlaybackProbeScript(), mediaValue -> {
            if (!missionRunning || !isDualVoiceMission() || side == null || side.turnGeneration != turnGeneration) {
                return;
            }
            JSONObject media = parseJsObject(mediaValue, "dual_text_handoff_linger_parse_error");
            long now = System.currentTimeMillis();
            double rms = media.optDouble("maxRms", 0.0);
            boolean recentActive = media.optBoolean("recentActive");
            boolean active = rms > currentDualAudioQuietThresholdRms();
            int count = media.optInt("count");
            updateDualVoiceLevel(side, rms, recentActive, count);

            long nextQuietStartedAtMs = quietStartedAtMs;
            if (active) {
                nextQuietStartedAtMs = 0L;
            } else if (nextQuietStartedAtMs == 0L) {
                nextQuietStartedAtMs = now;
            }
            long quietForMs = nextQuietStartedAtMs == 0L ? 0L : now - nextQuietStartedAtMs;
            long elapsedMs = now - startedAtMs;
            if (active != lastActive || poll % 10 == 0) {
                missionLog(active != lastActive ? "dual_text_handoff_source_audio_state" : "dual_text_handoff_source_audio_poll", json()
                        .put("turn", finishedTurn)
                        .put("side", side.id)
                        .put("active", active)
                        .put("recentActive", recentActive)
                        .put("quietForMs", quietForMs)
                        .put("elapsedMs", elapsedMs)
                        .put("maxRms", rms)
                        .put("mediaCount", count)
                        .put("poll", poll));
            }
            if ((nextQuietStartedAtMs > 0L && quietForMs >= DUAL_AUDIO_QUIET_TRANSFER_MS)
                    || elapsedMs >= DUAL_TEXT_HANDOFF_LINGER_MAX_MS) {
                String finishReason = elapsedMs >= DUAL_TEXT_HANDOFF_LINGER_MAX_MS ? "max-wait" : "quiet";
                missionLog("dual_text_handoff_source_audio_done", json()
                        .put("turn", finishedTurn)
                        .put("side", side.id)
                        .put("reason", finishReason)
                        .put("quietForMs", quietForMs)
                        .put("elapsedMs", elapsedMs)
                        .put("maxRms", rms));
                side.view.evaluateJavascript(buildMediaProbeCleanupScript(), cleanupResult ->
                        missionLog("dual_media_probe_cleanup", json()
                                .put("side", side.id)
                                .put("turn", finishedTurn)
                                .put("source", "text-handoff-linger")
                                .put("result", cleanupResult)));
                return;
            }
            long quietStartedForNextPoll = nextQuietStartedAtMs;
            mainHandler.postDelayed(() -> monitorDualTextHandoffSourceLinger(
                    side,
                    finishedTurn,
                    turnGeneration,
                    startedAtMs,
                    poll + 1,
                    quietStartedForNextPoll,
                    active
            ), DUAL_VOICE_TURN_POLL_MS);
        });
    }

    private void finishDualVoiceTurn(DualVoiceSide side, String reason, JSONObject details) {
        if (!missionRunning || !isDualVoiceMission()) {
            return;
        }
        String exactText = side.lastAssistantText == null ? "" : side.lastAssistantText.trim();
        missionLog("dual_turn_finish", json()
                .put("turn", dualTurnIndex + 1)
                .put("side", side.id)
                .put("reason", reason)
                .put("exactText", exactText)
                .put("details", details));
        int finishedTurn = dualTurnIndex + 1;
        boolean textCompleteHandoff = "a-text-complete-handoff".equals(reason);
        if (textCompleteHandoff) {
            monitorDualTextHandoffSourceLinger(side, finishedTurn, side.turnGeneration,
                    System.currentTimeMillis(), 0, 0L, side.lastAudioActive);
        } else {
            side.view.evaluateJavascript(buildMediaProbeCleanupScript(), cleanupResult ->
                    missionLog("dual_media_probe_cleanup", json()
                            .put("side", side.id)
                            .put("turn", finishedTurn)
                            .put("result", cleanupResult)));
        }
        dualTurnActive = false;
        dualTurnIndex++;
        if (dualTurnIndex >= DUAL_VOICE_MAX_TURNS || "timeout".equals(reason)) {
            completeDualVoiceMission(reason);
            return;
        }
        DualVoiceSide next = side == dualSideA ? dualSideB : dualSideA;
        String prompt = dualTransferPrompt(next, side, exactText, dualTurnIndex);
        missionLog("dual_transfer", json()
                .put("from", side.id)
                .put("to", next.id)
                .put("nextTurn", dualTurnIndex + 1)
                .put("delayMs", DUAL_VOICE_TRANSFER_DELAY_MS)
                .put("exactText", exactText)
                .put("promptChars", prompt.length()));
        mainHandler.postDelayed(() -> sendDualVoiceTurn(next, prompt), DUAL_VOICE_TRANSFER_DELAY_MS);
    }

    private void completeDualVoiceMission(String reason) {
        cleanupDualVoiceArtifacts("complete-" + reason);
        missionLog("mission_complete", json()
                .put("kind", activeMissionKind)
                .put("reason", reason)
                .put("turns", dualTurnIndex)
                .put("runDir", latestMissionExportPath));
        missionRunning = false;
        dualTurnActive = false;
        currentBatch = null;
        if (missionLogger != null) {
            missionLogger.close();
            missionLogger = null;
        }
        status("Dual voice complete: " + reason);
    }

    private String dualFirstPrompt() {
        return "역할극 보이스 대화입니다. 당신은 공부를 시키려고 설득하는 아버지입니다. "
                + "다른 보이스 창에는 지금 놀고 싶어 하는 딸이 있습니다. "
                + "딸에게 숙제를 먼저 하고 놀자고 부드럽게 설득하세요. "
                + "정확히 네 문장으로 답하세요. 각 문장은 마침표나 물음표로 끝내세요. "
                + "일부러 느리게, 또박또박, 쉬어 가며 말하세요. "
                + "마지막 네 번째 문장은 딸에게 짧게 선택지를 묻는 질문으로 끝내세요. "
                + "동시에 말하지 말고 당신 차례에만 한국어로 말하세요.";
    }

    private String dualTransferPrompt(DualVoiceSide target, DualVoiceSide source, String exactText, int nextTurnIndex) {
        boolean finalTurn = nextTurnIndex >= DUAL_VOICE_MAX_TURNS - 1;
        boolean fatherTurn = target == dualSideA;
        String instruction;
        if (fatherTurn) {
            instruction = finalTurn
                    ? "이번은 마지막 턴입니다. 딸의 말을 정확히 이어받아 부드럽게 마무리하세요. 정확히 네 문장으로, 각 문장은 마침표나 물음표로 끝내고, 일부러 느리게 또박또박 말하세요."
                    : "딸의 말을 정확히 이어받아 공부를 먼저 하도록 부드럽게 다시 설득하세요. 정확히 네 문장으로, 각 문장은 마침표나 물음표로 끝내고, 일부러 느리게 또박또박 말하세요. 마지막에는 짧게 확인 질문을 하세요.";
        } else {
            instruction = finalTurn
                    ? "이번은 마지막 턴입니다. 아버지 말을 정확히 이어받아 딸의 입장에서 두 문장 이내로 대답하고, 공부를 시작할지 타협안을 말하세요."
                    : "아버지 말을 정확히 이어받아 딸의 입장에서 두 문장 이내로 대답하세요. 놀고 싶은 마음을 드러내되, 마지막에는 짧게 되물어보세요.";
        }
        return "역할극 보이스 대화 계속입니다. 당신은 " + target.role + " " + target.name + "입니다. "
                + "상대 " + source.name + "의 방금 발화 원문을 아래에 그대로 전달받았습니다.\n"
                + "<<<상대 발화 원문>>>\n"
                + exactText + "\n"
                + "<<<끝>>>\n"
                + instruction + " 동시에 말하지 말고 당신 차례에만 한국어로 천천히 말하세요.";
    }

    private void setDualSideMicMuted(DualVoiceSide side, boolean muted, String source) {
        if (side == null || side.view == null) {
            return;
        }
        side.view.evaluateJavascript(buildMicTrackControlJs(muted, false), result ->
                missionLog("dual_mic_track_control", json()
                        .put("side", side.id)
                        .put("source", source)
                        .put("muted", muted)
                        .put("result", result)));
    }

    private void cleanupDualVoiceArtifacts(String reason) {
        if (dualSideA != null) {
            dualSideA.view.evaluateJavascript(buildMediaProbeCleanupScript(), null);
            setDualSideMicMuted(dualSideA, false, reason);
        }
        if (dualSideB != null) {
            dualSideB.view.evaluateJavascript(buildMediaProbeCleanupScript(), null);
            setDualSideMicMuted(dualSideB, false, reason);
        }
        setNativeMicMuted(false, reason);
        updateVoiceLevel(0.0, false, 0);
        updateDualVoiceLevel(dualSideB, 0.0, false, 0);
    }

    private void stopPhotoMission(String reason) {
        if (!missionRunning) {
            status("No mission running.");
            return;
        }
        missionLog("mission_stop", json().put("reason", reason).put("batchIndex", missionBatchIndex));
        if (isVoiceMission()) {
            cleanupVoiceArtifacts("stop-" + reason);
        }
        if (isDualVoiceMission()) {
            cleanupDualVoiceArtifacts("stop-" + reason);
        }
        missionRunning = false;
        dualTurnActive = false;
        autoUploadArmed = false;
        autoUploadUris.clear();
        currentBatch = null;
        if (missionLogger != null) {
            missionLogger.close();
            missionLogger = null;
        }
        status("Mission stopped: " + reason);
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
                probeAttachMenuAfterFallback(attempt, attachMode);
            });
        }, ATTACH_MENU_PROBE_DELAY_MS);
    }

    private void probeAttachMenuAfterFallback(int attempt, String attachMode) {
        mainHandler.postDelayed(() -> {
            if (!missionRunning || currentBatch == null) {
                return;
            }
            if (autoUploadDelivered) {
                missionLog("attach_menu_reprobe_skipped", json()
                        .put("batch", currentBatch.number)
                        .put("attempt", attempt)
                        .put("reason", "file-chooser-already-delivered"));
                waitAfterAttachAttempt(attempt);
                return;
            }
            webView.evaluateJavascript(buildUploadMenuTargetJs(attachMode, attempt), value -> {
                TargetPoint target = parseTargetPoint(value);
                missionLog("attach_menu_reprobe", json()
                        .put("batch", currentBatch == null ? -1 : currentBatch.number)
                        .put("attempt", attempt)
                        .put("mode", attachMode)
                        .put("raw", value)
                        .put("target", target.toJson()));
                if (target.found) {
                    tapTargetPoint(target, "attach_menu_reprobe_tapped", attempt);
                    waitAfterAttachAttempt(attempt);
                    return;
                }
                String directInputJs = buildAttachClickJs("file-input-menu-fallback-" + attachMode + "-" + attempt);
                webView.evaluateJavascript(directInputJs, result -> missionLog("attach_direct_input_fallback_result", json()
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
                + "if(attempt===0){hit=(nearRows.find(x=>words.test(x.label))||wordRows[0]||nearRows.find(x=>x.label)||nearRows[0]);}"
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

    private String buildMicCaptureHookJs() {
        return "(function(){"
                + "try{"
                + " const md=navigator.mediaDevices;"
                + " if(!md||!md.getUserMedia){return JSON.stringify({status:'no-getUserMedia'});}"
                + " window.__androidWtbMicStreams=window.__androidWtbMicStreams||[];"
                + " if(window.__androidWtbMicHook){return JSON.stringify({status:'already-installed',streams:window.__androidWtbMicStreams.length});}"
                + " const original=window.__androidWtbOriginalGetUserMedia||md.getUserMedia.bind(md);"
                + " window.__androidWtbOriginalGetUserMedia=original;"
                + " md.getUserMedia=function(constraints){"
                + "  return original(constraints).then(function(stream){"
                + "   try{"
                + "    if(constraints&&constraints.audio){"
                + "     window.__androidWtbMicStreams.push(stream);"
                + "     if(window.AndroidWebViewTestBed){AndroidWebViewTestBed.onEvent('mic-stream','tracks='+stream.getAudioTracks().length);}"
                + "    }"
                + "   }catch(e){}"
                + "   return stream;"
                + "  });"
                + " };"
                + " window.__androidWtbMicHook=true;"
                + " return JSON.stringify({status:'installed'});"
                + "}catch(e){return JSON.stringify({status:'error',error:String(e)});}"
                + "})();";
    }

    private String buildVoiceButtonScript() {
        return "(function(){"
                + "function label(el){return (el.innerText||el.textContent||el.ariaLabel||el.title||el.getAttribute('aria-label')||el.getAttribute('aria-labelledby')||el.getAttribute('data-testid')||'').replace(/\\s+/g,' ').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],[tabindex]')].filter(visible).filter(el=>!el.disabled);"
                + "const editors=[...document.querySelectorAll('#prompt-textarea,[data-testid=\"composer-text-input\"],textarea,[contenteditable=\"true\"],[role=\"textbox\"]')].filter(visible);"
                + "const editor=editors.length?editors[editors.length-1]:null;"
                + "const strong=/voice\\s*mode|start\\s*voice|voice\\s*chat|voice\\s*conversation|\\uC74C\\uC131\\s*\\uBAA8\\uB4DC|\\uBCF4\\uC774\\uC2A4\\s*\\uBAA8\\uB4DC|\\uC74C\\uC131\\s*\\uB300\\uD654/i;"
                + "const good=/voice|speak|talk|conversation|listen|headphone|\\uC74C\\uC131|\\uBCF4\\uC774\\uC2A4|\\uB300\\uD654|\\uB9D0\\uD558|\\uC2DC\\uC791/i;"
                + "const bad=/send|attach|upload|file|photo|image|stop|download|share|copy|\\uBCF4\\uB0B4|\\uCCA8\\uBD80|\\uC5C5\\uB85C\\uB4DC|\\uD30C\\uC77C|\\uC0AC\\uC9C4|\\uC774\\uBBF8\\uC9C0|\\uC911\\uC9C0|\\uB2E4\\uC6B4\\uB85C\\uB4DC/i;"
                + "const softBad=/dictation|microphone|\\bmic\\b|mute|unmute|\\uBC1B\\uC544\\uC4F0\\uAE30|\\uB9C8\\uC774\\uD06C|\\uC74C\\uC18C\\uAC70/i;"
                + "const scored=controls.map(el=>{const l=label(el);const r=el.getBoundingClientRect();let score=0;if(strong.test(l)){score+=90;}if(good.test(l)){score+=45;}if(bad.test(l)){score-=120;}if(softBad.test(l)&&!strong.test(l)){score-=35;}if(r.bottom>window.innerHeight*0.55){score+=8;}if(r.right>window.innerWidth*0.45){score+=4;}if(editor){const er=editor.getBoundingClientRect();if(r.top<er.bottom+120&&r.bottom>er.top-120){score+=8;if(r.left>er.right-170){score+=24;}if(r.left>er.right-115){score+=14;}}}return {el,l,score,x:Math.round((r.left+r.right)/2),y:Math.round((r.top+r.bottom)/2),w:Math.round(r.width),h:Math.round(r.height)};}).sort((a,b)=>b.score-a.score);"
                + "const best=scored[0]||null;"
                + "if(best&&best.score>25){best.el.click();return JSON.stringify({clicked:true,label:best.l,score:best.score,x:best.x,y:best.y,candidates:scored.slice(0,8).map(x=>({label:x.l,score:x.score,x:x.x,y:x.y}))});}"
                + "if(editor){"
                + " const er=editor.getBoundingClientRect();"
                + " const near=scored.filter(x=>{const r=x.el.getBoundingClientRect();return r.top<er.bottom+130&&r.bottom>er.top-120&&r.width>=28&&r.height>=28&&!bad.test(x.l)&&!softBad.test(x.l);}).sort((a,b)=>b.x-a.x);"
                + " const geom=near.find(x=>x.x>er.right-180)||near[0];"
                + " if(geom){geom.el.click();return JSON.stringify({clicked:true,geometryFallback:true,label:geom.l,score:geom.score,x:geom.x,y:geom.y,candidates:scored.slice(0,10).map(x=>({label:x.l,score:x.score,x:x.x,y:x.y,w:x.w,h:x.h}))});}"
                + "}"
                + "return JSON.stringify({clicked:false,editorFound:!!editor,candidates:scored.slice(0,12).map(x=>({label:x.l,score:x.score,x:x.x,y:x.y,w:x.w,h:x.h}))});"
                + "})();";
    }

    private String buildVoiceReadyScript() {
        return "(function(){"
                + "function label(el){return (el.innerText||el.textContent||el.ariaLabel||el.title||el.getAttribute('aria-label')||el.getAttribute('aria-labelledby')||el.getAttribute('data-testid')||'').replace(/\\s+/g,' ').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&r.top<window.innerHeight&&r.left<window.innerWidth&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],[tabindex]')].filter(visible).map(el=>label(el)).filter(Boolean);"
                + "const joined=controls.join(' | ');"
                + "const muteVisible=/mute|unmute|\\uC74C\\uC18C\\uAC70|\\uB9C8\\uC774\\uD06C/i.test(joined);"
                + "const leaveVisible=/leave|end\\s*voice|close\\s*voice|\\uB05D\\uB0B4|\\uC885\\uB8CC|\\uB098\\uAC00/i.test(joined);"
                + "const micVisible=/microphone|\\bmic\\b|\\uB9C8\\uC774\\uD06C/i.test(joined);"
                + "const listening=/listening|\\uB4E3\\uACE0|\\uB4E3\\uB294/i.test(joined);"
                + "const voiceOpen=muteVisible||leaveVisible||micVisible||listening;"
                + "return JSON.stringify({voiceOpen:voiceOpen,muteVisible:muteVisible,leaveVisible:leaveVisible,micVisible:micVisible,listening:listening,mediaCount:document.querySelectorAll('audio,video').length,labels:controls.slice(-16)});"
                + "})();";
    }

    private String buildKeyboardNudgeScript() {
        return "(function(){"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const selectors=['#prompt-textarea','[data-testid=\"composer-text-input\"]','textarea','[contenteditable=\"true\"]','[role=\"textbox\"]'];"
                + "let el=null;"
                + "for(const q of selectors){const list=[...document.querySelectorAll(q)].filter(visible);if(list.length){el=list[list.length-1];break;}}"
                + "if(!el){return JSON.stringify({status:'no-editor'});}"
                + "try{el.focus();el.click();}catch(e){}"
                + "return JSON.stringify({status:'focused',tag:el.tagName,role:el.getAttribute('role')||''});"
                + "})();";
    }

    private String buildMediaPlaybackProbeScript() {
        return "(function(){"
                + "try{"
                + " const threshold=0.0025;"
                + " const recentMs=" + VOICE_MEDIA_AUDIO_RECENT_MS + ";"
                + " const AC=window.AudioContext||window.webkitAudioContext;"
                + " const state=window.__androidWtbMediaProbe=window.__androidWtbMediaProbe||{items:[],lastSent:0};"
                + " if(!AC){return JSON.stringify({count:0,hasProbe:false,recentActive:false,maxRms:0,error:'no-audiocontext'});}"
                + " if(!state.ctx||state.ctx.state==='closed'){state.ctx=new AC();state.items=[];}"
                + " if(state.ctx.state==='suspended'){state.ctx.resume().catch(()=>{});}"
                + " const media=[...document.querySelectorAll('audio,video')].filter(m=>!m.muted&&m.readyState>1);"
                + " media.forEach(function(m){"
                + "  if(m.__androidWtbProbeIndex!==undefined){return;}"
                + "  try{"
                + "   const stream=m.captureStream?m.captureStream():(m.mozCaptureStream?m.mozCaptureStream():null);"
                + "   if(!stream){return;}"
                + "   const source=state.ctx.createMediaStreamSource(stream);"
                + "   const analyser=state.ctx.createAnalyser();"
                + "   analyser.fftSize=512;analyser.smoothingTimeConstant=0.2;"
                + "   source.connect(analyser);"
                + "   m.__androidWtbProbeIndex=state.items.length;"
                + "   state.items.push({source:source,analyser:analyser,buf:new Uint8Array(analyser.fftSize),lastActive:0});"
                + "  }catch(e){}"
                + " });"
                + " const now=performance.now();"
                + " let maxRms=0;"
                + " state.items.forEach(function(item){"
                + "  try{"
                + "   item.analyser.getByteTimeDomainData(item.buf);"
                + "   let sum=0;"
                + "   for(let i=0;i<item.buf.length;i++){const v=(item.buf[i]-128)/128;sum+=v*v;}"
                + "   const rms=Math.sqrt(sum/item.buf.length);"
                + "   if(rms>maxRms){maxRms=rms;}"
                + "   if(rms>threshold){item.lastActive=now;}"
                + "  }catch(e){}"
                + " });"
                + " const recentActive=state.items.some(item=>now-item.lastActive<=recentMs);"
                + " if(window.AndroidWebViewTestBed&&now-(state.lastSent||0)>80){"
                + "  state.lastSent=now;"
                + "  try{AndroidWebViewTestBed.onVoiceLevel(String(maxRms),String(recentActive),String(media.length));}catch(e){}"
                + " }"
                + " return JSON.stringify({count:media.length,probeCount:state.items.length,hasProbe:state.items.length>0,recentActive:recentActive,maxRms:maxRms,ctxState:state.ctx.state,voiceOpen:media.length>0,ts:Date.now()});"
                + "}catch(e){return JSON.stringify({count:0,hasProbe:false,recentActive:false,maxRms:0,error:String(e)});}"
                + "})();";
    }

    private String buildMediaProbeCleanupScript() {
        return "(function(){"
                + "try{"
                + " const state=window.__androidWtbMediaProbe;"
                + " if(!state){return JSON.stringify({status:'none'});}"
                + " (state.items||[]).forEach(function(item){try{item.source.disconnect();}catch(e){}try{item.analyser.disconnect();}catch(e){}});"
                + " if(state.ctx&&state.ctx.state!=='closed'){state.ctx.close().catch(()=>{});}"
                + " window.__androidWtbMediaProbe=null;"
                + " document.querySelectorAll('audio,video').forEach(m=>{try{delete m.__androidWtbProbeIndex;}catch(e){}});"
                + " return JSON.stringify({status:'closed'});"
                + "}catch(e){return JSON.stringify({status:'error',error:String(e)});}"
                + "})();";
    }

    private String buildVoiceResponseProbeScript() {
        return "(function(){"
                + "function text(el){return (el&&el.innerText?el.innerText:'').trim();}"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.getAttribute('aria-label')||el.getAttribute('data-testid')||'').replace(/\\s+/g,' ').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const assistantSelector='[data-message-author-role=\"assistant\"], article';"
                + "const assistantCount=document.querySelectorAll(assistantSelector).length;"
                + "function lastAssistantNode(){"
                + " let cont=window.__androidWtbTurns;"
                + " if(!cont||!cont.isConnected){const any=document.querySelector(assistantSelector);cont=any?(any.closest('main')||any.parentElement):null;window.__androidWtbTurns=cont;}"
                + " function lastMatch(root){if(!root){return null;}if(root.matches&&root.matches(assistantSelector)){return root;}const list=root.querySelectorAll?root.querySelectorAll(assistantSelector):[];return list.length?list[list.length-1]:null;}"
                + " for(let el=cont&&cont.lastElementChild;el;el=el.previousElementSibling){const hit=lastMatch(el);if(hit){return hit;}}"
                + " const all=document.querySelectorAll(assistantSelector);return all.length?all[all.length-1]:null;"
                + "}"
                + "const lastAssistant=text(lastAssistantNode());"
                + "const buttons=[...document.querySelectorAll('button')].filter(visible);"
                + "const buttonLabels=buttons.map(label);"
                + "const stopPattern=/stop|\\uC911\\uC9C0|\\uC815\\uC9C0/i;"
                + "const busyLabels=buttonLabels.filter(x=>stopPattern.test(x));"
                + "const busy=busyLabels.length>0;"
                + "const tail=lastAssistant.trim();"
                + "const sentenceComplete=/(?:[.!?。！？]|(?:\\uB2E4|\\uC694)\\.?)$/i.test(tail);"
                + "return JSON.stringify({assistantCount:assistantCount,lastAssistant:lastAssistant,lastAssistantLen:lastAssistant.length,busy:busy,busyLabels:busyLabels.slice(0,8),buttonCount:buttons.length,sentenceComplete:sentenceComplete,ts:Date.now()});"
                + "})();";
    }

    private String buildMicTrackControlJs(boolean muted, boolean stop) {
        return "(function(){"
                + "try{"
                + " const muted=" + muted + ";"
                + " const stop=" + stop + ";"
                + " const streams=window.__androidWtbMicStreams||[];"
                + " let tracks=0,live=0;"
                + " streams.forEach(function(stream){stream.getAudioTracks().forEach(function(track){tracks++;if(stop){track.stop();}else{track.enabled=!muted;}if(track.readyState==='live'){live++;}});});"
                + " window.__androidWtbMicMuted=muted;"
                + " return JSON.stringify({status:'ok',muted:muted,stop:stop,streams:streams.length,tracks:tracks,live:live});"
                + "}catch(e){return JSON.stringify({status:'error',error:String(e)});}"
                + "})();";
    }

    private String buildVoiceMuteScript(boolean muted) {
        return "(function(){"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.getAttribute('aria-label')||el.getAttribute('data-testid')||'').replace(/\\s+/g,' ').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const muted=" + muted + ";"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"]')].filter(visible).filter(el=>!el.disabled).map(el=>({el,label:label(el)}));"
                + "const muteWords=/mute|turn\\s*off.*(mic|microphone)|\\uB9C8\\uC774\\uD06C.*\\uB044|\\uC74C\\uC18C\\uAC70/i;"
                + "const openWords=/unmute|turn\\s*on.*(mic|microphone)|\\uB9C8\\uC774\\uD06C.*\\uCF1C|\\uC74C\\uC18C\\uAC70\\s*\\uD574\\uC81C/i;"
                + "const hit=controls.find(x=>muted?muteWords.test(x.label):openWords.test(x.label));"
                + "if(hit){hit.el.click();return JSON.stringify({clicked:true,label:hit.label,muted:muted});}"
                + "return JSON.stringify({clicked:false,muted:muted,candidates:controls.map(x=>x.label).filter(Boolean).slice(-16)});"
                + "})();";
    }

    private String buildVoiceDiagnosticsScript() {
        return "(function(){"
                + "function label(el){return (el.innerText||el.ariaLabel||el.title||el.getAttribute('aria-label')||el.getAttribute('data-testid')||'').replace(/\\s+/g,' ').trim();}"
                + "function visible(el){if(!el){return false;}const r=el.getBoundingClientRect();const s=getComputedStyle(el);return r.width>0&&r.height>0&&r.bottom>0&&r.right>0&&s.visibility!=='hidden'&&s.display!=='none';}"
                + "const controls=[...document.querySelectorAll('button,[role=\"button\"],[tabindex]')].filter(visible).map(el=>label(el)).filter(Boolean);"
                + "const streams=window.__androidWtbMicStreams||[];"
                + "let tracks=0,live=0,enabled=0;"
                + "streams.forEach(s=>s.getAudioTracks().forEach(t=>{tracks++;if(t.readyState==='live'){live++;}if(t.enabled){enabled++;}}));"
                + "return JSON.stringify({href:location.href,title:document.title,mediaCount:document.querySelectorAll('audio,video').length,micStreams:streams.length,micTracks:tracks,micLive:live,micEnabled:enabled,mediaProbe:!!window.__androidWtbMediaProbe,labels:controls.slice(-24)});"
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
                + "const assistantSelector='[data-message-author-role=\"assistant\"], article';"
                + "const assistantCount=document.querySelectorAll(assistantSelector).length;"
                + "function lastAssistantNode(){"
                + " let cont=window.__androidWtbTurns;"
                + " if(!cont||!cont.isConnected){const any=document.querySelector(assistantSelector);cont=any?(any.closest('main')||any.parentElement):null;window.__androidWtbTurns=cont;}"
                + " function lastMatch(root){if(!root){return null;}if(root.matches&&root.matches(assistantSelector)){return root;}const list=root.querySelectorAll?root.querySelectorAll(assistantSelector):[];return list.length?list[list.length-1]:null;}"
                + " for(let el=cont&&cont.lastElementChild;el;el=el.previousElementSibling){const hit=lastMatch(el);if(hit){return hit;}}"
                + " const all=document.querySelectorAll(assistantSelector);return all.length?all[all.length-1]:null;"
                + "}"
                + "const lastAssistant=text(lastAssistantNode());"
                + "const editor=!!document.querySelector('textarea,[contenteditable=\"true\"],[role=\"textbox\"]');"
                + "const buttons=[...document.querySelectorAll('button')].filter(visible);"
                + "const stopVisible=buttons.some(b=>/stop|중지|정지/i.test(b.innerText||b.ariaLabel||b.title||b.getAttribute('aria-label')||''));"
                + "const uploading=/uploading|첨부 중|업로드|upload failed|파일을 처리/i.test(bodyText);"
                + "return JSON.stringify({href:location.href,title:document.title,bodyLen:bodyText.length,bodyTail:bodyText.slice(-2400),assistantCount:assistantCount,lastAssistant:lastAssistant,lastAssistantLen:lastAssistant.length,editorExists:editor,stopVisible:stopVisible,uploadingVisible:uploading,ts:Date.now()});"
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

    private JSONObject parseJsObject(String value, String errorEvent) {
        try {
            return new JSONObject(unwrapJsString(value));
        } catch (Exception error) {
            missionLog(errorEvent, json()
                    .put("raw", value)
                    .put("error", error.getMessage()));
            return new JSONObject();
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

    private boolean isVoiceMission() {
        return MISSION_KIND_VOICE.equals(activeMissionKind);
    }

    private boolean isDualVoiceMission() {
        return MISSION_KIND_DUAL_VOICE.equals(activeMissionKind);
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

    private boolean hasRecordAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestVoicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_VOICE_PERMISSION);
        }
    }

    private void resetVoiceState() {
        voiceSawAudio = false;
        voiceLastAudioActive = false;
        voiceQuietStreak = 0;
        voiceTurnStartedAtMs = 0L;
        voiceLastTextProbeAtMs = 0L;
        voiceLastTextChangeAtMs = 0L;
        voiceLastAssistantText = "";
        updateVoiceLevel(0.0, false, 0);
    }

    private String voicePromptText() {
        String memo = memoEditor == null ? "" : memoEditor.getText().toString().trim();
        if (!memo.isEmpty()) {
            return memo;
        }
        return "웹뷰 보이스 테스트입니다. 천천히 두 문장으로 답하고, 마지막에 '음성 테스트 완료'라고 말해 주세요.";
    }

    private void setVoiceMicMuted(boolean muted, String source) {
        setNativeMicMuted(muted, source);
        webView.evaluateJavascript(buildMicTrackControlJs(muted, false), result ->
                missionLog("voice_mic_track_control", json()
                        .put("source", source)
                        .put("muted", muted)
                        .put("result", result)));
        status(muted ? "Voice mic muted." : "Voice mic open.");
    }

    private void setNativeMicMuted(boolean muted, String source) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMicrophoneMute(muted);
                voiceNativeMicMuted = muted;
                missionLog("voice_native_mic_mute", json()
                        .put("source", source)
                        .put("muted", muted));
            }
        } catch (Exception error) {
            missionLog("voice_native_mic_mute_failed", json()
                    .put("source", source)
                    .put("muted", muted)
                    .put("error", error.getMessage()));
        }
    }

    private void runVoiceDiagnostics(String source) {
        webView.evaluateJavascript(buildVoiceDiagnosticsScript(), result -> {
            missionLog("voice_diagnostics", json()
                    .put("source", source)
                    .put("result", result));
            status("Voice diag: " + result);
        });
    }

    private void cleanupVoiceArtifacts(String reason) {
        webView.evaluateJavascript(buildMediaProbeCleanupScript(), result ->
                missionLog("voice_media_probe_cleanup", json()
                        .put("reason", reason)
                        .put("result", result)));
        webView.evaluateJavascript(buildMicTrackControlJs(false, false), result ->
                missionLog("voice_mic_track_cleanup", json()
                        .put("reason", reason)
                        .put("result", result)));
        setNativeMicMuted(false, reason);
        updateVoiceLevel(0.0, false, 0);
    }

    private void updateVoiceLevel(double rms, boolean active, int mediaCount) {
        if (voiceLevelBar == null) {
            return;
        }
        double scaled = Math.max(0.0, Math.min(1.0, rms * AUDIO_LEVEL_RMS_GAIN));
        if (active) {
            scaled = Math.max(0.08, scaled);
        } else {
            scaled = scaled * 0.4;
        }
        voiceLevelBar.setScaleX((float) scaled);
        voiceLevelBar.setAlpha(active ? 1.0f : mediaCount > 0 ? 0.45f : 0.22f);
        voiceLevelBar.setBackgroundColor(active ? Color.rgb(30, 170, 90) : Color.rgb(120, 160, 135));
    }

    private void updateDualVoiceLevel(DualVoiceSide side, double rms, boolean active, int mediaCount) {
        View bar = side != null && "B".equals(side.id) ? dualVoiceLevelBar : voiceLevelBar;
        if (bar == null) {
            return;
        }
        double scaled = Math.max(0.0, Math.min(1.0, rms * AUDIO_LEVEL_RMS_GAIN));
        if (active) {
            scaled = Math.max(0.08, scaled);
        } else {
            scaled = scaled * 0.4;
        }
        bar.setScaleX((float) scaled);
        bar.setAlpha(active ? 1.0f : mediaCount > 0 ? 0.45f : 0.22f);
        if (side != null && "B".equals(side.id)) {
            bar.setBackgroundColor(active ? Color.rgb(70, 120, 230) : Color.rgb(120, 135, 170));
        } else {
            bar.setBackgroundColor(active ? Color.rgb(30, 170, 90) : Color.rgb(120, 160, 135));
        }
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
        if (requestCode == REQ_VOICE_PERMISSION) {
            if (hasRecordAudioPermission()) {
                if (pendingDualVoiceStart) {
                    pendingDualVoiceStart = false;
                    status("Microphone permission granted. Starting dual voice mission.");
                    mainHandler.postDelayed(this::startDualVoiceMission, 300);
                } else {
                    status("Microphone permission granted. Starting voice mission.");
                    mainHandler.postDelayed(this::startVoiceMission, 300);
                }
            } else {
                pendingDualVoiceStart = false;
                status("Microphone permission denied. Voice test cannot start.");
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
                case KeyEvent.KEYCODE_V:
                    startVoiceMission();
                    return true;
                case KeyEvent.KEYCODE_D:
                    startDualVoiceMission();
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
            if (!(missionRunning && isVoiceMission())) {
                injectTextObserver();
            }
            status("Loaded " + url);
        }
    }

    private class DualWebViewClient extends WebViewClient {
        final String side;

        DualWebViewClient(String side) {
            this.side = side;
        }

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
            missionLog("dual_page_finished", json()
                    .put("side", side)
                    .put("url", url));
            status("Loaded dual " + side + ": " + url);
        }
    }

    private class DualChromeClient extends WebChromeClient {
        final String side;

        DualChromeClient(String side) {
            this.side = side;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> {
                if (hasNeededAndroidPermissions(request.getResources())) {
                    request.grant(request.getResources());
                    status("Granted web permission " + side + ".");
                } else {
                    pendingPermissionRequest = request;
                    requestAndroidPermissionsForResources(request.getResources());
                    status("Requesting Android permission " + side + ".");
                }
            });
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            missionLog("dual_web_console", json()
                    .put("side", side)
                    .put("message", consoleMessage.message())
                    .put("source", consoleMessage.sourceId())
                    .put("line", consoleMessage.lineNumber())
                    .put("level", String.valueOf(consoleMessage.messageLevel())));
            return true;
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress < 100 && isDualVoiceMission()) {
                status("Loading " + side + " " + newProgress + "%");
            }
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
        final String side;

        TestBridge() {
            this("A");
        }

        TestBridge(String side) {
            this.side = side;
        }

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
                        .put("side", side)
                        .put("chars", text == null ? 0 : text.length())
                        .put("preview", preview));
                status(message);
            });
        }

        @JavascriptInterface
        public void onEvent(String kind, String payload) {
            runOnUiThread(() -> {
                missionLog("bridge_event", json()
                        .put("side", side)
                        .put("kind", kind)
                        .put("payload", payload));
                status(side + " " + kind + ": " + payload);
            });
        }

        @JavascriptInterface
        public void onVoiceLevel(String rmsText, String activeText, String countText) {
            double rms = 0.0;
            int count = 0;
            try {
                rms = Double.parseDouble(rmsText);
            } catch (Exception ignored) {
            }
            try {
                count = Integer.parseInt(countText);
            } catch (Exception ignored) {
            }
            boolean active = "true".equalsIgnoreCase(activeText);
            final double finalRms = rms;
            final int finalCount = count;
            runOnUiThread(() -> {
                if ("B".equals(side) && dualSideB != null) {
                    updateDualVoiceLevel(dualSideB, finalRms, active, finalCount);
                } else {
                    updateVoiceLevel(finalRms, active, finalCount);
                }
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

    private static class DualVoiceSide {
        final String id;
        final String name;
        final String role;
        final WebView view;
        final View levelBar;
        boolean ready;
        boolean sawAudio;
        boolean lastAudioActive;
        int quietStreak;
        int turnGeneration;
        long turnStartedAtMs;
        long lastTextProbeAtMs;
        long lastTextChangeAtMs;
        long lastTextStableProbeAtMs;
        long lastAudioActiveAtMs;
        long audioQuietStartedAtMs;
        double lastAudioRms;
        double lastAudioLevelPercent;
        double lastAudioDisplayLevelPercent;
        double audioQuietRunMaxRms;
        double audioQuietRunMaxLevelPercent;
        boolean lastTextBusy;
        boolean lastSentenceComplete;
        int preAssistantCount = -1;
        int lastButtonCount = -1;
        JSONArray lastBusyLabels = new JSONArray();
        String lastAssistantText = "";
        final ArrayList<AudioLevelSample> audioLevelSamples = new ArrayList<>();

        DualVoiceSide(String id, String name, String role, WebView view, View levelBar) {
            this.id = id;
            this.name = name;
            this.role = role;
            this.view = view;
            this.levelBar = levelBar;
        }

        void resetTurn() {
            sawAudio = false;
            lastAudioActive = false;
            quietStreak = 0;
            turnStartedAtMs = 0L;
            lastTextProbeAtMs = 0L;
            lastTextChangeAtMs = 0L;
            lastTextStableProbeAtMs = 0L;
            lastAudioActiveAtMs = 0L;
            audioQuietStartedAtMs = 0L;
            lastAudioRms = 0.0;
            lastAudioLevelPercent = 0.0;
            lastAudioDisplayLevelPercent = 0.0;
            audioQuietRunMaxRms = 0.0;
            audioQuietRunMaxLevelPercent = 0.0;
            lastTextBusy = false;
            lastSentenceComplete = false;
            preAssistantCount = -1;
            lastButtonCount = -1;
            lastBusyLabels = new JSONArray();
            lastAssistantText = "";
            audioLevelSamples.clear();
        }

        void recordAudioSample(
                long timeMs,
                double rms,
                double levelPercent,
                double displayLevelPercent,
                boolean thresholdActive,
                boolean recentActive,
                boolean textStable
        ) {
            audioLevelSamples.add(new AudioLevelSample(
                    timeMs,
                    rms,
                    levelPercent,
                    displayLevelPercent,
                    thresholdActive,
                    recentActive,
                    textStable
            ));
            long cutoff = timeMs - DUAL_AUDIO_TAIL_WINDOW_MS - 500L;
            while (!audioLevelSamples.isEmpty() && audioLevelSamples.get(0).timeMs < cutoff) {
                audioLevelSamples.remove(0);
            }
        }
    }

    private static class AudioLevelSample {
        final long timeMs;
        final double rms;
        final double levelPercent;
        final double displayLevelPercent;
        final boolean thresholdActive;
        final boolean recentActive;
        final boolean textStable;

        AudioLevelSample(
                long timeMs,
                double rms,
                double levelPercent,
                double displayLevelPercent,
                boolean thresholdActive,
                boolean recentActive,
                boolean textStable
        ) {
            this.timeMs = timeMs;
            this.rms = rms;
            this.levelPercent = levelPercent;
            this.displayLevelPercent = displayLevelPercent;
            this.thresholdActive = thresholdActive;
            this.recentActive = recentActive;
            this.textStable = textStable;
        }
    }

    private static class AudioTailStats {
        long windowMs;
        long windowStartAgoMs;
        int sampleCount;
        int candidateCount;
        int zeroSamples;
        int thresholdActiveSamples;
        int recentActiveSamples;
        int textStableSamples;
        double rawP50LevelPercent;
        double rawP90LevelPercent;
        double rawMaxLevelPercent;
        double displayP50LevelPercent;
        double displayP90LevelPercent;
        double displayMaxLevelPercent;
        double chosenLevelPercent;
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
