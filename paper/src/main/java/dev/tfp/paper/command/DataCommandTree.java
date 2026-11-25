/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536
 */
package dev.tfp.paper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.tfp.core.command.AddTrimCommand;
import dev.tfp.core.command.ClearWorldCommand;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.command.CountBlockCommand;
import dev.tfp.core.command.PlayerCommand;
import dev.tfp.core.command.PlayerCommand.Stat;
import dev.tfp.core.command.PlayerCommand.Verb;
import dev.tfp.core.command.WeatherCommand;
import dev.tfp.paper.boot.TfpCommands;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.DebugTree;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The TFC command literals under {@code /tfp} (PAR-DATA-01): {@code player}, {@code count}, {@code clearworld},
 * {@code trim} and {@code weather}, each for {@value TfpCommands#ADMIN_PERMISSION} (TFC: permission level 2). Syntax
 * Brigadier checks (integers and their ranges, the literals); everything else goes through the {@code core/command}
 * parsers so the words mean what TFC's arguments mean. {@code TfpCommands} adds these literals to its tree.
 *
 * <p>The target of {@code player} is an online player's name (TFC takes an entity selector; see the ledger).
 */
public final class DataCommandTree {
    /** What the commands need, once the module is enabled. */
    public record Ops(PlayerCommands player, WorldCommands world, TrimCommands trim, WeatherCommands weather,
            Messages messages) {
    }

    private DataCommandTree() {
    }

    /**
     * @param runner  turns an action into a command (reply sending, enabled check), as for the debug literals
     * @param enabled whether the module is enabled
     * @param ops     the live operations; read only while {@code enabled} holds
     */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> literals(DebugTree.Runner runner,
            BooleanSupplier enabled, Supplier<Ops> ops) {
        return List.of(player(runner, enabled, ops), count(runner, enabled, ops), clearWorld(runner, enabled, ops),
                trim(runner, enabled, ops), weather(runner, enabled, ops));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.getSender().hasPermission(TfpCommands.ADMIN_PERMISSION));
    }

    // ---- player -------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> player(DebugTree.Runner runner, BooleanSupplier enabled,
            Supplier<Ops> ops) {
        RequiredArgumentBuilder<CommandSourceStack, String> target = Commands.argument("target", StringArgumentType.word())
                .suggests((c, b) -> {
                    c.getSource().getSender().getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                    return b.buildFuture();
                });
        LiteralArgumentBuilder<CommandSourceStack> query = Commands.literal("query");
        for (Stat stat : Stat.values()) {
            query.then(Commands.literal(word(stat)).executes(runner.command(enabled,
                    ctx -> player(ctx, ops, new PlayerCommand.Request(Verb.QUERY, stat, 0)))));
        }
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add");
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
        for (Stat stat : List.of(Stat.HUNGER, Stat.SATURATION, Stat.WATER)) {
            int max = stat == Stat.WATER ? 100 : 20;
            set.then(Commands.literal(word(stat)).then(Commands.argument("value", IntegerArgumentType.integer(0, max))
                    .executes(runner.command(enabled, ctx -> player(ctx, ops, new PlayerCommand.Request(Verb.SET, stat,
                            IntegerArgumentType.getInteger(ctx, "value")))))));
            // TFC: add water takes 0 to 100, the other adds -20 to 20
            int addMin = stat == Stat.WATER ? 0 : -20;
            add.then(Commands.literal(word(stat)).then(Commands.argument("value", IntegerArgumentType.integer(addMin, max))
                    .executes(runner.command(enabled, ctx -> player(ctx, ops, new PlayerCommand.Request(Verb.ADD, stat,
                            IntegerArgumentType.getInteger(ctx, "value")))))));
            int resetTo = switch (stat) {
                case HUNGER -> PlayerCommand.RESET_HUNGER;
                case SATURATION -> PlayerCommand.RESET_SATURATION;
                default -> PlayerCommand.RESET_WATER;
            };
            reset.then(Commands.literal(word(stat)).executes(runner.command(enabled,
                    ctx -> player(ctx, ops, new PlayerCommand.Request(Verb.RESET, stat, resetTo)))));
        }
        return admin("player").then(target.then(query).then(set).then(reset).then(add));
    }

    private static String word(Stat stat) {
        return stat.name().toLowerCase(Locale.ROOT);
    }

    private static CompletableFuture<Reply> player(CommandContext<CommandSourceStack> ctx, Supplier<Ops> ops,
            PlayerCommand.Request request) {
        String name = StringArgumentType.getString(ctx, "target");
        Player target = ctx.getSource().getSender().getServer().getPlayerExact(name);
        if (target == null) {
            return done(Reply.error("command.player_not_found", name));
        }
        return ops.get().player().run(target, request);
    }

    // ---- count, clearworld --------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> count(DebugTree.Runner runner, BooleanSupplier enabled,
            Supplier<Ops> ops) {
        return admin("count").then(Commands.argument("radius", IntegerArgumentType.integer(
                CountBlockCommand.MIN_RADIUS, CountBlockCommand.MAX_RADIUS))
                .then(Commands.argument("block", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            b.suggest("minecraft:stone");
                            return b.buildFuture();
                        })
                        .executes(runner.command(enabled, ctx -> {
                            Parsed<CountBlockCommand.Request> parsed = CountBlockCommand.parse(List.of(
                                    Integer.toString(IntegerArgumentType.getInteger(ctx, "radius")),
                                    StringArgumentType.getString(ctx, "block")));
                            return switch (parsed) {
                                case Parsed.Refused<CountBlockCommand.Request> r -> done(refused(r));
                                case Parsed.Ok<CountBlockCommand.Request> ok ->
                                        ops.get().world().count(location(ctx), ok.value());
                            };
                        }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> clearWorld(DebugTree.Runner runner,
            BooleanSupplier enabled, Supplier<Ops> ops) {
        com.mojang.brigadier.Command<CommandSourceStack> noPreset = runner.command(enabled,
                ctx -> clear(ctx, ops, List.of(Integer.toString(IntegerArgumentType.getInteger(ctx, "radius")))));
        return admin("clearworld").then(Commands.argument("radius", IntegerArgumentType.integer(
                ClearWorldCommand.MIN_RADIUS, ClearWorldCommand.MAX_RADIUS))
                .executes(noPreset)
                .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((c, b) -> {
                            Arrays.stream(ClearWorldCommand.Preset.values()).forEach(p -> b.suggest(p.name()));
                            return b.buildFuture();
                        })
                        .executes(runner.command(enabled, ctx -> clear(ctx, ops, List.of(
                                Integer.toString(IntegerArgumentType.getInteger(ctx, "radius")),
                                StringArgumentType.getString(ctx, "preset")))))));
    }

    private static CompletableFuture<Reply> clear(CommandContext<CommandSourceStack> ctx, Supplier<Ops> ops,
            List<String> args) {
        return switch (ClearWorldCommand.parse(args)) {
            case Parsed.Refused<ClearWorldCommand.Request> r -> done(refused(r));
            case Parsed.Ok<ClearWorldCommand.Request> ok -> {
                CommandSender sender = ctx.getSource().getSender();
                sender.sendMessage(Component.text(ops.get().messages().get(sender, "command.data.clear.starting")));
                yield ops.get().world().clear(location(ctx), ok.value());
            }
        };
    }

    private static Location location(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getLocation();
    }

    // ---- trim ---------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> trim(DebugTree.Runner runner, BooleanSupplier enabled,
            Supplier<Ops> ops) {
        return admin("trim").then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((c, b) -> {
                    if (!b.getRemaining().contains(" ")) {
                        b.suggest("minecraft:iron_ingot");
                    }
                    return b.buildFuture();
                })
                .executes(runner.command(enabled, ctx -> trim(ctx, ops))));
    }

    private static CompletableFuture<Reply> trim(CommandContext<CommandSourceStack> ctx, Supplier<Ops> ops) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return done(Reply.error("command.player_only"));
        }
        List<String> args = new ArrayList<>(Arrays.asList(StringArgumentType.getString(ctx, "args").trim().split("\\s+")));
        return switch (AddTrimCommand.parse(args)) {
            case Parsed.Refused<AddTrimCommand.Request> r -> done(refused(r));
            case Parsed.Ok<AddTrimCommand.Request> ok -> {
                AddTrimCommand.Request request = ok.value();
                Ops o = ops.get();
                CompletableFuture<TrimCommands.Outcome> outcome = request.metal().isPresent()
                        ? o.trim().set(player, request.material(), request.pattern(), request.metal().get())
                        : o.trim().held(player, request.material(), request.pattern());
                yield outcome.thenApply(out -> trimReply(o.messages(), player, out));
            }
        };
    }

    /** The reply for a trim outcome; a failure repeats once per piece, as TFC reports it (the extra lines translated). */
    static Reply trimReply(Messages messages, CommandSender sender, TrimCommands.Outcome out) {
        if (out.notApplied()) {
            return Reply.error("command.data.trim.not_applied");
        }
        if (out.failure().isPresent()) {
            String key = switch (out.failure().get()) {
                case NOT_ARMOR -> "command.data.trim.not_armor";
                case BAD_MATERIAL -> "command.data.trim.bad_material";
                case BAD_TEMPLATE -> "command.data.trim.bad_template";
            };
            List<String> more = new ArrayList<>();
            for (int i = 1; i < out.reports(); i++) {
                more.add(messages.get(sender, key));
            }
            return Reply.error(key).withDetails(more);
        }
        return out.given() > 0 ? Reply.ok("command.data.trim.set", out.given()) : Reply.ok("command.data.trim.done");
    }

    // ---- weather ------------------------------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> weather(DebugTree.Runner runner, BooleanSupplier enabled,
            Supplier<Ops> ops) {
        LiteralArgumentBuilder<CommandSourceStack> weather = admin("weather");
        for (WeatherCommand.Action action : WeatherCommand.Action.values()) {
            weather.then(Commands.literal(action.name().toLowerCase(Locale.ROOT)).executes(runner.command(enabled,
                    ctx -> done(ops.get().weather().run(location(ctx).getWorld(), action)))));
        }
        return weather;
    }

    // ---- shared -------------------------------------------------------------------------------------------------

    /** A parse refusal as a reply: {@code command.data.parse.<error>} with the token. */
    static <T> Reply refused(Parsed.Refused<T> refused) {
        return Reply.error("command.data.parse." + refused.error().name().toLowerCase(Locale.ROOT), refused.token());
    }

    private static CompletableFuture<Reply> done(Reply reply) {
        return CompletableFuture.completedFuture(reply);
    }
}
