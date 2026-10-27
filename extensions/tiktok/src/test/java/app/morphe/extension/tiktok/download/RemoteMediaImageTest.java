package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import app.morphe.extension.tiktok.SettingsContextRule;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RemoteMediaImageTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();

    private static byte[] bitmapFile() {
        return ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 'B').put((byte) 'M').putInt(58).putInt(0).putInt(54)
                .putInt(40).putInt(1).putInt(1).putShort((short) 1).putShort((short) 24)
                .putInt(0).putInt(4).putInt(0).putInt(0).putInt(0).putInt(0)
                .put(new byte[]{0x33, 0x66, (byte) 0x99, 0}).array();
    }

    @Test public void platformRecognizedPhotoKeepsItsOriginalBytesDespiteAnUnknownShortSignature() throws Exception {
        byte[] body = bitmapFile();
        var transport = MediaTransportFixtures.publicClient(url ->
                MediaTransportFixtures.response(url, 200, body, "application/octet-stream", Map.of()));
        File target = File.createTempFile("photo-format-", ".tmp", RuntimeEnvironment.getApplication().getCacheDir());
        try {
            assertEquals("bmp", RemoteMedia.fetch(List.of("https://cdn.example/photo.image"), target,
                    RemoteMedia.Kind.IMAGE, transport));
            assertArrayEquals(body, Files.readAllBytes(target.toPath()));
        } finally {
            target.delete();
        }
    }

    @Test public void aPhotoRejectedByTheOldSignatureCheckReachesTheRealSlideshowPhotoPreparation() throws Exception {
        var previous = RemoteMedia.transportForTests;
        List<File> temporary = new ArrayList<>();
        Bitmap photo = null, frame = null;
        try {
            RemoteMedia.transportForTests = MediaTransportFixtures.publicClient(url ->
                    MediaTransportFixtures.response(url, 200, bitmapFile()));
            File cached = ReflectionHelpers.callStaticMethod(SlideshowVideo.class, "fetchPhoto",
                    from(Context.class, RuntimeEnvironment.getApplication()),
                    from(List.class, List.of("https://cdn.example/photo.image")),
                    from(int.class, 1), from(List.class, temporary));
            assertEquals(List.of(cached), temporary);
            assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'},
                    java.util.Arrays.copyOf(Files.readAllBytes(cached.toPath()), 4));
            photo = SlideshowVideo.decode(cached, new int[]{20, 20});
            assertNotNull(photo);
            frame = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
            SlideshowVideo.drawSwipe(new Canvas(frame), null, photo, 20, 20, new Paint(), 1f);
            assertEquals(0xff996633, frame.getPixel(10, 10));
        } finally {
            RemoteMedia.transportForTests = previous;
            if (photo != null) photo.recycle();
            if (frame != null) frame.recycle();
            for (File file : temporary) assertTrue(MediaCache.delete(file));
        }
    }

    @Test public void htmlClaimingToBeAnImageIsRefusedAndTheNextMirrorIsTried() throws Exception {
        byte[] html = "<html><body>not a photo</body></html>".getBytes(StandardCharsets.UTF_8);
        var transport = MediaTransportFixtures.publicClient(url -> MediaTransportFixtures.response(url, 200,
                url.getPath().contains("bad") ? html : bitmapFile(), "image/png", Map.of()));
        File target = File.createTempFile("photo-format-", ".tmp", RuntimeEnvironment.getApplication().getCacheDir());
        try {
            assertThrows(IOException.class, () -> RemoteMedia.fetch(List.of("https://cdn.example/bad"),
                    target, RemoteMedia.Kind.IMAGE, transport));
            assertFalse(target.exists());
            assertEquals("bmp", RemoteMedia.fetch(List.of("https://cdn.example/bad", "https://cdn.example/good"),
                    target, RemoteMedia.Kind.IMAGE, transport));
            assertArrayEquals(bitmapFile(), Files.readAllBytes(target.toPath()));
        } finally {
            target.delete();
        }
    }

    @Test public void imageFallbackCannotAcceptAnImageAsVideoOrSound() throws Exception {
        var transport = MediaTransportFixtures.publicClient(url -> MediaTransportFixtures.response(url, 200, bitmapFile()));
        File target = File.createTempFile("photo-format-", ".tmp", RuntimeEnvironment.getApplication().getCacheDir());
        try {
            for (var kind : List.of(RemoteMedia.Kind.VIDEO, RemoteMedia.Kind.AUDIO)) {
                assertThrows(IOException.class, () -> RemoteMedia.fetch(List.of("https://cdn.example/photo.image"), target, kind, transport));
                assertFalse(target.exists());
            }
        } finally {
            target.delete();
        }
    }

    @Test public void remainingHevcImageBrandsAreNotMistakenForAnUnknownFormat() throws Exception {
        for (String brand : List.of("hevc", "hevx")) {
            byte[] header = ByteBuffer.allocate(16).putInt(16).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                    .put(brand.getBytes(StandardCharsets.US_ASCII)).putInt(0).array();
            var transport = MediaTransportFixtures.publicClient(url -> MediaTransportFixtures.response(url, 200, header));
            File target = File.createTempFile("photo-format-", ".tmp", RuntimeEnvironment.getApplication().getCacheDir());
            try {
                assertEquals("heif", RemoteMedia.fetch(List.of("https://cdn.example/photo.image"), target, RemoteMedia.Kind.IMAGE, transport));
            } finally {
                target.delete();
            }
        }
    }
}
