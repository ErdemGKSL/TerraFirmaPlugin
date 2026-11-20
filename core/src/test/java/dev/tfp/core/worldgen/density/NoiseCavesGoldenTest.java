/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.density;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-24: the interpolated density caves of 20 chunks (5 per seed, full height) against the oracle of
 * {@link VanillaDensityGoldenTest}, which interpolates the five functions with TFC's {@code TrilinearInterpolator} code over
 * the 4 x 8 x 4 cells and combines them as {@code calculateBlockStateAtNoise} does: the open mask (cave density not positive)
 * is compared by count and SHA-256, and 40 sampled blocks per chunk by value.
 */
class NoiseCavesGoldenTest {
    private static String sha(byte[] bits) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bits));
    }

    @Test
    void openMasksAndValuesEqualTfcsInterpolation() throws Exception {
        final Map<Long, NoiseCaves> worlds = new HashMap<>();
        final List<String[]> rows = VanillaDensityGoldenTest.rows("masks");
        int chunks = 0;
        int values = 0;
        long open = 0;
        NoiseCaves.Chunk chunk = null;
        for (String[] r : rows) {
            if (r[0].equals("v")) {
                final int x = Integer.parseInt(r[2]);
                final int z = Integer.parseInt(r[4]);
                assertThat(chunk.density(x, Integer.parseInt(r[3]), z)).as(String.join(" ", r)).isEqualTo(Double.parseDouble(r[5]));
                values++;
                continue;
            }
            final NoiseCaves caves = worlds.computeIfAbsent(Long.parseLong(r[0]), NoiseCaves::forSeed);
            chunk = caves.chunk(Integer.parseInt(r[1]), Integer.parseInt(r[2]));
            final byte[] bits = new byte[384 * 256 / 8];
            int count = 0;
            int bit = 0;
            for (int y = -64; y < 320; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++, bit++) {
                        if (chunk.density((chunk.chunkX() << 4) + x, y, (chunk.chunkZ() << 4) + z) <= 0) {
                            count++;
                            bits[bit >> 3] |= (byte) (1 << (bit & 7));
                        }
                    }
                }
            }
            assertThat(count).as(String.join(" ", r)).isEqualTo(Integer.parseInt(r[3]));
            assertThat(sha(bits)).as(String.join(" ", r)).isEqualTo(r[4]);
            open += count;
            chunks++;
        }
        assertThat(chunks).isEqualTo(20);
        assertThat(values).isEqualTo(800);
        assertThat(open).isPositive();
    }

    @Test
    void theDensityFilesAreTfcsWhenTheCheckoutIsThere() throws IOException {
        final Path dir = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"),
            "src/generated/resources/data/tfc/worldgen/density_function");
        assumeTrue(Files.isDirectory(dir), "no TFC checkout");
        for (String name : List.of("noise_caves", "noodle_caves", "noodle_thickness", "noodle_ridge_a", "noodle_ridge_b")) {
            assertThat(JsonParser.parseString(NoiseCaves.json(name)))
                .isEqualTo(JsonParser.parseString(Files.readString(dir.resolve(name + ".json"))));
        }
    }

    @Test
    void outsideTheNoiseHeightThereAreNoCaves() {
        final NoiseCaves.Chunk chunk = NoiseCaves.forSeed(1L).chunk(3, -2);
        assertThat(chunk.density(50, -65, -20)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(chunk.density(50, 320, -20)).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void chunkSamplingKeepsTheBudget() {
        final NoiseCaves caves = NoiseCaves.forSeed(9L);
        for (int i = 0; i < 4; i++) {
            caves.chunk(i, i).density(i << 4, 0, i << 4);
        }
        final long start = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 16; i++) {
            final NoiseCaves.Chunk chunk = caves.chunk(100 + i, -7);
            for (int y = -64; y < 120; y++) {
                for (int b = 0; b < 256; b++) {
                    if (chunk.density(((100 + i) << 4) + (b & 15), y, -112 + (b >> 4)) <= 0) {
                        n++;
                    }
                }
            }
        }
        final double msPerChunk = (System.nanoTime() - start) / 1e6 / 16;
        // Generous guard (shared CI machines): the corner sampling and a full chunk of interpolation stay well under the
        // worldgen chunk budget of PAR-WORLD-03
        assertThat(msPerChunk).as("ms per chunk, %d open", n).isLessThan(40);
    }
}
