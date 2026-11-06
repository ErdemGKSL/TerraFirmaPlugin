/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.oreoverlay.FastPath.FaceChange;
import dev.tfp.core.oreoverlay.census.ExposureRule;
import dev.tfp.core.visuals.OverlayFace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FastPathTest {
    private static final int SOLID = ExposureRule.SOLID;
    private static final int OPEN = ExposureRule.OPEN;

    @Test
    void breakingTheRockInFrontOfAnOreShowsItsFace() {
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 4);
        OreOverlayTable t = w.table(0, 0);
        List<FaceChange> c = FastPath.decide((x, z) -> t, 5, 11, 5, false, FastPath.NOT_ORE);
        assertThat(c).containsExactly(new FaceChange(5, 10, 5, OverlayFace.UP, 4, true, false));
    }

    @Test
    void placingABlockToCoverTheFaceHidesIt() {
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 4).set(5, 10, 4, OPEN);
        OreOverlayTable t = w.table(0, 0);
        List<FaceChange> c = FastPath.decide((x, z) -> t, 5, 10, 4, true, FastPath.UNKNOWN_ORE);
        assertThat(c).containsExactly(new FaceChange(5, 10, 5, OverlayFace.NORTH, 4, false, false));
    }

    @Test
    void miningAnOreRemovesItsFacesAndExposesTheOresBehind() {
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 1).set(5, 10, 6, 2).set(5, 11, 5, OPEN);
        OreOverlayTable t = w.table(0, 0);
        List<FaceChange> c = FastPath.decide((x, z) -> t, 5, 10, 5, false, FastPath.NOT_ORE);
        assertThat(c).containsExactlyInAnyOrder(new FaceChange(5, 10, 5, OverlayFace.UP, 1, false, false),
                new FaceChange(5, 10, 6, OverlayFace.NORTH, 2, true, false));
    }

    @Test
    void anOreReplacedByRockOfTheSameLookNeedsTheRebuild() {
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 1).set(5, 11, 5, OPEN);
        OreOverlayTable t = w.table(0, 0);
        assertThat(FastPath.decide((x, z) -> t, 5, 10, 5, true, FastPath.UNKNOWN_ORE)).isEmpty();
        assertThat(FastPath.decide((x, z) -> t, 5, 10, 5, true, FastPath.NOT_ORE))
                .containsExactly(new FaceChange(5, 10, 5, OverlayFace.UP, 1, false, false));
        assertThat(FastPath.decide((x, z) -> t, 5, 10, 5, true, 7)).containsExactly(
                new FaceChange(5, 10, 5, OverlayFace.UP, 1, false, false),
                new FaceChange(5, 10, 5, OverlayFace.UP, 7, true, false));
    }

    @Test
    void changesAcrossAChunkEdgeUseTheNeighbourTable() {
        TestWorld w = new TestWorld(SOLID).set(16, 10, 0, 3);
        OreOverlayTable east = w.table(1, 0);
        List<FaceChange> c = FastPath.decide((x, z) -> x == 1 ? east : null, 15, 10, 0, false, FastPath.NOT_ORE);
        assertThat(c).containsExactly(new FaceChange(16, 10, 0, OverlayFace.WEST, 3, true, false));
    }

    @Test
    void aConcurrentChangeIsCorrectedByTheRebuild() {
        // Two neighbours of an ore change in the same tick; the fast path decides each from the old table, the rebuild
        // of both together gives the full build of the final world.
        TestWorld w = new TestWorld(SOLID).set(5, 10, 5, 0);
        OreOverlayTable t = w.table(0, 0);
        w.set(5, 11, 5, OPEN).set(5, 9, 5, OPEN);
        assertThat(FastPath.decide((x, z) -> t, 5, 11, 5, false, FastPath.NOT_ORE)).hasSize(1);
        w.set(5, 11, 5, SOLID);
        OreOverlayTable patched = ExposedFaces.patch(t, TestWorld.MAX_Y, w,
                new long[] {OverlayKeys.block(5, 11, 5), OverlayKeys.block(5, 9, 5)});
        assertThat(patched.sameAs(w.table(0, 0))).isTrue();
        assertThat(patched.mask(0)).isEqualTo(1 << OverlayFace.DOWN.ordinal());
    }

    /**
     * Property: for random edit sequences over two chunks (one edge between them), the viewer's overlays after the fast
     * path per edit plus the batched rebuilds and a reconcile equal what a full build of the final world shows, and the
     * patched tables equal the full builds.
     */
    @Test
    void fastPathAndRebuildConvergeToTheFullBuild() {
        Random r = new Random(1601);
        OverlayGate gate = new OverlayGate(48, true);
        for (int run = 0; run < 200; run++) {
            TestWorld w = new TestWorld(SOLID);
            for (int i = 0; i < 120; i++) {
                w.set(12 + r.nextInt(8), 4 + r.nextInt(8), 2 + r.nextInt(6), r.nextInt(3) == 0 ? OPEN : r.nextInt(4));
            }
            Map<Long, OreOverlayTable> tables = new HashMap<>();
            for (int cx = 0; cx <= 1; cx++) {
                tables.put(OverlayKeys.chunk(cx, 0), w.table(cx, 0));
            }
            ViewerSet viewer = new ViewerSet(100_000);
            AtomicInteger ids = new AtomicInteger();
            for (long k : tables.keySet()) {
                viewer.reconcile(k, gate.wanted(tables.get(k), true, 16, 8, 5), ids::incrementAndGet, Integer.MAX_VALUE);
            }
            List<Long> pending = new ArrayList<>();
            int edits = 10 + r.nextInt(30);
            for (int e = 0; e < edits; e++) {
                int x = 12 + r.nextInt(8);
                int y = 4 + r.nextInt(8);
                int z = 2 + r.nextInt(6);
                int code = switch (r.nextInt(4)) {
                    case 0 -> SOLID;
                    case 1 -> r.nextInt(4);
                    default -> OPEN;
                };
                w.set(x, y, z, code);
                boolean occludes = code != OPEN;
                int known = r.nextBoolean() ? FastPath.UNKNOWN_ORE : (code >= 0 ? code : FastPath.NOT_ORE);
                for (FaceChange c : FastPath.decide((cx, cz) -> tables.get(OverlayKeys.chunk(cx, cz)), x, y, z, occludes,
                        known)) {
                    long chunk = OverlayKeys.chunk(c.x() >> 4, c.z() >> 4);
                    int key = OverlayKeys.face(OverlayKeys.local(c.x(), c.y(), c.z(), TestWorld.MIN_Y), c.face().ordinal());
                    viewer.change(chunk, key, c.kind(), c.outcrop(), c.visible(), ids::incrementAndGet);
                }
                pending.add(OverlayKeys.block(x, y, z));
                if (r.nextInt(3) == 0) {
                    rebuild(tables, w, pending, viewer, gate, ids);
                }
            }
            rebuild(tables, w, pending, viewer, gate, ids);
            for (int cx = 0; cx <= 1; cx++) {
                long k = OverlayKeys.chunk(cx, 0);
                OreOverlayTable full = w.table(cx, 0);
                assertThat(tables.get(k).sameAs(full)).as("run " + run + " chunk " + cx).isTrue();
                Set<Integer> want = new HashSet<>();
                for (ViewerSet.Wanted wf : gate.wanted(full, true, 16, 8, 5)) {
                    want.add(wf.faceKey());
                    assertThat(viewer.kind(k, wf.faceKey())).isEqualTo(wf.kind());
                }
                assertThat(viewer.total(k)).as("run " + run).isEqualTo(want.size());
            }
        }
    }

    /** The authoritative rebuild: patch every touched chunk from the current world, then reconcile the viewer. */
    private static void rebuild(Map<Long, OreOverlayTable> tables, TestWorld w, List<Long> pending, ViewerSet viewer,
            OverlayGate gate, AtomicInteger ids) {
        if (pending.isEmpty()) {
            return;
        }
        long[] changed = pending.stream().mapToLong(Long::longValue).toArray();
        pending.clear();
        for (Map.Entry<Long, OreOverlayTable> e : tables.entrySet()) {
            e.setValue(ExposedFaces.patch(e.getValue(), TestWorld.MAX_Y, w, changed));
            viewer.reconcile(e.getKey(), gate.wanted(e.getValue(), true, 16, 8, 5), ids::incrementAndGet,
                    Integer.MAX_VALUE);
        }
    }
}
