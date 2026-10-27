package app.morphe.extension.tiktok.share;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Dialog;
import com.ss.android.ugc.aweme.share.core.ui.SkeletonSharePanelFragment;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34})
public class SingleShareSheetTest {
    private ActivityController<Activity> owner;
    private Activity activity;

    @Before public void setup() {
        SingleShareSheet.resetForTests();
        owner = Robolectric.buildActivity(Activity.class).setup();
        activity = owner.get();
    }

    @After public void cleanup() {
        SingleShareSheet.resetForTests();
        owner.close();
    }

    @Test public void repeatedPendingRequestsDoNotExtendAFailedOpeningForever() {
        assertTrue(SingleShareSheet.begin(activity));
        advance(1000);
        assertFalse(SingleShareSheet.begin(activity));
        advance(999);
        assertFalse(SingleShareSheet.begin(activity));
        advance(1);
        assertTrue(SingleShareSheet.begin(activity));
        assertFalse(SingleShareSheet.begin(activity));
    }

    @Test public void nativeFailureAllowsAnImmediateRetry() {
        assertTrue(SingleShareSheet.begin(activity));
        SingleShareSheet.completed(activity, null);
        assertTrue(SingleShareSheet.begin(activity));
    }

    @Test public void addedSheetBlocksDuplicatesAndDismissalAllowsImmediateReopening() {
        Sheet sheet = new Sheet(activity);
        assertTrue(SingleShareSheet.begin(activity));
        SingleShareSheet.completed(activity, sheet);
        advance(3000);
        assertFalse(SingleShareSheet.begin(activity));
        // Android may still report isAdded until its asynchronous removal completes.
        sheet.mDismissed = true;
        assertTrue(SingleShareSheet.begin(activity));
    }

    @Test public void aVisibleDialogBlocksDuplicatesEvenBeforeIsAddedIsReported() {
        Sheet sheet = new Sheet(activity);
        sheet.added = false;
        sheet.dialog = new Dialog(activity);
        sheet.dialog.show();
        try {
            SingleShareSheet.completed(activity, sheet);
            advance(3000);
            assertFalse(SingleShareSheet.begin(activity));
        } finally { sheet.dialog.dismiss(); }
    }

    @Test public void restoredShareSheetIsRecognizedButUnrelatedDialogsAreIgnored() {
        Sheet sheet = new Sheet(activity);
        sheet.LLJLLIL = new Object();
        SingleShareSheet.started(sheet);
        assertTrue(SingleShareSheet.begin(activity));
        SingleShareSheet.completed(activity, null);
        sheet.LLJLLIL = new SkeletonSharePanelFragment();
        SingleShareSheet.started(sheet);
        advance(3000);
        assertFalse(SingleShareSheet.begin(activity));
    }

    @Test public void otherActivitiesStayIndependentAndDestroyedOwnersCannotOpen() {
        assertTrue(SingleShareSheet.begin(activity));
        try (ActivityController<Activity> second = Robolectric.buildActivity(Activity.class).setup()) {
            assertTrue(SingleShareSheet.begin(second.get()));
            second.get().finish();
            assertFalse(SingleShareSheet.begin(second.get()));
        }
        owner.pause().stop().destroy();
        assertFalse(SingleShareSheet.begin(activity));
        assertFalse(SingleShareSheet.begin(null));
    }

    private static void advance(long ms) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); }

    public static final class Sheet {
        public boolean mDismissed;
        public Object LLJLLIL;
        boolean added = true;
        Dialog dialog;
        final Activity owner;
        Sheet(Activity owner) { this.owner = owner; }
        public boolean isAdded() { return added; }
        public Dialog getDialog() { return dialog; }
        public Activity getActivity() { return owner; }
    }
}
