/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianAi.java @ c3b4d9536 (initIdleActivity),
 * ai/amphibian/AmphibiousStrollToPoi.java, ai/SetLookTarget.java, same commit; with vanilla 1.21.1 TryFindWater,
 * BlockPos.withinManhattan, ShufflingList, BabyFollowAdult, DoNothing
 */
package dev.tfp.core.amphibian;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Golden tests of the amphibian idle behaviours, hand-traced from the TFC and vanilla methods named above. */
class AmphibianIdleGoldenTest {

    /** Scripted draws that record each call. */
    private static final class Script implements AmphibianIdle.Draws {
        final Deque<Number> values = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();

        Script(Number... v) {
            values.addAll(List.of(v));
        }

        @Override
        public int nextInt(int bound) {
            calls.add("int " + bound);
            return values.removeFirst().intValue();
        }

        @Override
        public float nextFloat() {
            calls.add("float");
            return values.removeFirst().floatValue();
        }
    }

    @Test
    void constantsMatchTfc() {
        assertThat(AmphibianIdle.LOOK_RANGE).isEqualTo(6.0);
        assertThat(AmphibianIdle.ADULT_FOLLOW_MIN).isEqualTo(15);
        assertThat(AmphibianIdle.ADULT_FOLLOW_MAX).isEqualTo(30);
        assertThat(AmphibianIdle.FIND_WATER_RANGE).isEqualTo(6);
        assertThat(AmphibianIdle.FIND_WATER_SPEED).isEqualTo(0.15f);
        assertThat(AmphibianIdle.SWIM_SPEED).isEqualTo(0.5f);
        assertThat(AmphibianIdle.STROLL_SPEED).isEqualTo(0.15f);
        assertThat(AmphibianIdle.HOME_MAX_DISTANCE).isEqualTo(100);
        assertThat(AmphibianIdle.PINNIPED_RETREAT_HOME_MAX).isEqualTo(10_000);
        assertThat(AmphibianIdle.MELEE_COOLDOWN).isEqualTo(20);
        assertThat(AmphibianIdle.TEMPTATION_RANGE).isEqualTo(10.0);
    }

    @Test
    void theLookTickerDrawsAtZeroAndFiresWhenItCountsDownToZero() {
        Script s = new Script(12); // UniformInt.of(30, 60): nextInt(31) + 30
        AmphibianIdle.Ticked t = AmphibianIdle.lookTicker(0, s);
        assertThat(t).isEqualTo(new AmphibianIdle.Ticked(42, false));
        assertThat(s.calls).containsExactly("int 31");
        assertThat(AmphibianIdle.lookTicker(2, s)).isEqualTo(new AmphibianIdle.Ticked(1, false));
        assertThat(AmphibianIdle.lookTicker(1, s)).isEqualTo(new AmphibianIdle.Ticked(0, true));
    }

    @Test
    void runOneDrawsTwoFloatsAndTheLargerGoesFirst() {
        assertThat(AmphibianIdle.temptationFirst(new Script(0.2f, 0.7f))).isTrue();
        assertThat(AmphibianIdle.temptationFirst(new Script(0.7f, 0.2f))).isFalse();
        // a tie keeps the listed order: the baby-follow first
        assertThat(AmphibianIdle.temptationFirst(new Script(0.5f, 0.5f))).isFalse();
    }

    @Test
    void babiesFollowAnAdultBetween15And31Blocks() {
        assertThat(AmphibianIdle.followsAdult(true, 14.99)).isFalse();
        assertThat(AmphibianIdle.followsAdult(true, 15.0)).isTrue();
        assertThat(AmphibianIdle.followsAdult(true, 30.99)).isTrue();
        assertThat(AmphibianIdle.followsAdult(true, 31.0)).isFalse();
        assertThat(AmphibianIdle.followsAdult(false, 20)).isFalse();
        assertThat(AmphibianIdle.followsAdult(true, Double.NaN)).isFalse();
        assertThat(AmphibianIdle.adultCloseEnough()).isEqualTo(14);
    }

