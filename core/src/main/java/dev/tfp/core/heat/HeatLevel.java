/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/Heat.java @ c3b4d9536
 */
package dev.tfp.core.heat;

import java.util.Optional;

/**
 * The visible heat levels of an item or device ({@code Heat}). Each level covers {@code [min, max)}; the level names and
 * the colour of each level are presentation, and live with the visuals. Temperatures above {@link #MAX_VISIBLE} stay
 * {@link #BRILLIANT_WHITE} here, as TFC's {@code getHeat} does.
 */
public enum HeatLevel {
    WARMING(0f, 80f),
    HOT(80f, 210f),
    VERY_HOT(210f, 480f),
    FAINT_RED(480f, 580f),
    DARK_RED(580f, 730f),
    BRIGHT_RED(730f, 930f),
    ORANGE(930f, 1100f),
    YELLOW(1100f, 1300f),
    YELLOW_WHITE(1300f, 1400f),
    WHITE(1400f, 1500f),
    BRILLIANT_WHITE(1500f, 1600f);

    /** {@code Heat.maxVisibleTemperature()}: the top of {@link #BRILLIANT_WHITE}. */
    public static final float MAX_VISIBLE = 1600f;

    private static final HeatLevel[] VALUES = values();

    private final float min;
    private final float max;

    HeatLevel(float min, float max) {
        this.min = min;
        this.max = max;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    /**
     * TFC {@code Heat.getHeat}: empty when {@code temperature <= 0} (the first level's minimum is exclusive), otherwise
     * the first level whose maximum exceeds the temperature, and {@link #BRILLIANT_WHITE} above the visible range.
     */
    public static Optional<HeatLevel> of(float temperature) {
        if (!(temperature > WARMING.min)) {
            return Optional.empty();
        }
        for (HeatLevel level : VALUES) {
            if (temperature < level.max) {
                return Optional.of(level);
            }
        }
        return Optional.of(BRILLIANT_WHITE);
    }

    /**
     * TFC {@code Heat.scaleTemperatureForGui}: a thermometer height in {@code [0, 51]} pixels, {@code (int) (51 * t / 1600)}
     * clamped.
     */
    public static int scaleForGui(float temperature) {
        int pixels = (int) (51 * temperature / MAX_VISIBLE);
        return Math.max(0, Math.min(51, pixels));
    }
}
