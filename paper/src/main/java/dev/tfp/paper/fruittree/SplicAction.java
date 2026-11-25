/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (useItemOn, maySplice)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.FruitTree;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Optional;
import org.bukkit.block.Block;

/**
 * PAR-FARM-28: splicing a sapling. TFC's {@code FruitTreeSaplingBlock.useItemOn}: the main hand holds that tree's sapling item, the off
 * hand a knife, and {@code maySplice} holds; the block's {@code saplings} goes up by one and the item is used up (not in creative).
 * The hand checks and the item are the listener's; this is the block part. Region thread of the block.
 */
public final class SplicAction {
    private SplicAction() {
    }

    /** What {@link #splice} found. */
    public enum Result { SPLICED, NOT_A_SAPLING, OTHER_TREE, FULL }

    /** Whether a cell is a fruit tree branch (a growing or a static branch). */
    static boolean branch(FruitTreeEffects fx, Block block) {
        return fx.blockAt(block).flatMap(FruitTreeDevice::parse)
            .map(p -> p.part() == FruitTreeDevice.Part.BRANCH || p.part() == FruitTreeDevice.Part.GROWING).orElse(false);
    }

    /** The {@code maySplice} of a position: the sapling there, if any, and the branch below. */
    public static boolean maySplice(FruitTreeEffects fx, Block block) {
        Optional<FruitTreeDevice.Parsed> p = fx.blockAt(block).flatMap(FruitTreeDevice::parse)
            .filter(x -> x.part() == FruitTreeDevice.Part.SAPLING);
        return SplicRules.maySplice(p.isPresent(), p.map(x -> x.intProp("saplings", 1)).orElse(0), branch(fx, block.getRelative(0, -1, 0)),
            fx.isAir(block));
    }

    /**
     * Raises the saplings count of the sapling block of {@code tree} at {@code block} by one when it may be spliced.
     *
     * @param tree the tree of the sapling item in the main hand
     */
    public static Result splice(FruitTreeEffects fx, Block block, FruitTree tree) {
        Optional<FruitTreeDevice.Parsed> p = fx.blockAt(block).flatMap(FruitTreeDevice::parse)
            .filter(x -> x.part() == FruitTreeDevice.Part.SAPLING);
        if (p.isEmpty()) {
            return Result.NOT_A_SAPLING;
        }
        if (p.get().tree() != tree) {
            return Result.OTHER_TREE;
        }
        if (!maySplice(fx, block)) {
            return Result.FULL;
        }
        BlockStateRef next = FruitTreeDevice.sapling(tree, p.get().intProp("saplings", 1) + 1);
        fx.show(block, next, null);
        return Result.SPLICED;
    }
}
