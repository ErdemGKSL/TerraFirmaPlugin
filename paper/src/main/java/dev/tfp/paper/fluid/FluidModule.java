/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fluid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FLUID-01: fluids as container contents. Adds the fluid line to the tooltip of filled buckets, jugs and glass
 * bottles ({@link FluidTooltipContributor}) and the debug fill
 * {@code /tfp debug fluid <name> <container> <fluid> [mB]}, which puts {@code mB} (default: full) of a registered fluid
 * ({@link TfcFluids}) into the container the player holds, after the container's {@code tfc:usable_in_*} check. Listed
 * after the {@code tags} and {@code tooltip} modules in the service file, whose services it requires.
 *
 * <p>Threads: the tooltip line runs on Netty threads over immutable data; the fill runs on the player's thread.
 */
public final class FluidModule implements FeatureModule {
    public static final String ID = "fluid";

    private volatile Scheduler scheduler;
    private volatile Tags tags;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        tags = context.services().require(Tags.class);
        ClassLoader loader = context.plugin().getClass().getClassLoader();
        FluidLang lang = FluidLang.fromJar(loader);
        if (!lang.load(FluidLang.FALLBACK)) {
            context.logger().warning("fluid: " + FluidLang.JAR_DIR + FluidLang.FALLBACK
                    + ".json is missing; fluid tooltips use English ids");
        }
        Logger logger = context.logger();
        context.scheduler().io(() -> {
            for (String locale : locales(name -> loader.getResourceAsStream(FluidLang.JAR_DIR + name))) {
                try {
                    lang.load(locale);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "fluid: cannot load the " + locale + " fluid names", e);
                }
            }
        });
        context.services().require(TooltipPipeline.class).add(new FluidTooltipContributor(lang));
    }

    /** The locales datagen lists in {@code content/lang/index.json}; empty when it is missing. */
    static List<String> locales(Function<String, InputStream> opener) {
        List<String> out = new ArrayList<>();
        try (InputStream in = opener.apply("index.json")) {
            if (in == null) {
                return out;
            }
            JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement e : index.getAsJsonArray("locales")) {
                out.add(e.getAsJsonObject().get("locale").getAsString());
            }
        } catch (IOException | RuntimeException e) {
            return out;
        }
        return out;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("fluid", node -> node
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("container", StringArgumentType.word())
                                .then(Commands.argument("fluid_and_amount", StringArgumentType.greedyString())
                                        .executes(tree.run(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            Player target = Bukkit.getPlayerExact(name);
                                            if (target == null) {
                                                return CompletableFuture.completedFuture(
                                                        Reply.error("command.player_not_found", name));
                                            }
                                            return debugFill(target, StringArgumentType.getString(ctx, "container"),
                                                    StringArgumentType.getString(ctx, "fluid_and_amount"));
                                        }))))));
    }

    /** Any thread: the debug fill, run on the player's thread. */
    CompletableFuture<Reply> debugFill(Player player, String container, String fluidAndAmount) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            return CompletableFuture.completedFuture(fillHeld(player, container, fluidAndAmount));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                out.complete(fillHeld(player, container, fluidAndAmount));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
        return out;
    }

    /** Player thread: fills the main-hand item. */
    private Reply fillHeld(Player player, String container, String fluidAndAmount) {
        ThreadAsserts.owned(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        Tags t = tags;
        Optional<String> itemId = t.itemId(held);
        Reply reply = fill(player.getName(), held, itemId.orElse(null), container, fluidAndAmount,
                (kind, fluid) -> kind.usableTag().map(tag -> t.is(TagType.FLUID, fluid, tag)).orElse(true));
        if (reply.ok()) {
            player.getInventory().setItemInMainHand(held);
        }
        return reply;
    }

    /** Whether a container kind may hold a fluid (the {@code tfc:usable_in_*} tag test). */
    @FunctionalInterface
    interface Usable {
        boolean test(FluidContainerKind kind, String fluidId);
    }

    /**
     * The debug fill on {@code stack} (changed in place on success): replaces its content with {@code mB} of the fluid,
     * capped at the container's capacity.
     *
     * @param itemId         the stack's item id, {@code null} for an empty hand
     * @param fluidAndAmount {@code <fluid> [mB]}
     */
    static Reply fill(String player, ItemStack stack, String itemId, String container, String fluidAndAmount,
            Usable usable) {
        if (itemId == null) {
            return Reply.error("debug.fluid.empty_hand", player);
        }
        Optional<FluidContainerKind> named = FluidContainerItems.byName(container);
        if (named.isEmpty()) {
            return Reply.error("debug.fluid.unknown_container", container,
                    String.join(", ", FluidContainerItems.all().values().stream().map(FluidContainerItems::name)
                            .sorted().toList()));
        }
        FluidContainerKind kind = named.get();
        if (FluidContainerItems.kindOf(itemId).filter(k -> k == kind).isEmpty()) {
            return Reply.error("debug.fluid.not_container", player, itemId, container);
        }
        String[] parts = fluidAndAmount.trim().split("\\s+");
        if (parts.length > 2 || parts[0].isEmpty()) {
            return Reply.error("debug.fluid.usage");
        }
        Optional<TfcFluid> fluid = TfcFluids.resolve(parts[0]);
        if (fluid.isEmpty()) {
            return Reply.error("debug.fluid.unknown_fluid", parts[0]);
        }
        int capacity = kind.defaultCapacity();
        int amount;
        try {
            amount = parts.length == 2 ? Integer.parseInt(parts[1]) : capacity;
        } catch (NumberFormatException e) {
            return Reply.error("debug.fluid.usage");
        }
        if (amount <= 0) {
            return Reply.error("debug.fluid.usage");
        }
        String id = fluid.get().id();
        if (!usable.test(kind, id)) {
            return Reply.error("debug.fluid.not_usable", id, container);
        }
        if (stack.getAmount() != 1 || FluidPdc.isNewer(stack)) {
            return Reply.error("debug.fluid.not_single", player);
        }
        ItemFluids.drain(stack, capacity);
        int filled = ItemFluids.fill(stack, new FluidAmount(id, amount), capacity, true);
        if (filled == 0) {
            return Reply.error("debug.fluid.not_single", player);
        }
        return Reply.ok("debug.fluid.done", player, filled, capacity, id, container);
    }

    /** For tests: a usable predicate from a plain fluid-id test. */
    static Usable usable(Predicate<String> fluids) {
        return (kind, fluid) -> fluids.test(fluid);
    }
}
