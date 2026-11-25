/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/tags/worldgen/placed_feature/feature/stratovolcanoes.json @ c3b4d9536, with
 * feature/{cinder_cones,tuyas,shield_volcanoes,volcanic}.json, in_biome/{underground_decoration,top_layer_modification}.json
 * and in_biome/large_features/*.json (which biome lists which family), the placed and configured features those tags name
 * (data/tfc/worldgen/{placed_feature,configured_feature}: the rivulets, flood fill lakes, random fissure selectors, pumice and
 * obsidian shard patches, springs, ice_and_snow), src/main/java/net/dries007/tfc/world/placement/{CenterOrDistanceToPlacement,
 * CinderConePlacement,TuyaPlacement,OceanRidgePlacement}.java, src/main/java/net/dries007/tfc/world/feature/IceAndSnowFeature.java
 * and the vanilla 1.21 placement steps they name (count, rarity_filter, in_square, height_range uniform and biased_to_bottom,
 * heightmap, simple_random_selector)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.height.HeightNoise;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every volcanic feature of one world (PAR-WORLD-14), in TFC's tag order: per biome family the rivulets, the crater lakes
 * (flood fill lakes of lava, spring water or water), the random fissures and the pumice and obsidian shard patches of the
 * cinder cones, tuyas, stratovolcanoes and shield volcanoes, the lava surface springs of the volcanic biomes, the ocean
 * ridge's rivulets, the hot springs, and the water, lava and glacier springs of every biome. The cinder cone caldera, the
 * volcano fissure and the hot springs are {@link Volcanoes}' rules, run here in the same level.
 * <p>
 * A chunk runs the features of the biome at its centre, each with its own {@link FeatureRandom} at the chunk, in one level
 * that sees the blocks its earlier features placed (NP-WORLD-01). Immutable and thread-safe.
 */
public final class VolcanicFeatures {
    /** {@code volcano_fissure.json} and {@code volcano_spring_water_fissure.json}: count and radius (the rest are defaults). */
    static final int FISSURE_COUNT = 3;
    static final int FISSURE_RADIUS = 6;

    private static final Fissure.Decoration TOPAZ = new Fissure.Decoration(Gem.TOPAZ, 3, 3, 6);
    private static final Fissure.Decoration DIAMOND = new Fissure.Decoration(Gem.DIAMOND, 3, 3, 6);
    private static final Fissure.Decoration SAPPHIRE = new Fissure.Decoration(Gem.SAPPHIRE, 3, 3, 6);
    private static final Fissure.Decoration EMERALD = new Fissure.Decoration(Gem.EMERALD, 3, 3, 6);
    /** The lava fissure lists of the stratovolcano variants, in list order: 3 topaz, 1 diamond, 2 plain. */
    private static final Fissure.Decoration[] LAVA_FISSURES = {TOPAZ, TOPAZ, TOPAZ, DIAMOND, null, null};
    /** The spring water fissure lists of the stratovolcano variants, in list order: 2 sapphire, 2 emerald, 2 plain. */
    private static final Fissure.Decoration[] SPRING_WATER_FISSURES = {SAPPHIRE, SAPPHIRE, EMERALD, EMERALD, null, null};
    /** {@code random_tuya_fissure}, in list order: 3 topaz, 1 diamond, 4 plain. */
    private static final Fissure.Decoration[] TUYA_FISSURES = {TOPAZ, TOPAZ, TOPAZ, DIAMOND, null, null, null, null};

    /** The centered-feature placement modifier of a rule. */
    enum Where {
        NONE, VOLCANO, TUYA, STRATOVOLCANO, OCEAN_RIDGE;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A {@code random_patch} of groundcover: {@code rarity_filter} 3, {@code in_square}, then the family's distance placement.
     *
     * @param id        the placed patch, e.g. {@code tfc:tuya_pumice_patch}; its block feature is the id without {@code _patch}
     * @param minEasing the placement's {@code min_easing}
     */
    record Patch(String id, VolcanicKind kind, Where where, float minEasing) {
        static final int RARITY = 3;

        String block() {
            return id.substring(0, id.length() - "_patch".length());
        }
    }

    /** A flood fill lake at a center: no draws. */
    record Lake(String id, Where where, Stratovolcanoes.Placement placement, Caldera.Config config) {}

