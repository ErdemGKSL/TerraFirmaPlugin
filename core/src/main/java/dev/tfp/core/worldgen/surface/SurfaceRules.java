/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/surface/SoilSurfaceState.java @ c3b4d9536, with SurfaceStates.java and
 * util/Helpers.java (adjustAverageTemperatureByElevation)
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceBlock.*;

import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.Mth;
import java.util.ArrayList;
import java.util.List;

/**
 * The surface states of TFC: the soil tables that choose between snow, a coarse soil, the soil and the dry surface by
 * rainfall and temperature, the transitions between them, the soil variant by temperature and flooding, the moraines and the
 * beach rules. Ported from {@code SoilSurfaceState} and {@code SurfaceStates}; the constants keep TFC's names. Pure and
 * thread-safe.
 */
public final class SurfaceRules {
    /** The soil block types the surface states use ({@code SoilBlockType}). */
    public enum Soil { GRASS, DIRT, COARSE_DIRT, MUD }

    /** The soil variants the surface states use ({@code SoilBlockType.Variant}). */
    public enum Variant { ENTISOL, ARIDISOL, OXISOL, FLUVISOL, ANDISOL }

    /** The salt that TFC's soil transitions hash the block position with. */
    private static final long TRANSITION_SALT = 729375982L;
    /** The salt of {@code SAND_AND_GRAVEL}. */
    private static final long SAND_GRAVEL_SALT = 728275914L;
    /** The number of regions of a soil table in TFC. */
    private static final int REGIONS = 18;
    /** {@code OverworldClimateModel.DEPTH_LEVEL} and {@code LAVA_LEVEL_TEMPERATURE}. */
    private static final float DEPTH_LEVEL = -64;
    private static final float LAVA_LEVEL_TEMPERATURE = 15f;

    public static final SurfaceRule RAW = of(SurfaceBlock.RAW);
    public static final SurfaceRule COBBLE = of(SurfaceBlock.COBBLE);
    public static final SurfaceRule GRAVEL = of(SurfaceBlock.GRAVEL);
    public static final SurfaceRule SAND = of(ROCK_SAND);
    public static final SurfaceRule SANDSTONE = of(ROCK_SANDSTONE);
    public static final SurfaceRule RIVER_SAND = of(SurfaceBlock.RIVER_SAND);

    public static final SurfaceRule BASALT = of(SurfaceBlock.BASALT);
    public static final SurfaceRule BASALT_COBBLE = of(SurfaceBlock.BASALT_COBBLE);
    public static final SurfaceRule BASALT_GRAVEL = of(SurfaceBlock.BASALT_GRAVEL);
    public static final SurfaceRule BASALT_MORAINE = (column, y) ->
        (Hash.hash(TRANSITION_SALT, column.x(), y, column.z()) & 127) > 96 ? SurfaceBlock.BASALT_COBBLE : SurfaceBlock.BASALT_GRAVEL;
    public static final SurfaceRule TUFF = of(SurfaceBlock.TUFF);
    public static final SurfaceRule TUFF_GRAVEL = of(SurfaceBlock.TUFF_GRAVEL);

    public static final SurfaceRule MORAINE = (column, y) ->
        (Hash.hash(TRANSITION_SALT, column.x(), y, column.z()) & 127) > 96 ? SurfaceBlock.COBBLE : SurfaceBlock.GRAVEL;
    public static final SurfaceRule SAND_AND_GRAVEL = (column, y) ->
        (Hash.hash(SAND_GRAVEL_SALT, column.x(), y, column.z()) & 127) > 48 ? ROCK_SAND : SurfaceBlock.GRAVEL;

    public static final SurfaceRule PACKED_ICE = of(SurfaceBlock.PACKED_ICE);
    public static final SurfaceRule BLUE_ICE = of(SurfaceBlock.BLUE_ICE);
    public static final SurfaceRule SNOW = of(SurfaceBlock.SNOW);

    public static final SurfaceRule DRY_MUD = of(HARDENED_CLAY);
    public static final SurfaceRule SALTED_EARTH = of(HALITE);
    public static final SurfaceRule OCEAN_MUD = of(MUD_FLUVISOL);

    /** {@code RARE_SHORE_SAND}: pink where warm and wet, green or black on mafic rock, else white. */
    public static final SurfaceRule RARE_SHORE_SAND = (column, y) -> rareShore(column, PINK_SAND, GREEN_SAND, BLACK_SAND, WHITE_SAND);
    public static final SurfaceRule RARE_SHORE_SANDSTONE = (column, y) ->
        rareShore(column, PINK_SANDSTONE, GREEN_SANDSTONE, BLACK_SANDSTONE, WHITE_SANDSTONE);
    /** {@code VOLCANIC_SHORE_SAND}: green where very wet, else black. */
    public static final SurfaceRule VOLCANIC_SHORE_SAND = (column, y) -> column.groundWater() > 420f ? GREEN_SAND : BLACK_SAND;
    public static final SurfaceRule VOLCANIC_SHORE_SANDSTONE = (column, y) ->
        column.groundWater() > 420f ? GREEN_SANDSTONE : BLACK_SANDSTONE;

