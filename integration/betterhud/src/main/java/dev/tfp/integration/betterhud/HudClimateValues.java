/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ItemTemperatureDisplayStyle.java @ c3b4d9536 (COLOR, the held heat text)
 */
package dev.tfp.integration.betterhud;

import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The climate and heat text placeholders (M2-CLIM-03, PAR-HUD-08). They read only the player's {@link HudSnapshot}
 * (the values the climate and needs services publish from the player's thread), never the world or an item, so they are
 * safe on BetterHud's async timer on Folia.
 * <ul>
 *   <li>{@code tfp_ambient_temp}: the ambient temperature, a dash until the first reading.</li>
 *   <li>{@code tfp_held_heat}: the held item's heat as TFC's default {@code COLOR} display style writes it (the level
 *       name and one {@value HeatDisplayStyle#STAR} per fifth of the level passed), empty while the item shows no heat.
 *       The name is the en_us one (a placeholder has no player locale here; see the ledger).</li>
 * </ul>
 */
final class HudClimateValues {
    private HudClimateValues() {
    }

    /** Text placeholders by name, as used in our generated BetterHud YAML ({@code [tfp_ambient_temp]}). */
    static final Map<String, Function<HudSnapshot, String>> TEXTS;

    static {
        Map<String, Function<HudSnapshot, String>> t = new LinkedHashMap<>();
        t.put("tfp_ambient_temp", s -> ClimateFormat.temperature(s.ambientTemperature()));
        t.put("tfp_held_heat", s -> heatText((float) s.heldTemperature()));
        TEXTS = Collections.unmodifiableMap(t);
    }

    /** TFC {@code ItemTemperatureDisplayStyle.COLOR.format}: level name and stars, empty for no positive level. */
    static String heatText(float temperature) {
        if (!(temperature > 0)) {
            return "";
        }
        Optional<HeatLevel> level = HeatLevel.of(temperature);
        return level.map(l -> levelName(l) + HeatDisplayStyle.STAR.repeat(HeatDisplayStyle.stars(l, temperature)))
                .orElse("");
    }

    /** The en_us name of a level (TFC {@code tfc.enum.heat.*}: the words of the constant, title case). */
    static String levelName(HeatLevel level) {
        StringBuilder out = new StringBuilder();
        for (String word : level.name().split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }
}
