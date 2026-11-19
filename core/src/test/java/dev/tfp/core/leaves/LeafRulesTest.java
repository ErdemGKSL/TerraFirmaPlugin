/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.leaves;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.leaves.LeafRules.Cell;
import dev.tfp.core.leaves.LeafRules.Pos;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-04: TFC's leaf distance, decay, fallen leaves landing and slowdown factors. */
class LeafRulesTest {
    private final Map<Pos, Cell> cells = new HashMap<>();
    private final LeafRules.Probe probe = (x, y, z) -> cells.getOrDefault(new Pos(x, y, z), Cell.OTHER);

    private void line(int length) {
        cells.put(new Pos(0, 0, 0), Cell.LOG);
        for (int i = 1; i <= length; i++) {
            cells.put(new Pos(i, 0, 0), Cell.LEAF);
        }
    }

    @Test
    void distanceCountsStepsThroughLeavesToALog() {
        line(12);
        for (int i = 1; i <= 9; i++) {
            assertThat(LeafRules.distance(probe, new Pos(i, 0, 0))).as("leaf " + i).isEqualTo(i);
            assertThat(LeafRules.decays(probe, new Pos(i, 0, 0))).isFalse();
        }
        for (int i = 10; i <= 12; i++) {
            assertThat(LeafRules.distance(probe, new Pos(i, 0, 0))).isEqualTo(LeafRules.NO_LOG);
            assertThat(LeafRules.decays(probe, new Pos(i, 0, 0))).isTrue();
        }
    }

    @Test
    void aLeafWithNoLogDecaysAndTheNearestLogWinsEitherWay() {
        cells.put(new Pos(0, 0, 0), Cell.LEAF);
        assertThat(LeafRules.decays(probe, new Pos(0, 0, 0))).isTrue();
        cells.put(new Pos(0, 1, 0), Cell.LOG);
        cells.put(new Pos(0, -1, 0), Cell.LEAF);
        cells.put(new Pos(0, -2, 0), Cell.LEAF);
        assertThat(LeafRules.distance(probe, new Pos(0, 0, 0))).isEqualTo(1);
        assertThat(LeafRules.distance(probe, new Pos(0, -2, 0))).isEqualTo(3);
    }

    @Test
    void cuttingTheLogOrphansTheLeavesThatReliedOnIt() {
        line(4);
        cells.put(new Pos(2, 0, 1), Cell.LOG); // a second log keeps every leaf of the line in reach
        assertThat(LeafRules.orphans(probe, new Pos(0, 0, 0))).isEmpty();
        cells.remove(new Pos(0, 0, 0));
        assertThat(LeafRules.orphans(probe, new Pos(0, 0, 0))).isEmpty();
    }

    @Test
    void withNoLogLeftEveryReachedLeafDecays() {
        line(4);
        cells.remove(new Pos(0, 0, 0));
        assertThat(LeafRules.orphans(probe, new Pos(0, 0, 0))).containsExactlyInAnyOrder(new Pos(1, 0, 0), new Pos(2, 0, 0),
                new Pos(3, 0, 0), new Pos(4, 0, 0));
    }

    @Test
    void floodIsBounded() {
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                for (int z = -3; z <= 3; z++) {
                    cells.put(new Pos(x, y, z), Cell.LEAF);
                }
            }
        }
        assertThat(LeafRules.orphans(probe, new Pos(0, 0, 4)).size()).isLessThanOrEqualTo(LeafRules.MAX_FLOOD);
    }

    private record Ground(Map<Pos, String> kinds) implements LeafRules.Column {
        String at(int x, int y, int z) {
            return kinds.getOrDefault(new Pos(x, y, z), "air");
        }

        @Override
        public boolean leavesOrReplaceable(int x, int y, int z) {
            return at(x, y, z).equals("leaf") || at(x, y, z).equals("air") || at(x, y, z).equals("plant");
        }

        @Override
        public boolean replaceable(int x, int y, int z) {
            return at(x, y, z).equals("air") || at(x, y, z).equals("plant");
        }

        @Override
        public boolean air(int x, int y, int z) {
            return at(x, y, z).equals("air");
        }

        @Override
        public boolean canSurvive(int x, int y, int z, boolean leaf) {
            return !at(x, y - 1, z).equals("air");
        }
    }

    @Test
    void fallenLeavesLandOnTheGroundBelow() {
        Map<Pos, String> kinds = new HashMap<>();
        kinds.put(new Pos(0, 60, 0), "dirt");
        kinds.put(new Pos(0, 64, 0), "leaf");
        kinds.put(new Pos(0, 63, 0), "leaf");
        Ground g = new Ground(kinds);
        var landing = LeafRules.fallenLeavesLanding(g, new Pos(0, 64, 0), false, () -> 0.3f);
        assertThat(landing).hasValue(new LeafRules.Landing(new Pos(0, 61, 0), true));
        assertThat(LeafRules.fallenLeavesLanding(g, new Pos(0, 64, 0), false, () -> 0.7f).orElseThrow().leaf()).isFalse();
        kinds.put(new Pos(0, 61, 0), "plant");
        assertThat(LeafRules.fallenLeavesLanding(g, new Pos(0, 64, 0), false, () -> 0.7f)).isPresent();
        assertThat(LeafRules.fallenLeavesLanding(g, new Pos(0, 64, 0), true, () -> 0.7f)).as("seasonal: only into air").isEmpty();
    }

    @Test
    void fallenLeavesNeedGroundWithinFifteenSteps() {
        Ground g = new Ground(new HashMap<>());
        assertThat(LeafRules.fallenLeavesLanding(g, new Pos(0, 64, 0), false, () -> 0f)).isEmpty();
    }

    @Test
    void slowFactorsMatchTfc() {
        assertThat(LeafRules.leavesFactor(false, LeafRules.LEAVES_MOVEMENT_MODIFIER)).isEqualTo(0.4f);
        assertThat(LeafRules.leavesFactor(true, LeafRules.LEAVES_MOVEMENT_MODIFIER)).isEqualTo(1f);
        assertThat(LeafRules.fallenLeavesFactor(1, false, 0.4f)).isEqualTo(1f);
        assertThat(LeafRules.fallenLeavesFactor(4, false, 0.4f)).isCloseTo(0.2f, within(1e-6f));
        assertThat(LeafRules.fallenLeavesFactor(8, false, 0.4f)).isCloseTo(0.4f, within(1e-6f));
        assertThat(LeafRules.plantFactor(0.9f, LeafRules.PLANTS_MOVEMENT_MODIFIER)).isCloseTo(0.92f, within(1e-6f));
        assertThat(LeafRules.horizontalFactor(0.4f, true)).isEqualTo(0.4f);
        assertThat(LeafRules.horizontalFactor(0.4f, false)).isCloseTo(0.85f, within(1e-6f));
        assertThat(LeafRules.fallingFactor(0.4f)).isCloseTo(0.88f, within(1e-6f));
    }
}
