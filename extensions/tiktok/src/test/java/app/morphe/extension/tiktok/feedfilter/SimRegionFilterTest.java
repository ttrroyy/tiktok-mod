package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.*;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SimRegionFilterTest {
    @Rule public final SettingsContextRule context = new SettingsContextRule();
    public static class Video extends Aweme {
        private final String country;
        Video(String country) { this.country = country; }
        public String getRegion() { return country; }
        public String getAid() { return "test-" + System.identityHashCode(this); }
        public boolean isAd() { return false; }
        public boolean isSoftAd() { return false; }
        public com.ss.android.ugc.aweme.feed.model.AwemeRawAd getAwemeRawAd() { return null; }
        public int getAwemeType() { return 0; }
        public java.util.List getBanners() { return null; }
        public void setBanners(java.util.List banners) { }
        public String getShareUrl() { return null; }
    }
    @Before public void setup() {
        Settings.SIM_SPOOF.save(true); Settings.SIM_SPOOF_ISO.save("ua"); Settings.FORCE_SIM_REGION.save(true);
    }
    @Test public void matchesTheChosenSimCountryAndRejectsMissingOrDifferentCountries() {
        SimRegionFilter filter = new SimRegionFilter();
        assertTrue(filter.getEnabled());
        assertFalse(filter.getFiltered(new Video("UA")));
        assertFalse(filter.getFiltered(new Video(" ua ")));
        assertTrue(filter.getFiltered(new Video("US")));
        assertTrue(filter.getFiltered(new Video(null)));
        assertTrue(filter.getFiltered(new Video("")));
        Settings.SIM_SPOOF.save(false); assertFalse(filter.getEnabled());
    }
    @Test public void aForYouBatchCannotKeepForeignVideosThroughTheFallback() {
        FeedItemList batch = new FeedItemList();
        batch.items = new ArrayList<>(List.of(new Video("US"), new Video("DE")));
        FeedItemsFilter.filter(batch);
        assertTrue(batch.items.isEmpty());
        Video local = new Video("UA");
        batch = new FeedItemList(); batch.items = new ArrayList<>(List.of(new Video("US"), local));
        FeedItemsFilter.filter(batch);
        assertEquals(List.of(local), batch.items);
    }
}
