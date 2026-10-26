/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.download;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A photo post saved as one MP4: each photo held on screen for the chosen time, the post's sound
 * under it, and nothing of TikTok's added. TikTok's own "Download video" on a one-photo post
 * builds that video on the phone and stamps its logo, the creator's handle and an end card on
 * it, and a post with several photos has no video choice at all.
 *
 * <p>The native photo picker offers a per-save checkbox above Select all.
 *
 * <p>The video is drawn in {@link SlideshowEncoder}. Everything decided along the way is a static
 * method here, so a test can reach it without a codec.
 */
public final class SlideshowVideo {
    static final int FPS = 30;
    static final int SWIPE_FRAMES = 10;
    static final int KEYFRAME_SECONDS = 1;
    /** The frame a portrait photo fills, as long and short sides. Landscape turns it round. */
    static final int LONG_SIDE = 1920;
    static final int SHORT_SIDE = 1080;
    /** Smaller frames for an encoder that turns the full size down. */
    private static final int[][] FALLBACK_SIDES = {{1280, 720}, {960, 540}};
    /** The shortest a pass of a repeated sound counts as. */
    static final long MIN_LOOP_US = 100_000L;

    private static final Set<String> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private SlideshowVideo() {}

    static boolean enabled() {
        return SettingsStatus.advancedDownloadsEnabled
                && !app.morphe.extension.shared.settings.Setting.isPaused();
    }

    /**
     * Queues the video of the photos at {@code indices}, all of them for null. Always takes the
     * save: TikTok's own path from here is a watermarked render, and its callback wants that
     * video's path, so a failure says so itself instead of falling back.
     */
    static boolean start(Object aweme, Set<?> indices) {
        return start(aweme, indices, true);
    }

