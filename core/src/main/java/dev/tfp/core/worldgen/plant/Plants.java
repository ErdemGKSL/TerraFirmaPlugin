/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/biome/*.json @ c3b4d9536 (the decoration steps: large features, then surface
 * decoration), with the biome feature lists data/tfc/tags/worldgen/placed_feature/in_biome/{large_features,
 * surface_decoration}/*.json and the feature tags they name (feature/land_plants, surface_grasses, ocean_plants, crops,
 * corals, ocean_decorations), and src/generated/resources/data/tfc/tags/block/{single_block_replaceable,can_be_snow_piled}.json
 */
package dev.tfp.core.worldgen.plant;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.tree.TreeView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The plants, wild crops and corals of one world (M2-GEN-13): for each biome, the supported features of its large feature
 * step (the corals) and of its surface decoration step (plants, grasses, wild crops, water plants), in its tag order. A chunk
 * runs the list of the biome at its centre, each feature with its own {@link FeatureRandom} at the chunk; its features see
 * the blocks its earlier features placed. Immutable after construction and thread-safe; each call's state is its own.
 */
public final class Plants {
    /** The decoration steps that hold plant features, in TFC's step order. */
    public enum Step {
        /** {@code large_features}: the corals (the forest runs here too, in {@code tree.Forest}). */
        LARGE_FEATURES(PlantData.LARGE_FEATURES),
        /** {@code surface_decoration}: the plants, grasses, wild crops and water plants. */
        SURFACE_DECORATION(PlantData.SURFACE_DECORATION);

        private final String dir;

        Step(String dir) {
            this.dir = dir;
        }
    }

    private final Map<Step, List<List<PlantFeature.Placed>>> features = new HashMap<>();
    private final Map<String, FeatureRandom> randoms = new HashMap<>();
    private final Set<String> singleBlockReplaceable;
    private final Set<String> snowPileable;
    private final Map<String, String> skipped;

    /**
     * @param levelSeed the world seed
     * @param data      the exported TFC data
     */
    public Plants(long levelSeed, PlantData data) {
        for (Step step : Step.values()) {
            final List<List<PlantFeature.Placed>> byBiome = new ArrayList<>(TfcBiomes.COUNT);
            for (int id = 0; id < TfcBiomes.COUNT; id++) {
                final List<PlantFeature.Placed> list = data.biomeFeatures(step.dir, TfcBiomes.name(id).toLowerCase(Locale.ROOT));
                byBiome.add(list);
                for (PlantFeature.Placed p : list) {
                    randoms.computeIfAbsent(p.id(), k -> new FeatureRandom(levelSeed, tfcId(k)));
                }
            }
            features.put(step, List.copyOf(byBiome));
        }
        this.singleBlockReplaceable = Set.copyOf(data.blockTag(PlantState.NS + "single_block_replaceable"));
        this.snowPileable = Set.copyOf(data.blockTag(PlantState.NS + "can_be_snow_piled"));
        this.skipped = Map.copyOf(new LinkedHashMap<>(data.skipped()));
    }

    /** The TFC id of a feature ({@code tfc:plant/allium_patch}), which seeds its random as the other features' ids do. */
    static String tfcId(String id) {
        return id.startsWith(PlantState.NS) ? "tfc:" + id.substring(PlantState.NS.length()) : id;
    }

    /** The supported features of a biome's step, in order. */
    public List<PlantFeature.Placed> features(Step step, int biome) {
        return biome < 0 || biome >= TfcBiomes.COUNT ? List.of() : features.get(step).get(biome);
    }

    /** The features of the biomes' lists that are left out, with the reason. */
    public Map<String, String> skipped() {
        return skipped;
    }

    /**
     * The blocks of one step inside chunk {@code (chunkX, chunkZ)}: the features of this chunk and its 8 neighbours are run
     * (a patch reaches up to 30 blocks from its chunk's corner), and only their blocks inside this chunk are given, each
     * position once with the state its chunk's features left there, in the order they were first placed, chunk by chunk.
     */
    public void place(int chunkX, int chunkZ, TreeView view, Step step, Consumer<PlantBlock> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int cx = chunkX + dx;
                final int cz = chunkZ + dz;
                for (PlantBlock block : decorate(cx, cz, view, step)) {
                    if (block.inChunk(chunkX, chunkZ)) {
                        out.accept(block);
                    }
                }
            }
        }
    }

    /** Every block the step's features of chunk {@code (chunkX, chunkZ)} place, inside the chunk or not. */
    public List<PlantBlock> decorate(int chunkX, int chunkZ, TreeView view, Step step) {
        final List<PlantFeature.Placed> list = features(step, view.biome((chunkX << 4) + 8, (chunkZ << 4) + 8));
        if (list.isEmpty()) {
            return List.of();
        }
        final PlantLevel level = new PlantLevel(view, singleBlockReplaceable, snowPileable);
        for (PlantFeature.Placed feature : list) {
            PlantPlacer.place(feature, level, randoms.get(feature.id()).chunk(chunkX, chunkZ), chunkX << 4, view.minY(), chunkZ << 4);
        }
        return List.copyOf(level.blocks());
    }

    /** Runs one feature in a fresh level, for tests: the blocks it placed, each position once, in first-placed order. */
    public static List<PlantBlock> run(PlantFeature.Placed feature, TreeView view, Set<String> singleBlockReplaceable,
                                       Set<String> snowPileable, dev.tfp.core.worldgen.random.XoroshiroRandomSource random,
                                       int x, int y, int z) {
        final PlantLevel level = new PlantLevel(view, singleBlockReplaceable, snowPileable);
        PlantPlacer.place(feature, level, random, x, y, z);
        return List.copyOf(level.blocks());
    }
}
