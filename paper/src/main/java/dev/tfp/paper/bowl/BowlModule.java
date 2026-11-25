/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536
 */
package dev.tfp.paper.bowl;

import dev.tfp.core.food.FoodTrait;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import dev.tfp.paper.placed.PlacedSupport;
import dev.tfp.paper.tags.Tags;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-09: the wooden and ceramic bowl blocks. Registers the two {@link BowlDevices} kinds, the placement of the vanilla
 * bowl ({@link BowlPlacement}) and the floor rule ({@link BowlSupport}). Needs the device, tags, neighbour and food modules
 * before it in the service file. The glassworking side is {@link BowlGlass#NONE} until PAR-PROC-16 registers a
 * {@link BowlGlass} service.
 */
public final class BowlModule implements FeatureModule {
    public static final String ID = "bowl";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        ItemFoodHooks hooks = ctx.services().require(ItemFoodHooks.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        CustomItems items = ctx.integrations().customItems();
        BowlGlass glass = ctx.services().get(BowlGlass.class).orElse(BowlGlass.NONE);
        BowlEffects effects = new PaperBowlEffects(items);
        BowlDevices bowls = new BowlDevices(tags::is, items, effects, new HookFood(hooks), glass);
        bowls.kinds().forEach(devs::register);
        ctx.listen(new BowlPlacement(blocks, effects));
        ctx.services().get(NeighbourService.class).ifPresent(n -> n.register(new BowlSupport(id -> !BowlDevices.isBowl(id) && PlacedSupport.solidId(id))));
    }

    /** TFC {@code FoodCapability.applyTrait(stack, SALTED)} on the stack's own food state. */
    record HookFood(ItemFoodHooks hooks) implements BowlFood {
        @Override
        public boolean canSalt(ItemStack stack) {
            return FoodPdc.has(stack) && !hooks.hasTrait(stack, "tfc:salted");
        }

        @Override
        public void salt(ItemStack stack) {
            FoodPdc.read(stack).ifPresent(f -> FoodPdc.write(stack,
                f.withTraitApplied(FoodTrait.SALTED, hooks.decayModifier(stack), hooks.now(), hooks.config())));
        }
    }
}
