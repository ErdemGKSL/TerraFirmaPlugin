/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.placed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.placed.HoldTracker.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** PAR-POT-01: the hold-right-click gesture on a fake clock (events every 4 ticks = 200 ms, as the vanilla client resends). */
class HoldTrackerTest {
    private final AtomicLong clock = new AtomicLong(10_000);
    private final HoldTracker<String> tracker = new HoldTracker<>(clock::get);
    private long tick = 100;

    /** One event at {@code atMs} after the start of the test's clock (one tick per 50 ms). */
    private HoldTracker.Step at(long atMs, String key) {
        clock.set(10_000 + atMs);
        tick = 100 + atMs / 50;
        return tracker.event(key, tick);
    }

    private List<Phase> events(String key, long... times) {
        List<Phase> out = new ArrayList<>();
        for (long t : times) {
            out.add(at(t, key).phase());
        }
        return out;
    }

    @Test
    void fourEventsInPointEightSecondsPlaceNothing() {
        assertThat(events("a", 0, 266, 533, 800)).doesNotContain(Phase.COMPLETE);
        assertThat(tracker.active()).isTrue();
    }

    @Test
    void sixEventsOverOneSecondPlaceOnce() {
        List<Phase> phases = events("a", 0, 200, 400, 600, 800, 1000);
        assertThat(phases).containsExactly(Phase.STARTED, Phase.PROGRESS, Phase.PROGRESS, Phase.PROGRESS, Phase.PROGRESS,
            Phase.COMPLETE);
        // one placement per hold: holding on does nothing more
        assertThat(events("a", 1200, 1400, 1600, 1800, 2000, 2200)).containsOnly(Phase.DONE);
    }

    @Test
    void aCompletedHoldNeedsARelease() {
        events("a", 0, 200, 400, 600, 800, 1000);
        assertThat(tracker.completed()).isTrue();
        // the cursor now rests on the placed item: another key, still held
        assertThat(events("placed-block", 1200, 1400, 1600, 1800, 2000, 2200)).containsOnly(Phase.DONE);
        assertThat(tracker.key()).contains("placed-block");
        // released (a gap), then a new hold places again
        assertThat(events("placed-block", 2600, 2800, 3000, 3200, 3400, 3600)).endsWith(Phase.COMPLETE)
            .startsWith(Phase.STARTED);
    }

    @Test
    void fiveEventsSpanningLessThanASecondWaitForTheSecond() {
        assertThat(events("a", 0, 100, 200, 300, 400)).doesNotContain(Phase.COMPLETE);
        assertThat(at(700, "a").phase()).isEqualTo(Phase.PROGRESS);
        assertThat(at(1000, "a").phase()).isEqualTo(Phase.COMPLETE);
    }

    @Test
    void progressRisesWithHeldTime() {
        at(0, "a");
        assertThat(at(200, "a").progress()).isEqualTo(0.2);
        assertThat(at(400, "a").progress()).isEqualTo(0.4);
    }

    @Test
    void aGapAbove300MsResets() {
        events("a", 0, 200, 400, 600);
        assertThat(at(950, "a").phase()).as("350 ms gap").isEqualTo(Phase.STARTED);
        assertThat(events("a", 1150, 1350, 1550, 1750)).doesNotContain(Phase.COMPLETE);
        assertThat(at(1950, "a").phase()).isEqualTo(Phase.COMPLETE);
    }

    @Test
    void aGapOf300MsIsTolerated() {
        assertThat(events("a", 0, 300, 600, 900, 1000)).endsWith(Phase.COMPLETE);
    }

    @Test
    void targetFaceSlotOrItemChangeResets() {
        // the key stands for block + face + hand + hotbar slot + item fingerprint: any change is another key
        for (String other : List.of("other-block", "other-face", "other-slot", "other-item")) {
            HoldTracker<String> t = new HoldTracker<>(clock::get);
            clock.set(0);
            t.event("a", 0);
            clock.set(200);
            t.event("a", 4);
            clock.set(400);
            t.event("a", 8);
            clock.set(600);
            assertThat(t.event(other, 12).phase()).as(other).isEqualTo(Phase.STARTED);
            clock.set(800);
            t.event(other, 16);
            clock.set(1000);
            assertThat(t.event(other, 20).phase()).as(other + ": only 600 ms on the new key").isEqualTo(Phase.PROGRESS);
        }
    }

    @Test
    void twoPacketsInOneTickCountOnce() {
        // UseItemOn + UseItem of the same client tick arrive in one server tick: eight events, four ticks
        for (long[] e : new long[][] {{0, 0}, {0, 0}, {250, 5}, {250, 5}, {500, 10}, {500, 10}, {750, 15}, {750, 15}}) {
            clock.set(e[0]);
            assertThat(tracker.event("a", e[1]).phase()).isNotEqualTo(Phase.COMPLETE);
        }
        assertThat(tracker.release().orElseThrow().events()).isEqualTo(4);
        // the same pairs with the fifth tick at a full second complete the hold exactly then
        for (long[] e : new long[][] {{0, 0}, {0, 0}, {250, 5}, {250, 5}, {500, 10}, {500, 10}, {750, 15}, {750, 15}}) {
            clock.set(e[0] + 5000);
            tracker.event("a", e[1] + 100);
        }
        clock.set(6000);
        assertThat(tracker.event("a", 120).phase()).as("5 counted events over 1 s").isEqualTo(Phase.COMPLETE);
        clock.set(6000);
        assertThat(tracker.event("a", 120).phase()).as("the second packet of that tick").isEqualTo(Phase.DONE);
    }

    @Test
    void releaseIsSeenAfterTheGap() {
        events("a", 0, 200);
        clock.set(10_000 + 450);
        assertThat(tracker.expired()).isFalse();
        clock.set(10_000 + 501);
        assertThat(tracker.expired()).isTrue();
        HoldTracker.Released<String> r = tracker.releaseIfExpired().orElseThrow();
        assertThat(r.key()).isEqualTo("a");
        assertThat(r.completed()).isFalse();
        assertThat(r.events()).isEqualTo(2);
        assertThat(tracker.active()).isFalse();
        assertThat(tracker.releaseIfExpired()).isEmpty();
    }

    @Test
    void aCompletedHoldIsReportedAsCompletedOnRelease() {
        events("a", 0, 200, 400, 600, 800, 1000);
        assertThat(tracker.release().orElseThrow().completed()).isTrue();
    }

    @Test
    void theCooldownDelaysTheNextCompletion() {
        events("a", 0, 200, 400, 600, 800, 1000);
        tracker.placedNow(); // e.g. a double-tap placement right after
        clock.set(10_000 + 1100);
        assertThat(tracker.cooledDown()).isFalse();
        clock.set(10_000 + 1250);
        assertThat(tracker.cooledDown()).isTrue();
    }
}
