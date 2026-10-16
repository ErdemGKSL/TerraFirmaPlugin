/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.hooks;

/** Heat queries used by {@code tfc:heat}. Implemented by the heat system (M3). */
public interface HeatHooks<S> {
    /** TFC {@code HeatCapability.getTemperature}: current temperature in °C, 0 for items without heat. */
    float temperature(S stack);
}
