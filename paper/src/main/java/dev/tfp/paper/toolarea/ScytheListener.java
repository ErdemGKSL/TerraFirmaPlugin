/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/ScytheItem.java @ c3b4d9536 (doScytheMining),
 *       src/main/java/net/dries007/tfc/common/items/CreativeMiningTool.java @ c3b4d9536
 */
package dev.tfp.paper.toolarea;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-TOOL-06: TFC's scythe cuts the 3x3x3 cube around the block it breaks ({@code ScytheItem.doScytheMining}): every other
 * block in it that is in {@code tfc:mineable/scythe} (the blocks the scythe is a correct tool for) is destroyed with the
 * scythe's drops, in creative too (without drops). The extra blocks go through a {@link BlockBreakEvent} of the player, so
 * protection plugins and the loot runtime see them, and cost no durability (TFC spends one point, for the origin block).
 * Blocks outside the region the break runs in (Folia) are skipped. A re-entrant guard stops the cuts from cutting again.
 */
public final class ScytheListener implements Listener {
    private final Function<ItemStack, Optional<String>> idOf;
    private final Predicate<Block> mineableWithScythe;
    private boolean cutting;

    /**
     * @param idOf               the custom item id of a stack
     * @param mineableWithScythe whether a block is in {@code tfc:mineable/scythe} (region thread)
     */
    public ScytheListener(Function<ItemStack, Optional<String>> idOf, Predicate<Block> mineableWithScythe) {
        this.idOf = idOf;
        this.mineableWithScythe = mineableWithScythe;
    }

    /** Whether an item id is a TFC scythe ({@code tfp:metal/scythe/<metal>}). */
    public static boolean isScythe(String id) {
        return id != null && id.startsWith("tfp:metal/scythe/");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (cutting) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!idOf.apply(tool).filter(ScytheListener::isScythe).isPresent()) {
            return;
        }
        Block origin = event.getBlock();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        cutting = true;
        try {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block b = origin.getRelative(dx, dy, dz);
                        if (b.getType().isAir() || !Bukkit.isOwnedByCurrentRegion(b) || !mineableWithScythe.test(b)) {
                            continue;
                        }
                        BlockBreakEvent cut = new BlockBreakEvent(b, player);
                        cut.setDropItems(!creative);
                        if (!cut.callEvent()) {
                            continue;
                        }
                        if (cut.isDropItems()) {
                            b.breakNaturally(tool);
                        } else {
                            b.setType(Material.AIR);
                        }
                    }
                }
            }
        } finally {
            cutting = false;
        }
    }
}
