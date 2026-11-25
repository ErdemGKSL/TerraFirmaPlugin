/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (randomTick)
 */
package dev.tfp.paper.plant;

import dev.tfp.core.worldgen.plant.PlantType;
import java.util.EnumSet;
import java.util.Set;

/**
 * The plant block types whose {@code randomTick} is {@code PlantBlock.randomTick} (age grows by {@code plantGrowthChance}) and that exist
 * as CraftEngine blocks: the plants of PAR-WORLD-12 with an age. The tall grasses, shrubs and passable cacti inherit it from
 * {@code PlantBlock} too; the floating plants as well; the types with a different {@code randomTick} (top plants, kelp trees, branching
 * cacti) have no block yet.
 */
final class PlantGrowthTypes {
    static final Set<PlantType> GROWS = EnumSet.of(PlantType.SHORT_GRASS, PlantType.BEACH_GRASS, PlantType.TALL_GRASS, PlantType.TALL_SHRUB,
        PlantType.FLOWERBED, PlantType.CACTUSBED, PlantType.FLOATING, PlantType.FLOATING_FRESH);

    private PlantGrowthTypes() {
    }
}
