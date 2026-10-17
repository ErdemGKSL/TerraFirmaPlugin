/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.tree.Forest;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.Wood;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * The blocks of the trees (M2-GEN-12). The CraftEngine wood blocks (logs, wood, leaves, krummholz, rooted dirt) do not exist
 * yet, so each TFC wood gets a vanilla wood family as a placeholder (ledger): its log, wood and leaves, the leaves standing in
 * for krummholz too, and vanilla rooted dirt and muddy mangrove roots for the roots. The placement rule is pure: {@link #write}
 * applies the tree decisions of one chunk through a {@link Target}.
 */
final class TreeBlocks {
    /** The vanilla wood family standing in for each TFC wood (a placeholder only: the closest bark and leaf colours). */
    private static final Map<Wood, String> FAMILY = new EnumMap<>(Map.ofEntries(
        Map.entry(Wood.ACACIA, "ACACIA"), Map.entry(Wood.ASH, "OAK"), Map.entry(Wood.ASPEN, "BIRCH"),
        Map.entry(Wood.BIRCH, "BIRCH"), Map.entry(Wood.BLACKWOOD, "DARK_OAK"), Map.entry(Wood.CHESTNUT, "DARK_OAK"),
        Map.entry(Wood.DOUGLAS_FIR, "SPRUCE"), Map.entry(Wood.HICKORY, "OAK"), Map.entry(Wood.KAPOK, "JUNGLE"),
        Map.entry(Wood.MANGROVE, "MANGROVE"), Map.entry(Wood.MAPLE, "CHERRY"), Map.entry(Wood.OAK, "OAK"),
        Map.entry(Wood.PALM, "JUNGLE"), Map.entry(Wood.PINE, "SPRUCE"), Map.entry(Wood.ROSEWOOD, "JUNGLE"),
        Map.entry(Wood.SEQUOIA, "MANGROVE"), Map.entry(Wood.SPRUCE, "SPRUCE"), Map.entry(Wood.SYCAMORE, "OAK"),
        Map.entry(Wood.WHITE_CEDAR, "BIRCH"), Map.entry(Wood.WILLOW, "MANGROVE")));

    private TreeBlocks() {}

    /** The vanilla placeholder of a tree block. */
    static Material placeholder(TreeBlock block) {
        final String family = FAMILY.get(block.wood());
        return switch (block.kind()) {
            case LOG -> Material.valueOf(family + "_LOG");
            case WOOD -> Material.valueOf(family + "_WOOD");
            case LEAVES, KRUMMHOLZ -> Material.valueOf(family + "_LEAVES");
            case ROOTED_DIRT -> Material.ROOTED_DIRT;
            case MUDDY_ROOTS -> Material.MUDDY_MANGROVE_ROOTS;
        };
    }

    /** The blocks of the generating chunk, by local x and z. */
    interface Target {
        /** The block at a position of the chunk. */
        Material type(int localX, int y, int localZ);

        void place(int localX, int y, int localZ, TreeBlock block);
    }

    /**
     * Writes the tree blocks of one chunk in order. A block outside the chunk or its heights is skipped. A block TFC sets
     * whatever is there (trunk, roots, krummholz: {@link TreeBlock#force()}) is always written; a template block only on air,
     * water or leaves ({@code TreeHelpers.placeTemplate}), re-validated here against the chunk as it is now, so an earlier
     * feature's block or a neighbouring chunk's trunk is kept.
     *
     * @return the number of blocks written
     */
    static int write(List<Forest.Placed> blocks, int chunkX, int chunkZ, int minY, int maxY, Target target) {
        int written = 0;
        for (Forest.Placed p : blocks) {
            if (p.x() >> 4 != chunkX || p.z() >> 4 != chunkZ || p.y() <= minY || p.y() >= maxY) {
                continue;
            }
            final int lx = p.x() & 15;
            final int lz = p.z() & 15;
            if (p.block().force() || replaceable(target.type(lx, p.y(), lz))) {
                target.place(lx, p.y(), lz, p.block());
                written++;
            }
        }
        return written;
    }

    /** {@code isWorldgenReplaceable || minecraft:leaves} over the generating chunk's vanilla blocks. */
    static boolean replaceable(Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.WATER || type.name().endsWith("_LEAVES");
    }
}
