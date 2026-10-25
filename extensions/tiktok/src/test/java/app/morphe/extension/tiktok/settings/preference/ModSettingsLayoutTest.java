package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.*;
import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ru-rRU-w360dp-h800dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ModSettingsLayoutTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    private final Map<Field, Boolean> flags = new HashMap<>();
    public static class Page extends Activity {
        @Override public void onCreate(Bundle state) {
            setTheme((getResources().getConfiguration().uiMode & 0x30) == 0x20
                    ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
            super.onCreate(state);
        }
    }
    @Before public void enableHooks() throws Exception {
        for (Field f : SettingsStatus.class.getDeclaredFields()) if (f.getType() == boolean.class && Modifier.isStatic(f.getModifiers())) {
            f.setAccessible(true); flags.put(f, f.getBoolean(null)); f.setBoolean(null, true);
        }
    }
    @After public void restoreHooks() throws Exception { for (var e : flags.entrySet()) e.getKey().setBoolean(null, e.getValue()); }

    @Test public void upgradeEnablesSimOnceAndPreservesTheSelectedCountry() {
        Settings.MOD_MENU_REVISION.save(0);
        Settings.SIM_SPOOF.save(false);
        Settings.SIM_SPOOF_ISO.save("ua");
        app.morphe.extension.shared.settings.BaseSettings.PAUSED.save(true);
        Settings.AUTO_STREAK.save(true);
        Settings.upgradeModDefaults();
        assertFalse(app.morphe.extension.shared.settings.BaseSettings.PAUSED.savedValue());
        assertFalse(Settings.AUTO_STREAK.savedValue());
        assertTrue(Settings.SIM_SPOOF.savedValue());
        assertTrue(Settings.REGION_SPOOF.savedValue());
        assertEquals("ua", Settings.SIM_SPOOF_ISO.savedValue());
        Settings.SIM_SPOOF.save(false);
        Settings.upgradeModDefaults();
        assertFalse(Settings.SIM_SPOOF.savedValue());
    }

    @Test public void upgradeEnablesRefreshRateOnceWithoutResettingExistingChoices() {
        Settings.MOD_MENU_REVISION.save(3);
        Settings.UNCAP_REFRESH_RATE.save(false);
        Settings.SIM_SPOOF.save(false);
        Settings.DOWNLOAD_PROGRESS.save(true);
        Settings.DOWNLOAD_VIDEO_QUALITY.save("1080p");
        Settings.upgradeModDefaults();
        assertTrue(Settings.UNCAP_REFRESH_RATE.savedValue());
        assertEquals(4, (int) Settings.MOD_MENU_REVISION.savedValue());
        assertFalse(Settings.SIM_SPOOF.savedValue());
        assertTrue(Settings.DOWNLOAD_PROGRESS.savedValue());
        assertEquals("1080p", Settings.DOWNLOAD_VIDEO_QUALITY.savedValue());
        Settings.UNCAP_REFRESH_RATE.save(false);
        Settings.upgradeModDefaults();
        assertFalse(Settings.UNCAP_REFRESH_RATE.savedValue());
    }

    @Test public void onlyTheCuratedPagesRemainAndRegionHasThreeControls() throws Exception {
        render("HOME", "REGION", "DOWNLOADS", "FEED_FILTER", "BEHAVIOR", "PLAYBACK", "DIAGNOSTICS");
    }
    @Test @Config(fontScale = 2.0f)
    public void largeTextKeepsNavigationAndCoreControls() throws Exception { render("HOME", "REGION"); }
    @Test @Config(qualifiers = "ru-rRU-w360dp-h800dp-notnight-mdpi")
    public void lightThemeKeepsTheSameSimpleMenu() throws Exception { render("HOME", "REGION"); }
    private void render(String... pages) throws Exception {
        for (String page : pages) {
            try (var owner = Robolectric.buildActivity(Page.class).setup().visible()) {
                Activity activity = owner.get(); Utils.setContext(activity);
                Utils.setIsDarkModeEnabled((activity.getResources().getConfiguration().uiMode & 0x30) == 0x20);
                TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
                if (!page.equals("HOME")) {
                    Bundle args = new Bundle(); args.putString("morphe_settings_section", page); fragment.setArguments(args);
                }
                activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
                activity.getFragmentManager().executePendingTransactions();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                PreferenceScreen screen = fragment.getPreferenceScreen();
                List<String> keys = new ArrayList<>();
                for (int i = 0; i < screen.getPreferenceCount(); i++) {
                    Preference row = screen.getPreference(i); keys.add(row.getKey());
                    assertFalse(String.valueOf(row.getTitle()).contains("Hushfeed"));
                    if (!(row instanceof LicensesPreference)) assertFalse(String.valueOf(row.getSummary()).contains("Hushfeed"));
                }
                if (page.equals("HOME")) {
                    assertEquals(Arrays.asList("section_region", "section_downloads", "section_feed_filter", "section_behavior",
                            "section_playback", "section_diagnostics", "action_mod_updates"), nonNull(keys));
                    assertNull(screen.findPreference("action_search_settings"));
                    assertNull(screen.findPreference("action_support_hushfeed"));
                    assertNull(screen.findPreference(SettingsStatusPreference.KEY));
                }
                if (page.equals("REGION")) {
                    assertEquals(Arrays.asList(Settings.SIM_SPOOF.key, "mod_sim_country", Settings.FORCE_SIM_REGION.key,
                            Settings.MOD_REGION_ADVANCED.key), nonNull(keys));
                    assertTrue(Settings.SIM_SPOOF.savedValue());
                    Preference advanced = screen.findPreference(Settings.MOD_REGION_ADVANCED.key);
                    advanced.getOnPreferenceChangeListener().onPreferenceChange(advanced, true);
                    ((android.preference.SwitchPreference) advanced).setChecked(true);
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    assertNotNull(screen.findPreference(Settings.SIMSPOOF_MCCMNC.key));
                    assertNotNull(screen.findPreference(Settings.SIMSPOOF_OP_NAME.key));
                    assertNotNull(screen.findPreference(Settings.REGION_REQUEST_SPOOF.key));
                    capture(activity, "region-expanded");
                    advanced.getOnPreferenceChangeListener().onPreferenceChange(advanced, false);
                    ((android.preference.SwitchPreference) advanced).setChecked(false);
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    assertNull(screen.findPreference(Settings.SIMSPOOF_MCCMNC.key));
                }
                if (page.equals("DOWNLOADS")) {
                    assertNull(screen.findPreference(Settings.DOWNLOAD_PROGRESS.key));
                    assertNull(screen.findPreference(Settings.DOWNLOAD_PHOTOS_AS_VIDEO.key));
                    assertNotNull(screen.findPreference(Settings.DOWNLOAD_ORIGINAL_PHOTOS.key).getSummary());
                }
                if (page.equals("BEHAVIOR")) assertNotNull(screen.findPreference(Settings.IMPORTED_FONT.key));
                capture(activity, page);
            }
        }
    }
    private static List<String> nonNull(List<String> values) {
        List<String> result = new ArrayList<>(); for (String value : values) if (value != null) result.add(value); return result;
    }
    private static void prepare(View view) {
        view.setScrollIndicators(0);
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view; for (int i=0;i<group.getChildCount();i++) prepare(group.getChildAt(i)); }
    }
    private static void capture(Activity activity, String name) throws Exception {
        String output = System.getProperty("morphe.screenshotDir"); if (output == null) return;
        View root = activity.findViewById(android.R.id.content); prepare(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0,0,360,800);
        Bitmap bitmap = Bitmap.createBitmap(360,800,Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        android.widget.TextView title = root.findViewWithTag("hushfeed_page_title");
        assertNotNull(title.getLayout());
        for (int i=0;i<title.getLineCount();i++) assertEquals(0,title.getLayout().getEllipsisCount(i));
        String style = activity.getResources().getConfiguration().fontScale > 1.3f ? "large" : Utils.isDarkModeEnabled() ? "dark" : "light";
        File file = new File(output, name.toLowerCase(Locale.ROOT)+"-"+style+".png"); file.getParentFile().mkdirs();
        try (var stream = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
        bitmap.recycle();
    }
}
