/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/IPickableCrop.java @ c3b4d9536 (yieldItemStack, the pick branches),
 *       src/main/java/net/dries007/tfc/util/loot/CropYieldProvider.java @ c3b4d9536 (getFloat)
 */
package dev.tfp.core.crop;

/**
 * Harvest rules: which fruit a pick gives at a stage, the item count from the yield, the growth a crop regrows to after a pick,
 * and the loot number lerp. Pure: the random draws are inputs ({@code roll} is 0 or 1, {@code unit} is in [0, 1)).
 */
public final class CropHarvest {

    /** Which fruit a pick gives. */
    public enum Fruit {
        /** The first fruit, picked one stage before the top. */
        FIRST,
        /** The second fruit, picked at full growth. */
        SECOND,
        /** Nothing to pick at this age. */
        NONE
    }

    private CropHarvest() {
    }

    /**
     * TFC's pick branches: at {@code maxAge - 1} the first fruit (only when the crop has one), at {@code maxAge} the second.
     *
     * @param age        the current age
     * @param maxAge     the crop's highest age
     * @param hasFirst   whether the crop has a first fruit (TFC's nullable {@code getFirstFruit})
     */
    public static Fruit fruit(int age, int maxAge, boolean hasFirst) {
        if (age == maxAge - 1 && hasFirst) {
            return Fruit.FIRST;
        }
        if (age == maxAge) {
            return Fruit.SECOND;
        }
        return Fruit.NONE;
    }

    /**
     * The growth a pick leaves: TFC's {@code Mth.nextFloat(random, min, max)} with the draw {@code unit}. First fruit regrows to
     * [0.4, 0.5], second to [0.5, 0.6]. The yield is reset to 0 by the caller.
     */
    public static float regrowGrowth(Fruit fruit, float unit) {
        float min = fruit == Fruit.FIRST ? 0.4f : 0.5f;
        float max = fruit == Fruit.FIRST ? 0.5f : 0.6f;
        return unit * (max - min) + min;
    }

    /** TFC's {@code yieldItemStack} count: {@code floor(lerp(yield, 1, 5) + roll)}, with {@code roll} 0 or 1. */
    public static int pickCount(float yield, int roll) {
        return (int) Math.floor(lerp(yield, 1f, 5f) + roll);
    }

    /** TFC's {@code CropYieldProvider}: a loot number lerped from {@code min} to {@code max} by the yield. */
    public static float lootYield(float yield, float min, float max) {
        return lerp(yield, min, max);
    }

    /** {@code Mth.lerp}: {@code start + delta * (end - start)}, in float32. */
    static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }
}
