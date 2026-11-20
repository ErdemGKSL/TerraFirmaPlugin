/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.density;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-24: the vanilla noises and TFC's five cave density functions against an oracle. The rows were written by a scratch
 * harness outside the repository that runs the Minecraft 1.21.11 server classes (NormalNoise, XoroshiroRandomSource,
 * DensityFunctions built from TFC's density JSON of c3b4d9536 at /opt/tfc, with TFC's Spaghetti2D/3D compute copied
 * verbatim and TFC's NoiseSampler wiring {@code fork.fromHashOf(<noise id>)}), for seeds 0, 42, -123456789 and
 * 1234567890123: 60 random positions per noise and seed (a fifth of them on integer coordinates) and 150 random block
 * positions per function and seed.
 */
class VanillaDensityGoldenTest {
    static List<String[]> rows(String name) throws IOException {
        final List<String[]> out = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(
            VanillaDensityGoldenTest.class.getResourceAsStream("/dev/tfp/core/worldgen/noisecaves/" + name + ".txt.gz")),
            StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("#")) {
                    out.add(line.split(" "));
                }
            }
        }
        return out;
    }

    @Test
    void everyVanillaNoiseMatchesNormalNoise() throws IOException {
        final Map<String, VanillaDensityNoise> noises = new HashMap<>();
        final List<String[]> rows = rows("noises");
        assertThat(rows).hasSize(4 * 17 * 60);
        for (String[] r : rows) {
            final long seed = Long.parseLong(r[0]);
            final String id = "minecraft:" + r[1];
            final VanillaDensityNoise noise = noises.computeIfAbsent(seed + id,
                k -> VanillaDensityNoise.create(PositionalRandom.fork(new XoroshiroRandomSource(seed)), id));
            assertThat(noise.getValue(Double.parseDouble(r[2]), Double.parseDouble(r[3]), Double.parseDouble(r[4])))
                .as(String.join(" ", r)).isCloseTo(Double.parseDouble(r[5]), within(1e-9));
        }
        assertThat(VanillaDensityNoise.PARAMETERS).hasSize(17);
    }

    @Test
    void theFiveCaveFunctionsMatchTheOracle() throws IOException {
        final Map<Long, NoiseCaves> worlds = new HashMap<>();
        final List<String[]> rows = rows("functions");
        assertThat(rows).hasSize(4 * 5 * 150);
        int exact = 0;
        for (String[] r : rows) {
            final NoiseCaves caves = worlds.computeIfAbsent(Long.parseLong(r[0]), NoiseCaves::forSeed);
            final double expected = Double.parseDouble(r[5]);
            final double actual = caves.function(r[1]).compute(Integer.parseInt(r[2]), Integer.parseInt(r[3]), Integer.parseInt(r[4]));
            assertThat(actual).as(String.join(" ", r)).isCloseTo(expected, within(1e-9));
            if (actual == expected) {
                exact++;
            }
        }
        assertThat(exact).isEqualTo(rows.size());
    }
}
