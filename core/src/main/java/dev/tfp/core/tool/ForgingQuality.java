/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgingQuality.java @ c3b4d9536 and ForgingBonusComponent.java
 */
package dev.tfp.core.tool;

import java.util.random.RandomGenerator;

/**
 * The anvil forging bonus of a worked item. Each bonus above {@link #NONE} scales the mining speed, the attack damage and the
 * durability of a tool. The multipliers are linear in the ordinal: the bonus's ordinal times 0.25 is the interpolation
 * step between the neutral value and the {@link ForgingConfig} maximum (TFC {@code Helpers.lerp}).
 *
 * <p>{@link #byRatio} takes the ratio of steps the player took to the optimal steps: a smaller ratio is a better forge.
 */
public enum ForgingQuality {
    NONE,
    MODEST,
    WELL,
    EXPERT,
    PERFECT,
    ;

    private static final ForgingQuality[] VALUES = values();

    /**
     * The bonus for a forge ratio, the highest bonus whose threshold is strictly above the ratio (TFC
     * {@code ForgingQuality.byRatio}). A ratio at or above the modest threshold gives {@link #NONE}.
     *
     * @param ratio  the steps taken divided by the optimal steps
     * @param config the thresholds
     */
    public static ForgingQuality byRatio(float ratio, ForgingConfig config) {
        for (int i = VALUES.length - 1; i > 0; i--) {
            if (VALUES[i].minRatio(config) > ratio) {
                return VALUES[i];
            }
        }
        return NONE;
    }

    /**
     * The largest ratio that still gives this bonus or better, the threshold TFC reads from the config. {@link #NONE} has
     * positive infinity.
     *
     * @param config the thresholds
     */
    public double minRatio(ForgingConfig config) {
        return switch (this) {
            case NONE -> Double.POSITIVE_INFINITY;
            case MODEST -> config.modestThreshold();
            case WELL -> config.wellThreshold();
            case EXPERT -> config.expertThreshold();
            case PERFECT -> config.perfectThreshold();
        };
    }

    /**
     * The mining speed multiplier of this bonus (TFC {@code ForgingQuality.efficiency}).
     *
     * @param config the maximum multipliers
     */
    public float efficiency(ForgingConfig config) {
        return lerp(ordinal() * 0.25f, 1.0f, (float) config.maxEfficiency());
    }

    /**
     * The probability that one point of damage is ignored by this bonus (TFC {@code ForgingQuality.durability}).
     *
     * @param config the maximum multipliers
     */
    public float durability(ForgingConfig config) {
        return lerp(ordinal() * 0.25f, 0f, (float) config.maxDurability());
    }

    /**
     * The attack damage multiplier of this bonus (TFC {@code ForgingQuality.damage}).
     *
     * @param config the maximum multipliers
     */
    public float damage(ForgingConfig config) {
        return lerp(ordinal() * 0.25f, 1.0f, (float) config.maxDamage());
    }

    /**
     * The damage an item takes after the bonus has ignored some points, as TFC {@code ForgingBonusComponent.applyLikeUnbreaking}.
     * Each point of damage is ignored with the bonus's durability probability. The loop bound is the damage that is still
     * counted, so a successful ignore also shortens the loop, as in TFC.
     *
     * @param damage the original damage
     * @param config the maximum multipliers
     * @param random the random source, one draw per counted point
     */
    public int applyLikeUnbreaking(int damage, ForgingConfig config, RandomGenerator random) {
        int result = damage;
        if (this != NONE) {
            float chance = durability(config);
            for (int i = 0; i < result; i++) {
                if (random.nextFloat() < chance) {
                    result--;
                }
            }
        }
        return result;
    }

    private static float lerp(float delta, float min, float max) {
        return min + (max - min) * delta;
    }
}
