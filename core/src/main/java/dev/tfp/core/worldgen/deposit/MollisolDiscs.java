/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/SoilForestAreaFeature.java @ c3b4d9536, with SoilForestAreaConfig.java,
 * the data of src/main/resources/data/tfc/worldgen/{configured_feature,placed_feature}/mollisol_disc.json and the vanilla
 * 1.21 steps it names (in_square, count, heightmap WORLD_SURFACE_WG).
 */
package dev.tfp.core.worldgen.deposit;

import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.noise.OpenSimplex2D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TFC's {@code tfc:mollisol_disc} ({@code tfc:soil_forest_area}, last of the soil discs tag): in cold, wet, sparse forest the
 * entisol, aridisol and oxisol soils of the chunk (and 3 blocks into its neighbours) become mollisol where the forest density,
 * smoothed between the chunk corners and roughened by noise, is low ({@code inverted}). Uses no random after the origin.
 * Pure and thread-safe.
 */
public final class MollisolDiscs {
    /** The placed feature id, which seeds the chunk random. */
    public static final String FEATURE = "tfc:mollisol_disc";
    /** {@code SoilForestAreaFeature.noise}. */
    static final OpenSimplex2D NOISE = new OpenSimplex2D(913703L);
    static final int MIN_FOREST = 1;
    static final int MAX_FOREST = 2;
    static final int HEIGHT = 7;
    /** The codec defaults of {@code spread} and {@code noise_scale}. */
    static final float SPREAD = 0.05f;
    static final float NOISE_SCALE = 1f;
    static final boolean INVERTED = true;
    /** {@code climate}: -9 to 3 degrees, groundwater (rainfall: {@code ignore_rivers}) at least 250, forest density at most 3. */
    static final ClimateFilter CLIMATE = new ClimateFilter(-9f, 3f, 250f, Float.POSITIVE_INFINITY, -1f, 1f, false, 0, 3, -64, 320,
        false, true);

    /** The materials a mollisol column turns, as the terrain holds them (the soil variant is not known: the writer checks it). */
    static final Set<DepositMaterial> TERRAIN_SOILS = Set.of(DepositMaterial.DIRT, DepositMaterial.GRASS, DepositMaterial.MUD);
    /** The kinds of the earlier discs that keep a soil variant (clay, clay grass, rooted dirt). */
    static final Set<DepositKind> DISC_SOILS = Set.of(DepositKind.CLAY, DepositKind.CLAY_GRASS, DepositKind.ROOTED_DIRT);

