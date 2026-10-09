package com.lanmessenger.apk;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import android.util.Log;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;
    private String[] pendingAndroidPermissions;
    private AudioManager audioManager;
    private boolean communicationModeSet = false;
    private MediaRecorder nativeRecorder;
    private File nativeRecordingFile;
    private boolean pendingNativeStart = false;

    private static final int FILE_PICKER = 7001;
    private static final int PERMISSION_REQ = 7002;
    private static final int INITIAL_PERMISSION_REQ = 7003;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setupWebView();
        web.addJavascriptInterface(new NativeRecorderBridge(), "AndroidRecorder");

        // Request the native microphone permission before the web page starts.
        // This avoids a race where Chromium/WebView tries to open the audio device
        // before Android has finished granting RECORD_AUDIO.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, INITIAL_PERMISSION_REQ);
        }
        web.loadUrl("file:///android_asset/index.html");
    }

    private final class NativeRecorderBridge {
        @JavascriptInterface public void startRecording() {
            runOnUiThread(() -> {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingNativeStart = true;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, INITIAL_PERMISSION_REQ);
                    return;
                }
                startNativeRecordingInternal();
            });
        }
        @JavascriptInterface public void stopRecording() {
            runOnUiThread(() -> stopNativeRecordingInternal());
        }
        @JavascriptInterface public void cancelRecording() {
            runOnUiThread(() -> cancelNativeRecordingInternal());
        }
    }

    private void startNativeRecordingInternal() {
        if (nativeRecorder != null) return;
        try {
            nativeRecordingFile = File.createTempFile("lan_voice_", ".m4a", getCacheDir());
            nativeRecorder = new MediaRecorder();
            nativeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            nativeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            nativeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            nativeRecorder.setAudioEncodingBitRate(96000);
            nativeRecorder.setAudioSamplingRate(44100);
            nativeRecorder.setOutputFile(nativeRecordingFile.getAbsolutePath());
            nativeRecorder.prepare();
            nativeRecorder.start();
            web.evaluateJavascript("window.__nativeRecorderStarted&&window.__nativeRecorderStarted()", null);
        } catch (Exception e) {
            releaseNativeRecorder();
            deleteNativeRecordingFile();
            web.evaluateJavascript("window.__nativeRecorderFailed&&window.__nativeRecorderFailed('شروع ضبط صدا انجام نشد؛ اجازه میکروفون را بررسی کن.')", null);
        }
    }

    private void stopNativeRecordingInternal() {
        if (nativeRecorder == null) {
            web.evaluateJavascript("window.__nativeRecorderFailed&&window.__nativeRecorderFailed('ضبط فعالی برای توقف وجود ندارد.')", null);
            return;
        }
        try {
            nativeRecorder.stop();
            releaseNativeRecorder();
            if (nativeRecordingFile == null || !nativeRecordingFile.isFile() || nativeRecordingFile.length() == 0) {
                deleteNativeRecordingFile();
                web.evaluateJavascript("window.__nativeRecorderFailed&&window.__nativeRecorderFailed('صدایی ضبط نشد؛ دوباره امتحان کن.')", null);
                return;
            }
            byte[] data = readAllBytes(nativeRecordingFile);
            String encoded = Base64.encodeToString(data, Base64.NO_WRAP);
            final int chunkSize = 48000;
            int total = (encoded.length() + chunkSize - 1) / chunkSize;
            for (int i = 0; i < total; i++) {
                String chunk = encoded.substring(i * chunkSize, Math.min(encoded.length(), (i + 1) * chunkSize));
                web.evaluateJavascript("window.__nativeRecorderChunk&&window.__nativeRecorderChunk(" + i + "," + total + ",'" + chunk + "')", null);
            }
            String name = "voice-" + System.currentTimeMillis() + ".m4a";
            String quotedName = org.json.JSONObject.quote(name);
            web.evaluateJavascript("window.__nativeRecorderChunksDone&&window.__nativeRecorderChunksDone('audio/mp4'," + quotedName + ")", null);
        } catch (Exception e) {
            releaseNativeRecorder();
            web.evaluateJavascript("window.__nativeRecorderFailed&&window.__nativeRecorderFailed('فایل ویس ساخته نشد؛ مدت ضبط را کمی بیشتر کن و دوباره امتحان کن.')", null);
        } finally {
            deleteNativeRecordingFile();
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private void cancelNativeRecordingInternal() {
        try { if (nativeRecorder != null) nativeRecorder.stop(); } catch (Exception ignored) {}
        releaseNativeRecorder();
        deleteNativeRecordingFile();
    }

    private void releaseNativeRecorder() {
        if (nativeRecorder != null) {
            try { nativeRecorder.reset(); } catch (Exception ignored) {}
            try { nativeRecorder.release(); } catch (Exception ignored) {}
            nativeRecorder = null;
        }
    }

    private void deleteNativeRecordingFile() {
        if (nativeRecordingFile != null) {
            try { nativeRecordingFile.delete(); } catch (Exception ignored) {}
            nativeRecordingFile = null;
        }
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return false; }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    Log.d("LANMessenger", "WebView permission request: origin="
                            + request.getOrigin() + " resources="
                            + java.util.Arrays.toString(request.getResources()));
                    handleWebPermission(request);
                });
            }

            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    Intent intent = params.createIntent();
                    if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    startActivityForResult(intent, FILE_PICKER);
                } catch (Exception e) {
                    // Fallback for WebView/file-picker combinations where createIntent fails.
                    try {
                        Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        fallback.addCategory(Intent.CATEGORY_OPENABLE);
                        fallback.setType("*/*");
                        fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                        startActivityForResult(fallback, FILE_PICKER);
                    } catch (Exception ignored) {
                        fileCallback = null;
                        Toast.makeText(MainActivity.this, "انتخاب فایل در دسترس نیست", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
                return true;
            }
        });

        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "باز کردن فایل امکان‌پذیر نیست", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleWebPermission(PermissionRequest request) {
        // Only honor requests from the page currently displayed in this WebView.
        if (!isSameOriginAsCurrentPage(request.getOrigin())) {
            request.deny();
            return;
        }

        ArrayList<String> androidPermissions = new ArrayList<>();
        boolean needsAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                needsAudio = true;
                addIfMissing(androidPermissions, Manifest.permission.RECORD_AUDIO);
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                addIfMissing(androidPermissions, Manifest.permission.CAMERA);
            }
        }

        if (androidPermissions.isEmpty()) {
            request.deny();
            return;
        }

        // Android WebView/Chromium audio capture can require the app to switch
        // into communication mode before the audio source is opened.
        if (needsAudio) {
            enterCommunicationMode();
        }

        ArrayList<String> missing = new ArrayList<>();
        for (String permission : androidPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }

        if (missing.isEmpty()) {
            grantWebResources(request);
            return;
        }

        if (pendingWebPermission != null) {
            pendingWebPermission.deny();
        }
        pendingWebPermission = request;
        pendingAndroidPermissions = missing.toArray(new String[0]);
        requestPermissions(pendingAndroidPermissions, PERMISSION_REQ);
    }

    private void enterCommunicationMode() {
        try {
            if (audioManager != null && !communicationModeSet) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                communicationModeSet = true;
            }
        } catch (Exception e) {
            Log.w("LANMessenger", "Could not set communication audio mode", e);
        }
    }

    private void restoreAudioMode() {
        try {
            if (audioManager != null && communicationModeSet) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                communicationModeSet = false;
            }
        } catch (Exception e) {
            Log.w("LANMessenger", "Could not restore audio mode", e);
        }
    }

    private void grantWebResources(PermissionRequest request) {
        ArrayList<String> granted = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            } else if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                granted.add(resource);
            }
        }
        if (granted.isEmpty()) request.deny();
        else request.grant(granted.toArray(new String[0]));
    }

    private boolean isSameOriginAsCurrentPage(Uri requested) {
        if (requested == null || web == null || web.getUrl() == null) return false;
        Uri current = Uri.parse(web.getUrl());
        String rs = requested.getScheme();
        String cs = current.getScheme();
        if (rs == null || cs == null || !rs.equalsIgnoreCase(cs)) return false;
        String rh = requested.getHost();
        String ch = current.getHost();
        if (rh == null || ch == null || !rh.equalsIgnoreCase(ch)) return false;
        int rp = requested.getPort();
        int cp = current.getPort();
        if (rp == -1) rp = defaultPort(rs);
        if (cp == -1) cp = defaultPort(cs);
        return rp == cp;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    private void addIfMissing(List<String> list, String value) {
        if (!list.contains(value)) list.add(value);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == INITIAL_PERMISSION_REQ) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (pendingNativeStart) {
                pendingNativeStart = false;
                if (granted) startNativeRecordingInternal();
                else web.evaluateJavascript("window.__nativeRecorderFailed&&window.__nativeRecorderFailed('اجازه میکروفون داده نشد؛ از تنظیمات برنامه اجازه Microphone را فعال کن.')", null);
            }
            return;
        }
        if (requestCode != PERMISSION_REQ) return;

        PermissionRequest request = pendingWebPermission;
        pendingWebPermission = null;
        pendingAndroidPermissions = null;
        if (request == null) return;

        boolean allGranted = true;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) grantWebResources(request);
        else { request.deny(); restoreAudioMode(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        if (pendingWebPermission != null) pendingWebPermission.deny();
        restoreAudioMode();
        cancelNativeRecordingInternal();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