    /**
     * With Check for already-saved videos on, a post saved here before is offered Open or Save
     * again first ({@code check}, false for Save again), and the video that's made is recorded
     * under the post's id.
     */
    private static boolean start(Object aweme, Set<?> indices, boolean check) {
        Context context = Utils.getContext();
        if (context == null) return failed();
        if (android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return failed();
        List<List<String>> photos = OriginalPhotos.sources(aweme);
        if (photos.isEmpty()) {
            Utils.showToastShort(L10n.t("The original photos aren't available. No video was saved."));
            return true;
        }
        List<Integer> chosen = OriginalPhotos.positions(indices, photos.size());
        String id = Reflect.string(aweme, "getAid", "aid");
        if (chosen.isEmpty() || id == null) return failed();
        List<List<String>> sources = new ArrayList<>();
        for (int position : chosen) sources.add(new ArrayList<>(photos.get(position)));
        List<String> sound = soundSources(aweme);
        int seconds = seconds(Settings.PHOTO_VIDEO_SECONDS.get());
        String name, path;
        try {
            name = DownloadFilenameFormatter.formatSelectedVideoName(aweme);
            path = DownloadFilenameFormatter.destinationPath(aweme, false);
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not name the photo video", exception);
            return failed();
        }
        String key = "photo video " + id;
        if (!ACTIVE.add(id)) {
            Utils.showToastShort(MediaJobScheduler.busyMessage(key));
            return true;
        }
        Context app = context.getApplicationContext();
        SaveProgress progress = SaveProgress.queued(1, Settings.DOWNLOAD_PROGRESS.get()).forPost(aweme);
        boolean checkSaved = Settings.CHECK_SAVED_VIDEOS.get();
        // Read as the save is accepted: a forget in settings while it runs leaves it unrecorded.
        long archiveGeneration = SavedVideoArchive.generation();
        AtomicBoolean asking = new AtomicBoolean();
        MediaJobScheduler.Job job = progress.submit("photo video", key, () -> {
            MediaFileWriter.Saved[] saved = {null};
            boolean[] silent = {false};
            try {
                if (checkSaved && check) {
                    Runnable saveAgain = () -> {
                        ACTIVE.remove(id);
                        start(aweme, indices, false);
                    };
                    asking.set(true);
                    SavedVideoArchive.Check found = SavedVideoArchive.check(app, id, saveAgain,
                            () -> ACTIVE.remove(id));
                    if (found == SavedVideoArchive.Check.OFFERED) {
                        progress.dismiss();
                        return;
                    }
                    asking.set(false);
                    if (found == SavedVideoArchive.Check.FAILED) return;
                }
                SaveProgress.Outcome outcome = progress.run(index ->
                        saved[0] = make(app, sources, sound, seconds, name, path, progress, silent));
                if (saved[0] == null) {
                    if (outcome.cancelled > 0 && outcome.stop == SaveProgress.Stop.NONE) return;
                    Utils.showToastLong(outcome.stop != SaveProgress.Stop.NONE
                            ? SaveProgress.message(outcome, "")
                            : L10n.t("The video couldn't be made from these photos. Try again."));
                } else {
                    if (checkSaved) SavedVideoArchive.rememberQuietly(app, id, saved[0], archiveGeneration);
                    if (silent[0]) Utils.showToastLong(L10n.t("The video was saved without sound"));
                    else SaveNotice.completed();
                }
            } catch (RuntimeException exception) {
                Logger.printException(() -> "Photo video failed", exception);
                Utils.showToastLong(L10n.t("The video couldn't be made from these photos. Try again."));
            }
        }, () -> {
            if (!asking.get()) ACTIVE.remove(id);
        });
        if (job == null) return true;
        progress.acknowledge(L10n.t("Making a video of the photos"), L10n.t("Waiting to save video"));
        return true;
    }

    /** For the tests: stands in for drawing, muxing and publishing the video. */
    interface Maker {
        MediaFileWriter.Saved make(String name) throws IOException;
    }

    static volatile Maker makerForTests;

    /**
     * The video of all of a post's photos, made now on the media worker that's calling, for a run
     * of picked posts (#197). It is the single save without its queue slot, its progress row and
     * its banner: the run has its own.
     */
    static MediaFileWriter.Saved makeForRun(Context app, Object aweme) throws IOException {
        List<List<String>> photos = OriginalPhotos.sources(aweme);
        if (photos.isEmpty()) throw new IOException("The original photos aren't available");
        String name = DownloadFilenameFormatter.formatSelectedVideoName(aweme);
        String path = DownloadFilenameFormatter.destinationPath(aweme, false);
        return make(app, photos, soundSources(aweme), seconds(Settings.PHOTO_VIDEO_SECONDS.get()), name, path,
                SaveProgress.begin(1), new boolean[]{false});
    }

    /** Whether a save of this post's video holds it. */
    static boolean saving(String id) {
        return ACTIVE.contains(id);
    }

    private static boolean failed() {
        Utils.showToastLong(L10n.t("The video couldn't be made from these photos. Try again."));
        return true;
    }

    /**
     * Draws the video, adds the sound when it can and publishes the one file. Nothing reaches the
     * gallery until the MP4 is finished, so a failure anywhere leaves only temporary files, and
     * those go here.
     */
    private static MediaFileWriter.Saved make(Context app, List<List<String>> photos, List<String> sound,
            int seconds, String name, String path, SaveProgress progress, boolean[] silent) throws IOException {
        Maker stand = makerForTests;
        if (stand != null) return stand.make(name);
        List<File> temporary = new ArrayList<>();
        try {
            MediaBudget.checkDiskSpace(app.getCacheDir(), -1L);
            File video = temp(app, temporary, ".mp4");
            drawPhotos(app, photos, seconds, video, progress, temporary);
            File result = video;
            try {
                if (sound.isEmpty()) throw new IOException("The post lists no sound");
                File fetched = temp(app, temporary, ".tmp");
                RemoteMedia.fetch(sound, fetched, RemoteMedia.Kind.AUDIO);
                File track = temp(app, temporary, ".m4a");
                SlideshowEncoder.soundTrack(fetched, track, durationUs(photos.size(), seconds));
                File muxed = temp(app, temporary, ".mp4");
                TrackMuxer.combine(video, track, muxed);
                result = muxed;
            } catch (MediaBudget.StopException stop) {
                throw stop;
            } catch (IOException | RuntimeException noSound) {
                Logger.printException(() -> "The photo video goes out without its sound", noSound);
                silent[0] = true;
            }
            return MediaFileWriter.publishForResult(app, result, name, "video/mp4", path, true);
        } finally {
            for (File file : temporary) {
                if (!MediaCache.delete(file)) Logger.printInfo(() -> "Could not remove a photo video temporary file");
            }
        }
    }

    private static File temp(Context app, List<File> temporary, String suffix) throws IOException {
        File file = MediaCache.createTempFile(app, "photo-video-", suffix);
        temporary.add(file);
        return file;
    }

    /** Cache the sources once so a reclaimed codec can retry without fetching signed URLs again. */
    private static void drawPhotos(Context app, List<List<String>> photos, int seconds, File output,
            SaveProgress progress, List<File> temporary) throws IOException {
        List<File> files = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            MediaBudget.check(null);
            files.add(fetchPhoto(app, photos.get(i), i + 1, temporary));
            progress.transfer(i + 1, photos.size() * 10L);
        }
        Bitmap first = decode(files.get(0), null);
        if (first == null) throw new IOException("The first photo could not be decoded");
        List<int[]> sizes;
        try {
            sizes = frameSizes(first.getWidth(), first.getHeight());
        } finally {
            first.recycle();
        }
        MediaBudget.deadline().allowAtLeast(renderTimeMs(photos.size(), seconds));
        renderWithFallback(sizes, size -> renderPhotos(files, seconds, output, progress, size));
    }

