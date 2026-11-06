/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.oreoverlay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.oreoverlay.census.ExposureRule;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ViewerSetTest {
    private final AtomicInteger ids = new AtomicInteger(100);

    private static List<ViewerSet.Wanted> faces(int n, int kind) {
        List<ViewerSet.Wanted> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(new ViewerSet.Wanted(i, kind, false, n - i));
        }
        return l;
    }

    @Test
    void reconcileSpawnsMissingAndRemovesStale() {
        ViewerSet v = new ViewerSet(100);
        ViewerSet.Plan p = v.reconcile(1L, faces(3, 0), ids::incrementAndGet, 100);
        assertThat(p.spawns()).hasSize(3);
        assertThat(p.removes()).isEmpty();
        assertThat(v.total()).isEqualTo(3);
        int stale = v.entity(1L, 0);
        ViewerSet.Plan again = v.reconcile(1L, faces(3, 0).subList(1, 3), ids::incrementAndGet, 100);
        assertThat(again.spawns()).isEmpty();
        assertThat(again.removes()).containsExactly(stale);
        // now faces 1, 2 of kind 0; wanted 0, 1 of kind 5: 1 changes kind (respawn), 2 goes, 0 is new
        ViewerSet.Plan kinds = v.reconcile(1L, faces(2, 5), ids::incrementAndGet, 100);
        assertThat(kinds.removes()).hasSize(2);
        assertThat(kinds.spawns()).extracting(ViewerSet.Spawn::faceKey).containsExactlyInAnyOrder(0, 1);
        assertThat(v.kind(1L, 1)).isEqualTo(5);
    }

    @Test
    void capKeepsTheNearestFaces() {
        ViewerSet v = new ViewerSet(2);
        ViewerSet.Plan p = v.reconcile(1L, faces(5, 0), ids::incrementAndGet, 100);
        // distSq = 5 - i: faces 4 and 3 are nearest
        assertThat(p.spawns()).extracting(ViewerSet.Spawn::faceKey).containsExactly(4, 3);
        assertThat(p.deferred()).isTrue();
        assertThat(v.total()).isEqualTo(2);
        assertThat(v.change(2L, 9, 0, false, true, ids::incrementAndGet).spawns()).as("the fast path respects the cap")
                .isEmpty();
        v.dropChunk(1L);
        assertThat(v.total()).isZero();
        assertThat(v.change(2L, 9, 0, false, true, ids::incrementAndGet).spawns()).hasSize(1);
    }

    @Test
    void budgetLimitsSpawnsPerCallAndDefersTheRest() {
        ViewerSet v = new ViewerSet(100);
        ViewerSet.Plan p = v.reconcile(1L, faces(10, 0), ids::incrementAndGet, 4);
        assertThat(p.spawns()).hasSize(4);
        assertThat(p.deferred()).isTrue();
        ViewerSet.Plan rest = v.reconcile(1L, faces(10, 0), ids::incrementAndGet, 100);
        assertThat(rest.spawns()).hasSize(6);
        assertThat(rest.deferred()).isFalse();
    }

    @Test
    void dropChunkReturnsAllItsIdsOnce() {
        ViewerSet v = new ViewerSet(100);
        v.reconcile(1L, faces(4, 0), ids::incrementAndGet, 100);
        v.reconcile(2L, faces(2, 0), ids::incrementAndGet, 100);
        assertThat(v.dropChunk(1L)).hasSize(4);
        assertThat(v.dropChunk(1L)).isEmpty();
        assertThat(v.chunks()).containsExactly(2L);
        assertThat(v.clear()).hasSize(2);
        assertThat(v.total()).isZero();
    }

    @Test
    void fastPathChangeIsIdempotent() {
        ViewerSet v = new ViewerSet(100);
        assertThat(v.change(1L, 7, 2, false, true, ids::incrementAndGet).spawns()).hasSize(1);
        assertThat(v.change(1L, 7, 2, false, true, ids::incrementAndGet).isEmpty()).isTrue();
        assertThat(v.change(1L, 7, 2, false, false, ids::incrementAndGet).removes()).hasSize(1);
        assertThat(v.change(1L, 7, 2, false, false, ids::incrementAndGet).isEmpty()).isTrue();
    }

    @Test
    void tokenBucketBurstsThenRefillsPerTick() {
        TokenBucket b = TokenBucket.defaults();
        assertThat(b.take(5000, 0)).isEqualTo(1000);
        assertThat(b.take(10, 0)).isZero();
        assertThat(b.take(100, 1)).isEqualTo(25);
        assertThat(b.take(100, 3)).isEqualTo(50);
        assertThat(b.take(5000, 1000)).as("refill caps at the burst").isEqualTo(1000);
        b.charge(6, 1000);
        assertThat(b.available(1000)).isZero();
        assertThat(b.available(1001)).isEqualTo(19);
    }

    @Test
    void gateRadiusAndViewRange() {
        OverlayGate g = new OverlayGate(48, true);
        assertThat(g.radiusChunks()).isEqualTo(4);
        assertThat(g.viewRange()).isEqualTo(0.75f);
        assertThat(new OverlayGate(40, true).radiusChunks()).isEqualTo(4);
        assertThat(new OverlayGate(32, true).radiusChunks()).isEqualTo(3);
        assertThat(g.near(0, 0, 4, -4)).isTrue();
        assertThat(g.near(0, 0, 5, 0)).isFalse();
        assertThat(OverlayGate.fullViewRange(10)).isEqualTo(2.5f);
    }

    @Test
    void farChunksSendOnlyOutcrops() {
        TestWorld w = new TestWorld(ExposureRule.SOLID).set(4, 20, 4, 0).set(4, 21, 4, ExposureRule.OPEN)
                .set(4, 10, 6, 0).set(4, 10, 7, ExposureRule.OPEN).surface(4, 4, 28);
        OreOverlayTable t = w.table(0, 0);
        assertThat(new OverlayGate(48, true).wanted(t, true, 0, 0, 0)).hasSize(2);
        List<ViewerSet.Wanted> far = new OverlayGate(48, true).wanted(t, false, 0, 0, 0);
        assertThat(far).hasSize(1);
        assertThat(far.getFirst().outcrop()).isTrue();
        assertThat(new OverlayGate(48, false).wanted(t, false, 0, 0, 0)).isEmpty();
    }
}
