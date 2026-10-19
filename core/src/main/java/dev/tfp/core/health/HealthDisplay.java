/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/HealthDisplayStyle.java @ c3b4d9536 (TFC formatter, shown health x 50)
 */
package dev.tfp.core.health;

import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionLevels;

/**
 * The health and max health TFC shows for a player: {@link HealthScale#shownHealth} of the vanilla values at one
 * health modifier. The shown health is capped at the shown max, so a vanilla HP above the vanilla max never shows
 * more than the bar.
 *
 * @param health    the shown health, 0..{@code maxHealth}
 * @param maxHealth the shown max health (850 at average nutrition with TFC's defaults: 20 x 0.85 x 50)
 */
public record HealthDisplay(float health, float maxHealth) {
    public HealthDisplay {
        if (!(health >= 0) || Float.isInfinite(health) || !(maxHealth > 0) || Float.isInfinite(maxHealth)) {
            throw new IllegalArgumentException("shown health must be >= 0 and the max positive: " + health + " / " + maxHealth);
        }
    }

    /**
     * The shown values of a player with vanilla {@code vanillaHealth} out of {@code vanillaMaxHealth} at
     * {@code healthModifier}.
     *
     * @throws IllegalArgumentException if the vanilla max is not positive or a value is not finite
     */
    public static HealthDisplay of(float vanillaHealth, float vanillaMaxHealth, float healthModifier, HealthConfig config) {
        if (!(vanillaMaxHealth > 0) || Float.isInfinite(vanillaMaxHealth) || !(vanillaHealth >= 0) || Float.isInfinite(vanillaHealth)) {
            throw new IllegalArgumentException("vanilla health must be >= 0 and the max positive: " + vanillaHealth + " / " + vanillaMaxHealth);
        }
        float max = HealthScale.shownHealth(vanillaMaxHealth, healthModifier, config);
        float shown = Math.min(HealthScale.shownHealth(vanillaHealth, healthModifier, config), max);
        return new HealthDisplay(shown, max);
    }

    /**
     * {@link #of} at the modifier of TFC's average nutrition ({@link NutritionLevels#DEFAULT_AVERAGE_NUTRITION}) with
     * TFC's nutrition defaults. The fallback for a player with no stored nutrition data (the publish path uses the stored data).
     */
    public static HealthDisplay atAverageNutrition(float vanillaHealth, float vanillaMaxHealth, HealthConfig config) {
        float modifier = NutritionLevels.healthModifier(NutritionLevels.DEFAULT_AVERAGE_NUTRITION, NutritionConfig.DEFAULT);
        return of(vanillaHealth, vanillaMaxHealth, modifier, config);
    }
}
