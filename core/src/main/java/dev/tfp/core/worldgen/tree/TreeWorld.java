/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The world one forest decision sees: the terrain of a {@link TreeView} with the blocks of the forest's earlier trees laid
 * over it, and the log of every block set, in order. Confined to the decision that owns it.
 */
final class TreeWorld {
    private final TreeView view;
    private final Map<Long, TreeGround> placed = new HashMap<>();
    private List<TreeBlock> log;
    private int rootX;
    private int rootY;
    private int rootZ;
    private Wood wood;

    TreeWorld(TreeView view) {
        this.view = view;
    }

    TreeView view() {
        return view;
    }

    TreeGround get(int x, int y, int z) {
        final TreeGround g = placed.get(key(x, y, z));
        return g != null ? g : view.ground(x, y, z);
    }

    /** Starts recording the blocks of one tree, relative to its root. */
    void begin(int x, int y, int z, Wood wood) {
        this.rootX = x;
        this.rootY = y;
        this.rootZ = z;
        this.wood = wood;
        this.log = new ArrayList<>();
    }

    /** The blocks the current tree set, in order. */
    List<TreeBlock> end() {
        final List<TreeBlock> out = log;
        log = null;
        return out;
    }

    /** {@code level.setBlock} of a block of the current tree's wood (roots, krummholz). */
    void set(int x, int y, int z, TreeBlock.Kind kind, TreeBlock.Axis axis, BranchDirection branch, boolean force) {
        set(x, y, z, kind, wood, axis, branch, force);
    }

    /** {@code level.setBlock}: records the block (of {@code blockWood}: a template's or trunk's own) and lays it over the terrain. */
    void set(int x, int y, int z, TreeBlock.Kind kind, Wood blockWood, TreeBlock.Axis axis, BranchDirection branch, boolean force) {
        placed.put(key(x, y, z), switch (kind) {
            case LOG, WOOD -> TreeGround.LOG;
            case LEAVES -> TreeGround.LEAVES;
            case ROOTED_DIRT -> TreeGround.ROOTED_DIRT;
            case MUDDY_ROOTS -> TreeGround.MUDDY_ROOTS;
            case KRUMMHOLZ -> TreeGround.KRUMMHOLZ;
        });
        log.add(new TreeBlock(x - rootX, y - rootY, z - rootZ, kind, blockWood, axis, branch, force));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
