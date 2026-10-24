package app.morphe.extension.tiktok.update;

import static org.junit.Assert.*;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class ModUpdateSignatureTest {
    @Test public void acceptsOnlyTheSamePackageSignerAndANonDecreasingVersion() throws Exception {
        var manager = RuntimeEnvironment.getApplication().getPackageManager();
        var shadow = Shadows.shadowOf(manager);
        String name = RuntimeEnvironment.getApplication().getPackageName();
        PackageInfo current = manager.getPackageInfo(name, 0);
        current.versionCode = 10;
        current.signatures = new Signature[]{new Signature(new byte[]{1, 2, 3})};
        shadow.installPackage(current);
        File apk = new File(RuntimeEnvironment.getApplication().getCacheDir(), "update.apk");
        PackageInfo next = new PackageInfo();
        next.packageName = name;
        next.versionCode = 11;
        next.signatures = current.signatures;
        shadow.setPackageArchiveInfo(apk.getAbsolutePath(), next);
        ModUpdater.verifyPackage(manager, name, apk);
        next.versionCode = 9;
        assertThrows(IOException.class, () -> ModUpdater.verifyPackage(manager, name, apk));
        next.versionCode = 11;
        next.packageName = "other.application";
        assertThrows(IOException.class, () -> ModUpdater.verifyPackage(manager, name, apk));
        next.packageName = name;
        next.signatures = new Signature[]{new Signature(new byte[]{4, 5, 6})};
        assertThrows(IOException.class, () -> ModUpdater.verifyPackage(manager, name, apk));
        next.signatures = null;
        assertThrows(IOException.class, () -> ModUpdater.verifyPackage(manager, name, apk));
    }
}
