/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sapling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.tree.ForestType;
import dev.tfp.core.worldgen.tree.SaplingTree;
import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.TreeData;
import dev.tfp.core.worldgen.tree.TreeFeature;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.TreeView;
import dev.tfp.core.worldgen.tree.Wood;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A grown sapling places its wood's tree feature on a live-like world (PAR-FARM-10), with the datagen output of TFC's tree data. */
class SaplingTreeTest {
    private static Path root;

    /** Flat grass at y 60, air above; a hook can override single positions. */
    private record Flat(BiFunction<int[], TreeGround, TreeGround> override) implements TreeView {
        @Override
        public TreeGround ground(int x, int y, int z) {
            final TreeGround base = y < 60 ? TreeGround.DIRT : y == 60 ? TreeGround.GRASS : TreeGround.AIR;
            return override.apply(new int[] {x, y, z}, base);
        }

        @Override
        public ForestType forestType(int chunkX, int chunkZ) {
            throw new UnsupportedOperationException();
        }

        @Override public int minY() { throw new UnsupportedOperationException(); }
        @Override public int seaLevel() { throw new UnsupportedOperationException(); }
        @Override public int oceanFloor(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public int worldSurface(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public boolean onIce(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public boolean saltWater(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public int surfaceRock(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public int biome(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public float averageTemperature(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public float averageRainfall(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public float groundwater(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public float rainVariance(int x, int z) { throw new UnsupportedOperationException(); }
        @Override public double tideLevel(int x, int z) { throw new UnsupportedOperationException(); }
    }

    @BeforeAll
    static void load() {
        root = Path.of(System.getProperty("tfp.contentData", "../content/generated/content/data"));
        assumeTrue(Files.isRegularFile(root.resolve(TreeData.FOREST_TAG)), "no generated tree data at " + root.toAbsolutePath());
    }

    private static TreeFeature normal(Wood wood) {
        return TreeData.entry(path -> {
            try {
                final Path file = root.resolve(path);
                return Files.isRegularFile(file) ? Optional.of(Files.readString(file)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "tfp:tree/" + wood.serializedName() + "_entry").normalTree();
    }

    @Test
    void everyWoodGrowsItsTreeOnOpenGrass() {
        for (Wood wood : Wood.values()) {
            final TreeFeature feature = normal(wood);
            Optional<Tree> tree = Optional.empty();
            // a feature may refuse a rotation (a wide trunk); the sapling then waits, so allow a few draws as the game would
            for (long seed = 1; seed <= 20 && tree.isEmpty(); seed++) {
                tree = SaplingTree.grow(feature, new Flat((p, g) -> p[0] == 0 && p[1] == 61 && p[2] == 0 ? TreeGround.AIR : g), seed, 0, 61, 0);
            }
            assertThat(tree).as(wood.name()).isPresent();
            assertThat(tree.get().wood()).isEqualTo(wood);
            assertThat(tree.get().blocks()).as(wood.name()).anyMatch(b -> b.kind() == TreeBlock.Kind.LOG);
            assertThat(tree.get().blocks()).anyMatch(b -> b.kind() == TreeBlock.Kind.LEAVES || wood == Wood.PALM);
        }
    }

    @Test
    void sameSeedSameTree() {
        final TreeFeature feature = normal(Wood.OAK);
        final Tree a = SaplingTree.grow(feature, new Flat((p, g) -> g), 7L, 0, 61, 0).orElseThrow();
        final Tree b = SaplingTree.grow(feature, new Flat((p, g) -> g), 7L, 0, 61, 0).orElseThrow();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void ceilingAboveTheSaplingRefusesTheTree() {
        final TreeFeature feature = normal(Wood.OAK);
        for (long seed = 1; seed <= 10; seed++) {
            assertThat(SaplingTree.grow(feature, new Flat((p, g) -> p[1] == 63 ? TreeGround.SOLID : g), seed, 0, 61, 0)).isEmpty();
        }
    }

    @Test
    void bareStoneBelowRefusesTheTree() {
        final TreeFeature feature = normal(Wood.OAK);
        assertThat(SaplingTree.grow(feature, new Flat((p, g) -> p[1] == 60 ? TreeGround.STONE : g), 3L, 0, 61, 0)).isEmpty();
    }

    @Test
    void refsNameTheCraftEngineBlocks() {
        final TreeFeature feature = normal(Wood.OAK);
        final Tree tree = SaplingTree.grow(feature, new Flat((p, g) -> g), 7L, 0, 61, 0).orElseThrow();
        assertThat(tree.blocks().stream().map(SaplingTreeRefs::ref).flatMap(Optional::stream))
            .allMatch(r -> r.startsWith("tfp:wood/log/oak[axis=") || r.startsWith("tfp:wood/wood/oak[axis=") || r.equals("tfp:wood/leaves/oak"));
    }

    @Test
    void refsCarryTheTemplatesBranchDirection() {
        final Tree tree = SaplingTree.grow(normal(Wood.OAK), new Flat((p, g) -> g), 7L, 0, 61, 0).orElseThrow();
        final java.util.List<String> refs = tree.blocks().stream().map(SaplingTreeRefs::ref).flatMap(Optional::stream).toList();
        assertThat(refs).anyMatch(r -> r.contains(",branch_direction=down]"));
        assertThat(refs).anyMatch(r -> r.startsWith("tfp:wood/wood/oak[axis=") && r.contains("branch_direction="));
        // every log or wood ref names a TFC branch direction other than none (a natural block)
        assertThat(refs.stream().filter(r -> r.startsWith("tfp:wood/log/") || r.startsWith("tfp:wood/wood/")))
            .allMatch(r -> r.matches(".*,branch_direction=[a-z_]+\\]") && !r.contains("branch_direction=none"));
        // a player-placed log is the default state and names no branch direction
        assertThat(SaplingTreeRefs.ref(new TreeBlock(0, 0, 0, TreeBlock.Kind.LOG, Wood.OAK, TreeBlock.Axis.X,
            dev.tfp.core.worldgen.tree.BranchDirection.NONE, false))).contains("tfp:wood/log/oak[axis=x]");
    }
}
