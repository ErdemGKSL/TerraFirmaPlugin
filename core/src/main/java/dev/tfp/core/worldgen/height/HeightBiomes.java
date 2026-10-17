/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (each biome's heightmap, carving, BiomeBlendType,
 * ShoreBlendType, shore, salty, setShoreBaseHeight and aquiferHeightOffset), with the defaults and the sampler factories of
 * src/main/java/net/dries007/tfc/world/biome/BiomeBuilder.java (heightmap: the float height of BiomeNoiseSampler.fromHeightNoise;
 * carving: the carving sampler's double height and the aquifer height of sea level - 16) and src/main/java/net/dries007/tfc/world/biome/BiomeBlendType.java.
 * Generated from TFC's source by a script (the rows are TFC's builder calls); edit with care.
 */
package dev.tfp.core.worldgen.height;

import static dev.tfp.core.worldgen.chunk.ChunkData.SEA_LEVEL_Y;

import dev.tfp.core.worldgen.biome.BiomeNoise;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.noise.Noise2D;
import java.util.function.LongFunction;

/**
 * The height properties of each TFC biome id ({@link TfcBiomes} order): the height map that TFC's {@code BiomeBuilder.heightmap}
 * registers, and the blend types the column height filler sums the biome weights by. All height maps take the level seed
 * ({@code seed.seed()}), so they do not depend on the order they are built in. The river biome has no height map: TFC samples
 * the biome weights without rivers.
 */
public final class HeightBiomes {
    /** {@code BiomeBlendType}: how a biome's weight is grouped when the chunk's biome weights are composed. */
    public enum BiomeBlendType {
        LAND, OCEAN, LAKE;

        public static final int SIZE = values().length;
    }

    /**
     * One biome's height properties.
     *
     * @param height          the height map factory from the level seed, or null (the river biome)
     * @param carving         whether a carving sampler wraps the height map: its height is the map's double value, where the
     *                        plain sampler keeps a {@code float}
     * @param shoreBaseHeight {@code BiomeExtension.getShoreBaseHeight()}
     * @param aquiferHeightOffset the {@code aquiferHeightOffset} added to the sampler height for the aquifer lookahead (0 if
     *                        none; unused for a carving biome, whose aquifer height is sea level - 16)
     */
    public record Entry(LongFunction<Noise2D> height, boolean carving, BiomeBlendType blendType, ShoreBlendType shoreBlendType,
                        boolean shore, boolean salty, int shoreBaseHeight, double aquiferHeightOffset) {}

