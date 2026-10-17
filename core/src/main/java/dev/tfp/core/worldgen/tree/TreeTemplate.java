/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from Minecraft 1.21's StructureTemplate (loadPalette, addToLists, buildInfoList: the block order), as TFC's tree
 * features use it at TerraFirmaCraft c3b4d9536 (src/main/java/net/dries007/tfc/world/feature/tree/TreeHelpers.java placeTemplate)
 */
package dev.tfp.core.worldgen.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One of TFC's tree structure templates ({@code tfp:<tree>/<n>}): its size and its blocks in the order vanilla's template
 * loader keeps them, which is the order the blocks are placed and the overlay's integrity draws are made. Immutable.
 *
 * @param name   the template id, e.g. {@code tfp:oak/1}
 * @param sizeX  the size in x
 * @param sizeY  the size in y
 * @param sizeZ  the size in z
 * @param blocks the blocks, in vanilla order
 */
public record TreeTemplate(String name, int sizeX, int sizeY, int sizeZ, List<Block> blocks) {
    /**
     * A template block at its position inside the template.
     *
     * @param kind   {@link TreeBlock.Kind#LOG}, {@link TreeBlock.Kind#WOOD} or {@link TreeBlock.Kind#LEAVES}
     * @param axis   the log axis before the rotation
     * @param branch the branch direction before the mirror and rotation
     */
    public record Block(int x, int y, int z, TreeBlock.Kind kind, Wood wood, TreeBlock.Axis axis, BranchDirection branch) {}

    public TreeTemplate {
        blocks = List.copyOf(blocks);
    }

    /**
     * The template with its blocks (in file order) put in vanilla's order: the full blocks (logs and wood) sorted by y, x, z,
     * then the blocks with another collision shape (TFC's leaves have none) sorted the same way. No tree block has block
     * entity data, vanilla's third list.
     */
    public static TreeTemplate of(String name, int sizeX, int sizeY, int sizeZ, List<Block> fileOrder) {
        final Comparator<Block> order = Comparator.<Block>comparingInt(Block::y).thenComparingInt(Block::x).thenComparingInt(Block::z);
        final List<Block> full = new ArrayList<>();
        final List<Block> other = new ArrayList<>();
        for (Block b : fileOrder) {
            (b.kind().isLog() ? full : other).add(b);
        }
        full.sort(order);
        other.sort(order);
        final List<Block> out = new ArrayList<>(full);
        out.addAll(other);
        return new TreeTemplate(name, sizeX, sizeY, sizeZ, out);
    }
}
