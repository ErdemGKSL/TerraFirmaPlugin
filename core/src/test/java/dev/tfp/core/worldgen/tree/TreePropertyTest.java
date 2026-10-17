/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.tree.BranchDirection.Mirror;
import dev.tfp.core.worldgen.tree.BranchDirection.Rotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Properties of the tree decision: determinism, TFC's shape bounds, the wood kinds, and the chunk-boundary rule. */
class TreePropertyTest {
    private static final TreeView VIEW = new TreeTestView();
    private static final long[] SEEDS = {0L, 1L, -99L, 0x5DEECE66DL};

    /** The trees of chunks x, z in [-6, 5] for a seed (every column of the test view is a forest biome). */
    private static List<Tree> trees(Forest forest) {
        final List<Tree> out = new ArrayList<>();
        for (int cx = -6; cx < 6; cx++) {
            for (int cz = -6; cz < 6; cz++) {
                out.addAll(forest.chunk(cx, cz, VIEW));
            }
        }
        return out;
    }

    @Test
    void decisionsAreDeterministicPerSeed() {
        for (long seed : SEEDS) {
            final List<Tree> a = trees(new Forest(seed, TreeTestData.entries()));
            final List<Tree> b = trees(new Forest(seed, TreeTestData.entries()));
            assertThat(a).isNotEmpty().isEqualTo(b);
        }
        assertThat(trees(new Forest(1L, TreeTestData.entries()))).isNotEqualTo(trees(new Forest(2L, TreeTestData.entries())));
    }

    @Test
    void decisionsAreTheSameOnManyThreads() {
        final Forest forest = new Forest(7L, TreeTestData.entries());
        final List<List<Tree>> serial = IntStream.range(0, 64).mapToObj(i -> forest.chunk(i % 8 - 4, i / 8 - 4, VIEW)).toList();
        final List<List<Tree>> parallel = IntStream.range(0, 64).parallel().mapToObj(i -> forest.chunk(i % 8 - 4, i / 8 - 4, VIEW)).toList();
        assertThat(parallel).isEqualTo(serial);
    }

    /**
     * TFC's bounds: a tree's template blocks are clipped to its root chunk +-16 blocks ({@code TreeHelpers.getPlacementSettings});
     * the decision never needs that clip, so every block lies inside it, which is also what lets {@link Forest#place} read only
     * the 3 x 3 chunks around a chunk. Roots go at most their {@code height} (6 at most in the forest data) below the root.
     */
    @Test
    void placementsStayInsideTfcBounds() {
        int count = 0;
        for (long seed : SEEDS) {
            for (Tree tree : trees(new Forest(seed, TreeTestData.entries()))) {
                final int minX = (tree.x() >> 4 << 4) - 16;
                final int minZ = (tree.z() >> 4 << 4) - 16;
                assertThat(tree.blocks()).isNotEmpty();
                for (TreeBlock b : tree.blocks()) {
                    assertThat(tree.x() + b.dx()).isBetween(minX, minX + 47);
                    assertThat(tree.z() + b.dz()).isBetween(minZ, minZ + 47);
                    assertThat(b.dy()).isBetween(-6, 64);
                    count++;
                }
            }
        }
        assertThat(count).isGreaterThan(10_000);
    }

    @Test
    void templatesFitTheBoundsForAnyRotation() {
        final Set<TreeTemplate> templates = new HashSet<>();
        for (ForestEntry entry : TreeTestData.entries()) {
            for (TreeFeature f : List.of(entry.normalTree(), entry.deadTree(), entry.oldGrowthFeature())) {
                switch (f) {
                    case TreeFeature.RandomTree t -> templates.addAll(t.structures());
                    case TreeFeature.OverlayTree t -> templates.addAll(List.of(t.base(), t.overlay()));
                    case TreeFeature.StackedTree t -> t.layers().forEach(l -> templates.addAll(l.templates()));
                    case TreeFeature.Krummholz k -> { }
                }
            }
        }
        assertThat(templates).hasSizeGreaterThan(100);
        for (TreeTemplate t : templates) {
            // the centre ((size - 1) / 2) goes on the trunk top, so a block is at most size / 2 from it, plus 1 for a wide trunk
            assertThat(Math.max(t.sizeX(), t.sizeZ()) / 2 + 1).as(t.name()).isLessThanOrEqualTo(16);
        }
    }

