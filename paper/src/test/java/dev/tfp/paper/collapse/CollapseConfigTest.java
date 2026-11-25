/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.collapse.CollapseRules;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** PAR-PHYS-07: the {@code collapse} section of config.yml (TFC's server collapse options). */
class CollapseConfigTest {
    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return y;
    }

    @Test
    void tfcDefaults() {
        CollapseConfig d = CollapseConfig.DEFAULT;
        assertThat(d.triggerChance()).isEqualTo(0.1);
        assertThat(d.fakeTriggerChance()).isEqualTo(0.35);
        assertThat(d.propagateChance()).isEqualTo(0.55);
        assertThat(d.explosionPropagateChance()).isEqualTo(0.3);
        assertThat(d.minRadius()).isEqualTo(3);
        assertThat(d.radiusVariance()).isEqualTo(16);
        assertThat(d.chiselsStartCollapses()).isTrue();
        assertThat(CollapseRules.DEFAULT_MIN_RADIUS).isEqualTo(d.minRadius());
        assertThat(CollapseService.EXPLOSION_PROPAGATE_CHANCE).isEqualTo(0.3);
    }

    @Test
    void theShippedConfigYmlHoldsTheDefaults() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/config.yml");
                Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(r);
            List<String> warnings = new ArrayList<>();
            assertThat(y.isConfigurationSection("collapse")).isTrue();
            assertThat(CollapseConfig.parse(y.getConfigurationSection("collapse"), warnings::add))
                    .isEqualTo(CollapseConfig.DEFAULT);
            assertThat(warnings).isEmpty();
        }
    }

    @Test
    void valuesAreRead() {
        CollapseConfig c = CollapseConfig.parse(yaml("""
                trigger-chance: 0.5
                fake-trigger-chance: 0
                propagate-chance: 1
                explosion-propagate-chance: 0.9
                min-radius: 5
                radius-variance: 32
                chisels-start-collapses: false
                """), m -> { throw new AssertionError(m); });
        assertThat(c).isEqualTo(new CollapseConfig(0.5, 0, 1, 0.9, 5, 32, false));
    }

    @Test
    void badValuesTakeTheDefaultWithAWarning() {
        List<String> warnings = new ArrayList<>();
        CollapseConfig c = CollapseConfig.parse(yaml("""
                trigger-chance: 1.5
                fake-trigger-chance: lots
                min-radius: 0
                radius-variance: 33
                propagate-chance: 0.25
                """), warnings::add);
        assertThat(c.triggerChance()).isEqualTo(0.1);
        assertThat(c.fakeTriggerChance()).isEqualTo(0.35);
        assertThat(c.minRadius()).isEqualTo(3);
        assertThat(c.radiusVariance()).isEqualTo(16);
        assertThat(c.propagateChance()).isEqualTo(0.25);
        assertThat(warnings).hasSize(4).allMatch(w -> w.startsWith("collapse."));
    }

    @Test
    void aMissingSectionIsAllDefaults() {
        assertThat(CollapseConfig.parse(null, m -> { throw new AssertionError(m); })).isEqualTo(CollapseConfig.DEFAULT);
    }

    @Test
    void theRecordRejectsOutOfRangeValues() {
        assertThatThrownBy(() -> new CollapseConfig(2, 0, 0, 0, 3, 16, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollapseConfig(0, 0, 0, 0, 33, 16, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
