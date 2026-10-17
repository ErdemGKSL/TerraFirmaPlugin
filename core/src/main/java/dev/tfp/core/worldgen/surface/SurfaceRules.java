/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceKind.*;

import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.Mth;
import java.util.ArrayList;
import java.util.List;

/**
 * The surface states of TFC, in kind space: the soil tables that choose between snow, a coarse soil, the soil and the dry
 * surface by rainfall and temperature, the transitions between them, and the beach rules. Ported from
 * {@code SoilSurfaceState} and {@code SurfaceStates}, with every soil variant mapped to its kind (see {@link SurfaceKind}).
 * Pure and thread-safe.
 */
public final class SurfaceRules {
    /** The raw rock: {@code SurfaceStates.RAW}. */
    public static final SurfaceRule RAW = (column, y) -> ROCK_SURFACE;

    /** The salt that TFC's soil transitions hash the block position with. */
    private static final long TRANSITION_SALT = 729375982L;

    private SurfaceRules() {}

    /** A rule that is always the same kind. */
    public static SurfaceRule of(SurfaceKind kind) {
        return (column, y) -> kind;
    }

    /** {@code SoilSurfaceState.transition}: {@code first} for the blocks whose hash is above a half, else {@code second}. */
    public static SurfaceRule transition(SurfaceRule first, SurfaceRule second) {
        return (column, y) -> (Hash.hash(TRANSITION_SALT, column.x(), y, column.z()) & 127) > 63
            ? first.kind(column, y) : second.kind(column, y);
    }

    /** {@code SoilSurfaceState.blobTransition}: {@code first} where the patch noise is positive, else {@code second}. */
    public static SurfaceRule blobTransition(SurfaceRule first, SurfaceRule second) {
        return (column, y) -> SurfaceNoise.PATCH.noise(column.x(), column.z()) > 0
            ? first.kind(column, y) : second.kind(column, y);
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

    /** {@code OverworldClimateModel.DEPTH_LEVEL} and {@code LAVA_LEVEL_TEMPERATURE}. */
    private static final float DEPTH_LEVEL = -64;
    private static final float LAVA_LEVEL_TEMPERATURE = 15f;

    /**
     * {@code SoilSurfaceState.getState}: the kind of a soil rule, from the rainfall and the temperature. The rain index and the
     * temperature index each pick one of the regions, and the lower of the two wins, so dry ground is gravel and cold ground
     * is snow whichever is more extreme.
     *
     * @param regions the {@value #REGIONS} regions, from the driest and coldest to the wettest and warmest
     */
    public static SurfaceRule climate(List<SurfaceRule> regions) {
        if (regions.size() != REGIONS) {
            throw new IllegalArgumentException("a soil table has " + REGIONS + " regions, got " + regions.size());
        }
        final int size = regions.size();
        return (column, y) -> {
            final float rainfall = column.groundWater();
            final float temperature = adjustTemperature(y, column.temperature(), column.seaLevel());
            final int x = column.x();
            final int z = column.z();
            final float noise = (float) SurfaceNoise.PATCH.noise(x, z);
            final float dither = (Hash.hash(TRANSITION_SALT, x, y, z) & 127) * (1f / 64) - 1;
            // Rain-controlled surface: the regions are picked by rainfall from 35 to 450 mm
            final int rainIndex = (int) Mth.clampedMap((double) (rainfall + 15 * noise + 10 * dither), 35, 450, 3, (double) (size - 0.01f));
            // Temperature-controlled surface: the regions are picked by temperature from -19 to -4 degrees
            final int tempIndex = (int) Mth.clampedMap((double) temperature, -19 + 0.5 * noise + 0.5 * dither, -4, 0, (double) (size - 0.01f));
            return regions.get(Math.min(rainIndex, tempIndex)).kind(column, y);
        };
    }

    /** The number of regions of a soil table in TFC. */
    private static final int REGIONS = 18;

    /** @return {@code head} followed by {@code tail} repeated {@code count} times. */
    private static List<SurfaceRule> table(List<SurfaceRule> head, SurfaceRule tail, int count) {
        final List<SurfaceRule> regions = new ArrayList<>(head);
        for (int i = 0; i < count; i++) {
            regions.add(tail);
        }
        return regions;
    }

    /**
     * {@code SoilSurfaceState.buildSurfaceType(GRASS, dry)}: the top of a grass surface. Snow when cold, the dry surface when
     * dry, coarse dirt and grass in between. The soil variants of one type share a kind, so the transitions to them are grass.
     */
    public static SurfaceRule grassTop(SurfaceRule dry) {
        final SurfaceRule coarse = of(COARSE_DIRT);
        final SurfaceRule soil = of(GRASS);
        return climate(table(List.of(
            of(SNOW),
            of(SNOW),
            transition(of(SNOW), dry),
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil),
            soil,
            blobTransition(soil, soil)
        ), soil, 9));
    }

