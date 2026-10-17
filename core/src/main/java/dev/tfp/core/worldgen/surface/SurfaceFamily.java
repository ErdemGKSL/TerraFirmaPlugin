/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the surface builder of each biome)
 */
package dev.tfp.core.worldgen.surface;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.Map;

/**
 * The surface builder family of a TFC biome, for the builders this port has. Each family names the TFC builder of the biome,
 * and {@link #forBiome} maps the biome id to it. Biomes whose TFC builder is not ported (lowlands, rivers, flats, dunes,
 * badlands, shield volcanoes, ice sheets, karst and the rest) fall back to {@link #NORMAL}, the divergence in the ledger.
 */
public enum SurfaceFamily {
    /** {@code NormalSurfaceBuilder.INSTANCE}: the land of most biomes. */
    NORMAL(SurfaceBuilders.NORMAL),
    /** {@code NormalSurfaceBuilder.ROCKY}: mountains and karst. */
    ROCKY(SurfaceBuilders.ROCKY),
    /** {@code SimpleSurfaceBuilder.VOLCANIC_SOIL}: the andisol land of the volcanic biomes. */
    VOLCANIC_SOIL(SurfaceBuilders.VOLCANIC_SOIL),
    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN}: the ocean and its atolls. */
    OCEAN(SurfaceBuilders.OCEAN),
    /** {@code ShoreAndOceanSurfaceBuilder.OCEAN_RIDGE}: the ocean ridge. */
    OCEAN_RIDGE(SurfaceBuilders.OCEAN_RIDGE),
    /** {@code ShoreAndOceanSurfaceBuilder.SANDY}: the sandy beaches. */
    SHORE_SANDY(SurfaceBuilders.SHORE_SANDY),
    /** {@code ShoreAndOceanSurfaceBuilder.SEA_CLIFFS}: the cliffs of the coast. */
    SEA_CLIFFS(SurfaceBuilders.SEA_CLIFFS),
    /** {@code ShoreAndOceanSurfaceBuilder.MOUNTAINS}: the gravel shore of oceanic mountains. */
    MOUNTAIN_SHORE(SurfaceBuilders.MOUNTAIN_SHORE),
    /** {@code ShoreAndOceanSurfaceBuilder.VOLCANIC_MOUNTAINS}: the gravel shore of volcanic islands. */
    VOLCANIC_MOUNTAIN_SHORE(SurfaceBuilders.VOLCANIC_MOUNTAIN_SHORE),
    /** {@code ShoreAndOceanSurfaceBuilder.ROCKY_SHORE}: the bare rock of the rocky coasts and islands. */
    ROCKY_COAST(SurfaceBuilders.ROCKY_COAST);

    private static final Map<String, SurfaceFamily> BY_BIOME = Map.ofEntries(
        Map.entry("OCEAN", OCEAN),
        Map.entry("OCEAN_REEF", OCEAN),
        Map.entry("DEEP_OCEAN", OCEAN),
        Map.entry("DEEP_OCEAN_TRENCH", OCEAN),
        Map.entry("OCEANIC_VOLCANIC_ARC", OCEAN),
        Map.entry("OCEAN_ATOLLS", OCEAN),
        Map.entry("DEEP_OCEAN_ATOLLS", OCEAN),
        Map.entry("OCEAN_RIDGE", OCEAN_RIDGE),
        Map.entry("COLLISIONAL_MOUNTAINS", ROCKY),
        Map.entry("HIGHLANDS", ROCKY),
        Map.entry("MOUNTAINS", ROCKY),
        Map.entry("OLD_MOUNTAINS", ROCKY),
        Map.entry("TOWER_KARST_PLAINS", ROCKY),
        Map.entry("TOWER_KARST_HILLS", ROCKY),
        Map.entry("TOWER_KARST_CANYONS", ROCKY),
        Map.entry("TOWER_KARST_HIGHLANDS", ROCKY),
        Map.entry("TOWER_KARST_LAKE", ROCKY),
        Map.entry("TOWER_KARST_BAY", ROCKY),
        Map.entry("MOUNTAIN_LAKE", ROCKY),
        Map.entry("OLD_MOUNTAIN_LAKE", ROCKY),
        Map.entry("EXTREME_DOLINE_PLATEAU", ROCKY),
        Map.entry("EXTREME_DOLINE_MOUNTAINS", ROCKY),
        Map.entry("GLACIALLY_CARVED_MOUNTAINS", ROCKY),
        Map.entry("GLACIALLY_CARVED_OCEANIC_MOUNTAINS", ROCKY),
        Map.entry("CANYONS", VOLCANIC_SOIL),
        Map.entry("VOLCANIC_ISLAND", VOLCANIC_SOIL),
        Map.entry("DOLINE_CANYONS", VOLCANIC_SOIL),
        Map.entry("VOLCANIC_MOUNTAIN_LAKE", VOLCANIC_SOIL),
        Map.entry("VOLCANIC_MOUNTAINS", VOLCANIC_SOIL),
        Map.entry("GLACIALLY_CARVED_VOLCANIC_MOUNTAINS", VOLCANIC_SOIL),
        Map.entry("GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS", VOLCANIC_SOIL),
        Map.entry("OCEANIC_MOUNTAINS", MOUNTAIN_SHORE),
        Map.entry("OCEANIC_MOUNTAIN_LAKE", MOUNTAIN_SHORE),
        Map.entry("VOLCANIC_OCEANIC_MOUNTAINS", VOLCANIC_MOUNTAIN_SHORE),
        Map.entry("VOLCANIC_MOUNTAIN_ISLANDS", VOLCANIC_MOUNTAIN_SHORE),
        Map.entry("VOLCANIC_OCEANIC_MOUNTAIN_LAKE", VOLCANIC_MOUNTAIN_SHORE),
        Map.entry("SHORE", SHORE_SANDY),
        Map.entry("TIDAL_FLATS", SHORE_SANDY),
        Map.entry("SETBACK_CLIFFS", SHORE_SANDY),
        Map.entry("COASTAL_DUNES", SHORE_SANDY),
        Map.entry("SEA_STACKS", SEA_CLIFFS),
        Map.entry("TERRACE_UPPER", SEA_CLIFFS),
        Map.entry("TERRACE_LOWER", SEA_CLIFFS),
        Map.entry("EMBAYMENTS", SEA_CLIFFS),
        Map.entry("GUANO_ISLAND", ROCKY_COAST),
        Map.entry("ROCKY_SHORES", ROCKY_COAST)
    );

    private final SurfaceBuilder builder;

    SurfaceFamily(SurfaceBuilder builder) {
        this.builder = builder;
    }

    /** @return The surface builder of the family. Immutable and thread-safe. */
    public SurfaceBuilder builder() {
        return builder;
    }

    /**
     * @param biomeId a TFC biome id, {@code 0} to {@code TfcBiomes.COUNT - 1}
     * @return The family of the biome, {@link #NORMAL} where the TFC builder is not ported.
     */
    public static SurfaceFamily forBiome(int biomeId) {
        return BY_BIOME.getOrDefault(TfcBiomes.name(biomeId), NORMAL);
    }
}
