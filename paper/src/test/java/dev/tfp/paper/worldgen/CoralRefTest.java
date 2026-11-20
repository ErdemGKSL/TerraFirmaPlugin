/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.plant.PlantState;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-25: the CraftEngine states of the generated coral plants, fans, wall fans and sea pickles. */
class CoralRefTest {
    private static PlantState coral(String id, PlantState.Kind kind, PlantState.Fluid fluid, int extra) {
        return new PlantState(id, kind, null, PlantState.Part.SINGLE, -1, fluid, extra);
    }

    @Test
    void coralStates() {
        assertThat(PlantBlocks.coralRef(coral("tfp:coral/tube_coral", PlantState.Kind.CORAL, PlantState.Fluid.SALT_WATER, -1)))
            .isEqualTo("tfp:coral/tube_coral[waterlogged=true]");
        assertThat(PlantBlocks.coralRef(coral("tfp:coral/fire_coral_fan", PlantState.Kind.CORAL_FAN, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo("tfp:coral/fire_coral_fan[waterlogged=false]");
        final String[] facings = {"north", "east", "south", "west"};
        for (int d = 0; d < 4; d++) {
            assertThat(PlantBlocks.coralRef(coral("tfp:coral/horn_coral_wall_fan", PlantState.Kind.CORAL_WALL_FAN,
                PlantState.Fluid.SALT_WATER, d)))
                .isEqualTo("tfp:coral/horn_coral_wall_fan[facing=" + facings[d] + ",waterlogged=true]");
        }
        assertThat(PlantBlocks.coralRef(coral("tfp:sea_pickle", PlantState.Kind.SEA_PICKLE, PlantState.Fluid.SALT_WATER, 3)))
            .isEqualTo("tfp:sea_pickle[pickles=3,waterlogged=true]");
        assertThat(PlantBlocks.coralRef(coral("minecraft:tube_coral_block", PlantState.Kind.CORAL_BLOCK,
            PlantState.Fluid.EMPTY, -1))).isNull();
    }
}
