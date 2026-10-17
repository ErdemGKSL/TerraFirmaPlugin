/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.weather.SnowRule.Change;
import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Golden values of {@link SnowRule#accumulate} and {@link SnowRule#removeSnowAt} from TFC at the pinned commit (c3b4d9536).
 *
 * <p>The oracle: TFC's {@code WeatherHelpers.handleSnowAccumulation}, {@code placeSnowOrSnowPile},
 * {@code placeSnowOrSnowPileAt}, {@code findOptimalSnowLocation}, {@code findIcicleLocation}, {@code removeSnowAt},
 * {@code isSnow}, {@code isIce}, {@code SnowPileBlock.canPlaceSnowPile}/{@code placeSnowPile}/{@code removePileOrSnow},
 * {@code IcePileBlock.placeIcePileOrIce}/{@code removeIcePileOrIce}, {@code EnvironmentHelpers.isAdjacentToNotWater} and
 * {@code Helpers.hash} were copied into a scratch harness with the block state and level swapped for stubs over a
 * {@link SnowScenario} (each tag test, fluid test, {@code canSurvive}, {@code isFaceSturdy}, {@code canSeeSky} and
 * {@code getHeight} reads the scenario's classes and flags; the pile block entity keeps the hidden states). Minecraft
 * 1.21.11's own {@code BlockPos}, {@code Direction} and {@code Direction.Plane.HORIZONTAL.getRandomDirection} ran from the
 * server jar, with {@code level.random} a {@code RandomSource} over {@code java.util.Random(rnd)}. Krummholz and cauldrons
 * are not in the scenarios. Each scenario {@code i} is seeded {@code i * 0x9E3779B97F4A7C15L + 12345L}; the oracle ran
 * {@code handleSnowAccumulation} at the surface and {@code removeSnowAt} at the surface and the three positions below it,
 * each on a fresh copy, and printed the blocks that changed (sorted by position) and, after accumulation, the random's next
 * {@code nextInt()} (so the number of draws matches too). The lines are chained in an FNV-1a 64 hash.
 */
class SnowRuleGoldenTest {
    private static final int SCENARIOS = 20_000;
    /** From the oracle: 20,000 scenarios, accumulation and four melts each. */
    private static final long HASH = 0x952df6972e6b538bL;
    private static final int NON_EMPTY_ACCUMULATIONS = 13_814;
    /** From the oracle: the changed blocks by their new class ("A" accumulation, "M" melt). */
    private static final Map<String, Integer> HISTOGRAM = Map.ofEntries(
        Map.entry("A:AIR", 283), Map.entry("A:ICE", 706), Map.entry("A:ICE_PILE", 940), Map.entry("A:ICICLE", 139),
        Map.entry("A:SNOW", 10_616), Map.entry("A:SNOW_PILE", 1_461), Map.entry("M:AIR", 9_892),
        Map.entry("M:ICE_PILEABLE", 308), Map.entry("M:ICE_PILEABLE_WATER", 926), Map.entry("M:ICICLE", 144),
        Map.entry("M:SNOW", 1_053), Map.entry("M:SNOW_PILE", 1_083), Map.entry("M:SNOW_PILEABLE", 3_195),
        Map.entry("M:WATER", 2_651));
    /** From the oracle: the first lines, verbatim (melt lines only when something changed). */
    private static final List<String> FIRST_LINES = List.of(
        "0|A|963,1,-699=SNOW/1/false/null/null;|562893535",
        "1|A|317,-8,863=SNOW/1/false/null/null;|-98379250",
        "2|A||1023259922",
        "3|A|898,3,-83=ICE/0/false/null/null;|1062159149",
        "3|M0|898,5,-83=WATER/0/false/null/null;",
        "4|A||1647186077",
        "4|M1|-856,15,338=ICE_PILEABLE_WATER/0/false/null/null;",
        "5|A|-814,-4,-66=SNOW/1/false/null/null;|-2059228754",
        "5|M3|-814,-5,-66=AIR/0/false/null/null;",
        "6|A|103,4,124=SNOW/1/false/null/null;|170831792",
        "6|M0|103,4,125=AIR/0/false/null/null;",
        "7|A|599,-4,732=SNOW/1/false/null/null;|1605998047",
        "7|M0|600,-4,732=SNOW_PILEABLE/0/false/null/null;",
        "8|A|-485,17,-221=SNOW/1/false/null/null;|770292984",
        "8|M2|-485,15,-221=SNOW_PILEABLE/0/false/null/null;",
        "8|M3|-485,14,-221=AIR/0/false/null/null;",
        "9|A||1488266712",
        "9|M1|-617,-7,-133=AIR/0/false/null/null;");

    @Test
    void matchesTfcOverTwentyThousandScenarios() {
        long hash = SnowScenario.FNV_START;
        int nonEmpty = 0;
        Map<String, Integer> histogram = new TreeMap<>();
        List<String> first = new ArrayList<>();
        for (int i = 0; i < SCENARIOS; i++) {
            SnowScenario s = new SnowScenario(i * 0x9E3779B97F4A7C15L + 12345L);
            World w = new World(s);
            Random random = new Random(s.rnd);
            for (Change c : SnowRule.accumulate(w, new BlockPos(s.sx, s.sy, s.sz), random, SnowRule.UNLIMITED)) {
                w.apply(c);
            }
            String d = w.diff();
            String line = i + "|A|" + d + "|" + random.nextInt();
            hash = SnowScenario.fnv(hash, line);
            if (!d.isEmpty()) {
                nonEmpty++;
            }
            count(histogram, "A", d);
            if (i < 10) {
                first.add(line);
            }
            for (int m = 0; m < 4; m++) {
                World mw = new World(s);
                for (Change c : SnowRule.removeSnowAt(mw, new BlockPos(s.sx, s.sy - m, s.sz))) {
                    mw.apply(c);
                }
                String md = mw.diff();
                String mline = i + "|M" + m + "|" + md;
                hash = SnowScenario.fnv(hash, mline);
                count(histogram, "M", md);
                if (i < 10 && !md.isEmpty()) {
                    first.add(mline);
                }
            }
        }
        assertThat(first).containsExactlyElementsOf(FIRST_LINES);
        assertThat(histogram).containsExactlyInAnyOrderEntriesOf(HISTOGRAM);
        assertThat(nonEmpty).isEqualTo(NON_EMPTY_ACCUMULATIONS);
        assertThat(hash).isEqualTo(HASH);
    }

    private static void count(Map<String, Integer> histogram, String op, String d) {
        for (String part : d.split(";")) {
            if (part.isEmpty()) {
                continue;
            }
            String kind = part.substring(part.indexOf('=') + 1);
            histogram.merge(op + ":" + kind.substring(0, kind.indexOf('/')), 1, Integer::sum);
        }
    }

    /**
     * The scenario as a {@link SnowRule.View}, with the changes applied as TFC's {@code setBlock} calls would leave the
     * blocks (a new block keeps the position's flags; only piles keep hidden states; only icicles keep the tip).
     */
    static final class World implements SnowRule.View {
        private final SnowScenario s;
        private final Map<Long, SnowScenario.C> changed = new TreeMap<>();

        World(SnowScenario s) {
            this.s = s;
        }

        SnowScenario.C get(BlockPos p) {
            SnowScenario.C c = changed.get(SnowScenario.key(p.x(), p.y(), p.z()));
            return c != null ? c : s.at(p.x(), p.y(), p.z());
        }

        @Override
        public SnowRule.Cell at(BlockPos p) {
            SnowScenario.C c = get(p);
            return new SnowRule.Cell(SnowRule.Block.valueOf(c.kind), c.layers, c.water, c.survives, c.sturdy, c.holds, c.sky);
        }

        @Override
        public int height(int x, int z) {
            return s.height(x, z);
        }

        private void set(BlockPos p, SnowScenario.C next) {
            SnowScenario.C old = get(p);
            SnowScenario.C c = next.copy();
            c.water = old.water;
            c.survives = old.survives;
            c.sturdy = old.sturdy;
            c.holds = old.holds;
            c.sky = old.sky;
            if (!c.kind.equals("SNOW_PILE") && !c.kind.equals("ICE_PILE")) {
                c.hidden = null;
                c.hiddenAbove = null;
            }
            if (!c.kind.equals("ICICLE")) {
                c.tip = false;
            }
            changed.put(SnowScenario.key(p.x(), p.y(), p.z()), c);
        }

        private static SnowScenario.C block(String kind, int layers) {
            SnowScenario.C c = new SnowScenario.C();
            c.kind = kind;
            c.layers = layers;
            return c;
        }

        void apply(Change ch) {
            BlockPos p = ch.pos();
            BlockPos above = p.offset(0, 1, 0);
            SnowScenario.C old = get(p);
            switch (ch.kind()) {
                case PLACE_SNOW -> set(p, block("SNOW", 1));
                case PLACE_SNOW_PILE, PLACE_ICE_PILE -> {
                    boolean snow = ch.kind() == SnowRule.Kind.PLACE_SNOW_PILE;
                    SnowScenario.C pile = block(snow ? "SNOW_PILE" : "ICE_PILE", snow ? 1 : 0);
                    pile.hidden = snow || old.kind.startsWith("ICE_PILEABLE") ? old.kind : "WATER";
                    pile.hiddenAbove = ch.withAbove() ? get(above).kind : null;
                    set(p, pile);
                    if (ch.withAbove()) {
                        set(above, block("AIR", 0));
                    }
                }
                case FREEZE -> set(p, block("ICE", 0));
                case PLACE_ICICLE -> {
                    SnowScenario.C c = block("ICICLE", 0);
                    c.tip = true;
                    set(p, c);
                }
                case ICICLE_TO_BASE, ICICLE_TO_TIP -> {
                    SnowScenario.C c = old.copy();
                    c.tip = ch.kind() == SnowRule.Kind.ICICLE_TO_TIP;
                    set(p, c);
                }
                case SET_SNOW_LAYERS -> {
                    SnowScenario.C c = old.copy();
                    c.layers = ch.layers();
                    set(p, c);
                }
                case REMOVE -> set(p, block("AIR", 0));
                case RESTORE_PILE -> {
                    set(p, block(old.hidden, 0));
                    if (ch.withAbove() && old.hiddenAbove != null) {
                        set(above, block(old.hiddenAbove, 0));
                    }
                }
                case MELT_TO_WATER -> set(p, block("WATER", 0));
            }
        }

        String diff() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<Long, SnowScenario.C> e : changed.entrySet()) {
                long k = e.getKey();
                int x = (int) (k >> 38);
                int z = (int) (k << 26 >> 38);
                int y = (int) (k << 52 >> 52);
                String now = e.getValue().describe();
                if (!now.equals(s.at(x, y, z).describe())) {
                    b.append(x).append(',').append(y).append(',').append(z).append('=').append(now).append(';');
                }
            }
            return b.toString();
        }
    }
}
