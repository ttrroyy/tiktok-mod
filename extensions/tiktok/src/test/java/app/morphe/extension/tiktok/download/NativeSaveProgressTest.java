package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import app.morphe.extension.tiktok.SettingsContextRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class NativeSaveProgressTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @After public void clear() {
        ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",null);
        SaveProgress.nativeEventsForTests = null;
    }
    @Test public void nativeCancelReachesTheActiveSaveAndCannotAffectAnotherSave() {
        SaveProgress current=SaveProgress.queuedFiles(3).forPost(new Object());
        SaveProgress other=SaveProgress.queuedFiles(2).forPost(new Object());
        ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",current);
        assertTrue(SaveProgress.cancelNative());
        assertTrue(current.isCancelled()); assertFalse(other.isCancelled());
        ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",null);
        assertFalse(SaveProgress.cancelNative());
        assertFalse(other.isCancelled());
    }

    @Test public void simultaneousSavesCannotOverwriteOrDismissTheNativeOwner() {
        java.util.List<Integer> events = new java.util.ArrayList<>();
        SaveProgress.nativeEventsForTests = (post, state, percent) -> { events.add(state); return true; };
        SaveProgress first = SaveProgress.queuedFiles(1).forPost(new Object());
        SaveProgress second = SaveProgress.queuedFiles(1).forPost(new Object());
        ReflectionHelpers.callInstanceMethod(first, "show");
        ReflectionHelpers.callInstanceMethod(second, "show");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(java.util.List.of(1), events);
        assertSame(first, ReflectionHelpers.getStaticField(SaveProgress.class, "nativeCurrent"));
        second.dismiss();
        assertEquals(java.util.List.of(1), events);
        first.dismiss();
        first.dismiss();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(java.util.List.of(1, 3), events);
        assertNull(ReflectionHelpers.getStaticField(SaveProgress.class, "nativeCurrent"));
    }

    @Test public void failedSaveClearsTheBarWithoutLeavingTheHostRetryJob() {
        java.util.List<Integer> events = new java.util.ArrayList<>();
        SaveProgress.nativeEventsForTests = (post, state, percent) -> { events.add(state); return true; };
        SaveProgress current = SaveProgress.queuedFiles(1).forPost(new Object());
        ReflectionHelpers.callInstanceMethod(current, "show");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        current.run(index -> { throw new java.io.IOException("Photo decode failed"); });
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(3, (int) events.get(events.size() - 1));
        assertFalse(events.contains(5));
        assertNull(ReflectionHelpers.getStaticField(SaveProgress.class, "nativeCurrent"));
    }
}
