package com.example.videotrigger.video;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.file.Path;
import java.util.List;

/**
 * A fully decoded video held in memory: an ordered list of RGBA frames plus
 * playback metadata. Owns the {@link NativeImage}s; {@link #close()} releases them.
 */
public final class LoadedVideo implements AutoCloseable {

    private final List<NativeImage> frames;
    public final int width;
    public final int height;
    public final int frameCount;
    public final double fps;
    public final boolean loop;
    public final String title;
    /** Optional companion WAV holding the clip's audio (may be {@code null}). */
    public final Path audioPath;

    LoadedVideo(List<NativeImage> frames, int width, int height, double fps,
                boolean loop, String title, Path audioPath) {
        this.frames = frames;
        this.width = width;
        this.height = height;
        this.frameCount = frames.size();
        this.fps = fps > 0 ? fps : 20.0;
        this.loop = loop;
        this.title = title;
        this.audioPath = audioPath;
    }

    public NativeImage frame(int index) {
        return frames.get(index);
    }

    /** Milliseconds needed to play this video once, based on its FPS. */
    public long durationMs() {
        return (long) (frameCount / this.fps * 1000.0);
    }

    @Override
    public void close() {
        for (NativeImage ni : frames) {
            ni.close();
        }
        frames.clear();
    }
}