    /**
     * A {@code simple_random_selector} of fissures that share one center placement.
     *
     * @param prefix the variant prefix of the listed placed features, e.g. {@code fuji}
     */
    record Fissures(String id, String prefix, Where where, Stratovolcanoes.Placement placement, Fissure.State fluid,
                    Fissure.Decoration[] list) {
        /** The placed features of the list, in order, e.g. {@code tfc:fuji_topaz_volcano_fissure}. */
        List<String> features() {
            final List<String> out = new ArrayList<>();
            for (Fissure.Decoration decoration : list) {
                out.add("tfc:" + prefix + "_" + configured(decoration, fluid).substring("tfc:".length()));
            }
            return out;
        }
    }

    /** The configured fissure of a decoration and fluid, e.g. {@code tfc:sapphire_volcano_spring_water_fissure}. */
    static String configured(Fissure.Decoration decoration, Fissure.State fluid) {
        return "tfc:" + (decoration == null ? "" : decoration.gem().id() + "_") + "volcano_"
            + (fluid.kind() == VolcanoKind.SPRING_WATER ? "spring_water_" : "") + "fissure";
    }

    /**
     * Rivulets: {@code count}, {@code in_square}, then a distance placement.
     *
     * @param configured the configured rivulet, e.g. {@code tfc:stratovolcano_rivulet}
     */
    record Rivulets(String id, String configured, int count, Where where, float minEasing, Stratovolcanoes.Placement placement,
                    Rivulet.Config config) {}

    /**
     * Springs: {@code count}, {@code in_square}, {@code height_range} (uniform, or biased to the bottom), and a climate.
     *
     * @param climate the {@code tfc:climate} placement, or null
     */
    record Springs(String id, int count, int minY, int maxY, boolean biasedToBottom, ClimateFilter climate, Spring.Config config) {}

    // feature/cinder_cones.json (volcano_caldera and random_volcano_fissure are Volcanoes')
    private static final Rivulets VOLCANO_RIVULET = new Rivulets("tfc:volcano_rivulet", "tfc:volcano_rivulet", 2, Where.VOLCANO, 0.7f,
        null, Rivulet.Config.BASALT);
    private static final Patch[] CONE_PATCHES = {new Patch("tfc:cinder_cone_pumice_patch", VolcanicKind.PUMICE, Where.VOLCANO, 0.8f),
        new Patch("tfc:cinder_cone_obsidian_shard_patch", VolcanicKind.OBSIDIAN_SHARD, Where.VOLCANO, 0.8f)};

    // feature/tuyas.json
    private static final Rivulets TUYA_RIVULET = new Rivulets("tfc:tuya_rivulet", "tfc:tuya_rivulet", 2, Where.TUYA, 0.84f, null,
        Rivulet.Config.BASALT);
    private static final Lake TUYA_CALDERA = new Lake("tfc:tuya_caldera", Where.TUYA, null, Caldera.Config.LAVA);
    private static final Fissures TUYA_FISSURE = new Fissures("tfc:random_tuya_fissure", "tuya", Where.TUYA, null, Fissure.State.LAVA,
        TUYA_FISSURES);
    private static final Patch[] TUYA_PATCHES = {new Patch("tfc:tuya_pumice_patch", VolcanicKind.PUMICE, Where.TUYA, 0.8f),
        new Patch("tfc:tuya_obsidian_shard_patch", VolcanicKind.OBSIDIAN_SHARD, Where.TUYA, 0.8f)};

