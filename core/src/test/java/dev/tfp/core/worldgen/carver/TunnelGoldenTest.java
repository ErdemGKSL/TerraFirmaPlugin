/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.carver;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

/**
 * Golden tunnel masks of 20 chunks against a vanilla + TFC oracle. Each row is a seed, a chunk, and for the cave carver
 * alone, the deep cave carver alone, the canyon carver alone and all three together: the number of positions in the mask
 * and the first 8 bytes (hex) of the SHA-256 of the mask's longs, little-endian ({@code CarvingMask.toArray()}, the same bit
 * layout as {@link TunnelMask#toLongArray()}). The world is y -64 to 319.
 *
 * <p>The oracle ran Minecraft's own {@code CaveWorldCarver} and {@code CanyonWorldCarver} classes (the Mojang-mapped 1.21.11
 * server jar) with {@code carveBlock} overridden to do nothing, as TFC's carvers reach their shapes. The configurations were
 * read from TFC's {@code configured_carver/cave.json}, {@code deep_cave.json} and {@code canyon.json} at the pinned commit
 * (c3b4d9536) through Minecraft's own {@code FloatProvider}, {@code HeightProvider} and {@code VerticalAnchor} codecs. The
 * driver is the loop of {@code TFCChunkGenerator.applyCarvers}: chunks -8 to 8, carver numbers from 1,
 * {@code new XoroshiroRandomSource(seed).forkPositional().at(chunkX, number, chunkZ)}. {@code Mth.sin(double)} and
 * {@code Mth.cos(double)} of 1.21.11 were replaced in the oracle by the {@code float} versions of 1.21.1, the version TFC
 * runs on (with the 1.21.11 versions, 2 of the 441 chunks of seed 42 differ). The port was compared with the oracle on
 * 1764 chunks (four seeds, chunks -10 to 10); these 20 rows are a sample with every carver present. Nothing ran on a server.
 */
class TunnelGoldenTest {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;

