/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (thirst, temperature)
 */
package dev.tfp.core.thirst;

/**
 * The server settings thirst depends on, with TFC's defaults ({@code ServerConfig}; TFC reads the values with
 * {@code floatValue()}, so they are floats here).
 *
 * @param maxThirst              {@code PlayerInfo.MAX_THIRST} (100); a new player starts full
 * @param thirstModifier         {@code thirstModifier} (5): thirst lost per point of hunger spent, before temperature
 * @param maxTemperatureDecay    {@code PlayerInfo.MAX_TEMPERATURE_THIRST_DECAY} (0.4): the extra share at high heat
 * @param lowTemperature         the temperature at which the heat share starts (22)
 * @param highTemperature        the temperature at which the heat share is maximal (34)
 * @param temperatureOverheating {@code enableThirstOverheating} (true)
 * @param slowedBelow            thirst below this slows the player (20)
 * @param severeBelow            thirst below this slows the player more (10); at 0 or less the player is dehydrated
 */
public record ThirstConfig(float maxThirst, float thirstModifier, float maxTemperatureDecay, float lowTemperature,
        float highTemperature, boolean temperatureOverheating, float slowedBelow, float severeBelow) {
    public static final ThirstConfig DEFAULT = new ThirstConfig(100f, 5f, 0.4f, 22f, 34f, true, 20f, 10f);

    public ThirstConfig {
        if (!(maxThirst > 0) || Float.isInfinite(maxThirst)) {
            throw new IllegalArgumentException("maxThirst must be positive and finite: " + maxThirst);
        }
        if (!(thirstModifier >= 0) || thirstModifier > 100) {
            throw new IllegalArgumentException("thirstModifier must be in [0, 100]: " + thirstModifier);
        }
        if (!(maxTemperatureDecay >= 0)) {
            throw new IllegalArgumentException("maxTemperatureDecay must be >= 0: " + maxTemperatureDecay);
        }
        if (!(highTemperature > lowTemperature)) {
            throw new IllegalArgumentException("highTemperature must exceed lowTemperature");
        }
        if (!(severeBelow >= 0) || !(slowedBelow >= severeBelow) || slowedBelow > maxThirst) {
            throw new IllegalArgumentException("thresholds must satisfy 0 <= severeBelow <= slowedBelow <= maxThirst");
        }
    }
}
