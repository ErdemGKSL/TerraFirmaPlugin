/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgingBonus.java @ c3b4d9536 (byRatio, efficiency, durability, damage)
 */
package dev.tfp.core.forge;

/**
 * The forging quality tiers and the multipliers each tier gives. Pure and thread-safe.
 *
 * <p>TFC reads the tier thresholds and the maximum multipliers from its server config. Here they are parameters, so the caller
 * supplies the configured values. Each tier scales linearly from no bonus at {@link #NONE} to the configured maximum at
 * {@link #PERFECT}.
 *
 * <p>Display names are UI and are not ported.
 */
public enum ForgingBonus {
    NONE,
    MODEST,
    WELL,
    EXPERT,
    PERFECT;

    private static final ForgingBonus[] VALUES = values();

    /**
     * @param ratio      the forging ratio to classify
     * @param minRatios  the minimum ratio for each tier, indexed by {@link #ordinal()}. The entry for {@link #NONE} is ignored, since
     *                   no ratio reaches it. Must have one entry per tier.
     * @return the highest tier whose minimum ratio is strictly above {@code ratio}, or {@link #NONE}, as TFC {@code byRatio}
     */
    public static ForgingBonus byRatio(float ratio, double[] minRatios) {
        if (minRatios.length != VALUES.length) {
            throw new IllegalArgumentException("Expected " + VALUES.length + " thresholds, got " + minRatios.length);
        }
        for (int i = VALUES.length - 1; i > 0; i--) {
            if (minRatios[i] > ratio) {
                return VALUES[i];
            }
        }
        return NONE;
    }

    /**
     * @param maxMultiplier the configured efficiency multiplier at {@link #PERFECT}
     * @return the efficiency multiplier for this tier, from 1.0 up to {@code maxMultiplier}
     */
    public float efficiency(float maxMultiplier) {
        return lerp(ordinal() * 0.25f, 1.0f, maxMultiplier);
    }

    /**
     * @param maxMultiplier the configured durability multiplier at {@link #PERFECT}
     * @return the durability multiplier for this tier, from 0.0 up to {@code maxMultiplier}
     */
    public float durability(float maxMultiplier) {
        return lerp(ordinal() * 0.25f, 0f, maxMultiplier);
    }

    /**
     * @param maxMultiplier the configured damage multiplier at {@link #PERFECT}
     * @return the damage multiplier for this tier, from 1.0 up to {@code maxMultiplier}
     */
    public float damage(float maxMultiplier) {
        return lerp(ordinal() * 0.25f, 1.0f, maxMultiplier);
    }

    private static float lerp(float delta, float min, float max) {
        return min + (max - min) * delta;
    }
}
