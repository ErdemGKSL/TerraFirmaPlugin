/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/tree/ForestFeature.java @ c3b4d9536 (place up to the tree loop, placeTree,
 * getTrees, getTree), with the data of src/main/resources/data/tfc/worldgen/configured_feature/forest.json,
 * placed_feature/forest.json, the tag data/tfc/tags/worldgen/configured_feature/forest_trees.json (entry order) and the
 * biomes whose data/tfc/tags/worldgen/placed_feature/in_biome/large_features/*.json list tfc:forest
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * TFC's forest feature ({@code tfc:forest}), the trees part: once per chunk, the chunk's {@link ForestType} gives the chance
 * and the number of trees; each tree picks a random column, the forest entries whose climate fits the column (sorted by their
 * distance from the climate's middle, trimmed to the forest type's species count, a few skipped for alternating forests), one
 * of them with a 0.6 chance to move to the next, and then the entry's krummholz, old growth, dead or normal tree, which TFC's
 * tree features place. Pure and thread-safe.
 *
 * <p>The soil discs of the entries draw nothing (they are {@code tfc:soil_forest_area} features), so the tree draws are TFC's.
 * The bushes, groundcover, leaf piles and fallen trees that follow the trees are not decided (see the ledger).
 */
public final class Forest {
    /** The placed feature id, which seeds the forest random. */
    public static final String FEATURE = "tfc:forest";
    /** {@code TFCChunkGenerator.SEA_LEVEL_Y}. */
    static final int SEA_LEVEL_Y = 63;

    /** The biomes whose large features tag does not list {@code tfc:forest}: the oceans, shores, flats and ice sheets. */
    static final Set<String> TREELESS_BIOMES = Set.of("coastal_dunes", "deep_ocean", "deep_ocean_trench", "dune_sea",
        "guano_island", "ice_sheet", "ice_sheet_mountains", "ice_sheet_oceanic", "ice_sheet_oceanic_mountains",
        "ice_sheet_shield_volcano", "ice_sheet_tuyas", "ice_sheet_volcanic_mountains", "ice_sheet_volcanic_oceanic_mountains",
        "mud_flats", "ocean", "ocean_reef", "ocean_ridge", "old_shield_volcano_shore", "rocky_shores", "salt_flats", "salt_marsh",
        "shield_volcano_shore", "shore", "subglacial_lake", "tidal_flats");

    private static final boolean[] DECORATED = decorated();

    private final FeatureRandom random;
    private final List<ForestEntry> entries;

