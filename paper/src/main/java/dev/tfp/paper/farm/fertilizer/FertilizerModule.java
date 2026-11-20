/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 */
package dev.tfp.paper.farm.fertilizer;

import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.tags.Tags;
import org.bukkit.inventory.ItemStack;

/** PAR-FARM-02: fertilizer items on farmland and crops. Must be listed after the device, tags and farm modules. */
public final class FertilizerModule implements FeatureModule {
    public static final String ID = "fertilizer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Fertilizers fertilizers = new Fertilizers(() -> context.registryService().registries());
        FoodHooks<ItemStack> noFood = new FoodHooks<>() {
            @Override
            public boolean isRotten(ItemStack stack) {
                return false;
            }

            @Override
            public boolean hasTrait(ItemStack stack, String trait) {
                return false;
            }
        };
        context.listen(new FertilizerListener(fertilizers, context.integrations().customBlocks(), tags, () -> devices,
            stack -> new ItemIngredientContext(tags::itemId, tags.snapshot(), noFood, s -> 0f)));
    }
}