    // feature/stratovolcanoes.json
    private static final Patch[] STRATOVOLCANO_PATCHES = {
        new Patch("tfc:stratovolcano_pumice_patch", VolcanicKind.PUMICE, Where.STRATOVOLCANO, 0.8f),
        new Patch("tfc:stratovolcano_obsidian_shard_patch", VolcanicKind.OBSIDIAN_SHARD, Where.STRATOVOLCANO, 0.8f)};
    private static final Lake[] STRATOVOLCANO_LAKES = {
        lake("tfc:fuji_caldera", Stratovolcanoes.Variant.FUJI, false, 0f, 0.6f, Caldera.Config.LAVA),
        lake("tfc:fuji_spring", Stratovolcanoes.Variant.FUJI, false, 0.8f, 1f, Caldera.Config.SPRING_WATER),
        lake("tfc:kelimutu_lava", Stratovolcanoes.Variant.KELIMUTU, true, 0f, 0.25f, Caldera.Config.LAVA),
        lake("tfc:kelimutu_spring_water", Stratovolcanoes.Variant.KELIMUTU, true, 0.25f, 0.75f, Caldera.Config.SPRING_WATER),
        lake("tfc:kelimutu_water", Stratovolcanoes.Variant.KELIMUTU, true, 0.75f, 1f, Caldera.Config.WATER),
        lake("tfc:tahoma_caldera", Stratovolcanoes.Variant.TAHOMA, false, 0f, 0.6f, Caldera.Config.LAVA),
        lake("tfc:tahoma_spring", Stratovolcanoes.Variant.TAHOMA, false, 0.8f, 1f, Caldera.Config.SPRING_WATER),
        lake("tfc:wizard_island_lava", Stratovolcanoes.Variant.CRATER_LAKE, true, 0f, 0.6f, Caldera.Config.LAVA),
        lake("tfc:wizard_island_spring", Stratovolcanoes.Variant.CRATER_LAKE, true, 0.8f, 1f, Caldera.Config.SPRING_WATER)};
    private static final Fissures[] STRATOVOLCANO_FISSURES = {
        fissures("fuji", Stratovolcanoes.Variant.FUJI, false, 0f, 0.6f, false),
        fissures("fuji", Stratovolcanoes.Variant.FUJI, false, 0.8f, 1f, true),
        fissures("tahoma", Stratovolcanoes.Variant.TAHOMA, false, 0f, 0.6f, false),
        // TFC's tahoma spring water fissures carry hash_max 0.8 (not hash_min, as Fuji's do): kept
        fissures("tahoma", Stratovolcanoes.Variant.TAHOMA, false, 0f, 0.8f, true),
        fissures("kelimutu", Stratovolcanoes.Variant.KELIMUTU, true, 0f, 0.25f, false),
        fissures("kelimutu", Stratovolcanoes.Variant.KELIMUTU, true, 0.25f, 0.75f, true),
        fissures("crater_lake", Stratovolcanoes.Variant.CRATER_LAKE, true, 0f, 0.6f, false),
        fissures("crater_lake", Stratovolcanoes.Variant.CRATER_LAKE, true, 0.8f, 1f, true)};
    private static final Rivulets[] STRATOVOLCANO_RIVULETS = {
        new Rivulets("tfc:fuji_rivulet", "tfc:stratovolcano_rivulet", 3, Where.STRATOVOLCANO, 0.7f,
            Stratovolcanoes.Placement.near(Stratovolcanoes.Variant.FUJI, 0.7f, 0f, 0.4f), Rivulet.Config.STRATOVOLCANO),
        new Rivulets("tfc:tahoma_rivulet", "tfc:stratovolcano_rivulet", 3, Where.STRATOVOLCANO, 0.83f,
            Stratovolcanoes.Placement.near(Stratovolcanoes.Variant.TAHOMA, 0.83f, 0f, 0.4f), Rivulet.Config.STRATOVOLCANO),
        new Rivulets("tfc:crater_lake_rivulet", "tfc:stratovolcano_rivulet", 1, Where.STRATOVOLCANO, 0.6f,
            Stratovolcanoes.Placement.near(Stratovolcanoes.Variant.CRATER_LAKE, 0.6f, 0.83f, 1f), Rivulet.Config.STRATOVOLCANO),
        new Rivulets("tfc:crater_lake_sulfur", "tfc:sulfur_rivulet", 1, Where.STRATOVOLCANO, 0.6f,
            Stratovolcanoes.Placement.near(Stratovolcanoes.Variant.CRATER_LAKE, 0.6f, 0.83f, 1f), Rivulet.Config.SULFUR)};
    /** The {@code tfc:stratovolcano} placement of the stratovolcano patches: any variant, {@code min_easing} 0.8. */
    private static final Stratovolcanoes.Placement STRATOVOLCANO_PATCH_PLACEMENT = Stratovolcanoes.Placement.near(null, 0.8f, 0f, 1f);

    // feature/shield_volcanoes.json
    private static final Patch[] SHIELD_PATCHES = {new Patch("tfc:pumice_shield_volcano_patch", VolcanicKind.PUMICE, Where.NONE, 0f),
        new Patch("tfc:obsidian_shard_shield_volcano_patch", VolcanicKind.OBSIDIAN_SHARD, Where.NONE, 0f)};

    // feature/volcanic.json
    private static final Springs LAVA_SURFACE_SPRING = new Springs("tfc:lava_surface_spring", 70, 64, 220, false, null, Spring.Config.LAVA);