    interface Render { void run(int[] size) throws IOException; }

    static void renderWithFallback(List<int[]> sizes, Render render) throws IOException {
        IOException failure = new IOException("No frame size could be encoded");
        for (int[] size : sizes) {
            MediaBudget.check(null);
            try {
                render.run(size);
                return;
            } catch (MediaBudget.StopException stop) {
                throw stop;
            } catch (IOException | RuntimeException | OutOfMemoryError refused) {
                failure.addSuppressed(refused);
                Logger.printInfo(() -> "Photo video retry after an encoder failure at " + size[0] + "x" + size[1]);
            }
        }
        throw failure;
    }

    static long renderTimeMs(int photos, int seconds) {
        return Math.min(MediaBudget.MOVING_CEILING_MS,
                MediaBudget.JOB_DEADLINE_MS + durationUs(photos, seconds) / 500);
    }

    private static void renderPhotos(List<File> photos, int seconds, File output,
            SaveProgress progress, int[] size) throws IOException {
        int perPhoto = framesPerPhoto(seconds);
        long total = (long) perPhoto * photos.size();
        SlideshowEncoder encoder = null;
        Bitmap frame = null, previous = null, photo = null;
        try {
            encoder = SlideshowEncoder.open(output, Collections.singletonList(size), FPS);
            frame = Bitmap.createBitmap(encoder.width, encoder.height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            SlideshowEncoder.Progress drawn = count -> {
                MediaBudget.moved(null);
                progress.transfer(total + count * 9, total * 10);
            };
            for (int i = 0; i < photos.size(); i++) {
                MediaBudget.check(null);
                photo = decode(photos.get(i), new int[]{encoder.width, encoder.height});
                if (photo == null) throw new IOException("Photo " + (i + 1) + " could not be decoded");
                int transition = previous == null ? 0 : Math.min(SWIPE_FRAMES, perPhoto);
                for (int step = 1; step <= transition; step++) {
                    drawSwipe(canvas, previous, photo, frame.getWidth(), frame.getHeight(), paint,
                            step / (float) transition);
                    encoder.add(frame, 1, drawn);
                }
                if (previous != null) previous.recycle();
                previous = photo;
                photo = null;
                drawSwipe(canvas, null, previous, frame.getWidth(), frame.getHeight(), paint, 1f);
                encoder.add(frame, perPhoto - transition, drawn);
            }
            encoder.finish();
        } finally {
            if (encoder != null) encoder.close();
            if (frame != null) frame.recycle();
            if (previous != null) previous.recycle();
            if (photo != null) photo.recycle();
        }
    }

    static void drawSwipe(Canvas canvas, Bitmap previous, Bitmap next, int width, int height,
            Paint paint, float fraction) {
        float t = Math.max(0f, Math.min(1f, fraction));
        float eased = t * t * (3f - 2f * t);
        int offset = Math.round(width * eased);
        canvas.drawColor(Color.BLACK);
        if (previous != null) drawPhoto(canvas, previous, width, height, paint, -offset);
        drawPhoto(canvas, next, width, height, paint, previous == null ? 0 : width - offset);
    }

    private static void drawPhoto(Canvas canvas, Bitmap photo, int width, int height, Paint paint, int offset) {
        Rect destination = fit(photo.getWidth(), photo.getHeight(), width, height);
        destination.offset(offset, 0);
        int save = canvas.save();
        canvas.clipRect(offset, 0, offset + width, height);
        canvas.drawBitmap(photo, null, destination, paint);
        canvas.restoreToCount(save);
    }

    /**
     * Fetches one photo and decodes it small enough for the frame, trying each copy TikTok lists
     * in turn: a phone that can't read the HEIF copy can still read the WebP one.
     */
    private static File fetchPhoto(Context app, List<String> urls, int position, List<File> temporary) throws IOException {
        IOException failure = new IOException("No copy of photo " + position + " could be drawn");
        for (String url : urls) {
            File file = MediaCache.createTempFile(app, "photo-video-", ".tmp");
            boolean keep = false;
            try {
                RemoteMedia.fetch(Collections.singletonList(url), file, RemoteMedia.Kind.IMAGE);
                Bitmap bitmap = decode(file, null);
                if (bitmap != null) {
                    bitmap.recycle();
                    temporary.add(file);
                    keep = true;
                    return file;
                }
                failure.addSuppressed(new IOException("Photo " + position + " didn't decode from "
                        + RemoteMedia.summarizeUrl(url)));
            } catch (MediaBudget.StopException stop) {
                throw stop;
            } catch (IOException | RuntimeException | OutOfMemoryError skipped) {
                failure.addSuppressed(skipped);
            } finally {
                if (!keep && !MediaCache.delete(file)) Logger.printInfo(() -> "Could not remove a photo video temporary file");
            }
        }
        throw failure;
    }

    /**
     * Decodes {@code file} at a power-of-two fraction of its size that still covers the frame,
     * so a 4,000 pixel photo never sits in memory whole. Null when this phone can't read it.
     */
    static Bitmap decode(File file, int[] frame) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            int[] target = frame != null ? frame : frameSize(bounds.outWidth, bounds.outHeight, LONG_SIDE, SHORT_SIDE);
            Rect fitted = fit(bounds.outWidth, bounds.outHeight, target[0], target[1]);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, fitted.width(), fitted.height());
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) return bitmap;
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, source) -> {
                    int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                    int[] target = frame != null ? frame : frameSize(width, height, LONG_SIDE, SHORT_SIDE);
                    Rect fitted = fit(width, height, target[0], target[1]);
                    decoder.setTargetSize(Math.max(1, fitted.width()), Math.max(1, fitted.height()));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } catch (IOException | RuntimeException refused) {
                Logger.printInfo(() -> "The photo's alternate decoder refused the image");
            }
            Bitmap primary = PhotoToJpeg.primaryImageReader.read(file);
            if (primary != null) {
                int[] target = frame != null ? frame : frameSize(primary.getWidth(), primary.getHeight(), LONG_SIDE, SHORT_SIDE);
                Rect fitted = fit(primary.getWidth(), primary.getHeight(), target[0], target[1]);
                if (primary.getWidth() <= fitted.width() && primary.getHeight() <= fitted.height()) return primary;
                try {
                    return Bitmap.createScaledBitmap(primary, Math.max(1, fitted.width()), Math.max(1, fitted.height()), true);
                } finally {
                    primary.recycle();
                }
            }
        }
        return null;
    }

    /** Where the post's sound can be fetched: its sound entry, then the addresses its video lists. */
    static List<String> soundSources(Object aweme) {
        List<String> sources = new ArrayList<>(OriginalSoundDownloads.sourceUrls(aweme));
        Object video = Reflect.property(aweme, "getVideo", "video");
        if (video != null) {
            for (String url : VideoDownloads.sourceUrls(video)) {
                if (MediaTransport.hasAllowedShape(url) && !sources.contains(url)) sources.add(url);
            }
        }
        return sources;
    }

    /** The setting, held to what the row offers, in case a restored backup carried something else. */
    static int seconds(int setting) {
        return Math.max(1, Math.min(10, setting));
    }

    static int framesPerPhoto(int seconds) {
        return seconds * FPS;
    }

    /** When frame {@code index} shows, counted from zero, in nanoseconds. */
    static long presentationTimeNs(long index, int fps) {
        return index * 1_000_000_000L / fps;
    }

    /** How long the video runs, which is where the sound is cut. */
    static long durationUs(int photos, int seconds) {
        return (long) photos * seconds * 1_000_000L;
    }

    /** Samples, per channel, that fit in {@code limitUs} at {@code sampleRate}. */
    static long trimFrames(long limitUs, int sampleRate) {
        return limitUs * sampleRate / 1_000_000L;
    }

    /**
     * How far one pass of a sound that's played again reaches: its last sample's time plus the
     * gap before it, so the next pass starts a sample later. A pass never counts as shorter than
     * {@link #MIN_LOOP_US}, which keeps a broken file's timestamps from repeating it forever.
     */
    static long loopLengthUs(long lastSampleUs, long sampleGapUs) {
        return Math.max(MIN_LOOP_US, Math.max(0, lastSampleUs) + Math.max(0, sampleGapUs));
    }

    /**
     * How many PCM bytes go into one encoder buffer: what's left of the decoded buffer, what the
     * encoder buffer holds and what's left before the cut, whichever is least, in whole samples.
     */
    static int pcmBytes(int available, int room, long framesLeft, int frameBytes) {
        if (frameBytes <= 0 || framesLeft <= 0) return 0;
        long bytes = Math.min(Math.min(available, room), framesLeft * frameBytes);
        return (int) (bytes - bytes % frameBytes);
    }

    static int bitRate(int width, int height) {
        long proposed = (long) width * height * 2L;
        return (int) Math.max(1_000_000L, Math.min(6_000_000L, proposed));
    }

    static int audioBitRate(int channels) {
        return channels == 1 ? 96_000 : 128_000;
    }

    /** The frame sizes to try, full size first, all shaped like the first photo. */
    static List<int[]> frameSizes(int width, int height) {
        List<int[]> sizes = new ArrayList<>();
        sizes.add(frameSize(width, height, LONG_SIDE, SHORT_SIDE));
        for (int[] sides : FALLBACK_SIDES) {
            int[] size = frameSize(width, height, sides[0], sides[1]);
            int[] last = sizes.get(sizes.size() - 1);
            if (size[0] != last[0] || size[1] != last[1]) sizes.add(size);
        }
        return sizes;
    }

    /**
     * The largest frame inside the long by short box with the photo's shape, both sides a
     * multiple of 16, which every H.264 encoder takes. A shape past 9:16 or 16:9 is held to that,
     * so a panorama doesn't make a sliver of a video; it gets bars instead.
     */
    static int[] frameSize(int width, int height, int longSide, int shortSide) {
        boolean landscape = width > height;
        int boxWidth = landscape ? longSide : shortSide;
        int boxHeight = landscape ? shortSide : longSide;
        double narrowest = (double) shortSide / longSide;
        double aspect = width > 0 && height > 0 ? (double) width / height : narrowest;
        aspect = Math.max(narrowest, Math.min(1 / narrowest, aspect));
        double frameWidth, frameHeight;
        if (aspect >= (double) boxWidth / boxHeight) {
            frameWidth = boxWidth;
            frameHeight = boxWidth / aspect;
        } else {
            frameHeight = boxHeight;
            frameWidth = boxHeight * aspect;
        }
        return new int[]{multipleOf16(frameWidth), multipleOf16(frameHeight)};
    }

    private static int multipleOf16(double side) {
        return Math.max(16, ((int) Math.floor(side + 1e-6)) / 16 * 16);
    }

    /** Where a photo sits in the frame: as large as fits, centered, black bars around the rest. */
    static Rect fit(int width, int height, int frameWidth, int frameHeight) {
        if (width <= 0 || height <= 0) return new Rect(0, 0, frameWidth, frameHeight);
        double scale = Math.min((double) frameWidth / width, (double) frameHeight / height);
        int fittedWidth = Math.max(1, Math.min(frameWidth, (int) Math.round(width * scale)));
        int fittedHeight = Math.max(1, Math.min(frameHeight, (int) Math.round(height * scale)));
        int left = (frameWidth - fittedWidth) / 2;
        int top = (frameHeight - fittedHeight) / 2;
        return new Rect(left, top, left + fittedWidth, top + fittedHeight);
    }

    /** The largest power of two that still leaves the decoded photo at least the fitted size. */
    static int sampleSize(int width, int height, int fittedWidth, int fittedHeight) {
        int sample = 1;
        if (fittedWidth <= 0 || fittedHeight <= 0) return sample;
        while (sample < 64 && width / (sample * 2L) >= fittedWidth && height / (sample * 2L) >= fittedHeight) {
            sample *= 2;
        }
        return sample;
    }
}
