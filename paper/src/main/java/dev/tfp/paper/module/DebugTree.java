/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.tfp.paper.boot.TfpCommands.Reply;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;

/**
 * The builder a {@link FeatureModule} adds its {@code /tfp debug <literal>} subcommands with (PAR-INFRA-01).
 * {@code TfpCommands} merges the literals under {@code debug}, which already requires {@code tfp.admin}.
 *
 * <pre>
 * tree.add("food", food -> food
 *         .executes(tree.player((player, ctx) -> describe(player)))
 *         .then(Commands.argument("name", StringArgumentType.word())
 *                 .executes(tree.run(ctx -> describe(StringArgumentType.getString(ctx, "name"))))));
 * </pre>
 *
 * Commands built with {@link #run} or {@link #player} answer "not enabled" while the plugin or this module is not
 * enabled, and send the {@link Reply} the same way the built-in commands do (translated, console waits for it).
 * A literal that a built-in command or an earlier module already uses is skipped and reported.
 */
public final class DebugTree {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

    /** A subcommand's work: a reply future, completed on whatever thread finishes it. */
    @FunctionalInterface
    public interface Action {
        CompletableFuture<Reply> apply(CommandContext<CommandSourceStack> ctx);
    }

    /** How {@code TfpCommands} turns an {@link Action} into a command (enabled check, reply sending). */
    @FunctionalInterface
    public interface Runner {
        Command<CommandSourceStack> command(BooleanSupplier moduleEnabled, Action action);
    }

    private final String moduleId;
    private final BooleanSupplier enabled;
    private final Runner runner;
    private final Set<String> taken;
    private final List<LiteralArgumentBuilder<CommandSourceStack>> literals = new ArrayList<>();
    private final List<String> problems;

    /**
     * @param taken    literal names already used under {@code debug}; this tree adds its accepted names to it
     * @param problems where skipped literals are reported
     */
    DebugTree(String moduleId, BooleanSupplier enabled, Runner runner, Set<String> taken, List<String> problems) {
        this.moduleId = moduleId;
        this.enabled = enabled;
        this.runner = runner;
        this.taken = taken;
        this.problems = problems;
    }

    /** The id of the module this tree belongs to. */
    public String moduleId() {
        return moduleId;
    }

    /** Adds {@code /tfp debug <name>}, shaped by {@code body} (executes, arguments, sub-literals). */
    public void add(String name, Consumer<LiteralArgumentBuilder<CommandSourceStack>> body) {
        if (!NAME.matcher(name).matches()) {
            problems.add("module " + moduleId + ": debug literal '" + name + "' is not [a-z0-9_]+; skipped");
            return;
        }
        if (!taken.add(name)) {
            problems.add("module " + moduleId + ": debug literal '" + name + "' is already used; skipped");
            return;
        }
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name);
        body.accept(literal);
        literals.add(literal);
    }

    /** A command running {@code action} while the plugin and this module are enabled. */
    public Command<CommandSourceStack> run(Action action) {
        return runner.command(enabled, action);
    }

    /** {@link #run} for players only; other senders get {@code command.player_only}. */
    public Command<CommandSourceStack> player(
            BiFunction<Player, CommandContext<CommandSourceStack>, CompletableFuture<Reply>> action) {
        return run(ctx -> ctx.getSource().getSender() instanceof Player p ? action.apply(p, ctx)
                : CompletableFuture.completedFuture(Reply.error("command.player_only")));
    }

    List<LiteralArgumentBuilder<CommandSourceStack>> literals() {
        return List.copyOf(literals);
    }
}
