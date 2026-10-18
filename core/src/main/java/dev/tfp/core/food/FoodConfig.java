/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

/**
 * The server settings food decay depends on (TFC {@code ServerConfig.foodDecayModifier} and
 * {@code foodDecayStackTicks}). Trait modifiers live on {@link FoodTrait} instances.
 *
 * @param decayModifier multiplier on every food's decay modifier (larger = shorter life, 0 = no decay)
 * @param stackTicks    the creation-date rounding window and stacking tolerance, in player ticks
 */
public record FoodConfig(float decayModifier, long stackTicks) {
    public static final FoodConfig DEFAULT = new FoodConfig(FoodDecay.DEFAULT_CONFIG_MODIFIER, FoodDecay.DEFAULT_STACK_TICKS);

    public FoodConfig {
        if (!(decayModifier >= 0) || Float.isInfinite(decayModifier)) {
            throw new IllegalArgumentException("decayModifier must be finite and >= 0: " + decayModifier);
        }
        if (stackTicks < 1) {
            throw new IllegalArgumentException("stackTicks < 1: " + stackTicks);
        }
    }
}
