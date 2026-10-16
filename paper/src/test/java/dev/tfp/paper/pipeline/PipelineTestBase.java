/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;

/** Shared fixture: an in-memory world and two fake regions split at chunkX = 0. */
abstract class PipelineTestBase {
    static final int AIR = 0;
    static final int STONE = 100;

    final Map<BlockPos, Integer> blocks = new ConcurrentHashMap<>();
    final AtomicInteger writes = new AtomicInteger();
    final BlockAccess<Integer> world = new BlockAccess<>() {
        @Override
        public Integer read(BlockPos pos) {
            return blocks.getOrDefault(pos, AIR);
        }

        @Override
        public void write(BlockPos pos, Integer state) {
            writes.incrementAndGet();
            blocks.put(pos, state);
        }
    };
    final FakeRegions regions = new FakeRegions(2);
    final ExecutorService workers = Executors.newFixedThreadPool(3);
    volatile long decideSleepMillis;

    static boolean toy(Integer s) {
        return s != null && s >= 1 && s <= 7;
    }

    /** Polls until the condition holds (machines under load make fixed sleeps flaky). */
    static void eventually(java.util.function.BooleanSupplier cond, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertThat(cond.getAsBoolean()).as(what).isTrue();
    }

    @AfterEach
    void stop() {
        regions.close();
        workers.shutdownNow();
    }

}
