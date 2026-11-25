/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536 (isValid, through ClimateFilter)
 *       src/main/java/net/dries007/tfc/world/placement/IntertidalPlacement.java @ c3b4d9536 (isValid)
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/forest_plants.json @ c3b4d9536
 *       src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/guano_islands.json @ c3b4d9536
 *       src/main/resources/data/tfc/worldgen/{placed_feature,configured_feature}/{sticks_forest,pinecone,salt_lick,humus,
 *       rotten_flesh,bone,extra_island_guano}{,_patch}.json, atoll_guano.json @ c3b4d9536
 *       and Minecraft 1.21's RarityFilter, CountPlacement, InSquarePlacement, RandomPatchFeature, HeightmapPlacement
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * PAR-WORLD-21: TFC's forest floor patches ({@code #tfc:feature/forest_plants}, inside {@code #tfc:feature/land_plants}) and
 * the guano of the islands ({@code #tfc:feature/guano_islands}, {@code tfc:atoll_guano}), with TFC's draws in TFC's order.
 *
 * <p>Each patch: {@code rarity_filter} (or {@code count}), {@code in_square} (x, then z), {@code tfc:climate} at the origin
 * (whose y is the world's lowest, vanilla's decoration origin; the forest bounds read {@link GroundView#forestDensity}, so
 * nothing is placed where the view does not know it), then per try the {@code random_patch} offsets (x, y, z) and the placed
 * groundcover: {@code WORLD_SURFACE_WG} height, {@code tfc:replaceable} (air or a fluid; a cover placed earlier in the patch
 * is neither) and {@code would_survive} (a sturdy floor; the top block of the terrain is taken as one, as
 * {@code ShoreGroundcover} does). The placed features draw nothing. Thread-safe; the outputs are the caller's.
 */
public final class ForestFloor {
    /** One placed groundcover: the block to place ({@code tfp:groundcover/<type>}, no fluid) at a position. */
    public record Cover(int x, int y, int z, String block) {
        public boolean inChunk(int chunkX, int chunkZ) {
            return x >> 4 == chunkX && z >> 4 == chunkZ;
        }
    }

    /**
     * A random patch of one groundcover.
     *
     * @param count  the {@code count} placement of a counted patch, or 0 for a {@code rarity_filter} patch
     * @param minTide the {@code tfc:intertidal} band of the placed cover, or {@link Integer#MIN_VALUE}
     * @param onStone whether the placed cover needs {@code tfc:on_top} of {@code #tfc:creeping_stone_plantable_on}
     */
    public record Patch(String id, String block, int rarity, int count, int tries, int xzSpread, ClimateFilter climate,
                        int minTide, int maxTide, boolean onStone) {
        static Patch forest(String id, String type, int rarity, int tries, int xzSpread, ClimateFilter climate) {
            return new Patch(id, "tfp:groundcover/" + type, rarity, 0, tries, xzSpread, climate, NO_TIDE, NO_TIDE, false);
        }
    }

    private static final int NO_TIDE = Integer.MIN_VALUE;

    /** {@code tfc:climate} with {@code min_forest} 3 and the codec's other defaults. */
    private static ClimateFilter forest(float minTemp, float maxTemp, float minGroundwater, float maxGroundwater, boolean fuzzy) {
        return new ClimateFilter(minTemp, maxTemp, minGroundwater, maxGroundwater, -1f, 1f, false, 3, 4, -64, 320, fuzzy, false);
    }

    /** The patches of {@code feature/forest_plants}, in tag order. */
    public static final List<Patch> FOREST_PLANTS = List.of(
        Patch.forest("tfc:sticks_forest_patch", "stick", 3, 20, 15, forest(-20, 50, 70, 500, false)),
        Patch.forest("tfc:pinecone_patch", "pinecone", 5, 10, 15, forest(-14, 0, 60, 320, false)),
        Patch.forest("tfc:salt_lick_patch", "salt_lick", 110, 100, 5, forest(5, 33, 100, 500, false)),
        Patch.forest("tfc:humus_patch", "humus", 5, 100, 5, forest(8, 20, 180, 420, true)),
        Patch.forest("tfc:rotten_flesh_patch", "rotten_flesh", 100, 10, 10, ClimateFilter.of(-30, 30, 0, 400)),
        Patch.forest("tfc:bone_patch", "bone", 100, 10, 10, ClimateFilter.of(-30, 30, 0, 400)));

    /** {@code tfc:extra_island_guano_patch} ({@code feature/guano_islands}): ten counted patches of 15 tries spreading 3. */
    public static final Patch EXTRA_ISLAND_GUANO = new Patch("tfc:extra_island_guano_patch", "tfp:groundcover/guano", 0, 10, 15, 3,
        ClimateFilter.of(-30, 15, 0, 500), 1, 32, true);

    /** {@code tfc:atoll_guano}: one guano at the chunk's corner column (heightmap, replaceable, would_survive; no draws). */
    public static final String ATOLL_GUANO = "tfc:atoll_guano";

    private final List<FeatureRandom> forestRandoms;
    private final FeatureRandom guanoRandom;

    public ForestFloor(long levelSeed) {
        forestRandoms = FOREST_PLANTS.stream().map(p -> new FeatureRandom(levelSeed, p.id())).toList();
        guanoRandom = new FeatureRandom(levelSeed, EXTRA_ISLAND_GUANO.id());
    }

    /** The forest floor patches of chunk {@code (chunkX, chunkZ)}, in tag order. */
    public void forestPlants(int chunkX, int chunkZ, GroundView view, Consumer<Cover> out) {
        for (int i = 0; i < FOREST_PLANTS.size(); i++) {
            patch(forestRandoms.get(i).chunk(chunkX, chunkZ), FOREST_PLANTS.get(i), chunkX << 4, chunkZ << 4, view, out);
        }
    }

    /** The guano of {@code feature/guano_islands} for chunk {@code (chunkX, chunkZ)}. */
    public void guanoIslands(int chunkX, int chunkZ, GroundView view, Consumer<Cover> out) {
        patch(guanoRandom.chunk(chunkX, chunkZ), EXTRA_ISLAND_GUANO, chunkX << 4, chunkZ << 4, view, out);
    }

    /** {@code tfc:atoll_guano} for chunk {@code (chunkX, chunkZ)}: the chunk's corner column, no draws. */
    public static void atollGuano(int chunkX, int chunkZ, GroundView view, Consumer<Cover> out) {
        final int x = chunkX << 4;
        final int z = chunkZ << 4;
        final int y = view.worldSurface(x, z);
        if (y > view.minY()) {
            out.accept(new Cover(x, y, z, "tfp:groundcover/guano"));
        }
    }

    /** One chunk's patch with TFC's draws in TFC's order (see the class comment). */
    public static void patch(XoroshiroRandomSource random, Patch patch, int blockX, int blockZ, GroundView view, Consumer<Cover> out) {
        if (patch.count() == 0) {
            if (!(random.nextFloat() < 1f / patch.rarity())) {
                return;
            }
            origin(random, patch, blockX, blockZ, view, out);
        } else {
            for (int c = 0; c < patch.count(); c++) {
                origin(random, patch, blockX, blockZ, view, out);
            }
        }
    }

    private static void origin(XoroshiroRandomSource random, Patch patch, int blockX, int blockZ, GroundView view,
            Consumer<Cover> out) {
        final int originX = blockX + random.nextInt(16);
        final int originZ = blockZ + random.nextInt(16);
        final int forest = patch.climate().hasForestBounds() ? view.forestDensity(originX, originZ) : -1;
        if (patch.climate().hasForestBounds() && forest < 0) {
            return; // the view does not know the forest (PAR-WORLD-09): a forest-bounded patch does not place
        }
        if (!patch.climate().isValid(view, originX, view.minY(), originZ, random, forest)) {
            return;
        }
        final Set<Long> placed = new HashSet<>();
        final int xz = patch.xzSpread() + 1;
        for (int i = 0; i < patch.tries(); i++) {
            final int x = originX + random.nextInt(xz) - random.nextInt(xz);
            random.nextInt(2);
            random.nextInt(2);
            final int z = originZ + random.nextInt(xz) - random.nextInt(xz);
            final int y = view.worldSurface(x, z);
            if (y <= view.minY()) {
                continue;
            }
            final long key = ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
            if (placed.contains(key)) {
                continue;
            }
            if (patch.minTide() != NO_TIDE) {
                final int heightDiff = (int) (y - view.tideLevel(x, z));
                if (heightDiff < patch.minTide() || heightDiff > patch.maxTide()) {
                    continue;
                }
            }
            if (patch.onStone() && !view.topBlockStone(x, z)) {
                continue;
            }
            placed.add(key);
            out.accept(new Cover(x, y, z, patch.block()));
        }
    }
}
