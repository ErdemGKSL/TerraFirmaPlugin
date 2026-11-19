/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.perf;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * PERF-GEN-05: the core data stage of one chunk ({@link ChunkDataGenerator#generate}: region grid, climate, rivers), with a cold region
 * cache (a new generator per invocation, a distant chunk), at one thread and at four threads (one generator per thread). The surface, rock
 * and vein stages live in the paper module's terrain and are covered by the server timing PERF-GEN-01.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgsAppend = "-Xmx2g")
@State(Scope.Thread)
public class WorldgenBenchmark {
    private ChunkDataGenerator generator;
    private final AtomicInteger next = new AtomicInteger();

    private static final AtomicInteger SEEDS = new AtomicInteger();

    @Setup(Level.Invocation)
    public void setup() {
        generator = ChunkDataGenerator.forLevel(-5793902121713522665L + SEEDS.incrementAndGet());
    }

    private ChunkData one() {
        int i = next.getAndIncrement();
        // Spread grid with 4-chunk spacing, as the recorded server measurement does.
        return generator.generate((i % 64) * 4, (i / 64 % 64) * 4);
    }

    @Benchmark
    @Threads(1)
    public ChunkData chunk1Thread() {
        return one();
    }

    @Benchmark
    @Threads(4)
    public ChunkData chunk4Threads() {
        return one();
    }
}
