/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536,
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowerLoomBlock.java @ c3b4d9536
 */
package dev.tfp.paper.powerloom;

import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loom.LoomDevice;
import dev.tfp.paper.loom.PaperLoomEffects;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.powerloom.PowerLoomIndex.Key;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.rotation.RotationService;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-MECH-08: the power loom. Registers the {@link PowerLoomDevice} kind ({@code tfp:power_loom}), watches the looms that a
 * rotation node can reach ({@link PowerLoomService}) and sets the facing on placement. Needs the device, recipe and rotation
 * modules before it in the service file; without the rotation module the loom is a loom nobody pushes. The block is written by
 * datagen (PAR-MECH-22).
 */
public final class PowerLoomModule implements FeatureModule {
    public static final String ID = "power_loom";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        PowerLoomDevice device = new PowerLoomDevice(new PowerLoomDevice.Services(stack -> recipe(recipes, stack)),
            devices.stacks());
        devices.register(device.kind());
        PaperLoomEffects effects = new PaperLoomEffects(context.scheduler(), blocks, () -> context.integrations().blockVisuals(),
            devices::neighbourChanged);
        context.listen(new PowerLoomListener(blocks, effects, context.scheduler()));
        Optional<RotationService> networks = context.services().get(RotationService.class);
        if (networks.isEmpty()) {
            context.logger().warning("Power loom: the rotation module is not running; power looms stay still");
            return;
        }
        PowerLoomIndex index = new PowerLoomIndex(context.chunkData());
        PaperPowerLoomAccess access = new PaperPowerLoomAccess(context.scheduler(), devices, blocks, networks.get(), device,
            index, uid -> context.plugin().getServer().getWorld(uid));
        PowerLoomService service = new PowerLoomService(access, context.logger());
        // any access to a loom (a click, a neighbour update such as an axle placed beside it) looks at it
        devices.hook(PowerLoomDevice.ID, new DeviceService.AccessHook() {
            @Override
            public void after(Block block) {
                service.watch(new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
            }
        });
        blocks.onChunkLoad(chunk -> index.keys(chunk).forEach(service::watch));
        blocks.onChunkUnload(chunk -> index.keys(chunk).forEach(service::drop));
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
