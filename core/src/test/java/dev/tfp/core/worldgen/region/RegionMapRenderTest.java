/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/drawing/RegionGeneratorTests.java @ c3b4d9536 (the drawn window and maps)
 */
package dev.tfp.core.worldgen.region;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.region.RegionMapRenderColours.MapKind;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Renders TFC's region drawing maps from our port: the window of {@code RegionGeneratorTests.testRegionGenerator}
 * (centre grid (0, 75), radius 200, so 400 x 400 grid points, about 51 km a side), one map per {@link MapKind}, for the
 * seeds of the region golden test. The PNGs go to {@code build/worldgen-images/<seed>/<map>.png} (relative to the
 * {@code core} module) for the visual check against TFC; {@code docs/worldgen/maps/README.md} records the comparison
 * with the same maps rendered from TFC's own classes (pixel diff 0).
 * <p>
 * The pinned values are one hash per seed over the pixels of every map, computed by this renderer and equal to the hash
 * of TFC's images at c3b4d9536.
 */
class RegionMapRenderTest {
    static final Path OUT = Path.of("build", "worldgen-images");
    static final long[] SEEDS = {42L, -7L, 20260917L};
    static final int CENTER_X = 0;
    static final int CENTER_Z = 75;
    static final int RADIUS = 200;

    /** Pixel hash per seed over all maps in {@link MapKind} order, same order as {@link #SEEDS}. */
    private static final long[] EXPECTED = {8156683512142618464L, -2413073636822255220L, -2614552465531807070L};

    @Test
    void regionMapsHaveThePinnedPixels() throws IOException {
        final long[] actual = new long[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            final long seed = SEEDS[i];
            final RegionGenerator generator = generator(seed);
            final Path dir = Files.createDirectories(OUT.resolve(Long.toString(seed)));
            final BufferedImage[] images = RegionMapRenderColours.renderAll(generator, CENTER_X, CENTER_Z, RADIUS);
            long hash = 0;
            for (MapKind kind : MapKind.values()) {
                final BufferedImage image = images[kind.ordinal()];
                assertThat(image.getWidth()).isEqualTo(2 * RADIUS);
                assertThat(image.getHeight()).isEqualTo(2 * RADIUS);
                write(image, dir.resolve(kind.name().toLowerCase(Locale.ROOT) + ".png"));
                hash = hash * 31 + pixelHash(image);
            }
            actual[i] = hash;
        }
        assertThat(actual).as("pixel hash of the region maps per seed").containsExactly(EXPECTED);
    }

    /** The overworld preset settings; seed 20260917 has finite continents on, as in the region golden test. */
    static RegionGenerator generator(long seed) {
        final boolean finite = seed == 20260917L;
        return new RegionGenerator(new RegionSettings(0.5f, finite, 20_000, 0f, 20_000, 0f), Seed.of(seed));
    }

    static void write(BufferedImage image, Path file) throws IOException {
        assertThat(ImageIO.write(image, "png", file.toFile())).as("png writer for %s", file).isTrue();
    }

    /** A hash of the RGB pixels, row by row, and the size. */
    static long pixelHash(BufferedImage image) {
        final int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        long h = 0;
        for (int pixel : pixels) {
            h = h * 31 + (pixel & 0xFFFFFF);
        }
        h = h * 31 + image.getWidth();
        return h * 31 + image.getHeight();
    }
}
