/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/IFarmland.java @ c3b4d9536 (addNutrient, addNutrients, consumeNutrients,
 *       produceNutrients, isMaxedOut),
 *       src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (setNutrientWithoutSync, addNutrients)
 */
package dev.tfp.core.farm;

/**
 * The nitrogen, phosphorus and potassium of one farmland block, each a fraction in {@code [0, 1]}. Immutable: every rule
 * returns the new value. Each setter clamps to {@code [0, 1]} exactly as TFC's {@code setNutrientWithoutSync} does.
 *
 * @param nitrogen   the nitrogen fraction
 * @param phosphorus the phosphorus fraction
 * @param potassium  the potassium fraction
 */
public record Nutrients(float nitrogen, float phosphorus, float potassium) {
    /** A new farmland: every nutrient at zero. */
    public static final Nutrients EMPTY = new Nutrients(0f, 0f, 0f);

    public Nutrients {
        checkUnit(nitrogen);
        checkUnit(phosphorus);
        checkUnit(potassium);
    }

    private static void checkUnit(float value) {
        if (!(value >= 0f && value <= 1f)) {
            throw new IllegalArgumentException("nutrient must be in [0, 1]: " + value);
        }
    }

    /** {@code Mth.clamp(value, 0, 1)}, the clamp every TFC nutrient write goes through. */
    static float clampUnit(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    /** The amount of {@code type}. */
    public float get(Nutrient type) {
        return switch (type) {
            case NITROGEN -> nitrogen;
            case PHOSPHOROUS -> phosphorus;
            case POTASSIUM -> potassium;
        };
    }

    /** {@code setNutrient}: this with {@code type} set to {@code value}, clamped to {@code [0, 1]}. */
    public Nutrients with(Nutrient type, float value) {
        float clamped = clampUnit(value);
        return switch (type) {
            case NITROGEN -> new Nutrients(clamped, phosphorus, potassium);
            case PHOSPHOROUS -> new Nutrients(nitrogen, clamped, potassium);
            case POTASSIUM -> new Nutrients(nitrogen, phosphorus, clamped);
        };
    }

    /**
     * TFC {@code consumeNutrients}: takes up to {@code amount} of {@code type}. A non-positive amount takes nothing.
     *
     * @return the new nutrients and the amount actually taken
     */
    public Taken consume(Nutrient type, float amount) {
        if (amount <= 0) {
            return new Taken(this, 0f);
        }
        float startValue = get(type);
        float consumed = Math.min(startValue, amount);
        return new Taken(with(type, startValue - consumed), consumed);
    }

    /**
     * TFC {@code produceNutrients}: a non-positive {@code amount} (a nutrient-producing crop) adds back a share of its
     * magnitude scaled by {@code growthDelta} and by the fraction of the other nutrients the crop satisfied. A positive amount
     * changes nothing.
     */
    public Nutrients produce(Nutrient type, float amount, float percentOtherNutrientsConsumed, float growthDelta) {
        if (amount <= 0) {
            float added = -(0.3f + 0.7f * percentOtherNutrientsConsumed) * amount * growthDelta;
            return with(type, get(type) + added);
        }
        return this;
    }

    /**
     * TFC {@code addNutrients(Fertilizer, multiplier)}: adds {@code multiplier} times each fertilizer component, each clamped.
     */
    public Nutrients addFertilizer(float fertNitrogen, float fertPhosphorus, float fertPotassium, float multiplier) {
        return new Nutrients(
            clampUnit(nitrogen + (fertNitrogen * multiplier)),
            clampUnit(phosphorus + (fertPhosphorus * multiplier)),
            clampUnit(potassium + (fertPotassium * multiplier)));
    }

    /** {@code isMaxedOut}: all three nutrients at 1. */
    public boolean isMaxedOut() {
        return nitrogen == 1f && phosphorus == 1f && potassium == 1f;
    }

    /**
     * The result of {@link #consume}.
     *
     * @param after the nutrients after the take
     * @param taken the amount taken
     */
    public record Taken(Nutrients after, float taken) {
    }
}