    /** {@code SHORE_SAND}: one of three common sands, or the rare sand, by the sand variant noise. */
    public static final SurfaceRule SHORE_SAND = (column, y) -> {
        final float variantNoiseValue = (float) SurfaceNoise.SAND_VARIANT.noise(column.x(), column.z());
        if (variantNoiseValue > 0.55) {
            return RARE_SHORE_SAND.block(column, y);
        } else if (variantNoiseValue > 0.2) {
            return YELLOW_SAND;
        } else if (variantNoiseValue > 0.1) {
            return BROWN_SAND;
        }
        return RED_SAND;
    };

    /** {@code SHORE_SANDSTONE}: as the sand, with its own cutoffs (TFC's sandstone does not always match the sand). */
    public static final SurfaceRule SHORE_SANDSTONE = (column, y) -> {
        final float variantNoiseValue = (float) SurfaceNoise.SAND_VARIANT.noise(column.x(), column.z());
        if (variantNoiseValue > 0.8) {
            return RARE_SHORE_SANDSTONE.block(column, y);
        } else if (variantNoiseValue > 0.4) {
            return YELLOW_SANDSTONE;
        } else if (variantNoiseValue > 0.2) {
            return RED_SANDSTONE;
        }
        return BROWN_SANDSTONE;
    };

    /**
     * {@code SHORE_SURFACE}: gravel where the beach noise is above a cutoff that falls with the temperature (gravel is more
     * common in cold climates), else the shore sand.
     */
    public static final SurfaceRule SHORE_SURFACE = (column, y) -> (beachIsGravel(column) ? GRAVEL : SHORE_SAND).block(column, y);

    /** {@code SHORE_UNDERLAYER}: the rock where the beach is gravel, else the shore sandstone. */
    public static final SurfaceRule SHORE_UNDERLAYER = (column, y) -> (beachIsGravel(column) ? RAW : SHORE_SANDSTONE).block(column, y);

    public static final SurfaceRule TOP_GRASS_TO_GRAVEL = surfaceType(Soil.GRASS, GRAVEL);
    public static final SurfaceRule TOP_GRASS_TO_SAND = surfaceType(Soil.GRASS, SAND);
    public static final SurfaceRule MID_DIRT_TO_GRAVEL = midType(Soil.DIRT, GRAVEL);
    public static final SurfaceRule MID_DIRT_TO_SAND = midType(Soil.DIRT, SAND);
    public static final SurfaceRule VOLCANIC_TOP_GRASS_TO_GRAVEL = volcanicSurfaceType(Soil.GRASS, BASALT_GRAVEL);
    public static final SurfaceRule VOLCANIC_MID_DIRT_TO_GRAVEL = volcanicMidType(Soil.DIRT, BASALT_GRAVEL);
    public static final SurfaceRule VOLCANIC_TOP_GRASS_TO_LOCAL_GRAVEL = volcanicSurfaceType(Soil.GRASS, GRAVEL);
    public static final SurfaceRule VOLCANIC_MID_DIRT_TO_LOCAL_GRAVEL = volcanicMidType(Soil.DIRT, GRAVEL);
    public static final SurfaceRule UNDER_GRAVEL = underType();
    public static final SurfaceRule MUD = surfaceType(Soil.MUD, GRAVEL);

    public static final SurfaceRule SNOWY_RAW = snowable(SNOW, RAW);
    public static final SurfaceRule SNOWY_COBBLE = snowable(SNOW, COBBLE);
    public static final SurfaceRule SNOWY_GRAVEL = snowable(SNOW, GRAVEL);
    public static final SurfaceRule SNOWY_SAND = snowable(SNOW, SAND);
    public static final SurfaceRule SNOWY_MORAINE = snowable(SNOW, MORAINE);
    public static final SurfaceRule SNOWY_BASALT = snowable(SNOW, BASALT);
    public static final SurfaceRule SNOWY_BASALT_COBBLE = snowable(SNOW, BASALT_COBBLE);
    public static final SurfaceRule SNOWY_BASALT_GRAVEL = snowable(SNOW, BASALT_GRAVEL);
    public static final SurfaceRule SNOWY_BASALT_MORAINE = snowable(SNOW, BASALT_MORAINE);
    public static final SurfaceRule SNOWY_SAND_AND_GRAVEL = snowable(SNOW, SAND_AND_GRAVEL);