    /**
     * @param levelSeed the world seed
     * @param entries   the forest entries of {@code #tfc:forest_trees}, in tag order
     */
    public Forest(long levelSeed, List<ForestEntry> entries) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
        this.entries = List.copyOf(entries);
    }

    public List<ForestEntry> entries() {
        return entries;
    }

    /** Whether a biome's feature list holds the forest. */
    public static boolean decorates(int biome) {
        return biome >= 0 && biome < DECORATED.length && DECORATED[biome];
    }

    /**
     * Every block the trees of the 3 x 3 chunks around {@code (chunkX, chunkZ)} set inside it, in chunk order (x outer) and then
     * TFC's order. A tree's blocks stay inside its root chunk +-16 blocks (TFC's template clip, which the forest data never
     * reaches: {@code TreePropertyTest}), so no other chunk's tree reaches in. Each chunk's forest is decided alone (its trees see
     * the terrain and each other, not the neighbours' trees; see the ledger).
     */
    public void place(int chunkX, int chunkZ, TreeView view, Consumer<Placed> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int cx = chunkX + dx;
                final int cz = chunkZ + dz;
                for (Tree tree : chunk(cx, cz, view)) {
                    for (TreeBlock block : tree.blocks()) {
                        if (tree.inChunk(block, chunkX, chunkZ)) {
                            out.accept(new Placed(tree, block));
                        }
                    }
                }
            }
        }
    }

    /** A block of a tree, with the tree it belongs to. */
    public record Placed(Tree tree, TreeBlock block) {
        public int x() {
            return tree.x() + block.dx();
        }

        public int y() {
            return tree.y() + block.dy();
        }

        public int z() {
            return tree.z() + block.dz();
        }
    }

    /**
     * The trees of the forest of chunk {@code (chunkX, chunkZ)}, in placement order: empty when the biome at the chunk's centre
     * has no forest (ledger), the forest type's chance fails, or no entry fits.
     */
    public List<Tree> chunk(int chunkX, int chunkZ, TreeView view) {
        if (!decorates(view.biome((chunkX << 4) + 8, (chunkZ << 4) + 8))) {
            return List.of();
        }
        return trees(random.chunk(chunkX, chunkZ), chunkX << 4, chunkZ << 4, view);
    }

    /**
     * {@code ForestFeature.place} up to the tree loop, with TFC's draws in TFC's order: the chance float, the tree count and
     * the bush count, then each tree.
     *
     * @param blockX the chunk's lowest block x
     * @param blockZ the chunk's lowest block z
     */
    public List<Tree> trees(XoroshiroRandomSource random, int blockX, int blockZ, TreeView view) {
        final ForestType forestType = view.forestType(blockX >> 4, blockZ >> 4);
        if (random.nextFloat() > forestType.perChunkChance()) {
            return List.of();
        }
        final int treeCount = forestType.sampleTrees(random);
        forestType.sampleBushes(random);
        final TreeWorld world = new TreeWorld(view);
        final List<Tree> out = new ArrayList<>();
        for (int i = 0; i < treeCount; i++) {
            placeTree(world, random, blockX, blockZ, forestType, out);
        }
        return out;
    }

    /** {@code ForestFeature.placeTree}. */
    private void placeTree(TreeWorld world, XoroshiroRandomSource random, int chunkX, int chunkZ, ForestType typeConfig, List<Tree> out) {
        final TreeView view = world.view();
        final int x = chunkX + random.nextInt(16);
        final int z = chunkZ + random.nextInt(16);
        int y = view.oceanFloor(x, z);
        final ForestEntry entry = getTree(view, random, x, y, z, typeConfig);
        if (entry == null) {
            return;
        }
        if (entry.floating()) {
            if (y < SEA_LEVEL_Y - 4) {
                return;
            }
            y = view.worldSurface(x, z) + random.nextInt(2);
        }
        final TreeFeature feature;
        final Tree.Variant variant;
        final int oldChance = entry.oldGrowthChance();
        final int deadChance = entry.deadChance();
        final float blockTemp = ClimateFilter.adjustAvgTempForElev(y, view.averageTemperature(x, z));
        final float treeMinTemp = entry.climate().minTemp();
        final float krumChance = blockTemp > treeMinTemp + 5f ? 0f : Mth.clampedMap(blockTemp, treeMinTemp + 2.5f, treeMinTemp + 5f, 1f, 0f);
        if (entry.krummholz().isPresent() && random.nextFloat() < krumChance) {
            feature = entry.krummholz().get();
            variant = Tree.Variant.KRUMMHOLZ;
        } else if (typeConfig.isPrimary() && oldChance > 0 && random.nextInt(oldChance) == 0) {
            feature = entry.oldGrowthFeature();
            variant = Tree.Variant.OLD_GROWTH;
        } else if (deadChance > 0 && (random.nextInt(deadChance) == 0 || typeConfig.isDead())) {
            feature = entry.deadTree();
            variant = Tree.Variant.DEAD;
        } else {
            final int spoilerChance = entry.spoilerOldGrowthChance();
            if (spoilerChance > 0 && random.nextInt(spoilerChance) == 0) {
                feature = entry.oldGrowthFeature();
                variant = Tree.Variant.OLD_GROWTH;
            } else {
                feature = entry.normalTree();
                variant = Tree.Variant.NORMAL;
            }
        }
        world.begin(x, y, z, feature.wood());
        final boolean placed = TreePlacer.place(feature, world, random, x, y, z);
        final List<TreeBlock> blocks = world.end();
        if (placed) {
            out.add(new Tree(x, y, z, feature.wood(), variant, blocks));
        }
    }

    /**
     * {@code ForestFeature.getTrees}: the entries valid at the position, sorted by {@code distanceFromMean} (a stable sort, so
     * ties keep tag order). The rain variance is not flipped for the southern hemisphere (ledger).
     */
    List<ForestEntry> getTrees(TreeView view, int x, int y, int z) {
        final float rainVariance = view.rainVariance(x, z);
        final float groundwater = view.groundwater(x, z);
        final int elevation = y;
        final float averageTemperature = ClimateFilter.adjustAvgTempForElev(elevation, view.averageTemperature(x, z));
        final List<ForestEntry> out = new ArrayList<>();
        for (ForestEntry entry : entries) {
            if (entry.isValid(averageTemperature, groundwater, rainVariance, elevation)) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingDouble(entry -> entry.distanceFromMean(averageTemperature, groundwater, rainVariance, elevation)));
        return out;
    }

    /** {@code ForestFeature.getTree}. */
    ForestEntry getTree(TreeView view, XoroshiroRandomSource random, int x, int y, int z, ForestType type) {
        final List<ForestEntry> entries = getTrees(view, x, y, z);
        if (entries.isEmpty()) {
            return null;
        }
        if (entries.size() == 1) {
            return entries.getFirst();
        }
        final int maxSize = type.maxTreeTypes();
        final int originalSize = entries.size();
        for (int i = maxSize; i < originalSize; i++) {
            entries.removeLast();
        }
        int alternate = type.alternateSize();
        while (entries.size() > 1 && alternate > 0) {
            entries.removeFirst();
            alternate--;
        }
        int index = 0;
        while (index < entries.size() - 1 && random.nextFloat() < 0.6f) {
            index++;
        }
        return entries.get(index);
    }

    private static boolean[] decorated() {
        final boolean[] out = new boolean[TfcBiomes.COUNT];
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            out[id] = !TREELESS_BIOMES.contains(TfcBiomes.name(id).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
