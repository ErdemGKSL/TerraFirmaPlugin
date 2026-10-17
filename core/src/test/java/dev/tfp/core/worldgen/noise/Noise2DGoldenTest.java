/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.noise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the {@link Noise2D} wrappers on {@code new OpenSimplex2D(42)}: ridged, clamped, terraces, easeIn
 * (which calls {@code Mth.clampedMap}) and scaled. Inputs are the first four points of {@link OpenSimplex2DGoldenTest}.
 *
 * The expected values were printed by TFC's own noise classes at the pinned commit (c3b4d9536):
 * {@code net.dries007.tfc.world.noise.{FastNoiseLite, Noise2D, OpenSimplex2D, Cellular2D}} copied unchanged into a scratch
 * harness. Only {@code Helpers.diamondAngle} is stubbed (copied verbatim), and {@code Mth} is Minecraft 1.21.11's own class.
 * Nothing was run on a server. Doubles are printed with {@link Double#toString}, which round-trips exactly, so the
 * assertions are exact.
 */
class Noise2DGoldenTest {
    private static final double[] D_XS = {0.5, 13.25, -100.125, 1000.3};
    private static final double[] D_ZS = {0.5, -4.75, 250.5, -2000.7};
    private static final double[] D_RIDGED = {0.5648744702339172, -0.1862565279006958, -0.594314694404602, 0.4231058359146118};
    private static final double[] D_CLAMPED = {-0.21756276488304138, -0.3, -0.3, -0.2884470820426941};
    private static final double[] D_TERRACES = {-0.5, -1.0, -1.0, -0.5};
    private static final double[] D_EASE_IN = {-0.38706166630643546, -0.29656413197517395, -1.594314694404602, -0.5768941640853882};
    private static final double[] D_SCALED = {0.6736558526754379, 0.11030760407447815, -0.19573602080345154, 0.5673293769359589};

    @Test
    void wrappersMatchTfc() {
        for (int i = 0; i < D_XS.length; i++) {
            final double x = D_XS[i], z = D_ZS[i];
            assertThat(new OpenSimplex2D(42L).ridged().noise(x, z)).as("ridged %d", i).isEqualTo(D_RIDGED[i]);
            assertThat(new OpenSimplex2D(42L).clamped(-0.3, 0.4).noise(x, z)).as("clamped %d", i).isEqualTo(D_CLAMPED[i]);
            assertThat(new OpenSimplex2D(42L).terraces(4).noise(x, z)).as("terraces %d", i).isEqualTo(D_TERRACES[i]);
            assertThat(new OpenSimplex2D(42L).easeIn(-0.2, 0.3, 0.5, 2.0, new OpenSimplex2D(7L)).noise(x, z)).as("easeIn %d", i).isEqualTo(D_EASE_IN[i]);
            assertThat(new OpenSimplex2D(42L).scaled(-0.5, 2.5).noise(x, z)).as("scaled %d", i).isEqualTo(D_SCALED[i]);
        }
    }
}
