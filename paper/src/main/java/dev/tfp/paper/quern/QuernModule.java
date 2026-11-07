/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/QuernRecipe.java @ c3b4d9536 (getRecipe),
 * file: src/main/java/net/dries007/tfc/common/recipes/ItemRecipe.java @ c3b4d9536 (assemble: result.getSingleStack(input))
 */
package dev.tfp.paper.quern;

import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.food.FoodStacks;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-04: the quern. Registers the {@link QuernDevice} kind with the device framework; the block {@code tfp:quern}
 * is written by datagen ({@code configuration/quern/quern.yml}). Needs the food, tags, device and recipe modules before it in
 * the service file. The rotation network (PAR-MECH-06) will drive the same kind as a rotation sink.
 */
public final class QuernModule implements FeatureModule {
    public static final String ID = "quern";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        Optional<ItemFoodHooks> food = context.services().get(ItemFoodHooks.class);
        BiPredicate<ItemStack, ItemStack> merge = food.<BiPredicate<ItemStack, ItemStack>>map(f -> f::canMerge)
            .orElse(ItemStack::isSimilar);
        PaperQuernEffects effects = new PaperQuernEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals(), devices::neighbourChanged);
        devices.register(QuernDevice.kind(new QuernDevice.Services(stack -> recipe(recipes, stack),
            stack -> tags.is(stack, QuernDevice.HANDSTONES), merge, FoodStacks::mergeInto, effects)));
    }

    /** The quern result of {@code input} on the recipe runtime, empty when it has no quern recipe. Any thread. */
    static Optional<UnaryOperator<ItemStack>> recipe(Recipes recipes, ItemStack input) {
        if (StackCodec.isEmpty(input)) {
            return Optional.empty();
        }
        return recipes.first(RecipeTypes.QUERN, input).map(entry -> {
            ItemStackProvider result = entry.recipe().require("result", ItemStackProvider.class);
            return in -> recipes.singleOutput(result, in);
        });
    }
}
