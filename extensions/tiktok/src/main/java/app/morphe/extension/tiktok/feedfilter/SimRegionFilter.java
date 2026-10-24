/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.spoof.region.RegionSpoof;
import com.ss.android.ugc.aweme.feed.model.Aweme;

/** Strict country matching for recommendations only. Unknown countries do not match. */
public final class SimRegionFilter implements IFilter {
    @Override public boolean getEnabled() {
        return Settings.SIM_SPOOF.get() && Settings.FORCE_SIM_REGION.get()
                && RegionSpoof.validCountry(Settings.SIM_SPOOF_ISO.get());
    }
    @Override public boolean getFiltered(Aweme video) {
        String country = Reflect.string(video, "getRegion", "region");
        return country == null || !Settings.SIM_SPOOF_ISO.get().trim().equalsIgnoreCase(country.trim());
    }
}
