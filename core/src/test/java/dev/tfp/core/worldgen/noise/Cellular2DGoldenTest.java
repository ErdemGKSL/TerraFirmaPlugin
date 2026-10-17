/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.noise;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link Cellular2D} (TFC's defaults: jitter 0.43701595f, sample 1). The cell fields are the same as
 * TFC's {@code Cellular2D.Cell}. The seed is folded to an int with {@code HashCommon.long2int}, so one of the seeds is
 * negative and large.
 *
 * The expected values were printed by TFC's own noise classes at the pinned commit (c3b4d9536):
 * {@code net.dries007.tfc.world.noise.{FastNoiseLite, Noise2D, OpenSimplex2D, Cellular2D}} copied unchanged into a scratch
 * harness. Only {@code Helpers.diamondAngle} is stubbed (copied verbatim), and {@code Mth} is Minecraft 1.21.11's own class.
 * Nothing was run on a server. Doubles are printed with {@link Double#toString}, which round-trips exactly, so the
 * assertions are exact.
 */
class Cellular2DGoldenTest {
    private static final long[] C_SEEDS = {0L, 0L, 42L, 42L, -7L, -7L, 1234567890123L, 1234567890123L, -9876543210L, -9876543210L};
    private static final double[] C_XS = {3.7, -250.5, 3.7, -250.5, 3.7, -250.5, 3.7, -250.5, 3.7, -250.5};
    private static final double[] C_ZS = {-1.2, 88.25, -1.2, 88.25, -1.2, 88.25, -1.2, 88.25, -1.2, 88.25};
    private static final double[] C_F1 = {0.3257272795851872, 0.05093502946597249, 0.08505520440984657, 0.2366112070700612, 0.06248544159946988, 0.04631092802796571, 0.08797492234723156, 0.0220183863746604, 0.3294172202895556, 0.04192111086651748};
    private static final double[] C_F2 = {0.4330168788728825, 0.5085970289995039, 0.26564262263728017, 0.2862317631758995, 0.6420407572327643, 0.5630883572790231, 0.09587565195274514, 0.02391975709221935, 0.6225979491919363, 0.4319553682189797};
    private static final double[] C_NOISE = {-0.6482076644897461, -0.7306791543960571, -0.4732476472854614, -0.15601234138011932, 0.77459317445755, -0.5977907180786133, -0.4327988028526306, 0.5857627987861633, 0.5997770428657532, 0.8970944881439209};
    private static final double[] C_ANGLE = {1.4313250945732539, 0.27025103034684683, 1.8125718313462844, 0.7142213132044889, 3.4043273020428835, 3.2415408053534556, 1.886017249648742, 3.396812911283713, 2.88875034280135, 2.74393016853851};
    private static final int[] C_CX = {3, -250, 3, -250, 4, -250, 3, -250, 4, -251};
    private static final int[] C_CY = {-1, 88, -1, 89, -1, 88, -1, 88, -2, 88};

    @Test
    void cellFieldsMatchTfc() {
        for (int i = 0; i < C_SEEDS.length; i++) {
            final Cellular2D.Cell cell = new Cellular2D(C_SEEDS[i]).cell(C_XS[i], C_ZS[i]);
            assertThat(cell.f1()).as("f1 %d", i).isEqualTo(C_F1[i]);
            assertThat(cell.f2()).as("f2 %d", i).isEqualTo(C_F2[i]);
            assertThat(cell.noise()).as("noise %d", i).isEqualTo(C_NOISE[i]);
            assertThat(cell.angle()).as("angle %d", i).isEqualTo(C_ANGLE[i]);
            assertThat(cell.cx()).as("cx %d", i).isEqualTo(C_CX[i]);
            assertThat(cell.cy()).as("cy %d", i).isEqualTo(C_CY[i]);
        }
    }
}
