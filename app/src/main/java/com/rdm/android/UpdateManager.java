package com.rdm.android;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks the public GitHub Releases feed and hands verified APK updates to Android's installer. */
final class UpdateManager {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/zigorminsk-debug/RDM/releases/latest";
    private static final String RELEASE_ASSET_PREFIX =
            "https://github.com/zigorminsk-debug/RDM/releases/download/";
    private static final String USER_AGENT = "RDM-Android-Updater";
    private static final String UPDATE_MANIFEST = "rdm-update.json";
    private static final String PREFERENCES = "rdm_updates";
    private static final String PENDING_APK = "pending_apk";
    private static final long MAX_APK_BYTES = 512L * 1024L * 1024L;
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RDM-updater");
        thread.setDaemon(true);
        return thread;
    });

    private UpdateManager() { }

    static void checkForUpdate(Activity activity, boolean showIfCurrent) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!BUSY.compareAndSet(false, true)) {
            if (showIfCurrent) {
                Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (showIfCurrent) {
            Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        }

        Context appContext = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            UpdateInfo update = null;
            long installedVersion = 0;
            Exception failure = null;
            try {
                update = fetchLatest(appContext);
                installedVersion = installedVersionCode(appContext);
            } catch (Exception e) {
                failure = e;
            }

            UpdateInfo result = update;
            long current = installedVersion;
            Exception error = failure;
            activity.runOnUiThread(() -> {
                BUSY.set(false);
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (error != null) {
                    if (showIfCurrent) {
                        Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                if (result != null && result.versionCode > current) {
                    showUpdateDialog(activity, result);
                } else if (showIfCurrent) {
                    Toast.makeText(activity, R.string.update_up_to_date, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    static void installPendingIfAllowed(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String path = preferences.getString(PENDING_APK, null);
        if (path == null || path.trim().isEmpty()) return;

        File apk = new File(path);
        if (!apk.isFile()) {
            preferences.edit().remove(PENDING_APK).apply();
            return;
        }
        if (!canInstallPackages(activity)) return;

        preferences.edit().remove(PENDING_APK).apply();
        launchInstaller(activity, apk);
    }

    private static UpdateInfo fetchLatest(Context context) throws Exception {
        JSONObject release = readJson(LATEST_RELEASE_API);
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IOException("The GitHub release has no assets.");

        String manifestUrl = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && UPDATE_MANIFEST.equals(asset.optString("name"))) {
                manifestUrl = asset.optString("browser_download_url", null);
                break;
            }
        }
        validateReleaseAssetUrl(manifestUrl);
        JSONObject manifest = readJson(manifestUrl);
        if (!context.getPackageName().equals(manifest.optString("packageName"))) {
            throw new IOException("The update is for a different Android package.");
        }

        long versionCode = manifest.getLong("versionCode");
        String versionName = manifest.getString("versionName");
        String apkName = manifest.getString("apkAssetName");
        String sha256 = manifest.getString("sha256").toLowerCase(Locale.ROOT);
        if (versionCode < 1 || versionCode > 2_100_000_000L
                || !apkName.matches("RDM-[0-9]+-[0-9]+\\.apk")
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("The GitHub update manifest is invalid.");
        }

        String apkUrl = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && apkName.equals(asset.optString("name"))) {
                apkUrl = asset.optString("browser_download_url", null);
                break;
            }
        }
        validateReleaseAssetUrl(apkUrl);
        return new UpdateInfo(versionCode, versionName, apkName, apkUrl, sha256);
    }

    private static JSONObject readJson(String address) throws IOException, org.json.JSONException {
        HttpURLConnection connection = openConnection(address);
        try {
            int responseCode = connection.getResponseCode();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("The update was redirected to a non-HTTPS URL.");
            }
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("GitHub returned HTTP " + responseCode + ".");
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                    if (body.length() > 1024 * 1024) {
                        throw new IOException("The GitHub update response is too large.");
                    }
                }
            }
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Only HTTPS update sources are accepted.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        return connection;
    }

    private static void validateReleaseAssetUrl(String address) throws IOException {
        if (address == null || !address.startsWith(RELEASE_ASSET_PREFIX)) {
            throw new IOException("The GitHub release asset URL is not trusted.");
        }
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo update) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(R.string.update_available_message,
                        update.versionName, update.versionCode))
                .setNegativeButton(R.string.later, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.update_download_and_install,
                        (dialog, which) -> downloadAndInstall(activity, update))
                .show();
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo update) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (!BUSY.compareAndSet(false, true)) return;

        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.update_download_progress));
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();

        Context appContext = activity.getApplicationContext();
        EXECUTOR.execute(() -> {
            File downloaded = null;
            Exception failure = null;
            try {
                downloaded = downloadApk(appContext, update);
                appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .edit().putString(PENDING_APK, downloaded.getAbsolutePath()).apply();
            } catch (Exception e) {
                failure = e;
            }

            File result = downloaded;
            Exception error = failure;
            activity.runOnUiThread(() -> {
                BUSY.set(false);
                if (progress.isShowing()) progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (error != null) {
                    Toast.makeText(activity, error instanceof IntegrityException
                            ? R.string.update_integrity_failed : R.string.update_download_failed,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (result != null && !canInstallPackages(activity)) {
                    showInstallPermissionDialog(activity);
                } else {
                    installPendingIfAllowed(activity);
                }
            });
        });
    }

    private static File downloadApk(Context context, UpdateInfo update) throws Exception {
        validateReleaseAssetUrl(update.apkUrl);
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create the APK cache directory.");
        }
        File temporary = new File(directory, update.apkAssetName + ".part");
        File apk = new File(directory, update.apkAssetName);
        HttpURLConnection connection = openConnection(update.apkUrl);
        MessageDigest digest = sha256Digest();
        long totalBytes = 0;
        try {
            int responseCode = connection.getResponseCode();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IOException("The update was redirected to a non-HTTPS URL.");
            }
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("GitHub returned HTTP " + responseCode + ".");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temporary, false)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    totalBytes += count;
                    if (totalBytes > MAX_APK_BYTES) {
                        throw new IOException("The APK exceeds the allowed download size.");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
        } catch (Exception e) {
            temporary.delete();
            throw e;
        } finally {
            connection.disconnect();
        }

        if (!toHex(digest.digest()).equalsIgnoreCase(update.sha256)) {
            temporary.delete();
            throw new IntegrityException("The downloaded APK checksum does not match.");
        }
        if (apk.exists() && !apk.delete()) {
            temporary.delete();
            throw new IOException("Could not replace the cached APK.");
        }
        if (!temporary.renameTo(apk)) {
            temporary.delete();
            throw new IOException("Could not finalize the downloaded APK.");
        }
        return apk;
    }

    private static void showInstallPermissionDialog(Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setNegativeButton(R.string.later, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.update_open_settings, (dialog, which) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    try {
                        activity.startActivity(settings);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, R.string.update_install_permission_title,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private static boolean canInstallPackages(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || context.getPackageManager().canRequestPackageInstalls();
    }

    private static void launchInstaller(Activity activity, File apk) {
        try {
            Uri apkUri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.update_installer_unavailable, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.update_installer_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private static long installedVersionCode(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
    }

    private static MessageDigest sha256Digest() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static final class UpdateInfo {
        final long versionCode;
        final String versionName;
        final String apkAssetName;
        final String apkUrl;
        final String sha256;

        UpdateInfo(long versionCode, String versionName, String apkAssetName,
                String apkUrl, String sha256) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkAssetName = apkAssetName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    private static final class IntegrityException extends IOException {
        IntegrityException(String message) {
            super(message);
        }
    }
}
