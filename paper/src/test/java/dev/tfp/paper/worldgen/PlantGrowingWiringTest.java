/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** PAR-FARM-29: the growing shapes' worldgen wiring: placeholders for the written types and the CraftEngine states. */
class PlantGrowingWiringTest {
    private static PlantState plant(String name, PlantType type, int age, PlantState.Fluid fluid, int extra) {
        return new PlantState("tfp:plant/" + name, PlantState.Kind.PLANT, type, PlantState.Part.SINGLE, age, fluid, extra);
    }

    @Test
    void everyWrittenGrowingTypeHasABlockPlaceholder() {
        for (PlantType type : PlantResources.WRITTEN_TYPES) {
            // PAR-WORLD-46: every type is written now; the face shapes go only as CraftEngine blocks (no placeholder)
            if (!PlantBlocks.GROWING_TYPES.contains(type) || java.util.Set.of(PlantType.VINE, PlantType.CREEPING,
                    PlantType.CREEPING_STONE, PlantType.EPIPHYTE).contains(type)) {
                continue;
            }
            Material m = PlantBlocks.placeholder(plant("x", type, 0, type.fluidProperty() == null ? PlantState.Fluid.EMPTY
                : type.fluidProperty(), -1));
            assertThat(m).as("%s", type).isNotNull();
            assertThat(m.isBlock()).isTrue();
        }
    }

    @Test
    void theShapesThatNeedFacesAreNotWrittenAsPlaceholders() {
        for (PlantType type : new PlantType[] {PlantType.VINE, PlantType.CREEPING, PlantType.CREEPING_STONE, PlantType.EPIPHYTE}) {
            // PAR-WORLD-46: written, but only as CraftEngine blocks (PlantBlocks.write skips a null placeholder)
            assertThat(PlantBlocks.placeholder(plant("x", type, -1, PlantState.Fluid.EMPTY, -1))).isNull();
        }
    }

    @Test
    void theCraftEngineStatesCarryTheDrawnAgesFacesAndFluids() {
        assertThat(PlantBlocks.ceRef(plant("hanging_vines", PlantType.WEEPING_TOP, 21, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo("tfp:plant/hanging_vines[age=21]");
        assertThat(PlantBlocks.ceRef(plant("hanging_vines_plant", PlantType.WEEPING, -1, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo("tfp:plant/hanging_vines_plant");
        assertThat(PlantBlocks.ceRef(plant("winged_kelp", PlantType.KELP_TOP, 18, PlantState.Fluid.SALT_WATER, -1)))
            .isEqualTo("tfp:plant/winged_kelp[age=18,fluid=salt_water]");
        // north (index 2) and up (index 1)
        assertThat(PlantBlocks.ceRef(plant("jungle_vines", PlantType.VINE, -1, PlantState.Fluid.EMPTY, (1 << 2) | (1 << 1))))
            .isEqualTo("tfp:plant/jungle_vines[up=true,north=false,south=false,west=false,east=false]".replace("north=false", "north=true"));
        assertThat(PlantBlocks.ceRef(plant("giant_kelp_flower", PlantType.KELP_TREE_FLOWER, 5, PlantState.Fluid.SALT_WATER, 1)))
            .isEqualTo("tfp:plant/giant_kelp_flower[age=5,facing=up,fluid=salt_water]");
        assertThat(PlantBlocks.ceRef(plant("saguaro", PlantType.BRANCHING_CACTUS_TOP, -1, PlantState.Fluid.EMPTY, (1 << 6) | 1)))
            .isEqualTo("tfp:plant/saguaro[down=true,up=false,north=false,south=false,west=false,east=false,grows_branches=true]");
        assertThat(PlantBlocks.ceRef(plant("guzmania", PlantType.EPIPHYTE, -1, PlantState.Fluid.EMPTY, 4)))
            .isEqualTo("tfp:plant/guzmania[facing=west]");
        assertThat(PlantBlocks.ceRef(plant("moss", PlantType.CREEPING, -1, PlantState.Fluid.EMPTY, 1)))
            .startsWith("tfp:plant/moss[down=true,up=false");
        assertThat(PlantBlocks.ceRef(plant("bird_nest_fern", PlantType.PERCHED_EPIPHYTE, -1, PlantState.Fluid.EMPTY, -1)))
            .isEqualTo("tfp:plant/bird_nest_fern");
    }
}
