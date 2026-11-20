/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{WildCropBlock,WildSpreadingCropBlock,SpreadingCropBlock,TFCPumpkinBlock}.java @ c3b4d9536
 *       (isMature, the sides, getGrowthLimit, postGrowthTick), src/main/java/net/dries007/tfc/common/blockentities/DecayingBlockEntity.java @ c3b4d9536
 *       (isRotten), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (wildCropMaturityStartMonth, wildCropMaturityEndMonth)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodTrait;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** The pure rules of the wild crops, the spreading crops' fruit and the rot of a fruit block. No Bukkit types. */
public final class WildRules {
    /** TFC config default {@code wildCropMaturityStartMonth}. */
    public static final Month MATURE_FROM = Month.JUNE;
    /** TFC config default {@code wildCropMaturityEndMonth}. */
    public static final Month MATURE_TO = Month.OCTOBER;
    /** More than this many fruit blocks around a spreading crop cap its growth limit. */
    public static final int FRUIT_AROUND_LIMIT = 2;
    public static final float CROWDED_GROWTH_LIMIT = 0.9f;
    public static final float REGROWTH_MIN = 0.6f;
    public static final float REGROWTH_MAX = 0.72f;

    private WildRules() {
    }

    /** TFC {@code WildCropBlock.isMature}: the month (already hemispheric) lies in {@code [start, end]}, wrapping over the new year. */
    public static boolean isMature(Month month, Month start, Month end) {
        int m = month.ordinal();
        if (start.ordinal() <= end.ordinal()) {
            return m >= start.ordinal() && m <= end.ordinal();
        }
        return m >= start.ordinal() || m <= end.ordinal();
    }

    /** {@link #isMature(Month, Month, Month)} with TFC's default window (June to October). */
    public static boolean isMature(Month month) {
        return isMature(month, MATURE_FROM, MATURE_TO);
    }

    /** TFC {@code SpreadingCropBlock.getGrowthLimit}: more than two fruit blocks around cap the limit at 0.9, else the base limit. */
    public static float growthLimit(int fruitAround, float baseLimit) {
        return fruitAround > FRUIT_AROUND_LIMIT ? CROWDED_GROWTH_LIMIT : baseLimit;
    }

    /** What a grown spreading crop does: nothing, or places a fruit on {@code side} and falls back to {@code growth}. */
    public record Spread(boolean place, Side side, float growth) {
        public static final Spread NONE = new Spread(false, null, 0f);
    }

    /**
     * TFC {@code SpreadingCropBlock.postGrowthTick}: a crop with growth at 1 draws a horizontal side (one {@code nextInt(4)}, order north,
     * east, south, west); when the ground there takes fruit and the cell is replaceable, the fruit is placed and the crop's growth falls to
     * {@code nextFloat(0.6, 0.72)} (a second draw, only then). Below growth 1 nothing is drawn.
     */
    public static Spread spread(float growth, RandomGenerator random, Predicate<Side> canPlace) {
        if (growth < 1f) {
            return Spread.NONE;
        }
        Side side = Side.values()[random.nextInt(4)];
        if (!canPlace.test(side)) {
            return Spread.NONE;
        }
        return new Spread(true, side, REGROWTH_MIN + random.nextFloat() * (REGROWTH_MAX - REGROWTH_MIN));
    }

    /**
     * TFC {@code DecayingBlockEntity.isRotten} for a fruit block whose stored food was created at {@code creationDate}: the food rule with
     * the item's decay modifier and the wild trait of a worldgen fruit. A rotten flag is rotten for good.
     */
    public static boolean rotten(long creationDate, boolean wild, float foodDecayModifier, float configModifier, long now) {
        List<FoodTrait> traits = wild ? List.of(FoodTrait.WILD) : List.of();
        return FoodDecay.isRotten(creationDate, FoodDecay.decayDateModifier(foodDecayModifier, configModifier, traits), now);
    }

    /** The first tick at which the fruit is rotten, or {@link Long#MAX_VALUE} when it never rots. */
    public static long rottenAt(long creationDate, boolean wild, float foodDecayModifier, float configModifier) {
        if (FoodDecay.isNeverDecayFlag(creationDate)) {
            return Long.MAX_VALUE;
        }
        if (creationDate == FoodDecay.ROTTEN_FLAG) {
            return Long.MIN_VALUE;
        }
        List<FoodTrait> traits = wild ? List.of(FoodTrait.WILD) : List.of();
        float mod = FoodDecay.decayDateModifier(foodDecayModifier, configModifier, traits);
        if (mod == Float.POSITIVE_INFINITY) {
            return Long.MAX_VALUE;
        }
        return FoodDecay.rottenDate(creationDate, mod);
    }
}
