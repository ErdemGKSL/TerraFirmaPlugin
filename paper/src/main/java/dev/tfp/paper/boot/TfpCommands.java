/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.tfp.core.calendar.CalendarFormat;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.showcase.Showcase;
import dev.tfp.paper.i18n.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The {@code /tfp} Brigadier tree, registered by {@link TfpBootstrap} (M1-SVC-05). Built from a supplier
 * because the plugin instance may not be enabled (refused by the compat gate) when a command runs.
 *
 * <pre>
 * /tfp                         version (tfp.command)
 * /tfp version
 * /tfp reload                  = reload data (tfp.admin)
 * /tfp reload config|data|content
 * /tfp debug block|chunk [x z] the block you look at / your chunk and its climate (players, tfp.admin); with x z
 *                              (block coordinates, default overworld) the reading at that position, from the console too
 * /tfp debug snowtime &lt;x&gt; &lt;z&gt; &lt;tick&gt;  sets the chunk's last snow-update calendar tick (tfp.admin, console too; tick &gt;= 0)
 * /tfp debug worldgen &lt;n&gt;      times n chunks of the TFC fill path in throwaway chunk data (tfp.admin, console too)
 * /tfp debug player [name]     player data (yours without a name)
 * /tfp debug packets           packet handler metrics
 * /tfp debug showcase &lt;family&gt; every variant of rocks|soils|ores in a grid at your position (players)
 * /tfp time set day|noon|night|midnight|hour &lt;0-24&gt;   next time of day (tfp.admin, TFC TimeCommand)
 * /tfp time add years|months|days|hours &lt;n&gt; | ticks &lt;n&gt;   skip forward (tfp.admin)
 * </pre>
 *
 * <p>Replies are translated with {@link Messages} into the sender's locale. Handlers answer with futures;
 * the reply is sent from whatever thread completes them ({@code sendMessage} is thread-safe).
 */
public final class TfpCommands {
    public static final String PERMISSION = "tfp.command";
    public static final String ADMIN_PERMISSION = "tfp.admin";
    /** The most chunks one {@code /tfp debug worldgen} times (they run on the command's thread). */
    public static final int MAX_WORLDGEN_CHUNKS = 2000;

    /** A translated reply: a message key with arguments, plus untranslated technical detail lines. */
    public record Reply(boolean ok, String key, List<Object> args, List<String> details) {
        public Reply {
            args = List.copyOf(args);
            details = List.copyOf(details);
        }

        public static Reply ok(String key, Object... args) {
            return new Reply(true, key, Arrays.asList(args), List.of());
        }

        public static Reply error(String key, Object... args) {
            return new Reply(false, key, Arrays.asList(args), List.of());
        }

        public Reply withDetails(List<String> lines) {
            return new Reply(ok, key, args, lines);
        }
    }

    /** What the commands call into; implemented by the enabled plugin. */
    public interface Handler {
        /** Multi-line version and status report (names and versions; not translated). */
        String version();

        /** Reloads config.yml off-thread. */
        CompletableFuture<Reply> reloadSettings();

        /** Reloads our registries off-thread and swaps them in. */
        CompletableFuture<Reply> reloadData();

        /** Re-installs content into the client plugins and reloads them. */
        CompletableFuture<Reply> reloadContent();

        /** The block {@code player} looks at: vanilla state, custom state, stored data. */
        CompletableFuture<Reply> debugBlock(Player player);

        /** {@code player}'s chunk: our chunk-scale data entries, then the climate at the player (M2-CLIM-03). */
        CompletableFuture<Reply> debugChunk(Player player);

        /** The chunk and climate at block {@code x, z} of the default overworld (M2-CLIM-03), for the console too. */
        CompletableFuture<Reply> debugChunkAt(int x, int z);

        /**
         * Writes {@code tick} as the last snow-update calendar tick of the chunk at block {@code x, z} of the default
         * overworld (the chunk's {@code ChunkSnowTime}), on the chunk's region. {@code tick} is not negative.
         */
        CompletableFuture<Reply> debugSnowTime(int x, int z, long tick);

        /**
         * M2-GEN-15: generates {@code count} chunks of the default overworld through the TFC fill path into throwaway chunk
         * data (never loaded into the world) and replies with the count, mean, p95, min and max milliseconds per chunk.
         */
        CompletableFuture<Reply> debugWorldgen(int count);

        /** Player data of the online player {@code name}. */
        CompletableFuture<Reply> debugPlayer(String name);

        /** Test aid: sets the thirst of the online player {@code name} (0..100). */
        CompletableFuture<Reply> debugSetThirst(String name, float value);

        /** Packet layer and per-handler metrics. */
        CompletableFuture<Reply> debugPackets();

