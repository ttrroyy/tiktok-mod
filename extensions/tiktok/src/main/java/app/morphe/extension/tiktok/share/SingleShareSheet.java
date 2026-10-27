/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.share;

import android.app.Activity;
import android.app.Dialog;
import android.os.SystemClock;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Coalesces the host's share-sheet requests while its fragment transaction is pending or visible. */
public final class SingleShareSheet {
    static final long PENDING_MS = 2000;
    private static final WeakHashMap<Activity, Opening> openings = new WeakHashMap<>();

    private static final class Opening {
        final long at = SystemClock.uptimeMillis();
        final WeakReference<Object> sheet;
        Opening(Object sheet) { this.sheet = new WeakReference<>(sheet); }
    }

    private SingleShareSheet() {}

    public static synchronized boolean begin(Activity activity) {
        if (activity == null) return false;
        if (activity.isFinishing() || activity.isDestroyed()) {
            openings.remove(activity);
            return false;
        }
        Opening opening = openings.get(activity);
        if (opening != null) {
            Object sheet = opening.sheet.get();
            boolean dismissed = sheet != null && Boolean.TRUE.equals(Reflect.readField(sheet, "mDismissed"));
            if (!dismissed) {
                Object dialog = Reflect.invoke(sheet, "getDialog");
                if (dialog instanceof Dialog && ((Dialog) dialog).isShowing()) return false;
                if (Boolean.TRUE.equals(Reflect.invoke(sheet, "isAdded"))) return false;
                if (SystemClock.uptimeMillis() - opening.at < PENDING_MS) return false;
            }
        }
        openings.put(activity, new Opening(null));
        return true;
    }

    /** A null native result releases the reservation immediately, so a failed open can be retried. */
    public static synchronized void completed(Activity activity, Object sheet) {
        if (activity == null) return;
        if (sheet == null || activity.isFinishing() || activity.isDestroyed()) openings.remove(activity);
        else openings.put(activity, new Opening(sheet));
    }

    /** Also accounts for a sheet restored by Android after Activity recreation. */
    public static void started(Object sheet) {
        // shortcut: TikTok 47.5.3's sheet content field; verify again when changing the base APK.
        Object content = Reflect.readField(sheet, "LLJLLIL");
        if (content == null || !"com.ss.android.ugc.aweme.share.core.ui.SkeletonSharePanelFragment"
                .equals(content.getClass().getName())) return;
        Object activity = Reflect.invoke(sheet, "getActivity");
        if (activity instanceof Activity) completed((Activity) activity, sheet);
    }

    static synchronized void resetForTests() { openings.clear(); }
}