    private static final Entry[] ENTRIES = {
        new Entry(seed -> HeightNoise.ocean(seed, -26, -12), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 0 OCEAN
        new Entry(seed -> HeightNoise.ocean(seed, -26, -12), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 1 OCEAN_ATOLLS
        new Entry(seed -> HeightNoise.ocean(seed, -16, -8), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 2 OCEAN_REEF
        new Entry(seed -> HeightNoise.ocean(seed, -46, -30), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 3 DEEP_OCEAN
        new Entry(seed -> HeightNoise.ocean(seed, -46, -30), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 4 DEEP_OCEAN_ATOLLS
        new Entry(seed -> HeightNoise.oceanTrench(seed, -60, -46), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 5 DEEP_OCEAN_TRENCH
        new Entry(seed -> HeightNoise.ocean(seed, -26, -12), false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 6 OCEANIC_VOLCANIC_ARC
        new Entry(HeightNoise::oceanRidge, false, BiomeBlendType.OCEAN, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 7 OCEAN_RIDGE
        new Entry(seed -> HeightNoise.riftValley(seed, 2, 25, false), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 8 RIFT_VALLEY
        new Entry(seed -> HeightNoise.riftValley(seed, -10, 25, true), false, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 9 RIFT_LAKE
        new Entry(seed -> HeightNoise.ridgeMountains(seed, 18, 130, 0.3f, 170, 35), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 10 COLLISIONAL_MOUNTAINS
        new Entry(seed -> HeightNoise.hills(seed, -2, 4), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -12), // 11 PLAINS
        new Entry(seed -> HeightNoise.hills(seed, -5, 16), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -8), // 12 HILLS
        new Entry(HeightNoise::lowlands, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 13 LOWLANDS
        new Entry(HeightNoise::lowlands, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -16), // 14 SALT_MARSH
        new Entry(seed -> HeightNoise.canyons(seed, -8, 21), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 15 LOW_CANYONS
        new Entry(seed -> HeightNoise.hills(seed, -2, 4), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 16 RIVER_VALLEY
        new Entry(seed -> HeightNoise.hills(seed, -5, 28), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 17 ROLLING_HILLS
        new Entry(seed -> HeightNoise.sharpHills(seed, -3, 28), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 18 HIGHLANDS
        new Entry(seed -> HeightNoise.badlands(seed, 22, 19.5f), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 19 BADLANDS
        new Entry(seed -> HeightNoise.hills(seed, 20, 30), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 20 PLATEAU
        new Entry(seed -> HeightNoise.hills(seed, 20, 30), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 21 PLATEAU_WIDE
        new Entry(seed -> HeightNoise.mountains(seed, 16, 40, 1f), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 22 OLD_MOUNTAINS
        new Entry(seed -> HeightNoise.ridgeMountains(seed, 10, 90, 0.4f, 140, 40), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 23 MOUNTAINS
        new Entry(seed -> HeightNoise.ridgeMountains(seed, 10, 80, 0.4f, 130, 40), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 24 VOLCANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.mountains(seed, -16, 60, 1f), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -8), // 25 OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.mountains(seed, -24, 50, 1f), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -8), // 26 VOLCANIC_OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.canyons(seed, -2, 40), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 27 CANYONS
        new Entry(HeightNoise::rockyIslands, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, 0), // 28 GUANO_ISLAND
        new Entry(seed -> HeightNoise.hills(seed, -5, 28), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -8), // 29 VOLCANIC_ISLAND
        new Entry(seed -> HeightNoise.mountains(seed, -24, 50, 1f), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -8), // 30 VOLCANIC_MOUNTAIN_ISLANDS
        new Entry(HeightNoise::shore, false, BiomeBlendType.LAND, ShoreBlendType.SANDY, true, true, SEA_LEVEL_Y + (-4), -16), // 31 SHORE
        new Entry(HeightNoise::shore, false, BiomeBlendType.OCEAN, ShoreBlendType.SANDY, true, true, SEA_LEVEL_Y + (-4), -16), // 32 TIDAL_FLATS
        new Entry(seed -> HeightNoise.hills(seed, 10, 30), false, BiomeBlendType.LAND, ShoreBlendType.SEA_STACKS, true, true, SEA_LEVEL_Y + (-6), -40), // 33 SEA_STACKS
        new Entry(seed -> HeightNoise.constant(0), false, BiomeBlendType.LAND, ShoreBlendType.UPPER_TERRACE, true, true, SEA_LEVEL_Y, -40), // 34 TERRACE_UPPER
        new Entry(seed -> HeightNoise.constant(0), false, BiomeBlendType.LAND, ShoreBlendType.LOWER_TERRACE, true, true, SEA_LEVEL_Y, -40), // 35 TERRACE_LOWER
        new Entry(seed -> HeightNoise.hills(seed, 20, 30), false, BiomeBlendType.LAND, ShoreBlendType.SETBACK_CLIFFS, true, true, SEA_LEVEL_Y, -40), // 36 SETBACK_CLIFFS
        new Entry(seed -> HeightNoise.constant(0), false, BiomeBlendType.LAND, ShoreBlendType.DUNES, true, true, SEA_LEVEL_Y, -40), // 37 COASTAL_DUNES
        new Entry(seed -> HeightNoise.constant(-15), false, BiomeBlendType.LAND, ShoreBlendType.ROCKY_SHORES, true, true, SEA_LEVEL_Y, -40), // 38 ROCKY_SHORES
        new Entry(HeightNoise::shore, false, BiomeBlendType.LAND, ShoreBlendType.EMBAYMENTS, true, true, SEA_LEVEL_Y, -40), // 39 EMBAYMENTS
        new Entry(HeightNoise::lake, false, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 40 LAKE
        new Entry(null, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 41 RIVER
        new Entry(seed -> HeightNoise.ridgeMountains(seed, 10, 90, 0.4f, 140, 40), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 42 MOUNTAIN_LAKE (carving: undergroundLakes)
        new Entry(seed -> HeightNoise.ridgeMountains(seed, 10, 80, 0.4f, 130, 40), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 43 VOLCANIC_MOUNTAIN_LAKE (carving: undergroundLakes)
        new Entry(seed -> HeightNoise.mountains(seed, -16, 60, 1f), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 44 OLD_MOUNTAIN_LAKE (carving: undergroundLakes)
        new Entry(seed -> HeightNoise.mountains(seed, -16, 60, 1f), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, 0), // 45 OCEANIC_MOUNTAIN_LAKE (carving: undergroundLakes)
        new Entry(seed -> HeightNoise.mountains(seed, -24, 50, 1f), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, 0), // 46 VOLCANIC_OCEANIC_MOUNTAIN_LAKE (carving: undergroundLakes)
        new Entry(seed -> HeightNoise.hills(seed, 20, 30), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 47 PLATEAU_LAKE (carving: undergroundLakes)
        new Entry(HeightNoise::flats, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 48 MUD_FLATS
        new Entry(HeightNoise::saltFlats, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 49 SALT_FLATS
        new Entry(seed -> HeightNoise.dunes(seed, 2, 16), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 50 DUNE_SEA
        new Entry(seed -> HeightNoise.dunes(seed, 2, 16), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 51 GRASSY_DUNES
        new Entry(seed -> HeightNoise.canyons(seed, 8, 60), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 52 WHORLED_CANYONS
        new Entry(seed -> HeightNoise.stairCanyons(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 53 STAIR_STEP_CANYONS
        new Entry(seed -> HeightNoise.mesas(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 54 MESAS
        new Entry(seed -> HeightNoise.buttes(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 55 BUTTES
        new Entry(seed -> HeightNoise.hoodoos(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -24), // 56 HOODOOS
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.hills(seed, 22, 32), 16).max(HeightNoise.canyons(seed, 0, 52).spread(1.5)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 57 ROCKY_PLATEAU
        new Entry(seed -> HeightNoise.fengcongPlains(seed, 0, 90), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 58 TOWER_KARST_PLAINS
        new Entry(seed -> HeightNoise.mogotes(seed, 6, 110), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 59 TOWER_KARST_CANYONS
        new Entry(seed -> HeightNoise.fenglinPlains(seed, 0, 90), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 60 TOWER_KARST_HILLS
        new Entry(seed -> HeightNoise.mogotes(seed, 6, 110), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 61 TOWER_KARST_HIGHLANDS
        new Entry(seed -> HeightNoise.fenglinPlains(seed, -8, 98), false, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 62 TOWER_KARST_LAKE
        new Entry(seed -> HeightNoise.fenglinPlains(seed, -12, 102), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -16), // 63 TOWER_KARST_BAY
        new Entry(seed -> HeightNoise.burren(seed, HeightNoise.hills(seed, 22, 32), 1.4), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 64 BURREN_PLATEAU
        new Entry(seed -> HeightNoise.burren(seed, HeightNoise.badlands(seed, 22, 19.5f), 1.0), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 65 BURREN_BADLANDS
        new Entry(seed -> HeightNoise.burren(seed, HeightNoise.badlands(seed, 35, 33f), 1.0), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 66 BURREN_BADLANDS_TALL
        new Entry(seed -> HeightNoise.burren(seed, HeightNoise.drumlins(seed), 1.5), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 67 BURREN_ROCHE_MOUTONEE
        new Entry(seed -> HeightNoise.burren(seed, HeightNoise.hills(seed, 6, 12), 1.5), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 68 BURREN_PLAINS
        new Entry(seed -> HeightNoise.shilin(seed, HeightNoise.hills(seed, 4, 10), 28), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 69 SHILIN_PLAINS
        new Entry(seed -> HeightNoise.shilin(seed, HeightNoise.canyons(seed, -2, 30), 26), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 70 SHILIN_CANYONS
        new Entry(seed -> HeightNoise.shilin(seed, HeightNoise.hills(seed, -2, 16), 26), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 71 SHILIN_HILLS
        new Entry(seed -> HeightNoise.shilin(seed, HeightNoise.sharpHills(seed, 0, 32), 32), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 72 SHILIN_HIGHLANDS
        new Entry(seed -> HeightNoise.shilin(seed, HeightNoise.hills(seed, 12, 22), 32), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 73 SHILIN_PLATEAU
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.hills(seed, 4, 10), 6), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 74 DOLINE_PLAINS
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.hills(seed, -5, 16), 10), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 75 DOLINE_HILLS
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.hills(seed, -5, 28), 18), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 76 DOLINE_ROLLING_HILLS
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.sharpHills(seed, -3, 20), 22), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 77 DOLINE_HIGHLANDS
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.hills(seed, 22, 32), 22), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 78 DOLINE_PLATEAU
        new Entry(seed -> HeightNoise.bowlDolines(seed, HeightNoise.canyons(seed, -2, 34), 15), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 79 DOLINE_CANYONS
        new Entry(seed -> HeightNoise.hills(seed, 4, 10), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 80 CENOTE_PLAINS (carving: cenotes)
        new Entry(seed -> HeightNoise.hills(seed, -5, 16), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 81 CENOTE_HILLS (carving: cenotes)
        new Entry(seed -> HeightNoise.hills(seed, -5, 28), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 82 CENOTE_ROLLING_HILLS (carving: cenotes)
        new Entry(seed -> HeightNoise.canyons(seed, 2, 28), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 83 CENOTE_CANYONS (carving: cenotes)
        new Entry(seed -> HeightNoise.sharpHills(seed, 0, 24), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 84 CENOTE_HIGHLANDS (carving: cenotes)
        new Entry(seed -> HeightNoise.hills(seed, 20, 30), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 85 CENOTE_PLATEAU (carving: cenotes)
        new Entry(HeightNoise::mogotePlateau, true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 86 EXTREME_DOLINE_PLATEAU (carving: cenotes)
        new Entry(seed -> HeightNoise.tiankeng(seed, HeightNoise.mountains(seed, 16, 40, 1.3f)), true, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 87 EXTREME_DOLINE_MOUNTAINS (carving: cenotes)
        new Entry(seed -> HeightNoise.activeShieldVolcano(seed, BiomeNoise.activeHotSpots(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 88 ACTIVE_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.dormantShieldVolcano(seed, BiomeNoise.dormantHotSpots(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 89 DORMANT_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.extinctShieldVolcano(seed, BiomeNoise.extinctHotSpots(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 90 EXTINCT_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.ancientShieldVolcano(seed, 90, 130, BiomeNoise.ancientHotSpots(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 91 ANCIENT_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.sunkenShieldVolcano(seed, BiomeNoise.ancientHotSpots(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -16), // 92 SUNKEN_SHIELD_VOLCANO
        new Entry(HeightNoise::shore, false, BiomeBlendType.LAND, ShoreBlendType.EMBAYMENTS, true, true, SEA_LEVEL_Y, -16), // 93 SHIELD_VOLCANO_SHORE
        new Entry(HeightNoise::shore, false, BiomeBlendType.LAND, ShoreBlendType.SANDY, true, true, SEA_LEVEL_Y, -16), // 94 OLD_SHIELD_VOLCANO_SHORE
        new Entry(seed -> HeightNoise.iceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 95 ICE_SHEET
        new Entry(seed -> HeightNoise.montaneIceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)).max(HeightNoise.glacialCirques(seed).addConstant(39)).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 96 ICE_SHEET_MOUNTAINS
        new Entry(seed -> HeightNoise.oceanicIceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)).max(HeightNoise.glacialCirques(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 97 ICE_SHEET_OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.montaneIceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)).max(HeightNoise.glacialCirques(seed).addConstant(39)).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 98 ICE_SHEET_VOLCANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.oceanicIceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)).max(HeightNoise.glacialCirques(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 99 ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.glaciatedShieldVolcano(seed, BiomeNoise.hotSpotIntensity(seed)).max(HeightNoise.shieldVolcanoIceSheetSurface(seed, BiomeNoise.hotSpotIntensity(seed)).add(HeightNoise.glacialSurfaceTexture(seed))), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 100 ICE_SHEET_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.iceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 101 ICE_SHEET_TUYAS
        new Entry(seed -> HeightNoise.iceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)), true, BiomeBlendType.LAKE, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 102 SUBGLACIAL_LAKE (carving: undergroundLakes)
        new Entry(HeightNoise::glacialBase, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 103 ICE_SHEET_EDGE
        new Entry(HeightNoise::glacialBase, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 104 ICE_SHEET_TUYAS_EDGE
        new Entry(seed -> HeightNoise.oceanicIceSheetSurfaceHeight(seed).add(HeightNoise.glacialSurfaceTexture(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, 0), // 105 ICE_SHEET_OCEANIC
        new Entry(seed -> HeightNoise.glacialCirquesIceSurfaceHeight(seed).max(HeightNoise.glacialCirques(seed)), false, BiomeBlendType.LAND, ShoreBlendType.CLASSIC, true, true, SEA_LEVEL_Y + (-16), -24), // 106 ICE_SHEET_OCEANIC_MOUNTAINS_EDGE
        new Entry(seed -> HeightNoise.glacialCirques(seed).addConstant(39).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 107 ICE_SHEET_MOUNTAINS_EDGE
        new Entry(seed -> HeightNoise.glacialCirques(seed).addConstant(39).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 108 GLACIATED_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 109 GLACIATED_OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed).addConstant(39).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed).addConstant(39)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 110 GLACIATED_VOLCANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed).max(HeightNoise.glacialCirquesIceSurfaceHeight(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 111 GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS
        new Entry(HeightNoise::lake, false, BiomeBlendType.LAKE, ShoreBlendType.CLASSIC, true, false, SEA_LEVEL_Y + (-16), -16), // 112 MELTWATER_LAKE
        new Entry(seed -> HeightNoise.glaciatedShieldVolcano(seed, BiomeNoise.hotSpotIntensity(seed)).max(HeightNoise.shieldVolcanoGlacierSurface(seed, BiomeNoise.hotSpotIntensity(seed)).add(HeightNoise.glacialSurfaceTexture(seed))), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 113 GLACIATED_SHIELD_VOLCANO
        new Entry(seed -> HeightNoise.ocean(seed, -16, -8), false, BiomeBlendType.LAND, ShoreBlendType.CLASSIC, true, true, SEA_LEVEL_Y + (-12), -24), // 114 ICE_SHEET_SHORE
        new Entry(seed -> HeightNoise.glacialCirques(seed).addConstant(39), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 115 GLACIALLY_CARVED_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 116 GLACIALLY_CARVED_OCEANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed).addConstant(39), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 117 GLACIALLY_CARVED_VOLCANIC_MOUNTAINS
        new Entry(seed -> HeightNoise.glacialCirques(seed), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, true, SEA_LEVEL_Y, -24), // 118 GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS
        new Entry(HeightNoise::drumlins, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 119 DRUMLINS
        new Entry(HeightNoise::drumlins, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 120 TUYAS
        new Entry(HeightNoise::knobAndKettle, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, -16), // 121 KNOB_AND_KETTLE
        new Entry(seed -> HeightNoise.hills(seed, -4, 3).add(HeightNoise.patternedGround(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 122 PATTERNED_GROUND
        new Entry(HeightNoise::invertedPatternedGround, false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 123 INVERTED_PATTERNED_GROUND
        new Entry(seed -> HeightNoise.hills(seed, -2, 4).add(HeightNoise.stoneCircles(seed)), false, BiomeBlendType.LAND, ShoreBlendType.NONE, false, false, SEA_LEVEL_Y, 0), // 124 STONE_CIRCLES
    };

    private HeightBiomes() {}

    /** @return the height properties of a TFC biome id. */
    public static Entry get(int biomeId) {
        return ENTRIES[biomeId];
    }

    /** The number of entries ({@link TfcBiomes#COUNT}). */
    static int size() {
        return ENTRIES.length;
    }
}