    @Test
    void woodKindsAreTheTreesOwn() {
        final Set<Wood> seen = new HashSet<>();
        for (long seed : SEEDS) {
            for (Tree tree : trees(new Forest(seed, TreeTestData.entries()))) {
                assertThat(tree.wood()).isIn((Object[]) Wood.values());
                seen.add(tree.wood());
                for (TreeBlock b : tree.blocks()) {
                    assertThat(b.wood()).isEqualTo(tree.wood());
                    if (!b.kind().isLog()) {
                        assertThat(b.axis()).isEqualTo(TreeBlock.Axis.Y);
                        assertThat(b.branch()).isEqualTo(BranchDirection.NONE);
                    }
                    if (b.kind() == TreeBlock.Kind.KRUMMHOLZ) {
                        assertThat(tree.variant()).isEqualTo(Tree.Variant.KRUMMHOLZ);
                    }
                }
            }
        }
        assertThat(seen).hasSizeGreaterThan(10);
    }

    /** {@link Forest#place} gives each chunk exactly the tree blocks inside it, from its own and the 8 neighbouring chunks' trees. */
    @Test
    void placeKeepsTheBlocksInsideTheChunk() {
        final Forest forest = new Forest(3L, TreeTestData.entries());
        final Set<String> expected = new HashSet<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                for (Tree tree : forest.chunk(cx, cz, VIEW)) {
                    for (TreeBlock b : tree.blocks()) {
                        final int x = tree.x() + b.dx();
                        final int z = tree.z() + b.dz();
                        if (x >> 4 >= -2 && x >> 4 <= 2 && z >> 4 >= -2 && z >> 4 <= 2) {
                            expected.add(x + "," + (tree.y() + b.dy()) + "," + z + "," + b.kind() + "@" + tree.x() + "," + tree.z());
                        }
                    }
                }
            }
        }
        final Set<String> placed = new HashSet<>();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                final int chunkX = cx;
                final int chunkZ = cz;
                forest.place(cx, cz, VIEW, p -> {
                    assertThat(p.x() >> 4).isEqualTo(chunkX);
                    assertThat(p.z() >> 4).isEqualTo(chunkZ);
                    placed.add(p.x() + "," + p.y() + "," + p.z() + "," + p.block().kind() + "@" + p.tree().x() + "," + p.tree().z());
                });
            }
        }
        assertThat(placed).isNotEmpty().isEqualTo(expected);
    }

    @Test
    void forestBiomesFollowTheLargeFeatureTags() {
        assertThat(Forest.decorates(TfcBiomes.PLAINS)).isTrue();
        assertThat(Forest.decorates(TfcBiomes.LOWLANDS)).isTrue();
        assertThat(Forest.decorates(TfcBiomes.OCEAN)).isFalse();
        assertThat(Forest.decorates(TfcBiomes.SALT_MARSH)).isFalse();
        assertThat(IntStream.range(0, TfcBiomes.COUNT).filter(Forest::decorates).count()).isEqualTo(TfcBiomes.COUNT - Forest.TREELESS_BIOMES.size());
    }

    @Test
    void forestDataLoadsInTagOrder() {
        final List<ForestEntry> entries = TreeTestData.entries();
        assertThat(entries).hasSize(19);
        assertThat(entries.getFirst().name()).isEqualTo("tfp:tree/acacia_entry");
        assertThat(entries.getLast().name()).isEqualTo("tfp:tree/willow_entry");
        assertThat(entries).allSatisfy(e -> assertThat(e.normalTree().wood()).isEqualTo(e.deadTree().wood()));
    }

    @Test
    void branchDirectionTransformsAreGroupActions() {
        for (BranchDirection d : BranchDirection.values()) {
            assertThat(d.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.COUNTERCLOCKWISE_90)).isEqualTo(d);
            assertThat(d.rotate(Rotation.CLOCKWISE_180).rotate(Rotation.CLOCKWISE_180)).isEqualTo(d);
            for (Mirror m : Mirror.values()) {
                assertThat(d.mirror(m).mirror(m)).isEqualTo(d);
            }
        }
    }

    @Test
    void forestLayerIsTheSameOnManyThreads() {
        final ForestLayers layers = new ForestLayers(99L);
        final List<ForestType> serial = IntStream.range(0, 4096).mapToObj(i -> layers.forestType(i % 64 - 32, i / 64 - 32)).toList();
        final List<ForestType> parallel = IntStream.range(0, 4096).parallel().mapToObj(i -> layers.forestType(i % 64 - 32, i / 64 - 32)).toList();
        assertThat(parallel).isEqualTo(serial);
    }
}
