/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.download;

import android.view.*;
import android.widget.*;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** One native checkbox in the host photo picker; no persisted or cross-post format preference. */
public final class PhotoVideoPicker {
    private static final String TAG = "mod_save_photos_as_video";
    private static final Map<Object, WeakReference<CompoundButton>> CHOICES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Filled from the host fragment's actual inflation instruction by the patch. */
    public static int nativeLayout() { return 0; }

    public static void install(Object fragment) {
        if (!SlideshowVideo.enabled()) return;
        try {
            View root = (View) Reflect.invoke(fragment, "getView");
            Object post = post(fragment);
            if (root == null || post == null || root.findViewWithTag(TAG) != null) return;
            CompoundButton selectAll = null;
            for (Method method : fragment.getClass().getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0 && CompoundButton.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true); selectAll = (CompoundButton) method.invoke(fragment); break;
                }
            }
            if (selectAll == null || !(selectAll.getParent() instanceof ViewGroup)) return;
            ViewGroup footer = (ViewGroup) selectAll.getParent();
            View label = footer.getChildAt(footer.indexOfChild(selectAll) + 1);
            if (!(label instanceof TextView)) return;
            View copyRoot = LayoutInflater.from(root.getContext()).inflate(nativeLayout(), null, false);
            CompoundButton check = copyRoot.findViewById(selectAll.getId());
            TextView text = copyRoot.findViewById(label.getId());
            ViewGroup row = (ViewGroup) check.getParent();
            ((ViewGroup) row.getParent()).removeView(row);
            for (int i = row.getChildCount() - 1; i >= 0; i--)
                if (row.getChildAt(i) != check && row.getChildAt(i) != text) row.removeViewAt(i);
            attach(post, selectAll, row, check, text);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Logger.printException(() -> "Could not attach the photo video checkbox", failure);
        }
    }

    static void attach(Object post, CompoundButton selectAll, ViewGroup row, CompoundButton check, TextView text) {
        ViewGroup footer = (ViewGroup) selectAll.getParent();
        ViewGroup parent = (ViewGroup) footer.getParent();
        if (parent.findViewWithTag(TAG) != null) return;
        int newId = View.generateViewId();
        check.setId(newId); text.setId(View.generateViewId()); row.setId(View.generateViewId());
        if (text.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) text.getLayoutParams();
            params.removeRule(RelativeLayout.LEFT_OF); params.removeRule(RelativeLayout.RIGHT_OF);
            params.removeRule(RelativeLayout.START_OF); params.addRule(RelativeLayout.END_OF, newId);
        }
        text.setText(L10n.t("Save as video"));
        check.setContentDescription(text.getText()); check.setChecked(false);
        text.setOnClickListener(v -> check.toggle()); row.setOnClickListener(v -> check.toggle());
        row.setTag(TAG); row.setMinimumHeight(Math.round(48 * row.getResources().getDisplayMetrics().density));
        parent.addView(row, parent.indexOfChild(footer));
        CHOICES.put(post, new WeakReference<>(check));
    }

    static boolean saveIfSelected(Object post, Set<?> indices) {
        WeakReference<CompoundButton> reference = CHOICES.get(post);
        CompoundButton check = reference == null ? null : reference.get();
        if (!SlideshowVideo.enabled() || check == null || !check.isChecked()) return false;
        if (indices != null && indices.isEmpty()) return false;
        return SlideshowVideo.start(post, indices == null ? null : new HashSet<>(indices));
    }

    public static void clear(Object fragment) {
        try { clearPost(post(fragment)); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    static void clearPost(Object post) { CHOICES.remove(post); }

    private static Object post(Object fragment) throws ReflectiveOperationException {
        for (Field field : fragment.getClass().getDeclaredFields()) {
            if (!field.getType().getName().equals("com.ss.android.ugc.aweme.feed.model.Aweme")) continue;
            field.setAccessible(true); return field.get(fragment);
        }
        return null;
    }
}
