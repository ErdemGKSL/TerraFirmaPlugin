/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.worldgen.bench;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.core.worldgen.height.BiomeLayers;
import dev.tfp.core.worldgen.height.SurfaceHeights;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionSettings;
import dev.tfp.core.worldgen.river.RiverBlendType;
import dev.tfp.core.worldgen.volcano.Volcanoes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Throughput of TFC's pure core stages ({@link ChunkDataGenerator#generate} chunk data plus {@link SurfaceHeights#chunk} heights) at 1, 4
 * and 8 worker threads over a contiguous chunk grid. Not a JMH benchmark: the worldgen build file is not owned by PAR-WORLD-03,
 * so this is a plain main with warm-up rounds and the same measurement discipline (fixed seed, fresh grid per round, wall time
 * over the whole grid). Run: {@code java -cp worldgen/build/classes/java/main:core/build/classes/java/main:worldgen/build/jmh-classes
 * dev.tfp.worldgen.bench.ChunkCostBench [side=64] [rounds=3]}.
 */
public final class ChunkCostBench {
    private static final long SEED = -5793902121713522665L;

    private ChunkCostBench() {
    }

    public static void main(String[] args) throws Exception {
        int side = args.length > 0 ? Integer.parseInt(args[0]) : 64;
        int rounds = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        System.out.println("cpus=" + Runtime.getRuntime().availableProcessors() + " grid=" + side + "x" + side + " rounds=" + rounds);
        // The same construction order as TfcTerrain: one Seed stream, region, chunk data, biome layer
        Seed stream = Seed.of(SEED);
        RegionGenerator regions = new RegionGenerator(RegionSettings.OVERWORLD, stream);
        ChunkDataGenerator gen = new ChunkDataGenerator(regions, stream);
        BiomeLayers layers = new BiomeLayers(regions, stream);
        Volcanoes volcanoes = new Volcanoes(SEED);
        SurfaceHeights heights = new SurfaceHeights(SEED, regions, layers, RiverBlendType.createSamplers(SEED), volcanoes.cones());
        run(gen, heights, 4, 16, 0); // warm-up
        for (int threads : new int[] {1, 4, 8}) {
            List<Double> perSecond = new ArrayList<>();
            for (int r = 0; r < rounds; r++) {
                // A new grid origin per round so the generator's caches do not serve the same chunks twice
                perSecond.add(run(gen, heights, threads, side, 1000 + r * 200));
            }
            double[] v = perSecond.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            System.out.printf("threads=%d chunks/s median=%.1f min=%.1f max=%.1f%n", threads, v[v.length / 2], v[0], v[v.length - 1]);
        }
    }

    private static double run(ChunkDataGenerator gen, SurfaceHeights heights, int threads, int side, int origin) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int x = 0; x < side; x++) {
                final int cx = origin + x;
                jobs.add(() -> {
                    int n = 0;
                    for (int z = 0; z < side; z++) {
                        n += gen.generate(cx, origin + z) != null && heights.chunk(cx, origin + z) != null ? 1 : 0;
                    }
                    return n;
                });
            }
            long t0 = System.nanoTime();
            int total = 0;
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                total += f.get();
            }
            double s = (System.nanoTime() - t0) / 1e9;
            return total / s;
        } finally {
            pool.shutdown();
        }
    }
}
