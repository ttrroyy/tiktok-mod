/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.L10n;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class ModUpdater {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private final WeakReference<Activity> owner;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private AlertDialog progress;
    private ProgressBar bar;
    private TextView label;

    private interface UiAction { void run(Activity activity); }

    private ModUpdater(Activity activity) { owner = new WeakReference<>(activity); }

    public static void check(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!BUSY.compareAndSet(false, true)) {
            Utils.showToastShort(L10n.t("An update is already being checked or downloaded"));
            return;
        }
        ModUpdater update = new ModUpdater(activity);
        update.showProgress(activity, L10n.t("Checking for an update"), true);
        if (!Utils.runOnBackgroundThread(update::checkRelease)) {
            BUSY.set(false);
            update.fail(new IOException(L10n.t("Try again in a few seconds")));
        }
    }

    private void ui(UiAction action) {
        Utils.runOnMainThread(() -> {
            Activity activity = owner.get();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) action.run(activity);
        });
    }

    private void checkRelease() {
        try {
            HttpURLConnection connection = open(UpdateSpec.MANIFEST_URL);
            byte[] metadata;
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    checkCancelled();
                    if (bytes.size() + n > 65536) throw new IOException(L10n.t("Update metadata is too large"));
                    bytes.write(buffer, 0, n);
                }
                metadata = bytes.toByteArray();
            } finally { connection.disconnect(); }
            UpdateSpec spec = new UpdateSpec(new JSONObject(new String(metadata, "UTF-8")), Build.SUPPORTED_ABIS);
            checkCancelled();
            BUSY.set(false);
            ui(activity -> {
                progress.dismiss();
                if (spec.build <= UpdateSpec.CURRENT_BUILD) {
                    showMessage(activity, L10n.t("Updates"), L10n.t("The latest mod version is installed"));
                } else {
                    AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setTitle(L10n.t("Version available: ") + spec.version)
                            .setMessage(spec.changes + L10n.t("\n\nSize: ") + spec.size / (1024 * 1024) + L10n.t(" MB"))
                            .setNegativeButton(L10n.t("Later"), null)
                            .setPositiveButton(L10n.t("Update"), (d, which) -> beginDownload(activity, spec)).create();
                    dialog.show();
                    SettingsUi.styleStandardAlertDialog(dialog);
                }
            });
        } catch (Exception error) { BUSY.set(false); fail(error); }
    }

    private void beginDownload(Activity activity, UpdateSpec spec) {
        if (!BUSY.compareAndSet(false, true)) return;
        cancelled.set(false);
        showProgress(activity, L10n.t("Downloading update"), false);
        if (!Utils.runOnBackgroundThread(() -> download(spec))) {
            BUSY.set(false);
            fail(new IOException(L10n.t("Try again in a few seconds")));
        }
    }

    private void download(UpdateSpec spec) {
        File temporary = null;
        try {
            Activity activity = owner.get();
            if (activity == null) return;
            File target = ModUpdateProvider.apk(activity);
            File directory = target.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException(L10n.t("Couldn't create the update folder. Try again."));
            if (target.isFile() && target.length() == spec.size && spec.hash.equals(hash(target))) {
                verifyPackage(activity.getPackageManager(), activity.getPackageName(), target);
            } else {
                temporary = File.createTempFile("update-", ".part", directory);
                HttpURLConnection connection = open(spec.url);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                int previous = -1;
                try (InputStream stream = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[65536];
                    int n;
                    while ((n = stream.read(buffer)) != -1) {
                        checkCancelled();
                        total += n;
                        if (total > spec.size) throw new IOException(L10n.t("The APK size does not match"));
                        output.write(buffer, 0, n);
                        digest.update(buffer, 0, n);
                        int percent = (int) (total * 100 / spec.size);
                        if (percent != previous) {
                            previous = percent;
                            ui(a -> { bar.setProgress(percent); label.setText(percent + "%"); });
                        }
                    }
                    output.getFD().sync();
                } finally { connection.disconnect(); }
                if (total != spec.size || !spec.hash.equals(hex(digest.digest())))
                    throw new IOException(L10n.t("The APK checksum does not match"));
                verifyPackage(activity.getPackageManager(), activity.getPackageName(), temporary);
                checkCancelled();
                android.system.Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
            }
            checkCancelled();
            ui(a -> { progress.dismiss(); install(a); });
        } catch (Exception error) { fail(error); }
        finally {
            if (temporary != null && temporary.exists()) temporary.delete();
            BUSY.set(false);
        }
    }

    private void showProgress(Activity activity, String title, boolean indeterminate) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = SettingsUi.dp(activity, 24);
        content.setPadding(padding, padding, padding, padding);
        label = SettingsUi.text(activity, indeterminate ? L10n.t("Please wait…") : L10n.t("0%"),
                SettingsUi.TEXT_BODY, SettingsUi.textPrimary(), 0);
        content.addView(label);
        bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(indeterminate);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(SettingsUi.accent()));
        bar.setIndeterminateTintList(ColorStateList.valueOf(SettingsUi.accent()));
        content.addView(bar, new LinearLayout.LayoutParams(-1, SettingsUi.dp(activity, 24)));
        progress = new AlertDialog.Builder(activity).setTitle(title).setView(content)
                .setNegativeButton(L10n.t("Cancel"), (d, which) -> cancelled.set(true)).create();
        progress.setOnCancelListener(d -> cancelled.set(true));
        progress.setCanceledOnTouchOutside(false);
        progress.show();
        SettingsUi.styleStandardAlertDialog(progress);
    }

    private void fail(Exception error) {
        if (cancelled.get()) return;
        ui(activity -> {
            if (progress != null) progress.dismiss();
            showMessage(activity, L10n.t("Update failed"), error instanceof IOException
                    ? error.getMessage() : L10n.t("Couldn't check for an update. Try again later."));
        });
    }

    private static void showMessage(Activity activity, String title, String text) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(text)
                .setPositiveButton(L10n.t("OK"), null).create();
        dialog.show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }

    private void checkCancelled() throws InterruptedIOException {
        if (cancelled.get()) throw new InterruptedIOException(L10n.t("Download cancelled"));
    }

    private static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 6; redirects++) {
            UpdateSpec.requireGitHub(url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "TikTok-Mod-Updater");
            int status;
            try { status = connection.getResponseCode(); }
            catch (IOException error) { connection.disconnect(); throw error; }
            if (status == 200) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308) && location != null) {
                url = new URL(url, location);
            } else throw new IOException(status == 404 ? L10n.t("No public update release is available yet") : L10n.t("GitHub is unavailable (") + status + ")");
        }
        throw new IOException(L10n.t("Too many GitHub redirects"));
    }

    static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = stream.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] value) {
        StringBuilder text = new StringBuilder(value.length * 2);
        for (byte item : value) text.append(Character.forDigit((item >>> 4) & 15, 16)).append(Character.forDigit(item & 15, 16));
        return text.toString();
    }

    @SuppressWarnings("deprecation")
    static void verifyPackage(PackageManager manager, String packageName, File file) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo current = manager.getPackageInfo(packageName, flags);
        PackageInfo next = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (next == null || !packageName.equals(next.packageName)
                || (Build.VERSION.SDK_INT >= 28 ? next.getLongVersionCode() < current.getLongVersionCode() : next.versionCode < current.versionCode)
                || !signatures(current).equals(signatures(next))) throw new IOException(L10n.t("APK rejected: package, version or signature does not match"));
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signatures(PackageInfo info) throws Exception {
        Signature[] values = Build.VERSION.SDK_INT >= 28
                ? info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners() : info.signatures;
        if (values == null || values.length == 0) throw new IOException(L10n.t("Couldn't verify the APK signature. Download the update again."));
        Set<String> result = new HashSet<>();
        for (Signature value : values) result.add(hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray())));
        return result;
    }

    private void install(Activity activity) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                AlertDialog permission = new AlertDialog.Builder(activity).setTitle(L10n.t("Android permission"))
                        .setMessage(L10n.t("Allow this app to install updates, then check for updates again. The downloaded APK has been kept."))
                        .setNegativeButton(L10n.t("Later"), null)
                        .setPositiveButton(L10n.t("Open settings"), (d, which) -> activity.startActivity(new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())))).create();
                permission.show();
                SettingsUi.styleStandardAlertDialog(permission);
                return;
            }
            Uri uri = ModUpdateProvider.uri(activity);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("TikTok update", uri));
            activity.startActivity(intent);
        } catch (Exception error) { showMessage(activity, L10n.t("Installation"), L10n.t("Couldn't open the Android installer. Try again from Settings.")); }
    }
}
