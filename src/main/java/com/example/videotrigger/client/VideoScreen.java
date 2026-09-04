package com.example.videotrigger.client;

import com.example.videotrigger.VideotriggerMod;
import com.example.videotrigger.video.LoadedVideo;
import com.example.videotrigger.video.VideoPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A full-screen overlay that renders the decoded video frames in real time.
 * The current frame is uploaded to a single {@link DynamicTexture} and drawn
 * centred with letterboxing. Clicking (or Esc) closes the screen.
 *
 * <p>Uses Minecraft 1.26.2's "extract render state" GUI model
 * ({@link GuiGraphicsExtractor}).</p>
 */
public class VideoScreen extends Screen {

    private final LoadedVideo video;
    private final boolean pause;
    private final double scale;
    private final long startTime = System.currentTimeMillis();

    private DynamicTexture texture;
    private Identifier textureLoc;
    private int lastFrame = -1;
    private boolean closed;

    public VideoScreen(LoadedVideo video, boolean pause, double scale) {
        super(Component.literal(video.title != null ? video.title : "Video"));
        this.video = video;
        this.pause = pause;
        this.scale = Math.max(0.05, Math.min(1.0, scale));
    }

    @Override
    public boolean isPauseScreen() {
        return this.pause;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gge, int mouseX, int mouseY, float partialTick) {
        if (closed) {
            return;
        }
        double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        int frameIndex = (int) (elapsedSeconds * video.fps);
        frameIndex = Math.max(0, frameIndex);
        if (frameIndex >= video.frameCount) {
            if (video.loop) {
                frameIndex %= video.frameCount;
            } else {
                this.onClose();
                return;
            }
        }
        // Solid black background (cutscene look).
        gge.fill(0, 0, this.width, this.height, 0xFF000000);

        ensureTexture();
        if (frameIndex != lastFrame) {
            // NOTE: do NOT use DynamicTexture.setPixels() here - it closes the image it
            // replaces, which would destroy our shared frame list. Copy into the texture's
            // own persistent NativeImage instead.
            copyIntoTexture(video.frame(frameIndex));
            texture.upload();
            lastFrame = frameIndex;
        }

        // Fit the video inside the screen, preserving aspect ratio.
        float maxW = this.width * (float) scale;
        float maxH = this.height * (float) scale;
        float s = Math.min(maxW / video.width, maxH / video.height);
        int dw = Math.max(1, (int) (video.width * s));
        int dh = Math.max(1, (int) (video.height * s));
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        // Draw the full texture. The last 4 floats of the simple blit are PIXEL
        // coordinates (u, v, uWidth, vHeight), so use the explicit pixel-based
        // overload with the texture's own dimensions.
        gge.blit(RenderPipelines.GUI_TEXTURED, textureLoc, dx, dy,
                0.0F, 0.0F, video.width, video.height, video.width, video.height);
    }

    private void ensureTexture() {
        if (texture == null) {
            texture = new DynamicTexture("VideoTrigger", video.width, video.height, false);
            textureLoc = Identifier.fromNamespaceAndPath(VideotriggerMod.MODID, "video/frame");
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

    @Override
    public void onClose() {
        if (closed) {
            return;
        }
        closed = true;
        if (textureLoc != null) {
            Minecraft.getInstance().getTextureManager().release(textureLoc);
            textureLoc = null;
        }
        texture = null;
        if (video != null) {
            video.close();
        }
        VideoPlayer.get().finish();
        super.onClose();
    }
}
