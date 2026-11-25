/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (BASE_MULTIBLOCKS,
 *       BLOOMERY_CHIMNEY, GATE_X, GATE_Z: the offsets tested below)
 */
package dev.tfp.core.bloomery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-METAL-14: the bloomery multiblock on a block grid, per facing, and the molten tower and intake order. */
class BloomeryStructureTest {
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    /** A grid world: a block name per position; unset is air. */
    static final class Grid implements BloomeryStructure.World {
        final Map<List<Integer>, String> blocks = new HashMap<>();
        int ox;
        int oy;
        int oz;

        Grid at(int x, int y, int z) {
            Grid g = new Grid();
            g.blocks.putAll(blocks);
            g.ox = x;
            g.oy = y;
            g.oz = z;
            return g;
        }

        void set(int x, int y, int z, String name) {
            blocks.put(List.of(x, y, z), name);
        }

        String get(int dx, int dy, int dz) {
            return blocks.getOrDefault(List.of(ox + dx, oy + dy, oz + dz), "air");
        }

        @Override
        public boolean insulation(int dx, int dy, int dz) {
            return get(dx, dy, dz).equals("stone");
        }

        @Override
        public boolean bloomery(int dx, int dy, int dz) {
            return get(dx, dy, dz).equals("bloomery");
        }

        @Override
        public boolean air(int dx, int dy, int dz) {
            return get(dx, dy, dz).equals("air");
        }

        @Override
        public boolean center(int dx, int dy, int dz) {
            return Set.of("air", "molten", "bloom").contains(get(dx, dy, dz));
        }

        @Override
        public boolean inside(int dx, int dy, int dz) {
            return Set.of("air", "molten").contains(get(dx, dy, dz));
        }
    }