    private SurfaceRules() {}

    /** A rule that is always the same block. */
    public static SurfaceRule of(SurfaceBlock block) {
        return (column, y) -> block;
    }

    /** {@code SoilSurfaceState.soil}: the soil block of a type and a variant. */
    public static SurfaceBlock soilBlock(Soil type, Variant variant) {
        return SurfaceBlock.valueOf(type.name() + "_" + variant.name());
    }

    private static SurfaceRule soil(Soil type, Variant variant) {
        return of(soilBlock(type, variant));
    }

    /**
     * {@code SoilSurfaceState.transitioningSoil(type)}: silt (fluvisol) near a flooding river, else entisol below 16 degrees,
     * oxisol above 16.7, and the patch noise picks between them in the band.
     */
    public static SurfaceRule transitioningSoil(Soil type) {
        final SurfaceBlock silt = soilBlock(type, Variant.FLUVISOL);
        final SurfaceBlock coldBlock = soilBlock(type, Variant.ENTISOL);
        final SurfaceBlock hotBlock = soilBlock(type, Variant.OXISOL);
        return (column, y) -> {
            // First, check if near a "flooding" river, and place silt if so
            if (column.baseGroundwater() > 25 && Math.abs(column.rainVariance()) > 0.5) {
                return silt;
            }
            // Then run through the temperature calculations
            final float temp = column.temperature();
            if (temp < 16f) {
                return coldBlock;
            }
            if (temp > 16.7f) {
                return hotBlock;
            }
            return SurfaceNoise.PATCH.noise(column.x(), column.z()) > 0 ? hotBlock : coldBlock;
        };
    }

    /** {@code SoilSurfaceState.transition}: {@code first} for the blocks whose hash is above a half, else {@code second}. */
    public static SurfaceRule transition(SurfaceRule first, SurfaceRule second) {
        return (column, y) -> (Hash.hash(TRANSITION_SALT, column.x(), y, column.z()) & 127) > 63
            ? first.block(column, y) : second.block(column, y);
    }

    /** {@code SoilSurfaceState.blobTransition}: {@code first} where the patch noise is positive, else {@code second}. */
    public static SurfaceRule blobTransition(SurfaceRule first, SurfaceRule second) {
        return (column, y) -> SurfaceNoise.PATCH.noise(column.x(), column.z()) > 0
            ? first.block(column, y) : second.block(column, y);
    }

    /**
     * {@code Helpers.adjustAverageTemperatureByElevation}: the sea-level temperature cools with altitude above sea level, at
     * {@code 0.16225} per block, to at most {@code 17.822} lower. Below sea level the lava level pulls it towards 15 at y = -64.
     */
    public static float adjustTemperature(int y, float averageTemperature, float seaLevel) {
        if (y > 0) {
            return averageTemperature - Mth.clamp((y - seaLevel) * 0.16225f, 0, 17.822f);
        }
        // Account for lava level influence
        final float depthInfluence = Mth.inverseLerp(y, DEPTH_LEVEL, 0);
        return Mth.lerp(depthInfluence, LAVA_LEVEL_TEMPERATURE, averageTemperature);
    }

    /**
     * {@code SoilSurfaceState.getState}: the block of a soil rule, from the rainfall and the temperature. The rain index and
     * the temperature index each pick one of the regions, and the lower of the two wins, so dry ground is gravel and cold
     * ground is snow whichever is more extreme.
     *
     * @param regions the {@value #REGIONS} regions, from the driest and coldest to the wettest and warmest
     */
    public static SurfaceRule climate(List<SurfaceRule> regions) {
        if (regions.size() != REGIONS) {
            throw new IllegalArgumentException("a soil table has " + REGIONS + " regions, got " + regions.size());
        }
        final SurfaceRule[] table = regions.toArray(SurfaceRule[]::new);
        final int size = table.length;
        return (column, y) -> {
            final float rainfall = column.groundWater();
            final float temperature = adjustTemperature(y, column.temperature(), column.seaLevel());
            final int x = column.x();
            final int z = column.z();
            final float noise = (float) SurfaceNoise.PATCH.noise(x, z);
            final float dither = (Hash.hash(TRANSITION_SALT, x, y, z) & 127) * (1f / 64) - 1;
            // Rain-controlled surface: the regions are picked by rainfall from 35 to 450 mm. All arguments are floats or
            // ints in TFC, so this is Minecraft's float clampedMap
            final int rainIndex = (int) Mth.clampedMap(rainfall + 15 * noise + 10 * dither, 35f, 450f, 3f, size - 0.01f);
            // Temperature-controlled surface: the regions are picked by temperature from -19 to -4 degrees (double clampedMap)
            final int tempIndex = (int) Mth.clampedMap((double) temperature, -19 + 0.5 * noise + 0.5 * dither, -4, 0, (double) (size - 0.01f));
            return table[Math.min(rainIndex, tempIndex)].block(column, y);
        };
    }