        /** Places every loaded variant of {@code family} ({@code rocks|soils|ores}) in a grid next to {@code player}. */
        CompletableFuture<Reply> debugShowcase(Player player, String family);

        /**
         * {@code /tfp time}: moves the calendar forward by {@code delta}'s ticks, on the calendar's thread. The reply
         * is {@code key} with the label, the ticks moved and the new date.
         */
        CompletableFuture<Reply> skipTime(String key, String label, TimeCommand.Delta delta);
    }

    private TfpCommands() {
    }

    /**
     * @param pluginVersion our version, from the plugin meta (available even when the plugin is disabled)
     * @param handler       the enabled plugin, or {@code null} while it is not enabled
     * @param messages      server-side translations
     */
    public static LiteralCommandNode<CommandSourceStack> build(String pluginVersion, Supplier<Handler> handler,
            Messages messages) {
        Command<CommandSourceStack> version = ctx -> {
            Handler h = handler.get();
            CommandSender sender = ctx.getSource().getSender();
            sender.sendMessage(Component.text(h != null ? h.version()
                    : messages.get(sender, "command.version.not_enabled", pluginVersion), NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        };
        Command<CommandSourceStack> reloadData = run(handler, messages, (h, ctx) -> {
            send(ctx.getSource().getSender(), messages, Reply.ok("reload.data.started"));
            return h.reloadData();
        });
        return Commands.literal("tfp")
                .requires(s -> s.getSender().hasPermission(PERMISSION))
                .executes(version)
                .then(Commands.literal("version").executes(version))
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(reloadData)
                        .then(Commands.literal("config").executes(run(handler, messages, (h, ctx) -> h.reloadSettings())))
                        .then(Commands.literal("data").executes(reloadData))
                        .then(Commands.literal("content").executes(run(handler, messages, (h, ctx) -> h.reloadContent()))))
                .then(Commands.literal("debug")
                        .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("block").executes(player(handler, messages, Handler::debugBlock)))
                        .then(Commands.literal("chunk")
                                .executes(run(handler, messages, (h, ctx) -> ctx.getSource().getSender() instanceof Player p
                                        ? h.debugChunk(p)
                                        : CompletableFuture.completedFuture(Reply.error("command.position_required"))))
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(run(handler, messages, (h, ctx) -> h.debugChunkAt(
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "z")))))))
                        .then(Commands.literal("snowtime")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("tick", LongArgumentType.longArg())
                                                        .executes(run(handler, messages, (h, ctx) -> {
                                                            long tick = LongArgumentType.getLong(ctx, "tick");
                                                            if (tick < 0) {
                                                                return CompletableFuture.completedFuture(
                                                                        Reply.error("debug.snowtime.negative", tick));
                                                            }
                                                            return h.debugSnowTime(IntegerArgumentType.getInteger(ctx, "x"),
                                                                    IntegerArgumentType.getInteger(ctx, "z"), tick);
                                                        }))))))
                        .then(Commands.literal("worldgen")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_WORLDGEN_CHUNKS))
                                        .executes(run(handler, messages, (h, ctx) -> h.debugWorldgen(
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("player")
                                .executes(player(handler, messages, (h, p) -> h.debugPlayer(p.getName())))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(run(handler, messages, (h, ctx) -> h.debugPlayer(
                                                StringArgumentType.getString(ctx, "name"))))))
                        .then(Commands.literal("thirst")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 100f))
                                                .executes(run(handler, messages, (h, ctx) -> h.debugSetThirst(
                                                        StringArgumentType.getString(ctx, "name"),
                                                        FloatArgumentType.getFloat(ctx, "value")))))))
                        .then(Commands.literal("packets").executes(run(handler, messages, (h, ctx) -> h.debugPackets())))
                        .then(Commands.literal("showcase")
                                .then(Commands.argument("family", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Showcase.Family.arguments().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(run(handler, messages, (h, ctx) ->
                                                ctx.getSource().getSender() instanceof Player p
                                                        ? h.debugShowcase(p, StringArgumentType.getString(ctx, "family"))
                                                        : CompletableFuture.completedFuture(Reply.error("command.player_only")))))))
                .then(Commands.literal("time")
                        .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("set")
                                .then(Commands.literal("day").executes(timeSet(handler, messages, "day",
                                        TimeCommand.DAY_TICKS_OF_DAY)))
                                .then(Commands.literal("noon").executes(timeSet(handler, messages, "noon",
                                        TimeCommand.NOON_TICKS_OF_DAY)))
                                .then(Commands.literal("night").executes(timeSet(handler, messages, "night",
                                        TimeCommand.NIGHT_TICKS_OF_DAY)))
                                .then(Commands.literal("midnight").executes(timeSet(handler, messages, "midnight",
                                        TimeCommand.MIDNIGHT_TICKS_OF_DAY)))
                                .then(Commands.literal("hour")
                                        .then(Commands.argument("hour", IntegerArgumentType.integer(0, 24))
                                                .executes(run(handler, messages, (h, ctx) -> {
                                                    int hour = IntegerArgumentType.getInteger(ctx, "hour");
                                                    return h.skipTime("time.set.done", "hour " + hour,
                                                            TimeCommand.setTimeOfDay(TimeCommand.hourTicksOfDay(hour)));
                                                })))))
                        .then(Commands.literal("add")
                                .then(timeAdd(handler, messages, TimeCommand.Unit.YEARS, "years"))
                                .then(timeAdd(handler, messages, TimeCommand.Unit.MONTHS, "months"))
                                .then(timeAdd(handler, messages, TimeCommand.Unit.DAYS, "days"))
                                .then(timeAdd(handler, messages, TimeCommand.Unit.HOURS, "hours"))
                                .then(timeAdd(handler, messages, TimeCommand.Unit.TICKS, "ticks"))))
                .build();
    }

    private static Command<CommandSourceStack> timeSet(Supplier<Handler> handler, Messages messages, String label,
            int ticksOfDay) {
        return run(handler, messages, (h, ctx) -> h.skipTime("time.set.done", label,
                TimeCommand.setTimeOfDay(ticksOfDay)));
    }

    /** {@code add <unit> <n>}: a literal {@code unit} with an integer argument {@code name} of at least 1. */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> timeAdd(Supplier<Handler> handler,
            Messages messages, TimeCommand.Unit unit, String name) {
        return Commands.literal(name).then(Commands.argument(name, IntegerArgumentType.integer(1))
                .executes(run(handler, messages, (h, ctx) -> {
                    int n = IntegerArgumentType.getInteger(ctx, name);
                    return h.skipTime("time.add.done", n + " " + name, TimeCommand.add(unit, n));
                })));
    }

    @FunctionalInterface
    private interface Action {
        CompletableFuture<Reply> apply(Handler handler, CommandContext<CommandSourceStack> ctx);
    }

    private static Command<CommandSourceStack> run(Supplier<Handler> handler, Messages messages, Action action) {
        return ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            Handler h = handler.get();
            if (h == null) {
                send(sender, messages, Reply.error("command.not_enabled"));
                return 0;
            }
            reply(sender, messages, action.apply(h, ctx));
            return Command.SINGLE_SUCCESS;
        };
    }

    /**
     * How long a console (RCON) command waits for its reply. RCON only captures what is sent while the command
     * runs, so the reply must be ready before the command returns. Players are not waited for.
     */
    static final long CONSOLE_WAIT_MILLIS = 2_000;

    /**
     * Sends {@code future}'s reply to {@code sender}. A console sender gets the reply synchronously when it is
     * ready within {@link #CONSOLE_WAIT_MILLIS}, so RCON returns it; otherwise (and always for players) the reply
     * is sent from whichever thread completes the future.
     */
    static void reply(CommandSender sender, Messages messages, CompletableFuture<Reply> future) {
        if (!(sender instanceof Player)) {
            try {
                send(sender, messages, outcome(future.get(CONSOLE_WAIT_MILLIS, TimeUnit.MILLISECONDS), null));
                return;
            } catch (ExecutionException e) {
                send(sender, messages, outcome(null, e.getCause()));
                return;
            } catch (TimeoutException e) {
                // Too slow for the command: the reply is still sent below, to the server log for the console.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        future.whenComplete((r, err) -> send(sender, messages, outcome(r, err)));
    }

    private static Reply outcome(Reply r, Throwable err) {
        return err == null ? r : Reply.error("command.failed", rootMessage(err));
    }

    private static Command<CommandSourceStack> player(Supplier<Handler> handler, Messages messages,
            java.util.function.BiFunction<Handler, Player, CompletableFuture<Reply>> action) {
        return run(handler, messages, (h, ctx) -> ctx.getSource().getSender() instanceof Player p ? action.apply(h, p)
                : CompletableFuture.completedFuture(Reply.error("command.player_only")));
    }

    static String rootMessage(Throwable t) {
        Throwable c = t;
        while ((c instanceof java.util.concurrent.CompletionException || c instanceof java.util.concurrent.ExecutionException)
                && c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.toString();
    }

    static void send(CommandSender sender, Messages messages, Reply reply) {
        sender.sendMessage(Component.text(messages.get(sender, reply.key(), reply.args().toArray()),
                reply.ok() ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (String line : reply.details()) {
            sender.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
    }
}