    /** Seed, chunk x, chunk z, then count and hash for cave, deep cave, canyon and all. */
    private static final String[] ROWS = {
        "0\t8\t5\t0\te3b0c44298fc1c14\t174\t717e2929664924a5\t3873\t32d3de3b93cd2ecb\t4047\tbc89ca6083e3b375",
        "0\t-9\t3\t3033\t18367a1bec3efbcf\t0\te3b0c44298fc1c14\t0\te3b0c44298fc1c14\t3033\t18367a1bec3efbcf",
        "0\t-7\t-2\t5\t72ade1e81bdba99a\t16291\t9f1a3af129b19058\t0\te3b0c44298fc1c14\t16296\t1fa29e26f6862125",
        "0\t9\t2\t68\tbcc647cbe6b247ac\t5721\t274462b86a9cad1d\t148\t3f98866c69aa383e\t5937\ta8d6b0b8030c888e",
        "0\t10\t-8\t362\t8ed50f90ed3be56e\t3525\t4b84d6c701061ffb\t662\t639c0ceeed191eef\t4549\teda7c196beeebb9e",
        "42\t-4\t0\t0\te3b0c44298fc1c14\t8185\td240b35182416510\t0\te3b0c44298fc1c14\t8185\td240b35182416510",
        "42\t3\t-1\t0\te3b0c44298fc1c14\t6915\te4766bf6cd01f79a\t0\te3b0c44298fc1c14\t6915\te4766bf6cd01f79a",
        "42\t10\t3\t0\te3b0c44298fc1c14\t0\te3b0c44298fc1c14\t3003\t4eb93b1909bfb67d\t3003\t4eb93b1909bfb67d",
        "42\t8\t6\t4501\t2ceb147ba82a1d14\t0\te3b0c44298fc1c14\t0\te3b0c44298fc1c14\t4501\t2ceb147ba82a1d14",
        "42\t5\t-7\t0\te3b0c44298fc1c14\t15085\t3c61c16d006a83e5\t0\te3b0c44298fc1c14\t15085\t3c61c16d006a83e5",
        "-7654321\t-2\t-2\t0\te3b0c44298fc1c14\t3209\t6a28419ba9f10c72\t5659\tfef4001d91bee760\t8049\t014dd67eafc724ee",
        "-7654321\t-6\t-1\t9372\td79c238fa5066960\t4931\ta4da17315120c91c\t0\te3b0c44298fc1c14\t14131\t3cacd0bbdaee02fa",
        "-7654321\t-10\t2\t1605\td59c9a703396bf13\t15750\tf036bde254760b26\t0\te3b0c44298fc1c14\t16913\t3c57eb8169045db1",
        "-7654321\t-9\t2\t1422\t16ec1da77990d992\t15096\t5375d4d1875e648e\t116\t536d507009615872\t16126\t98e3eeae4ffc6b89",
        "-7654321\t-9\t3\t1367\t8cc24217b14ca102\t10903\t3e3fc839199769e1\t2914\tbb4fce79c50f1450\t13987\tfe1b760c86ce9ddb",
        "1234567890123\t-2\t6\t982\t5236a561f671a447\t0\te3b0c44298fc1c14\t3934\t25d7109e98f77055\t4916\tdb5e2de2684b87d0",
        "1234567890123\t-2\t4\t3232\te66565805794d7b8\t0\te3b0c44298fc1c14\t0\te3b0c44298fc1c14\t3232\te66565805794d7b8",
        "1234567890123\t7\t10\t41\tfa3c06f258515635\t16251\t5fd8ad4cd084a950\t0\te3b0c44298fc1c14\t16292\t1e20d345d1994786",
        "1234567890123\t8\t-8\t17\t4c5e26b04e6db9b2\t8035\t840d90bb7686805b\t88\tc3811ec206b01086\t8081\t3cb830eee564cf0c",
        "1234567890123\t7\t-8\t316\t938abd791aa0f0da\t6600\t317ed7f1dc02bb9f\t2204\t4fabf6b4edb4bd36\t7629\tb44e8419fa1a131d",
    };

    @Test
    void matchesVanillaShapesWithTfcConfigurations() {
        assertThat(ROWS.length).isEqualTo(20);
        int caves = 0;
        int deepCaves = 0;
        int canyons = 0;
        for (String row : ROWS) {
            final String[] parts = row.split("\t");
            assertThat(row(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))).isEqualTo(row);
            caves += parts[3].equals("0") ? 0 : 1;
            deepCaves += parts[5].equals("0") ? 0 : 1;
            canyons += parts[7].equals("0") ? 0 : 1;
        }
        // The sample covers each carver more than once
        assertThat(caves).isGreaterThanOrEqualTo(5);
        assertThat(deepCaves).isGreaterThanOrEqualTo(5);
        assertThat(canyons).isGreaterThanOrEqualTo(5);
    }

    /** The oracle's row of a chunk: seed, chunk, then count and hash per carver and for all. */
    static String row(long seed, int chunkX, int chunkZ) {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(seed));
        final StringBuilder row = new StringBuilder().append(seed).append('\t').append(chunkX).append('\t').append(chunkZ);
        for (TunnelCarvers.Carver carver : TunnelCarvers.Carver.values()) {
            append(row, TunnelCarvers.mask(fork, chunkX, chunkZ, MIN_Y, HEIGHT, carver));
        }
        append(row, TunnelCarvers.mask(fork, chunkX, chunkZ, MIN_Y, HEIGHT));
        return row.toString();
    }

    private static void append(StringBuilder row, TunnelMask mask) {
        row.append('\t').append(mask.count()).append('\t').append(hash(mask));
    }

    static String hash(TunnelMask mask) {
        final long[] words = mask.toLongArray();
        final ByteBuffer buffer = ByteBuffer.allocate(words.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (long word : words) {
            buffer.putLong(word);
        }
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(buffer.array());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        final StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            hex.append(String.format("%02x", digest[i]));
        }
        return hex.toString();
    }
}
