/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java @ c3b4d9536 (advanceTree: the
 * {@code TreeGrower} places the wood's configured tree feature at the sapling's position)
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.Optional;

/**
 * Public entry of the tree placer for a grown sapling (PAR-FARM-10): runs one configured tree feature at a position of a live
 * world, with the same checks and draws as the worldgen forest, and returns the blocks it would set. Pure. Lives in this package
 * because {@code TreePlacer} and {@code TreeWorld} are package-private; it adds no behaviour to them.
 */
public final class SaplingTree {
    private SaplingTree() {}

    /**
     * {@code ConfiguredFeature.place} of {@code feature} with the root at {@code (x, y, z)} (the sapling's own position, which the
     * view must already report as air: TFC removes the sapling before it places the tree).
     *
     * @param seed the random seed of this growth
     * @return the tree, or empty when the feature refused the position (the sapling stays)
     */
    public static Optional<Tree> grow(TreeFeature feature, TreeView view, long seed, int x, int y, int z) {
        final TreeWorld world = new TreeWorld(view);
        world.begin(x, y, z, feature.wood());
        final boolean placed = TreePlacer.place(feature, world, new XoroshiroRandomSource(seed), x, y, z);
        final java.util.List<TreeBlock> blocks = world.end();
        return placed ? Optional.of(new Tree(x, y, z, feature.wood(), Tree.Variant.NORMAL, blocks)) : Optional.empty();
    }
}
