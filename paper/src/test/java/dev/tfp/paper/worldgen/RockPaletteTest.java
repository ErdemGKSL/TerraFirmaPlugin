/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.worldgen.surface.SurfaceKind;
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

    // ---- PAR-WORLD-04: the surface kinds as CraftEngine ids -------------------------------------------------

    @Test
    void surfaceKindsMapToTheSoilAndGravelIds() {
        assertThat(RockPalette.surfaceId(SurfaceKind.GRASS, "alfisol", "granite")).isEqualTo("tfp:grass/alfisol");
        assertThat(RockPalette.surfaceId(SurfaceKind.DIRT, "podzol", null)).isEqualTo("tfp:dirt/podzol");
        assertThat(RockPalette.surfaceId(SurfaceKind.COARSE_DIRT, "entisol", null)).isEqualTo("tfp:coarse_dirt/entisol");
        assertThat(RockPalette.surfaceId(SurfaceKind.MUD, "fluvisol", null)).isEqualTo("tfp:mud/fluvisol");
        assertThat(RockPalette.surfaceId(SurfaceKind.GRAVEL, null, "granite")).isEqualTo("tfp:rock/gravel/granite");
        for (SurfaceKind kind : List.of(SurfaceKind.ROCK_SURFACE, SurfaceKind.SAND, SurfaceKind.SANDSTONE, SurfaceKind.SNOW,
            SurfaceKind.PACKED_ICE)) {
            assertThat(RockPalette.surfaceId(kind, "alfisol", "granite")).as(kind.name()).isNull();
            assertThat(RockPalette.surfaceIndex(kind, 0, 0, 21)).as(kind.name()).isEqualTo(-1);
        }
        assertThatThrownBy(() -> RockPalette.surfaceId(SurfaceKind.GRASS, "loam", null))
            .isInstanceOf(IllegalArgumentException.class);
        // TFC SoilBlockType.Variant order
        assertThat(RockPalette.SOILS).containsExactly("entisol", "aridisol", "oxisol", "fluvisol", "andisol", "podzol",
            "alfisol", "mollisol");
    }

    @Test
    void surfaceIndexIsThePositionInTheSurfaceIds() {
        List<String> ids = RockPalette.surfaceIds(TfcTerrain.ROCK_IDS);
        int rocks = TfcTerrain.ROCK_IDS.size();
        assertThat(ids).hasSize(4 * 8 + rocks).doesNotHaveDuplicates();
        for (SurfaceKind kind : SurfaceKind.values()) {
            for (int soil = 0; soil < RockPalette.SOILS.size(); soil++) {
                for (int rock = 0; rock < rocks; rock++) {
                    String rockName = TfcTerrain.ROCK_IDS.get(rock).substring("tfp:rock/raw/".length());
                    String id = RockPalette.surfaceId(kind, RockPalette.SOILS.get(soil), rockName);
                    int index = RockPalette.surfaceIndex(kind, soil, rock, rocks);
                    if (id == null) {
                        assertThat(index).isEqualTo(-1);
                    } else {
                        assertThat(ids.get(index)).as("%s soil %d rock %d", kind, soil, rock).isEqualTo(id);
                    }
                }
            }
        }
        assertThatThrownBy(() -> RockPalette.surfaceIndex(SurfaceKind.GRASS, 8, 0, rocks))
            .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> RockPalette.surfaceIndex(SurfaceKind.GRAVEL, 0, rocks, rocks))
            .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> RockPalette.surfaceIds(List.of("tfp:rock/gravel/granite")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPaletteWithTheSurfaceIdsIsRefusedWhileASoilBlockIsUnknown() {
        // The refuse rule covers the surface blocks once they are palette ids: no vanilla stand-in for a missing soil.
        List<String> ids = new ArrayList<>(TfcTerrain.ROCK_IDS);
        int base = ids.size();
        ids.addAll(RockPalette.surfaceIds(TfcTerrain.ROCK_IDS));
        RockPalette<String> refused = RockPalette.resolve(1, true, ids,
            id -> id.equals("tfp:mud/oxisol") || id.equals("tfp:rock/gravel/chert") ? Optional.empty() : Optional.of(id));
        assertThat(refused.resolved()).isFalse();
        assertThat(refused.refusal()).contains("tfp:mud/oxisol", "tfp:rock/gravel/chert").doesNotContain("tfp:grass/");
        RockPalette<String> palette = RockPalette.resolve(1, true, ids, Optional::of);
        assertThat(palette.resolved()).isTrue();
        assertThat(palette.block(base + RockPalette.surfaceIndex(SurfaceKind.GRASS, 6, 0, base))).isEqualTo("tfp:grass/alfisol");
        assertThat(palette.block(base + RockPalette.surfaceIndex(SurfaceKind.GRAVEL, 0, 0, base)))
            .isEqualTo("tfp:rock/gravel/shale");
    }

    @Test
    void everySurfaceIdIsAGeneratedCraftEngineBlock() throws Exception {
        java.nio.file.Path here = java.nio.file.Path.of("").toAbsolutePath();
        java.nio.file.Path repo = java.nio.file.Files.isDirectory(here.resolve("spikes")) ? here : here.getParent();
        java.nio.file.Path config = repo.resolve("content/generated/content/craftengine/tfp/configuration");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(config.resolve("soil")),
            "datagen output present");
        java.util.Set<String> blocks = new java.util.HashSet<>();
        for (String dir : List.of("soil", "rock")) {
            try (var files = java.nio.file.Files.list(config.resolve(dir))) {
                for (java.nio.file.Path file : files.toList()) {
                    boolean inBlocks = false;
                    for (String line : java.nio.file.Files.readAllLines(file)) {
                        if (!line.startsWith(" ") && !line.startsWith("#")) {
                            inBlocks = line.startsWith("blocks");
                        } else if (inBlocks && line.matches(" {2}tfp:[a-z0-9_/]+:")) {
                            blocks.add(line.strip().substring(0, line.strip().length() - 1));
                        }
                    }
                }
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(blocks.contains("tfp:peat"), "datagen output has the soil block set");
        assertThat(blocks).containsAll(RockPalette.surfaceIds(TfcTerrain.ROCK_IDS));
    }
}
