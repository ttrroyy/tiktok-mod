/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Uses the image pipeline shipped in TikTok 47.5.3, including its registered custom decoders. */
final class TikTokImageDecoder {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    static final int MAX_SIDE = 2048;
    interface Types { Class<?> get(String name) throws ClassNotFoundException; }
    static Types types = Class::forName;

    private TikTokImageDecoder() {}

    private static void checkActive() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new MediaBudget.StopException("Photo decoding was cancelled", MediaBudget.StopException.Reason.CANCELLED);
        }
        MediaBudget.check(null);
    }

    static boolean convertToPng(File file) throws IOException {
        checkActive();
        if (file.length() < 16 || file.length() > MAX_BYTES) return false;
        Object source = null;
        Closeable reference = null;
        Method closeSource = null;
        try {
            Class<?> factoryType = types.get("X.04rf");
            Class<?> builderType = types.get("X.05SD");
            Class<?> requestType = types.get("X.06Ji");
            Class<?> resizeType = types.get("X.0Ack");
            Class<?> optionsBuilderType = types.get("com.facebook.imagepipeline.common.ImageDecodeOptionsBuilder");
            Class<?> optionsType = types.get("X.06JF");
            Class<?> sourceType = types.get("X.06Lk");
            Method finished = sourceType.getMethod("LIZ");
            Method result = sourceType.getMethod("getResult");
            closeSource = sourceType.getMethod("close");
            // Only the checked, downloaded local file reaches the host loader, never a remote URI.
            Object builder = builderType.getMethod("LIZLLL", Uri.class).invoke(null, Uri.fromFile(file));
            builderType.getField("LIZJ").set(builder,
                    resizeType.getConstructor(int.class, int.class).newInstance(MAX_SIDE, MAX_SIDE));
            Object options = optionsBuilderType.getConstructor().newInstance();
            optionsBuilderType.getMethod("setBitmapConfig", Bitmap.Config.class).invoke(options, Bitmap.Config.ARGB_8888);
            optionsBuilderType.getField("LJFF").setBoolean(options, true); // forceStaticImage for a photo's frame
            builderType.getField("LJFF").set(builder, optionsType.getConstructor(optionsBuilderType).newInstance(options));
            Object request = builderType.getMethod("LIZ").invoke(builder);
            Object factory = factoryType.getMethod("LJIILJJIL").invoke(null);
            Object pipeline = factoryType.getMethod("LJIILIIL").invoke(factory);
            source = pipeline.getClass().getMethod("LJI", requestType, Object.class).invoke(pipeline, request, null);
            if (source == null) return false;
            long stopAt = SystemClock.elapsedRealtime() + 30_000L;
            while (!(Boolean) finished.invoke(source)) {
                checkActive();
                if (SystemClock.elapsedRealtime() >= stopAt) throw new IOException("Photo decoding timed out");
                try {
                    Thread.sleep(25);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new MediaBudget.StopException("Photo decoding was cancelled", MediaBudget.StopException.Reason.CANCELLED);
                }
            }
            checkActive();
            Object held = result.invoke(source);
            if (held == null) return false;
            reference = (Closeable) held;
            Object image = held.getClass().getMethod("get").invoke(held);
            if (image == null) return false;
            Bitmap bitmap = (Bitmap) image.getClass().getMethod("getUnderlyingBitmap").invoke(image);
            if (bitmap == null || (long) bitmap.getWidth() * bitmap.getHeight() > PhotoToJpeg.MAX_PIXELS) return false;
            try (OutputStream output = MediaFileWriter.withDiskBudget(
                    new FileOutputStream(file), file.getParentFile())) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("Could not prepare the native photo");
                }
            }
            checkActive();
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            Throwable cause = unavailable instanceof InvocationTargetException
                    ? ((InvocationTargetException) unavailable).getCause() : unavailable;
            if (cause instanceof OutOfMemoryError) throw new IOException("Not enough memory to decode the photo");
            return false;
        } catch (OutOfMemoryError exhausted) {
            throw new IOException("Not enough memory to decode the photo");
        } finally {
            // The bitmap belongs to Fresco: release its references instead of recycling a borrowed bitmap.
            try {
                if (reference != null) reference.close();
            } finally {
                if (source != null && closeSource != null) {
                    try { closeSource.invoke(source); } catch (ReflectiveOperationException ignored) { }
                }
            }
        }
    }
}