    /** {@code SoilSurfaceState.buildMidType(DIRT, dry)}: the layer under the top of a dirt surface. */
    public static SurfaceRule midDirt(SurfaceRule dry) {
        final SurfaceRule coarse = of(COARSE_DIRT);
        final SurfaceRule soil = of(DIRT);
        return climate(table(List.of(
            of(PACKED_ICE),
            blobTransition(of(PACKED_ICE), dry),
            dry,
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil),
            soil,
            blobTransition(soil, soil)
        ), soil, 9));
    }

    /** {@code SoilSurfaceState.buildVolcanicSurfaceType(GRASS, dry)}: the top of a volcanic (andisol) grass surface. */
    public static SurfaceRule volcanicGrassTop(SurfaceRule dry) {
        final SurfaceRule coarse = of(COARSE_DIRT);
        final SurfaceRule soil = of(GRASS);
        return climate(table(List.of(
            of(SNOW),
            of(SNOW),
            transition(of(SNOW), dry),
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil),
            soil
        ), soil, 10));
    }

    /** {@code SoilSurfaceState.buildVolcanicMidType(DIRT, dry)}: the layer under a volcanic dirt surface. */
    public static SurfaceRule volcanicMidDirt(SurfaceRule dry) {
        final SurfaceRule coarse = of(COARSE_DIRT);
        final SurfaceRule soil = of(DIRT);
        return climate(table(List.of(
            of(PACKED_ICE),
            blobTransition(of(PACKED_ICE), dry),
            dry,
            dry,
            transition(dry, coarse),
            coarse,
            transition(coarse, soil),
            soil
        ), soil, 10));
    }

    /** {@code SoilSurfaceState.buildUnderType}: the rock under a surface, gravel once the rock is near the surface. */
    public static SurfaceRule underGravel() {
        final SurfaceRule gravel = of(GRAVEL);
        return climate(table(List.of(
            RAW,
            RAW,
            blobTransition(RAW, gravel)
        ), gravel, 15));
    }

    /**
     * {@code SurfaceStates.SHORE_SURFACE}: gravel where the beach noise is above a cutoff that falls with the temperature
     * (gravel is more common in cold climates), else sand.
     */
    public static SurfaceRule shoreSurface() {
        return (column, y) -> beachIsGravel(column) ? GRAVEL : SAND;
    }

    /** {@code SurfaceStates.SHORE_UNDERLAYER}: the rock where the beach is gravel, else sandstone. */
    public static SurfaceRule shoreUnderlayer() {
        return (column, y) -> beachIsGravel(column) ? ROCK_SURFACE : SANDSTONE;
    }

    /** The cutoff of the beach noise for a column: {@code Mth.clampedMap(temperature, -15, 25, -0.7, 0.7)}. */
    private static boolean beachIsGravel(SurfaceColumn column) {
        final float variantNoiseValue = (float) SurfaceNoise.SAND_GRAVEL_BEACH.noise(column.x(), column.z());
        final double gravelCutoff = Mth.clampedMap((double) column.temperature(), -15, 25, -0.7, 0.7);
        return variantNoiseValue > gravelCutoff;
    }
}