    // in_biome/large_features/ocean_ridge.json: tfc:ocean_ridge with min_distance 0, max_distance 32
    private static final Rivulets OCEAN_RIDGE_RIVULET = new Rivulets("tfc:ocean_ridge_rivulet", "tfc:ocean_ridge_rivulet", 2,
        Where.OCEAN_RIDGE, 0f, null, Rivulet.Config.OCEAN_RIDGE);
    static final int OCEAN_RIDGE_MIN_DISTANCE = 0;
    static final int OCEAN_RIDGE_MAX_DISTANCE = 32;

    // in_biome/underground_decoration.json, in its order
    private static final Springs[] UNDERGROUND_SPRINGS = {
        new Springs("tfc:water_spring", 110, -64, 63, false, null, Spring.Config.WATER),
        new Springs("tfc:lava_spring", 50, -64, 63, false, null, Spring.Config.LAVA),
        new Springs("tfc:water_surface_spring", 60, 64, 180, true,
            ClimateFilter.of(-16f, Float.POSITIVE_INFINITY, 65f, Float.POSITIVE_INFINITY), Spring.Config.WATER),
        new Springs("tfc:extra_water_surface_spring", 30, 64, 180, false,
            ClimateFilter.of(-16f, Float.POSITIVE_INFINITY, 390f, Float.POSITIVE_INFINITY), Spring.Config.WATER),
        new Springs("tfc:glacial_spring", 200, 110, 122, false, null, Spring.Config.GLACIAL),
        new Springs("tfc:ice_sheet_spring", 50, 64, 96, false, null, Spring.Config.ICE_SHEET)};

    /**
     * PAR-WORLD-21: {@code tfc:glacial_oceanic_spring}, a large feature of the glaciated (volcanic) oceanic mountains and the
     * ice sheet oceanic mountain edge, listed before their volcanic families: {@code tfc:glacial_spring}'s configured spring
     * 200 times at a uniform y of 80 to 92. Not a row of the configured-feature table (a placed id only), so it is kept out
     * of {@link #rules()}.
     */
    static final Springs GLACIAL_OCEANIC_SPRING = new Springs("tfc:glacial_oceanic_spring", 200, 80, 92, false, null,
        Spring.Config.GLACIAL);

    /** Whether a biome's large features list {@code tfc:glacial_oceanic_spring}. */
    static boolean hasGlacialOceanicSpring(int biome) {
        return biome == TfcBiomes.GLACIATED_OCEANIC_MOUNTAINS || biome == TfcBiomes.ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
            || biome == TfcBiomes.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS;
    }

    private static Lake lake(String id, Stratovolcanoes.Variant variant, boolean offset, float hashMin, float hashMax, Caldera.Config config) {
        return new Lake(id, Where.STRATOVOLCANO, Stratovolcanoes.Placement.center(variant, offset, hashMin, hashMax), config);
    }

    private static Fissures fissures(String prefix, Stratovolcanoes.Variant variant, boolean offset, float hashMin, float hashMax,
                                     boolean springWater) {
        return new Fissures("tfc:random_" + prefix + (springWater ? "_spring_water" : "") + "_fissure", prefix, Where.STRATOVOLCANO,
            Stratovolcanoes.Placement.center(variant, offset, hashMin, hashMax),
            springWater ? Fissure.State.SPRING_WATER : Fissure.State.LAVA, springWater ? SPRING_WATER_FISSURES : LAVA_FISSURES);
    }

    private final long levelSeed;
    private final Volcanoes volcanoes;
    private final Tuyas tuyas;
    private final Stratovolcanoes stratovolcanoes;
    private final Map<String, FeatureRandom> randoms = new HashMap<>();

    /** @param levelSeed the world seed */
    public VolcanicFeatures(long levelSeed) {
        this(levelSeed, new Volcanoes(levelSeed));
    }

    /**
     * @param levelSeed the world seed
     * @param volcanoes the cinder cones and hot springs of the same seed (shared with the surface heights)
     */
    public VolcanicFeatures(long levelSeed, Volcanoes volcanoes) {
        this.levelSeed = levelSeed;
        this.volcanoes = volcanoes;
        this.tuyas = new Tuyas(levelSeed);
        this.stratovolcanoes = new Stratovolcanoes(levelSeed);
        for (String id : placedIds()) {
            randoms.put(id, new FeatureRandom(levelSeed, id));
        }
        randoms.put(GLACIAL_OCEANIC_SPRING.id(), new FeatureRandom(levelSeed, GLACIAL_OCEANIC_SPRING.id()));
    }

