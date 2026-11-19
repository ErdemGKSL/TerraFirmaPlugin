/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.alloy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.VesselRules;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.vessel.VesselItems;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-01: the alloy display for vessels and crucibles. Provides {@link AlloyRecipes}, stores a mixed alloy on an item
 * ({@link AlloyPdc}) and shows it on the tooltip ({@link AlloyTooltipContributor}). Listed after the {@code tooltip},
 * {@code recipes}, {@code custom item} and {@code vessel} modules. The vessel and crucible fill and melt code writes the alloy
 * through {@link AlloyPdc} (follow-up item).
 *
 * <p>Debug: {@code /tfp debug alloy <name> put <fluid> <mB>} adds a fluid to the alloy of the held vessel or crucible
 * (capacity 3000 mB; a bare metal name such as {@code copper} is {@code metal/copper}), {@code clear} empties it.
 */
public final class AlloyModule implements FeatureModule {
    public static final String ID = "alloy";

    private volatile Scheduler scheduler;
    private volatile CustomItems customItems;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        AlloyRecipes recipes = new AlloyRecipes(context.services().require(Recipes.class));
        context.services().provide(AlloyRecipes.class, recipes);
        scheduler = context.scheduler();
        customItems = context.integrations().customItems();
        FluidLang lang = FluidLang.fromJar(context.plugin().getClass().getClassLoader());
        if (!lang.load(FluidLang.FALLBACK)) {
            context.logger().warning("alloy: " + FluidLang.JAR_DIR + FluidLang.FALLBACK + ".json is missing; the alloy lines use ids");
        }
        context.services().require(TooltipPipeline.class).add(new AlloyTooltipContributor(lang, recipes::get));
    }

    @Override
    public void debugCommands(DebugTree tree) {
        var put = Commands.literal("put").then(Commands.argument("fluid", StringArgumentType.word())
            .then(Commands.argument("amount", IntegerArgumentType.integer(1, VesselRules.SMALL_VESSEL_CAPACITY))
                .executes(tree.run(ctx -> onPlayer(ctx, p -> put(p, StringArgumentType.getString(ctx, "fluid"),
                    IntegerArgumentType.getInteger(ctx, "amount")))))));
        var clear = Commands.literal("clear").executes(tree.run(ctx -> onPlayer(ctx, this::clear)));
        tree.add("alloy", a -> a.then(Commands.argument("name", StringArgumentType.word()).then(put).then(clear)));
    }

    private CompletableFuture<Reply> onPlayer(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.Function<Player, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        if (player == null) {
            reply.complete(Reply.error("debug.alloy.no_player"));
            return reply;
        }
        Scheduler sch = scheduler;
        if (sch == null || !sch.entity(player, () -> reply.complete(action.apply(player)),
            () -> reply.complete(Reply.error("debug.alloy.no_player")))) {
            reply.complete(Reply.error("debug.alloy.no_player"));
        }
        return reply;
    }

    private boolean holdsContainer(ItemStack held) {
        Optional<String> id = customItems.idOf(held);
        return id.isPresent() && (VesselItems.isVesselId(id.get()) || id.get().equals(AlloyTooltipContributor.CRUCIBLE_ID));
    }

    private Reply put(Player player, String fluidName, int amount) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!holdsContainer(held)) {
            return Reply.error("debug.alloy.no_container");
        }
        Optional<TfcFluid> fluid = TfcFluids.resolve(fluidName).or(() -> TfcFluids.resolve("metal/" + fluidName));
        if (fluid.isEmpty()) {
            return Reply.error("debug.alloy.unknown_fluid", fluidName);
        }
        FluidAlloy.Fill fill = AlloyPdc.read(held).fill(fluid.get().id(), amount, VesselRules.SMALL_VESSEL_CAPACITY);
        AlloyPdc.write(held, fill.alloy());
        player.getInventory().setItemInMainHand(held);
        return Reply.ok("debug.alloy.put", fill.accepted(), fluid.get().id());
    }

    private Reply clear(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!holdsContainer(held)) {
            return Reply.error("debug.alloy.no_container");
        }
        AlloyPdc.write(held, null);
        player.getInventory().setItemInMainHand(held);
        return Reply.ok("debug.alloy.cleared");
    }
}
