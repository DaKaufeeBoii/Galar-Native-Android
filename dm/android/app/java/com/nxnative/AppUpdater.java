package com.nxnative;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

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
                if (appInfo.length >= 2 && appInfo[1] != null) {
                    String configured = appInfo[1].replace("https://", "").replace("http://", "").replace("github.com/", "").replaceAll("/+$", "").trim();
                    if (configured.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) repo = configured;
                }
                JSONObject release = fetchLatestRelease(repo);
                if (release == null && !repo.equalsIgnoreCase(UPSTREAM_REPO)) release = fetchLatestRelease(UPSTREAM_REPO);

                if (release == null) {
                    if (!quiet) showToast(a, "Could not check for updates. Check internet connection.");
                    return;
                }

                String tagName = release.optString("tag", "");
                String releaseVersion = tagName;
                String releaseName = "Galar Native " + tagName;
                String releaseBody = "A newer release is available. Open the official release page to read its notes and download it.";
                String apkUrl = "https://github.com/" + repo + "/releases";

                // Check version
                String currentVersion = "";
                try {
                    PackageInfo pInfo = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                    currentVersion = pInfo.versionName;
                } catch (Exception ignored) {}

                if (isNewer(releaseVersion, currentVersion)) {
                    final String finalApkUrl = apkUrl;
                    a.runOnUiThread(() -> showUpdateDialog(a, releaseName, releaseBody, finalApkUrl));
                } else {
                    if (!quiet) showToast(a, "You are using the latest version (" + currentVersion + ").");
                }
            } catch (Throwable t) {
                if (!quiet) showToast(a, "Update check failed: " + t.getMessage());
            }
        }).start();
    }

    private static JSONObject fetchLatestRelease(String repo) {
        try {
            URL url = new URL("https://github.com/" + repo + "/releases.atom");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "GalarNative-Android");
            conn.setRequestProperty("Accept", "application/atom+xml");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;

            try (java.io.InputStream in = conn.getInputStream();
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, "UTF-8"))) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<id>[^<]*/tag/([^<]+)</id>");
                String line;
                while ((line = reader.readLine()) != null) {
                    java.util.regex.Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String tag = matcher.group(1).trim();
                        if (!tag.isEmpty()) {
                            JSONObject result = new JSONObject();
                            result.put("tag", tag);
                            return result;
                        }
                    }
                }
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static int compareVersions(String a, String b) {
        try {
            String cleanA = normalizeVersion(a), cleanB = normalizeVersion(b);
            if (cleanA.isEmpty() || cleanB.isEmpty()) return 0;
            String[] tParts = cleanA.split("\\.");
            String[] cParts = cleanB.split("\\.");
            int max = Math.max(tParts.length, cParts.length);
            for (int i = 0; i < max; i++) {
                int t = i < tParts.length && !tParts[i].isEmpty() ? Integer.parseInt(tParts[i]) : 0;
                int c = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
                if (t != c) return Integer.compare(t, c);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String normalizeVersion(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("v")) s = s.substring(1);
        // Releases built by build_apk.py use 0.<versionCode>; older repacks use 1.0.<versionCode>.
        if (s.matches("1\\.0\\.\\d+")) s = s.substring(4);
        return s.matches("\\d+(\\.\\d+)*") ? s : "";
    }

    private static boolean isNewer(String version, String currentVersion) {
        return compareVersions(version, currentVersion) > 0;
    }

    private static void showToast(Activity a, String msg) {
        a.runOnUiThread(() -> Toast.makeText(a, msg, Toast.LENGTH_SHORT).show());
    }

    private static void showUpdateDialog(Activity a, String title, String notes, String apkUrl) {
        new AlertDialog.Builder(a)
            .setTitle("Update Available: " + title)
            .setMessage(notes.isEmpty() ? "A new version of Galar Native is available." : notes)
            .setPositiveButton("Open release page", (dialog, which) -> openReleasePage(a, apkUrl))
            .setNegativeButton("Later", null)
            .show();
    }

    private static void openReleasePage(Activity a, String url) {
        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(a, "Could not open the official release page: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
