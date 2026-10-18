/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (nutrition and exhaustion settings)
 */
package dev.tfp.core.food;

/**
 * The server settings nutrition and hunger depend on, with TFC's defaults ({@code ServerConfig}; TFC reads
 * the {@code Double} values with {@code floatValue()}, so they are floats here).
 *
 * @param hungerWindow              {@code nutritionRotationHungerWindow}: total hunger of eaten food averaged (80)
 * @param defaultNutrition          the starting value of grain, fruit, vegetables and protein (TFC passes 0.5)
 * @param defaultDairy              the starting value of dairy (TFC passes 0)
 * @param minHealthModifier         {@code nutritionMinimumHealthModifier} (0.2)
 * @param defaultHealthModifier     {@code nutritionDefaultHealthModifier} (0.85), at an average of 0.4
 * @param maxHealthModifier         {@code nutritionMaximumHealthModifier} (3.0), reached at an average of 0.95
 * @param passiveExhaustionModifier {@code passiveExhaustionMultiplier} (1 = a full hunger bar every 2.5 days; 0 = off)
 */
public record NutritionConfig(int hungerWindow, float defaultNutrition, float defaultDairy, float minHealthModifier,
        float defaultHealthModifier, float maxHealthModifier, float passiveExhaustionModifier) {
    public static final NutritionConfig DEFAULT = new NutritionConfig(80, 0.5f, 0f, 0.2f, 0.85f, 3.0f, 1f);

    public NutritionConfig {
        if (hungerWindow < 1) {
            throw new IllegalArgumentException("hungerWindow < 1: " + hungerWindow);
        }
        if (!(passiveExhaustionModifier >= 0) || passiveExhaustionModifier > 100) {
            throw new IllegalArgumentException("passiveExhaustionModifier must be in [0, 100]: " + passiveExhaustionModifier);
        }
    }
}
