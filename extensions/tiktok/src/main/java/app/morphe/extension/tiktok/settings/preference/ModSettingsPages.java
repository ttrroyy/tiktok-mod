/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.categories.*;
import app.morphe.extension.tiktok.spoof.sim.SimPreset;
import app.morphe.extension.tiktok.spoof.sim.SimPresets;
import java.util.*;

/** A curated menu reusing the existing controls and their persistence handlers. */
@SuppressWarnings("deprecation")
public final class ModSettingsPages {
    private final Context context;
    private final PreferenceScreen target;
    private int order;
    private ModSettingsPages(Context context, PreferenceScreen target) { this.context = context; this.target = target; }

    public static void addTo(Context context, PreferenceScreen target, String page) {
        ModSettingsPages m = new ModSettingsPages(context, target);
        switch (page) {
            case "REGION": m.region(); break;
            case "DOWNLOADS": m.downloads(); break;
            case "FEED_FILTER": m.feed(); break;
            case "BEHAVIOR": m.fonts(); break;
            case "DIAGNOSTICS": m.diagnostics(); break;
            default: m.extras(); break;
        }
    }
    private PreferenceScreen scratch() { return target.getPreferenceManager().createPreferenceScreen(context); }
    private void add(Preference row) {
        row.setOrder(order++);
        if (!(row instanceof LicensesPreference) && row.getSummary() != null)
            row.setSummary(row.getSummary().toString().replace("Hushfeed", "мод").replace("HushFeed", "мод"));
        target.addPreference(row);
    }
    private void heading(String title) { add(new SectionHeadingPreference(context, title)); }
    private void take(PreferenceCategory source, String... titles) {
        for (String title : titles) for (int i = 0; i < source.getPreferenceCount(); i++) {
            Preference row = source.getPreference(i);
            boolean ghost = title.equals("Ghost mode") && Settings.GHOST_MODE.key.equals(row.getKey());
            if (!ghost && !L10n.t(context, title).contentEquals(row.getTitle())) continue;
            source.removePreference(row);
            switch (title) {
                case "Save the sound as well": row.setSummary(L10n.t(context, "Also save the video's audio as a separate file.")); break;
                case "Download original photos": row.setSummary(L10n.t(context, "Save each photo at its original resolution.")); break;
                case "Save a profile picture on a long press": row.setSummary(L10n.t(context, "Hold a profile picture to save it in the photo folder.")); break;
                case "Save a story from its share sheet": row.setSummary(L10n.t(context, "Add a download button to a story's Share menu.")); break;
            }
            add(row);
            break;
        }
    }
    private void region() {
        TogglePreference enabled = new TogglePreference(context, "SIM region",
                "TikTok uses the selected SIM country. Restart after changing the country.", Settings.SIM_SPOOF);
        enabled.setOnPreferenceChangeListener((preference, value) -> {
            Settings.REGION_SPOOF.save(Boolean.TRUE.equals(value));
            Settings.REGION_REQUEST_SPOOF.save(Boolean.TRUE.equals(value));
            Settings.SIM_SPOOF_FEED_REAL.save(false);
            return true;
        });
        add(enabled);
        Preference country = new Preference(context) {
            @Override protected void onBindView(android.view.View view) {
                refreshCountry(this);
                super.onBindView(view);
            }
        };
        country.setKey("mod_sim_country");
        country.setTitle(L10n.t(context, "SIM country"));
        refreshCountry(country);
        country.setOnPreferenceClickListener(row -> { chooseCountry(row); return true; });
        add(country);
        add(new TogglePreference(context, "Force SIM country feed",
                "Only videos TikTok marks with the selected country stay in For You. The feed can be empty if no videos match.", Settings.FORCE_SIM_REGION));
        PreferenceCategory source = new SimSpoofPreferenceCategory(context, scratch());
        List<Preference> advancedRows = new ArrayList<>();
        for (int i = 0; i < source.getPreferenceCount(); i++) {
            Preference p = source.getPreference(i);
            if (p instanceof SimPresetPreference || Settings.SIM_SPOOF_ISO.key.equals(p.getKey())
                    || Settings.SIMSPOOF_MCCMNC.key.equals(p.getKey()) || Settings.SIMSPOOF_OP_NAME.key.equals(p.getKey())
                    || Settings.REGION_SPOOF.key.equals(p.getKey()) || Settings.REGION_REQUEST_SPOOF.key.equals(p.getKey())
                    || Settings.REGION_STORE_SPOOF.key.equals(p.getKey())) advancedRows.add(p);
        }
        for (Preference p : advancedRows) source.removePreference(p);
        TogglePreference advanced = new TogglePreference(context, "Advanced region settings",
                "Choose a carrier or edit country, operator and request region separately.", Settings.MOD_REGION_ADVANCED);
        add(advanced);
        if (Settings.MOD_REGION_ADVANCED.savedValue()) for (Preference p : advancedRows) add(p);
        advanced.setOnPreferenceChangeListener((p, value) -> {
            for (Preference row : advancedRows) {
                if (Boolean.TRUE.equals(value)) add(row); else target.removePreference(row);
            }
            return true;
        });
    }
    private void refreshCountry(Preference row) {
        row.setSummary(new Locale("", Settings.SIM_SPOOF_ISO.savedValue()).getDisplayCountry(Locale.getDefault()));
    }
    private void chooseCountry(Preference row) {
        LinkedHashMap<String, SimPreset> countries = new LinkedHashMap<>();
        for (SimPreset preset : SimPresets.PRESETS) countries.putIfAbsent(preset.iso, preset);
        List<SimPreset> presets = new ArrayList<>(countries.values());
        String[] labels = new String[presets.size()];
        int selected = -1;
        for (int i = 0; i < presets.size(); i++) {
            SimPreset preset = presets.get(i);
            labels[i] = new Locale("", preset.iso).getDisplayCountry(Locale.getDefault());
            if (preset.iso.equalsIgnoreCase(Settings.SIM_SPOOF_ISO.savedValue())) selected = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(L10n.t(context, "SIM country"))
                .setSingleChoiceItems(labels, selected, (choice, position) -> {
                    SimPreset p = presets.get(position);
                    Settings.SIM_SPOOF_ISO.save(p.iso);
                    Settings.SIMSPOOF_MCCMNC.save(p.mccMnc);
                    Settings.SIMSPOOF_OP_NAME.save(p.operatorName);
                    Settings.SIM_SPOOF_FEED_REAL.save(false);
                    Settings.REGION_SPOOF.save(true);
                    Settings.REGION_REQUEST_SPOOF.save(true);
                    refreshCountry(row);
                    choice.dismiss();
                    Utils.showToastLong(L10n.t("Restart TikTok to apply this"));
                }).setNegativeButton(L10n.t(context, "Cancel"), null).create();
        dialog.show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }
    private void downloads() {
        PreferenceCategory source = new DownloadsPreferenceCategory(context, scratch());
        take(source, "Remove watermark", "Video download quality", "Save the sound as well",
                "Download original photos", "Save a profile picture on a long press", "Save a story from its share sheet");
        heading("Download folders");
        take(source, "Video destination", "Photo destination", "Sticker destination");
        heading("Offline videos");
        take(source, "Use your own offline videos limit", "Offline videos limit", "Keep offline videos until you delete them");
    }
    private void feed() {
        take(new FeedFilterPreferenceCategory(context, scratch()), "Remove feed ads", "Hide TikTok Shop", "Hide LIVE videos",
                "Hide stories", "Hide photo posts", "Hide AI-generated videos", "Blocked caption words", "Maximum video length", "Views range", "Likes range");
        take(new InboxPreferenceCategory(context, scratch()), "Hide suggested accounts");
        take(new FeedNavigationPreferenceCategory(context, scratch()), "Hide the Tako bubble");
    }
    private void fonts() {
        PreferenceCategory source = new ExtensionPreferenceCategory(context, scratch());
        List<Preference> rows = new ArrayList<>();
        for (int i = 0; i < source.getPreferenceCount(); i++) {
            Preference row = source.getPreference(i);
            String key = row.getKey();
            if (key != null && (key.contains("font") || Settings.SYSTEM_EMOJI.key.equals(key))) rows.add(row);
        }
        for (Preference row : rows) { source.removePreference(row); add(row); }
    }
    private void extras() {
        heading("Playback");
        take(new PlaybackPreferenceCategory(context, scratch()), "Keep playing in the background", "Stop video looping");
        heading("Navigation");
        take(new FeedNavigationPreferenceCategory(context, scratch()), "Filter feed tabs", "Filter bottom tabs");
        take(new InterfacePreferenceCategory(context, scratch()), "Automatic Clear display", "Keep Clear display across videos");
        heading("Privacy");
        take(new PrivacyPreferenceCategory(context, scratch()), "Ghost mode", "Hide online status", "Clean up shared links",
                "Stop analytics and tracking", "Hide a VPN connection", "Block the advertising ID");
        take(new ExtensionPreferenceCategory(context, scratch()), "Keep the language picked in TikTok", "Skip the splash ad");
        heading("Comments");
        take(new CommentsPreferenceCategory(context, scratch()), "Lift text length limits");
        heading("Streak");
        take(new InboxPreferenceCategory(context, scratch()), "Keep a streak going", "Who to message", "When to send", "Message");
    }
    private void diagnostics() {
        take(new DebugPreferenceCategory(context, scratch()), "Log diagnostics", "Capture crash reports locally", "Export diagnostic report", "Clear diagnostic data");
        add(new LicensesPreference(context));
    }
}
