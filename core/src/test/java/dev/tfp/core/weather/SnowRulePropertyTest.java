/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.SnowRule.Change;
import dev.tfp.core.weather.SnowRule.Kind;
import dev.tfp.core.world.BlockPos;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Properties of {@link SnowRule} over the random scenarios of {@link SnowScenario}. */
class SnowRulePropertyTest {
    private static final int SCENARIOS = 5_000;
    private static final Set<Kind> ACCUMULATION = EnumSet.of(Kind.PLACE_SNOW, Kind.PLACE_SNOW_PILE, Kind.FREEZE,
            Kind.PLACE_ICE_PILE, Kind.PLACE_ICICLE, Kind.ICICLE_TO_BASE);
    private static final Set<Kind> MELT = EnumSet.of(Kind.SET_SNOW_LAYERS, Kind.REMOVE, Kind.RESTORE_PILE,
            Kind.MELT_TO_WATER, Kind.ICICLE_TO_TIP);

    private static SnowScenario scenario(int i) {
        return new SnowScenario(i * 0x2545F4914F6CDD1DL + 99L);
    }

    @Test
    void sameInputsAndSeedGiveTheSameDecision() {
        for (int i = 0; i < SCENARIOS; i++) {
            SnowScenario s = scenario(i);
            BlockPos surface = new BlockPos(s.sx, s.sy, s.sz);
            List<Change> a = SnowRule.accumulate(new SnowRuleGoldenTest.World(s), surface, new Random(s.rnd), 16);
            List<Change> b = SnowRule.accumulate(new SnowRuleGoldenTest.World(s), surface, new Random(s.rnd), 16);
            assertThat(a).isEqualTo(b);
            assertThat(SnowRule.melt(new SnowRuleGoldenTest.World(s), surface, 16))
                    .isEqualTo(SnowRule.melt(new SnowRuleGoldenTest.World(s), surface, 16));
        }
    }

    @Test
    void decisionsAreTheSameOnManyThreads() {
        List<List<Change>> serial = IntStream.range(0, SCENARIOS).mapToObj(SnowRulePropertyTest::decideAll).toList();
        List<List<Change>> parallel = IntStream.range(0, SCENARIOS).parallel().mapToObj(SnowRulePropertyTest::decideAll).toList();
        assertThat(parallel).isEqualTo(serial);
    }

    private static List<Change> decideAll(int i) {
        SnowScenario s = scenario(i);
        BlockPos surface = new BlockPos(s.sx, s.sy, s.sz);
        List<Change> out = new java.util.ArrayList<>(SnowRule.accumulate(new SnowRuleGoldenTest.World(s), surface, new Random(s.rnd), 16));
        out.addAll(SnowRule.melt(new SnowRuleGoldenTest.World(s), surface, 16));
        return out;
    }

    @Test
    void kindsBelongToTheirEventAndSnowNeverGoesAboveTheSurface() {
        int piles = 0;
        for (int i = 0; i < SCENARIOS; i++) {
            SnowScenario s = scenario(i);
            BlockPos surface = new BlockPos(s.sx, s.sy, s.sz);
            for (Change c : SnowRule.accumulate(new SnowRuleGoldenTest.World(s), surface, new Random(s.rnd), 16)) {
                assertThat(ACCUMULATION).contains(c.kind());
                assertThat(Math.abs(c.pos().x() - s.sx) + Math.abs(c.pos().z() - s.sz)).isLessThanOrEqualTo(1);
                if (c.kind() == Kind.PLACE_SNOW || c.kind() == Kind.PLACE_SNOW_PILE) {
                    // Snow and piles sit at the sky-visible surface or below it (a pile absorbs at most the block above it)
                    assertThat(c.pos().y()).isBetween(s.sy - 2, s.sy);
                    piles += c.kind() == Kind.PLACE_SNOW_PILE ? 1 : 0;
                }
                if (c.kind() == Kind.FREEZE || c.kind() == Kind.PLACE_ICE_PILE) {
                    assertThat(c.pos()).isIn(surface.offset(0, -1, 0), surface.offset(0, -2, 0));
                }
                if (c.kind() == Kind.PLACE_ICICLE) {
                    // In a neighbour column, from the surface up, within the scan
                    assertThat(c.pos().y()).isBetween(s.sy, s.sy + 15);
                    assertThat(c.pos().x() == s.sx && c.pos().z() == s.sz).isFalse();
                }
            }
            for (Change c : SnowRule.melt(new SnowRuleGoldenTest.World(s), surface, 16)) {
                assertThat(MELT).contains(c.kind());
                assertThat(c.pos().x()).isEqualTo(s.sx);
                assertThat(c.pos().z()).isEqualTo(s.sz);
                assertThat(c.pos().y()).isLessThanOrEqualTo(s.sy);
                if (c.kind() == Kind.SET_SNOW_LAYERS) {
                    assertThat(c.layers()).isBetween(1, SnowRule.MAX_SNOW_LAYERS - 1);
                }
            }
        }
        assertThat(piles).isPositive();
    }

    @Test
    void anAccumulationWritesEachPositionOnce() {
        for (int i = 0; i < SCENARIOS; i++) {
            SnowScenario s = scenario(i);
            List<Change> out = SnowRule.accumulate(new SnowRuleGoldenTest.World(s), new BlockPos(s.sx, s.sy, s.sz),
                    new Random(s.rnd), 16);
            assertThat(out.stream().map(Change::pos).distinct().count()).isEqualTo(out.size());
        }
    }
}
