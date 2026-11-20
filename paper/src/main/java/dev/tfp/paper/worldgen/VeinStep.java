/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/worldgen/biome/*.json @ c3b4d9536 (which biomes list in_biome/veins/{kaolin, montane,
 * collisional_mountains, river}), with world/feature/vein/VeinFeature.java and KaolinDiscVeinFeature.java (what a vein
 * replaces) and the block tags tfc:grass, tfc:kaolin_clay_replaceable, c:stones and c:gravels.
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.ore.KaolinDisc;
import dev.tfp.core.worldgen.ore.OreVeins;
import dev.tfp.core.worldgen.ore.VeinBlock;
import dev.tfp.core.worldgen.ore.VeinConfig;
import dev.tfp.core.worldgen.ore.VeinData;
import dev.tfp.core.worldgen.ore.Weighted;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.bukkit.Material;

/**
 * PAR-WORLD-30: TFC's vein step of one world, as the terrain fill applies it to each position: the main vein tag
 * ({@code in_biome/veins}, every type: clusters, discs, pipes, the projected coal and salt discs, the near-lava sulfur, the
 * dikes and the gravel disc that write rock), then the kaolin disc ({@code in_biome/veins/kaolin}, in its biomes and climate),
 * then the biome passes ({@code montane}, {@code collisional_mountains}, {@code river}). A chunk runs a biome tag when a biome
 * of its 3x3 chunk area lists it, as TFC's {@code applyBiomeDecoration} collects the adjacent biomes; the tags run in this
 * fixed order (NP-WORLD-01, ledger). Pure and thread-safe; a {@link Chunk} is confined to one worldgen worker.
 *
 * <p>A decision is a block code: a palette index ({@code >= 0}: the rocks, the ores, then the hardened rocks and rock gravels the
 * veins write), a custom block ({@link #custom}: the coal and salt blocks and the kaolin clays, which are written as CraftEngine
 * blocks when CraftEngine has them and as vanilla placeholders until then), or {@link #KEEP}.
 */
final class VeinStep {
    /** The code of a position the step leaves alone. */
    static final int KEEP = Integer.MIN_VALUE;

    /** Bit of {@link #chunkTags}: the kaolin disc. */
    static final int KAOLIN = 1;

    /** The biome tags in the order a chunk runs them; bit {@code 1 << (i + 1)} of {@link #chunkTags}. */
    static final List<String> BIOME_PASSES = VeinData.BIOME_VEIN_TAGS;

    /** The biomes whose feature list names each tag (TFC's biome JSON). */
    static final Map<String, List<String>> TAG_BIOMES = tagBiomes();

    private static Map<String, List<String>> tagBiomes() {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        out.put(VeinData.KAOLIN_TAG, List.of("ancient_shield_volcano", "badlands", "buttes", "canyons", "cenote_highlands",
            "cenote_plateau", "cenote_rolling_hills", "doline_highlands", "doline_plateau", "doline_rolling_hills",
            "dormant_shield_volcano", "extinct_shield_volcano", "extreme_doline_mountains", "extreme_doline_plateau", "highlands",
            "mesas", "old_mountains", "plateau", "plateau_wide", "rift_lake", "rift_valley", "rolling_hills", "shilin_highlands",
            "shilin_hills", "shilin_plateau", "stair_step_canyons", "tower_karst_highlands", "tower_karst_hills",
            "volcanic_mountain_islands"));
        out.put("montane", List.of("active_shield_volcano", "ancient_shield_volcano", "collisional_mountains",
            "dormant_shield_volcano", "extinct_shield_volcano", "extreme_doline_mountains", "glacially_carved_mountains",
            "glacially_carved_oceanic_mountains", "glacially_carved_volcanic_mountains", "glacially_carved_volcanic_oceanic_mountains",
            "glaciated_mountains", "glaciated_oceanic_mountains", "glaciated_shield_volcano", "glaciated_volcanic_mountains",
            "glaciated_volcanic_oceanic_mountains", "ice_sheet_mountains", "ice_sheet_mountains_edge", "ice_sheet_oceanic_mountains",
            "ice_sheet_oceanic_mountains_edge", "ice_sheet_shield_volcano", "ice_sheet_volcanic_mountains",
            "ice_sheet_volcanic_oceanic_mountains", "mountain_lake", "mountains", "oceanic_mountain_lake", "oceanic_mountains",
            "oceanic_volcanic_arc", "old_mountain_lake", "old_mountains", "old_shield_volcano_shore", "shield_volcano_shore",
            "sunken_shield_volcano", "tower_karst_bay", "tower_karst_canyons", "tower_karst_highlands", "tower_karst_hills",
            "tower_karst_lake", "tower_karst_plains", "volcanic_island", "volcanic_mountain_islands", "volcanic_mountain_lake",
            "volcanic_mountains", "volcanic_oceanic_mountain_lake", "volcanic_oceanic_mountains"));
        out.put("collisional_mountains", List.of("collisional_mountains"));
        out.put("river", List.of("lake", "meltwater_lake", "oceanic_mountain_lake", "rift_lake", "river", "tower_karst_lake",
            "volcanic_oceanic_mountain_lake"));
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The tag bits of each biome id. */
    private static final int[] BIOME_TAGS = biomeTags();

    private static int[] biomeTags() {
        final Map<String, Integer> ids = new HashMap<>();
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            ids.put(TfcBiomes.name(id).toLowerCase(Locale.ROOT), id);
        }
        final int[] out = new int[TfcBiomes.COUNT];
        for (Map.Entry<String, List<String>> e : TAG_BIOMES.entrySet()) {
            final int bit = e.getKey().equals(VeinData.KAOLIN_TAG) ? KAOLIN : 1 << (BIOME_PASSES.indexOf(e.getKey()) + 1);
            for (String name : e.getValue()) {
                final Integer id = ids.get(name);
                if (id == null) {
                    throw new IllegalStateException("unknown TFC biome " + name + " in vein tag " + e.getKey());
                }
                out[id] |= bit;
            }
        }
        return out;
    }