    /** The placed features that draw: each has its own random. */
    private static List<String> placedIds() {
        final List<String> ids = new ArrayList<>();
        for (Rivulets r : allRivulets()) {
            ids.add(r.id());
        }
        for (Patch p : allPatches()) {
            ids.add(p.id());
        }
        for (Fissures f : allFissures()) {
            ids.add(f.id());
        }
        for (Springs s : allSprings()) {
            ids.add(s.id());
        }
        return ids;
    }

    private static List<Rivulets> allRivulets() {
        final List<Rivulets> out = new ArrayList<>(List.of(VOLCANO_RIVULET, TUYA_RIVULET, OCEAN_RIDGE_RIVULET));
        out.addAll(List.of(STRATOVOLCANO_RIVULETS));
        return out;
    }

    private static List<Patch> allPatches() {
        final List<Patch> out = new ArrayList<>(List.of(CONE_PATCHES));
        out.addAll(List.of(TUYA_PATCHES));
        out.addAll(List.of(STRATOVOLCANO_PATCHES));
        out.addAll(List.of(SHIELD_PATCHES));
        return out;
    }

    private static List<Fissures> allFissures() {
        final List<Fissures> out = new ArrayList<>(List.of(TUYA_FISSURE));
        out.addAll(List.of(STRATOVOLCANO_FISSURES));
        return out;
    }

    private static List<Lake> allLakes() {
        final List<Lake> out = new ArrayList<>(List.of(TUYA_CALDERA));
        out.addAll(List.of(STRATOVOLCANO_LAKES));
        return out;
    }

    private static List<Springs> allSprings() {
        final List<Springs> out = new ArrayList<>(List.of(LAVA_SURFACE_SPRING));
        out.addAll(List.of(UNDERGROUND_SPRINGS));
        return out;
    }

    /** The cinder cones and hot springs of this world. */
    public Volcanoes volcanoes() {
        return volcanoes;
    }

    /** The tuya cells of this world. */
    public Tuyas tuyas() {
        return tuyas;
    }

    /** The stratovolcano cells of this world. */
    public Stratovolcanoes stratovolcanoes() {
        return stratovolcanoes;
    }

    /** Whether a biome's large features hold {@code #tfc:feature/shield_volcanoes}. */
    static boolean hasShieldFeatures(int biome) {
        return biome == TfcBiomes.ACTIVE_SHIELD_VOLCANO || biome == TfcBiomes.ANCIENT_SHIELD_VOLCANO
            || biome == TfcBiomes.DORMANT_SHIELD_VOLCANO || biome == TfcBiomes.EXTINCT_SHIELD_VOLCANO
            || biome == TfcBiomes.OLD_SHIELD_VOLCANO_SHORE || biome == TfcBiomes.SHIELD_VOLCANO_SHORE
            || biome == TfcBiomes.SUNKEN_SHIELD_VOLCANO;
    }

    /** Whether a biome's large features hold {@code #tfc:feature/volcanic}: the cinder cone, tuya and stratovolcano biomes. */
    static boolean hasVolcanicFeatures(int biome) {
        return CinderCones.hasCinderCones(biome) || Tuyas.hasTuyas(biome) || Stratovolcanoes.hasStratovolcanoes(biome);
    }

