/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (building the firepit)
 */
package dev.tfp.paper.fire;

import java.util.List;
import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Builds a lit firepit where a fire starter found sticks and logs on the ground. The firepit block is PAR-HEAT-06's; it
 * installs its factory with {@link FireStarting#firepitFactory}. Without a factory no firepit can stand anywhere and a
 * strong start places a fire block (TFC's fallback when {@code FirepitBlock.canSurvive} fails). Region thread of the
 * block.
 */
public interface FirepitFactory {
    /** TFC {@code FirepitBlock.canSurvive(level, pos)}: the position can hold a firepit. */
    boolean canSurvive(Block at);

    /**
     * Places a firepit at {@code at} (the axis is the player's horizontal facing, X without a player), puts {@code logs}
     * (one item each, at most four) into fuel slots 0.. and lights it. The ground items are already removed.
     *
     * @param player the player, or {@code null}
     * @return whether the firepit stands (TFC returns {@code true} after {@code setBlock} either way)
     */
    boolean build(Block at, Axis axis, List<ItemStack> logs, Player player);
}
