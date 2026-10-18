/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Renders the region map of one region per seed to {@code build/worldgen-images/region-<seed>.png} (relative to the
 * test working directory, the {@code core} module). One pixel is one grid point; points outside the continent cell are
 * black. Colours: deep ocean to light ocean by ocean depth, land green, island sand, mountain grey, volcanic red, river
 * and lake blue. The image is only written for a visual check; the test asserts the file, the size, and a hash of the
 * pixel array, which is the same for every run of this renderer.
 */
class RegionMapRenderTest {
    private static final Path OUT = Path.of("build", "worldgen-images");
    /** The seeds and grid point of the golden test, one region each (grid (0, 0)). */
    private static final long[] SEEDS = {42L, -7L, 20260917L};
    private static final int GRID_X = 0;
    private static final int GRID_Z = 0;
    /** Hash of the rendered pixel arrays of the three regions, computed by this renderer at this commit. */
    private static final long EXPECTED_PIXEL_HASH = -3439752065245483941L;

    @Test
    void rendersRegionMapsWithStablePixels() throws IOException {
        Files.createDirectories(OUT);
        long combined = 0;
        for (long seed : SEEDS) {
            final boolean finite = seed == 20260917L;
            final RegionGenerator generator = new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
            final Region region = generator.getOrCreateRegion(GRID_X, GRID_Z);
            final BufferedImage image = render(region);

            final Path file = OUT.resolve("region-" + seed + ".png");
            assertThat(ImageIO.write(image, "png", file.toFile())).as("png writer for %s", file).isTrue();

            assertThat(file).exists();
            assertThat(Files.size(file)).isPositive();
            final BufferedImage read = ImageIO.read(file.toFile());
            assertThat(read.getWidth()).isEqualTo(region.sizeX());
            assertThat(read.getHeight()).isEqualTo(region.sizeZ());

            final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            for (int pixel : pixels) {
                combined = combined * 31 + pixel;
            }
            combined = combined * 31 + image.getWidth();
            combined = combined * 31 + image.getHeight();
        }
        assertThat(combined).as("pixel hash of the rendered region maps").isEqualTo(EXPECTED_PIXEL_HASH);
    }

    /** One pixel per grid point of the region's bounding box, row by row from minZ, column by column from minX. */
    static BufferedImage render(Region region) {
        final BufferedImage image = new BufferedImage(region.sizeX(), region.sizeZ(), BufferedImage.TYPE_INT_RGB);
        for (int z = region.minZ(); z <= region.maxZ(); z++) {
            for (int x = region.minX(); x <= region.maxX(); x++) {
                final Region.Point point = region.at(x, z);
                final int rgb = point == null ? 0x000000 : colour(point);
                image.setRGB(x - region.minX(), z - region.minZ(), rgb);
            }
        }
        return image;
    }

    static int colour(Region.Point p) {
        if (p.river() || p.lake()) {
            return 0x3CA0E6;
        }
        if (!p.land()) {
            // Ocean: 0 (shallow) to 5 (deep) by ocean depth.
            final int depth = Math.min(Math.max(p.oceanDepth, 0), 5);
            return blend(0x7FC8F0, 0x0A2A6E, depth / 5f);
        }
        if (p.volcanic()) {
            return 0xB42814;
        }
        if (p.mountain()) {
            return 0x8C8C8C;
        }
        if (p.island()) {
            return 0xD8C080;
        }
        return 0x4E9A3A;
    }

    private static int blend(int from, int to, float t) {
        final int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        final int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        final int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }
}
