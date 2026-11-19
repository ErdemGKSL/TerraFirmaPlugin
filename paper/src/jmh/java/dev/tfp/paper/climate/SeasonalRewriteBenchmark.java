/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import dev.tfp.core.calendar.Month;
import dev.tfp.paper.packet.BiomePaletteRewrite;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * PERF-SEA-01 (PAR-CLIM-06): the cost of the seasonal biome remap on the packet path.
 *
 * <ul>
 *   <li>{@link #rewriteSection}: {@link BiomePaletteRewrite#rewriteSection} for one 4 x 4 x 4 biome section, followed by the
 *       palette sizing ({@code distinct}, {@code bits}) the handler does on a change.</li>
 *   <li>{@link #rewriteChunk}: the same for a 24-section chunk (world height 384).</li>
 *   <li>{@link #registrySync}: building the per-user {@code IdMap} for all variants, the work of
 *       {@code SeasonalBiomeHandler.ids}: one name build and one registry lookup per base and variant. The client registry is
 *       a {@link HashMap} by name standing in for PacketEvents' {@code IRegistry.getByName} (a name map too).</li>
 * </ul>
 *
 * Run (no Gradle source set exists for paper yet, see docs/perf/seasonal-rewrite.md): the command is documented there.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class SeasonalRewriteBenchmark {
    private static final int SECTIONS = 24;
    private static final int BASE_REGISTRY_OFFSET = 100;

    private SeasonalChunkTable table;
    private BiomePaletteRewrite.IdMap ids;
    private int[] pristine;
    private int[] cells;
    private Map<String, Integer> clientRegistry;

    @Setup
    public void setup() {
        Random random = new Random(42);
        byte[] base = new byte[SeasonalChunkTable.COLUMNS];
        for (int i = 0; i < base.length; i++) {
            base[i] = (byte) random.nextInt(SeasonalBiomeRemap.BASE_COUNT);
        }
        short[] variants = new short[SECTIONS * SeasonalChunkTable.CELLS_PER_SECTION];
        for (int i = 0; i < variants.length; i++) {
            int biome = base[i & 15];
            variants[i] = (short) SeasonalBiomeRemap.variantIndex(biome, random.nextInt(dev.tfp.core.climate.SeasonalColour.BAND_COUNT));
        }
        table = new SeasonalChunkTable(0, 0, -64, SECTIONS, Month.JULY, base, variants);

        clientRegistry = new HashMap<>();
        int next = BASE_REGISTRY_OFFSET;
        for (int i = 0; i < SeasonalBiomeRemap.BASE_COUNT; i++) {
            clientRegistry.put(SeasonalBiomeRemap.baseIdOf(i), next++);
        }
        for (int i = 0; i < SeasonalBiomeRemap.VARIANT_COUNT; i++) {
            clientRegistry.put(SeasonalBiomeRemap.variantIdOf(i), next++);
        }
        ids = sync();

        pristine = new int[SeasonalChunkTable.CELLS_PER_SECTION];
        for (int cell = 0; cell < pristine.length; cell++) {
            pristine[cell] = ids.base()[table.baseAt(cell)];
        }
        cells = new int[pristine.length];
    }

    private BiomePaletteRewrite.IdMap sync() {
        int[] base = new int[SeasonalBiomeRemap.BASE_COUNT];
        for (int i = 0; i < base.length; i++) {
            base[i] = clientRegistry.getOrDefault(SeasonalBiomeRemap.baseIdOf(i), -1);
        }
        int[] variants = new int[SeasonalBiomeRemap.VARIANT_COUNT];
        for (int i = 0; i < variants.length; i++) {
            variants[i] = clientRegistry.getOrDefault(SeasonalBiomeRemap.variantIdOf(i), -1);
        }
        return new BiomePaletteRewrite.IdMap(base, variants, clientRegistry.size());
    }

    @Benchmark
    public int rewriteSection() {
        System.arraycopy(pristine, 0, cells, 0, cells.length);
        return section(0);
    }

    @Benchmark
    public int rewriteChunk() {
        int sum = 0;
        for (int s = 0; s < SECTIONS; s++) {
            System.arraycopy(pristine, 0, cells, 0, cells.length);
            sum += section(s);
        }
        return sum;
    }

    private int section(int s) {
        BiomePaletteRewrite.rewriteSection(cells, table, s, ids);
        return BiomePaletteRewrite.bits(BiomePaletteRewrite.distinct(cells), ids.registrySize());
    }

    @Benchmark
    public BiomePaletteRewrite.IdMap registrySync() {
        return sync();
    }
}
