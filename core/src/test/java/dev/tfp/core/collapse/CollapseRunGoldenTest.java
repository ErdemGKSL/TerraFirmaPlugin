/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.CollapseGoldenTest.Lcg;
import dev.tfp.core.world.BlockPos;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PAR-PHYS-02 golden values for a real collapse: {@link CollapseRules#drawRadius}, {@link CollapseRules#start} and every
 * {@link CollapseRules#propagate} step until the collapse ends, on 60 scripted 41 x 13 x 41 worlds. The rows come from a scratch
 * oracle transcribed from TFC at c3b4d9536 ({@code CollapseRecipe.startCollapse}, {@code collapseBlock},
 * {@code TFCFallingBlockEntity.canFallThrough}, the collapse loop of {@code WorldTracker.tick}); its one change is that the next
 * positions are sorted (TFC's hash-set order). Both sides apply a phase the same way before the next: the fallen block becomes
 * air, and so does the block below when it was broken. Same LCG and block codes as {@link CollapseGoldenTest}.
 *
 * <p>Row format: {@code r<radius>/<start steps>:<hash>/<propagation steps>:<hash>/...}, ending with the step that found nothing.
 */
class CollapseRunGoldenTest {
    private static final String RESOURCE = "collapse-run-golden.txt";
    private static final String LOWER = "AAAAAAAARRRRSOLPTCBA";
    private static final String UPPER = "RRRRRRRRRRRSSOOLPTCA";

    private static List<String> golden() throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = CollapseRunGoldenTest.class.getResourceAsStream(RESOURCE);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line.strip());
                }
            }
        }
        return lines;
    }

    private static int hash(List<CollapseRules.CollapseStep> steps) {
        int h = 0;
        for (CollapseRules.CollapseStep s : steps) {
            BlockPos p = s.pos();
            int e = (p.x() * 73856093) ^ (p.y() * 19349663) ^ (p.z() * 83492791);
            h = 31 * h + e * 2 + (s.breakBelow() ? 1 : 0) + CollapseGoldenTest.codeOf(s.result());
        }
        return h;
    }

    private static void apply(Map<BlockPos, Character> w, List<CollapseRules.CollapseStep> steps) {
        for (CollapseRules.CollapseStep s : steps) {
            if (s.breakBelow()) {
                w.put(s.pos().offset(0, -1, 0), 'A');
            }
            w.put(s.pos(), 'A');
        }
    }

    @Test
    void startAndPropagationMatchTfc() throws IOException {
        List<String> expected = golden();
        CollapseRules rules = CollapseGoldenTest.rules();
        List<String> rows = new ArrayList<>();
        for (int c = 0; c < 60; c++) {
            Lcg worldRng = new Lcg(7919L * c + 3);
            Lcg rng = new Lcg(7919L * c + 11);
            Map<BlockPos, Character> w = new HashMap<>();
            for (int dx = -20; dx <= 20; dx++) {
                for (int dy = -6; dy <= 6; dy++) {
                    for (int dz = -20; dz <= 20; dz++) {
                        w.put(new BlockPos(dx, dy, dz), (dy <= -3 ? LOWER : UPPER).charAt(worldRng.nextInt(20)));
                    }
                }
            }
            CollapseView view = CollapseGoldenTest.view(w);
            int radius = rules.drawRadius(rng);
            CollapseRules.Phase phase = rules.start(view, CollapseGoldenTest.ORIGIN, radius, rng);
            List<String> parts = new ArrayList<>();
            parts.add("r" + radius);
            parts.add(phase.steps().size() + ":" + hash(phase.steps()));
            apply(w, phase.steps());
            Optional<CollapseRules.InProgress> next = phase.followUp();
            for (int n = 0; next.isPresent() && n < 50; n++) {
                phase = rules.propagate(view, next.get(), rng);
                parts.add(phase.steps().size() + ":" + hash(phase.steps()));
                apply(w, phase.steps());
                next = phase.followUp();
            }
            rows.add(String.join("/", parts));
        }
        assertThat(rows).containsExactlyElementsOf(expected.subList(0, 60));
    }

    @Test
    void propagationDelayMatchesTheTrackerDraws() throws IOException {
        String line = golden().get(60);
        assertThat(line).startsWith("DELAYS ");
        Lcg d = new Lcg(424242L);
        List<String> delays = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            delays.add(Long.toString(CollapseRules.propagationDelay(d)));
        }
        assertThat(String.join(" ", delays)).isEqualTo(line.substring("DELAYS ".length()));
    }

    @Test
    void radiusStaysInTheConfiguredRange() {
        CollapseRules rules = CollapseGoldenTest.rules();
        Lcg rng = new Lcg(99L);
        for (int i = 0; i < 1000; i++) {
            assertThat(rules.drawRadius(rng)).isBetween(CollapseRules.DEFAULT_MIN_RADIUS, rules.maxRadius());
        }
        assertThat(rules.maxRadius()).isEqualTo(18);
    }

    @Test
    void startDropsTheFirstRockAboveANonFullBlock() {
        // a column: raw rock, a slab, raw rock; the upper rock is above a non-full block and falls; the falling block can pass
        // the slab (not sturdy), so the slab is not broken first
        Map<BlockPos, Character> w = new HashMap<>();
        w.put(new BlockPos(0, -4, 0), 'R');
        w.put(new BlockPos(0, -3, 0), 'L');
        w.put(new BlockPos(0, -2, 0), 'R');
        CollapseRules always = new CollapseRules(CollapseGoldenTest.kinds(), CollapseGoldenTest.supports(), 1, 1, 1, 1, 1);
        CollapseRules.Phase phase = always.start(CollapseGoldenTest.view(w), CollapseGoldenTest.ORIGIN, 3,
            new Lcg(1L));
        assertThat(phase.steps()).containsExactly(
            new CollapseRules.CollapseStep(new BlockPos(0, -2, 0), CollapseGoldenTest.COBBLE, false));
        assertThat(phase.followUp()).isPresent();
        assertThat(phase.followUp().get().next()).containsExactly(new BlockPos(0, -1, 0));
        assertThat(phase.followUp().get().radiusSquared()).isEqualTo(9.0);
    }
}
