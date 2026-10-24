/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.inbox;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.View;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.SignedInUser;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import java.util.*;

@SuppressWarnings("deprecation")
public final class StreakRecipientsPreference extends Preference {
    private boolean loading;
    public StreakRecipientsPreference(Context context) {
        super(context);
        setKey(Settings.AUTO_STREAK_RECIPIENT.key);
        setPersistent(false);
        setTitle(L10n.t(context, "Who to message"));
        refresh();
    }
    public void refresh() {
        setSummary(L10n.t(getContext(), "Choose people from your TikTok contacts.") + "\n"
                + L10n.f(getContext(), "Selected: %1$d", StreakMessenger.handlesOf(Settings.AUTO_STREAK_RECIPIENT.savedValue()).size()));
    }
    @Override protected void onBindView(View view) {
        refresh();
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }
    @Override protected void onClick() {
        if (loading) return;
        String account = SignedInUser.id();
        if (account == null) { Utils.showToastLong(L10n.t(getContext(), "Sign in to TikTok first")); return; }
        loading = true;
        if (!Utils.runOnBackgroundThread(() -> {
            List<String[]> contacts = StreakMessenger.selectableContacts(getContext(), account);
            Utils.runOnMainThread(() -> { loading = false; if (account.equals(SignedInUser.id())) show(account, contacts); });
        })) loading = false;
    }
    private void show(String account, List<String[]> contacts) {
        List<String> selected = StreakMessenger.handlesOf(Settings.AUTO_STREAK_RECIPIENT.savedValue());
        Set<String> known = new HashSet<>();
        for (String[] c : contacts) known.add(c[0]);
        for (String handle : selected) if (known.add(handle)) contacts.add(new String[]{handle, "@" + handle});
        if (contacts.isEmpty()) {
            Utils.showToastLong(L10n.t(getContext(), "Open Inbox and your Friends list in TikTok, then try again"));
            return;
        }
        String[] labels = new String[contacts.size()];
        boolean[] checked = new boolean[contacts.size()];
        for (int i = 0; i < contacts.size(); i++) {
            String[] c = contacts.get(i);
            labels[i] = c[1].equals("@" + c[0]) ? c[1] : c[1] + "\n@" + c[0];
            checked[i] = selected.contains(c[0]);
        }
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle(L10n.t(getContext(), "Who to message"))
                .setMultiChoiceItems(labels, checked, (d, i, value) -> checked[i] = value)
                .setNegativeButton(L10n.t(getContext(), "Cancel"), null)
                .setPositiveButton(L10n.t(getContext(), "Save"), (d, which) -> {
                    if (!account.equals(SignedInUser.id())) {
                        Utils.showToastLong(L10n.t(getContext(), "Account changed. Open the picker again.")); return;
                    }
                    List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < contacts.size(); i++)
                        if (((AlertDialog) d).getListView().isItemChecked(i)) chosen.add(contacts.get(i)[0]);
                    String value = TextUtils.join(",", chosen);
                    if (callChangeListener(value)) { Settings.AUTO_STREAK_RECIPIENT.save(value); refresh(); }
                }).create();
        dialog.show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }
}
