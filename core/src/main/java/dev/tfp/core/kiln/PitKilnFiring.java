/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (cookContents: heat each
 *       heatable item to the kiln temperature, then the cached recipe; updateCache on lighting and on the last tick;
 *       serverTick order)
 */
package dev.tfp.core.kiln;

import java.util.Objects;

/**
 * What one content slot of a pit kiln holds after the burn, computed once from the {@link PitKilnRules.Outcome} instead of
 * per tick.
 *
 * <p>TFC per lit tick: every item with a heat capability is set to the kiln temperature, then the recipe cached for its
 * slot converts it when the temperature reaches the recipe's ({@code isValidTemperature}). The recipes are cached when
 * the kiln is lit and again on the last tick ({@code remainingTicks <= 0}, before that tick's cooking). So:
 * <ol>
 *   <li>The item's own recipe converts it on the first tick at its temperature ({@link PitKilnRules#convertTick}).
 *       The stale cached recipe applied to the result on later ticks makes the same result again (TFC's recipe results
 *       do not depend on the input beyond copied components), so the conversion happens once.</li>
 *   <li>On the last tick of a kiln that fired, the result's own recipe is cached and may convert it once more at the
 *       final temperature.</li>
 *   <li>Every heatable item left ends at the temperature of the last cooked tick.</li>
 * </ol>
 * A failed kiln stops cooking the tick before the failure and never reaches step 2. An item without a heat capability is
 * never touched, even when it has a recipe, as in TFC.
 *
 * @see PitKilnRules
 */
public final class PitKilnFiring {
    private PitKilnFiring() {
    }

    /**
     * The item operations the firing needs; the platform layer implements them on its stacks.
     *
     * @param <S> the stack type
     */
    public interface Items<S> {
        /** Whether {@code stack} is empty. */
        boolean isEmpty(S stack);

        /** TFC {@code HeatCapability.get(stack) != null}. */
        boolean heatable(S stack);

        /** The temperature of the heating recipe of {@code stack}, or {@code NaN} when it has none. */
        float recipeTemperature(S stack);

        /** TFC {@code heat.setTemperature(temperature)} at calendar tick {@code tick}: a new stack, the input untouched. */
        S withTemperature(S stack, float temperature, long tick);

        /**
         * TFC {@code recipe.assembleItem(stack)} of the stack's own recipe (the result keeps the input's heat); an empty
         * stack when the recipe makes no item (liquids are lost).
         */
        S convert(S stack);
    }

    /**
     * The content of one slot after the kiln reached {@code outcome}.
     *
     * @param stack   the slot's stack when the kiln was lit
     * @param litTick the tick the kiln was lit
     * @param outcome the kiln's outcome ({@link PitKilnRules#evaluate})
     * @param config  the kiln config
     * @param items   the stack operations
     */
    public static <S> S resolve(S stack, long litTick, PitKilnRules.Outcome outcome, PitKilnRules.Config config,
            Items<S> items) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(items, "items");
        long last = outcome.cookedThrough();
        if (last == PitKilnRules.NOTHING || last < litTick || items.isEmpty(stack) || !items.heatable(stack)) {
            return stack;
        }
        S current = stack;
        boolean convertedBefore = false;
        float own = items.recipeTemperature(current);
        if (!Float.isNaN(own)) {
            long at = PitKilnRules.convertTick(own, litTick, config);
            if (at != PitKilnRules.NEVER && at <= last) {
                float t = PitKilnRules.temperatureAt(at, litTick, config);
                current = items.convert(items.withTemperature(current, t, at));
                convertedBefore = at < last;
            }
        }
        float lastTemperature = PitKilnRules.temperatureAt(last, litTick, config);
        // The last tick re-caches the recipes before cooking: a result made on an earlier tick meets its own recipe once.
        if (outcome.phase() == PitKilnRules.Phase.FIRED && convertedBefore && !items.isEmpty(current)
                && items.heatable(current)) {
            current = items.withTemperature(current, lastTemperature, last);
            float next = items.recipeTemperature(current);
            if (!Float.isNaN(next) && lastTemperature >= next) {
                current = items.convert(current);
            }
        }
        if (!items.isEmpty(current) && items.heatable(current)) {
            current = items.withTemperature(current, lastTemperature, last);
        }
        return current;
    }
}
