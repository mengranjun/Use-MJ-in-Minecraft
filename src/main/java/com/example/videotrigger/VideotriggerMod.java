package com.example.videotrigger;

import com.example.videotrigger.client.ClientChatHandler;
import com.example.videotrigger.client.ClientCommandHandler;
import com.example.videotrigger.client.VideoOverlayRenderer;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Chat Video Trigger — a pure client-side NeoForge mod.
 *
 * <p>When the player types a custom trigger phrase in chat, a random video from
 * that trigger's list is played full screen. Triggers and videos are configured
 * in {@code config/videotrigger/triggers.json} (see {@code VideoTriggerConfig}).</p>
 */
@Mod(value = VideotriggerMod.MODID, dist = Dist.CLIENT)
public class VideotriggerMod {

    public static final String MODID = "videotrigger";

    private static final Logger LOGGER = LogUtils.getLogger();

    public VideotriggerMod(IEventBus modEventBus) {
        LOGGER.info("[videotrigger] Initialized");
        NeoForge.EVENT_BUS.register(ClientChatHandler.class);
        NeoForge.EVENT_BUS.register(ClientCommandHandler.class);
        NeoForge.EVENT_BUS.register(VideoOverlayRenderer.class);
    }
}
