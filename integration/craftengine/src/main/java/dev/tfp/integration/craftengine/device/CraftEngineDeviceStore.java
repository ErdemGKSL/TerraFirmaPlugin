/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.device;

import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceStore;
import java.util.Optional;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.block.Block;

/**
 * {@link DeviceStore} on CraftEngine block entities (PAR-INFRA-02): finds the {@link DeviceController} at a block
 * ({@code getBlockEntityAtIfLoaded}, which creates the block entity of a generated device on first access) and reads
 * or writes its record map. Region thread of the block (CraftEngine's world storage is not safe elsewhere).
 */
public final class CraftEngineDeviceStore implements DeviceStore {
    @Override
    public Optional<DataMap> read(Block block) {
        DeviceController c = controllerAt(block);
        return c == null || c.hasUnreadableData() ? Optional.empty() : Optional.of(c.read());
    }

    @Override
    public boolean write(Block block, DataMap data) {
        DeviceController c = controllerAt(block);
        if (c == null) {
            return false;
        }
        c.write(data);
        return true;
    }

    static DeviceController controllerAt(Block block) {
        CEWorld world = BukkitAdaptor.adapt(block.getWorld()).storageWorld();
        if (world == null) {
            return null;
        }
        BlockEntity be = world.getBlockEntityAtIfLoaded(new BlockPos(block.getX(), block.getY(), block.getZ()));
        if (be == null || !be.isValid() || be.controller == null) {
            return null;
        }
        // first DeviceController of a single or composite controller (a facing/waterlogged block wraps ours)
        return be.controller.get(DeviceController.class, 0);
    }
}
