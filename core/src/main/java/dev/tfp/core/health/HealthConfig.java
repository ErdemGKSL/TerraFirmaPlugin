/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (health and regeneration settings; PlayerInfo MAX_HUNGER, MAX_THIRST, PASSIVE_HEALING_PER_TEN_TICKS)
 */
package dev.tfp.core.health;

/**
 * The health settings with TFC's defaults.
 *
 * @param vanillaMaxHealth         the player's vanilla max health attribute (20). TFC does not change it at c3b4d9536,
 *                                 so the displayed TFC health is this times the units below
 * @param displayUnitsPerHp        TFC's {@code HealthDisplayStyle.TFC} multiplier (50): one vanilla HP shows as 50
 * @param regenIntervalTicks      the regeneration step: every 10 ticks ({@code tickCount % 10 == 0})
 * @param regenPerStep            {@code PASSIVE_HEALING_PER_TEN_TICKS} (20 x 0.0002): vanilla HP healed per step at
 *                                multiplier 1
 * @param naturalRegenerationModifier the server setting {@code naturalRegenerationModifier} (1)
 * @param minFood                 {@code food >= 4} needed to regenerate; also the lower end of the food bonus
 * @param maxHunger               {@code PlayerInfo.MAX_HUNGER} (20): the upper end of the food bonus
 * @param minThirst               {@code thirst > 20} needed to regenerate; also the lower end of the thirst bonus
 * @param maxThirst               {@code PlayerInfo.MAX_THIRST} (100): the upper end of the thirst bonus
 */
public record HealthConfig(float vanillaMaxHealth, float displayUnitsPerHp, int regenIntervalTicks, float regenPerStep,
        float naturalRegenerationModifier, float minFood, float maxHunger, float minThirst, float maxThirst) {
    public static final HealthConfig DEFAULT = new HealthConfig(
            20f, 50f, 10, 20 * 0.0002f, 1f, 4f, 20f, 20f, 100f);

    public HealthConfig {
        if (!(vanillaMaxHealth > 0) || Float.isInfinite(vanillaMaxHealth)) {
            throw new IllegalArgumentException("vanillaMaxHealth must be positive and finite: " + vanillaMaxHealth);
        }
        if (!(displayUnitsPerHp > 0) || Float.isInfinite(displayUnitsPerHp)) {
            throw new IllegalArgumentException("displayUnitsPerHp must be positive and finite: " + displayUnitsPerHp);
        }
        if (regenIntervalTicks <= 0) {
            throw new IllegalArgumentException("regenIntervalTicks must be positive: " + regenIntervalTicks);
        }
        if (!(maxHunger > minFood) || !(maxThirst > minThirst)) {
            throw new IllegalArgumentException("maxHunger must exceed minFood and maxThirst must exceed minThirst");
        }
        if (!(regenPerStep >= 0) || !(naturalRegenerationModifier >= 0)) {
            throw new IllegalArgumentException("regeneration rates must be >= 0");
        }
    }
}
