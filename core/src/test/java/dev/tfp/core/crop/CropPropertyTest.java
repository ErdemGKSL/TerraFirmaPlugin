/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTick, growthTickStep),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/ICropBlock.java @ c3b4d9536 (getGrowthLimit),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/IPickableCrop.java @ c3b4d9536 (the pick branches)
 */
package dev.tfp.core.crop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.crop.CropRules.Alive;
import dev.tfp.core.crop.CropRules.Dead;
import dev.tfp.core.crop.CropRules.Grown;
import dev.tfp.core.crop.CropRules.Jitter;
import dev.tfp.core.crop.CropRules.Progress;
import dev.tfp.core.crop.CropRules.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Properties of the crop rules: lazy catch-up equals stepping, a call over a split equals one call, the lazy result is the same on any
 * thread, the seeded jitter is in range, and the edges of TFC's rules (dry death, no-light expiry, stage and harvest selection).
 */
class CropPropertyTest {
    private static final long START = 3L * CalendarTicks.CALENDAR_TICKS_IN_DAY + 777;
    private static final long I = CropRules.UPDATE_INTERVAL;

    @Test
    void splitAtAnIntervalEqualsOneCall() {
        for (CropType crop : CropType.values()) {
            for (int sched = 0; sched < 4; sched++) {
                long end = START + 40 * I + 12345;
                CropRules.Spec spec = crop.spec(1f, 1f, 1f);
                Progress whole = advance(crop, sched, START, end);
                Progress first = advance(crop, sched, START, START + 17 * I);
                if (first instanceof Alive alive) {
                    assertThat(alive.state().lastTick()).isEqualTo(START + 17 * I);
                    Progress split = CropRules.advance(alive.state(), spec, CropGoldenTest.schedule(sched), CropGoldenTest.feed(crop),
                        Jitter.seeded(42L), end);
                    assertThat(split).as("%s schedule %d", crop, sched).isEqualTo(whole);
                } else {
                    // A death is final: the whole span dies at the same step.
                    assertThat(whole).as("%s schedule %d", crop, sched).isEqualTo(first);
                }
            }
        }
    }

    @Test
    void steppingEveryIntervalEqualsTheCatchUp() {
        for (CropType crop : CropType.values()) {
            CropRules.Spec spec = crop.spec(1f, 1f, 1f);
            CropRules.ClimateSource climate = CropGoldenTest.schedule(2);
            CropRules.NutrientFeed feed = CropGoldenTest.feed(crop);
            Jitter jitter = Jitter.seeded(42L);
            long end = START + 30 * I;

            CropState state = CropState.planted(START);
            Progress stepped = null;
            for (long t = START + I; t <= end; t += I) {
                Step step = CropRules.step(state, spec, climate.at(t - I), climate.at(t), t - I, t, jitter.at(t - I),
                    feed.between(t - I, t));
                if (step instanceof Grown grown) {
                    state = grown.state();
                } else {
                    stepped = new Dead(t, ((CropRules.Died) step).fullyGrown());
                    break;
                }
            }
            Progress lazy = CropRules.advance(CropState.planted(START), spec, climate, feed, jitter, end);
            if (stepped == null) {
                assertThat(lazy).as(crop.name()).isEqualTo(new Alive(state));
            } else {
                assertThat(lazy).as(crop.name()).isEqualTo(stepped);
            }
        }
    }

