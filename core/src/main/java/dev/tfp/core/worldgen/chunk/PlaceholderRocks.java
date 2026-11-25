/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/RegionChunkDataGenerator.java @ c3b4d9536 (generateRock: the rock of
 * a point and layer is rockLayerSettings.sampleAtLayer), PAR-WORLD-23
 */
package dev.tfp.core.worldgen.chunk;

import dev.tfp.core.worldgen.rock.RockLayerSampler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The raw rock of a region rock id and a layer, by index (PAR-WORLD-23): TFC's rock layer walk of the overworld preset
 * ({@link RockLayerSampler#PRESET}, {@code RockLayerSettings.sampleAtLayer}), as an index of {@link #names()}. The names are
 * the raw rock ids {@code tfp:rock/raw/<name>} of the content, in a fixed order the palettes rely on. The class keeps its
 * name and API for its callers (volcanoes, groundcover, terrain).
 */
public final class PlaceholderRocks {
    private static final String[] NAMES = {
        "shale", "claystone", "limestone", "conglomerate", "dolomite", "chert", "chalk", "tuff",
        "rhyolite", "basalt", "andesite", "dacite",
        "granite", "diorite", "gabbro",
        "quartzite", "slate", "phyllite", "schist", "gneiss", "marble",
    };
    private static final Map<String, Integer> INDEX = indexByRockId();

    private PlaceholderRocks() {}

    private static Map<String, Integer> indexByRockId() {
        final Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < NAMES.length; i++) {
            map.put("tfc:" + NAMES[i], i);
        }
        for (String rock : RockLayerSampler.PRESET.rocks()) {
            if (!map.containsKey(rock)) {
                throw new AssertionError("preset rock without a raw rock index: " + rock);
            }
        }
        return Map.copyOf(map);
    }

    /** @return The 21 raw rock names, in index order. */
    public static List<String> names() {
        return List.of(NAMES);
    }

    /** @return The number of raw rocks: the size of {@link #names()}. */
    public static int count() {
        return NAMES.length;
    }

    /** @return The index in {@link #names()} of a rock id ({@code tfc:granite}). */
    public static int indexOf(String rockId) {
        final Integer i = INDEX.get(rockId);
        if (i == null) {
            throw new IllegalArgumentException("unknown rock " + rockId);
        }
        return i;
    }

    /**
     * @param index an index of {@link #names()}
     * @return Whether the rock is mafic: TFC's rock settings mark basalt and gabbro ({@code RockSettings.mafic}; checked
     *         against the exported data by {@code RockSettingsTest}). Used for the beach sand colour.
     */
    public static boolean isMafic(int index) {
        final String name = NAMES[index];
        return name.equals("basalt") || name.equals("gabbro");
    }

    /**
     * The index in {@link #names()} of the rock at a region rock id and a rock layer: TFC's {@code sampleAtLayer} of the
     * overworld preset.
     *
     * @param rock  The region rock id ({@code Region.Point.rock}).
     * @param layer The rock layer, 0 at the surface ({@code ChunkDataGenerator.RockSample.layer}).
     */
    public static int index(int rock, int layer) {
        return index(RockLayerSampler.PRESET, rock, layer);
    }

    /**
     * PAR-WORLD-52: the index in {@link #names()} of the rock at a region rock id and a rock layer in a world's own rock layer
     * walk ({@code RockLayerSampler.decode(settings.rockLayerSettings())}). The sampler's rocks must have passed
     * {@link #requireKnown}.
     */
    public static int index(RockLayerSampler rockLayers, int rock, int layer) {
        return INDEX.get(rockLayers.sampleAtLayer(rock, layer));
    }

    /**
     * PAR-WORLD-52: refuses a rock layer config that names a rock id without exported rock settings (only TFC's 21 raw rocks
     * have blocks and {@code RockSettings}), so a world fails at load instead of at its first sample.
     *
     * @return {@code rockLayers}
     * @throws IllegalArgumentException naming the first unknown rock id
     */
    public static RockLayerSampler requireKnown(RockLayerSampler rockLayers) {
        for (String rock : rockLayers.rocks()) {
            if (!INDEX.containsKey(rock)) {
                throw new IllegalArgumentException("rock_layer_settings: no rock settings for rock " + rock + " (known: "
                    + names().stream().map(n -> "tfc:" + n).toList() + ")");
            }
        }
        return rockLayers;
    }
}
