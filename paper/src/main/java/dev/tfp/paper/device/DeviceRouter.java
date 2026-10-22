/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device;

import dev.tfp.core.persist.DataMap;
import java.util.Optional;
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

        @Override
        public Optional<DataMap> created(String blockId) {
            return Optional.empty();
        }
    };

    /**
     * A device block entity was created or loaded without any stored record (placement by a player, a command, the
     * CraftEngine API or worldgen). Returns the record map to store at once: a fresh record of the block's kind with
     * {@code lastUpdateTick} stamped now, so the device's time starts when its block entity does (TFC: the block entity
     * ticks from its creation). Empty when {@code blockId} is no device kind. Touches no world; any thread.
     */
    Optional<DataMap> created(String blockId);

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
