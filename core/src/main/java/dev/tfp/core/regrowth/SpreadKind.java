/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/ShortGrassBlock.java @ c3b4d9536 (randomTick: spreadSelf(2, 2, 4)),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCTallGrassBlock.java @ c3b4d9536 (randomTick: spreadSelf(2, 2, 4), placeTwoHalves),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (BlockType of each plant)
 */
package dev.tfp.core.regrowth;

/**
 * How a plant spreads over time: the {@code spreadSelf} arguments of its block class. Only the plants that spread by a random draw within
 * a range are here; creeping and floating water plants have their own rules (follow-up PAR-FARM-21).
 *
 * @param range     the largest offset of a new plant on each axis ({@code selfSpreadRange})
 * @param radius    the square radius checked for plants of the natural-regrowing set ({@code radius})
 * @param maxPlants the plants in that square above which spreading is denied ({@code maxPlants})
 * @param tall      whether the plant has two halves
 */
public record SpreadKind(int range, int radius, int maxPlants, boolean tall) {
    /** {@code ShortGrassBlock}: grasses and beach grass. */
    public static final SpreadKind SHORT = new SpreadKind(2, 2, 4, false);
    /** {@code TFCTallGrassBlock}: two-block grasses, ferns, palms and tall flowers. */
    public static final SpreadKind TALL = new SpreadKind(2, 2, 4, true);
}
