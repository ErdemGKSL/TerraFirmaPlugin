/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/soil/SoilBlockType.java @ c3b4d9536 (the Variant order and the
 *       GRASS, DIRT, COARSE_DIRT and MUD block types of the surface ids)
 */
package dev.tfp.paper.worldgen;

import dev.tfp.core.worldgen.rock.RockLayerSampler;
import dev.tfp.core.worldgen.rock.RockSettings;
import dev.tfp.core.worldgen.surface.SurfaceKind;
import dev.tfp.paper.data.RegistryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The server block data of the placeholder rocks, resolved once per CraftEngine state table (M0-CE-01 decision). Pure:
 * the caller passes whether CraftEngine is loaded and a lookup, so the refuse rule runs without a server.
 *
 * <p>The generator must not write vanilla stone in place of a rock it could not resolve, so if CraftEngine has not loaded
 * or any rock id is unknown, the palette is {@linkplain #resolved() refused} and the chunk is not generated.
 *
 * @param <T>        the block data type (Bukkit {@code BlockData} in the generator, anything in tests)
 * @param generation the state-table generation the palette was resolved from
 */
public final class RockPalette<T> {
    /** TFC's soils, in the order of {@code SoilBlockType.Variant} (the soil index of {@link #surfaceIndex}). */
    public static final List<String> SOILS = List.of("entisol", "aridisol", "oxisol", "fluvisol", "andisol", "podzol",
        "alfisol", "mollisol");
    /** The surface kinds that are one CraftEngine block per soil, in the order of {@link #surfaceIds}. */
    static final List<SurfaceKind> SOIL_KINDS = List.of(SurfaceKind.GRASS, SurfaceKind.DIRT, SurfaceKind.COARSE_DIRT,
        SurfaceKind.MUD);
    private static final String RAW_ROCK = "tfp:rock/raw/";

    private final long generation;
    private final List<T> blocks;
    private final String refusal;

    private RockPalette(long generation, List<T> blocks, String refusal) {
        this.generation = generation;
        this.blocks = blocks;
        this.refusal = refusal;
    }

    /**
     * Resolves {@code ids} in order: the result has one block per id, or a refusal naming what is missing.
     *
     * @param generation the state-table generation the lookup belongs to
     * @param loaded     whether CraftEngine has loaded its configuration
     * @param ids        the custom block ids, in palette order
     * @param lookup     the block data of an id, or empty if it is unknown
     */
    public static <T> RockPalette<T> resolve(long generation, boolean loaded, List<String> ids, Function<String, Optional<T>> lookup) {
        if (!loaded) {
            return new RockPalette<>(generation, null, "CraftEngine has not loaded its configuration");
        }
        List<T> blocks = new ArrayList<>(ids.size());
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            Optional<T> data = lookup.apply(id);
            if (data == null || data.isEmpty()) {
                missing.add(id);
            } else {
                blocks.add(data.get());
            }
        }
        if (!missing.isEmpty()) {
            return new RockPalette<>(generation, null, "unknown custom block(s) " + missing);
        }
        return new RockPalette<>(generation, List.copyOf(blocks), null);
    }

    /**
     * The CraftEngine block of a surface kind (PAR-WORLD-04): {@code tfp:<grass|dirt|coarse_dirt|mud>/<soil>} for the soil
     * kinds and {@code tfp:rock/gravel/<rock>} for gravel. Null for the kinds with no CraftEngine block of their own here:
     * the rock surface (the rock palette itself), sand and sandstone (a colour per rock, PAR-WORLD-23), snow and packed
     * ice (vanilla blocks in TFC too).
     *
     * @param soil a name of {@link #SOILS}; ignored for gravel
     * @param rock the rock name ({@code granite}); ignored for the soil kinds
     */
    public static String surfaceId(SurfaceKind kind, String soil, String rock) {
        return switch (kind) {
            case GRASS, DIRT, COARSE_DIRT, MUD -> {
                if (!SOILS.contains(soil)) {
                    throw new IllegalArgumentException("unknown soil " + soil);
                }
                yield "tfp:" + kind.name().toLowerCase(java.util.Locale.ROOT) + "/" + soil;
            }
            case GRAVEL -> "tfp:rock/gravel/" + rock;
            case ROCK_SURFACE, SAND, SANDSTONE, SNOW, PACKED_ICE -> null;
        };
    }

    /**
     * The surface block ids to append to a palette's ids, so the surface blocks resolve once with the rocks and fall under
     * the same refuse rule: every soil kind of {@link #SOIL_KINDS} for every soil of {@link #SOILS}, then the gravel of
     * every rock. {@link #surfaceIndex} is the position of a block in this list.
     *
     * @param rawRockIds the raw rock ids of the palette ({@code tfp:rock/raw/<rock>}), in rock index order
     */
    public static List<String> surfaceIds(List<String> rawRockIds) {
        List<String> ids = new ArrayList<>(SOIL_KINDS.size() * SOILS.size() + rawRockIds.size());
        for (SurfaceKind kind : SOIL_KINDS) {
            for (String soil : SOILS) {
                ids.add(surfaceId(kind, soil, null));
            }
        }
        for (String raw : rawRockIds) {
            if (!raw.startsWith(RAW_ROCK)) {
                throw new IllegalArgumentException("not a raw rock id: " + raw);
            }
            ids.add(surfaceId(SurfaceKind.GRAVEL, null, raw.substring(RAW_ROCK.length())));
        }
        return List.copyOf(ids);
    }

    /**
     * The position of a surface block in {@link #surfaceIds}, or -1 for a kind that has no CraftEngine block there (see
     * {@link #surfaceId}). Plain arithmetic: the terrain fill calls it per block and reads the cached block data with
     * {@link #block}, offset by where the surface ids start in its palette, so no id is looked up per block.
     *
     * @param soil  the index in {@link #SOILS}; ignored for gravel
     * @param rock  the rock index; ignored for the soil kinds
     * @param rocks how many rocks the palette has
     */
    public static int surfaceIndex(SurfaceKind kind, int soil, int rock, int rocks) {
        final int soilKind = SOIL_KINDS.indexOf(kind);
        if (soilKind >= 0) {
            if (soil < 0 || soil >= SOILS.size()) {
                throw new IndexOutOfBoundsException("soil " + soil);
            }
            return soilKind * SOILS.size() + soil;
        }
        if (kind == SurfaceKind.GRAVEL) {
            if (rock < 0 || rock >= rocks) {
                throw new IndexOutOfBoundsException("rock " + rock + " of " + rocks);
            }
            return SOIL_KINDS.size() * SOILS.size() + rock;
        }
        return -1;
    }

    /**
     * PAR-WORLD-23: the rock settings of every rock of TFC's preset ({@code data/tfp/tfp/worldgen/rock_settings/*.json} in
     * the plugin jar), by rock id ({@code tfc:granite}), in the preset's order.
     *
     * @throws IllegalArgumentException when a rock has no file
     */
    public static Map<String, RockSettings> rockSettingsFromJar(ClassLoader loader) {
        return RockSettings.load(RockLayerSampler.PRESET.rocks(), path -> {
            try (InputStream in = loader.getResourceAsStream(RegistryService.JAR_DATA_ROOT + "/" + path)) {
                return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + path + " from the plugin jar", e);
            }
        });
    }

    /**
     * PAR-WORLD-23: the CraftEngine block of a rock-coloured surface kind from the rock's settings (TFC's
     * {@code SurfaceStates}: raw, gravel, sand and sandstone of the rock at the position): {@code ROCK_SURFACE} the raw rock,
     * {@code GRAVEL} its gravel, {@code SAND} its sand colour, {@code SANDSTONE} its raw sandstone colour; null for the
     * other kinds.
     */
    public static String rockSurfaceId(SurfaceKind kind, RockSettings rock) {
        return switch (kind) {
            case ROCK_SURFACE -> rock.raw();
            case GRAVEL -> rock.gravel();
            case SAND -> rock.sand();
            case SANDSTONE -> rock.sandstone();
            case GRASS, DIRT, COARSE_DIRT, MUD, SNOW, PACKED_ICE -> null;
        };
    }

    public long generation() {
        return generation;
    }

    /** True when every rock resolved and the chunk may be generated. */
    public boolean resolved() {
        return blocks != null;
    }

    /** Why the palette is refused, or null when it is resolved. */
    public String refusal() {
        return refusal;
    }

    /** The block data at {@code index} (see {@link dev.tfp.core.worldgen.chunk.PlaceholderRocks#index}). Requires {@link #resolved()}. */
    public T block(int index) {
        if (blocks == null) {
            throw new IllegalStateException("palette refused: " + refusal);
        }
        return blocks.get(index);
    }
}
