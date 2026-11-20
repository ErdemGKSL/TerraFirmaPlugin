/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.rock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.settings.RockLayerConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-23: TFC's {@code RockLayerSettings.decode} refusals and {@code sampleAtLayer} against a TFC oracle. */
class RockLayerSettingsTest {
    private static RockLayerConfig with(List<RockLayerConfig.Layer> layers, List<String> bottom, List<String> land) {
        final RockLayerConfig p = RockLayerConfig.PRESET;
        return new RockLayerConfig(p.rocks(), bottom, layers, p.oceanFloor(), p.volcanic(), land, p.uplift());
    }

    @Test
    void thePresetDecodes() {
        final RockLayerSampler s = RockLayerSampler.decode(RockLayerConfig.PRESET);
        assertThat(s.rocks()).hasSize(21).contains("tfc:granite", "tfc:tuff");
        assertThat(s.sampleAtLayer(2, 0)).isEqualTo(RockLayerSampler.PRESET.sampleAtLayer(2, 0));
    }

    @Test
    void anUnknownRockIsRefused() {
        final List<RockLayerConfig.Layer> layers = new ArrayList<>(RockLayerConfig.PRESET.layers());
        layers.add(new RockLayerConfig.Layer("extra", Map.of("obsidian", "bottom")));
        assertThatThrownBy(() -> RockLayerSampler.decode(with(layers, RockLayerConfig.PRESET.bottom(), RockLayerConfig.PRESET.land())))
            .hasMessage("No rock with id: obsidian");
        assertThatThrownBy(() -> RockLayerSampler.decode(with(RockLayerConfig.PRESET.layers(), List.of("granite", "pumice"),
            RockLayerConfig.PRESET.land()))).hasMessage("No rock with id: pumice");
    }

    @Test
    void aCycleIsRefused() {
        // a layer may only name layers before it: a cycle needs a forward reference, which TFC refuses
        final List<RockLayerConfig.Layer> layers = new ArrayList<>();
        layers.add(new RockLayerConfig.Layer("a", Map.of("granite", "b")));
        layers.add(new RockLayerConfig.Layer("b", Map.of("diorite", "a")));
        assertThatThrownBy(() -> RockLayerSampler.decode(with(layers, RockLayerConfig.PRESET.bottom(), List.of("a"))))
            .hasMessage("No layer with id: b");
        final List<RockLayerConfig.Layer> self = List.of(new RockLayerConfig.Layer("a", Map.of("granite", "a")));
        assertThatThrownBy(() -> RockLayerSampler.decode(with(self, RockLayerConfig.PRESET.bottom(), List.of("a"))))
            .hasMessage("No layer with id: a");
    }

    @Test
    void duplicateAndMissingLayersAreRefused() {
        final List<RockLayerConfig.Layer> layers = new ArrayList<>(RockLayerConfig.PRESET.layers());
        layers.add(new RockLayerConfig.Layer("felsic", Map.of("granite", "bottom")));
        assertThatThrownBy(() -> RockLayerSampler.decode(with(layers, RockLayerConfig.PRESET.bottom(), RockLayerConfig.PRESET.land())))
            .hasMessage("More than one layer named felsic");
        assertThatThrownBy(() -> RockLayerSampler.decode(with(RockLayerConfig.PRESET.layers(), RockLayerConfig.PRESET.bottom(),
            List.of("sedimentary", "metamorphic")))).hasMessage("No layer with id: metamorphic");
    }

    @Test
    void aMissingBottomIsRefused() {
        final Map<String, Object> map = new LinkedHashMap<>(RockLayerConfig.PRESET.toMap());
        map.remove("bottom");
        assertThatThrownBy(() -> RockLayerConfig.fromMap(map)).hasMessageContaining("bottom");
        assertThatThrownBy(() -> RockLayerSampler.decode(with(RockLayerConfig.PRESET.layers(), List.of(),
            RockLayerConfig.PRESET.land()))).hasMessageContaining("Empty layer");
    }

    @Test
    void samplesEqualTfcsSampleAtLayer() throws IOException {
        int n = 0;
        try (InputStream in = RockLayerSettingsTest.class.getResourceAsStream("/dev/tfp/core/worldgen/rock/rock_layers.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                final String[] c = line.split("\t");
                assertThat(RockLayerSampler.PRESET.sampleAtLayer(Integer.parseInt(c[0]), Integer.parseInt(c[1])))
                    .as(line).isEqualTo("tfc:" + c[2]);
                n++;
            }
        }
        assertThat(n).isEqualTo(10_000);
    }
}