    /** The positions a formed base needs as insulation, relative to the inside block, for {@code facing}. */
    static List<int[]> baseInsulation(String facing) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] {0, -1, 0});
        int[] g = BloomeryStructure.delta(facing);
        for (String side : FACINGS) {
            if (!side.equals(facing)) {
                int[] d = BloomeryStructure.delta(side);
                out.add(new int[] {d[0], 0, d[1]});
            }
        }
        boolean z = facing.equals("north") || facing.equals("south");
        for (String side : z ? new String[] {"east", "west"} : new String[] {"north", "south"}) {
            int[] d = BloomeryStructure.delta(side);
            out.add(new int[] {g[0] + d[0], 0, g[1] + d[1]});
        }
        out.add(new int[] {g[0], -1, g[1]});
        for (String side : FACINGS) {
            int[] d = BloomeryStructure.delta(side);
            out.add(new int[] {d[0], 1, d[1]});
        }
        return out;
    }

    /** A formed bloomery with inside block (10, 64, 10) and {@code levels} chimney levels. */
    static Grid build(String facing, int levels) {
        Grid g = new Grid();
        for (int[] p : baseInsulation(facing)) {
            g.set(10 + p[0], 64 + p[1], 10 + p[2], "stone");
        }
        int[] d = BloomeryStructure.delta(facing);
        g.set(10 + d[0], 64, 10 + d[1], "bloomery");
        for (int level = 1; level <= levels; level++) {
            for (String side : FACINGS) {
                int[] s = BloomeryStructure.delta(side);
                g.set(10 + s[0], 64 + level, 10 + s[1], "stone");
            }
        }
        return g.at(10, 64, 10);
    }

    @Test
    void aBuiltBloomeryIsFormedOnEveryFacing() {
        for (String facing : FACINGS) {
            assertThat(BloomeryStructure.isFormed(build(facing, 1), facing)).as(facing).isTrue();
        }
    }

    @Test
    void theBaseNeedsEveryOneOfItsInsulationBlocks() {
        for (String facing : FACINGS) {
            List<int[]> required = baseInsulation(facing);
            assertThat(required).as(facing + " positions").hasSize(1 + 3 + 2 + 1 + 4);
            for (int[] p : required) {
                Grid g = build(facing, 1);
                g.blocks.remove(List.of(10 + p[0], 64 + p[1], 10 + p[2]));
                assertThat(BloomeryStructure.isFormed(g, facing)).as(facing + " without " + List.of(p[0], p[1], p[2])).isFalse();
            }
        }
    }

    @Test
    void theGateBlockAndTheInsideMustBeRight() {
        Grid g = build("north", 1);
        g.blocks.remove(List.of(10, 64, 9));
        assertThat(BloomeryStructure.isFormed(g, "north")).as("no gate").isFalse();
        g = build("north", 1);
        g.set(10, 64, 10, "stone");
        assertThat(BloomeryStructure.isFormed(g, "north")).as("a stone inside").isFalse();
        for (String ok : new String[] {"air", "molten", "bloom"}) {
            g = build("north", 1);
            g.set(10, 64, 10, ok);
            assertThat(BloomeryStructure.isFormed(g, "north")).as(ok + " inside").isTrue();
        }
        assertThat(BloomeryStructure.isFormed(build("north", 1), "south")).as("wrong facing").isFalse();
    }

    @Test
    void chimneyLevelsCountTheLeadingFormedLevelsUpToTheMaximum() {
        assertThat(BloomeryStructure.chimneyLevels(build("east", 0), 3)).as("the base already has the first ring").isEqualTo(1);
        assertThat(BloomeryStructure.chimneyLevels(build("east", 1), 3)).isEqualTo(1);
        assertThat(BloomeryStructure.chimneyLevels(build("east", 3), 3)).isEqualTo(3);
        assertThat(BloomeryStructure.chimneyLevels(build("east", 5), 3)).as("capped").isEqualTo(3);
        assertThat(BloomeryStructure.chimneyLevels(build("east", 5), 5)).isEqualTo(5);
        Grid gap = build("east", 3);
        gap.blocks.remove(List.of(11, 66, 10)); // a missing wall block on level 2
        assertThat(BloomeryStructure.chimneyLevels(gap, 3)).as("stops at the first gap").isEqualTo(1);
        Grid full = build("east", 3);
        full.set(10, 65, 10, "stone"); // a solid block inside level 1
        assertThat(BloomeryStructure.chimneyLevels(full, 3)).isZero();
        Grid molten = build("east", 3);
        molten.set(10, 65, 10, "molten");
        molten.set(10, 66, 10, "molten");
        assertThat(BloomeryStructure.chimneyLevels(molten, 3)).as("molten metal is inside").isEqualTo(3);
        molten.set(10, 66, 10, "bloom");
        assertThat(BloomeryStructure.chimneyLevels(molten, 3)).as("a bloom is not chimney").isEqualTo(1);
    }

    @Test
    void theGateStaysOnlyBetweenInsulation() {
        for (String facing : FACINGS) {
            boolean z = facing.equals("north") || facing.equals("south");
            Grid ok = new Grid();
            String[] sides = z ? new String[] {"west", "east"} : new String[] {"north", "south"};
            for (String side : sides) {
                int[] d = BloomeryStructure.delta(side);
                ok.set(d[0], 0, d[1], "stone");
            }
            ok.set(0, 1, 0, "stone");
            ok.set(0, -1, 0, "stone");
            assertThat(BloomeryStructure.canGateStayInPlace(ok, facing)).as(facing + " in air").isTrue();
            ok.set(0, 0, 0, "bloomery");
            assertThat(BloomeryStructure.canGateStayInPlace(ok, facing)).as(facing + " as the bloomery").isTrue();
            for (String side : sides) {
                int[] d = BloomeryStructure.delta(side);
                Grid g = new Grid();
                g.blocks.putAll(ok.blocks);
                g.blocks.remove(List.of(d[0], 0, d[1]));
                assertThat(BloomeryStructure.canGateStayInPlace(g, facing)).as(facing + " without " + side).isFalse();
            }
            Grid roof = new Grid();
            roof.blocks.putAll(ok.blocks);
            roof.blocks.remove(List.of(0, 1, 0));
            assertThat(BloomeryStructure.canGateStayInPlace(roof, facing)).isFalse();
            ok.set(0, 0, 0, "stone");
            assertThat(BloomeryStructure.canGateStayInPlace(ok, facing)).as("a stone is no gate").isFalse();
            // the sides along the other axis do not matter
            Grid other = new Grid();
            other.blocks.putAll(ok.blocks);
            other.set(0, 0, 0, "air");
            for (String side : z ? new String[] {"north", "south"} : new String[] {"east", "west"}) {
                int[] d = BloomeryStructure.delta(side);
                other.blocks.remove(List.of(d[0], 0, d[1]));
            }
            assertThat(BloomeryStructure.canGateStayInPlace(other, facing)).isTrue();
        }
    }

    @Test
    void oppositeAndDeltaAreConsistent() {
        for (String f : FACINGS) {
            int[] a = BloomeryStructure.delta(f);
            int[] b = BloomeryStructure.delta(BloomeryStructure.opposite(f));
            assertThat(a[0] + b[0]).isZero();
            assertThat(a[1] + b[1]).isZero();
        }
    }

    // ---- the molten tower (TFC manageMoltenBlockTower) and the intake order --------------------------------------

    @Test
    void moltenTowerFillsFromTheBottomFourLayersABlock() {
        assertThat(BloomeryRules.moltenTower(0, 16, 3)).containsExactly(0, 0, 0);
        assertThat(BloomeryRules.moltenTower(1, 16, 3)).as("at least one layer").containsExactly(1, 0, 0);
        assertThat(BloomeryRules.moltenTower(3, 16, 3)).containsExactly(1, 0, 0);
        assertThat(BloomeryRules.moltenTower(4, 16, 3)).containsExactly(1, 0, 0);
        assertThat(BloomeryRules.moltenTower(8, 16, 3)).containsExactly(2, 0, 0);
        assertThat(BloomeryRules.moltenTower(16, 16, 3)).containsExactly(4, 0, 0);
        assertThat(BloomeryRules.moltenTower(20, 16, 3)).containsExactly(4, 1, 0);
        assertThat(BloomeryRules.moltenTower(48, 16, 3)).containsExactly(4, 4, 4);
        assertThat(BloomeryRules.moltenTower(200, 16, 3)).as("no more than the tower").containsExactly(4, 4, 4);
        assertThat(BloomeryRules.moltenTower(10, 8, 2)).containsExactly(4, 1);
    }

    @Test
    void theIntakeSequenceAlternatesCatalystThenInputAndMatchesTheCounts() {
        BloomeryRecipe recipe = new BloomeryRecipe("fluid:iron", 100, "minecraft:charcoal", 1, "tfp:bloom", 100);
        BloomeryItem coal = new BloomeryItem("minecraft:charcoal", 3, null, 0);
        BloomeryItem ore = new BloomeryItem("tfp:ore", 2, "fluid:iron", 100);
        BloomeryItem junk = new BloomeryItem("minecraft:stick", 5, null, 0);
        List<BloomeryItem> world = List.of(ore, junk, coal);
        List<Integer> seq = BloomeryRules.intakeSequence(recipe, world, 0, 10);
        // catalyst (index 2), input (0), catalyst, input, catalyst: the ore runs out after two
        assertThat(seq).containsExactly(2, 0, 2, 0, 2);
        int[] counts = BloomeryRules.intake(recipe, world, 0, 10);
        assertThat(counts).containsExactly(2, 0, 3);
        assertThat(BloomeryRules.intakeSequence(recipe, world, 0, 4)).as("the capacity cuts the last pair").containsExactly(2, 0, 2, 0);
        assertThat(BloomeryRules.intakeSequence(recipe, world, 0, 3)).containsExactly(2, 0, 2);
        assertThat(BloomeryRules.intakeSequence(recipe, world, 3, 3)).as("full").isEmpty();
        assertThat(BloomeryRules.intakeSequence(null, world, 0, 3)).isEmpty();
        assertThat(BloomeryRules.intakeSequence(recipe, List.of(ore), 0, 5)).as("no catalyst yet").isEmpty();
        assertThat(BloomeryRules.intakeSequence(recipe, List.of(ore), 1, 5)).as("inputs alone are not taken on top").isEmpty();
        assertThat(BloomeryRules.intakeSequence(recipe, List.of(coal), 1, 5)).as("a catalyst on top of held units").containsExactly(0, 0, 0);
    }

    @Test
    void bloomDataFollowsTfcsCountAndLayers() {
        BloomData made = BloomData.made(5);
        assertThat(made.layers()).isEqualTo(5);
        assertThat(made.dropped().count()).isEqualTo(4);
        assertThat(made.dropped().maxCount()).isEqualTo(5);
        BloomData big = BloomData.made(16);
        assertThat(big.layers()).isEqualTo(8);
        assertThat(big.dropped().layers()).as("8 * 15 / 16").isEqualTo(7);
        BloomData one = new BloomData(1, 100);
        assertThat(one.layers()).as("never below one layer while an item is left").isEqualTo(1);
        assertThat(one.dropped().layers()).isZero();
        assertThat(BloomData.CODEC.decode(BloomData.CODEC.encode(big))).isEqualTo(big);
        BloomeryData lit = BloomeryData.FRESH.lit(1_000L, 14_400);
        assertThat(lit.lit()).isTrue();
        assertThat(lit.finishTick()).isEqualTo(15_400L);
        assertThat(BloomeryData.CODEC.decode(BloomeryData.CODEC.encode(lit))).isEqualTo(lit);
        assertThat(lit.unlit().lit()).isFalse();
    }
}
