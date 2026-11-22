package com.nxnative;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {
    private static final String DEFAULT_REPO = "DaKaufeeBoii/Galar-Native-Android";
    private static final String UPSTREAM_REPO = "simipoB/Galar-Native-Android";

    public static void check(Activity a, boolean quiet) {
        new Thread(() -> {
            try {
                String repo = DEFAULT_REPO;
                String[] appInfo = GameSettings.app(a);
                if (appInfo.length >= 2 && appInfo[1] != null && appInfo[1].contains("/")) {
                    String r = appInfo[1].replace("github.com/", "").trim();
                    if (!r.isEmpty()) repo = r;
                }

                JSONObject release = fetchLatestRelease(repo);
                if (release == null && !repo.equals(UPSTREAM_REPO)) {
                    release = fetchLatestRelease(UPSTREAM_REPO);
                }

                if (release == null) {
                    if (!quiet) showToast(a, "Could not check for updates. Check internet connection.");
                    return;
                }

                String tagName = release.optString("tag_name", "");
                String releaseName = release.optString("name", tagName);
                String releaseBody = release.optString("body", "");

                // Find APK asset download URL
                String apkUrl = null;
                String apkName = "GalarNative-Update.apk";
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            apkName = name;
                            break;
                        }
                    }
                }

                if (apkUrl == null) {
                    if (!quiet) showToast(a, "No APK asset found in latest release.");
                    return;
                }

                // Check version
                int currentCode = 0;
                String currentVersion = "";
                try {
                    PackageInfo pInfo = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                    currentCode = pInfo.versionCode;
                    currentVersion = pInfo.versionName;
                } catch (Exception ignored) {}

                if (isNewer(tagName, currentCode, currentVersion)) {
                    final String finalApkUrl = apkUrl;
                    final String finalApkName = apkName;
                    a.runOnUiThread(() -> showUpdateDialog(a, releaseName, releaseBody, finalApkUrl, finalApkName));
                } else {
                    if (!quiet) showToast(a, "You are using the latest version (" + currentVersion + ").");
                }
            } catch (Exception e) {
                if (!quiet) showToast(a, "Update check failed: " + e.getMessage());
            }
        }).start();
    }

    private static JSONObject fetchLatestRelease(String repo) {
        try {
            URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "GalarNative-Android");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNewer(String tag, int currentCode, String currentVersion) {
        String clean = tag.replaceAll("[^0-9.]", "").trim();
        if (clean.isEmpty()) return false;
        try {
            int tagNum = Integer.parseInt(clean.replace(".", ""));
            String curClean = (currentVersion != null ? currentVersion : "").replaceAll("[^0-9.]", "").replace(".", "").trim();
            int curNum = curClean.isEmpty() ? currentCode : Integer.parseInt(curClean);
            return tagNum > curNum;
        } catch (Exception e) {
            return !tag.equalsIgnoreCase("v" + currentVersion) && !tag.equalsIgnoreCase(currentVersion);
        }
    }

    private static void showToast(Activity a, String msg) {
        a.runOnUiThread(() -> Toast.makeText(a, msg, Toast.LENGTH_SHORT).show());
    }

    private static void showUpdateDialog(Activity a, String title, String notes, String apkUrl, String apkName) {
        new AlertDialog.Builder(a)
            .setTitle("Update Available: " + title)
            .setMessage(notes.isEmpty() ? "A new version of Galar Native is available." : notes)
            .setPositiveButton("Download & Install", (dialog, which) -> downloadAndInstall(a, apkUrl, apkName))
            .setNegativeButton("Later", null)
            .show();
    }

    public static void downloadAndInstall(Activity a, String apkUrl, String apkName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!a.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(a, "Please allow Galar Native to install updates", Toast.LENGTH_LONG).show();
                try {
                    a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {}
            }
        }

        Toast.makeText(a, "Downloading " + apkName + "...", Toast.LENGTH_SHORT).show();

        try {
            DownloadManager dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("Galar Native Update");
            request.setDescription("Downloading latest release APK");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName);

            final long downloadId = dm.enqueue(request);

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception ignored) {}

                        Uri uri = dm.getUriForDownloadedFile(downloadId);
                        if (uri != null) {
                            Intent install = new Intent(Intent.ACTION_VIEW);
                            install.setDataAndType(uri, "application/vnd.android.package-archive");
                            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(install);
                        }
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= 33) {
                a.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 2); // Context.RECEIVER_EXPORTED = 2
            } else {
                a.registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } catch (Exception e) {
            Toast.makeText(a, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
