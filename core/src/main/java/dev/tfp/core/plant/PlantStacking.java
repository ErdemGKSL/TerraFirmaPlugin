/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (createFlowerbed: canBeReplaced, getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java @ c3b4d9536 (canBeReplaced, getStateForPlacement)
 */
package dev.tfp.core.plant;

import dev.tfp.core.worldgen.plant.PlantType;
import java.util.OptionalInt;

/** Placing another plant item onto a flowerbed or cactus bed raises its {@code age} (more petals), pure. */
public final class PlantStacking {
    /** {@code Plant.getMaxAgeForType}: 3 for the types with an age. */
    public static final int MAX_AGE = 3;

    private PlantStacking() {
    }

    /** Whether blocks of {@code type} stack. */
    public static boolean stacks(PlantType type) {
        return type == PlantType.FLOWERBED || type == PlantType.CACTUSBED;
    }

    /**
     * {@code canBeReplaced} with the same item in hand, then {@code getStateForPlacement}: the new age, or empty when the click is not a
     * stack (a sneaking player, another item, a full bed): then the placement is the ordinary one.
     */
    public static OptionalInt stackedAge(PlantType type, int age, boolean sneaking, boolean sameItem) {
        if (!stacks(type) || sneaking || !sameItem || age >= MAX_AGE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.min(MAX_AGE, age + 1));
    }
}
