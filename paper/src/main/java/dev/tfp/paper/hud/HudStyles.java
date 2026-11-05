/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ClientConfig.java @ c3b4d9536 (healthDisplayStyle, climateTooltipStyle, enableExperienceBar, disabledExperienceBarStyle)
 */
package dev.tfp.paper.hud;

import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.health.HealthConfig;
import dev.tfp.core.health.HealthDisplay;
import dev.tfp.core.health.HealthDisplayStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * TFC's HUD display options, server-wide. TFC keeps them per player in its client config ({@code ClientConfig}); a vanilla
 * client has none, so the server owner picks one value for everyone (NP-CLIENT-04, ledger). Keys use TFC's names in
 * kebab case and TFC's defaults:
 * <pre>
 * hud:
 *   health-display-style: TFC            # TFC | VANILLA | TFC_CURRENT | VANILLA_CURRENT
 *   climate-temperature-style: CELSIUS   # CELSIUS | FAHRENHEIT | RANKINE | KELVIN (TFC climateTooltipStyle)
 *   enable-experience-bar: true
 *   disabled-experience-bar-style: HOVER # HOVER | BUMP | LEFT_HOTBAR
 * </pre>
 * Immutable, so the BetterHud placeholder threads may read one instance freely. Parsing is lenient like the rest of
 * config.yml: missing keys take TFC's default, a bad value takes the default with a warning, unknown keys warn.
 * <p>
 * The hidden XP bar ({@code enable-experience-bar: false} and its {@code BUMP}/{@code LEFT_HOTBAR} layouts) is not built:
 * the value is read and kept, but the vanilla XP bar stays and a warning says so (ledger divergence).
 *
 * @param health      TFC {@code healthDisplayStyle}
 * @param climate     TFC {@code climateTooltipStyle}
 * @param experienceBar TFC {@code enableExperienceBar}
 * @param disabledExperienceBar TFC {@code disabledExperienceBarStyle}, used only while the XP bar is off
 */
public record HudStyles(HealthDisplayStyle health, ClimateTemperatureStyle climate, boolean experienceBar,
                        DisabledExperienceBarStyle disabledExperienceBar) {
    /** The config.yml section. */
    public static final String SECTION = "hud";
    public static final String HEALTH_KEY = "health-display-style";
    public static final String CLIMATE_KEY = "climate-temperature-style";
    public static final String XP_BAR_KEY = "enable-experience-bar";
    public static final String XP_STYLE_KEY = "disabled-experience-bar-style";
    /** The keys of the section, in config.yml order. */
    public static final List<String> KEYS = List.of(HEALTH_KEY, CLIMATE_KEY, XP_BAR_KEY, XP_STYLE_KEY);

    /** TFC's {@code ClientConfig} defaults. */
    public static final HudStyles DEFAULTS = new HudStyles(HealthDisplayStyle.DEFAULT, ClimateTemperatureStyle.DEFAULT,
            true, DisabledExperienceBarStyle.HOVER);

    public HudStyles {
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(climate, "climate");
        Objects.requireNonNull(disabledExperienceBar, "disabledExperienceBar");
    }

    /** TFC {@code DisabledExperienceBarStyle}, same constants and order. */
    public enum DisabledExperienceBarStyle {
        HOVER,
        BUMP,
        LEFT_HOTBAR;

        static Optional<DisabledExperienceBarStyle> byName(String name) {
            String n = name.strip().replace('-', '_').toUpperCase(Locale.ROOT);
            return Arrays.stream(values()).filter(s -> s.name().equals(n)).findFirst();
        }
    }

    /** The health bar text of a shown {@link HealthDisplay} in this style ({@code 850/850} at TFC's default). */
    public String healthText(HealthDisplay display, HealthConfig config) {
        return health.format(display, config);
    }

    /** A climate temperature in this unit ({@code 12.3°C} at TFC's default), a dash for NaN. */
    public String temperatureText(double celsius) {
        return climate.format((float) celsius);
    }

    /**
     * @param styles   the typed styles (always complete)
     * @param warnings human-readable problems, one per entry, prefixed with the dotted key
     */
    public record Parsed(HudStyles styles, List<String> warnings) {
        public Parsed {
            Objects.requireNonNull(styles, "styles");
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Reads the {@code hud} section (the plain tree a YAML reader produces); null or absent means TFC's defaults.
     * A non-map value warns and takes the defaults.
     */
    public static Parsed parse(Object section) {
        List<String> warnings = new ArrayList<>();
        Map<?, ?> m;
        if (section == null) {
            m = Map.of();
        } else if (section instanceof Map<?, ?> s) {
            m = s;
        } else {
            warnings.add(SECTION + ": expected a section, got '" + section + "'; using defaults");
            m = Map.of();
        }
        for (Object k : m.keySet()) {
            if (!KEYS.contains(String.valueOf(k))) {
                warnings.add("unknown key '" + SECTION + "." + k + "' (ignored)");
            }
        }
        HealthDisplayStyle health = named(m, HEALTH_KEY, HealthDisplayStyle.DEFAULT, HealthDisplayStyle::byName,
                HealthDisplayStyle.values(), warnings);
        ClimateTemperatureStyle climate = named(m, CLIMATE_KEY, ClimateTemperatureStyle.DEFAULT,
                ClimateTemperatureStyle::byName, ClimateTemperatureStyle.values(), warnings);
        boolean xpBar = DEFAULTS.experienceBar();
        if (m.containsKey(XP_BAR_KEY)) {
            Object o = m.get(XP_BAR_KEY);
            if (o instanceof Boolean b) {
                xpBar = b;
            } else {
                warnings.add(SECTION + "." + XP_BAR_KEY + ": expected true or false, got '" + o + "'; using " + xpBar);
            }
        }
        DisabledExperienceBarStyle xpStyle = named(m, XP_STYLE_KEY, DEFAULTS.disabledExperienceBar(),
                n -> DisabledExperienceBarStyle.byName(n), DisabledExperienceBarStyle.values(), warnings);
        if (!xpBar) {
            warnings.add(SECTION + "." + XP_BAR_KEY + ": false is not supported yet; the vanilla XP bar stays and "
                    + XP_STYLE_KEY + " has no effect");
        }
        return new Parsed(new HudStyles(health, climate, xpBar, xpStyle), warnings);
    }

    /** The tree {@link #parse} reads back to equal styles, keys in config.yml order. */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(HEALTH_KEY, health.name());
        out.put(CLIMATE_KEY, climate.name());
        out.put(XP_BAR_KEY, experienceBar);
        out.put(XP_STYLE_KEY, disabledExperienceBar.name());
        return out;
    }

    private static <E extends Enum<E>> E named(Map<?, ?> m, String key, E def, Function<String, Optional<E>> byName,
                                               E[] values, List<String> warnings) {
        if (!m.containsKey(key)) {
            return def;
        }
        Object o = m.get(key);
        Optional<E> v = o == null ? Optional.empty() : byName.apply(o.toString());
        if (v.isPresent()) {
            return v.get();
        }
        warnings.add(SECTION + "." + key + ": unknown style '" + o + "' (expected one of "
                + Arrays.stream(values).map(Enum::name).toList() + "); using " + def.name());
        return def;
    }
}