    /** A column of the area: the caller turns the soils between {@code bottom} and {@code top}. */
    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int z, int bottom, int top);
    }

    private final FeatureRandom random;

    public MollisolDiscs(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /**
     * Adds the mollisol blocks of chunk {@code (chunkX, chunkZ)} to {@code out}: the areas of the 3 x 3 chunks around it (x
     * outer), where the biome at the origin chunk's centre lists the soil discs. A position becomes mollisol when the terrain
     * has a soil there and no earlier disc replaced it, or an earlier disc put a soil kind there; each position once.
     */
    void chunk(int chunkX, int chunkZ, GroundView view, Deposits.MaterialAt materials, List<DepositBlock> out) {
        final Map<Long, DepositKind> earlier = new HashMap<>();
        for (DepositBlock b : out) {
            earlier.put(Deposits.key(b.x(), b.y(), b.z()), b.kind());
        }
        final Set<Long> done = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int cx = chunkX + dx;
                final int cz = chunkZ + dz;
                if (!Deposits.SOIL_DISC_BIOMES.contains(PowderSnow.biomeName(view, cx, cz))) {
                    continue;
                }
                place(random.chunk(cx, cz), cx << 4, cz << 4, view, (x, z, bottom, top) -> {
                    if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                        return;
                    }
                    for (int y = bottom; y <= top; y++) {
                        final long key = Deposits.key(x, y, z);
                        final DepositKind before = earlier.get(key);
                        final DepositMaterial material = materials.material(x, y, z);
                        final boolean soil = before != null ? DISC_SOILS.contains(before) : material != null && TERRAIN_SOILS.contains(material);
                        if (soil && done.add(key)) {
                            out.add(new DepositBlock(x, y, z, DepositKind.MOLLISOL, material, null, FEATURE));
                        }
                    }
                });
            }
        }
    }

    /**
     * One chunk's feature: {@code in_square} (x, then z; the only draws), then {@code count 2} of that one origin (the area
     * does not depend on it and a second pass changes nothing, so it runs once), the heightmap and the climate filter with
     * the forest density of the origin's chunk, then {@code SoilForestAreaFeature.place}. The forest densities of the chunk
     * and its 8 neighbours must be known ({@link GroundView#forestDensity}), or nothing is placed.
     *
     * @param visitor gets each column whose soil density reaches 0.5, with the y range {@code placeColumn} walks
     * @return whether the area was drawn
     */
    public static boolean place(XoroshiroRandomSource random, int blockX, int blockZ, GroundView view, Visitor visitor) {
        final int ox = blockX + random.nextInt(16);
        final int oz = blockZ + random.nextInt(16);
        final int oy = view.worldSurface(ox, oz);
        if (oy <= view.minY()) {
            return false;
        }
        final int[] density = new int[9];
        for (int i = 0; i < 9; i++) {
            density[i] = view.forestDensity(ox + (i / 3 - 1) * 16, oz + (i % 3 - 1) * 16);
            if (density[i] < 0) {
                return false;
            }
        }
        if (!CLIMATE.isValid(view, ox, oy, oz, random, density[4])) {
            return false;
        }
        // density[(dx + 1) * 3 + (dz + 1)]: west is dx -1, north is dz -1
        final int densityCenter = density[4];
        final int densityNorth = density[3];
        final int densitySouth = density[5];
        final int densityEast = density[7];
        final int densityWest = density[1];
        final double density00 = 0.25 * (densityCenter + densityWest + densityNorth + density[0]);
        final double density10 = 0.25 * (densityCenter + densityEast + densityNorth + density[6]);
        final double density01 = 0.25 * (densityCenter + densityWest + densitySouth + density[2]);
        final double density11 = 0.25 * (densityCenter + densityEast + densitySouth + density[8]);
        final double deltaX = density10 - density00;
        final double deltaZ = density01 - density00;
        final double deltaXZ = density00 - density10 - density01 + density11;
        final int minX = blockX;
        final int minZ = blockZ;
        for (int dx = -3; dx <= 18; dx++) {
            final int dxc = Math.clamp(dx, 0, 15);
            final double varDX = polynomialHelper(dxc * (1.0 / 15));
            final double termDX = deltaX * varDX;
            for (int dz = -3; dz <= 18; dz++) {
                final int dzc = Math.clamp(dz, 0, 15);
                final double varDZ = polynomialHelper(dzc * (1.0 / 15));
                final double forestDensity = density00 + termDX + deltaZ * varDZ + deltaXZ * varDX * varDZ;
                final int x = minX + dx;
                final int z = minZ + dz;
                final double blobNoise = NOISE.noise(x * SPREAD, z * SPREAD) * NOISE_SCALE;
                double soilDensity = Mth.clampedMap(forestDensity + blobNoise, MIN_FOREST - 0.25, MAX_FOREST + 0.25, 0, 1);
                if (INVERTED) {
                    soilDensity = 1 - soilDensity;
                }
                if (dxc != dx || dzc != dz) {
                    final double edgeBlobNoise = 0.5 - Math.abs(NOISE.noise(x, z));
                    if (dxc != dx) {
                        soilDensity = Mth.clampedMap(Math.abs(dxc - dx), 0, 4, soilDensity, edgeBlobNoise);
                    }
                    if (dzc != dz) {
                        soilDensity = Mth.clampedMap(Math.abs(dzc - dz), 0, 4, soilDensity, edgeBlobNoise);
                    }
                }
                if (soilDensity >= 0.5) {
                    // placeColumn reads the origin chunk's heightmap, which wraps a column outside it to (x & 15, z & 15)
                    final int surfaceY = view.oceanFloor(minX + (x & 15), minZ + (z & 15));
                    visitor.visit(x, z, surfaceY - HEIGHT, surfaceY);
                }
            }
        }
        return true;
    }

    /** {@code SoilForestAreaFeature.polynomialHelper}: {@code 3x^2 - 2x^3}. */
    static double polynomialHelper(double x) {
        return 3 * x * x - 2 * x * x * x;
    }
}
