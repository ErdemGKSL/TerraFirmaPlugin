/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.noise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link OpenSimplex2D}: the raw one-octave noise (constructor seed folding included) and the
 * chain {@code octaves(3).scaled(43, 63).spread(0.014f)} that TFC's region chunk-data generator uses for layer height.
 * Seeds include a negative value and one that needs the high 32 bits.
 *
 * The expected values were printed by TFC's own noise classes at the pinned commit (c3b4d9536):
 * {@code net.dries007.tfc.world.noise.{FastNoiseLite, Noise2D, OpenSimplex2D, Cellular2D}} copied unchanged into a scratch
 * harness. Only {@code Helpers.diamondAngle} is stubbed (copied verbatim), and {@code Mth} is Minecraft 1.21.11's own class.
 * Nothing was run on a server. Doubles are printed with {@link Double#toString}, which round-trips exactly, so the
 * assertions are exact.
 */
class OpenSimplex2DGoldenTest {
    private static final long[] A_SEEDS = {0L, 0L, 0L, 0L, 1L, 1L, 1L, 1L, 42L, 42L, 42L, 42L, -7L, -7L, -7L, -7L, 1234567890123L, 1234567890123L, 1234567890123L, 1234567890123L};
    private static final double[] A_XS = {0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3};
    private static final double[] A_ZS = {0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7};
    private static final double[] A_EXPECTED = {0.3464852273464203, -0.49874457716941833, 0.8572891354560852, -0.3236311972141266, 0.21682773530483246, 0.3329634368419647, 0.5504550933837891, 0.13416820764541626, -0.21756276488304138, -0.5931282639503479, -0.797157347202301, -0.2884470820426941, 0.0521022230386734, -0.40353918075561523, -0.5159553289413452, 0.13212646543979645, -0.052231840789318085, 0.7850931286811829, -0.5992772579193115, -0.3540245592594147};
    private static final long[] B_SEEDS = {0L, 0L, 0L, 0L, 1L, 1L, 1L, 1L, 42L, 42L, 42L, 42L, -7L, -7L, -7L, -7L, 1234567890123L, 1234567890123L, 1234567890123L, 1234567890123L};
    private static final double[] B_XS = {0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3, 0.5, 13.25, -100.125, 1000.3};
    private static final double[] B_ZS = {0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7, 0.5, -4.75, 250.5, -2000.7};
    private static final double[] B_EXPECTED = {53.05646366626024, 52.537634029984474, 52.77772422134876, 51.55429366230965, 52.969000078272074, 52.801506977528334, 56.31066280603409, 49.953992545604706, 53.09758391417563, 52.60705764591694, 53.93052916228771, 50.339650988578796, 53.0195105036255, 54.10638454556465, 52.58201240748167, 54.710445284843445, 52.96446600649506, 53.01971078570932, 54.243607476353645, 55.40553483366966};

    @Test
    void rawOneOctaveMatchesTfc() {
        for (int i = 0; i < A_EXPECTED.length; i++) {
            assertThat(new OpenSimplex2D(A_SEEDS[i]).noise(A_XS[i], A_ZS[i])).as("sample %d", i).isEqualTo(A_EXPECTED[i]);
        }
    }

    @Test
    void layerHeightChainMatchesTfc() {
        for (int i = 0; i < B_EXPECTED.length; i++) {
            final double value = new OpenSimplex2D(B_SEEDS[i]).octaves(3).scaled(43, 63).spread(0.014f).noise(B_XS[i], B_ZS[i]);
            assertThat(value).as("sample %d", i).isEqualTo(B_EXPECTED[i]);
        }
    }
}
