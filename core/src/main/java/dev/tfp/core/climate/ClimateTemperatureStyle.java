/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ClimateTemperatureDisplayStyle.java @ c3b4d9536
 */
package dev.tfp.core.climate;

import java.util.Locale;
import java.util.Optional;

/**
 * TFC's {@code climateTooltipStyle} client option: the unit climate (external) temperatures are shown in. The conversions
 * are TFC's float arithmetic and its {@code %.1f}; the unit suffixes are TFC's en_us {@code tfc.tooltip.temperature_*}
 * strings ({@code %s°C}, {@code %s°F}, {@code %s°R}, {@code %s K}). This is TFC's {@code formatRange} (every value is
 * shown, cold ones too), which is what TFC's climate screen and tab tooltip use.
 * <p>
 * Two differences from TFC, both shared with {@link ClimateFormat}: the decimal separator is always {@code .}
 * ({@link Locale#ROOT}, TFC uses the client's locale), and a value that rounds to zero shows as {@code 0.0}, never
 * {@code -0.0}. NaN (no reading yet) shows {@link ClimateFormat#UNKNOWN}.
 */
public enum ClimateTemperatureStyle {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    RANKINE("°R"),
    KELVIN(" K");

    /** TFC's {@code ClientConfig} default. */
    public static final ClimateTemperatureStyle DEFAULT = CELSIUS;

    private final String suffix;

    ClimateTemperatureStyle(String suffix) {
        this.suffix = suffix;
    }

    /** The unit text after the number, from TFC's en_us lang. */
    public String suffix() {
        return suffix;
    }

    /** The temperature in this unit, TFC's float arithmetic. */
    public float convert(float celsius) {
        return switch (this) {
            case CELSIUS -> celsius;
            case FAHRENHEIT -> celsius * (9f / 5f) + 32f;
            case RANKINE -> celsius * (9f / 5f) + 491.67f;
            case KELVIN -> celsius + 273.15f;
        };
    }

    /** {@code 68.0°F}: the converted value with one decimal place and the unit, or {@link ClimateFormat#UNKNOWN}. */
    public String format(float celsius) {
        if (Float.isNaN(celsius) || Float.isInfinite(celsius)) {
            return ClimateFormat.UNKNOWN;
        }
        String number = String.format(Locale.ROOT, "%.1f", convert(celsius));
        if (number.equals("-0.0")) {
            number = "0.0";
        }
        return number + suffix;
    }

    /** The style named {@code name} (TFC's enum constant, case insensitive), if any. */
    public static Optional<ClimateTemperatureStyle> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String n = name.strip().toUpperCase(Locale.ROOT);
        for (ClimateTemperatureStyle s : values()) {
            if (s.name().equals(n)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
