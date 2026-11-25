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
 *   <li>Gate ({@value #GATE} overlays per player, {@code ore-overlay.max-per-player} default and the S1 gate), on the
 *       PAR-WORLD-19 census of our world with TFC's caves, over every window of the area (S1 §2.3): at the default 40 m
 *       cutoff (3-chunk radius, PAR-WORLD-62) p95 stays within it on all three seeds; at S1's former 48 m (4-chunk
 *       radius) p95 is above it on seeds 1 and 2, the documented reason the default moved. Where a window's raw count is
 *       above the gate the nearest-first cap ({@link ViewerSet}) binds: a player is never sent more than the gate.</li>
 *   <li>CPU: the table build of the median, p95 and largest chunk stays within the design's 0.5 ms per chunk target
 *       (median of 20 timed runs after warm-up, with a generous margin for shared CI machines).</li>
 * </ul>
 */
class OreOverlayBudgetTest {
    /** S1 gate and default {@code ore-overlay.max-per-player}. */
    static final int GATE = 8000;
    /** Default {@code ore-overlay.cutoff-blocks} (PAR-WORLD-62). */
    static final int CUTOFF = 40;
    /** The default send radius: {@code ceil(40 / 16)} = 3 chunks, 49 chunks. */
    static final OverlayGate DEFAULT_GATE = new OverlayGate(CUTOFF, true);
    /** S1's published radius (its former 48 m square, 81 chunks): a cutoff of 64 m in {@code ceil(cutoff / 16)}. */
    static final OverlayGate S1_FOUR_CHUNKS = new OverlayGate(64, true);
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
            int radius = S1_FOUR_CHUNKS.radiusChunks();
            assertThat(radius).isEqualTo(4);
            assertThat(dist(faces(estimateTables.get(i)))).as("seed %d estimate per chunk", a.seed())
                    .isEqualTo(a.estimatePerChunk());
            assertThat(dist(r.radiusTotals(faces(estimateTables.get(i)), radius, a.seed())))
                    .as("seed %d estimate, 4-chunk radius, 20 samples", a.seed()).isEqualTo(a.estimateGated());
            assertThat(dist(r.radiusTotals(faces(measuredTables.get(i)), radius, a.seed())))
                    .as("seed %d measured, 4-chunk radius, 20 samples", a.seed()).isEqualTo(a.measuredGated());
        }
    }

    // ---------------------------------------------------------------------------------------------- gate

    /** Every (2r+1)² window inside the area (S1 §2.3, {@code analyze.py}): the faces it holds, row-major. */
    private static int[] windowTotals(BudgetFixture.Area a, int[] perChunk, int r) {
        int width = 2 * r + 1;
        int span = a.size() - width + 1;
        int[] out = new int[span * span];
        for (int z0 = 0; z0 < span; z0++) {
            for (int x0 = 0; x0 < span; x0++) {
                int sum = 0;
                for (int dz = 0; dz < width; dz++) {
                    for (int dx = 0; dx < width; dx++) {
                        sum += perChunk[(z0 + dz) * a.size() + x0 + dx];
                    }
                }
                out[z0 * span + x0] = sum;
            }
        }
        return out;
    }

    /** S1 §2.3 (PAR-WORLD-19, measured with caves): all-window p50 / p95 / max per seed, radius 3 (40 m). */
    static final BudgetFixture.Published[] S1_WINDOWS_R3 = {
        new BudgetFixture.Published(2192, 6969, 11171),
        new BudgetFixture.Published(2807, 7615, 10528),
        new BudgetFixture.Published(1246, 3136, 3919),
    };
    /** S1 §2.3: the same at radius 4 (48 m). */
    static final BudgetFixture.Published[] S1_WINDOWS_R4 = {
        new BudgetFixture.Published(3786, 8901, 13000),
        new BudgetFixture.Published(4971, 11293, 13108),
        new BudgetFixture.Published(2197, 4613, 5761),
    };

    @Test
    void theDefaultRadiusKeepsP95WithinTheGateOnEverySeed() {
        assertThat(DEFAULT_GATE.radiusChunks()).isEqualTo(3);
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            int[] w = windowTotals(a, faces(measuredTables.get(i)), DEFAULT_GATE.radiusChunks());
            assertThat(w).hasSize(26 * 26);
            assertThat(dist(w)).as("seed %d measured, all windows, radius 3", a.seed()).isEqualTo(S1_WINDOWS_R3[i]);
            assertThat(p(w, 0.95)).as("seed %d p95, 40 m", a.seed()).isLessThanOrEqualTo(GATE);
            // The PAR-WORLD-17 combined estimate (vanilla cave proxy as a per-chunk floor over the measurement) is
            // superseded by the cave-world measurement (S1 §4) and not gated: it lifts every chunk whose measured cave faces
            // are below blocks × 0.0659 to that figure, a pessimistic bound. Its seed 2 all-window p95 at 40 m is 8 241, which the nearest-first cap absorbs.
        }
    }

    @Test
    void atTheFormer48mTheGateFailsOnSeedsOneAndTwo() {
        // S1 §4: the documented reason for PAR-WORLD-62 (pinned, not passed)
        int r = S1_FOUR_CHUNKS.radiusChunks();
        assertThat(r).isEqualTo(4);
        for (int i = 0; i < areas.size(); i++) {
            BudgetFixture.Area a = areas.get(i);
            int[] w = windowTotals(a, faces(measuredTables.get(i)), r);
            assertThat(w).hasSize(24 * 24);
            assertThat(dist(w)).as("seed %d measured, all windows, radius 4", a.seed()).isEqualTo(S1_WINDOWS_R4[i]);
            if (a.seed() == 3) {
                assertThat(p(w, 0.95)).as("seed 3 p95, 48 m").isLessThanOrEqualTo(GATE);
            } else {
                assertThat(p(w, 0.95)).as("seed %d p95, 48 m", a.seed()).isGreaterThan(GATE);
            }
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
        StringBuilder report = new StringBuilder("ore overlay budget, TFC caves (PAR-WORLD-19), 20 samples:\n");
        for (OverlayGate gate : new OverlayGate[] {DEFAULT_GATE, S1_FOUR_CHUNKS}) {
            for (int i = 0; i < areas.size(); i++) {
                BudgetFixture.Area a = areas.get(i);
                for (boolean estimate : new boolean[] {false, true}) {
                    int[] raw = new int[CensusReport.SAMPLES];
                    int[] sent = sentAtSamples(a, (estimate ? estimateTables : measuredTables).get(i), gate, GATE, raw);
                    for (int s = 0; s < sent.length; s++) {
                        assertThat(sent[s]).as("seed %d sample %d", a.seed(), s).isEqualTo(Math.min(raw[s], GATE));
                    }
                    assertThat(Arrays.stream(sent).max().orElse(0)).as("seed %d entities per player", a.seed())
                            .isLessThanOrEqualTo(GATE);
                    if (gate == S1_FOUR_CHUNKS) {
                        // the raw counts are the published 20-sample figures
                        assertThat(dist(raw)).isEqualTo(estimate ? a.estimateGated() : a.measuredGated());
                    }
                    long capped = Arrays.stream(raw).filter(n -> n > GATE).count();
                    report.append(String.format(Locale.ROOT,
                            "radius %d, seed %d, %s: raw faces p50/p95/max %d/%d/%d, sent p95/max %d/%d, %d of 20 capped%n",
                            gate.radiusChunks(), a.seed(), estimate ? "estimate" : "measured", p(raw, 0.5), p(raw, 0.95),
                            Arrays.stream(raw).max().orElse(0), p(sent, 0.95), Arrays.stream(sent).max().orElse(0),
                            capped));
                }
            }
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
