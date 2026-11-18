/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/drawing/RegionGeneratorTests.java @ c3b4d9536 (drawWithRivers)
 */
package dev.tfp.core.worldgen.river;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.RegionGenerator;
import dev.tfp.core.worldgen.region.RegionMapRenderColours;
import dev.tfp.core.worldgen.region.RegionSettings;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Renders the river map of TFC's {@code drawWithRivers}: every river edge's midpoint fractal over the rivers and lakes
 * map, sampled {@link RegionMapRenderColours#RIVER_SCALE} times per grid point, for the centre grid (0, 75) and radius 50
 * (100 x 100 grid points, 400 x 400 pixels). The PNG goes to {@code build/worldgen-images/<seed>/rivers.png}; the pinned
 * hashes are this renderer's and equal TFC's at c3b4d9536 ({@code docs/worldgen/maps/README.md}).
 */
class RiverMapRenderTest {
    private static final Path OUT = Path.of("build", "worldgen-images");
    private static final long[] SEEDS = {42L, -7L, 20260917L};
    private static final long[] EXPECTED = {6952514703577498831L, -7537343335837014934L, -7275090879569258426L};

    @Test
    void riverMapsHaveThePinnedPixels() throws IOException {
        final long[] actual = new long[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            final long seed = SEEDS[i];
            final boolean finite = seed == 20260917L;
            final RegionGenerator generator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
            final BufferedImage image = RegionMapRenderColours.renderRivers(generator, 0, 75, 50);
            assertThat(image.getWidth()).isEqualTo(100 * RegionMapRenderColours.RIVER_SCALE);

            final Path dir = Files.createDirectories(OUT.resolve(Long.toString(seed)));
            final Path file = dir.resolve("rivers.png");
            assertThat(ImageIO.write(image, "png", file.toFile())).isTrue();

            final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            long h = 0;
            int river = 0;
            for (int pixel : pixels) {
                h = h * 31 + (pixel & 0xFFFFFF);
                if ((pixel & 0xFFFFFF) == 0x64D2FA) {
                    river++;
                }
            }
            assertThat(river).as("river pixels of seed %d", seed).isPositive();
            actual[i] = h;
        }
        assertThat(actual).as("pixel hash of the river map per seed").containsExactly(EXPECTED);
    }
}
