/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/CreepingPlantBlock.java @ c3b4d9536 (create, createStone)
 */
package dev.tfp.core.regrowth;

/** Which ground a creeping plant creeps on: {@code CreepingPlantBlock.create} or {@code createStone}. */
public enum CreepKind {
    /** Any sturdy face except {@code tfc:creeping_plant_not_plantable_on} (ice, snow). */
    NORMAL,
    /** Only {@code tfc:creeping_stone_plantable_on} (stone, cobblestone, sandstone). */
    STONE
}
