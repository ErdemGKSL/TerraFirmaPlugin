/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The {@code /tfp} Brigadier tree, registered by {@link TfpBootstrap}. Built from a supplier because the
 * plugin instance may not be enabled (refused by the compat gate) when a command runs. M1-SVC-05 adds
 * {@code reload data|content} and {@code debug}.
 */
public final class TfpCommands {
    public static final String PERMISSION = "tfp.command";
    public static final String ADMIN_PERMISSION = "tfp.admin";

    /** What the commands call into; implemented by the enabled plugin. */
    public interface Handler {
        /** Multi-line version and status report. */
        String version();

        /** Reloads config.yml off-thread; completes with a summary for the sender. */
        CompletableFuture<String> reloadSettings();
    }

    private TfpCommands() {
    }

    /**
     * @param pluginVersion our version, from the plugin meta (available even when the plugin is disabled)
     * @param handler       the enabled plugin, or {@code null} while it is not enabled
     */
    public static LiteralCommandNode<CommandSourceStack> build(String pluginVersion, Supplier<Handler> handler) {
        Command<CommandSourceStack> version = ctx -> {
            Handler h = handler.get();
            reply(ctx, h != null ? h.version()
                    : "TerraFirmaPlugin " + pluginVersion + " (not enabled; see the startup log)", NamedTextColor.GRAY);
            return Command.SINGLE_SUCCESS;
        };
        return Commands.literal("tfp")
                .requires(s -> s.getSender().hasPermission(PERMISSION))
                .executes(version)
                .then(Commands.literal("version").executes(version))
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("config").executes(ctx -> {
                            Handler h = handler.get();
                            if (h == null) {
                                reply(ctx, "TerraFirmaPlugin is not enabled.", NamedTextColor.RED);
                                return 0;
                            }
                            h.reloadSettings().whenComplete((msg, err) -> reply(ctx,
                                    err == null ? msg : "Reload failed: " + err.getMessage(),
                                    err == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        })))
                .build();
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text, NamedTextColor color) {
        ctx.getSource().getSender().sendMessage(Component.text(text, color));
    }
}
