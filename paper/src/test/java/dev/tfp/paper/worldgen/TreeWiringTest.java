/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.tree.BranchDirection;
import dev.tfp.core.worldgen.tree.Forest;
import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.Wood;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-12: the generator's tree blocks, and the rule that writes them into one chunk. */
class TreeWiringTest {
    private static TreeBlock block(int dx, int dy, int dz, TreeBlock.Kind kind, boolean force) {
        return new TreeBlock(dx, dy, dz, kind, Wood.OAK, TreeBlock.Axis.Y, BranchDirection.NONE, force);
    }

    @Test
    void everyWoodAndKindHasAPlaceholder() {
        for (Wood wood : Wood.values()) {
            for (TreeBlock.Kind kind : TreeBlock.Kind.values()) {
                final Material m = TreeBlocks.placeholder(new TreeBlock(0, 0, 0, kind, wood, TreeBlock.Axis.Y, BranchDirection.NONE, true));
                assertThat(m).as(wood + " " + kind).isNotNull();
                assertThat(m.isBlock()).isTrue();
                if (kind == TreeBlock.Kind.LEAVES) {
                    assertThat(TreeBlocks.replaceable(m)).isTrue();
                }
            }
        }
    }

    @Test
    void writesOnlyItsChunkAndKeepsTakenPositionsForTemplateBlocks() {
        final Map<String, Material> world = new HashMap<>();
        world.put("0,70,0", Material.STONE);   // ground under the trunk
        world.put("1,72,0", Material.STONE);   // something an earlier feature put there
        world.put("2,72,0", Material.OAK_LEAVES);
        final TreeBlocks.Target target = new TreeBlocks.Target() {
            @Override
            public Material type(int localX, int y, int localZ) {
                return world.getOrDefault(localX + "," + y + "," + localZ, Material.AIR);
            }

            @Override
            public void place(int localX, int y, int localZ, TreeBlock block) {
                assertThat(localX).isBetween(0, 15);
                assertThat(localZ).isBetween(0, 15);
                world.put(localX + "," + y + "," + localZ, TreeBlocks.placeholder(block));
            }
        };
        // a tree rooted at (16, 71, 0): chunk (1, 0); written into chunk (1, 0)
        final Tree tree = new Tree(16, 71, 0, Wood.OAK, Tree.Variant.NORMAL, List.of(
            block(0, -1, 0, TreeBlock.Kind.ROOTED_DIRT, true),  // forced over the stone
            block(0, 0, 0, TreeBlock.Kind.LOG, true),
            block(1, 1, 0, TreeBlock.Kind.LEAVES, false),       // taken: stays stone
            block(2, 1, 0, TreeBlock.Kind.LEAVES, false),       // leaves replace leaves
            block(3, 1, 0, TreeBlock.Kind.LEAVES, false),
            block(-1, 1, 0, TreeBlock.Kind.LEAVES, false),      // chunk (0, 0)
            block(0, 400, 0, TreeBlock.Kind.LEAVES, false)));   // above the world
        final List<Forest.Placed> placed = tree.blocks().stream().map(b -> new Forest.Placed(tree, b)).toList();
        final int written = TreeBlocks.write(placed, 1, 0, -64, 320, target);
        assertThat(written).isEqualTo(4);
        assertThat(world.get("0,70,0")).isEqualTo(Material.ROOTED_DIRT);
        assertThat(world.get("0,71,0")).isEqualTo(Material.OAK_LOG);
        assertThat(world.get("1,72,0")).isEqualTo(Material.STONE);
        assertThat(world.get("2,72,0")).isEqualTo(Material.OAK_LEAVES);
        assertThat(world.get("3,72,0")).isEqualTo(Material.OAK_LEAVES);
        assertThat(world.get("15,72,0")).isNull();
    }
}