    /**
     * The blocks inside chunk {@code (chunkX, chunkZ)}: the large features of this chunk and its 8 neighbours are run (each
     * reaches at most 14 blocks past its chunk), and only their blocks inside this chunk are given, chunk by chunk; then the
     * chunk's own springs, which write their own column only, in the level of the chunk's large features.
     */
    public void place(int chunkX, int chunkZ, VolcanoView view, Consumer<VolcanicBlock> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final VolcanoLevel level = new VolcanoLevel(view);
                largeFeatures(level, chunkX + dx, chunkZ + dz);
                if (dx == 0 && dz == 0) {
                    undergroundDecoration(level, chunkX, chunkZ);
                }
                for (VolcanicBlock block : level.volcanicBlocks()) {
                    if (block.inChunk(chunkX, chunkZ)) {
                        out.accept(block);
                    }
                }
            }
        }
    }

    /** Every block the volcanic features and springs of chunk {@code (chunkX, chunkZ)} place, inside the chunk or not. */
    public List<VolcanicBlock> decorate(int chunkX, int chunkZ, VolcanoView view) {
        final VolcanoLevel level = new VolcanoLevel(view);
        largeFeatures(level, chunkX, chunkZ);
        undergroundDecoration(level, chunkX, chunkZ);
        return level.volcanicBlocks();
    }

    /** The large features of the biome at the chunk's centre, family by family in TFC's tag order. */
    void largeFeatures(VolcanoLevel level, int chunkX, int chunkZ) {
        final int biome = level.view().biome((chunkX << 4) + 8, (chunkZ << 4) + 8);
        // PAR-WORLD-21: before the stratovolcanoes in the three biomes that list it (none of them has cinder cones or tuyas)
        if (hasGlacialOceanicSpring(biome)) {
            springs(level, GLACIAL_OCEANIC_SPRING, chunkX, chunkZ);
        }
        if (CinderCones.hasCinderCones(biome)) {
            rivulets(level, VOLCANO_RIVULET, chunkX, chunkZ);
            volcanoes.placeCaldera(level, chunkX, chunkZ);
            volcanoes.placeFissure(level, chunkX, chunkZ);
            for (Patch patch : CONE_PATCHES) {
                patch(level, patch, chunkX, chunkZ);
            }
        }
        if (Tuyas.hasTuyas(biome)) {
            rivulets(level, TUYA_RIVULET, chunkX, chunkZ);
            lake(level, TUYA_CALDERA, chunkX, chunkZ);
            fissures(level, TUYA_FISSURE, chunkX, chunkZ);
            for (Patch patch : TUYA_PATCHES) {
                patch(level, patch, chunkX, chunkZ);
            }
        }
        if (Stratovolcanoes.hasStratovolcanoes(biome)) {
            for (Patch patch : STRATOVOLCANO_PATCHES) {
                patch(level, patch, chunkX, chunkZ);
            }
            for (Lake lake : STRATOVOLCANO_LAKES) {
                lake(level, lake, chunkX, chunkZ);
            }
            for (Fissures fissures : STRATOVOLCANO_FISSURES) {
                fissures(level, fissures, chunkX, chunkZ);
            }
            for (Rivulets rivulets : STRATOVOLCANO_RIVULETS) {
                rivulets(level, rivulets, chunkX, chunkZ);
            }
        }
        if (hasVolcanicFeatures(biome)) {
            springs(level, LAVA_SURFACE_SPRING, chunkX, chunkZ);
        }
        if (hasShieldFeatures(biome)) {
            for (Patch patch : SHIELD_PATCHES) {
                patch(level, patch, chunkX, chunkZ);
            }
        }
        if (biome == TfcBiomes.OCEAN_RIDGE) {
            rivulets(level, OCEAN_RIDGE_RIVULET, chunkX, chunkZ);
        }
        volcanoes.placeSprings(level, chunkX, chunkZ, Volcanoes.springs(biome));
    }

    /** {@code in_biome/underground_decoration}'s springs (every biome), then {@code top_layer_modification}'s ice and snow. */
    void undergroundDecoration(VolcanoLevel level, int chunkX, int chunkZ) {
        for (Springs springs : UNDERGROUND_SPRINGS) {
            springs(level, springs, chunkX, chunkZ);
        }
        iceAndSnow();
    }

    /** {@code IceAndSnowFeature.place}: TFC's feature places nothing ("todo: do something properly here") and succeeds. */
    static boolean iceAndSnow() {
        return true;
    }

    private void rivulets(VolcanoLevel level, Rivulets rule, int chunkX, int chunkZ) {
        final XoroshiroRandomSource random = randoms.get(rule.id()).chunk(chunkX, chunkZ);
        for (int i = 0; i < rule.count(); i++) {
            final int x = random.nextInt(16) + (chunkX << 4);
            final int z = random.nextInt(16) + (chunkZ << 4);
            if (near(rule.where(), rule.minEasing(), rule.placement(), level.view(), x, z)) {
                Rivulet.place(level, random, x, z, rule.config());
            }
        }
    }

    private void patch(VolcanoLevel level, Patch rule, int chunkX, int chunkZ) {
        final XoroshiroRandomSource random = randoms.get(rule.id()).chunk(chunkX, chunkZ);
        if (!(random.nextFloat() < 1f / Patch.RARITY)) {
            return;
        }
        final int x = random.nextInt(16) + (chunkX << 4);
        final int z = random.nextInt(16) + (chunkZ << 4);
        if (near(rule.where(), rule.minEasing(), rule.where() == Where.STRATOVOLCANO ? STRATOVOLCANO_PATCH_PLACEMENT : null,
            level.view(), x, z)) {
            GroundcoverPatch.place(level, random, x, z, rule.kind());
        }
    }

    private void lake(VolcanoLevel level, Lake rule, int chunkX, int chunkZ) {
        final long center = center(rule.where(), rule.placement(), level.view(), chunkX, chunkZ);
        if (center != Long.MIN_VALUE) {
            final int cx = CinderCones.unpackX(center);
            final int cz = CinderCones.unpackZ(center);
            Caldera.place(level, cx, level.view().worldSurface(cx, cz), cz, rule.config());
        }
    }

    private void fissures(VolcanoLevel level, Fissures rule, int chunkX, int chunkZ) {
        // The selector draws first, then the chosen fissure's center placement
        final XoroshiroRandomSource random = randoms.get(rule.id()).chunk(chunkX, chunkZ);
        final Fissure.Decoration decoration = rule.list()[random.nextInt(rule.list().length)];
        final long center = center(rule.where(), rule.placement(), level.view(), chunkX, chunkZ);
        if (center != Long.MIN_VALUE) {
            final int cx = CinderCones.unpackX(center);
            final int cz = CinderCones.unpackZ(center);
            placeFissure(level, random, cx, level.view().worldSurface(cx, cz), cz, rule.fluid(), decoration);
        }
    }

    /** A {@code volcano_fissure} or {@code volcano_spring_water_fissure}: the fluid in raw basalt, 1 to 3 fissures within 6 blocks. */
    static void placeFissure(VolcanoLevel level, XoroshiroRandomSource random, int x, int y, int z, Fissure.State fluid,
                             Fissure.Decoration decoration) {
        // min_depth: VerticalAnchor.aboveBottom(16)
        Fissure.place(level, random, new int[] {x, y, z}, fluid, new Fissure.State(VolcanoKind.RAW_ROCK, Fissure.BASALT),
            FISSURE_COUNT, FISSURE_RADIUS, level.view().minY() + 16, 10, 24, 6, decoration);
    }

    private void springs(VolcanoLevel level, Springs rule, int chunkX, int chunkZ) {
        final XoroshiroRandomSource random = randoms.get(rule.id()).chunk(chunkX, chunkZ);
        for (int i = 0; i < rule.count(); i++) {
            final int x = random.nextInt(16) + (chunkX << 4);
            final int z = random.nextInt(16) + (chunkZ << 4);
            final int y;
            if (rule.biasedToBottom()) {
                // BiasedToBottomHeight.sample with inner 1
                final int k = random.nextInt(rule.maxY() - rule.minY() - 1 + 1);
                y = random.nextInt(k + 1) + rule.minY();
            } else {
                // UniformHeight.sample
                y = random.nextInt(rule.maxY() - rule.minY() + 1) + rule.minY();
            }
            if (rule.climate() == null || rule.climate().isValid(level.view(), x, y, z, random)) {
                Spring.place(level, x, y, z, rule.config());
            }
        }
    }

    /** A {@code center: true} placement at the chunk's origin: the center column in this chunk, or {@link Long#MIN_VALUE}. */
    private long center(Where where, Stratovolcanoes.Placement placement, VolcanoView view, int chunkX, int chunkZ) {
        final int x0 = chunkX << 4;
        final int z0 = chunkZ << 4;
        switch (where) {
            case VOLCANO:
                return volcanoes.volcanoCenter(chunkX, chunkZ, view);
            case TUYA: {
                final int biome = view.biome(x0, z0);
                if (!Tuyas.hasTuyas(biome)) {
                    return Long.MIN_VALUE;
                }
                final long center = tuyas.center(x0, z0, Tuyas.frequency(biome));
                if (center != Long.MIN_VALUE) {
                    final int cx = CinderCones.unpackX(center);
                    final int cz = CinderCones.unpackZ(center);
                    if (cx >> 4 == chunkX && cz >> 4 == chunkZ && Tuyas.hasTuyas(view.biome(cx, cz))) {
                        return center;
                    }
                }
                return Long.MIN_VALUE;
            }
            case STRATOVOLCANO:
                return stratovolcanoes.center(placement, view, x0, z0);
            default:
                return Long.MIN_VALUE;
        }
    }

    /** A distance placement at a column: the easing strictly between {@code minEasing} and 1 (TFC's default maximum). */
    private boolean near(Where where, float minEasing, Stratovolcanoes.Placement placement, VolcanoView view, int x, int z) {
        switch (where) {
            case NONE:
                return true;
            case VOLCANO: {
                final int biome = view.biome(x, z);
                if (!CinderCones.hasCinderCones(biome)) {
                    return false;
                }
                final float easing = volcanoes.cones().easing(x, z, CinderCones.frequency(biome));
                return easing > minEasing && easing < 1f;
            }
            case TUYA: {
                final int biome = view.biome(x, z);
                if (!Tuyas.hasTuyas(biome)) {
                    return false;
                }
                final float easing = tuyas.easing(x, z, Tuyas.frequency(biome));
                return easing > minEasing && easing < 1f;
            }
            case STRATOVOLCANO:
                return stratovolcanoes.test(placement, view, x, z);
            case OCEAN_RIDGE: {
                final double distance = HeightNoise.getOceanRidgeWarpedEdgeDistanceAndScale(x, z, levelSeed, false).x;
                return OCEAN_RIDGE_MIN_DISTANCE <= distance && distance <= OCEAN_RIDGE_MAX_DISTANCE;
            }
            default:
                return false;
        }
    }

    /**
     * The rules by TFC id, each as the cells of its {@code volcano_features.tsv} row (the columns the rule sets; the others
     * are empty), read from the rule constants this class runs: the golden test compares them with TFC's table.
     */
    public static Map<String, Map<String, String>> rules() {
        final Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Patch p : allPatches()) {
            out.put(p.block(), cells("group", "simple_block", "state", "tfc:groundcover/" + p.kind().name().toLowerCase(Locale.ROOT),
                "placed", "1", "placement", "heightmap;block_predicate_filter;block_predicate_filter"));
            out.put(p.id(), cells("group", "random_patch", "state", p.block(), "placed", "1",
                "placement", "rarity_filter;in_square" + (p.where() == Where.NONE ? "" : ";" + p.where().id()),
                "placed_rarity", String.valueOf(Patch.RARITY)));
        }
        for (Fissure.Decoration decoration : new Fissure.Decoration[] {SAPPHIRE, EMERALD, null}) {
            final Map<String, String> cells = cells("group", "fissure", "state", "spring_water",
                "radius", String.valueOf(FISSURE_RADIUS), "count", String.valueOf(FISSURE_COUNT), "placed", "0");
            if (decoration != null) {
                cells.put("decoration_gem", decoration.gem().id());
                cells.put("decoration_rarity", String.valueOf(decoration.rarity()));
                cells.put("decoration_radius", String.valueOf(decoration.radius()));
                cells.put("decoration_count", String.valueOf(decoration.count()));
            }
            out.put(configured(decoration, Fissure.State.SPRING_WATER), cells);
        }
        for (Springs s : allSprings()) {
            out.put(s.id(), cells("group", "spring", "state", s.config().state().name().toLowerCase(Locale.ROOT),
                "valid_blocks", String.valueOf(s.config().validBlocks().size()), "placed", "1",
                "placement", "count;in_square;height_range" + (s.climate() == null ? "" : ";climate")));
        }
        for (Lake l : allLakes()) {
            out.put(l.id(), cells("group", "flood_fill_lake", "state", l.config().state().name().toLowerCase(Locale.ROOT),
                "overfill", l.config().overfill() ? "1" : "0", "placed", "1", "placement", l.where().id() + ";heightmap"));
        }
        for (Rivulets r : allRivulets()) {
            if (r.id().equals(r.configured())) {
                out.put(r.id(), cells("group", "rivulet", "placed", "1", "placement", "count;in_square;" + r.where().id()));
            } else {
                // The stratovolcano variants place the configured rivulet under their own placed ids
                out.put(r.configured(), cells("group", "rivulet", "placed", "0"));
            }
        }
        out.put("tfc:ice_and_snow", cells("group", "ice_and_snow", "placed", "1"));
        for (Fissures f : allFissures()) {
            out.put(f.id(), cells("group", "random_selector", "list_len", String.valueOf(f.list().length),
                "features", String.join(";", f.features()), "placed", "1"));
        }
        return out;
    }

    private static Map<String, String> cells(String... pairs) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }
}
