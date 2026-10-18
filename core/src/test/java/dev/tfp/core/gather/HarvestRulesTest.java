/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/** Invariants of the M3-GATH-02 harvest rules, and the determinism of the seeded roll source. */
class HarvestRulesTest {

    private static final List<HeldTool> TOOLS = List.of(
            HeldTool.NONE, HeldTool.of(ToolTag.SHARP), HeldTool.of(ToolTag.SHEAR), HeldTool.of(ToolTag.SILK_TOUCH));

    private static List<HarvestTable> tablesWithPrefix(String prefix) {
        return HarvestTables.all().stream().filter(t -> t.blockId().startsWith(prefix)).toList();
    }

    @Test
    void twigYieldsItsOwnTwigOnceWithAnyTool() {
        List<HarvestTable> twigs = tablesWithPrefix("tfc:wood/twig/");
        assertThat(twigs).hasSize(20);
        for (HarvestTable twig : twigs) {
            for (HeldTool tool : TOOLS) {
                assertThat(HarvestRules.drops(twig, tool, Map.of(), () -> 0.999))
                        .as(twig.blockId() + " with " + tool.tags())
                        .containsExactly(new GatherYield(twig.blockId(), 1));
            }
        }
    }

    @Test
    void fallenLeavesYieldAsManyAsTheirLayers() {
        List<HarvestTable> fallen = tablesWithPrefix("tfc:wood/fallen_leaves/");
        assertThat(fallen).hasSize(20);
        for (HarvestTable table : fallen) {
            for (int layers = 1; layers <= 8; layers++) {
                assertThat(HarvestRules.drops(table, HeldTool.NONE, Map.of("layers", "" + layers), () -> 0.5))
                        .as(table.blockId() + " layers " + layers)
                        .containsExactly(new GatherYield(table.blockId(), layers));
            }
        }
    }

    @Test
    void leavesYieldTheLeafOnlyWithShearsOrSilkTouchAndNoSticks() {
        for (HarvestTable leaves : tablesWithPrefix("tfc:wood/leaves/")) {
            for (HeldTool tool : List.of(HeldTool.of(ToolTag.SHEAR), HeldTool.of(ToolTag.SILK_TOUCH))) {
                // the stick roll is gated on not having shears or silk touch, so no stick at either roll
                for (double u : new double[] {0.0, 0.999}) {
                    assertThat(HarvestRules.drops(leaves, tool, Map.of(), () -> u))
                            .as(leaves.blockId() + " " + tool.tags() + " " + u)
                            .noneMatch(y -> y.itemId().equals("minecraft:stick"))
                            .anyMatch(y -> y.itemId().equals(leaves.blockId()) && y.count() == 1);
                }
            }
        }
    }

    @Test
    void sharpLeavesDropSticksAtTwentyPercentAndOneToTwo() {
        HarvestTable oak = HarvestRules.table("tfc:wood/leaves/oak");
        // a roll of 0.0 passes the 20% gate and takes the minimum count
        assertThat(HarvestRules.drops(oak, HeldTool.of(ToolTag.SHARP), Map.of(), () -> 0.0))
                .contains(new GatherYield("minecraft:stick", 1));
        // a roll of 0.999 fails the 20% gate and the 5% gate, so no stick
        assertThat(HarvestRules.drops(oak, HeldTool.of(ToolTag.SHARP), Map.of(), () -> 0.999))
                .noneMatch(y -> y.itemId().equals("minecraft:stick"));
    }

    @Test
    void plantsNeedASharpOrShearTool() {
        HarvestTable allium = HarvestRules.table("tfc:plant/allium");
        assertThat(HarvestRules.drops(allium, HeldTool.NONE, Map.of(), () -> 0.0)).isEmpty();
        assertThat(HarvestRules.drops(allium, HeldTool.of(ToolTag.SHARP), Map.of(), () -> 0.0))
                .containsExactly(new GatherYield("tfc:plant/allium", 1));
        assertThat(HarvestRules.drops(allium, HeldTool.of(ToolTag.SHEAR), Map.of(), () -> 0.0))
                .containsExactly(new GatherYield("tfc:plant/allium", 1));
    }

    @Test
    void creativeBreakDropsNothing() {
        HarvestTable oak = HarvestRules.table("tfc:wood/leaves/oak");
        assertThat(HarvestRules.breakDrops(oak, HeldTool.of(ToolTag.SHARP), Map.of(), () -> 0.0, true)).isEmpty();
        assertThat(HarvestRules.breakDrops(oak, HeldTool.of(ToolTag.SHARP), Map.of(), () -> 0.0, false)).isNotEmpty();
    }

    @Test
    void seededRollSourceGivesTheSameYieldsEveryTime() {
        for (HarvestTable table : HarvestTables.all()) {
            for (HeldTool tool : TOOLS) {
                List<GatherYield> first = HarvestRules.drops(table, tool, Map.of(), seeded(42));
                for (int i = 0; i < 3; i++) {
                    assertThat(HarvestRules.drops(table, tool, Map.of(), seeded(42)))
                            .as(table.blockId() + " " + tool.tags())
                            .isEqualTo(first);
                }
            }
        }
    }

    @Test
    void seededStickRateMatchesTheTwentyFivePercentChanceFromLeaves() {
        // With a sharp tool the oak leaves drop a stick with probability 0.2 + 0.8 * 0.05 = 0.24.
        HarvestTable oak = HarvestRules.table("tfc:wood/leaves/oak");
        int sticks = 0;
        int runs = 20_000;
        for (int i = 0; i < runs; i++) {
            if (HarvestRules.drops(oak, HeldTool.of(ToolTag.SHARP), Map.of(), seeded(1000 + i)).stream()
                    .anyMatch(y -> y.itemId().equals("minecraft:stick"))) {
                sticks++;
            }
        }
        assertThat((double) sticks / runs).isBetween(0.22, 0.26);
    }

    @Test
    void everyTableMapsToAHarvestKind() {
        for (HarvestTable table : HarvestTables.all()) {
            assertThat(HarvestKind.of(table.blockId())).isNotNull();
        }
        assertThat(HarvestKind.of("tfc:wood/twig/oak")).isEqualTo(HarvestKind.TWIG);
        assertThat(HarvestKind.of("tfc:groundcover/stick")).isEqualTo(HarvestKind.STICK);
        assertThatThrownBy(() -> HarvestKind.of("tfc:stone/granite")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidRulesAreRejected() {
        assertThatThrownBy(() -> new LootEntry.SetCount(List.of(), 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LootEntry.SetCount(List.of(), 3, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarvestCondition.Chance(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarvestCondition.StateIs(Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarvestTable(" ", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static DoubleSupplier seeded(long seed) {
        Random random = new Random(seed);
        return random::nextDouble;
    }
}