    /** The vein tag bits of one biome id. */
    static int biomeTags(int biome) {
        return BIOME_TAGS[biome];
    }

    /** The biome id of a block column, as the world's biome source gives it. */
    @FunctionalInterface
    interface BiomeAt {
        int biome(int x, int z);
    }

    /**
     * The vein tag bits of a chunk: the union of the tags of every biome in its 3x3 chunk area, sampled at each quart (TFC
     * collects the biomes of the adjacent chunks' sections).
     */
    static int chunkTags(int chunkX, int chunkZ, BiomeAt biomes) {
        int tags = 0;
        for (int x = (chunkX - 1) << 4; x < (chunkX + 2) << 4; x += 4) {
            for (int z = (chunkZ - 1) << 4; z < (chunkZ + 2) << 4; z += 4) {
                tags |= BIOME_TAGS[biomes.biome(x, z)];
            }
        }
        return tags;
    }

    /** The kaolin clay block ids, by {@link KaolinDisc.Clay#ordinal}. */
    private static final List<String> CLAY_IDS = java.util.Arrays.stream(KaolinDisc.Clay.values())
        .map(c -> "tfp:" + c.path()).toList();

    private final List<String> rockIds;
    private final Map<String, Integer> rockIndex = new HashMap<>();
    private final OreVeins main;
    private final OreVeins biome;
    private final KaolinDisc kaolin;
    private final List<String> paletteIds;
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private final List<String> customIds;
    private final Map<String, Integer> customIndex = new HashMap<>();

    /**
     * @param read    the text of a file below the data root, or empty when it is missing
     * @param rockIds the raw rock ids, the first palette entries
     */
    VeinStep(long seed, Function<String, Optional<String>> read, List<String> rockIds) {
        this.rockIds = List.copyOf(rockIds);
        for (int i = 0; i < rockIds.size(); i++) {
            rockIndex.put(rockIds.get(i), i);
        }
        this.main = new OreVeins(seed, OreVeinResources.loadAll(read), rockIds);
        this.biome = new OreVeins(seed, List.of(), OreVeinResources.biomeVeins(read), rockIds);
        this.kaolin = new KaolinDisc(seed, OreVeinResources.kaolin(read));
        final TreeSet<String> ores = new TreeSet<>(main.oreIds());
        ores.addAll(biome.oreIds());
        final TreeSet<String> rocks = new TreeSet<>();
        final TreeSet<String> custom = new TreeSet<>();
        for (OreVeins veins : List.of(main, biome)) {
            for (VeinConfig config : veins.types()) {
                for (Weighted<VeinBlock> weighted : config.blocks().values()) {
                    for (VeinBlock block : weighted.values()) {
                        if (block.kind() == VeinBlock.Kind.OTHER && !rockIndex.containsKey(block.id())) {
                            (block.id().startsWith("tfp:rock/") ? rocks : custom).add(block.id());
                        }
                    }
                }
            }
        }
        final List<String> palette = new ArrayList<>(rockIds);
        palette.addAll(ores);
        palette.addAll(rocks);
        this.paletteIds = List.copyOf(palette);
        for (int i = 0; i < paletteIds.size(); i++) {
            paletteIndex.put(paletteIds.get(i), i);
        }
        final List<String> customs = new ArrayList<>(custom);
        customs.addAll(CLAY_IDS);
        this.customIds = List.copyOf(customs);
        for (int i = 0; i < customIds.size(); i++) {
            customIndex.put(customIds.get(i), i);
        }
    }

    /** The veins of the main tag (the vein indicators read their cluster types). */
    OreVeins main() {
        return main;
    }

    /** The palette ids: the raw rocks, every ore of every pass (sorted), then the other rock blocks the veins write (sorted). */
    List<String> paletteIds() {
        return paletteIds;
    }

    /** The blocks outside the palette: the non-rock blocks the veins write (sorted), then the kaolin clays. */
    List<String> customIds() {
        return customIds;
    }

    /** Whether a code is a custom block, and which: {@code customIds().get(-code - 1)}. */
    static boolean isCustom(int code) {
        return code < 0 && code != KEEP;
    }

    static int customOf(int code) {
        return -code - 1;
    }

