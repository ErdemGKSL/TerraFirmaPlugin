/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/large_features/*.json @ c3b4d9536 (the biomes that
 * list #tfc:feature/cinder_cones, tfc:random_active_hot_spring, tfc:random_empty_hot_spring and tfc:lava_hot_spring), with
 * data/tfc/tags/worldgen/placed_feature/feature/cinder_cones.json, the placed and configured features
 * data/tfc/worldgen/{placed_feature,configured_feature}/{volcano_caldera,random_volcano_fissure,topaz_volcano_fissure,
 * diamond_volcano_fissure,volcano_fissure,random_active_hot_spring,random_empty_hot_spring,lava_hot_spring}.json,
 * src/main/java/net/dries007/tfc/world/placement/CenterOrDistanceToPlacement.java (center = true) and the vanilla 1.21
 * placement steps they name (rarity_filter, in_square, heightmap, simple_random_selector)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.function.Consumer;

/**
 * The volcano and hot spring features of one world (M2-GEN-14), in TFC's large feature order: the cinder cones' caldera
 * (lava in the crater) and volcano fissure (a lava tube in basalt from the cone's center), then the hot springs. A chunk
 * runs the features of the biome at its centre, each with its own {@link FeatureRandom} at the chunk, in one level that sees
 * the blocks its earlier features placed. The cones' heights are {@link #cones()}. Immutable and thread-safe.
 * <p>
 * Not ported (ledger): the cones' rivulets, pumice and obsidian patches, the {@code #tfc:feature/volcanic} lava surface
 * springs, the other centered features (tuyas, tuff rings, atolls, stratovolcanoes) and their fissures.
 */
public final class Volcanoes {
    /** The fissure configs of {@code random_volcano_fissure}, in its list order: 3 topaz, 1 diamond, 4 plain. */
    private static final Fissure.Decoration[] VOLCANO_FISSURES = {
        new Fissure.Decoration(Gem.TOPAZ, 3, 3, 6), new Fissure.Decoration(Gem.TOPAZ, 3, 3, 6),
        new Fissure.Decoration(Gem.TOPAZ, 3, 3, 6), new Fissure.Decoration(Gem.DIAMOND, 3, 3, 6), null, null, null, null};
    /** {@code random_active_hot_spring}, in its list order. */
    private static final HotSpring.Config[] ACTIVE_SPRINGS = {
        HotSpring.Config.SAPPHIRE_EMPTY, HotSpring.Config.EMERALD_EMPTY, HotSpring.Config.EMPTY, HotSpring.Config.EMPTY,
        HotSpring.Config.SAPPHIRE, HotSpring.Config.SAPPHIRE, HotSpring.Config.SAPPHIRE,
        HotSpring.Config.EMERALD, HotSpring.Config.EMERALD, HotSpring.Config.EMERALD,
        HotSpring.Config.HOT_SPRING, HotSpring.Config.HOT_SPRING, HotSpring.Config.HOT_SPRING,
        HotSpring.Config.HOT_SPRING, HotSpring.Config.HOT_SPRING, HotSpring.Config.HOT_SPRING};
    /** {@code random_empty_hot_spring}, in its list order. */
    private static final HotSpring.Config[] EMPTY_SPRINGS = {
        HotSpring.Config.SAPPHIRE_EMPTY, HotSpring.Config.EMERALD_EMPTY, HotSpring.Config.EMPTY, HotSpring.Config.EMPTY};
    /** {@code lava_hot_spring}'s {@code tfc:climate} placement: {@code max_elevation} 40, every other field its default. */
    private static final ClimateFilter LAVA_SPRING_CLIMATE = new ClimateFilter(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1f, 1f, false, -64, 40, false, false);

    /** The hot spring list a biome's large features hold. */
    enum Springs {
        NONE, ACTIVE, EMPTY, LAVA
    }

    private final CinderCones cones;
    private final FeatureRandom caldera;
    private final FeatureRandom fissure;
    private final FeatureRandom activeSprings;
    private final FeatureRandom emptySprings;
    private final FeatureRandom lavaSpring;

