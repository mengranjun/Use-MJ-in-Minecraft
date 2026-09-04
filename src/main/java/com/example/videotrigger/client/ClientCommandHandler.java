package com.example.videotrigger.client;

import com.example.videotrigger.config.TriggerConfigManager;
import com.example.videotrigger.config.VideoTriggerConfig;
import com.example.videotrigger.video.VideoPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Registers the client-only {@code /videotrigger} command so players can manage
 * triggers and videos in-game.
 *
 * <ul>
 *   <li>{@code /videotrigger reload} — re-read config from disk.</li>
 *   <li>{@code /videotrigger list} — list triggers and video counts.</li>
 *   <li>{@code /videotrigger test <trigger>} — play a trigger's random video.</li>
 *   <li>{@code /videotrigger add <trigger> <type> <path>} — add a video clip.</li>
 * </ul>
 */
public final class ClientCommandHandler {

    private ClientCommandHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("videotrigger")

                .then(Commands.literal("reload").executes(ctx -> {
                    TriggerConfigManager.reload();
                    ctx.getSource().sendSystemMessage(Component.literal("[VideoTrigger] Config reloaded."));
                    return 1;
                }))

                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSystemMessage(Component.literal(buildSummary()));
                    return 1;
                }))

                .then(Commands.literal("test").then(Commands.argument("trigger", StringArgumentType.string())
                        .executes(ctx -> {
                            String trigger = StringArgumentType.getString(ctx, "trigger");
                            VideoTriggerConfig.Entry entry = TriggerConfigManager.findEntry(trigger);
                            if (entry != null) {
                                VideoPlayer.get().play(entry);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("[VideoTrigger] No trigger named '" + trigger + "'."));
                            }
                            return 1;
                        })))

                .then(Commands.literal("add")
                        .then(Commands.argument("trigger", StringArgumentType.string())
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .then(Commands.argument("path", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    String trigger = StringArgumentType.getString(ctx, "trigger");
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    String path = StringArgumentType.getString(ctx, "path");
                                                    TriggerConfigManager.addClip(trigger, type, path);
                                                    ctx.getSource().sendSystemMessage(Component.literal(
                                                            "[VideoTrigger] Added '" + type + "' clip '" + path + "' for trigger '" + trigger + "'."));
                                                    return 1;
                                                }))))));
    }

    private static String buildSummary() {
        VideoTriggerConfig cfg = TriggerConfigManager.get();
        if (cfg.triggers.isEmpty()) {
            return "[VideoTrigger] No triggers configured.";
        }
        StringBuilder sb = new StringBuilder("[VideoTrigger] Triggers:");
        for (VideoTriggerConfig.Entry e : cfg.triggers) {
            int n = e.videos == null ? 0 : e.videos.size();
            sb.append(" ").append(e.trigger).append("(").append(n).append("videos)");
        }
        return sb.toString();
    }
}