    /** The vanilla placeholder of a custom block, written while CraftEngine does not have it. */
    static Material placeholder(String customId) {
        return switch (customId) {
            case "tfp:lignite", "tfp:bituminous_coal" -> Material.COAL_BLOCK;
            case "tfp:halite" -> Material.CALCITE;
            case "tfp:kaolin_clay_grass" -> Material.GRASS_BLOCK;
            case "tfp:red_kaolin_clay" -> Material.RED_TERRACOTTA;
            case "tfp:pink_kaolin_clay" -> Material.PINK_TERRACOTTA;
            case "tfp:white_kaolin_clay" -> Material.WHITE_TERRACOTTA;
            default -> throw new IllegalArgumentException("no placeholder for " + customId);
        };
    }

    /**
     * The decisions of one chunk.
     *
     * @param tags          the chunk's {@link #chunkTags}
     * @param kaolinClimate whether the kaolin disc's climate placement passes at the chunk's origin
     * @param view          the ground height (the ocean floor heightmap, after the carvers) and the lava of the world
     */
    Chunk chunk(int chunkX, int chunkZ, int tags, boolean kaolinClimate, OreVeins.WorldView view) {
        final Set<String> passes = new java.util.HashSet<>();
        for (int i = 0; i < BIOME_PASSES.size(); i++) {
            if ((tags & (1 << (i + 1))) != 0) {
                passes.add(BIOME_PASSES.get(i));
            }
        }
        final OreVeins.Chunk mainChunk = main.chunk(chunkX, chunkZ, Set.of(), view);
        final OreVeins.Chunk biomeChunk = passes.isEmpty() ? null : biome.chunk(chunkX, chunkZ, passes, view);
        KaolinDisc.Chunk clay = null;
        if ((tags & KAOLIN) != 0 && kaolinClimate) {
            clay = kaolin.chunk(chunkX, chunkZ, view);
        }
        return new Chunk(mainChunk.isEmpty() ? null : mainChunk, biomeChunk == null || biomeChunk.isEmpty() ? null : biomeChunk,
            clay == null || clay.isEmpty() ? null : clay);
    }

    /** One chunk's vein step. */
    final class Chunk {
        private final OreVeins.Chunk main;
        private final OreVeins.Chunk biome;
        private final KaolinDisc.Chunk clay;

        private Chunk(OreVeins.Chunk main, OreVeins.Chunk biome, KaolinDisc.Chunk clay) {
            this.main = main;
            this.biome = biome;
            this.clay = clay;
        }

        /** Whether no vein reaches the chunk (every decision keeps the block). */
        boolean isEmpty() {
            return main == null && biome == null && clay == null;
        }

        /**
         * The block of a raw rock position after the vein step: the main tag's veins, then the kaolin disc (over raw and
         * hardened rock and rock gravel), then the biome passes (over the raw rock still there). Never {@link #KEEP}.
         *
         * @param rock the raw rock index at the position
         */
        int rock(int x, int y, int z, int rock) {
            int code = rock;
            int raw = rock;
            // c:stones (raw and hardened) and c:gravels: what the kaolin disc replaces
            boolean stoneOrGravel = true;
            if (main != null) {
                final VeinBlock block = main.decide(x, y, z, rock);
                if (block != null) {
                    code = code(block, rock);
                    final boolean deposit = block.kind() == VeinBlock.Kind.DEPOSIT;
                    raw = !deposit && code >= 0 && code < rockIds.size() ? code : -1;
                    stoneOrGravel = !deposit && code >= 0 && paletteIds.get(code).startsWith("tfp:rock/");
                }
            }
            if (clay != null && stoneOrGravel) {
                final KaolinDisc.Clay c = clay.decide(x, y, z, KaolinDisc.Soil.REPLACEABLE);
                if (c != null) {
                    return clayCode(c);
                }
            }
            if (biome != null && raw >= 0) {
                final VeinBlock block = biome.decide(x, y, z, raw);
                if (block != null) {
                    code = code(block, raw);
                }
            }
            return code;
        }

        /** The kaolin clay of a soil position (grass, dirt, gravel, snow block), or {@link #KEEP}. */
        int soil(int x, int y, int z, KaolinDisc.Soil soil) {
            if (clay == null || soil == KaolinDisc.Soil.OTHER) {
                return KEEP;
            }
            final KaolinDisc.Clay c = clay.decide(x, y, z, soil);
            return c == null ? KEEP : clayCode(c);
        }
    }

    private int clayCode(KaolinDisc.Clay clay) {
        return -1 - customIndex.get(CLAY_IDS.get(clay.ordinal()));
    }

    /**
     * The code of a vein block that replaced raw rock {@code rock}: an ore or another rock of the palette, a custom block, or
     * the rock itself for a placer deposit (the deposit blocks are not generated yet; ledger).
     */
    private int code(VeinBlock block, int rock) {
        if (block.kind() == VeinBlock.Kind.DEPOSIT) {
            return rock;
        }
        final Integer index = paletteIndex.get(block.id());
        if (index != null) {
            return index;
        }
        return -1 - customIndex.get(block.id());
    }
}
