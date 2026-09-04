package com.example.videotrigger.config;

import com.example.videotrigger.VideotriggerMod;
import com.example.videotrigger.client.SampleAssets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Loads, caches and hot-reloads {@link VideoTriggerConfig} from the config folder.
 * The config is re-read whenever its file modification time changes, so players can
 * tweak triggers and videos in a text editor without restarting the game.
 */
public final class TriggerConfigManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Root folder for this mod's configurable content. */
    public static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("videotrigger");
    /** The JSON file that holds triggers and their videos. */
    public static final Path CONFIG_FILE = CONFIG_DIR.resolve("triggers.json");
    /** Folder where players drop video files (gifs / frame folders). */
    public static final Path VIDEO_DIR = CONFIG_DIR.resolve("videos");
    /** Classpath root that holds the videos bundled inside the mod jar. */
    private static final String BUNDLED_VIDEOS_ROOT = "/assets/videotrigger/videos/";
    /**
     * Marker written the first time the bundled videos/sample are installed. After
     * that the player owns the folder, so files they delete stay deleted instead of
     * being re-copied from the jar on every launch.
     */
    private static final Path INSTALL_MARKER = CONFIG_DIR.resolve(".bundled_installed");

    private static VideoTriggerConfig cached;
    private static long lastModified = -1L;
    /** Prevents the config/example seeding from touching the disk more than once. */
    private static volatile boolean defaultsEnsured = false;
    /** Avoids re-stat-ing the config file on every call (e.g. every render frame). */
    private static volatile long lastStatCheck = 0L;
    private static final long STAT_THROTTLE_MS = 1000L;

    private TriggerConfigManager() {
    }

    /** Returns the current config, re-reading from disk if it changed. */
    public static synchronized VideoTriggerConfig get() {
        ensureDefaults();
        long now = System.currentTimeMillis();
        if (cached != null && now - lastStatCheck < STAT_THROTTLE_MS) {
            return cached;
        }
        lastStatCheck = now;
        try {
            long mtime = Files.exists(CONFIG_FILE) ? Files.getLastModifiedTime(CONFIG_FILE).toMillis() : -1L;
            if (cached == null || mtime != lastModified) {
                if (Files.exists(CONFIG_FILE)) {
                    try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                        cached = GSON.fromJson(reader, VideoTriggerConfig.class);
                    } catch (JsonParseException | IOException e) {
                        LOGGER.error("[videotrigger] Failed to parse {} - using defaults", CONFIG_FILE, e);
                        cached = defaultConfig();
                    }
                } else {
                    cached = defaultConfig();
                    write(cached, CONFIG_FILE);
                }
                lastModified = mtime;
            }
        } catch (IOException e) {
            LOGGER.error("[videotrigger] Failed to stat config file", e);
            if (cached == null) cached = defaultConfig();
        }
        if (cached == null) cached = defaultConfig();
        return cached;
    }

    /**
     * Finds the first trigger entry whose phrase matches the given chat message.
     * Matching ignores surrounding whitespace and (by default) case.
     */
    public static VideoTriggerConfig.Entry findEntry(String message) {
        VideoTriggerConfig cfg = get();
        if (message == null) return null;
        String msg = message.trim();
        boolean cs = cfg.caseSensitive;
        for (VideoTriggerConfig.Entry e : cfg.triggers) {
            if (e == null || e.trigger == null) continue;
            String trigger = e.trigger.trim();
            if (trigger.isEmpty()) continue;
            String a = cs ? trigger : trigger.toLowerCase(Locale.ROOT);
            String b = cs ? msg : msg.toLowerCase(Locale.ROOT);
            if (a.equals(b)) return e;
        }
        return null;
    }

    /** Forces the config to be re-read from disk (used by the reload command). */
    public static synchronized void reload() {
        lastModified = -1L;
        lastStatCheck = 0L;
        cached = null;
        get();
    }

    /** Saves the given config to disk and invalidates the cache. */
    public static synchronized void saveConfig(VideoTriggerConfig cfg) {
        write(cfg, CONFIG_FILE);
        lastModified = -1L;
        lastStatCheck = 0L;
        cached = cfg;
    }

    /**
     * Adds a video clip to the trigger's list (creating the trigger if needed)
     * and persists the change. Used by the in-game {@code /videotrigger add} command.
     */
    public static synchronized void addClip(String trigger, String type, String path) {
        if (trigger == null || trigger.trim().isEmpty()) return;
        VideoTriggerConfig cfg = get();
        VideoTriggerConfig.Entry entry = null;
        String t = trigger.trim();
        for (VideoTriggerConfig.Entry e : cfg.triggers) {
            if (t.equalsIgnoreCase(e.trigger)) { entry = e; break; }
        }
        if (entry == null) {
            entry = new VideoTriggerConfig.Entry();
            entry.trigger = t;
            cfg.triggers.add(entry);
        }
        VideoTriggerConfig.Clip clip = new VideoTriggerConfig.Clip();
        clip.type = (type == null || type.trim().isEmpty()) ? "frames" : type.trim();
        clip.path = path == null ? "" : path.trim();
        entry.videos.add(clip);
        saveConfig(cfg);
    }

    private static void ensureDefaults() {
        if (defaultsEnsured) {
            return;
        }
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.createDirectories(VIDEO_DIR);
        } catch (IOException e) {
            LOGGER.error("[videotrigger] Could not create config directories", e);
        }
        // Install the videos the mod ships with (from the jar) into the config folder
        // ONLY ONCE. After the first run, the player owns the folder: files they delete
        // must not be re-created from the jar on later launches.
        if (!Files.exists(INSTALL_MARKER)) {
            installBundledVideos();
            SampleAssets.ensureSamples(VIDEO_DIR);
            try {
                Files.createDirectories(CONFIG_DIR);
                Files.writeString(INSTALL_MARKER, "installed-once", StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("[videotrigger] Could not write install marker", e);
            }
        }
        defaultsEnsured = true;
    }

    /**
     * Copies the mod's bundled video files out of the jar into the config folder,
     * only when the destination does not already exist (so they can be edited later).
     * Reads the list of files from {@code assets/videotrigger/videos/manifest.txt}.
     */
    private static void installBundledVideos() {
        try (InputStream manifest = VideotriggerMod.class.getResourceAsStream(BUNDLED_VIDEOS_ROOT + "manifest.txt")) {
            if (manifest == null) {
                return; // no bundled videos in this build
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(manifest, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String relPath = line.trim().replace('\\', '/');
                    if (relPath.isEmpty() || relPath.startsWith("#")) {
                        continue;
                    }
                    Path target = VIDEO_DIR.resolve(relPath).normalize();
                    // Keep bundled installs strictly inside our videos folder.
                    if (!target.startsWith(VIDEO_DIR)) {
                        continue;
                    }
                    if (Files.exists(target)) {
                        continue;
                    }
                    try (InputStream in = VideotriggerMod.class
                            .getResourceAsStream(BUNDLED_VIDEOS_ROOT + relPath)) {
                        if (in == null) {
                            continue;
                        }
                        if (target.getParent() != null) {
                            Files.createDirectories(target.getParent());
                        }
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("[videotrigger] Failed to install bundled videos", e);
        }
    }

    private static VideoTriggerConfig defaultConfig() {
        VideoTriggerConfig cfg = new VideoTriggerConfig();
        // A demonstrator entry so the mod does something out of the box.
        VideoTriggerConfig.Entry entry = new VideoTriggerConfig.Entry();
        entry.trigger = "MJ";
        VideoTriggerConfig.Clip clip = new VideoTriggerConfig.Clip();
        clip.type = "mp4";
        clip.path = "videos/example/MJHP(1).mp4";
        clip.title = "MJ";
        entry.videos.add(clip);
        cfg.triggers.add(entry);
        cfg.defaultFps = 20.0;
        return cfg;
    }

    private static void write(VideoTriggerConfig cfg, Path file) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, writer);
        } catch (IOException e) {
            LOGGER.error("[videotrigger] Could not write default config to {}", file, e);
        }
    }
}
