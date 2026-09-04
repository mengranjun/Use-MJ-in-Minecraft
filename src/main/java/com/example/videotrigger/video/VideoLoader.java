package com.example.videotrigger.video;

import com.example.videotrigger.config.VideoTriggerConfig;
import com.example.videotrigger.config.TriggerConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Decodes a {@link VideoTriggerConfig.Clip} into a {@link LoadedVideo}.
 *
 * <p>Supported sources:</p>
 * <ul>
 *   <li>{@code "gif"} — a single animated GIF (decoded with Java2D).</li>
 *   <li>{@code "frames"} — a folder of .png/.jpg/.bmp images, sorted by file name.</li>
 *   <li>{@code "mp4"} — an MP4/H.264 file (decoded with JCodec).</li>
 * </ul>
 */
public final class VideoLoader {

    /** Structural cap on decoded frame count as a safety net. */
    private static final int MAX_FRAMES = 4000;
    /** Hard cap on total decoded frame memory (RGBA = 4 bytes per pixel). */
    private static final long MAX_FRAME_MEMORY = 256L * 1024 * 1024;

    private static final Logger LOGGER = LogUtils.getLogger();

    private VideoLoader() {
    }

    /**
     * Loads the video described by {@code clip}. Returns {@code null} on failure
     * (the caller should report the error to the player).
     */
    public static LoadedVideo load(VideoTriggerConfig.Clip clip, double defaultFps, boolean defaultLoop) {
        // Clip paths are documented as relative to config/videotrigger/ (e.g. "videos/anim.gif").
        Path base = TriggerConfigManager.CONFIG_DIR;
        String type = clip.type == null ? "frames" : clip.type.toLowerCase(Locale.ROOT);
        String rel = clip.path == null ? "" : clip.path.trim();
        Path res;
        try {
            res = resolveWithin(base, rel);
        } catch (IOException e) {
            LOGGER.error("[videotrigger] Invalid clip path '{}': {}", rel, e.getMessage());
            return null;
        }
        double fps = clip.fps != null ? clip.fps : defaultFps;
        boolean loop = clip.loop != null ? clip.loop : defaultLoop;
        String title = clip.title != null ? clip.title : rel;

        if (type.equals("gif")) {
            try {
                return loadGif(res, fps, loop, title);
            } catch (IOException e) {
                LOGGER.error("[videotrigger] Failed to load GIF '{}'", res, e);
                return null;
            }
        }
        if (type.equals("mp4")) {
            try {
                return loadMp4(res, fps, loop, title);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[videotrigger] Failed to load MP4 '{}'", res, e);
                return null;
            }
        }
        try {
            return loadFrames(res, fps, loop, title);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[videotrigger] Failed to load frames from '{}'", res, e);
            return null;
        }
    }

    /**
     * Decodes an MP4/H.264 file with JCodec. Frames are pushed into memory until
     * EOF or {@link #MAX_FRAMES}, whichever comes first. Any decode failure is
     * wrapped in an {@link IOException}.
     */
    private static LoadedVideo loadMp4(Path mp4, double fps, boolean loop, String title) throws IOException {
        if (!Files.isRegularFile(mp4)) {
            throw new IOException("MP4 file not found: " + mp4);
        }
        FileChannelWrapper channel = NIOUtils.readableChannel(mp4.toFile());
        List<NativeImage> frames = new ArrayList<>();
        int w = 0, h = 0;
        long totalBytes = 0;
        try {
            FrameGrab grab = FrameGrab.createFrameGrab(channel);
            Picture pic;
            while ((pic = grab.getNativeFrame()) != null) {
                int fw = pic.getWidth();
                int fh = pic.getHeight();
                if (w == 0) {
                    w = fw;
                    h = fh;
                }
                if (frames.size() >= MAX_FRAMES || overBudget(totalBytes, fw, fh)) {
                    LOGGER.warn("[videotrigger] MP4 '{}' hit memory/frame limit - truncated", mp4);
                    break;
                }
                NativeImage ni = toNativeImage(AWTUtil.toBufferedImage(pic));
                frames.add(ni);
                totalBytes += (long) fw * fh * 4;
            }
            if (frames.isEmpty()) {
                throw new IOException("MP4 produced no decodable frames: " + mp4);
            }
            return new LoadedVideo(frames, w, h, fps, loop, title);
        } catch (IOException e) {
            closeFrames(frames);
            throw e;
        } catch (Exception e) {
            closeFrames(frames);
            throw new IOException("MP4 decode failed: " + mp4, e);
        } finally {
            try { channel.close(); } catch (IOException ignored) { }
        }
    }

