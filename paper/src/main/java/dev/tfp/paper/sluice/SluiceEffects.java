/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick, getRepresentativeState,
 * getWaterInputPos, getWaterOutputPos, isItemValid),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/SluiceBlock.java @ c3b4d9536 (FACING, UPPER, getFluidOutputPos)
 */
package dev.tfp.paper.sluice;

import dev.tfp.core.pan.SluiceRules;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** What the sluice device reads from and does to the world (PAR-PROC-13). Region thread of the block unless noted. */
public interface SluiceEffects {
    /** TFC {@code getRepresentativeState}: from the fluids at the input and the output. */
    SluiceRules.State flow(Block upper);

    /**
     * TFC {@code INPUT_ONLY} / {@code OUTPUT_ONLY} handling: fills or empties the output water. The change is queued for
     * the region commit and re-validated there ("decide async, apply sync").
     */
    void water(Block upper, SluiceRules.State state);

    /** Rolls the loot of each deposit and spawns the items at the water output. Returns at once. */
    void output(Block upper, List<String> deposits);

    /** Calls the device back after {@code ticks}; one pending callback per sluice. */
    void wake(Block upper, long ticks);

    /** TFC {@code Helpers.playSound(level, pos, ITEM_PICKUP)}: a soil piece was used up. */
    void used(Block upper);

    /**
     * TFC {@code SluiceBlock.setPlacedBy}: the placed block becomes the upper half facing {@code facing} and the lower half
     * is placed one step along it. Queued for the region commit, which checks both blocks again.
     */
    void place(Block upper, BlockStateRef base, BlockFace facing);

    /**
     * TFC {@code updateShape} and {@code onRemove}: a block next to a half changed, so the half checks that its partner is
     * still there; with the partner gone it goes too, and the output water the sluice made is emptied. Queued for the
     * region commit, which checks again. Removal callbacks do not know the block's state, so the check is made from the
     * neighbour update the partner's removal causes.
     */
    void verify(Block block, BlockStateRef state);
}
