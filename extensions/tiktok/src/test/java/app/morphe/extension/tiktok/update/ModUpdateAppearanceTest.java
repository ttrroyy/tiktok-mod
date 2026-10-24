package app.morphe.extension.tiktok.update;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "ru-rRU-w360dp-h800dp-mdpi-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ModUpdateAppearanceTest {
    @Test public void downloadProgressHasACancelActionAndAConsistentRange() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class)) {
            owner.get().setTheme(android.R.style.Theme_Material_NoActionBar);
            owner.setup().visible();
            Activity activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            var constructor = ModUpdater.class.getDeclaredConstructor(Activity.class);
            constructor.setAccessible(true);
            Object updater = constructor.newInstance(activity);
            var show = ModUpdater.class.getDeclaredMethod("showProgress", Activity.class, String.class, boolean.class);
            show.setAccessible(true);
            show.invoke(updater, activity, "Загрузка обновления", false);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            ProgressBar bar = progress(dialog.getWindow().getDecorView());
            assertNotNull(bar);
            assertEquals(100, bar.getMax());
            assertFalse(bar.isIndeterminate());
            bar.setProgress(42);
            String directory = System.getProperty("morphe.screenshotDir");
            if (directory != null) {
                View decor = dialog.getWindow().getDecorView();
                decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
                decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
                Bitmap image = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(image));
                File destination = new File(directory, "update-progress.png");
                destination.getParentFile().mkdirs();
                try (var stream = new FileOutputStream(destination)) { image.compress(Bitmap.CompressFormat.PNG, 100, stream); }
                image.recycle();
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            var cancelled = ModUpdater.class.getDeclaredField("cancelled");
            cancelled.setAccessible(true);
            assertTrue(((AtomicBoolean) cancelled.get(updater)).get());
        }
    }
    private static ProgressBar progress(View view) {
        if (view instanceof ProgressBar) return (ProgressBar) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ProgressBar result = progress(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
}
