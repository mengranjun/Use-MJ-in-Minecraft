package com.example.videotrigger.video;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.nio.file.Path;

/**
 * Plays a clip's companion WAV through the system audio device (JavaSound).
 * Kept deliberately simple and fail-safe: if the audio device is unavailable the
 * video still plays silently.
 */
public final class VideoAudio {

    private static final Logger LOGGER = LogUtils.getLogger();

    private VideoAudio() {
    }

    /** Opens (and prepares) the WAV for playback; returns {@code null} on failure. */
    public static Clip open(Path wav) {
        if (wav == null) {
            return null;
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
            Clip clip = AudioSystem.getClip();
            clip.open(in);
            return clip;
        } catch (Exception e) {
            LOGGER.warn("[videotrigger] Could not open audio '{}'", wav, e);
            return null;
        }
    }

    /** Starts playback if a clip was opened. */
    public static void start(Clip clip) {
        if (clip == null) {
            return;
        }
        try {
            clip.setFramePosition(0);
            clip.start();
        } catch (Exception e) {
            LOGGER.warn("[videotrigger] Could not start audio", e);
        }
    }

    /** Stops and releases the audio clip (call when the video closes). */
    public static void close(Clip clip) {
        if (clip == null) {
            return;
        }
        try {
            clip.stop();
        } catch (Exception ignored) {
            // best effort
        }
        try {
            clip.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
