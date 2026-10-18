/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/DefaultCropBlock.java @ c3b4d9536 (postGrowthTick),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/DoubleCropBlock.java @ c3b4d9536 (postGrowthTick, maxSingleAge)
 */
package dev.tfp.core.crop;

/**
 * The growth stages of a crop and the age a block shows for a growth value. Pure.
 *
 * @param maxAge       the highest age, the fully grown stage
 * @param maxSingleAge the highest age the bottom block shows; equal to {@code maxAge} for a single block
 */
public record CropStages(int maxAge, int maxSingleAge) {

    /** The age shown and whether it is the top block of a two-block crop. */
    public record Stage(int age, boolean top) {
    }

    /** A single-block crop with {@code stages} stages. */
    public static CropStages single(int stages) {
        return new CropStages(stages - 1, stages - 1);
    }

    /** A two-block crop with {@code bottom} and {@code top} stages. */
    public static CropStages doubled(int bottom, int top) {
        return new CropStages(bottom + top - 1, bottom - 1);
    }

    /** True for a two-block crop, whose top block appears above {@code maxSingleAge}. */
    public boolean doubled() {
        return maxSingleAge < maxAge;
    }

    /**
     * TFC's {@code postGrowthTick} age. A single block shows {@code maxAge} at full growth and {@code (int) (growth * maxAge)}
     * before it. A two-block crop clamps that to {@code [0, maxAge]} and puts the top block on the ages above {@code maxSingleAge}.
     */
    public Stage stage(float growth) {
        if (!doubled()) {
            return new Stage(growth == 1 ? maxAge : (int) (growth * maxAge), false);
        }
        int age = Math.max(0, Math.min(maxAge, (int) (growth * maxAge)));
        return new Stage(age, age > maxSingleAge);
    }
}
