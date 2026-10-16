/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.ready;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.ready.ReadyGate.Signal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** M1-BOOT-04: ready = CE reloaded ∧ BetterModel models ready ∧ registries loaded. */
class ReadyGateTest {
    @Test
    void readyOnlyWhenAllThreeSignalsArrive() {
        ReadyGate gate = new ReadyGate();
        CompletableFuture<Void> ready = gate.ready();
        gate.signal(Signal.REGISTRIES_LOADED);
        gate.signal(Signal.CRAFTENGINE_RELOADED);
        assertThat(ready).isNotDone();
        assertThat(gate.isReady()).isFalse();
        assertThat(gate.pending()).containsExactly(Signal.BETTERMODEL_MODELS_READY);
        assertThat(gate.describe()).contains("BetterModel");
        gate.signal(Signal.BETTERMODEL_MODELS_READY);
        assertThat(ready).isCompleted();
        assertThat(gate.isReady()).isTrue();
        assertThat(gate.describe()).isEqualTo("ready");
    }

    @Test
    void everyOrderOfSignalsWorks() {
        Signal[] s = Signal.values();
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] order : orders) {
            ReadyGate gate = new ReadyGate();
            for (int i = 0; i < order.length; i++) {
                assertThat(gate.isReady()).isFalse();
                gate.signal(s[order[i]]);
            }
            assertThat(gate.isReady()).isTrue();
        }
    }

    @Test
    void repeatedSignalsDoNotCountTwice() {
        ReadyGate gate = new ReadyGate();
        gate.signal(Signal.CRAFTENGINE_RELOADED);
        gate.signal(Signal.CRAFTENGINE_RELOADED);
        gate.signal(Signal.REGISTRIES_LOADED);
        gate.signal(Signal.REGISTRIES_LOADED);
        assertThat(gate.isReady()).isFalse();
    }

    @Test
    void waivedSignalsCountAsMetAndAreReported() {
        ReadyGate gate = new ReadyGate();
        gate.waive(Signal.CRAFTENGINE_RELOADED, "CraftEngine not installed");
        gate.waive(Signal.BETTERMODEL_MODELS_READY, "BetterModel not installed");
        assertThat(gate.isReady()).isFalse();
        gate.signal(Signal.REGISTRIES_LOADED);
        assertThat(gate.isReady()).isTrue();
        assertThat(gate.waived()).containsKeys(Signal.CRAFTENGINE_RELOADED, Signal.BETTERMODEL_MODELS_READY);
        assertThat(gate.describe()).startsWith("ready (degraded:").contains("CraftEngine not installed");
        // A late real signal after a waiver changes nothing.
        gate.signal(Signal.CRAFTENGINE_RELOADED);
        assertThat(gate.waived()).hasSize(2);
    }

    @Test
    void failureCompletesExceptionally() {
        ReadyGate gate = new ReadyGate();
        gate.signal(Signal.CRAFTENGINE_RELOADED);
        gate.fail(Signal.REGISTRIES_LOADED, new RuntimeException("bad json"));
        assertThat(gate.ready()).isCompletedExceptionally();
        assertThat(gate.isReady()).isFalse();
        assertThat(gate.describe()).isEqualTo("failed");
        gate.signal(Signal.BETTERMODEL_MODELS_READY);
        assertThat(gate.isReady()).isFalse();
    }

    @Test
    void callersCannotCompleteTheGate() {
        ReadyGate gate = new ReadyGate();
        gate.ready().complete(null);
        gate.ready().cancel(true);
        assertThat(gate.isReady()).isFalse();
        assertThat(gate.ready()).isNotDone();
    }

    @Test
    void waitersRegisteredBeforeAndAfterBothRunOnce() {
        ReadyGate gate = new ReadyGate();
        AtomicInteger runs = new AtomicInteger();
        gate.ready().thenRun(runs::incrementAndGet);
        for (Signal s : Signal.values()) {
            gate.signal(s);
        }
        gate.ready().thenRun(runs::incrementAndGet);
        assertThat(runs).hasValue(2);
    }

    @RepeatedTest(20)
    void concurrentSignalsCompleteExactlyOnce() throws Exception {
        ReadyGate gate = new ReadyGate();
        AtomicInteger completions = new AtomicInteger();
        gate.ready().thenRun(completions::incrementAndGet);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++) {
                Signal s = Signal.values()[i % 3];
                boolean waive = i >= 3;
                fs.add(pool.submit(() -> {
                    start.await();
                    if (waive) {
                        gate.waive(s, "test");
                    } else {
                        gate.signal(s);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var f : fs) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(gate.isReady()).isTrue();
        assertThat(gate.pending()).isEmpty();
        assertThat(completions).hasValue(1);
    }
}
