Repository created to show how to implement file download functionality in a WebView, **Complementary to [this](https://stackoverflow.com/a/76823847/18939913) StackOverflow question.**

**TL;DR** [Set download listener][1] on the `WebView` and use the [`DownloadManager`][2] to download the file to the **external Downloads directory**.

## Downloading:

1. **Parse the URL** string to [`Uri`][3] object.
2. Extract the last part of the path that is probably the **file name**. It is also a good idea to check if the file **has an extension** by checking if it contains a dot in its name, if it doesn't you can use [`MimeTypeMap`][4] to get extension based on the [`mimeType`][5].
3. Construct a [`Request`][6] object and set the destination to the external **Downloads directory**. Also set the [`User-Agent`][7] to maintain consistency with requests sent by `WebView` and make **notification** show when the **download completes** so the user knows it's done. 
4. Get [`DownloadManager`][2] system service and **start the download**.

```java
WebView webView = findViewById(R.id.web_view);
webView.loadUrl("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf");

webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
    Uri uri = Uri.parse(url);

    String[] path = uri.getPath().split("/");
    String fileName = path[path.length - 1];
    if(!fileName.contains(".")) {
        fileName += MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    }

    DownloadManager.Request request = new DownloadManager.Request(uri);

    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    request.addRequestHeader("User-Agent", userAgent);
    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    downloadManager.enqueue(request);
});
```

## Permission handling:

This will work, but only on Android devices running **API level 29+** because on any older version, your app is required to have the [`WRITE_EXTERNAL_STORAGE`][8] permission to write to the **shared Downlaods directory** even if it's through the `DownloadManager`. If you want to support older versions (you really should) follow these steps:

1. Declare the permission in your [`Manifest`][9], remember, it's **only needed for API level <=28**.
```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
```

2. Add `uri, userAgent and mimeType` as global variables in the Activity so they can be used in other functions
```java
public class MainActivity extends AppCompatActivity {
    private Uri downloadUri;
    private String downloadUserAgent;
    private String downloadMimeType;

    ...
```

3. Abstract the download logic to a separate function so it can be reused and make it use global variables from the previous step.
```java
private void download() {
    String[] path = downloadUri.getPath().split("/");
    String fileName = path[path.length - 1];
    if(!fileName.contains(".")) {
        fileName += MimeTypeMap.getSingleton().getExtensionFromMimeType(downloadMimeType);
    }

    DownloadManager.Request request = new DownloadManager.Request(downloadUri);
    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    request.addRequestHeader("User-Agent", downloadUserAgent);
    DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    downloadManager.enqueue(request);
}
```

4. In `setDownloadListener` assign values to global variables. Check the device API level because, as described earlier, we only need the permission for API level **lower than 29** and [permissions][10] are only granted **at runtime** on API level greater than 22 (before they were granted at the installation). If permission is already granted simply start the download otherwise **request it**.
```java
webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
    downloadUri = Uri.parse(url);
    downloadUserAgent = userAgent;
    downloadMimeType = mimeType;

    // Permission is only required for API level <= 28 && Permissions are granted at runtime only for API level 23 and above
    if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    boolean hasWriteExternalStoragePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if(!hasWriteExternalStoragePermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        } else {
            download();
        }
    } else {
        download();
    }
});
```

5. Handle the permission request result by overriding the [`onRequestPermissionsResult`][11] function and first checking if the `request code` is **0**, this is to ensure permission result is referencing to the `permission request` we created earlier (we've set *0* as the request code there), in the same statement we also check that there is an actual result to check. After that, we check if the first (and in our case the only) permission has become **granted**, if it has we again **start the download**, but if it's not, then we have some more work to do. If the user has **denied** it, we check if the [`shouldShowRequestPermissionRationale`][12] returns false, this can be confusing, but what we've essentially done is we've checked if the user has **permanently declined** the permission, if they had, we show them a little dialog telling them it is **required for the file download** and directing them to the **settings** where they can **enable it**. P.S. You can suppress `newAPI` since it'll never be call on the "old API level" because of the if check we've implemented in step 4 that.
```java
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
                    .setNegativeButton("Cancel", (DialogInterface p0, int p1) -> {})
                    .show();
        }
    }
}
```

## Cookies:

I know this is a long answer already, but I feel a need to tell you just one more thing and that is what to do if your file requires **authentication to access** and how you can pass **cookies** from the `WebView` to the `DownloadManager`.

To get cookies you can use the [`CookieManager`][13]. It will return string formatted the way you can put it directly into a [cookie header][14]. You need to be careful thou, because the `CookieManager` return **`null`** if there is no cookies or in the case of a **bug** (described in the code comment).
```java
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
```


  [1]: https://developer.android.com/reference/android/webkit/WebView#setDownloadListener(android.webkit.DownloadListener)
  [2]: https://developer.android.com/reference/android/app/DownloadManager
  [3]: https://developer.android.com/reference/android/net/Uri
  [4]: https://developer.android.com/reference/android/webkit/MimeTypeMap
  [5]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
  [6]: https://developer.android.com/reference/android/app/DownloadManager.Request
  [7]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent
  [8]: https://developer.android.com/reference/android/Manifest.permission#WRITE_EXTERNAL_STORAGE
  [9]: https://developer.android.com/guide/topics/manifest/manifest-intro
  [10]: https://developer.android.com/training/permissions/requesting
  [11]: https://developer.android.com/reference/androidx/core/app/ActivityCompat.OnRequestPermissionsResultCallback
  [12]: https://developer.android.com/reference/androidx/core/app/ActivityCompat#shouldShowRequestPermissionRationale(android.app.Activity,java.lang.String)
  [13]: https://developer.android.com/reference/android/webkit/CookieManager
  [14]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cookie
  
