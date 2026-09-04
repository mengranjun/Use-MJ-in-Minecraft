package com.example.videotrigger.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Seeds a small, guaranteed-to-work sample animation (a folder of PNG frames)
 * so the mod demonstrates playback out of the box. Players replace these files
 * with their own videos.
 */
public final class SampleAssets {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SampleAssets() {
    }

    /**
     * Generates {@code videos/JustAI/frame_000n.png} if the videos folder is empty.
     * The default config already references this folder, so the mod works immediately.
     */
    public static void ensureSamples(Path videoDir) {
        try {
            boolean hasContent = false;
            if (Files.isDirectory(videoDir)) {
                try (var stream = Files.list(videoDir)) {
                    hasContent = stream.anyMatch(p -> {
                        if (Files.isDirectory(p)) return true;
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".gif") || n.endsWith(".png") || n.endsWith(".jpg")
                                || n.endsWith(".jpeg") || n.endsWith(".bmp");
                    });
                }
            }
            if (!hasContent) {
                generateExample(videoDir.resolve("JustAI"));
                LOGGER.info("[videotrigger] Seeded sample animation in {}", videoDir.resolve("JustAI"));
            }
        } catch (IOException e) {
            LOGGER.warn("[videotrigger] Could not seed sample animation", e);
        }
    }

    private static void generateExample(Path dir) throws IOException {
        int w = 160, h = 120, frames = 20;
        Files.createDirectories(dir);
        for (int i = 0; i < frames; i++) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Transparent background: when drawn as a HUD overlay the game shows through.

            int barX = (i * 26) % (w + 24) - 12;
            g.setColor(new Color(245, 140, 40, 255));
            g.fillRect(barX, 0, 12, h);

            g.setColor(new Color(240, 240, 245, 255));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            String text = i % 12 < 6 ? "M J" : "MJ!";
            g.drawString(text, (w - g.getFontMetrics().stringWidth(text)) / 2, h / 2 + 15);
            g.dispose();

            Path file = dir.resolve(String.format(Locale.ROOT, "frame_%04d.png", i));
            ImageIO.write(img, "png", file.toFile());
        }
    }
}
