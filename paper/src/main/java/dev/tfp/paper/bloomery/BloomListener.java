/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BloomBlock.java @ c3b4d9536 (onDestroyedByPlayer)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * TFC {@code BloomBlock.onDestroyedByPlayer}: a player in survival who breaks a bloom takes one item from it and the block
 * stays until the count is zero; in creative the block is simply removed. Nothing else about the break is changed (an
 * explosion or a piston removes the whole bloom without a drop, as TFC).
 */
public final class BloomListener implements Listener {
    private final CustomBlocks blocks;
    private final DeviceService devices;
    private final BloomDevice device;

    public BloomListener(CustomBlocks blocks, DeviceService devices, BloomDevice device) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.device = Objects.requireNonNull(device, "device");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!blocks.stateAt(block).map(BlockStateRef::id).filter(BloomDevice.ID::equals).isPresent()) {
            return;
        }
        GameMode mode = event.getPlayer().getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            event.setDropItems(false);
            return;
        }
        if (device.dropOne(devices, block)) {
            event.setCancelled(true); // the block stays (or was removed by the device)
        }
    }
}
