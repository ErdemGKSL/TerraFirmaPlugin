/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-WORLD-58: a waterlogged groundcover decision names the CE state with TFC's fluid=water. */
class WetGroundcoverRefTest {
    @Test
    void waterAddsTheFluidProperty() {
        assertThat(TfcTerrain.withFluid("tfp:rock/loose/granite[count=2]", true)).isEqualTo("tfp:rock/loose/granite[count=2,fluid=water]");
        assertThat(TfcTerrain.withFluid("tfp:groundcover/clam", true)).isEqualTo("tfp:groundcover/clam[fluid=water]");
        assertThat(TfcTerrain.withFluid("tfp:ore/small_hematite", false)).isEqualTo("tfp:ore/small_hematite");
        assertThat(TfcTerrain.withFluid("tfp:rock/loose/granite[count=1]", false)).isEqualTo("tfp:rock/loose/granite[count=1]");
    }
}
