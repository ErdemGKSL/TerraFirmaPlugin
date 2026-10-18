/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/HealthDisplayStyle.java @ c3b4d9536 (TFC formatter; ForgeEventHandler.onLivingHurt; IngameOverlays.renderHealthBar)
 */
package dev.tfp.core.health;

/**
 * TFC's health numbers. The health bar shows {@code vanilla HP x healthModifier x 50}; incoming damage is scaled by the
 * physical multiplier and then divided by the health modifier (the player's nutrition), so a well-fed player takes less.
 */
public final class HealthScale {
    private HealthScale() {}

    /** The shown health of {@code hp} at {@code healthModifier}: TFC {@code HealthDisplayStyle.TFC} ({@code curr x 50}). */
    public static float shownHealth(float hp, float healthModifier, HealthConfig config) {
        return (hp * healthModifier) * config.displayUnitsPerHp();
    }

    /** The shown max health at {@code healthModifier}. */
    public static float shownMaxHealth(float healthModifier, HealthConfig config) {
        return shownHealth(config.vanillaMaxHealth(), healthModifier, config);
    }

    /**
     * The vanilla damage a player takes from a hit of {@code amount}, after the physical multiplier and the health
     * modifier. TFC skips the scaling for {@link Float#MAX_VALUE} (the vanilla kill command), so that is returned as is.
     * The forging bonus of the attacker's weapon is not part of this rule (it belongs to the forging feature).
     */
    public static float playerDamage(float amount, float physicalMultiplier, float healthModifier) {
        if (amount == Float.MAX_VALUE) {
            return amount;
        }
        float scaled = amount * physicalMultiplier;
        return scaled / healthModifier;
    }
}
