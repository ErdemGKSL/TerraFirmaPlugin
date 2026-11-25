/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern.powered;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.quern.QuernDevice;
import dev.tfp.paper.quern.powered.PoweredQuernAccess.Key;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.rotation.RotationBlockStates;
import dev.tfp.paper.rotation.RotationService;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * PAR-MECH-06: querns driven by the rotation network. A quern with an axle-like block above it (TFC's {@code SinkNode(pos, UP)})
 * is watched ({@link PoweredQuernService}): it starts its grind by itself when it has input and a handstone, runs it at the
 * network speed and starts the next at once. Hoppers feed and empty it through the device automation (PAR-INFRA-60). Needs the
 * quern, device and rotation modules before it in the service file.
 */
public final class PoweredQuernModule implements FeatureModule {
    public static final String ID = "quern_powered";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Recipes recipes = context.services().require(Recipes.class);
        Optional<RotationService> networks = context.services().get(RotationService.class);
        if (networks.isEmpty()) {
            context.logger().warning("Powered quern: the rotation module is not running; querns stay hand-driven");
            return;
        }
        CustomBlocks blocks = context.integrations().customBlocks();
        PoweredQuernIndex index = new PoweredQuernIndex(context.chunkData());
        PaperPoweredAccess access = new PaperPoweredAccess(context.scheduler(), devices, blocks, networks.get(), recipes, index,
            uid -> context.plugin().getServer().getWorld(uid));
        PoweredQuernService service = new PoweredQuernService(access, context.logger());
        // any access to a quern (a click, a neighbour update such as an axle placed above, a hopper-side update) looks at it
        devices.hook(QuernDevice.ID, new DeviceService.AccessHook() {
            @Override
            public void after(Block block) {
                if (hasRotationAbove(blocks, block)) {
                    service.watch(new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
                }
            }
        });
        blocks.onChunkLoad(chunk -> index.keys(chunk).forEach(service::watch));
        blocks.onChunkUnload(chunk -> index.keys(chunk).forEach(service::drop));
    }

    /** Whether the block above is one of the rotation network's blocks (same chunk column, so the same region). */
    static boolean hasRotationAbove(CustomBlocks blocks, Block quern) {
        Optional<BlockStateRef> above = blocks.stateAt(quern.getRelative(0, 1, 0));
        return above.isPresent() && RotationBlockStates.block(above.get().id()).isPresent();
    }
}
