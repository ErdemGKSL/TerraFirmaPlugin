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
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.kyori.adventure.text.TextComponent;

/**
 * The climate and heat text placeholders (M2-CLIM-03, PAR-HUD-08). They read only the player's {@link HudSnapshot}
 * (the values the climate and needs services publish from the player's thread), never the world or an item, so they are
 * safe on BetterHud's async timer on Folia.
 * <ul>
 *   <li>{@code tfp_ambient_temp}: the ambient temperature, a dash until the first reading.</li>
 *   <li>{@code tfp_held_heat}: the held item's heat as TFC's default {@code COLOR} display style writes it, built by the
 *       tooltip's own {@link HeatDisplayStyle#formatColored} (the {@code heat.level.*} name from our en_us lang and one
 *       {@value HeatDisplayStyle#STAR} per fifth of the level passed), empty while the item shows no heat. en_us: a
 *       placeholder has no player locale here (see the ledger).</li>
 *   <li>{@code tfp_held_heat_color}: the colour of that text, TFC {@code Heat.getColor} as a hex code
 *       ({@code #ff5555} for {@code RED}), empty while the item shows no heat. The popup layout colours the text with
 *       it (PAR-HUD-10).</li>
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
        t.put("tfp_held_heat_color", s -> heatColor((float) s.heldTemperature()));
        TEXTS = Collections.unmodifiableMap(t);
    }

    /** The plugin's message bundles, loaded on first use. */
    private static final class Lang {
        static final Messages MESSAGES = Messages.fromJar();
    }

    /** TFC {@code ItemTemperatureDisplayStyle.COLOR.formatColored}, en_us; empty when the temperature has no level. */
    private static Optional<TextComponent> colored(float temperature) {
        return HeatDisplayStyle.COLOR.formatColored(temperature, Messages.FALLBACK, Lang.MESSAGES::get);
    }

    /** TFC {@code ItemTemperatureDisplayStyle.COLOR}: level name and stars, empty when there is no heat level. */
    static String heatText(float temperature) {
        return colored(temperature).map(TextComponent::content).orElse("");
    }

    /** TFC {@code Heat.getColor} of the temperature's level as {@code #rrggbb}, empty when there is no heat level. */
    static String heatColor(float temperature) {
        return colored(temperature).map(TextComponent::color).map(c -> c.asHexString().toLowerCase(Locale.ROOT))
                .orElse("");
    }
}
