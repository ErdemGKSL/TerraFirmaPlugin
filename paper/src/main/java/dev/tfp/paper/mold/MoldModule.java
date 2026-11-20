/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536
 */
package dev.tfp.paper.mold;

import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.vessel.VesselItems;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.module.DebugTree;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-05: fired molds. A molten vessel or mold pours into a mold ({@link MoldScreens}, {@link MoldListener}); the
 * content solidifies by cooling (lazy, {@link MoldItems#isMolten}); a cooled mold gives its casting by sneak-use or by a
 * right-click on it in an inventory; the crafting grid already handles it (PAR-PROC-03). Listed after the {@code vessel},
 * {@code recipes}, {@code gui}, {@code tags}, {@code heat} and {@code tooltip} modules whose services it uses.
 */
public final class MoldModule implements FeatureModule {
    public static final String ID = "mold";

    private volatile dev.tfp.paper.scheduler.Scheduler scheduler;
    private volatile java.util.function.LongSupplier clock;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        ItemHeatHooks heat = context.services().require(ItemHeatHooks.class);
        VesselItems vessels = context.services().require(VesselItems.class);
        var custom = context.integrations().customItems();
        MoldItems molds = new MoldItems(new MoldItems.Services(() -> context.clock().get().playerTicks(), custom::idOf,
            (fluid, tag) -> tags.is(TagType.FLUID, fluid, tag), heat, mold -> casting(recipes, heat, mold)));
        scheduler = context.scheduler();
        clock = () -> context.clock().get().playerTicks();
        MoldPourer pourer = new MoldPourer(vessels, molds);
        MoldScreens screens = new MoldScreens(gui, context.scheduler(), context.messages(), molds, pourer);
        context.services().provide(MoldItems.class, molds);
        context.services().provide(MoldPourer.class, pourer);
        context.listen(new MoldListener(molds, pourer, screens, context.messages(), () -> ThreadLocalRandom.current().nextFloat()));

        FluidLang lang = FluidLang.fromJar(context.plugin().getClass().getClassLoader());
        if (!lang.load(FluidLang.FALLBACK)) {
            context.logger().warning("mold: " + FluidLang.JAR_DIR + FluidLang.FALLBACK + ".json is missing; the tooltip uses ids");
        }
        context.services().require(TooltipPipeline.class).add(new MoldTooltipContributor(lang));
    }

    /**
     * {@code /tfp debug mold <name> heat <temperature>} sets the heat of the held item now; {@code fluid <id> <amount>} sets
     * its fluid (for the in-game checks: a molten vessel or a filled mold without a fire).
     */
    @Override
    public void debugCommands(DebugTree tree) {
        var heat = Commands.literal("heat").then(Commands.argument("temperature", FloatArgumentType.floatArg(0f, 1600f))
            .executes(tree.run(ctx -> onHeld(ctx, (p, held) -> {
                HeatPdc.write(held, new HeatState(FloatArgumentType.getFloat(ctx, "temperature"), clock.getAsLong()));
                p.getInventory().setItemInMainHand(held);
                return Reply.ok("debug.mold.done");
            }))));
        var fluid = Commands.literal("fluid").then(Commands.argument("id", StringArgumentType.string())
            .then(Commands.argument("amount", IntegerArgumentType.integer(0, 3000))
                .executes(tree.run(ctx -> onHeld(ctx, (p, held) -> {
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    FluidPdc.write(held, amount == 0 ? null
                        : new dev.tfp.core.vessel.FluidAmount(StringArgumentType.getString(ctx, "id"), amount));
                    p.getInventory().setItemInMainHand(held);
                    return Reply.ok("debug.mold.done");
                })))));
        tree.add("mold", n -> n.then(Commands.argument("name", StringArgumentType.word()).then(heat).then(fluid)));
    }

    private CompletableFuture<Reply> onHeld(com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack> ctx,
            java.util.function.BiFunction<Player, ItemStack, Reply> action) {
        Player player = org.bukkit.Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "name"));
        if (player == null) {
            return CompletableFuture.completedFuture(Reply.error("debug.mold.no_player"));
        }
        CompletableFuture<Reply> reply = new CompletableFuture<>();
        dev.tfp.paper.scheduler.Scheduler sch = scheduler;
        if (sch == null || !sch.entity(player, () -> reply.complete(action.apply(player, player.getInventory().getItemInMainHand())),
            () -> reply.complete(Reply.error("debug.mold.no_player")))) {
            reply.complete(Reply.error("debug.mold.no_player"));
        }
        return reply;
    }

    /**
     * TFC {@code CastingRecipe.get(mold)} and {@code assemble}: the first casting recipe of the mold item whose fluid
     * ingredient takes the content, the result with the mold's heat when the mold is hotter than the result.
     */
    static Optional<MoldItems.Cast> casting(Recipes recipes, ItemHeatHooks heat, ItemStack mold) {
        var ctx = recipes.ingredients();
        var content = dev.tfp.paper.recipe.ItemIngredientContext.containedFluid(mold);
        if (content.isEmpty()) {
            return Optional.empty();
        }
        Optional<RecipeEntry> entry = recipes.index().first(RecipeTypes.CASTING, mold, ctx,
            r -> r.recipe().values().get("fluid") instanceof SizedFluidIngredient f && f.test(content.get(), ctx.tags()));
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        ItemStackProvider provider = entry.get().recipe().require("result", ItemStackProvider.class);
        var hooks = recipes.modifiers().forCrafting(List.of(mold), extra -> { });
        ItemStack out = provider.getSingleStack(mold.clone(), hooks);
        if (out == null || out.isEmpty()) {
            return Optional.empty();
        }
        if (heat.temperature(mold) > heat.temperature(out)) {
            out = hooks.copyHeat(out, mold);
        }
        float chance = entry.get().recipe().get("break_chance", Float.class).orElse(1f);
        return Optional.of(new MoldItems.Cast(out, chance));
    }
}
