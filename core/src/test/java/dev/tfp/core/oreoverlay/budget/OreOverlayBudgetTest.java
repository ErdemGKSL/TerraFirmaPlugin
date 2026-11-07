/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay.budget;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.oreoverlay.CellLookup;
import dev.tfp.core.oreoverlay.ExposedFaces;
import dev.tfp.core.oreoverlay.OreOverlayTable;
import dev.tfp.core.oreoverlay.OreSnapshot;
import dev.tfp.core.oreoverlay.OverlayGate;
import dev.tfp.core.oreoverlay.ViewerSet;
import dev.tfp.core.oreoverlay.census.ArrayChunkView;
import dev.tfp.core.oreoverlay.census.CensusReport;
import dev.tfp.core.oreoverlay.census.ChunkCensus;
import dev.tfp.core.oreoverlay.census.ExposureCensus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-REL-12 overlay count budget (deterministic): the S1 census fixture ({@link BudgetFixture}, 3 seeds × 32 × 32
 * chunks) run through the runtime's table build ({@link ExposedFaces#compute}) and the viewer cap ({@link ViewerSet}).
 *
 * <ul>
 *   <li>The fixture recounts S1 exactly: per-chunk faces and the 4-chunk-radius totals of the 20 sample positions equal
 *       the published census numbers, so a change of the table build that alters the face count fails here.</li>
 *   <li>Gate ({@value #GATE} overlays per player, {@code ore-overlay.max-per-player} default and the S1 gate): today's
 *       world (measured faces) stays within it at every sample; with the S1 combined estimate (TFC caves) the overlays a
 *       player is actually sent never exceed it (nearest-first cap), the 40 m cutoff (3-chunk radius) keeps p95 within it,
 *       and the raw 48 m p95 is pinned at S1's values (above the gate on seed 1: the open S1 owner question).</li>
 *   <li>CPU: the table build of the median, p95 and largest chunk stays within the design's 0.5 ms per chunk target
 *       (median of 20 timed runs after warm-up, with a generous margin for shared CI machines).</li>
 * </ul>
 */
class OreOverlayBudgetTest {
    /** S1 gate and default {@code ore-overlay.max-per-player}. */
    static final int GATE = 8000;
    /** Default {@code ore-overlay.cutoff-blocks}. */
    static final int CUTOFF = 48;
    /** design §4.3: table build target per chunk. */
    static final double BUILD_TARGET_MS = 0.5;
    /** Margin on the target for noisy shared CI cores (the test fails only far above the target). */
    static final double BUILD_MARGIN = 4.0;

    private static List<BudgetFixture.Area> areas;
    /** Per area: the chunk tables of the combined estimate and of today's world, row-major. */
    private static List<OreOverlayTable[]> estimateTables;
    private static List<OreOverlayTable[]> measuredTables;

    @BeforeAll
    static void build() {
        areas = BudgetFixture.load();
        estimateTables = new ArrayList<>();
        measuredTables = new ArrayList<>();
        for (BudgetFixture.Area a : areas) {
            OreOverlayTable[] est = new OreOverlayTable[a.chunks().size()];
            OreOverlayTable[] meas = new OreOverlayTable[a.chunks().size()];
            for (int dz = 0; dz < a.size(); dz++) {
                for (int dx = 0; dx < a.size(); dx++) {
                    BudgetFixture.Chunk c = a.chunk(dx, dz);
                    int cx = a.originX() + dx;
                    int cz = a.originZ() + dz;
                    int estCave = c.estimate(a.caveFacesPerBlock()) - c.facesSurface();
                    est[dz * a.size() + dx] = ExposedFaces.compute(
                            BudgetFixture.snapshot(cx, cz, c.blocks(), c.facesSurface(), estCave), CellLookup.NONE);
                    meas[dz * a.size() + dx] = ExposedFaces.compute(
                            BudgetFixture.snapshot(cx, cz, c.blocks(), c.facesSurface(), c.facesCave()), CellLookup.NONE);
                }
            }
            estimateTables.add(est);
            measuredTables.add(meas);
        }
    }

    private static int[] faces(OreOverlayTable[] tables) {
        return Arrays.stream(tables).mapToInt(OreOverlayTable::faceCount).toArray();
    }

    /** A census report shell of the area's size: only its sampling ({@link CensusReport#radiusTotals}) is used. */
    private static CensusReport shell(BudgetFixture.Area a) {
        ChunkCensus empty = ExposureCensus.count(new ArrayChunkView(0, 16), new boolean[0]);
        return new CensusReport("tfc", List.of(), new boolean[0], a.originX(), a.originZ(), a.size(),
                Collections.nCopies(a.size() * a.size(), empty));
    }

    private static int p(int[] v, double q) {
        return CensusReport.percentile(v, q);
    }

    private static BudgetFixture.Published dist(int[] v) {
        return new BudgetFixture.Published(p(v, 0.5), p(v, 0.95), Arrays.stream(v).max().orElse(0));
    }

    // ---------------------------------------------------------------------------------------------- fixture

    @Test
    void fixtureIsTheExportOfTheSpikeReports() throws IOException {
        Path out = Path.of("..").resolve(BudgetFixture.SPIKE_OUT);
        Assumptions.assumeTrue(Files.isDirectory(out), "no spike reports at " + out.toAbsolutePath());
        assertThat(BudgetFixture.parse(BudgetFixture.export(out))).isEqualTo(areas);
    }

    @Test
    void snapshotsHoldTheCensusCountsExactly() {
        assertThat(areas).extracting(BudgetFixture.Area::seed).containsExactly(1L, 2L, 3L);
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            assertThat(a.chunks()).hasSize(32 * 32);
            for (int k = 0; k < a.chunks().size(); k++) {
                BudgetFixture.Chunk c = a.chunks().get(k);
                OreOverlayTable est = estimateTables.get(i)[k];
                OreOverlayTable meas = measuredTables.get(i)[k];
                assertThat(est.size()).as("seed %d chunk %d ores", a.seed(), k).isEqualTo(c.blocks());
                assertThat(est.faceCount()).as("seed %d chunk %d estimate faces", a.seed(), k)
                        .isEqualTo(c.estimate(a.caveFacesPerBlock()));
                assertThat(est.outcropFaceCount()).isEqualTo(c.facesSurface());
                assertThat(meas.faceCount()).as("seed %d chunk %d measured faces", a.seed(), k)
                        .isEqualTo(c.facesSurface() + c.facesCave());
                assertThat(meas.outcropFaceCount()).isEqualTo(c.facesSurface());
            }
        }
    }

    @Test
    void tableBuildRecountsThePublishedS1Numbers() {
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            CensusReport r = shell(a);
            int radius = new OverlayGate(CUTOFF, true).radiusChunks();
            assertThat(dist(faces(estimateTables.get(i)))).as("seed %d estimate per chunk", a.seed())
                    .isEqualTo(a.estimatePerChunk());
            assertThat(dist(r.radiusTotals(faces(estimateTables.get(i)), radius, a.seed())))
                    .as("seed %d estimate, 4-chunk radius, 20 samples", a.seed()).isEqualTo(a.estimateGated());
            assertThat(dist(r.radiusTotals(faces(measuredTables.get(i)), radius, a.seed())))
                    .as("seed %d measured, 4-chunk radius, 20 samples", a.seed()).isEqualTo(a.measuredGated());
        }
    }

    // ---------------------------------------------------------------------------------------------- gate

    @Test
    void todaysWorldStaysWithinTheGateAtEverySample() {
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            int[] totals = shell(a).radiusTotals(faces(measuredTables.get(i)), new OverlayGate(CUTOFF, true).radiusChunks(),
                    a.seed());
            assertThat(p(totals, 0.95)).as("seed %d p95", a.seed()).isLessThanOrEqualTo(GATE);
            assertThat(Arrays.stream(totals).max().orElse(0)).as("seed %d samples max", a.seed()).isLessThanOrEqualTo(GATE);
        }
    }

    /**
     * What one player at the centre of a sample square is sent: every chunk of the square reconciled into one
     * {@link ViewerSet} with the default cap, no rate limit (a settled join).
     */
    private static int[] sentAtSamples(BudgetFixture.Area a, OreOverlayTable[] tables, OverlayGate gate, int cap,
            int[] rawOut) {
        int r = gate.radiusChunks();
        int width = 2 * r + 1;
        int span = a.size() - width + 1;
        // the same sample centres as CensusReport#radiusTotals
        java.util.SplittableRandom random = new java.util.SplittableRandom(a.seed() ^ r * 0x9E3779B97F4A7C15L);
        int[] sent = new int[CensusReport.SAMPLES];
        AtomicInteger ids = new AtomicInteger();
        for (int s = 0; s < CensusReport.SAMPLES; s++) {
            int x0 = random.nextInt(span);
            int z0 = random.nextInt(span);
            int vcx = a.originX() + x0 + r;
            int vcz = a.originZ() + z0 + r;
            double vx = (vcx << 4) + 8;
            double vz = (vcz << 4) + 8;
            ViewerSet viewer = new ViewerSet(cap);
            int raw = 0;
            for (int dz = 0; dz < width; dz++) {
                for (int dx = 0; dx < width; dx++) {
                    OreOverlayTable t = tables[(z0 + dz) * a.size() + x0 + dx];
                    boolean near = gate.near(vcx, vcz, t.chunkX(), t.chunkZ());
                    assertThat(near).isTrue();
                    raw += t.faceCount();
                    viewer.reconcile(t.chunkKey(), gate.wanted(t, true, vx, 0, vz), ids::incrementAndGet,
                            Integer.MAX_VALUE);
                }
            }
            sent[s] = viewer.total();
            rawOut[s] = raw;
        }
        return sent;
    }

    @Test
    void withTfcCavesAPlayerIsNeverSentMoreThanTheGate() {
        OverlayGate gate = new OverlayGate(CUTOFF, true);
        StringBuilder report = new StringBuilder("ore overlay budget, S1 combined estimate, 4-chunk radius, 20 samples:\n");
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            int[] raw = new int[CensusReport.SAMPLES];
            int[] sent = sentAtSamples(a, estimateTables.get(i), gate, GATE, raw);
            for (int s = 0; s < sent.length; s++) {
                assertThat(sent[s]).as("seed %d sample %d", a.seed(), s).isEqualTo(Math.min(raw[s], GATE));
            }
            assertThat(Arrays.stream(sent).max().orElse(0)).as("seed %d entities per player", a.seed()).isLessThanOrEqualTo(GATE);
            // the raw count is pinned at S1 (its p95 is above the gate on seed 1: the open owner question)
            assertThat(dist(raw)).isEqualTo(a.estimateGated());
            long capped = Arrays.stream(raw).filter(n -> n > GATE).count();
            report.append(String.format(Locale.ROOT,
                    "seed %d: raw faces p50/p95/max %d/%d/%d, sent p95/max %d/%d, %d of 20 samples capped%n", a.seed(),
                    p(raw, 0.5), p(raw, 0.95), Arrays.stream(raw).max().orElse(0), p(sent, 0.95),
                    Arrays.stream(sent).max().orElse(0), capped));
        }
        System.out.print(report);
    }

    @Test
    void aThreeChunkRadiusKeepsTheEstimateWithinTheGate() {
        OverlayGate gate = new OverlayGate(40, true);
        assertThat(gate.radiusChunks()).isEqualTo(4);
        // ceil(40 / 16) + 1 = 4 as well; S1's "40 m" option is the 3-chunk square (49 chunks): cutoff 32 m in this formula
        OverlayGate threeChunks = new OverlayGate(32, true);
        assertThat(threeChunks.radiusChunks()).isEqualTo(3);
        StringBuilder report = new StringBuilder("ore overlay budget, S1 combined estimate, 3-chunk radius, 20 samples:\n");
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            int[] raw = new int[CensusReport.SAMPLES];
            sentAtSamples(a, estimateTables.get(i), threeChunks, GATE, raw);
            assertThat(p(raw, 0.95)).as("seed %d p95, 3-chunk radius", a.seed()).isLessThanOrEqualTo(GATE);
            report.append(String.format(Locale.ROOT, "seed %d: raw faces p50/p95/max %d/%d/%d%n", a.seed(), p(raw, 0.5),
                    p(raw, 0.95), Arrays.stream(raw).max().orElse(0)));
        }
        System.out.print(report);
    }

    // ---------------------------------------------------------------------------------------------- CPU

    private static double medianBuildMs(OreSnapshot s) {
        for (int i = 0; i < 50; i++) {
            ExposedFaces.compute(s, CellLookup.NONE);
        }
        long[] runs = new long[20];
        for (int i = 0; i < runs.length; i++) {
            long t0 = System.nanoTime();
            ExposedFaces.compute(s, CellLookup.NONE);
            runs[i] = System.nanoTime() - t0;
        }
        Arrays.sort(runs);
        return (runs[9] + runs[10]) / 2.0 / 1e6;
    }

    @Test
    void tableBuildStaysWithinTheCpuTarget() {
        // the chunks of all three seeds by estimated faces: the median, p95 and largest one
        List<BudgetFixture.Chunk> all = new ArrayList<>();
        List<Double> cpb = new ArrayList<>();
        for (BudgetFixture.Area a : areas) {
            for (BudgetFixture.Chunk c : a.chunks()) {
                all.add(c);
                cpb.add(a.caveFacesPerBlock());
            }
        }
        Integer[] order = new Integer[all.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Integer.compare(all.get(x).blocks(), all.get(y).blocks()));
        StringBuilder report = new StringBuilder("ore overlay table build (median of 20 runs):\n");
        double limit = BUILD_TARGET_MS * BUILD_MARGIN;
        for (double q : new double[] {0.5, 0.95, 1.0}) {
            int k = order[Math.min(order.length - 1, (int) Math.ceil(q * order.length) - 1)];
            BudgetFixture.Chunk c = all.get(k);
            OreSnapshot s = BudgetFixture.snapshot(0, 0, c.blocks(), c.facesSurface(),
                    c.estimate(cpb.get(k)) - c.facesSurface());
            double ms = medianBuildMs(s);
            report.append(String.format(Locale.ROOT, "  ores p%d: %d ores, %d faces: %.3f ms (target %.1f ms, fails above %.1f)%n",
                    (int) (q * 100), c.blocks(), c.estimate(cpb.get(k)), ms, BUILD_TARGET_MS, limit));
            assertThat(ms).as("table build of a chunk with %d ores", c.blocks()).isLessThanOrEqualTo(limit);
        }
        System.out.print(report);
    }
}
