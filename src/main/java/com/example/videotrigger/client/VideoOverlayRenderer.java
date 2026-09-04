package com.example.videotrigger.client;

import com.example.videotrigger.VideotriggerMod;
import com.example.videotrigger.config.TriggerConfigManager;
import com.example.videotrigger.config.VideoTriggerConfig;
import com.example.videotrigger.video.LoadedVideo;
import com.example.videotrigger.video.VideoAudio;
import com.example.videotrigger.video.VideoPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import javax.sound.sampled.Clip;

/**
 * Draws the currently playing video on top of the game HUD (after vanilla GUI),
 * without pausing the game and without any background, so videos with a
 * transparent background show the gameplay behind them.
 *
 * <p>The video plays once and stops automatically; there is no player-facing
 * stop control so it never interferes with key bindings.</p>
 */
public class VideoOverlayRenderer {

    private static final VideoOverlayRenderer INSTANCE = new VideoOverlayRenderer();

    private LoadedVideo video;
    private boolean active;
    private long startTime;
    private DynamicTexture texture;
    private Identifier textureLoc;
    private int lastFrame = -1;
    private double scale = 1.0;
    private int offsetX = 0;
    private int offsetY = 0;
    private Clip audio;

    private VideoOverlayRenderer() {
    }

    public static VideoOverlayRenderer get() {
        return INSTANCE;
    }

    public boolean isActive() {
        return active;
    }

    /** Begins drawing the given video over the HUD. */
    public void start(LoadedVideo v) {
        this.video = v;
        this.startTime = System.currentTimeMillis();
        this.active = true;
        this.lastFrame = -1;
        // Snapshot the per-play config so the render loop never touches the disk.
        VideoTriggerConfig cfg = TriggerConfigManager.get();
        this.scale = Math.max(0.05, Math.min(1.0, cfg.scale));
        this.offsetX = cfg.overlayOffsetX;
        this.offsetY = cfg.overlayOffsetY;
        // Play the clip's companion WAV alongside the video (fail-safe).
        this.audio = VideoAudio.open(v.audioPath);
        VideoAudio.start(this.audio);
    }

    /** Stops the overlay and releases all resources (called when the video finishes). */
    public void stop() {
        if (textureLoc != null) {
            Minecraft.getInstance().getTextureManager().release(textureLoc);
            textureLoc = null;
        }
        texture = null;
        lastFrame = -1;
        VideoAudio.close(audio);
        audio = null;
        if (video != null) {
            video.close();
        }
        video = null;
        active = false;
        VideoPlayer.get().finish();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        VideoOverlayRenderer r = INSTANCE;
        if (!r.active || r.video == null) {
            return;
        }

        LoadedVideo v = r.video;
        double elapsedSeconds = (System.currentTimeMillis() - r.startTime) / 1000.0;
        int frameIndex = (int) (elapsedSeconds * v.fps);
        frameIndex = Math.max(0, frameIndex);
        if (frameIndex >= v.frameCount) {
            if (v.loop) {
                frameIndex %= v.frameCount;
            } else {
                r.stop();
                return;
            }
        }

        r.ensureTexture();
        if (frameIndex != r.lastFrame) {
            r.copyIntoTexture(v.frame(frameIndex));
            r.texture.upload();
            r.lastFrame = frameIndex;
        }

        GuiGraphicsExtractor gge = event.getGuiGraphics();
        int screenW = gge.guiWidth();
        int screenH = gge.guiHeight();
        // "scale" means "how much of the screen the video may fill", same as the
        // full-screen mode, so the two render paths behave consistently.
        float maxW = screenW * (float) r.scale;
        float maxH = screenH * (float) r.scale;
        float s = Math.min(maxW / v.width, maxH / v.height);
        int dw = Math.max(1, (int) (v.width * s));
        int dh = Math.max(1, (int) (v.height * s));
        int dx = (screenW - dw) / 2 + r.offsetX;
        int dy = (screenH - dh) / 2 + r.offsetY;

        // Draw on top of the HUD with alpha blending: transparent parts of the
        // frame show the game through.
        gge.blit(RenderPipelines.GUI_TEXTURED, r.textureLoc, dx, dy,
                0.0F, 0.0F, dw, dh, v.width, v.height, v.width, v.height);
    }

    private void ensureTexture() {
        if (texture == null) {
            texture = new DynamicTexture("VideoTrigger", video.width, video.height, false);
            textureLoc = Identifier.fromNamespaceAndPath(VideotriggerMod.MODID, "overlay/frame");
            Minecraft.getInstance().getTextureManager().register(textureLoc, texture);
        }
    }

    /** Copies the given frame into the texture's own pixel buffer (owned by the texture). */
    private void copyIntoTexture(NativeImage frame) {
        NativeImage dst = texture.getPixels();
        int w = Math.min(dst.getWidth(), frame.getWidth());
        int h = Math.min(dst.getHeight(), frame.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dst.setPixel(x, y, frame.getPixel(x, y));
            }
        }
    }
}
