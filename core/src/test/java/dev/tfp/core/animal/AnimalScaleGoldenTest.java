/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.animal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.livestock.LivestockAge;
import org.junit.jupiter.api.Test;

/**
 * M5-ENT-01 golden values: {@code AGE_SCALES} ({@code Mth.map(i + 1, 1, 32, 0.8f, 1.2f)}) and the render scale
 * ({@code Mth.clampedMap(size, 1f, 32f, 0.9f, 1.1f)}), as raw float bits. Exported by a scratch program that called
 * Minecraft's own {@code net.minecraft.util.Mth} float overloads (server jar 26.3; the overloads are the same as in TFC's
 * 1.21.1), and the products {@code age x render (x 0.5f)} computed in float there too.
 */
class AnimalScaleGoldenTest {
    /** size → AGE_SCALES[size - 1] float bits. */
    private static final int[] AGE_BITS = {
        0x3f4ccccd, 0x3f501a6d, 0x3f53680d, 0x3f56b5ae, 0x3f5a034e, 0x3f5d50ee, 0x3f609e8e, 0x3f63ec2e,
        0x3f6739cf, 0x3f6a876f, 0x3f6dd50f, 0x3f7122b0, 0x3f747050, 0x3f77bdf0, 0x3f7b0b90, 0x3f7e5930,
        0x3f80d368, 0x3f827a38, 0x3f842108, 0x3f85c7d9, 0x3f876ea9, 0x3f891579, 0x3f8abc49, 0x3f8c6319,
        0x3f8e09e9, 0x3f8fb0b9, 0x3f915789, 0x3f92fe5a, 0x3f94a52a, 0x3f964bfa, 0x3f97f2ca, 0x3f99999a,
    };
    /** size → render scale float bits. */
    private static final int[] RENDER_BITS = {
        0x3f666666, 0x3f680d36, 0x3f69b406, 0x3f6b5ad6, 0x3f6d01a6, 0x3f6ea877, 0x3f704f47, 0x3f71f617,
        0x3f739ce7, 0x3f7543b7, 0x3f76ea87, 0x3f789157, 0x3f7a3828, 0x3f7bdef8, 0x3f7d85c8, 0x3f7f2c98,
        0x3f8069b4, 0x3f813d1c, 0x3f821084, 0x3f82e3ec, 0x3f83b754, 0x3f848abc, 0x3f855e24, 0x3f86318c,
        0x3f8704f4, 0x3f87d85d, 0x3f88abc5, 0x3f897f2d, 0x3f8a5295, 0x3f8b25fd, 0x3f8bf965, 0x3f8ccccd,
    };

    @Test
    void ageScalesMatchTfcBitForBit() {
        for (int size = 1; size <= 32; size++) {
            assertThat(Float.floatToRawIntBits(AnimalScale.ageScale(size))).as("AGE_SCALES[%d]", size - 1)
                    .isEqualTo(AGE_BITS[size - 1]);
            assertThat(Float.floatToRawIntBits(AnimalScale.renderScale(size))).as("render scale %d", size)
                    .isEqualTo(RENDER_BITS[size - 1]);
        }
        assertThat(AnimalScale.ageScale(1)).isEqualTo(0.8f);
        assertThat(AnimalScale.ageScale(32)).isEqualTo(1.2f);
    }

    @Test
    void sizesAreClampedLikeSetGeneticSize() {
        assertThat(AnimalScale.ageScale(-5)).isEqualTo(AnimalScale.ageScale(1));
        assertThat(AnimalScale.ageScale(99)).isEqualTo(AnimalScale.ageScale(32));
        assertThat(AnimalScale.renderScale(99)).isEqualTo(1.1f);
        assertThat(AnimalScale.modelScale(AnimalScale.NO_SIZE, LivestockAge.ADULT)).isEqualTo(1f);
        assertThat(AnimalScale.modelScale(AnimalScale.NO_SIZE, LivestockAge.CHILD)).isEqualTo(0.5f);
    }

    @Test
    void childAdultOldProducts() {
        int[][] golden = { // size, adult bits, child bits
            {1, 0x3f3851eb, 0x3eb851eb},
            {4, 0x3f4564f6, 0x3ec564f6},
            {16, 0x3f7d8725, 0x3efd8725},
            {18, 0x3f83bd77, 0x3f03bd77},
            {32, 0x3fa8f5c3, 0x3f28f5c3},
        };
        for (int[] g : golden) {
            int size = g[0];
            assertThat(Float.floatToRawIntBits(AnimalScale.modelScale(size, LivestockAge.ADULT))).as("adult %d", size)
                    .isEqualTo(g[1]);
            assertThat(Float.floatToRawIntBits(AnimalScale.modelScale(size, LivestockAge.OLD))).as("old %d", size)
                    .isEqualTo(g[1]);
            assertThat(Float.floatToRawIntBits(AnimalScale.modelScale(size, LivestockAge.CHILD))).as("child %d", size)
                    .isEqualTo(g[2]);
            // The model multiplier times the SCALE attribute is the drawn size.
            assertThat(AnimalScale.ageScale(size) * AnimalScale.modelMultiplier(size, LivestockAge.CHILD))
                    .isEqualTo(AnimalScale.modelScale(size, LivestockAge.CHILD));
        }
    }
}
