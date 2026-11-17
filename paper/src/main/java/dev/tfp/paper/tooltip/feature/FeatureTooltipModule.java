/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.item.feature.FeatureTooltips;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.heat.HeatTooltipRecipes;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-50: TFC's per-feature tooltip lines that no item feature draws: the contents of sealed device items
 * (barrel, large vessel, powderkeg) and of the crucible item, the fertilized egg and its hatch day, the javelin's
 * thrown damage, the pan's deposit, the flower cutting's plant, the lamp's fuel, the mold's metal and the glass batch
 * with its operations. Each line appears as soon as an item carries the data ({@link FeatureData}); the items
 * themselves come with their features. Listed after the {@code tags}, {@code tooltip}, {@code recipe} and heat modules
 * in the service file. Adds {@code /tfp debug egg <name> (fertilized <days> | clear)}.
 *
 * <p>Threads: the lines run on Netty threads over the packet copy and immutable snapshots; the debug command edits the
 * held item on the player's thread.
 */
public final class FeatureTooltipModule implements FeatureModule {
    public static final String ID = "feature_tooltip";

    private volatile Scheduler scheduler;
    private volatile LongSupplier calendarTicks;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        calendarTicks = () -> context.clock().get().calendarTicks();
        FeatureLang lang = FeatureLang.fromJar(context.plugin().getClass().getClassLoader(),
                task -> context.scheduler().io(task));
        if (!lang.load(FeatureLang.FALLBACK)) {
            context.logger().warning("feature_tooltip: " + FeatureLang.JAR_DIR + FeatureLang.FALLBACK
                    + ".json is missing; feature lines use ids");
        }
        FeatureLineRenderer renderer = new FeatureLineRenderer(lang, FeatureTooltipModule::vanillaItemKey);

        ItemHeatDefinitionsCache heat = context.services().require(ItemHeatDefinitionsCache.class);
        Recipes recipeService = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        HeatTooltipRecipes recipes = new HeatTooltipRecipes(
                id -> recipeService.index().byItem(RecipeTypes.HEATING, id),
                () -> ItemIngredientContext.lookup(tags.snapshot()));
        FeatureSources.GlassDanger danger = (item, ctx) -> {
            float temperature = HeatTooltipRecipes.temperature(item, heat.get(), HeatConfig.DEFAULT,
                    ctx.clock().playerTicks()).orElse(0f);
            float recipe = recipes.find(item, temperature).map(HeatTooltipRecipes.Heating::temperature)
                    .orElse(Float.NaN);
            return FeatureTooltips.glassDanger(temperature, recipe);
        };

        TooltipPipeline pipeline = context.services().require(TooltipPipeline.class);
        for (FeatureLinesContributor c : contributors(renderer, danger)) {
            pipeline.add(c);
        }
    }

    /** The contributors of this item, in TFC's positions. */
    static List<FeatureLinesContributor> contributors(FeatureLineRenderer renderer, FeatureSources.GlassDanger danger) {
        int hover = FeatureSources.ORDER_HOVER;
        return List.of(
                new FeatureLinesContributor("device_contents", hover, FeatureSources::contents, renderer),
                new FeatureLinesContributor("javelin", hover, FeatureSources::javelin, renderer),
                new FeatureLinesContributor("pan_deposit", hover, FeatureSources::pan, renderer),
                new FeatureLinesContributor("flower_cutting", hover, FeatureSources::flowerCutting, renderer),
                new FeatureLinesContributor("lamp_fuel", hover, FeatureSources::lamp, renderer),
                new FeatureLinesContributor("mold_metal", hover, FeatureSources::mold, renderer),
                new FeatureLinesContributor("glass_working", FeatureSources.ORDER_GLASS, FeatureSources.glass(danger),
                        renderer),
                new FeatureLinesContributor("egg", FeatureSources.ORDER_EGG, FeatureSources::egg, renderer));
    }

    /** The client translation key of a vanilla item id, or {@code null}. */
    static String vanillaItemKey(String itemId) {
        Material material = Material.matchMaterial(itemId);
        if (material == null) {
            return null;
        }
        String path = material.getKey().getKey();
        return (material.isBlock() ? "block.minecraft." : "item.minecraft.") + path;
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("egg", node -> node
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("fertilized")
                                .then(Commands.argument("days", IntegerArgumentType.integer(0, 100_000))
                                        .executes(tree.run(ctx -> debugEgg(
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "days"))))))
                        .then(Commands.literal("clear")
                                .executes(tree.run(ctx -> debugEgg(StringArgumentType.getString(ctx, "name"), -1))))));
    }

    /** Any thread: writes (days &ge; 0) or clears (days &lt; 0) the egg record of the named player's held egg. */
    CompletableFuture<Reply> debugEgg(String name, int days) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            return CompletableFuture.completedFuture(Reply.error("command.player_not_found", name));
        }
        long today = CalendarTicks.totalDays(Math.max(0, calendarTicks.getAsLong()));
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            return CompletableFuture.completedFuture(applyEgg(player, days, today));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                out.complete(applyEgg(player, days, today));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", name)));
        return out;
    }

    private static Reply applyEgg(Player player, int days, long today) {
        ThreadAsserts.owned(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        Reply reply = editEgg(player.getName(), held, days, today);
        if (reply.ok()) {
            player.getInventory().setItemInMainHand(held);
        }
        return reply;
    }

    /**
     * The debug edit on {@code stack} (changed in place on success): TFC's egg component with hatch day
     * {@code today + days}, or none when {@code days < 0}. Only a vanilla egg carries it, as in TFC.
     */
    static Reply editEgg(String player, ItemStack stack, int days, long today) {
        if (stack == null || stack.getType() != Material.EGG) {
            return Reply.error("debug.egg.not_egg", player);
        }
        FeatureData.Egg egg = days < 0 ? null : new FeatureData.Egg(true, today + days);
        if (!EggPdc.write(stack, egg)) {
            return Reply.error("debug.egg.not_egg", player);
        }
        return egg == null ? Reply.ok("debug.egg.cleared", player)
                : Reply.ok("debug.egg.done", player, egg.hatchDay(), days);
    }
}