    /** @param levelSeed the world seed */
    public Volcanoes(long levelSeed) {
        this.cones = new CinderCones(levelSeed);
        this.caldera = new FeatureRandom(levelSeed, "tfc:volcano_caldera");
        this.fissure = new FeatureRandom(levelSeed, "tfc:random_volcano_fissure");
        this.activeSprings = new FeatureRandom(levelSeed, "tfc:random_active_hot_spring");
        this.emptySprings = new FeatureRandom(levelSeed, "tfc:random_empty_hot_spring");
        this.lavaSpring = new FeatureRandom(levelSeed, "tfc:lava_hot_spring");
    }

    /** The cinder cones of this world, which shape the surface heights. */
    public CinderCones cones() {
        return cones;
    }

    /** Whether a biome's large features hold {@code #tfc:feature/cinder_cones}. */
    static boolean hasConeFeatures(int biome) {
        return CinderCones.hasCinderCones(biome);
    }

    /** The hot spring feature a biome's large features hold. */
    static Springs springs(int biome) {
        if (biome == TfcBiomes.CANYONS || biome == TfcBiomes.DORMANT_SHIELD_VOLCANO || biome == TfcBiomes.OLD_MOUNTAINS
            || biome == TfcBiomes.VOLCANIC_MOUNTAINS) {
            return Springs.ACTIVE;
        }
        if (biome == TfcBiomes.EXTINCT_SHIELD_VOLCANO || biome == TfcBiomes.HIGHLANDS || biome == TfcBiomes.LOW_CANYONS
            || biome == TfcBiomes.PLATEAU || biome == TfcBiomes.PLATEAU_WIDE || biome == TfcBiomes.ROCKY_PLATEAU) {
            return Springs.EMPTY;
        }
        if (biome == TfcBiomes.DEEP_OCEAN_TRENCH) {
            return Springs.LAVA;
        }
        return Springs.NONE;
    }

