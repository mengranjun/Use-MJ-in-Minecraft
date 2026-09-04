package com.example.videotrigger.video;

import com.example.videotrigger.client.VideoOverlayRenderer;
import com.example.videotrigger.client.VideoScreen;
import com.example.videotrigger.config.TriggerConfigManager;
import com.example.videotrigger.config.VideoTriggerConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side controller that, given a fired trigger, picks one of its videos at
 * random, decodes it off-thread, and opens the {@link VideoScreen} on the render
 * thread when it is ready.
 */
public final class VideoPlayer {

    private static final VideoPlayer INSTANCE = new VideoPlayer();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean playing = new AtomicBoolean(false);

    private VideoPlayer() {
    }

    public static VideoPlayer get() {
        return INSTANCE;
    }

    public boolean isBusy() {
        return loading.get() || playing.get();
    }

    /**
     * Fires playback for a trigger. One candidate clip is chosen uniformly at
     * random, mirroring the desired "one of N videos with equal probability".
     */
    public void play(VideoTriggerConfig.Entry entry) {
        if (loading.get() || playing.get()) {
            return; // never interrupt a video that is already showing
        }
        if (entry == null || entry.videos == null || entry.videos.isEmpty()) {
            return;
        }
        VideoTriggerConfig.Clip clip = entry.videos.get(ThreadLocalRandom.current().nextInt(entry.videos.size()));
        VideoTriggerConfig cfg = TriggerConfigManager.get();
        double baseFps = cfg.defaultFps;
        boolean pause = cfg.pauseGame;
        double scale = cfg.scale;
        boolean hudOverlay = cfg.hudOverlay;

        loading.set(true);
        Thread worker = new Thread(() -> {
            LoadedVideo video;
            try {
                video = VideoLoader.load(clip, baseFps, false);
            } catch (Throwable t) {
                LOGGER.error("[videotrigger] Unexpected error while decoding video", t);
                loading.set(false);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        String name = clip.path != null ? clip.path : clip.type;
                        mc.player.sendSystemMessage(
                                Component.literal("[VideoTrigger] Could not load video: " + name));
                    }
                });
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                loading.set(false);
                if (video == null) {
                    if (mc.player != null) {
                        String name = clip.path != null ? clip.path : clip.type;
                        mc.player.sendSystemMessage(
                                Component.literal("[VideoTrigger] Could not load video: " + name));
                    }
                    return;
                }
                playing.set(true);
                if (hudOverlay) {
                    // Draw on top of the HUD: no pause, transparent background supported.
                    VideoOverlayRenderer.get().start(video);
                } else {
                    mc.setScreenAndShow(new VideoScreen(video, pause, scale));
                }
            });
        }, "videotrigger-loader");
        worker.setDaemon(true);
        worker.start();
    }

    /** Called when playback ends (overlay stop or screen close). */
    public void finish() {
        playing.set(false);
    }
}
