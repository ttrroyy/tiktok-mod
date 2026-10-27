package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import app.morphe.extension.tiktok.SettingsContextRule;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 34})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TikTokImageDecoderTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    private TikTokImageDecoder.Types previous;

    @Before public void reset() {
        previous = TikTokImageDecoder.types;
        TikTokImageDecoder.types = NativeImageHostFixtures::type;
        NativeImageHostFixtures.reset();
    }
    @After public void restore() {
        TikTokImageDecoder.types = previous;
        Thread.interrupted();
    }
    private File target() throws IOException {
        return File.createTempFile("native-photo-", ".tmp", RuntimeEnvironment.getApplication().getCacheDir());
    }

    @Test public void nativeOnlyPhotoUsesTheGeneralHostPipelineAndKeepsAlphaInPng() throws Exception {
        File file = target();
        Bitmap result = null;
        try {
            var transport = MediaTransportFixtures.publicClient(url ->
                    MediaTransportFixtures.response(url, 200, NativeImageHostFixtures.encoded(false)));
            assertEquals("png", RemoteMedia.fetch(List.of("https://cdn.example/photo.image"), file,
                    RemoteMedia.Kind.IMAGE, transport));
            assertEquals(1, NativeImageHostFixtures.starts);
            var request = NativeImageHostFixtures.lastRequest;
            assertEquals("file", request.uri.getScheme());
            assertEquals(TikTokImageDecoder.MAX_SIDE, request.resize.width);
            assertEquals(TikTokImageDecoder.MAX_SIDE, request.resize.height);
            assertTrue(request.options.staticFrame);
            assertTrue(NativeImageHostFixtures.sourceClosed);
            assertTrue(NativeImageHostFixtures.referenceClosed);
            assertTrue(NativeImageHostFixtures.bitmap.isRecycled());
            result = BitmapFactory.decodeFile(file.getPath());
            assertNotNull(result);
            assertEquals(3, result.getWidth());
            assertEquals(2, result.getHeight());
            assertEquals(0x80, android.graphics.Color.alpha(result.getPixel(1, 1)));
        } finally {
            if (result != null) result.recycle();
            file.delete();
        }
    }

    @Test public void bothAnUnknownHeaderAndAKnownFormatAndroidCannotDecodeReachRealSlideshowPreparation() throws Exception {
        var transportBefore = RemoteMedia.transportForTests;
        try {
            for (boolean knownHeader : List.of(false, true)) {
                NativeImageHostFixtures.reset();
                List<File> temporary = new ArrayList<>();
                Bitmap photo = null;
                try {
                    RemoteMedia.transportForTests = MediaTransportFixtures.publicClient(url ->
                            MediaTransportFixtures.response(url, 200, NativeImageHostFixtures.encoded(knownHeader)));
                    File file = ReflectionHelpers.callStaticMethod(SlideshowVideo.class, "fetchPhoto",
                            from(Context.class, RuntimeEnvironment.getApplication()),
                            from(List.class, List.of("https://cdn.example/photo.image")),
                            from(int.class, 7), from(List.class, temporary));
                    assertEquals(List.of(file), temporary);
                    photo = SlideshowVideo.decode(file, new int[]{20, 20});
                    assertNotNull(photo);
                    assertEquals(3, photo.getWidth());
                    assertEquals(1, NativeImageHostFixtures.starts);
                    assertTrue(NativeImageHostFixtures.sourceClosed);
                } finally {
                    if (photo != null) photo.recycle();
                    for (File file : temporary) assertTrue(MediaCache.delete(file));
                }
            }
        } finally {
            RemoteMedia.transportForTests = transportBefore;
        }
    }

    @Test public void htmlAndAnImageRefusedByTheHostAreDeletedAndTheSourceIsClosed() throws Exception {
        File file = target();
        try {
            for (byte[] bytes : List.of("<html>not a photograph</html>".getBytes(StandardCharsets.UTF_8),
                    NativeImageHostFixtures.encoded(false))) {
                NativeImageHostFixtures.reset();
                NativeImageHostFixtures.refuse = true;
                var transport = MediaTransportFixtures.publicClient(url ->
                        MediaTransportFixtures.response(url, 200, bytes));
                assertThrows(IOException.class, () -> RemoteMedia.fetch(List.of("https://cdn.example/photo.image"),
                        file, RemoteMedia.Kind.IMAGE, transport));
                assertFalse(file.exists());
                assertTrue(NativeImageHostFixtures.sourceClosed);
            }
        } finally {
            file.delete();
        }
    }

    @Test public void cancellationClosesThePendingHostRequest() throws Exception {
        File file = target();
        try {
            Files.write(file.toPath(), NativeImageHostFixtures.encoded(false));
            NativeImageHostFixtures.onPoll = () -> Thread.currentThread().interrupt();
            MediaBudget.StopException stopped = assertThrows(MediaBudget.StopException.class,
                    () -> TikTokImageDecoder.convertToPng(file));
            assertEquals(MediaBudget.StopException.Reason.CANCELLED, stopped.reason);
            assertTrue(NativeImageHostFixtures.sourceClosed);
        } finally {
            Thread.interrupted();
            file.delete();
        }
    }

    @Test public void aHostRequestThatNeverFinishesTimesOutAndCloses() throws Exception {
        File file = target();
        try {
            Files.write(file.toPath(), NativeImageHostFixtures.encoded(false));
            NativeImageHostFixtures.onPoll = () -> ShadowSystemClock.advanceBy(Duration.ofSeconds(31));
            assertThrows(IOException.class, () -> TikTokImageDecoder.convertToPng(file));
            assertTrue(NativeImageHostFixtures.sourceClosed);
        } finally {
            file.delete();
        }
    }

    @Test public void encodedImageOverTheBudgetIsRefusedBeforeCallingTheHost() throws Exception {
        File file = target();
        try {
            try (RandomAccessFile sparse = new RandomAccessFile(file, "rw")) {
                sparse.setLength((long) TikTokImageDecoder.MAX_BYTES + 1);
            }
            assertFalse(TikTokImageDecoder.convertToPng(file));
            assertEquals(0, NativeImageHostFixtures.starts);
        } finally {
            file.delete();
        }
    }
}