    @Test
    void sameInputsGiveTheSameResultOnAnyThread() throws Exception {
        List<Object[]> cases = new ArrayList<>();
        for (CropType crop : CropType.values()) {
            for (int sched = 0; sched < 4; sched++) {
                cases.add(new Object[]{crop, sched});
            }
        }
        List<Progress> expected = new ArrayList<>();
        for (Object[] c : cases) {
            expected.add(advance((CropType) c[0], (Integer) c[1], START, START + 90 * I + 5));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 3; round++) {
                List<Future<Progress>> futures = new ArrayList<>();
                for (Object[] c : cases) {
                    futures.add(pool.submit(() -> advance((CropType) c[0], (Integer) c[1], START, START + 90 * I + 5)));
                }
                for (int i = 0; i < cases.size(); i++) {
                    assertThat(futures.get(i).get()).isEqualTo(expected.get(i));
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void seededJitterIsDeterministicInRangeAndSeedSensitive() {
        Jitter a = Jitter.seeded(7L), a2 = Jitter.seeded(7L), b = Jitter.seeded(8L);
        int differing = 0;
        for (long t = 0; t < 10_000; t += 37) {
            float v = a.at(t);
            assertThat(v).isEqualTo(a2.at(t));
            assertThat(v).isBetween(0.9f, 1.1f);
            if (v != b.at(t)) {
                differing++;
            }
        }
        assertThat(differing).isGreaterThan(200);
        assertThat(Jitter.MIDPOINT.at(123L)).isEqualTo(1f);
    }

    @Test
    void aCropOutsideItsClimateDiesAtTheEndOfItsFirstStep() {
        // Soybean needs hydration from 25. At hydration 0 the step is not healthy at either end, so it dies at the end of the step.
        CropRules.ClimateSource dry = t -> new CropRules.Sample(0, 15f);
        Progress p = CropRules.advance(CropState.planted(START), CropType.SOYBEAN.spec(1f, 1f, 1f), dry,
            CropGoldenTest.feed(CropType.SOYBEAN), Jitter.MIDPOINT, START + 3 * I);
        assertThat(p).isEqualTo(new Dead(START + I, false));
    }

    @Test
    void growthIsMonotoneWhileAliveAndTheYieldStaysInRange() {
        for (CropType crop : CropType.values()) {
            CropState state = CropState.planted(START);
            float lastGrowth = 0f;
            for (int day = 1; day <= 60; day++) {
                Progress p = CropRules.advance(state, crop.spec(1f, 1f, 1f), CropGoldenTest.schedule(0), CropGoldenTest.feed(crop),
                    Jitter.seeded(9L), START + day * (long) CalendarTicks.CALENDAR_TICKS_IN_DAY);
                if (!(p instanceof Alive alive)) {
                    break;
                }
                state = alive.state();
                assertThat(state.growth()).isBetween(lastGrowth, 1f);
                assertThat(state.yield()).isBetween(0f, 60f);
                lastGrowth = state.growth();
            }
        }
    }

    @Test
    void aCropWithoutLightNeverGrowsAndExpires() {
        CropType crop = CropType.CARROT;
        Progress p = CropRules.advance(CropState.planted(START), crop.spec(1f, 1f, 0f), CropGoldenTest.schedule(0),
            CropGoldenTest.feed(crop), Jitter.MIDPOINT, START + 200 * I);
        assertThat(p).isInstanceOf(Dead.class);
        assertThat(((Dead) p).fullyGrown()).isFalse();
        // Before it expires, it stays at zero growth.
        Progress early = CropRules.advance(CropState.planted(START), crop.spec(1f, 1f, 0f), CropGoldenTest.schedule(0),
            CropGoldenTest.feed(crop), Jitter.MIDPOINT, START + I);
        assertThat(early).isInstanceOf(Alive.class);
        assertThat(((Alive) early).state().growth()).isEqualTo(0f);
        assertThat(((Alive) early).state().expiry()).isGreaterThan(0f);
    }

    @Test
    void stagesAreMonotoneAndTheTopBlockAppearsAboveTheBottomStages() {
        for (CropType crop : CropType.values()) {
            CropStages stages = crop.stages();
            int lastAge = -1;
            for (int i = 0; i <= 1000; i++) {
                CropStages.Stage stage = stages.stage(i / 1000f);
                assertThat(stage.age()).isBetween(0, stages.maxAge());
                assertThat(stage.age()).isGreaterThanOrEqualTo(lastAge);
                assertThat(stage.top()).isEqualTo(stages.doubled() && stage.age() > stages.maxSingleAge());
                lastAge = stage.age();
            }
            assertThat(stages.stage(1f).age()).isEqualTo(stages.maxAge());
        }
    }

    @Test
    void pickFruitFollowsTheStageAndTheFirstFruitFlag() {
        assertThat(CropHarvest.fruit(6, 7, true)).isEqualTo(CropHarvest.Fruit.FIRST);
        assertThat(CropHarvest.fruit(6, 7, false)).isEqualTo(CropHarvest.Fruit.NONE);
        assertThat(CropHarvest.fruit(7, 7, false)).isEqualTo(CropHarvest.Fruit.SECOND);
        assertThat(CropHarvest.fruit(7, 7, true)).isEqualTo(CropHarvest.Fruit.SECOND);
        assertThat(CropHarvest.fruit(3, 7, true)).isEqualTo(CropHarvest.Fruit.NONE);
    }

    @Test
    void growthLimitNeedsLightAndFluid() {
        assertThat(CropRules.growthLimit(11, true)).isEqualTo(0f);
        assertThat(CropRules.growthLimit(12, true)).isEqualTo(1f);
        assertThat(CropRules.growthLimit(15, false)).isEqualTo(0f);
    }

    @Test
    void cropTypesHaveUniqueSerializedNames() {
        assertThat(CropType.values()).extracting(CropType::serializedName).doesNotHaveDuplicates().hasSize(29);
    }

    private static Progress advance(CropType crop, int sched, long from, long to) {
        return CropRules.advance(CropState.planted(from), crop.spec(1f, 1f, 1f), CropGoldenTest.schedule(sched),
            CropGoldenTest.feed(crop), Jitter.seeded(42L), to);
    }

}