    @Test
    void withinManhattanStartsAtTheOriginAndMirrorsZ() {
        List<int[]> order = AmphibianIdle.withinManhattan(6, 6, 6);
        assertThat(order.get(0)).containsExactly(0, 0, 0);
        // depth 1: x=-1 (y=0, z=0), x=0 (y=-1; y=0 z=+1,-1; y=1), x=1
        assertThat(order.get(1)).containsExactly(-1, 0, 0);
        assertThat(order.get(2)).containsExactly(0, -1, 0);
        assertThat(order.get(3)).containsExactly(0, 0, 1);
        assertThat(order.get(4)).containsExactly(0, 0, -1);
        assertThat(order.get(5)).containsExactly(0, 1, 0);
        assertThat(order.get(6)).containsExactly(1, 0, 0);
        // the full octahedron of radius 18 clipped to the 6-cube
        int count = 0;
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    count++;
                }
            }
        }
        assertThat(order).hasSize(count);
    }

    @Test
    void tryFindWaterPushesItsWaitAndPrefersWaterWithAirAbove() {
        // waiting: the wait is pushed to now + 22 and nothing is searched
        AmphibianIdle.FoundWater w = AmphibianIdle.findWater(100, 120, null);
        assertThat(w.waitAt()).isEqualTo(122);
        assertThat(w.offset()).isEmpty();
        // water under a block at (0,-1,1) (fallback, farther than 1.5? no: close), water with air above at (2,0,0)
        AmphibianIdle.WaterProbe probe = new AmphibianIdle.WaterProbe() {
            @Override
            public boolean water(int dx, int dy, int dz) {
                return (dx == 0 && dy == -1 && dz == 2) || (dx == 2 && dy == 0 && dz == 0) || (dx == 0 && dy == 0 && dz == 0);
            }

            @Override
            public boolean airAbove(int dx, int dy, int dz) {
                return dx == 2;
            }

            @Override
            public boolean close(int dx, int dy, int dz) {
                return false;
            }
        };
        w = AmphibianIdle.findWater(200, 0, probe);
        assertThat(w.waitAt()).isEqualTo(240);
        assertThat(w.offset()).get().satisfies(o -> assertThat(o).containsExactly(2, 0, 0));
        // without water with air above, the first water farther than 1.5 blocks (the mob's own column is skipped)
        AmphibianIdle.WaterProbe covered = new AmphibianIdle.WaterProbe() {
            @Override
            public boolean water(int dx, int dy, int dz) {
                return dx == 0 && dz != 0;
            }

            @Override
            public boolean airAbove(int dx, int dy, int dz) {
                return false;
            }

            @Override
            public boolean close(int dx, int dy, int dz) {
                return Math.abs(dz) <= 1 && dy == 0;
            }
        };
        w = AmphibianIdle.findWater(200, 0, covered);
        assertThat(w.offset()).get().satisfies(o -> assertThat(o).containsExactly(0, -1, 1));
    }

    /** A recording gate world. */
    private static final class World implements AmphibianIdle.GateWorld<String> {
        final List<String> asked = new ArrayList<>();
        boolean fish;
        Optional<String> swim = Optional.empty();
        Optional<String> stroll = Optional.empty();
        Optional<String> look = Optional.empty();
        Optional<String> home = Optional.empty();

        @Override
        public boolean smallFishVisible() {
            asked.add("fish");
            return fish;
        }

        @Override
        public Optional<String> swimTarget() {
            asked.add("swim");
            return swim;
        }

        @Override
        public Optional<String> strollTarget() {
            asked.add("stroll");
            return stroll;
        }

        @Override
        public Optional<String> lookTargetInSameMedium() {
            asked.add("look");
            return look;
        }

        @Override
        public Optional<String> home(int maxDistance) {
            asked.add("home " + maxDistance);
            return home;
        }
    }

    @Test
    void theGateTriesEverythingInOrderAndTheHomeStrollWinsAfterItsWait() {
        World w = new World();
        w.fish = true;
        w.swim = Optional.of("swim");
        w.home = Optional.of("home");
        Script s = new Script(7); // DoNothing(30, 60): 30 + nextInt(31)
        AmphibianIdle.Gate<String> g = AmphibianIdle.gate(true, false, 500, 0, 100, w, s);
        assertThat(w.asked).containsExactly("fish", "swim", "home 100");
        assertThat(g.attack()).isTrue();
        assertThat(g.walk()).isEqualTo(new AmphibianIdle.Walk<>("home", 0.5f, 5));
        assertThat(g.busyTicks()).isEqualTo(37);
        assertThat(g.homeWaitAt()).isEqualTo(580);
        assertThat(AmphibianIdle.gateRestartAt(500, 37)).isEqualTo(539);

        // inside the home wait, on land with no stroll spot: walk to the look target within 3 at 0.15
        World land = new World();
        land.look = Optional.of("player");
        land.home = Optional.of("home");
        g = AmphibianIdle.gate(false, true, 560, 580, 100, land, new Script(0));
        // HUNTED_RECENTLY: getAttackTarget returns empty before looking at the fish
        assertThat(land.asked).containsExactly("stroll", "look", "home 100");
        assertThat(g.attack()).isFalse();
        assertThat(g.walk()).isEqualTo(new AmphibianIdle.Walk<>("player", 0.15f, 3));
        assertThat(g.homeWaitAt()).isEqualTo(580);
        // StrollToPoi: "time <= wait" still waits, the tick after does not
        assertThat(AmphibianIdle.strollHome(580, 580, 100, false, land).walk()).isNull();
        assertThat(AmphibianIdle.strollHome(581, 580, 100, false, land).walk()).isNotNull();
    }
}
