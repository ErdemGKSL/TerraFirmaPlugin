/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (tick: apply custom TFC regeneration)
 */
package dev.tfp.core.health;

/**
 * TFC's passive regeneration rule ({@code PlayerInfo.tick}, the "custom TFC regeneration" block). Every
 * {@link HealthConfig#regenIntervalTicks()} ticks, a hurt player with food at least {@code minFood} and thirst above
 * {@code minThirst} heals {@code multiplier x regenPerStep x naturalRegenerationModifier}, where the multiplier is
 * {@code 1 + inverseLerp(food, 4, 20) + inverseLerp(thirst, 20, 100)}, from 1 to 3.
 */
public final class HealthRegen {
    private HealthRegen() {}

    /** Whether food and thirst allow passive regeneration at all (TFC's gate, before the hurt check). */
    public static boolean canRegenerate(float food, float thirst, HealthConfig config) {
        return food >= config.minFood() && thirst > config.minThirst();
    }

    /** The regeneration multiplier from food and thirst, in {@code [1, 3]} for values in range. */
    public static float multiplier(float food, float thirst, HealthConfig config) {
        float foodBonus = inverseLerp(food, config.minFood(), config.maxHunger());
        float thirstBonus = inverseLerp(thirst, config.minThirst(), config.maxThirst());
        return 1 + foodBonus + thirstBonus;
    }

    /** The vanilla HP one regeneration step heals, before the max-health cap. */
    public static float healPerStep(float food, float thirst, HealthConfig config) {
        return multiplier(food, thirst, config) * config.regenPerStep() * config.naturalRegenerationModifier();
    }

    private static float inverseLerp(float delta, float start, float end) {
        return (delta - start) / (end - start);
    }
}
