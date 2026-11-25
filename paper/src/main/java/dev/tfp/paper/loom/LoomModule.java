/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LoomBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 * getSlotStackLimit, tick),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLoomBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement)
 */
package dev.tfp.paper.loom;

import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-06: the loom. Registers the {@link LoomDevice} kind; the blocks {@code tfp:wood/loom/<wood>} are written by
 * datagen ({@code configuration/loom/loom.yml}). Needs the device and recipe modules before it in the service file.
 */
public final class LoomModule implements FeatureModule {
    public static final String ID = "loom";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        PaperLoomEffects effects = new PaperLoomEffects(context.scheduler(), context.integrations().customBlocks(),
            () -> context.integrations().blockVisuals(), devices::neighbourChanged);
        devices.register(LoomDevice.kind(new LoomDevice.Services(stack -> recipe(recipes, stack), effects)));
        context.listen(new LoomListener(context.integrations().customBlocks(), effects, context.scheduler()));
    }

    /** TFC {@code LoomRecipe.getRecipe(stack)}: the recipe whose ingredient accepts {@code stack}. Any thread. */
    static Optional<LoomDevice.Recipe> recipe(Recipes recipes, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return Optional.empty();
        }
        return recipes.first(RecipeTypes.LOOM, stack).map(entry -> {
            int count = entry.recipe().require("ingredient", SizedIngredient.class).count();
            int steps = entry.recipe().require("steps", Integer.class);
            ItemStackProvider result = entry.recipe().require("result", ItemStackProvider.class);
            return new LoomDevice.Recipe(count, steps, in -> recipes.singleOutput(result, in));
        });
    }
}
