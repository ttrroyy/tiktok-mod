/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.update;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Shares only the verified update APK with Android's installer. */
public final class ModUpdateProvider extends ContentProvider {
    static File apk(Context context) {
        return new File(context.getCacheDir(), "mod-updates/update.apk");
    }

    public static Uri uri(Context context) {
        return Uri.parse("content://" + context.getPackageName() + ".modupdates/update.apk");
    }

    private boolean accepts(Uri uri) {
        return uri(getContext()).equals(uri);
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return accepts(uri) ? "application/vnd.android.package-archive" : null;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!accepts(uri) || !"r".equals(mode)) throw new FileNotFoundException("Read-only update APK");
        return ParcelFileDescriptor.open(apk(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!accepts(uri)) throw new IllegalArgumentException("Unknown update URI");
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "TikTok-update.apk";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = apk(getContext()).length();
        }
        result.addRow(row);
        return result;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
