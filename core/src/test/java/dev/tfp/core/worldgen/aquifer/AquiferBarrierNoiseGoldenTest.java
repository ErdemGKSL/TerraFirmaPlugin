/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.aquifer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

/**
 * Golden values of the aquifer barrier noise, sampled as {@code TFCAquifer.calculatePressure} does ({@code (x, y * 0.5, z)}).
 * Each row is a seed, a block position and the exact double (hex). The oracle ran Minecraft's own
 * {@code NormalNoise.create(new XoroshiroRandomSource(seed).forkPositional().fromHashOf("minecraft:aquifer_barrier"),
 * new NoiseParameters(-3, 1.0))} from the Mojang-mapped 1.21.11 server jar, the construction of TFC's
 * {@code NoiseSampler.barrierNoise} at the pinned commit (c3b4d9536) with the vanilla {@code aquifer_barrier.json}. The
 * positions are {@code java.util.Random(7)} draws in x, z [-10000, 10000) and y [-64, 320). Nothing ran on a server.
 */
class AquiferBarrierNoiseGoldenTest {
    private static final String[] ROWS = {
        "0\t-5764\t76\t-515\t0x1.7e0e54d2f735ep-3",
        "0\t8044\t-60\t-3746\t-0x1.339ddaa9ad4a8p-4",
        "0\t-2032\t265\t8850\t-0x1.03f8024439ddp-6",
        "0\t9534\t-64\t3712\t0x1.5e274748a6934p-4",
        "0\t8708\t303\t4495\t-0x1.747b537c9855bp-4",
        "0\t-338\t65\t738\t0x1.4ed6c6af82e2cp-3",
        "42\t1307\t167\t7842\t0x1.20178aaafd33p-2",
        "42\t-6076\t-61\t7051\t-0x1.ebd144e239409p-4",
        "42\t8718\t244\t-4761\t0x1.6f29a6c2ba565p-3",
        "42\t7400\t176\t-189\t-0x1.0cfb2b09060ep-4",
        "42\t-9877\t281\t8679\t-0x1.baef3648ebda8p-2",
        "42\t-6109\t263\t6492\t-0x1.6e36ce842a58bp-2",
        "-7654321\t1818\t2\t-4354\t0x1.a08e0bf20b43fp-6",
        "-7654321\t-6766\t181\t-1120\t-0x1.ee48a52140bap-4",
        "-7654321\t-2327\t-1\t9333\t0x1.51a1d60526cb3p-3",
        "-7654321\t4395\t192\t8289\t-0x1.d6a36f3b6c205p-2",
        "-7654321\t9276\t236\t5012\t0x1.d5ef0339275bdp-3",
        "-7654321\t-4755\t146\t170\t-0x1.541f8104ddac6p-1",
        "1234567890123\t6844\t180\t-792\t-0x1.0a441cbf62b4p-2",
        "1234567890123\t1565\t218\t7157\t0x1.7abe2672e7f96p-5",
        "1234567890123\t-326\t29\t-104\t-0x1.81d94cac8c738p-3",
        "1234567890123\t-7171\t256\t-695\t-0x1.dee1f031d5761p-3",
        "1234567890123\t-3180\t201\t-5471\t0x1.dfe597d59d7f7p-4",
        "1234567890123\t-653\t102\t-9348\t-0x1.485c38a39f02fp-2",
    };

    @Test
    void matchesVanillaNormalNoise() {
        for (String row : ROWS) {
            final String[] f = row.split("\t");
            final AquiferBarrierNoise noise = AquiferBarrierNoise.forSeed(Long.parseLong(f[0]));
            final int x = Integer.parseInt(f[1]);
            final int y = Integer.parseInt(f[2]);
            final int z = Integer.parseInt(f[3]);
            assertThat(noise.getValue(x, y * 0.5, z)).as(row).isEqualTo(Double.parseDouble(f[4]));
        }
    }

    @Test
    void sharesTheAquiferFork() {
        final PositionalRandom fork = PositionalRandom.fork(new XoroshiroRandomSource(42L));
        final AquiferBarrierNoise viaFork = AquiferBarrierNoise.create(fork);
        final AquiferBarrierNoise viaSeed = AquiferBarrierNoise.forSeed(42L);
        for (int i = 0; i < 100; i++) {
            assertThat(viaFork.getValue(i * 37.0, i * 0.5, -i * 11.0)).isEqualTo(viaSeed.getValue(i * 37.0, i * 0.5, -i * 11.0));
        }
    }

    @Test
    void boundedAndNotFlat() {
        final AquiferBarrierNoise noise = AquiferBarrierNoise.forSeed(0L);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int x = -200; x < 200; x += 3) {
            for (int z = -200; z < 200; z += 7) {
                final double v = noise.getValue(x, 10.5, z);
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
        }
        // NormalNoise of one octave: about [-1, 1] in practice, centred on 0
        assertThat(min).isLessThan(-0.3).isGreaterThan(-1.5);
        assertThat(max).isGreaterThan(0.3).isLessThan(1.5);
    }
}
