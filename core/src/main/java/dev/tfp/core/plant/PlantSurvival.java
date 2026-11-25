/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCBushBlock.java @ c3b4d9536 (canSurvive, mayPlaceOn),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (createDry, createPerchedEpiphyte, createCactusFlower),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java @ c3b4d9536 (mayPlaceOn, createBeachGrass),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (canSurvive, the upper half),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCPassableCactusBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/FloatingWaterPlantBlock.java @ c3b4d9536 (canSurvive)
 */
package dev.tfp.core.plant;

import dev.tfp.core.worldgen.plant.PlantState;
import dev.tfp.core.worldgen.plant.PlantType;
import java.util.List;
import java.util.function.Predicate;

/**
 * Whether a placed TFC plant survives (PAR-FARM-12), pure: the {@code canSurvive} of each block class that only looks at the block below
 * (and, for the tall cacti, at the four sides). The platform answers the questions about the neighbours with a {@link Below} and the
 * {@code solidSides} flag. Worldgen has its own, narrower version for the terrain blocks it places ({@code PlantType.survives}).
 */
public final class PlantSurvival {
    public static final String BUSH_PLANTABLE_ON = "tfc:bush_plantable_on";
    public static final String GRASS_PLANTABLE_ON = "tfc:grass_plantable_on";
    public static final String DRY_PLANTABLE_ON = "tfc:dry_plant_plantable_on";
    public static final String EPIPHYTE_PLANTABLE_ON = "tfc:epiphyte_plantable_on";
    /** {@code ShortGrassBlock.createBeachGrass}: {@code BlockTags.SAND} or {@code Tags.Blocks.SANDS}. */
    public static final List<String> SANDS = List.of("minecraft:sand", "c:sands");

    private PlantSurvival() {
    }

    /**
     * The block below a plant, as the platform reads it.
     *
     * @param tags           whether the block is in a block tag ({@code tfc:bush_plantable_on})
     * @param sameBlockLower whether it is the lower half of the plant's own block (the upper half of a tall plant needs it)
     * @param branchingCactus whether it is a branching cactus block (the cactus flower sits on it)
     * @param fluid          the fluid of the block below (floating plants)
     */
    public record Below(Predicate<String> tags, boolean sameBlockLower, boolean branchingCactus, PlantState.Fluid fluid) {
        public static Below of(Predicate<String> tags) {
            return new Below(tags, false, false, PlantState.Fluid.EMPTY);
        }
    }

    /** Whether the rule of {@code type} is ported here (the others are never removed by this class). */
    public static boolean ported(PlantType type) {
        return switch (type) {
            case STANDARD, SHORT_SHRUB, SHRUB, FLOWERBED, CACTUSBED, CACTUS_FLOWER, CACTUS, PASSABLE_CACTUS, DRY, PERCHED_EPIPHYTE,
                 SHORT_GRASS, BEACH_GRASS, TALL_GRASS, TALL_SHRUB, FLOATING, FLOATING_FRESH -> true;
            default -> false;
        };
    }

    /** The tags one of which the block below must be in, for the lower (or only) block of {@code type}; empty for other rules. */
    public static List<String> supportTags(PlantType type) {
        return switch (type) {
            case STANDARD, SHORT_SHRUB, SHRUB, FLOWERBED -> List.of(BUSH_PLANTABLE_ON);
            // TFCTallGrassBlock (and TFCTallShrubBlock) extend ShortGrassBlock: mayPlaceOn is grass_plantable_on
            case SHORT_GRASS, TALL_GRASS, TALL_SHRUB -> List.of(GRASS_PLANTABLE_ON);
            case BEACH_GRASS -> SANDS;
            case DRY, CACTUSBED, CACTUS, PASSABLE_CACTUS -> List.of(DRY_PLANTABLE_ON);
            case PERCHED_EPIPHYTE -> List.of(EPIPHYTE_PLANTABLE_ON);
            default -> List.of();
        };
    }

    /**
     * {@code canSurvive}.
     *
     * @param upper      the upper half of a tall plant
     * @param solidSides whether a horizontal neighbour is solid or lava-like (only {@code CACTUS} looks at it; true denies)
     */
    public static boolean canSurvive(PlantType type, boolean upper, Below below, boolean solidSides) {
        if (!ported(type)) {
            return true;
        }
        if (upper && type.isTall()) {
            // the tall blocks: the upper half stands on the lower half of the same block (TFCTallGrassBlock.canSurvive)
            if (type == PlantType.CACTUS && solidSides) {
                return false;
            }
            return below.sameBlockLower();
        }
        return switch (type) {
            case FLOATING -> below.fluid() == PlantState.Fluid.SALT_WATER;
            case FLOATING_FRESH -> below.fluid() == PlantState.Fluid.WATER;
            case CACTUS_FLOWER -> below.branchingCactus();
            case CACTUS -> !solidSides && below.tags().test(DRY_PLANTABLE_ON);
            default -> supportTags(type).stream().anyMatch(below.tags());
        };
    }
}
