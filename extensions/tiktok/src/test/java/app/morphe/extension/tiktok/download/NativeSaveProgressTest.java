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
    @After public void clear() { ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",null); }
    @Test public void nativeCancelReachesTheActiveSaveAndCannotAffectAnotherSave() {
        SaveProgress current=SaveProgress.queuedFiles(3).forPost(new Object());
        SaveProgress other=SaveProgress.queuedFiles(2).forPost(new Object());
        ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",current);
        SaveProgress.cancelNative();
        assertTrue(current.isCancelled()); assertFalse(other.isCancelled());
        ReflectionHelpers.setStaticField(SaveProgress.class,"nativeCurrent",null);
        SaveProgress.cancelNative();
        assertFalse(other.isCancelled());
    }
}
