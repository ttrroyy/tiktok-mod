package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.net.Uri;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Tests the reflection contract without pretending to execute TikTok's ARM codecs on the JVM. */
public final class NativeImageHostFixtures {
    static int starts;
    static boolean refuse, sourceClosed, referenceClosed;
    static Runnable onPoll;
    static Request lastRequest;
    static Bitmap bitmap;

    static void reset() {
        starts = 0;
        refuse = sourceClosed = referenceClosed = false;
        onPoll = null;
        lastRequest = null;
        bitmap = null;
    }

    static byte[] encoded(boolean knownHeader) {
        byte[] marker = "native-test-image-format".getBytes(StandardCharsets.US_ASCII);
        if (knownHeader) {
            byte[] png = Arrays.copyOf(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, 8 + marker.length);
            System.arraycopy(marker, 0, png, 8, marker.length);
            return png;
        }
        return marker;
    }

    static Class<?> type(String name) throws ClassNotFoundException {
        switch (name) {
            case "X.04rf": return Factory.class;
            case "X.05SD": return Builder.class;
            case "X.06Ji": return Request.class;
            case "X.0Ack": return Resize.class;
            case "com.facebook.imagepipeline.common.ImageDecodeOptionsBuilder": return OptionsBuilder.class;
            case "X.06JF": return Options.class;
            case "X.06Lk": return Source.class;
            default: throw new ClassNotFoundException(name);
        }
    }

    public static class Factory {
        public static Factory LJIILJJIL() { return new Factory(); }
        public Pipeline LJIILIIL() { return new Pipeline(); }
    }
    public static class Builder {
        public Resize LIZJ;
        public Options LJFF;
        private Uri uri;
        public static Builder LIZLLL(Uri uri) {
            Builder builder = new Builder();
            builder.uri = uri;
            return builder;
        }
        public Request LIZ() { return new Request(uri, LIZJ, LJFF); }
    }
    public static class Request {
        final Uri uri;
        final Resize resize;
        final Options options;
        Request(Uri uri, Resize resize, Options options) {
            this.uri = uri;
            this.resize = resize;
            this.options = options;
        }
    }
    public static class Resize {
        final int width, height;
        public Resize(int width, int height) { this.width = width; this.height = height; }
    }
    public static class OptionsBuilder {
        public boolean LJFF;
        Bitmap.Config config;
        public OptionsBuilder setBitmapConfig(Bitmap.Config config) { this.config = config; return this; }
    }
    public static class Options {
        final boolean staticFrame;
        final Bitmap.Config config;
        public Options(OptionsBuilder builder) { staticFrame = builder.LJFF; config = builder.config; }
    }
    public static class Pipeline {
        public Source LJI(Request request, Object caller) throws IOException {
            starts++;
            lastRequest = request;
            sourceClosed = referenceClosed = false;
            if (!"file".equals(request.uri.getScheme())) throw new AssertionError("A remote URI reached the host");
            byte[] bytes = Files.readAllBytes(new File(request.uri.getPath()).toPath());
            if (!refuse && (Arrays.equals(bytes, encoded(false)) || Arrays.equals(bytes, encoded(true)))) {
                bitmap = Bitmap.createBitmap(3, 2, request.options.config);
                bitmap.eraseColor(0x80996633);
            }
            return new Source();
        }
    }
    public static class Source {
        public boolean LIZ() {
            if (onPoll != null) { onPoll.run(); return false; }
            return true;
        }
        public Object getResult() { return bitmap == null ? null : new Reference(); }
        public boolean close() {
            sourceClosed = true;
            if (bitmap != null && (referenceClosed || onPoll != null)) bitmap.recycle();
            return true;
        }
    }
    public static class Reference implements Closeable {
        public Image get() { return new Image(); }
        @Override public void close() {
            referenceClosed = true;
            if (sourceClosed && bitmap != null) bitmap.recycle();
        }
    }
    public static class Image {
        public Bitmap getUnderlyingBitmap() { return bitmap; }
    }
}
