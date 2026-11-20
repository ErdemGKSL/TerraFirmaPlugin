/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.rock.RockLayerSampler;
import dev.tfp.core.worldgen.rock.RockSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PAR-WORLD-23: TFC's {@code KarstSurfaceRocks}. A point is karst exactly when TFC's {@code sampleAtLayer(point.rock, 0)}
 * (golden in {@code rock/RockLayerSettingsTest}) names a rock whose exported settings say {@code karst}. The task draws no
 * random, so every non-karst point keeps the region of the karst-free generator (the region and biome goldens' oracle ran
 * without the karst task and stay valid for it); karst points can only change their biome, to a karst variant.
 */
class KarstSurfaceRocksGoldenTest {
    private static Map<String, RockSettings> rocks() {
        final Path root = Path.of("..", "content", "generated", "content", "data");
        assumeTrue(Files.isDirectory(root.resolve(RockSettings.DIR)), "no generated rock settings (run tools/datagen)");
        return RockSettings.load(RockLayerSampler.PRESET.rocks(), path -> {
            try {
                final Path f = root.resolve(path);
                return Files.isRegularFile(f) ? Optional.of(Files.readString(f)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void karstFlagsFollowTheSurfaceRock() {
        final Map<String, RockSettings> rocks = rocks();
        int karst = 0;
        int points = 0;
        for (long seed : new long[] {42L, -7L, 20260917L}) {
            final RegionGenerator with = new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(seed), RockLayerSampler.PRESET,
                r -> rocks.get(r).isKarst());
            final RegionGenerator without = new RegionGenerator(RegionSettings.OVERWORLD, Seed.of(seed));
            for (int[] g : new int[][] {{0, 0}, {-400, -60}, {350, -400}, {120, -120}, {-250, 350}}) {
                final Region a = with.getOrCreateRegion(g[0], g[1]);
                final Region b = without.getOrCreateRegion(g[0], g[1]);
                for (Region.Point p : a.points()) {
                    final Region.Point q = b.atOrThrow(p.x, p.z);
                    assertThat(p.rock).isEqualTo(q.rock);
                    assertThat(q.isSurfaceRockKarst).isFalse();
                    final boolean expected = rocks.get(RockLayerSampler.PRESET.sampleAtLayer(p.rock, 0)).isKarst();
                    assertThat(p.isSurfaceRockKarst).isEqualTo(expected);
                    assertThat(p.temperature).isEqualTo(q.temperature);
                    assertThat(p.rainfall).isEqualTo(q.rainfall);
                    if (!expected) {
                        assertThat(p.biome).as("non-karst point at %d,%d", p.x, p.z).isEqualTo(q.biome);
                    } else {
                        karst++;
                    }
                    points++;
                }
            }
        }
        // land is sedimentary three times in four and four of its seven rocks are karst: karst points are common
        assertThat(karst).isGreaterThan(points / 50);
    }
}
