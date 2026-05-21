package com.yadiate.yoonkeyboard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public final class GitHubReleaseUpdater {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/yadiate/YoonKeyboard/releases/latest";
    private static final String USER_AGENT = "YoonKeyboard-Updater";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String APK_FILE_NAME = "YoonKeyboard-latest.apk";

    private GitHubReleaseUpdater() {
    }

    public interface Callback {
        void onStatus(String message);

        void onDownloaded(File apkFile, String releaseLabel);

        void onError(String message);
    }

    public interface CheckCallback {
        void onStatus(String message);

        void onChecked(UpdateCandidate candidate, boolean updateAvailable);

        void onError(String message);
    }

    public static final class UpdateCandidate {
        public final String releaseLabel;
        public final String tagName;
        public final String versionName;
        public final String downloadUrl;

        private UpdateCandidate(String releaseLabel, String tagName, String versionName, String downloadUrl) {
            this.releaseLabel = releaseLabel;
            this.tagName = tagName;
            this.versionName = versionName;
            this.downloadUrl = downloadUrl;
        }
    }

    public static void checkLatestRelease(Activity activity, String currentVersionName, CheckCallback callback) {
        Thread worker = new Thread(() -> {
            try {
                postStatus(activity, callback, "GitHub 릴리즈를 확인합니다.");
                JSONObject release = fetchLatestRelease();
                JSONObject asset = findInstallableApkAsset(release);
                if (asset == null) {
                    postError(activity, callback, "GitHub 최신 릴리즈에 설치용 APK가 없습니다.");
                    return;
                }

                String tagName = release.optString("tag_name", "");
                String versionName = versionNameFromTag(tagName);
                UpdateCandidate candidate = new UpdateCandidate(
                        releaseLabel(release),
                        tagName,
                        versionName,
                        asset.getString("browser_download_url"));
                postChecked(activity, callback, candidate,
                        compareVersions(candidate.versionName, currentVersionName) > 0);
            } catch (JSONException ex) {
                postError(activity, callback, "GitHub 릴리즈 정보를 읽을 수 없습니다.");
            } catch (IOException ex) {
                postError(activity, callback, "GitHub 릴리즈를 확인할 수 없습니다.");
            } catch (RuntimeException ex) {
                postError(activity, callback, "업데이트 확인을 시작할 수 없습니다.");
            }
        }, "YoonKeyboardReleaseChecker");
        worker.start();
    }

    public static void downloadLatestReleaseApk(Activity activity, Callback callback) {
        Thread worker = new Thread(() -> {
            try {
                postStatus(activity, callback, "GitHub 릴리즈를 확인합니다.");
                JSONObject release = fetchLatestRelease();
                JSONObject asset = findInstallableApkAsset(release);
                if (asset == null) {
                    postError(activity, callback, "GitHub 최신 릴리즈에 설치용 APK가 없습니다.");
                    return;
                }

                String releaseLabel = releaseLabel(release);
                String downloadUrl = asset.getString("browser_download_url");
                postStatus(activity, callback, releaseLabel + " APK를 다운로드합니다.");
                File apkFile = downloadApk(activity, downloadUrl);
                postDownloaded(activity, callback, apkFile, releaseLabel);
            } catch (JSONException ex) {
                postError(activity, callback, "GitHub 릴리즈 정보를 읽을 수 없습니다.");
            } catch (IOException ex) {
                postError(activity, callback, "업데이트 APK를 다운로드할 수 없습니다.");
            } catch (RuntimeException ex) {
                postError(activity, callback, "업데이트를 시작할 수 없습니다.");
            }
        }, "YoonKeyboardReleaseUpdater");
        worker.start();
    }

    public static void downloadReleaseApk(Activity activity, UpdateCandidate candidate, Callback callback) {
        Thread worker = new Thread(() -> {
            try {
                postStatus(activity, callback, candidate.releaseLabel + " APK를 다운로드합니다.");
                File apkFile = downloadApk(activity, candidate.downloadUrl);
                postDownloaded(activity, callback, apkFile, candidate.releaseLabel);
            } catch (IOException ex) {
                postError(activity, callback, "업데이트 APK를 다운로드할 수 없습니다.");
            } catch (RuntimeException ex) {
                postError(activity, callback, "업데이트를 시작할 수 없습니다.");
            }
        }, "YoonKeyboardReleaseDownloader");
        worker.start();
    }

    public static boolean canRequestPackageInstalls(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    public static boolean startInstallOrOpenPermissionSettings(Activity activity, File apkFile) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            throw new IllegalStateException("Downloaded APK is missing");
        }
        if (!canRequestPackageInstalls(activity)) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settingsIntent);
            return false;
        }

        Uri uri = UpdateApkProvider.uriFor(activity, apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, APK_MIME);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

        List<ResolveInfo> installers = activity.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo installer : installers) {
            if (installer.activityInfo != null) {
                activity.grantUriPermission(installer.activityInfo.packageName, uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }

        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ex) {
            throw new IllegalStateException("No package installer found", ex);
        }
    }

    private static JSONObject fetchLatestRelease() throws IOException, JSONException {
        HttpURLConnection connection = openConnection(LATEST_RELEASE_API);
        int status = connection.getResponseCode();
        String body = readString(connection, status);
        connection.disconnect();
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw new IOException("No GitHub release found");
        }
        if (status < 200 || status >= 300) {
            throw new IOException("GitHub release request failed: " + status);
        }
        return new JSONObject(body);
    }

    private static JSONObject findInstallableApkAsset(JSONObject release) throws JSONException {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }

        JSONObject fallback = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            String lowerName = name.toLowerCase(Locale.US);
            if (!lowerName.endsWith(".apk") || lowerName.contains("legacy")) {
                continue;
            }
            if (lowerName.contains("com.yadiate.yoonkeyboard") || lowerName.contains("current")) {
                return asset;
            }
            if (fallback == null) {
                fallback = asset;
            }
        }
        return fallback;
    }

    private static String releaseLabel(JSONObject release) {
        String tag = release.optString("tag_name", "");
        String name = release.optString("name", "");
        if (!name.trim().isEmpty()) {
            return name;
        }
        return tag.trim().isEmpty() ? "최신 릴리즈" : tag;
    }

    private static String versionNameFromTag(String tagName) {
        String value = tagName == null ? "" : tagName.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        return value;
    }

    private static int compareVersions(String leftVersion, String rightVersion) {
        int[] left = versionNumbers(leftVersion);
        int[] right = versionNumbers(rightVersion);
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            int leftValue = i < left.length ? left[i] : 0;
            int rightValue = i < right.length ? right[i] : 0;
            if (leftValue != rightValue) {
                return leftValue < rightValue ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] versionNumbers(String versionName) {
        String clean = versionName == null ? "" : versionName.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        String[] parts = clean.split("[^0-9]+");
        int count = 0;
        for (String part : parts) {
            if (!part.isEmpty()) {
                count++;
            }
        }
        int[] numbers = new int[count];
        int index = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                numbers[index++] = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                numbers[index - 1] = 0;
            }
        }
        return numbers;
    }

    private static File downloadApk(Context context, String downloadUrl) throws IOException {
        File updateDir = new File(context.getCacheDir(), "updates");
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            throw new IOException("Cannot create update cache");
        }
        File output = new File(updateDir, APK_FILE_NAME);
        File temp = new File(updateDir, APK_FILE_NAME + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Cannot clean previous update temp file");
        }

        HttpURLConnection connection = openConnection(downloadUrl);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            readString(connection, status);
            connection.disconnect();
            throw new IOException("APK download failed: " + status);
        }

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }

        if (temp.length() <= 0L) {
            throw new IOException("Downloaded APK is empty");
        }
        if (output.exists() && !output.delete()) {
            throw new IOException("Cannot replace previous update APK");
        }
        if (!temp.renameTo(output)) {
            throw new IOException("Cannot finalize update APK");
        }
        return output;
    }

    private static HttpURLConnection openConnection(String urlText) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private static String readString(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }

    private static void postStatus(Activity activity, Callback callback, String message) {
        activity.runOnUiThread(() -> callback.onStatus(message));
    }

    private static void postStatus(Activity activity, CheckCallback callback, String message) {
        activity.runOnUiThread(() -> callback.onStatus(message));
    }

    private static void postChecked(
            Activity activity,
            CheckCallback callback,
            UpdateCandidate candidate,
            boolean updateAvailable
    ) {
        activity.runOnUiThread(() -> callback.onChecked(candidate, updateAvailable));
    }

    private static void postDownloaded(Activity activity, Callback callback, File apkFile, String releaseLabel) {
        activity.runOnUiThread(() -> callback.onDownloaded(apkFile, releaseLabel));
    }

    private static void postError(Activity activity, Callback callback, String message) {
        activity.runOnUiThread(() -> callback.onError(message));
    }

    private static void postError(Activity activity, CheckCallback callback, String message) {
        activity.runOnUiThread(() -> callback.onError(message));
    }
}
