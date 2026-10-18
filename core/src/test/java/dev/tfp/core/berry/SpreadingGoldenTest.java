/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (golden oracle: growAndPropagate,
 *       maybeStopGrowing),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java @ c3b4d9536 (golden oracle: growAndPropagate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (golden oracle: distanceToGround),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (golden oracle: randomTick,
 *       createTree),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (golden oracle: tick cycles,
 *       randomTick reset gate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java @ c3b4d9536 (golden oracle: onUpdate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (golden oracle: onUpdate)
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.berry.SpreadRules.Surroundings;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ClimateRange;
import dev.tfp.core.calendar.Month;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * M3-FARM-03 golden values for the spreading bushes, canes, cranberry and fruit trees, from TFC at c3b4d9536. The rows are in
 * {@code spreading-golden.txt}, written by a scratch oracle that reads TFC's {@code FruitBlocks} tables and the climate JSON, and
 * transcribes the decisions literally. Row kinds and codes:
 *
 * <ul>
 *   <li>{@code SPREAD_SPECIES}, {@code BUSH}, {@code TREE}: name, growth time or maximum height, the lifecycle of each month (D, H, F, R),
 *       and the climate range (min and max hydration, min and max temperature).</li>
 *   <li>{@code TICKS}: a tree's growth time, and the same at a sapling modifier of 0.5 ({@code (long)(ticks * 0.5)}).</li>
 *   <li>{@code SPREAD}: species, stage, drawn side, the stop roll, then the decision for every combination of empty sides (bit i for
 *       N, E, S, W), the space above, the ground distance and the nearby count (0 or 11). Codes: S stage up, K a cane that keeps growing,
 *       C a cane that stops the bush, U the bush grows up, N nothing.</li>
 *   <li>{@code CANE}: stage and survival of the bush at the cane's position. Codes: S stage up, V converts to a bush, N nothing.</li>
 *   <li>{@code DIST}: distance, then for each pattern of bush blocks below (bit i-1 for the block i below) the distance to the ground.</li>
 *   <li>{@code SAPLING}: tree, then the decision for every month, elapsed ticks (growth time -1, +0, +1, +5000), climate, saplings,
 *       on-branch and roll. Codes: W waits, X withers, a digit is a branch with that sapling count at stage 0, a letter a+count at stage 1.</li>
 *   <li>{@code BRANCH}, {@code BRANCH_RESET}, {@code BRANCH_TICKS}: the growth cycles of a counter, the reset gate and the random tick gate.</li>
 *   <li>{@code LEAVES}: persistent, climate, then the next stage for every stored stage, expected stage and delta (the pick bloom delay,
 *       and one more tick). Codes are the stage letters.</li>
 *   <li>{@code CRANBERRY_DRY}: the stored stage, and the stage of a dry cranberry (always D).</li>
 * </ul>
 */
class SpreadingGoldenTest {
    private static final String RESOURCE = "/dev/tfp/core/berry/spreading-golden.txt";
    private static final long NOW = 10_000_000L;
    private static final long PICK_BLOOM = BerryRules.PICK_BLOOM_DELAY_TICKS;

    private static final Map<String, List<String[]>> ROWS = load();

    private static Map<String, List<String[]>> load() {
        try (InputStream in = SpreadingGoldenTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing golden resource " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\\|", -1))
                .collect(Collectors.groupingBy(fields -> fields[0], Collectors.toList()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String[]> rows(String kind) {
        return ROWS.getOrDefault(kind, List.of());
    }

    private static char lifecycleCode(Lifecycle lifecycle) {
        return switch (lifecycle) {
            case DORMANT -> 'D';
            case HEALTHY -> 'H';
            case FLOWERING -> 'F';
            case FRUITING -> 'R';
        };
    }

    private static String lifecycleCodes(IntFunction<Lifecycle> lifecycleOfMonth) {
        StringBuilder sb = new StringBuilder();
        for (int month = 0; month < 12; month++) {
            sb.append(lifecycleCode(lifecycleOfMonth.apply(month)));
        }
        return sb.toString();
    }

    private static void assertClimate(ClimateRange range, String[] fields, int from) {
        assertThat(range.getMinHydration(false)).isEqualTo(Integer.parseInt(fields[from]));
        assertThat(range.getMaxHydration(false)).isEqualTo(Integer.parseInt(fields[from + 1]));
        assertThat(range.getMinTemperature(false)).isEqualTo(Float.parseFloat(fields[from + 2]));
        assertThat(range.getMaxTemperature(false)).isEqualTo(Float.parseFloat(fields[from + 3]));
    }

    private static char spreadCode(SpreadRules.SpreadStep step) {
        return switch (step.kind()) {
            case STAGE_UP -> 'S';
            case CANE -> step.stopAtStageTwo() ? 'C' : 'K';
            case BUSH_UP -> 'U';
            case NONE -> 'N';
        };
    }

    private static char caneCode(SpreadRules.CaneKind kind) {
        return switch (kind) {
            case STAGE_UP -> 'S';
            case CONVERT_TO_BUSH -> 'V';
            case NONE -> 'N';
        };
    }

    @Test
    void theSpreadingSpeciesTablesAndClimateAreTfcs() {
        List<String[]> rows = rows("SPREAD_SPECIES");
        assertThat(rows).hasSize(4);
        for (String[] f : rows) {
            SpreadingSpecies species = SpreadingSpecies.valueOf(f[1]);
            assertThat(species.maxHeight()).isEqualTo(Integer.parseInt(f[2]));
            assertThat(lifecycleCodes(m -> species.lifecycleFor(Month.valueOf(m)))).isEqualTo(f[3]);
            assertClimate(species.climate(), f, 4);
        }
    }

    @Test
    void theCranberryTableAndClimateAreTfcs() {
        List<String[]> rows = rows("BUSH");
        assertThat(rows).hasSize(1);
        String[] f = rows.get(0);
        assertThat(f[1]).isEqualTo("CRANBERRY");
        assertThat(lifecycleCodes(m -> BushSpecies.CRANBERRY.lifecycleFor(Month.valueOf(m)))).isEqualTo(f[2]);
        assertClimate(BushSpecies.CRANBERRY.climate(), f, 3);
    }

    @Test
    void theFruitTreeTablesClimateAndGrowthTimeAreTfcs() {
        List<String[]> trees = rows("TREE");
        assertThat(trees).hasSize(8);
        for (String[] f : trees) {
            FruitTree tree = FruitTree.valueOf(f[1]);
            assertThat(tree.defaultTicksToGrow()).isEqualTo(Integer.parseInt(f[2]) * CalendarTicks.CALENDAR_TICKS_IN_DAY);
            assertThat(lifecycleCodes(m -> tree.lifecycleFor(Month.valueOf(m)))).isEqualTo(f[3]);
            assertClimate(tree.climate(), f, 4);
        }
        for (String[] f : rows("TICKS")) {
            FruitTree tree = FruitTree.valueOf(f[1]);
            assertThat(FruitTreeRules.ticksToGrow(tree, 1.0)).isEqualTo(Long.parseLong(f[2]));
            assertThat(FruitTreeRules.ticksToGrow(tree, 0.5)).isEqualTo(Long.parseLong(f[3]));
        }
    }

    @Test
    void theSpreadDecisionMatchesTfcForEveryFact() {
        int checked = 0;
        for (String[] f : rows("SPREAD")) {
            SpreadingSpecies species = SpreadingSpecies.valueOf(f[1]);
            int stage = Integer.parseInt(f[2]);
            Side side = Side.valueOf(switch (f[3]) {
                case "N" -> "NORTH";
                case "E" -> "EAST";
                case "S" -> "SOUTH";
                default -> "WEST";
            });
            boolean roll = "T".equals(f[4]);
            StringBuilder codes = new StringBuilder();
            for (int mask = 0; mask < 16; mask++) {
                for (int above = 0; above <= 1; above++) {
                    for (int ground = 1; ground <= species.maxHeight(); ground++) {
                        for (int count : new int[] {0, 11}) {
                            EnumSet<Side> empty = EnumSet.noneOf(Side.class);
                            for (int i = 0; i < 4; i++) {
                                if (((mask >> i) & 1) == 1) {
                                    empty.add(Side.values()[i]);
                                }
                            }
                            SpreadRules.SpreadStep step = SpreadRules.spread(stage, species.maxHeight(), side, roll,
                                new Surroundings(empty, above == 1, ground, count));
                            codes.append(spreadCode(step));
                        }
                    }
                }
            }
            assertThat(codes.toString()).as("%s stage %s side %s roll %s", f[1], f[2], f[3], f[4]).isEqualTo(f[5]);
            checked++;
        }
        assertThat(checked).isEqualTo(40);
    }

    @Test
    void theCaneStepMatchesTfc() {
        List<String[]> rows = rows("CANE");
        assertThat(rows).hasSize(6);
        for (String[] f : rows) {
            int stage = Integer.parseInt(f[1]);
            boolean survives = "T".equals(f[2]);
            assertThat(String.valueOf(caneCode(SpreadRules.caneStep(stage, survives)))).as(String.join("|", f)).isEqualTo(f[3]);
        }
    }

    @Test
    void theGroundDistanceMatchesTfcForEveryPattern() {
        List<String[]> rows = rows("DIST");
        assertThat(rows).hasSize(4);
        for (String[] f : rows) {
            int distance = Integer.parseInt(f[1]);
            StringBuilder digits = new StringBuilder();
            for (int pattern = 0; pattern < 16; pattern++) {
                final int p = pattern;
                digits.append(SpreadRules.distanceToGround(i -> ((p >> (i - 1)) & 1) == 1, distance));
            }
            assertThat(digits.toString()).as("distance %s", distance).isEqualTo(f[2]);
        }
    }

    @Test
    void theSaplingDecisionMatchesTfcForEveryFact() {
        for (String[] f : rows("SAPLING")) {
            FruitTree tree = FruitTree.valueOf(f[1]);
            long ttg = FruitTreeRules.ticksToGrow(tree, 1.0);
            StringBuilder codes = new StringBuilder();
            for (int month = 0; month < 12; month++) {
                for (long elapsed : new long[] {ttg - 1, ttg, ttg + 1, ttg + 5000}) {
                    for (boolean climateOk : new boolean[] {false, true}) {
                        for (int saplings = 0; saplings <= 4; saplings++) {
                            for (boolean onBranch : new boolean[] {false, true}) {
                                for (boolean roll : new boolean[] {false, true}) {
                                    FruitTreeRules.SaplingTick tick = FruitTreeRules.saplingTick(tree, Month.valueOf(month), elapsed, ttg,
                                        climateOk, saplings, onBranch, roll);
                                    codes.append(saplingCode(tick));
                                }
                            }
                        }
                    }
                }
            }
            assertThat(codes.toString()).as(tree.name()).isEqualTo(f[2]);
        }
    }

    private static char saplingCode(FruitTreeRules.SaplingTick tick) {
        if (tick instanceof FruitTreeRules.Waiting) {
            return 'W';
        }
        if (tick instanceof FruitTreeRules.Withered) {
            return 'X';
        }
        FruitTreeRules.Grown grown = (FruitTreeRules.Grown) tick;
        return grown.stage() == 0 ? (char) ('0' + grown.saplings()) : (char) ('a' + grown.saplings());
    }

    @Test
    void theBranchCycleGateMatchesTfc() {
        // TFC: 5 * ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY (28800 player ticks a default day).
        assertThat(FruitTreeRules.FRUIT_BRANCH_GROWTH_TICKS).isEqualTo(144_000L);
        for (String[] f : rows("BRANCH")) {
            assertThat(FruitTreeRules.branchCycles(Long.parseLong(f[1]), FruitTreeRules.FRUIT_BRANCH_GROWTH_TICKS))
                .as(f[1]).isEqualTo(Long.parseLong(f[2]));
        }
        for (String[] f : rows("BRANCH_RESET")) {
            boolean reset = FruitTreeRules.branchResets("T".equals(f[1]), "T".equals(f[2]));
            assertThat(reset ? "R" : "G").as(String.join("|", f)).isEqualTo(f[3]);
        }
        for (String[] f : rows("BRANCH_TICKS")) {
            assertThat(FruitTreeRules.branchRandomTicks(Integer.parseInt(f[1]))).as(f[1]).isEqualTo("T".equals(f[2]));
        }
    }

    @Test
    void theLeavesLifecycleMatchesTfc() {
        assertThat(PICK_BLOOM).isEqualTo(10L * 60 * 20 * 24);
        for (String[] f : rows("LEAVES")) {
            boolean persistent = "T".equals(f[1]);
            boolean climateOk = "T".equals(f[2]);
            StringBuilder codes = new StringBuilder();
            for (Lifecycle stored : Lifecycle.values()) {
                for (Lifecycle expected : Lifecycle.values()) {
                    for (long delta : new long[] {PICK_BLOOM, PICK_BLOOM + 1}) {
                        // lastPicked is 0 and now is delta, so now - lastPicked is delta.
                        codes.append(lifecycleCode(FruitTreeRules.leavesLifecycle(persistent, climateOk, stored, expected, delta, 0L,
                            PICK_BLOOM)));
                    }
                }
            }
            assertThat(codes.toString()).as(String.join("|", f)).isEqualTo(f[3]);
        }
    }

    @Test
    void aDryCranberryIsDormantAndKeepsItsGrowth() {
        assertThat(rows("CRANBERRY_DRY")).hasSize(4);
        // In range at every sampled tick: the climate does not decide here, only the water does.
        BerryRules.ClimateSource inRange = tick -> new BerryRules.Sample(60, 5f);
        BerryClock clock = new BerryClock(NOW, 0L, 8, true);
        for (String[] f : rows("CRANBERRY_DRY")) {
            BerryBush bush = new BerryBush(lifecycleOf(f[1].charAt(0)), 1, 10, NOW - 1000, NOW - 5);
            BerryRules.Outcome outcome = CranberryRules.catchUp(bush, NOW, false, clock, inRange, BerryRules.Settings.DEFAULT);
            assertThat(outcome).isInstanceOf(BerryRules.Alive.class);
            BerryRules.Alive alive = (BerryRules.Alive) outcome;
            assertThat(String.valueOf(lifecycleCode(alive.bush().lifecycle()))).as(String.join("|", f)).isEqualTo(f[2]);
            assertThat(alive.bush()).isEqualTo(new BerryBush(Lifecycle.DORMANT, 1, 10, NOW - 1000, NOW - 5));
            assertThat(alive.propagations()).isEmpty();
        }
    }

    private static Lifecycle lifecycleOf(char code) {
        return switch (code) {
            case 'D' -> Lifecycle.DORMANT;
            case 'H' -> Lifecycle.HEALTHY;
            case 'F' -> Lifecycle.FLOWERING;
            default -> Lifecycle.FRUITING;
        };
    }
}
