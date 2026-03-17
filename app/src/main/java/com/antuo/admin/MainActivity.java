package com.antuo.admin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.Manifest;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private ProgressBar progressBar;
    private static final String TARGET_URL = "http://175.178.45.188";
    private static final String API_VERSION_CHECK = TARGET_URL + "/api/version/check";

    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraImageUri;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int PERMISSION_REQUEST_CODE = 1002;

    private Handler mainHandler;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        progressBar = findViewById(R.id.progress_bar);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(settings.getUserAgentString() + " AntuoApp/1.0");

        webView.setBackgroundColor(0x00000000);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url);
                    return true;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                webView.setBackgroundColor(0xFF1565C0);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
                fileUploadCallback = filePathCallback;
                requestPermissionsIfNeeded();
                openFileChooser();
                return true;
            }
        });

        checkForUpdates();
    }

    private void checkForUpdates() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String currentVersion = "1.0.4";
                    try {
                        PackageManager pm = getPackageManager();
                        currentVersion = pm.getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}

                    String platform = "admin";
                    URL url = new URL(API_VERSION_CHECK + "?platform=" + platform + "&currentVersion=" + currentVersion);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        InputStream is = conn.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            sb.append(new String(buffer, 0, len, "UTF-8"));
                        }
                        is.close();
                        conn.disconnect();

                        final JSONObject json = new JSONObject(sb.toString());
                        if (json.optBoolean("needsUpdate", false)) {
                            final String latestVersion = json.optString("latestVersion", "");
                            final String downloadUrl = json.optString("downloadUrl", "");
                            final String releaseNotes = json.optString("releaseNotes", "");
                            final boolean forceUpdate = json.optBoolean("forceUpdate", false);

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showUpdateDialog(latestVersion, downloadUrl, releaseNotes, forceUpdate);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception ignored) {}

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl(TARGET_URL);
                    }
                });
            }
        }).start();
    }

    private void showUpdateDialog(String version, String downloadUrl, String notes, boolean forceUpdate) {
        String message = "发现新版本 " + version + "\n\n更新内容:\n" + notes;
        if (forceUpdate) {
            message += "\n\n⚠️ 必须更新才能继续使用";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("发现新版本");
        builder.setMessage(message);
        builder.setCancelable(!forceUpdate);

        if (forceUpdate) {
            builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    downloadAndInstall(downloadUrl);
                }
            });
        } else {
            builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    downloadAndInstall(downloadUrl);
                }
            });
            builder.setNegativeButton("暂不更新", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    webView.loadUrl(TARGET_URL);
                }
            });
        }

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (forceUpdate) {
                    finish();
                } else {
                    webView.loadUrl(TARGET_URL);
                }
            }
        });

        builder.show();
    }

    private void downloadAndInstall(final String downloadUrl) {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(this, "下载链接无效", Toast.LENGTH_SHORT).show();
            webView.loadUrl(TARGET_URL);
            return;
        }

        Toast.makeText(this, "正在下载更新...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(downloadUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(30000);

                    File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "antuo_admin_update.apk");
                    FileOutputStream fos = new FileOutputStream(apkFile);

                    InputStream is = conn.getInputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    int totalLen = conn.getContentLength();
                    int downloaded = 0;

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        if (totalLen > 0) {
                            final int progress = (downloaded * 100) / totalLen;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "下载中: " + progress + "%", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }

                    fos.close();
                    is.close();
                    conn.disconnect();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            installApk(apkFile);
                        }
                    });

                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            webView.loadUrl(TARGET_URL);
                        }
                    });
                }
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "安装失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            webView.loadUrl(TARGET_URL);
        }
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.CAMERA
                };
            } else {
                perms = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                };
            }
            boolean needRequest = false;
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                requestPermissions(perms, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("ANTUO_" + timeStamp, ".jpg", storageDir);
    }

    private void openFileChooser() {
        Intent cameraIntent = null;
        try {
            File photoFile = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                photoFile
            );
            cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            cameraImageUri = null;
            cameraIntent = null;
        }

        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryIntent.setType("image/*");
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES,
            new String[]{"image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif"});
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(galleryIntent, "选择文件");
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }

        if (chooser.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
        } else {
            startActivityForResult(galleryIntent, FILE_CHOOSER_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST) return;
        if (fileUploadCallback == null) return;

        Uri[] results = null;
        try {
            if (resultCode == Activity.RESULT_OK) {
                if (data == null || data.getData() == null) {
                    if (cameraImageUri != null) {
                        results = new Uri[]{cameraImageUri};
                    }
                } else {
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else {
                        String dataStr = data.getDataString();
                        if (dataStr != null) {
                            results = new Uri[]{Uri.parse(dataStr)};
                        }
                    }
                }
            }
        } catch (Exception e) {
            results = null;
        }

        try {
            fileUploadCallback.onReceiveValue(results);
        } catch (Exception e) {}
        fileUploadCallback = null;
        cameraImageUri = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted && fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = null;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
