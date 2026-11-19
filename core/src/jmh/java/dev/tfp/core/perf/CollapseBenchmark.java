/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.perf;

import dev.tfp.core.collapse.CollapseCell;
import dev.tfp.core.collapse.CollapseKinds;
import dev.tfp.core.collapse.CollapseRandom;
import dev.tfp.core.collapse.CollapseRules;
import dev.tfp.core.collapse.CollapseView;
import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.collapse.TriggerDecision;
import dev.tfp.core.world.BlockPos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** PERF-COL-01: the collapse trigger decision for a mined block in a typical cave (13 x 7 x 13 box, 50 % raw rock). */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CollapseBenchmark {
    private static final String RAW = "tfc:rock/raw/granite";
    private static final String SMOOTH = "tfc:rock/smooth/granite";
    private static final String ORE = "tfc:ore/poor_hematite/granite";
    private static final String OAK = "tfc:wood/horizontal_support/oak";

    private CollapseRules rules;
    private CollapseView view;
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    /** 0.2 is "not real, fake" and the radii are the maximum, so every candidate in the full 9 x 5 x 9 box is examined. */
    private static final CollapseRandom REAL = new CollapseRandom() {
        @Override
        public float nextFloat() {
            return 0.2f;
        }

        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    };

    @Setup
    public void setup() {
        CollapseKinds kinds = CollapseKinds.builder()
            .canStart(RAW, ORE).canTrigger(RAW, SMOOTH, ORE).notSolidSupporting(SMOOTH)
            .canCollapse(RAW, SMOOTH, ORE).toughness(RAW, 2).toughness(SMOOTH, 2).recipe("tfc:rock/cobble/granite", RAW, SMOOTH, ORE)
            .build();
        rules = new CollapseRules(kinds, new SupportTable(List.of(new SupportTable.Support(Set.of(OAK), 2, 2, 4))),
            CollapseRules.DEFAULT_TRIGGER_CHANCE, CollapseRules.DEFAULT_FAKE_TRIGGER_CHANCE);
        Map<BlockPos, CollapseCell> cells = new HashMap<>();
        long state = 12345;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    state = state * 6364136223846793005L + 1442695040888963407L;
                    int r = (int) ((state >>> 33) % 10);
                    String kind = dy >= 1 ? "minecraft:air" : r < 5 ? RAW : r < 7 ? SMOOTH : r < 8 ? ORE : "minecraft:air";
                    boolean full = !kind.equals("minecraft:air");
                    cells.put(new BlockPos(dx, dy, dz), new CollapseCell(kind, full, full));
                }
            }
        }
        cells.put(ORIGIN, new CollapseCell(RAW, true, true));
        CollapseCell air = new CollapseCell("minecraft:air", false, false);
        view = pos -> cells.getOrDefault(pos, air);
    }

    @Benchmark
    public TriggerDecision triggerDecision() {
        return rules.trigger(view, ORIGIN, REAL);
    }
}
