/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java @ c3b4d9536
 * (the colours from src/main/java/net/dries007/tfc/common/component/heat/Heat.java)
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.heat.HeatLevel;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * TFC's {@code ItemTemperatureDisplayStyle} (the {@code heatTooltipStyle} client option): how an item or device
 * temperature reads in a tooltip. {@link #COLOR} (TFC's default) shows the heat level's name followed by one
 * {@value #STAR} per fifth of the level already passed; the others show the number in a unit. {@link #formatColored}
 * gives the text the colour of the heat level ({@code Heat.getColor}).
 *
 * <p>A vanilla client has no client config, so this is one server-wide choice (NOT-PARITY NP-CLIENT-04).
 */
public enum HeatDisplayStyle {
    COLOR,
    CELSIUS,
    FAHRENHEIT,
    RANKINE,
    KELVIN;

    /** U+066D ARABIC FIVE POINTED STAR, the progress mark of the {@link #COLOR} style. */
    public static final String STAR = "٭";

    /** Message key prefix of the level names: {@code heat.level.<level in lower case>}. */
    public static final String LEVEL_KEY_PREFIX = "heat.level.";

    /** TFC {@code Heat.getColor}: the chat colour of each level. */
    public static NamedTextColor color(HeatLevel level) {
        return switch (level) {
            case WARMING, HOT, VERY_HOT -> NamedTextColor.GRAY;
            case FAINT_RED, DARK_RED -> NamedTextColor.DARK_RED;
            case BRIGHT_RED -> NamedTextColor.RED;
            case ORANGE -> NamedTextColor.GOLD;
            case YELLOW, YELLOW_WHITE -> NamedTextColor.YELLOW;
            case WHITE, BRILLIANT_WHITE -> NamedTextColor.WHITE;
        };
    }

    /**
     * The number of {@value #STAR} marks of the {@link #COLOR} style: one for each {@code i} in 1..4 with
     * {@code temperature > min + i * 0.2 * (max - min)} (float arithmetic as TFC), none for {@link HeatLevel#BRILLIANT_WHITE}.
     */
    public static int stars(HeatLevel level, float temperature) {
        if (level == HeatLevel.BRILLIANT_WHITE) {
            return 0;
        }
        int n = 0;
        for (int i = 1; i <= 4; i++) {
            if (temperature <= level.min() + ((float) i * 0.2f) * (level.max() - level.min())) {
                continue;
            }
            n++;
        }
        return n;
    }

    /** TFC {@code format}: the text of a temperature, or empty when it is not positive (or, for COLOR, has no level). */
    public Optional<TextComponent> format(float temperature, String locale, TooltipText text) {
        if (!(temperature > 0)) {
            return Optional.empty();
        }
        return Optional.ofNullable(formatter(temperature, locale, text));
    }

    /** TFC {@code formatColored}: as {@link #format} in the colour of the temperature's heat level; empty without a level. */
    public Optional<TextComponent> formatColored(float temperature, String locale, TooltipText text) {
        Optional<HeatLevel> level = HeatLevel.of(temperature);
        if (level.isEmpty()) {
            return Optional.empty();
        }
        TextComponent t = formatter(temperature, locale, text);
        return t == null ? Optional.empty() : Optional.of(t.color(color(level.get())));
    }

    private TextComponent formatter(float temperature, String locale, TooltipText text) {
        return switch (this) {
            case COLOR -> {
                Optional<HeatLevel> level = HeatLevel.of(temperature);
                if (level.isEmpty()) {
                    yield null;
                }
                String name = text.get(locale, LEVEL_KEY_PREFIX + level.get().name().toLowerCase(Locale.ROOT));
                yield Component.text(name + STAR.repeat(stars(level.get(), temperature)));
            }
            case CELSIUS -> Component.text(text.get(locale, "tooltip.temperature.celsius", whole(temperature)));
            case FAHRENHEIT -> Component.text(text.get(locale, "tooltip.temperature.fahrenheit",
                    whole(temperature * (9f / 5f) + 32f)));
            case RANKINE -> Component.text(text.get(locale, "tooltip.temperature.rankine",
                    whole(temperature * (9f / 5f) + 491.67f)));
            case KELVIN -> Component.text(text.get(locale, "tooltip.temperature.kelvin", whole(temperature + 273.15f)));
        };
    }

    /** TFC {@code String.format("%.0f", t)}, locale-independent. */
    static String whole(float temperature) {
        return String.format(Locale.ROOT, "%.0f", temperature);
    }
}
