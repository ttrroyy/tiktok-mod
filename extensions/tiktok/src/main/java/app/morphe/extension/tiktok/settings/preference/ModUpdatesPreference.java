/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.app.Activity;
import android.preference.Preference;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.ImmediateAction;
import app.morphe.extension.tiktok.update.ModUpdater;

@SuppressWarnings("deprecation")
public final class ModUpdatesPreference extends Preference implements ImmediateAction {
    public static final String KEY = "action_mod_updates";
    public static final String TITLE = "Проверить обновления";
    public static final String SUMMARY = "Проверить новую версию и скачать обновление";

    public ModUpdatesPreference(Context context) {
        super(context);
        setKey(KEY);
        setTitle(TITLE);
        setSummary(SUMMARY);
        setOnPreferenceClickListener(preference -> {
            Activity activity = context instanceof Activity ? (Activity) context : Utils.getVisibleActivity();
            ModUpdater.check(activity);
            return true;
        });
    }

    @Override
    public boolean actsOnTap() {
        return true;
    }
}
