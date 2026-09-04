package com.example.videotrigger.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration document, loaded from
 * {@code config/videotrigger/triggers.json}.
 *
 * <p>Example:</p>
 * <pre>{@code
 * {
 *   "consume_trigger_message": true,
 *   "pause_game": true,
 *   "scale": 1.0,
 *   "case_sensitive": false,
 *   "triggers": [
 *     { "trigger": "MJ", "videos": [
 *         { "type": "gif",    "path": "videos/anim1.gif", "loop": true },
 *         { "type": "frames", "path": "videos/anim2",     "fps": 30, "loop": true }
 *     ]}
 *   ]
 * }
 * }</pre>
 */
public class VideoTriggerConfig {

    /**
     * If true, the matched chat message is not sent/broadcast.
     * Default false: the trigger phrase is still sent normally, and the video plays.
     */
    @SerializedName("consume_trigger_message")
    public boolean consumeTriggerMessage = false;

    /** If true, opening the video screen pauses the game (singleplayer). */
    @SerializedName("pause_game")
    public boolean pauseGame = true;

    /** Multiplier applied to the video size on screen (1.0 = fit screen). */
    @SerializedName("scale")
    public double scale = 1.0;

    /**
     * If true, the video is drawn on top of the game HUD (no pause, no background -
     * transparent videos show the game behind). If false, a full-screen cutscene
     * screen with a black background is used instead.
     */
    @SerializedName("hud_overlay")
    public boolean hudOverlay = true;

    /** Horizontal pixel offset from the screen centre when {@code hud_overlay} is on. */
    @SerializedName("overlay_offset_x")
    public int overlayOffsetX = 0;

    /** Vertical pixel offset from the screen centre when {@code hud_overlay} is on. */
    @SerializedName("overlay_offset_y")
    public int overlayOffsetY = 0;

    /** If true, trigger matching is case-sensitive. */
    @SerializedName("case_sensitive")
    public boolean caseSensitive = false;

    /** Global default frames-per-second used when a clip does not specify one. */
    @SerializedName("default_fps")
    public double defaultFps = 20.0;

    /** List of trigger entries. */
    @SerializedName("triggers")
    public List<Entry> triggers = new ArrayList<>();

    /** A single trigger phrase and the videos it may play. */
    public static class Entry {
        /** The exact chat text that triggers playback (case-insensitive by default). */
        @SerializedName("trigger")
        public String trigger = "";

        /** Candidate videos; one is chosen at random when the trigger fires. */
        @SerializedName("videos")
        public List<Clip> videos = new ArrayList<>();
    }

    /** A single playable video source. */
    public static class Clip {
        /** Either {@code "gif"} (a single animated .gif) or {@code "frames"} (a folder of .png/.jpg frames). */
        @SerializedName("type")
        public String type = "frames";

        /** Path relative to {@code config/videotrigger/}. */
        @SerializedName("path")
        public String path = "";

        /** Optional per-clip FPS override. */
        @SerializedName("fps")
        public Double fps = null;

        /** Optional per-clip loop override. */
        @SerializedName("loop")
        public Boolean loop = null;

        /** Optional human-readable title shown on the screen. */
        @SerializedName("title")
        public String title = null;
    }
}
