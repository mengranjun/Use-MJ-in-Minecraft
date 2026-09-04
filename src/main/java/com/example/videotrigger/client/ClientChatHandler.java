package com.example.videotrigger.client;

import com.example.videotrigger.config.TriggerConfigManager;
import com.example.videotrigger.config.VideoTriggerConfig;
import com.example.videotrigger.video.VideoPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;

/**
 * Listens for the chat text the player submits and, when it matches a configured
 * trigger, plays a random associated video.
 */
public class ClientChatHandler {

    private ClientChatHandler() {
    }

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }
        VideoTriggerConfig.Entry entry = TriggerConfigManager.findEntry(message);
        if (entry == null) {
            return;
        }
        // Optionally prevent the trigger phrase from being broadcast/echoed.
        if (TriggerConfigManager.get().consumeTriggerMessage) {
            event.setCanceled(true);
        }
        VideoPlayer.get().play(entry);
    }
}
