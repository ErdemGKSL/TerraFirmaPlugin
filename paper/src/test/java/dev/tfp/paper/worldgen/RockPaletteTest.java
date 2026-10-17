/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The refuse rule of M2-GEN-07 (M0-CE-01 decision), with fakes: no CraftEngine and no server needed. */
class RockPaletteTest {
    private static final List<String> IDS = List.of("tfp:rock/raw/granite", "tfp:rock/raw/basalt");

    @Test
    void refusesWhileCraftEngineIsNotLoaded() {
        List<String> looked = new ArrayList<>();
        RockPalette<String> palette = RockPalette.resolve(0, false, IDS, id -> {
            looked.add(id);
            return Optional.of("state:" + id);
        });
        assertThat(palette.resolved()).isFalse();
        assertThat(palette.refusal()).contains("has not loaded");
        assertThat(looked).isEmpty();
        assertThatThrownBy(() -> palette.block(0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesWhenAnyRockIsUnknownAndNamesIt() {
        Map<String, String> known = Map.of("tfp:rock/raw/granite", "granite-state");
        RockPalette<String> palette = RockPalette.resolve(3, true, IDS, id -> Optional.ofNullable(known.get(id)));
        assertThat(palette.resolved()).isFalse();
        assertThat(palette.refusal()).contains("tfp:rock/raw/basalt").doesNotContain("tfp:rock/raw/granite");
        assertThat(palette.generation()).isEqualTo(3);
    }

    @Test
    void nullLookupResultCountsAsUnknown() {
        RockPalette<String> palette = RockPalette.resolve(1, true, IDS, id -> null);
        assertThat(palette.resolved()).isFalse();
        assertThat(palette.refusal()).contains("unknown custom block");
    }

    @Test
    void resolvedPaletteKeepsIdOrderAndGeneration() {
        Map<String, String> known = Map.of(
            "tfp:rock/raw/granite", "granite-state",
            "tfp:rock/raw/basalt", "basalt-state");
        RockPalette<String> palette = RockPalette.resolve(7, true, IDS, id -> Optional.ofNullable(known.get(id)));
        assertThat(palette.resolved()).isTrue();
        assertThat(palette.refusal()).isNull();
        assertThat(palette.generation()).isEqualTo(7);
        assertThat(palette.block(0)).isEqualTo("granite-state");
        assertThat(palette.block(1)).isEqualTo("basalt-state");
    }

    @Test
    void theTerrainRockIdsAreTheRawRockContent() {
        assertThat(TfcTerrain.ROCK_IDS).hasSize(21).allMatch(id -> id.startsWith("tfp:rock/raw/"));
        assertThat(TfcTerrain.ROCK_IDS.get(0)).isEqualTo("tfp:rock/raw/shale");
    }
}