    private static LoadedVideo loadFrames(Path dir, double fps, boolean loop, String title) throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                            || n.endsWith(".bmp") || n.endsWith(".tga")) {
                        files.add(p);
                    }
                });
            }
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        }
        if (files.isEmpty()) {
            throw new IOException("No image frames found in " + dir);
        }

        List<NativeImage> frames = new ArrayList<>();
        int w = 0, h = 0;
        long totalBytes = 0;
        try {
            for (Path f : files) {
                NativeImage ni;
                try (InputStream in = Files.newInputStream(f)) {
                    ni = NativeImage.read(in);
                }
                int fw = ni.getWidth();
                int fh = ni.getHeight();
                if (frames.size() >= MAX_FRAMES || overBudget(totalBytes, fw, fh)) {
                    ni.close();
                    LOGGER.warn("[videotrigger] Frames folder '{}' hit memory/frame limit - truncated", dir);
                    break;
                }
                if (w == 0) {
                    w = fw;
                    h = fh;
                }
                frames.add(ni);
                totalBytes += (long) fw * fh * 4;
            }
        } catch (IOException | RuntimeException e) {
            closeFrames(frames);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Frames decode failed: " + dir, e);
        }
        if (frames.isEmpty()) {
            throw new IOException("No decodable image frames in " + dir);
        }
        return new LoadedVideo(frames, w, h, fps, loop, title);
    }

    private static LoadedVideo loadGif(Path gif, double fps, boolean loop, String title) throws IOException {
        if (!Files.isRegularFile(gif)) {
            throw new IOException("GIF file not found: " + gif);
        }
        Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
        if (!it.hasNext()) {
            throw new IOException("No GIF image reader available");
        }
        ImageReader reader = it.next();
        List<NativeImage> frames = new ArrayList<>();
        int w = 0, h = 0;
        long totalBytes = 0;
        try (ImageInputStream iis = ImageIO.createImageInputStream(gif.toFile())) {
            reader.setInput(iis, false, true);
            int n = reader.getNumImages(true);
            if (n <= 0) {
                throw new IOException("GIF contains no frames: " + gif);
            }
            for (int i = 0; i < n; i++) {
                BufferedImage bi = reader.read(i);
                int fw = bi.getWidth();
                int fh = bi.getHeight();
                if (frames.size() >= MAX_FRAMES || overBudget(totalBytes, fw, fh)) {
                    LOGGER.warn("[videotrigger] GIF '{}' hit memory/frame limit - truncated", gif);
                    break;
                }
                if (w == 0) {
                    w = fw;
                    h = fh;
                }
                NativeImage ni = toNativeImage(bi);
                frames.add(ni);
                totalBytes += (long) fw * fh * 4;
            }
        } catch (IOException e) {
            closeFrames(frames);
            throw e;
        } catch (RuntimeException e) {
            closeFrames(frames);
            throw new IOException("GIF decode failed: " + gif, e);
        } finally {
            reader.dispose();
        }
        if (frames.isEmpty()) {
            throw new IOException("GIF produced no decodable frames: " + gif);
        }
        return new LoadedVideo(frames, w, h, fps, loop, title);
    }

    /** Converts a Java2D frame into a Minecraft {@link NativeImage}. */
    private static NativeImage toNativeImage(BufferedImage bi) throws IOException {
        int w = bi.getWidth();
        int h = bi.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IOException("Frame has invalid dimensions " + w + "x" + h);
        }
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int a = (argb >>> 24) & 0xFF;
                // Minecraft's NativeImage stores pixels as ABGR-in-int (RGBA in memory
                // on little-endian), matching GL_RGBA upload. Java2D getRGB() returns
                // ARGB, so repack to match the native order.
                ni.setPixel(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return ni;
    }

    /** Releases every frame already decoded into {@code frames} (used on failure). */
    private static void closeFrames(List<NativeImage> frames) {
        for (NativeImage ni : frames) {
            ni.close();
        }
        frames.clear();
    }

    /** True if adding a {@code w x h} frame would exceed the total memory budget. */
    private static boolean overBudget(long totalBytes, int w, int h) {
        return totalBytes + (long) w * h * 4 > MAX_FRAME_MEMORY;
    }

    /**
     * Resolves a clip path against {@code base}, rejecting empty, absolute and
     * ".."-escaping paths so a config can never read files outside the config dir.
     */
    private static Path resolveWithin(Path base, String rel) throws IOException {
        if (rel == null || rel.trim().isEmpty()) {
            throw new IOException("Empty video path");
        }
        Path resolved = base.resolve(rel).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("video path escapes the config directory: " + rel);
        }
        return resolved;
    }
}