    /** @return {@code head} followed by {@code tail} repeated until the table is full. */
    private static List<SurfaceRule> table(List<SurfaceRule> head, SurfaceRule tail) {
        final List<SurfaceRule> regions = new ArrayList<>(head);
        while (regions.size() < REGIONS) {
            regions.add(tail);
        }
        return regions;
    }

    /** {@code SoilSurfaceState.buildSnowableSurface}: snow where cold, else the typical state. */
    public static SurfaceRule snowable(SurfaceRule snow, SurfaceRule typical) {
        return climate(table(List.of(snow, snow, transition(snow, typical)), typical));
    }

    /**
     * {@code SoilSurfaceState.buildSurfaceType(type, dry)}: the top of a soil surface. Snow when cold, the dry surface when
     * dry, then coarse aridisol, the aridisol soil, and the soil of the temperature where it is wet.
     */
    public static SurfaceRule surfaceType(Soil type, SurfaceRule dry) {
        final SurfaceRule coarse = soil(Soil.COARSE_DIRT, Variant.ARIDISOL);
        final SurfaceRule arid = soil(type, Variant.ARIDISOL);
        final SurfaceRule soil = transitioningSoil(type);
        return climate(table(List.of(
            SNOW,
            SNOW,
            transition(SNOW, dry),
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, arid),
            arid,
            blobTransition(arid, soil)
        ), soil));
    }

    /** {@code SoilSurfaceState.buildVolcanicSurfaceType(type, dry)}: the top of a volcanic (andisol) surface. */
    public static SurfaceRule volcanicSurfaceType(Soil type, SurfaceRule dry) {
        final SurfaceRule coarse = soil(Soil.COARSE_DIRT, Variant.ANDISOL);
        final SurfaceRule soil = soil(type, Variant.ANDISOL);
        return climate(table(List.of(
            SNOW,
            SNOW,
            transition(SNOW, dry),
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil)
        ), soil));
    }

    /** {@code SoilSurfaceState.buildMidType(type, dry)}: the layer under the top of a soil surface. */
    public static SurfaceRule midType(Soil type, SurfaceRule dry) {
        final SurfaceRule coarse = soil(Soil.COARSE_DIRT, Variant.ARIDISOL);
        final SurfaceRule arid = soil(type, Variant.ARIDISOL);
        final SurfaceRule soil = transitioningSoil(type);
        return climate(table(List.of(
            PACKED_ICE,
            blobTransition(PACKED_ICE, dry),
            dry,
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, arid),
            arid,
            blobTransition(arid, soil)
        ), soil));
    }

    /** {@code SoilSurfaceState.buildVolcanicMidType(type, dry)}: the layer under a volcanic surface. */
    public static SurfaceRule volcanicMidType(Soil type, SurfaceRule dry) {
        final SurfaceRule coarse = soil(Soil.COARSE_DIRT, Variant.ANDISOL);
        final SurfaceRule soil = soil(type, Variant.ANDISOL);
        return climate(table(List.of(
            PACKED_ICE,
            blobTransition(PACKED_ICE, dry),
            dry,
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil)
        ), soil));
    }

    /** {@code SoilSurfaceState.buildUnderType}: the rock under a surface, gravel once the climate allows soil. */
    public static SurfaceRule underType() {
        return climate(table(List.of(
            RAW,
            RAW,
            blobTransition(RAW, GRAVEL)
        ), GRAVEL));
    }

    private static SurfaceBlock rareShore(SurfaceColumn column, SurfaceBlock pink, SurfaceBlock green, SurfaceBlock black, SurfaceBlock white) {
        if (column.groundWater() > 300f && column.temperature() > 15f) {
            return pink;
        } else if (column.seaLevelMafic()) {
            if (column.groundWater() > 300f) {
                return green;
            }
            return black;
        }
        return white;
    }

    /** The cutoff of the beach noise for a column: {@code Mth.clampedMap(temperature, -15, 25, -0.7, 0.7)}. */
    private static boolean beachIsGravel(SurfaceColumn column) {
        final float variantNoiseValue = (float) SurfaceNoise.SAND_GRAVEL_BEACH.noise(column.x(), column.z());
        final double gravelCutoff = Mth.clampedMap((double) column.temperature(), -15, 25, -0.7, 0.7);
        return variantNoiseValue > gravelCutoff;
    }
}
