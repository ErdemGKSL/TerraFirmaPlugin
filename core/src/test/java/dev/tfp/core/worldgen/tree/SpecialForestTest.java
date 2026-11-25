/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.tree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-11: the dead forest of the lowlands, the mangrove forest of the salt marshes and the mangrove's special root
 * placer. The forest loop and the tree features are the golden-tested ones ({@link TreeGoldenTest}); the special root placer
 * has no oracle golden (the 1.21.11 stub harness of TreeGoldenTest is not kept), so its draws are checked against the rules
 * read from {@code SpecialRootPlacer} at c3b4d9536: the base rise, the five fixed positions and the replace rules.
 */
class SpecialForestTest {
    private static final long[] SEEDS = {0L, 1L, -99L, 0x5DEECE66DL};

    /** The test view with one biome everywhere. */
    private static TreeView biome(int biome) {
        return new TreeTestView() {
            @Override
            public int biome(int x, int z) {
                return biome;
            }
        };
    }

    private static List<ForestEntry> dead() {
        return TreeData.entries(TreeTestData.reader(), TreeData.DEAD_FOREST_TAG);
    }

    private static List<ForestEntry> mangrove() {
        return TreeData.entries(TreeTestData.reader(), TreeData.MANGROVE_FOREST_TAG);
    }

    private static List<Tree> trees(Forest forest, TreeView view) {
        final List<Tree> out = new ArrayList<>();
        for (int cx = -6; cx < 6; cx++) {
            for (int cz = -6; cz < 6; cz++) {
                out.addAll(forest.chunk(cx, cz, view));
            }
        }
        return out;
    }

    @Test
    void deadAndMangroveTagsLoad() {
        final List<ForestEntry> dead = dead();
        assertThat(dead).hasSize(19);
        assertThat(dead.getFirst().name()).isEqualTo("tfp:tree/dead_acacia_entry");
        assertThat(dead).allSatisfy(e -> assertThat(e.deadChance()).isEqualTo(1));
        final List<ForestEntry> mangrove = mangrove();
        assertThat(mangrove).hasSize(1);
        final ForestEntry m = mangrove.getFirst();
        assertThat(m.floating()).isTrue();
        final TreeFeature.Roots roots = ((TreeFeature.RandomTree) m.normalTree()).rootSystem().orElseThrow();
        assertThat(roots.special()).isTrue();
        assertThat(roots.skewChance()).isEqualTo(0.2f);
        assertThat(roots.width()).isEqualTo(15);
        assertThat(roots.height()).isEqualTo(8);
        assertThat(roots.tries()).isEqualTo(1);
        assertThat(roots.required()).isTrue();
        assertThat(((TreeFeature.RandomTree) m.deadTree()).rootSystem().orElseThrow().special()).isFalse();
        // the dead mangrove entry is in no tag (TFC never places it), but it loads
        assertThat(TreeData.entry(TreeTestData.reader(), "tfp:tree/dead_mangrove_entry").deadChance()).isEqualTo(1);
    }

