package xyz.pisoj.webviewexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private Uri downloadUri;
    private String downloadUserAgent;
    private String downloadMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = findViewById(R.id.web_view);
        webView.loadUrl("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf");

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            downloadUri = Uri.parse(url);
            downloadUserAgent = userAgent;
            downloadMimeType = mimeType;

            // Permission is only required for API level <= 28 && Permissions are granted at runtime only for API level 23 and above
            if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                boolean hasWriteExternalStoragePermission =
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if(!hasWriteExternalStoragePermission) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                } else {
                    download();
                }
            } else {
                download();
            }
        });
    }

    private void download() {
        String[] path = downloadUri.getPath().split("/");
        String fileName = path[path.length - 1];
        if(!fileName.contains(".")) {
            fileName += MimeTypeMap.getSingleton().getExtensionFromMimeType(downloadMimeType);
        }

        String cookies = CookieManager.getInstance().getCookie(downloadUri.toString());
        // There is a bug in certain versions of WebView related to same-site cookies not being returned by the CookieManager
        // so if you know you will only be downloading files behind authentication you should here probably display a dialog saying the file cannot be downloaded and request the user to update there WebView
        /*if(cookies == null) {
            new AlertDialog.Builder(this)
                    .setMessage("The file cannot be downloaded, try updating your WebView.")
                    .setPositiveButton("Okay", (DialogInterface p0, int p1) -> {})
                    .show();
            return;
        }*/

        DownloadManager.Request request = new DownloadManager.Request(downloadUri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        if(cookies != null) { request.addRequestHeader("cookie", cookies); }
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.addRequestHeader("User-Agent", downloadUserAgent);
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadManager.enqueue(request);
    }

    @SuppressLint("NewApi")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 0 && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                download();
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                new AlertDialog.Builder(this)
                        .setMessage("You permanently declined the storage permission witch is required to download this file. If you want to download it go to the settings and allow the storage permission.")
                        .setPositiveButton("Settings", (DialogInterface p0, int p1) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri intentUri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(intentUri);
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", (DialogInterface p0, int p1) -> {
                        })
                        .show();
            }
        }
    }
}