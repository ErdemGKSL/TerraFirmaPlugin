/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onCropsGrow)
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.worldrules.WorldRules;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;

/**
 * Vanilla bamboo grows a new stalk block only with TFC's {@code plantLongGrowthChance} ({@link WorldRules#bambooGrows}).
 * Paper reports the stalk growing up as a spread from the stalk to the block above (some builds as a grow event of the
 * new block); both are handled, one roll each. The bamboo sapling's first stalk is not a stalk's growth and stays vanilla.
 */
final class BambooListener implements Listener {
    private final Supplier<RandomGenerator> random;

    BambooListener(Supplier<RandomGenerator> random) {
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.BAMBOO && event.getNewState().getType() == Material.BAMBOO
                && !roll()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (event.getNewState().getType() == Material.BAMBOO
                && event.getBlock().getRelative(BlockFace.DOWN).getType() == Material.BAMBOO && !roll()) {
            event.setCancelled(true);
        }
    }

    private boolean roll() {
        return WorldRules.bambooGrows(random.get().nextFloat(), WorldRules.PLANT_LONG_GROWTH_CHANCE);
    }
}