    /** The lowlands run the forest and then the dead forest: the forest's trees come first, unchanged, then no normal trees. */
    @Test
    void lowlandsAddADeadForestAfterTheForest() {
        final TreeView view = biome(TfcBiomes.LOWLANDS);
        int deadTrees = 0;
        for (long seed : SEEDS) {
            final Forest plain = new Forest(seed, TreeTestData.entries());
            final Forest full = new Forest(seed, TreeTestData.entries(), dead(), mangrove());
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    final List<Tree> first = plain.chunk(cx, cz, view);
                    final List<Tree> all = full.chunk(cx, cz, view);
                    assertThat(all.subList(0, first.size())).isEqualTo(first);
                    for (Tree t : all.subList(first.size(), all.size())) {
                        // dead_chance 1: never a normal tree (krummholz and old growth are drawn first, as in TFC)
                        assertThat(t.variant()).isNotEqualTo(Tree.Variant.NORMAL);
                        if (t.variant() == Tree.Variant.DEAD) {
                            deadTrees++;
                        }
                    }
                }
            }
        }
        assertThat(deadTrees).isPositive();
        // other forest biomes have no dead forest, the mangrove biome no forest
        assertThat(trees(new Forest(5L, TreeTestData.entries(), dead(), mangrove()), biome(TfcBiomes.PLAINS)))
            .isEqualTo(trees(new Forest(5L, TreeTestData.entries()), biome(TfcBiomes.PLAINS)));
    }

    @Test
    void saltMarshesGrowMangrovesWithRoots() {
        final TreeView view = biome(TfcBiomes.SALT_MARSH);
        assertThat(trees(new Forest(5L, TreeTestData.entries()), view)).isEmpty();
        final Set<TreeBlock.Kind> kinds = EnumSet.noneOf(TreeBlock.Kind.class);
        int count = 0;
        for (long seed : SEEDS) {
            final Forest forest = new Forest(seed, TreeTestData.entries(), dead(), mangrove());
            for (int cx = -6; cx < 6; cx++) {
                for (int cz = -6; cz < 6; cz++) {
                    for (Tree tree : forest.chunk(cx, cz, view)) {
                        assertThat(tree.wood()).isEqualTo(Wood.MANGROVE);
                        count++;
                        for (TreeBlock b : tree.blocks()) {
                            kinds.add(b.kind());
                            // Forest.place's rule: a tree stays inside its chunk +- 16 blocks
                            assertThat((tree.x() + b.dx()) >> 4).isBetween(cx - 1, cx + 1);
                            assertThat((tree.z() + b.dz()) >> 4).isBetween(cz - 1, cz + 1);
                        }
                    }
                }
            }
        }
        assertThat(count).isPositive();
        assertThat(kinds).contains(TreeBlock.Kind.TREE_ROOTS, TreeBlock.Kind.LOG, TreeBlock.Kind.LEAVES);
        assertThat(kinds).doesNotContain(TreeBlock.Kind.KRUMMHOLZ);
    }

    /** {@code SpecialRootPlacer.placeRoots} over water on mud: the base rises 0 to 3, and its five fixed roots are set. */
    @Test
    void specialRootPlacerFollowsTfc() {
        final TreeFeature.Roots roots = ((TreeFeature.RandomTree) mangrove().getFirst().normalTree()).rootSystem().orElseThrow();
        // mud at y <= 60, water up to 63, air above
        final TreeView flat = new TreeTestView() {
            @Override
            public TreeGround ground(int x, int y, int z) {
                return y <= 60 ? TreeGround.MUD : y <= 63 ? TreeGround.WATER : TreeGround.AIR;
            }
        };
        final Set<Integer> rises = new HashSet<>();
        for (int seed = 0; seed < 64; seed++) {
            final TreeWorld world = new TreeWorld(flat);
            world.begin(0, 64, 0, Wood.MANGROVE);
            final XoroshiroRandomSource random = new XoroshiroRandomSource(seed);
            final int base = TreePlacer.placeRoots(world, 0, 64, 0, roots, random);
            final List<TreeBlock> blocks = world.end();
            rises.add(base - 64);
            final Set<String> at = new HashSet<>();
            for (TreeBlock b : blocks) {
                at.add(b.dx() + "," + b.dy() + "," + b.dz());
                assertThat(b.force()).isTrue();
                // air and water take tree roots, mud muddy roots; nothing else is listed
                final TreeGround was = flat.ground(b.dx(), 64 + b.dy(), b.dz());
                assertThat(b.kind()).isEqualTo(was == TreeGround.MUD ? TreeBlock.Kind.MUDDY_ROOTS : TreeBlock.Kind.TREE_ROOTS);
                // roots only go down or along their direction, never past the placer width
                assertThat(b.dy()).isLessThanOrEqualTo(base - 64);
                assertThat(Math.abs(b.dx()) + Math.abs(b.dz())).isLessThanOrEqualTo(roots.width());
            }
            final int r = base - 64;
            assertThat(at).contains("0," + (r - 1) + ",0", "0," + r + ",-1", "1," + r + ",0", "0," + r + ",1", "-1," + r + ",0");
            // each position is set once: a placed root is not air, fluid or a listed ground kind
            assertThat(at).hasSameSizeAs(blocks);
            // the same draws give the same roots
            final TreeWorld again = new TreeWorld(flat);
            again.begin(0, 64, 0, Wood.MANGROVE);
            assertThat(TreePlacer.placeRoots(again, 0, 64, 0, roots, new XoroshiroRandomSource(seed))).isEqualTo(base);
            assertThat(again.end()).isEqualTo(blocks);
        }
        assertThat(rises).containsExactlyInAnyOrder(0, 1, 2, 3);
    }
}
