package app.morphe.extension.tiktok.update;

import static org.junit.Assert.*;
import android.net.Uri;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ModUpdateProviderTest {
    @Test public void sharesOnlyTheFixedApkAndRejectsWritesAndTraversal() throws Exception {
        var controller = Robolectric.buildContentProvider(ModUpdateProvider.class).create();
        ModUpdateProvider provider = controller.get();
        var context = provider.getContext();
        var file = ModUpdateProvider.apk(context);
        file.getParentFile().mkdirs();
        try (var stream = new FileOutputStream(file)) { stream.write(new byte[]{1, 2, 3}); }
        Uri uri = ModUpdateProvider.uri(context);
        try (var descriptor = provider.openFile(uri, "r")) { assertNotNull(descriptor); }
        assertThrows(FileNotFoundException.class, () -> provider.openFile(uri, "rw"));
        assertThrows(FileNotFoundException.class, () -> provider.openFile(Uri.parse(uri + "/../private"), "r"));
        assertThrows(FileNotFoundException.class, () -> provider.openFile(Uri.parse(uri + "?other=file"), "r"));
        assertEquals("application/vnd.android.package-archive", provider.getType(uri));
        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", ModUpdater.hash(file));
        file.delete();
    }
}
