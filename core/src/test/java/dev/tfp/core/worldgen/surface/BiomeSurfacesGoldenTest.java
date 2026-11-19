/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/SurfaceManager.java @ c3b4d9536 (golden values), with every builder of
 * world/surface/builder, SurfaceStates.java, SoilSurfaceState.java and SurfaceBuilderContext.java
 */
package dev.tfp.core.worldgen.surface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden columns of every TFC biome's own surface builder, with the full block identity (soil variant, sand colour, basalt,
 * tuff, ice, sea ice). {@code BiomeSurfacesGolden.txt} next to this file holds 1000 columns, four per biome and level seed for
 * the seeds 42 and -7316240178312371234, from a fixed random draw: the rock top (oceans and ice sheets lower), the slope, the
 * climate, the biome weight, the warmest month at sea level ({@code NaN}: no climate model, so no frozen ocean), the rock at
 * {@code y = 0} (0 plain, 1 karst, 2 mafic), the original biome under a river, and a cinder cone easing for one column in six.
 * The last field is what the pass wrote, from the highest height down: {@code y:} then each block (or {@code -} for a height
 * left alone), with {@code *n} for runs.
 * <p>
 * The oracle is the harness of {@link SurfaceGoldenTest}: TFC's builders compiled unchanged against Minecraft 1.21.11, built
 * from one {@code Seed.of(levelSeed)} stream in TFC's registry order with the atoll and tuya wrappers' seeds drawn after each
 * builder, every builder wrapped in TFC's {@code CinderConeSurfaceBuilder} (its easing an input of the column), and the chunk
 * random {@code new XoroshiroRandomSource(chunkX * 1842639486192314L, chunkZ * 579238196380231L)} fresh for each column. The
 * local rock is one rock type, so the rock's sand and the river sand (the sea-level rock's sand) are the same block.
 */
class BiomeSurfacesGoldenTest {
    private static final Path GOLDEN = Path.of("src/test/java/dev/tfp/core/worldgen/surface/BiomeSurfacesGolden.txt");
    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;

    @Test
    void everyBiomeMatchesTfc() throws IOException {
        final List<String> lines = Files.readAllLines(GOLDEN).stream().filter(l -> !l.startsWith("#") && !l.isBlank()).toList();
        assertThat(lines).hasSize(1000);
        final Map<String, Integer> ids = new HashMap<>();
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            ids.put(TfcBiomes.name(id), id);
        }
        final Map<Long, BiomeSurfaces> surfaces = new HashMap<>();
        final Set<String> biomes = new HashSet<>();
        final List<String> mismatches = new ArrayList<>();
        for (String line : lines) {
            final String[] f = line.split(" ");
            final long seed = Long.parseLong(f[1]);
            final int x = Integer.parseInt(f[2]);
            final int z = Integer.parseInt(f[3]);
            final int top = Integer.parseInt(f[4]);
            final float warmest = Float.parseFloat(f[12]);
            final int rock0 = Integer.parseInt(f[13]);
            final SurfaceColumn column = new SurfaceColumn(x, z, SEA_LEVEL, Double.parseDouble(f[5]), Float.parseFloat(f[6]),
                Float.parseFloat(f[7]), Float.parseFloat(f[8]), Float.parseFloat(f[9]), (int) SurfaceNoise.shoreTide(seed).noise(x, z),
                f[10].equals("1"), Double.parseDouble(f[11]), Integer.parseInt(f[16]), Float.parseFloat(f[15]),
                Float.isNaN(warmest) ? Float.POSITIVE_INFINITY : warmest, rock0 == 1, rock0 == 2, ids.get(f[14]));
            final BiomeSurfaces table = surfaces.computeIfAbsent(seed, s -> new BiomeSurfaces(Seed.of(s)));
            final ColumnSurface surface = ColumnSurface.build(table.builder(ids.get(f[0])), column, top, MIN_Y,
                ColumnSurface.chunkRandom(x >> 4, z >> 4));
            final String actual = encode(surface);
            if (!actual.equals(f[17])) {
                mismatches.add(line + "\n    got " + actual);
            }
            biomes.add(f[0]);
        }
        assertThat(mismatches).as(mismatches.size() + " mismatches").isEmpty();
        assertThat(biomes).hasSize(TfcBiomes.COUNT);
    }

    @Test
    void theTableHoldsEveryBiomeAndItsRiverFlags() {
        final BiomeSurfaces table = new BiomeSurfaces(Seed.of(1L));
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            assertThat(table.builder(id)).as(TfcBiomes.name(id)).isNotNull();
        }
        assertThat(table.isShore(TfcBiomes.SHORE)).isTrue();
        assertThat(table.isShore(TfcBiomes.PLAINS)).isFalse();
        assertThat(table.hasSandyRiverShores(TfcBiomes.PLAINS)).isTrue();
        assertThat(table.hasSandyRiverShores(TfcBiomes.OCEAN)).isFalse();
    }

    /** The oracle's encoding: the written blocks from the highest height down, gaps as '-', runs as {@code *n}. */
    static String encode(ColumnSurface surface) {
        int hi = Integer.MIN_VALUE;
        int lo = Integer.MAX_VALUE;
        for (int y = surface.highestY(); y >= surface.lowestY(); y--) {
            if (surface.block(y) != null) {
                hi = Math.max(hi, y);
                lo = Math.min(lo, y);
            }
        }
        if (hi == Integer.MIN_VALUE) {
            return "none";
        }
        final StringBuilder sb = new StringBuilder().append(hi).append(':');
        String prev = null;
        int n = 0;
        for (int y = hi; y >= lo - 1; y--) {
            final String name = y < lo ? null : name(surface.block(y));
            if (name != null && name.equals(prev)) {
                n++;
                continue;
            }
            if (prev != null) {
                sb.append(prev);
                if (n > 1) {
                    sb.append('*').append(n);
                }
                sb.append(',');
            }
            prev = name;
            n = 1;
        }
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static String name(SurfaceBlock block) {
        if (block == null) {
            return "-";
        }
        // One rock type in the oracle: the river sand is the rock's sand
        return block == SurfaceBlock.RIVER_SAND ? "ROCK_SAND" : block.name();
    }
}
