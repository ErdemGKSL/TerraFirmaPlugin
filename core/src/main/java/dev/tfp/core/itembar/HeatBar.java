/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/BarSystem.java @ c3b4d9536 (HEAT)
 */
package dev.tfp.core.itembar;

import dev.tfp.core.heat.HeatLevel;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * TFC's heat bar ({@code BarSystem.HEAT}): visible while the item is hot ({@code HeatCapability.isHot}: temperature
 * above 0) and the option is on (TFC's client option {@code displayItemHeatBars}, here a server setting); width
 * {@code clamp(round(13 * temperature / recipe temperature), 1, 13)}, or over {@link HeatLevel#MAX_VISIBLE} for an item
 * without a heating recipe; colour the {@code Heat} level colour. Its reset ({@code createDefaultItem}) is a cold item.
 *
 * @param <S> the stack view
 */
public final class HeatBar<S> implements ItemBar<S> {
    public static final String ID = "heat";

    private final BooleanSupplier enabled;
    private final Function<S, Optional<Float>> temperature;
    private final BiFunction<S, Float, Optional<Float>> recipeTemperature;
    private final ToIntFunction<HeatLevel> colors;

    /**
     * @param enabled           the {@code displayItemHeatBars} setting, read on every call
     * @param temperature       the stack's lazy temperature now, empty when it has no heat state or cannot be heated
     * @param recipeTemperature the temperature of the stack's heating recipe (TFC {@code HeatingRecipe.getRecipe}),
     *                          given the stack and its temperature; empty without a recipe
     * @param colors            the RGB colour of each heat level (presentation: TFC's {@code ChatFormatting})
     */
    public HeatBar(BooleanSupplier enabled, Function<S, Optional<Float>> temperature,
            BiFunction<S, Float, Optional<Float>> recipeTemperature, ToIntFunction<HeatLevel> colors) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.temperature = Objects.requireNonNull(temperature, "temperature");
        this.recipeTemperature = Objects.requireNonNull(recipeTemperature, "recipeTemperature");
        this.colors = Objects.requireNonNull(colors, "colors");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isBarVisible(S stack) {
        return enabled.getAsBoolean() && isHot(temperature.apply(stack).orElse(0f));
    }

    @Override
    public int barWidth(S stack) {
        Optional<Float> t = temperature.apply(stack);
        if (t.isEmpty()) {
            return 0;
        }
        return width(t.get(), recipeTemperature.apply(stack, t.get()));
    }

    @Override
    public int barColor(S stack) {
        float t = temperature.apply(stack).orElse(0f);
        if (t > 0f) {
            Optional<HeatLevel> level = HeatLevel.of(t);
            if (level.isPresent()) {
                return colors.applyAsInt(level.get());
            }
        }
        return 0;
    }

    /** TFC {@code HeatCapability.isHot}: the temperature is above 0. */
    public static boolean isHot(float temperature) {
        return temperature > 0f;
    }

    /**
     * TFC's heat bar width: {@code clamp(round(13f * temperature / recipe), 1, 13)} with the heating recipe's
     * temperature, else with {@code Heat.maxVisibleTemperature()} (1600).
     */
    public static int width(float temperature, Optional<Float> recipeTemperature) {
        float max = recipeTemperature.orElse(HeatLevel.MAX_VISIBLE);
        return clamp(Math.round(WIDTH * temperature / max), 1, WIDTH);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