    /**
     * The blocks inside chunk {@code (chunkX, chunkZ)}: the features of this chunk and its 8 neighbours are run (a hot spring
     * and its fissures reach 14 blocks from their origin, a caldera 14 blocks from its chunk), and only their blocks inside
     * this chunk are given, chunk by chunk, each position with the block its chunk's features left there.
     */
    public void place(int chunkX, int chunkZ, VolcanoView view, Consumer<VolcanoBlock> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (VolcanoBlock block : decorate(chunkX + dx, chunkZ + dz, view)) {
                    if (block.inChunk(chunkX, chunkZ)) {
                        out.accept(block);
                    }
                }
            }
        }
    }

    /** Every block the volcano and hot spring features of chunk {@code (chunkX, chunkZ)} place, inside the chunk or not. */
    public List<VolcanoBlock> decorate(int chunkX, int chunkZ, VolcanoView view) {
        final int x0 = chunkX << 4;
        final int z0 = chunkZ << 4;
        final int biome = view.biome(x0 + 8, z0 + 8);
        final Springs springs = springs(biome);
        if (!hasConeFeatures(biome) && springs == Springs.NONE) {
            return List.of();
        }
        final VolcanoLevel level = new VolcanoLevel(view);
        if (hasConeFeatures(biome)) {
            final long center = volcanoCenter(chunkX, chunkZ, view);
            // volcano_caldera: placement tfc:volcano (center), heightmap WORLD_SURFACE_WG; no draws
            if (center != Long.MIN_VALUE) {
                final int cx = CinderCones.unpackX(center);
                final int cz = CinderCones.unpackZ(center);
                Caldera.place(level, cx, view.worldSurface(cx, cz), cz);
            }
            // random_volcano_fissure: the selector draws first, then the chosen fissure's volcano placement
            final XoroshiroRandomSource random = fissure.chunk(chunkX, chunkZ);
            final Fissure.Decoration decoration = VOLCANO_FISSURES[random.nextInt(VOLCANO_FISSURES.length)];
            if (center != Long.MIN_VALUE) {
                final int cx = CinderCones.unpackX(center);
                final int cz = CinderCones.unpackZ(center);
                placeVolcanoFissure(level, random, cx, view.worldSurface(cx, cz), cz, decoration);
            }
        }
        switch (springs) {
            case ACTIVE -> randomSpring(level, activeSprings.chunk(chunkX, chunkZ), x0, z0, 50, ACTIVE_SPRINGS);
            case EMPTY -> randomSpring(level, emptySprings.chunk(chunkX, chunkZ), x0, z0, 70, EMPTY_SPRINGS);
            case LAVA -> lavaSpring(level, lavaSpring.chunk(chunkX, chunkZ), x0, z0);
            case NONE -> { }
        }
        return level.blocks();
    }

    /**
     * {@code tfc:volcano} with {@code center: true} at the chunk's origin: the center of the cone whose cell holds the origin,
     * when the origin's biome has cones, the center is inside this chunk and the center's biome has cones too. Packed by
     * {@link CinderCones#pack}, or {@link Long#MIN_VALUE}.
     */
    public long volcanoCenter(int chunkX, int chunkZ, VolcanoView view) {
        final int x0 = chunkX << 4;
        final int z0 = chunkZ << 4;
        final int biome = view.biome(x0, z0);
        if (!CinderCones.hasCinderCones(biome)) {
            return Long.MIN_VALUE;
        }
        final long center = cones.center(x0, z0, CinderCones.frequency(biome));
        if (center != Long.MIN_VALUE) {
            final int cx = CinderCones.unpackX(center);
            final int cz = CinderCones.unpackZ(center);
            if (cx >> 4 == chunkX && cz >> 4 == chunkZ && CinderCones.hasCinderCones(view.biome(cx, cz))) {
                return center;
            }
        }
        return Long.MIN_VALUE;
    }

    /** {@code volcano_fissure} and its gem variants: lava in raw basalt, 1 to 3 fissures within 6 blocks. */
    static void placeVolcanoFissure(VolcanoLevel level, XoroshiroRandomSource random, int x, int y, int z, Fissure.Decoration decoration) {
        // min_depth: VerticalAnchor.aboveBottom(16)
        Fissure.place(level, random, new int[] {x, y, z}, Fissure.State.LAVA, new Fissure.State(VolcanoKind.RAW_ROCK, Fissure.BASALT),
            3, 6, level.view().minY() + 16, 10, 24, 6, decoration);
    }

    /** A {@code random_*_hot_spring}: {@code rarity_filter}, {@code in_square}, then the selector's pick, at the chunk's floor. */
    private static void randomSpring(VolcanoLevel level, XoroshiroRandomSource random, int x0, int z0, int chance, HotSpring.Config[] list) {
        if (!(random.nextFloat() < 1f / chance)) {
            return;
        }
        final int x = random.nextInt(16) + x0;
        final int z = random.nextInt(16) + z0;
        final HotSpring.Config config = list[random.nextInt(list.length)];
        HotSpring.place(level, random, x, level.view().minY(), z, config);
    }

    /** {@code lava_hot_spring}: rarity 20, {@code in_square}, {@code OCEAN_FLOOR_WG}, at most 40 high. */
    private static void lavaSpring(VolcanoLevel level, XoroshiroRandomSource random, int x0, int z0) {
        if (!(random.nextFloat() < 1f / 20)) {
            return;
        }
        final int x = random.nextInt(16) + x0;
        final int z = random.nextInt(16) + z0;
        final int y = level.view().oceanFloor(x, z);
        if (LAVA_SPRING_CLIMATE.isValid(level.view(), x, y, z, random)) {
            HotSpring.place(level, random, x, y, z, HotSpring.Config.LAVA);
        }
    }

    /** Runs one hot spring config in a fresh level, for tests: the blocks it placed. */
    static List<VolcanoBlock> runHotSpring(VolcanoView view, HotSpring.Config config, XoroshiroRandomSource random, int x, int y, int z) {
        final VolcanoLevel level = new VolcanoLevel(view);
        HotSpring.place(level, random, x, y, z, config);
        return level.blocks();
    }

    /** Runs one volcano fissure in a fresh level, for tests: the blocks it placed. */
    static List<VolcanoBlock> runVolcanoFissure(VolcanoView view, Fissure.Decoration decoration, XoroshiroRandomSource random, int x, int y, int z) {
        final VolcanoLevel level = new VolcanoLevel(view);
        placeVolcanoFissure(level, random, x, y, z, decoration);
        return level.blocks();
    }

    /** Runs one caldera in a fresh level, for tests: the blocks it placed. */
    static List<VolcanoBlock> runCaldera(VolcanoView view, int x, int y, int z) {
        final VolcanoLevel level = new VolcanoLevel(view);
        Caldera.place(level, x, y, z);
        return level.blocks();
    }
}
