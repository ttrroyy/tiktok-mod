package app.morphe.extension.tiktok.download;

import android.util.AtomicFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Android rename replaces an existing file; Windows File.renameTo does not. */
@Implements(value = AtomicFile.class, minSdk = 30)
public class AndroidAtomicFileShadow {
    @Implementation protected static void rename(File source, File target) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new IllegalStateException("Test journal rename failed", error);
        }
    }
}
