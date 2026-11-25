/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (maySplice)
 */
package dev.tfp.paper.fruittree;

/** PAR-FARM-28: the pure rule of knife splicing. */
public final class SplicRules {
    /** A sapling on a branch holds at most this many saplings ({@code saplings < 3}), one on the ground one more. */
    public static final int MAX_ON_BRANCH = 3;
    public static final int MAX_ON_GROUND = 4;

    private SplicRules() {
    }

    /**
     * {@code FruitTreeSaplingBlock.maySplice}: with a sapling at the position, it may take another while it holds fewer than three
     * (on a branch) or four (on the ground); on an empty cell, only above a branch (a fresh sapling is then placed there).
     *
     * @param sapling       whether the position holds a fruit tree sapling
     * @param saplings      the sapling's {@code saplings} count (ignored without a sapling)
     * @param branchBelow   whether the block below is a fruit tree branch ({@code tfc:fruit_tree_branch})
     * @param air           whether the position is air
     */
    public static boolean maySplice(boolean sapling, int saplings, boolean branchBelow, boolean air) {
        if (sapling) {
            return branchBelow ? saplings < MAX_ON_BRANCH : saplings < MAX_ON_GROUND;
        }
        return branchBelow && air;
    }
}
