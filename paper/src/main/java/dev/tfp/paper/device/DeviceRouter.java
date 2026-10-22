/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.persist.DataMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Where the {@code tfp:device} CraftEngine behaviour sends its callbacks (PAR-INFRA-02), in Bukkit terms. Implemented by
 * {@link DeviceService}; reached through {@link DeviceBridge}. Every method runs on the block's region thread.
 */
public interface DeviceRouter {
    /** Before the device module is enabled (or after it is disabled): nothing is handled. */
    DeviceRouter NONE = new DeviceRouter() {
        @Override
        public boolean use(Block block, Player player, EquipmentSlot hand, ItemStack item) {
            return false;
        }

        @Override
        public void neighbourChanged(Block block) {
        }

        @Override
        public void removed(World world, int x, int y, int z, String blockId, DataMap data) {
        }
    };

    /** A right click on the device. @return true if a handler consumed it (the click is cancelled) */
    boolean use(Block block, Player player, EquipmentSlot hand, ItemStack item);

    /** A neighbour of the device changed. */
    void neighbourChanged(Block block);

    /**
     * The device's block entity was removed with its block. {@code data} is its last stored record map (empty when it
     * was never written).
     */
    void removed(World world, int x, int y, int z, String blockId, DataMap data);
}
