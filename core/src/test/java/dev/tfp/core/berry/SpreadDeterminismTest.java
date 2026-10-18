/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (growAndPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (onUpdate)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.berry.SpreadRules.Surroundings;
import dev.tfp.core.calendar.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The spread, sapling and cranberry decisions are functions of their inputs only: the random draws are passed in, and nothing is read from
 * a shared state. The tests use fixed inputs from a fixed generator (no clock, no {@code Random}), and check that the same inputs give the
 * same decisions in order, in reverse order, and across parallel threads.
 */
class SpreadDeterminismTest {
    private static final int CASES = 512;
    private static final long NOW = 10_000_000L;

    /** One spread decision: species, stage, drawn side, stop roll and the surroundings. */
    private record SpreadInput(SpreadingSpecies species, int stage, Side side, boolean roll, Surroundings surroundings) {
    }

    /** One sapling decision: tree, month, elapsed ticks, climate, saplings, on-branch and roll. */
    private record SaplingInput(FruitTree tree, Month month, long elapsed, boolean climateOk, int saplings, boolean onBranch, boolean roll) {
    }

    /** A 64-bit linear congruential step with fixed constants (Knuth's MMIX). */
    private static long next(long state) {
        return state * 6364136223846793005L + 1442695040888963407L;
    }

    private static List<SpreadInput> spreadInputs() {
        List<SpreadInput> inputs = new ArrayList<>();
        long state = 0x5EED0F0F0FL;
        SpreadingSpecies[] species = SpreadingSpecies.values();
        for (int i = 0; i < CASES; i++) {
            state = next(state);
            SpreadingSpecies s = species[(int) ((state >>> 33) % species.length)];
            state = next(state);
            int stage = (int) ((state >>> 33) % 3);
            state = next(state);
            Side side = Side.values()[(int) ((state >>> 33) % 4)];
            state = next(state);
            boolean roll = ((state >>> 40) & 1) == 1;
            state = next(state);
            EnumSet<Side> empty = EnumSet.noneOf(Side.class);
            for (Side candidate : Side.values()) {
                if (((state >>> (8 + candidate.ordinal())) & 1) == 1) {
                    empty.add(candidate);
                }
            }
            state = next(state);
            boolean above = ((state >>> 40) & 1) == 1;
            int ground = 1 + (int) ((state >>> 33) % s.maxHeight());
            state = next(state);
            int count = (int) ((state >>> 33) % 20);
            inputs.add(new SpreadInput(s, stage, side, roll, new Surroundings(empty, above, ground, count)));
        }
        return inputs;
    }

    private static List<SaplingInput> saplingInputs() {
        List<SaplingInput> inputs = new ArrayList<>();
        long state = 0xFACE_BEEFL;
        FruitTree[] trees = FruitTree.values();
        for (int i = 0; i < CASES; i++) {
            state = next(state);
            FruitTree tree = trees[(int) ((state >>> 33) % trees.length)];
            state = next(state);
            Month month = Month.valueOf((int) ((state >>> 33) % 12));
            state = next(state);
            long ttg = FruitTreeRules.ticksToGrow(tree, 1.0);
            long elapsed = ttg - 2 + (state >>> 33) % (ttg / 2 + 4);
            state = next(state);
            boolean climate = ((state >>> 40) & 1) == 1;
            int saplings = (int) ((state >>> 33) % 5);
            state = next(state);
            boolean onBranch = ((state >>> 40) & 1) == 1;
            boolean roll = ((state >>> 41) & 1) == 1;
            inputs.add(new SaplingInput(tree, month, elapsed, climate, saplings, onBranch, roll));
        }
        return inputs;
    }

    private static SpreadRules.SpreadStep spread(SpreadInput in) {
        return SpreadRules.spread(in.stage(), in.species().maxHeight(), in.side(), in.roll(), in.surroundings());
    }

    private static FruitTreeRules.SaplingTick sapling(SaplingInput in) {
        return FruitTreeRules.saplingTick(in.tree(), in.month(), in.elapsed(), FruitTreeRules.ticksToGrow(in.tree(), 1.0),
            in.climateOk(), in.saplings(), in.onBranch(), in.roll());
    }

    @Test
    void theSpreadDecisionIsTheSameInOrderInReverseAndAcrossThreads() {
        List<SpreadInput> inputs = spreadInputs();
        List<SpreadRules.SpreadStep> sequential = inputs.stream().map(SpreadDeterminismTest::spread).toList();

        List<SpreadInput> reversed = new ArrayList<>(inputs);
        Collections.reverse(reversed);
        List<SpreadRules.SpreadStep> backwards = new ArrayList<>(reversed.stream().map(SpreadDeterminismTest::spread).toList());
        Collections.reverse(backwards);
        assertThat(backwards).isEqualTo(sequential);

        List<SpreadRules.SpreadStep> parallel = IntStream.range(0, CASES * 8).parallel()
            .mapToObj(i -> spread(inputs.get(i % CASES)))
            .toList();
        for (int i = 0; i < parallel.size(); i++) {
            assertThat(parallel.get(i)).isEqualTo(sequential.get(i % CASES));
        }
    }

    @Test
    void theSaplingDecisionIsTheSameInOrderAndAcrossThreads() {
        List<SaplingInput> inputs = saplingInputs();
        List<FruitTreeRules.SaplingTick> sequential = inputs.stream().map(SpreadDeterminismTest::sapling).toList();
        List<FruitTreeRules.SaplingTick> parallel = IntStream.range(0, CASES * 8).parallel()
            .mapToObj(i -> sapling(inputs.get(i % CASES)))
            .toList();
        for (int i = 0; i < parallel.size(); i++) {
            assertThat(parallel.get(i)).isEqualTo(sequential.get(i % CASES));
        }
    }

    @Test
    void theCranberryCatchUpIsTheSameAcrossThreads() {
        BerryRules.ClimateSource climate = tick -> new BerryRules.Sample(60 + (int) (Math.floorMod(tick, 7L)), 5f);
        BerryClock clock = new BerryClock(NOW, 2L * 24000 * 8 * 12, 8, true);
        List<BerryBush> bushes = List.of(
            new BerryBush(Lifecycle.HEALTHY, 0, 24, NOW - 3_000_000L, NOW - 1L),
            new BerryBush(Lifecycle.FLOWERING, 1, 10, NOW - 500_000L, NOW - 400_000L),
            new BerryBush(Lifecycle.DORMANT, 2, 0, NOW - 90_000L, NOW - 2_000_000L));
        for (BerryBush bush : bushes) {
            for (boolean waterlogged : new boolean[] {false, true}) {
                BerryRules.Outcome expected = CranberryRules.catchUp(bush, NOW, waterlogged, clock, climate, BerryRules.Settings.DEFAULT);
                List<BerryRules.Outcome> parallel = IntStream.range(0, 256).parallel()
                    .mapToObj(i -> CranberryRules.catchUp(bush, NOW, waterlogged, clock, climate, BerryRules.Settings.DEFAULT))
                    .toList();
                assertThat(parallel).allSatisfy(outcome -> assertThat(outcome).isEqualTo(expected));
            }
        }
    }

    @Test
    void theHandCheckedDecisionsHold() {
        Surroundings empty = new Surroundings(EnumSet.allOf(Side.class), true, 1, 0);
        // A side growth, with a roll that asks for more growth and more than ten nearby bushes: the cane keeps the bush growing.
        assertThat(SpreadRules.spread(1, 4, Side.EAST, true, new Surroundings(EnumSet.of(Side.EAST), true, 1, 11)))
            .isEqualTo(new SpreadRules.SpreadStep(SpreadRules.Kind.CANE, Side.EAST, false));
        // The same with a roll that does not ask for more growth: the bush stops.
        assertThat(SpreadRules.spread(1, 4, Side.EAST, false, new Surroundings(EnumSet.of(Side.EAST), true, 1, 11)))
            .isEqualTo(new SpreadRules.SpreadStep(SpreadRules.Kind.CANE, Side.EAST, true));
        // A blocked side with room above and ground under the maximum height grows upward.
        assertThat(SpreadRules.spread(1, 3, Side.NORTH, true, new Surroundings(EnumSet.noneOf(Side.class), true, 2, 0)))
            .isEqualTo(new SpreadRules.SpreadStep(SpreadRules.Kind.BUSH_UP, Side.NORTH, true));
        // Ground at the maximum height stops the upward growth.
        assertThat(SpreadRules.spread(1, 3, Side.NORTH, true, new Surroundings(EnumSet.noneOf(Side.class), true, 3, 0)))
            .isEqualTo(new SpreadRules.SpreadStep(SpreadRules.Kind.NONE, Side.NORTH, false));
        // Stage 0 always becomes stage 1; a fully grown bush never spreads.
        assertThat(SpreadRules.spread(0, 4, Side.SOUTH, false, empty).kind()).isEqualTo(SpreadRules.Kind.STAGE_UP);
        assertThat(SpreadRules.spread(2, 4, Side.SOUTH, true, empty).kind()).isEqualTo(SpreadRules.Kind.NONE);
    }
}
