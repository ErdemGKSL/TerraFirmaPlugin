/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/PowderSnowFeature.java @ c3b4d9536, with the data of
 * src/main/resources/data/tfc/worldgen/{configured_feature,placed_feature}/powder_snow.json, the tag
 * src/generated/resources/data/tfc/tags/block/powder_snow_replaceable.json and the vanilla 1.21 steps it names
 * (rarity_filter, in_square, heightmap WORLD_SURFACE_WG).
 */
package dev.tfp.core.worldgen.deposit;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.ore.Metaballs3D;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * TFC's {@code tfc:powder_snow} (soil discs tag): a metaball blob of powder snow in the snow and ice above the sea level of
 * cold places. Pure and thread-safe.
 */
public final class PowderSnow {
    /** The placed feature id, which seeds the chunk random. */
    public static final String FEATURE = "tfc:powder_snow";
    static final int RARITY = 2;
    /** {@code climate max_temperature -15}. */
    static final ClimateFilter CLIMATE = ClimateFilter.of(Float.NEGATIVE_INFINITY, -15f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);

    /** A position of the blob: the caller replaces the block there when it is snow ({@link DepositMaterial#SNOW}). */
    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z);
    }

    private final FeatureRandom random;

    public PowderSnow(long levelSeed) {
        this.random = new FeatureRandom(levelSeed, FEATURE);
    }

    /**
     * Adds the powder snow of chunk {@code (chunkX, chunkZ)} to {@code out}: the blobs of the 3 x 3 chunks around it (x outer),
     * where the biome at the origin chunk's centre lists the soil discs. A position in {@code replaced} is skipped; a replaced
     * position is added to it.
     */
    void chunk(int chunkX, int chunkZ, GroundView view, Deposits.MaterialAt materials, Set<Long> replaced, List<DepositBlock> out) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final int cx = chunkX + dx;
                final int cz = chunkZ + dz;
                if (!Deposits.SOIL_DISC_BIOMES.contains(biomeName(view, cx, cz))) {
                    continue;
                }
                place(random.chunk(cx, cz), cx << 4, cz << 4, view, (x, y, z) -> {
                    if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                        return;
                    }
                    final long key = Deposits.key(x, y, z);
                    if (!replaced.contains(key) && materials.material(x, y, z) == DepositMaterial.SNOW) {
                        replaced.add(key);
                        out.add(new DepositBlock(x, y, z, DepositKind.POWDER_SNOW, DepositMaterial.SNOW, null, FEATURE));
                    }
                });
            }
        }
    }

    /** The lower-case TFC biome name at the centre of a chunk, or an empty string. */
    static String biomeName(GroundView view, int cx, int cz) {
        final int biome = view.biome((cx << 4) + 8, (cz << 4) + 8);
        return biome >= 0 && biome < TfcBiomes.COUNT ? TfcBiomes.name(biome).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * One chunk's feature with TFC's draws in TFC's order: the rarity float, {@code in_square} (x, then z), the heightmap and
     * the climate filter (no draws, not fuzzy), then size {@code 6 + nextInt(4)}, a {@code Metaballs3D} of a fork of the
     * random (6 to 7 balls, sizes {@code -0.15 size} to {@code 0.4 size}, radius {@code 0.3 size}, in float) and x, y, z outer
     * to inner. {@code visitor} gets each position inside the blob and above the sea level.
     *
     * @return whether the blob was drawn
     */
    public static boolean place(XoroshiroRandomSource random, int blockX, int blockZ, GroundView view, Visitor visitor) {
        if (!(random.nextFloat() < 1f / RARITY)) {
            return false;
        }
        final int ox = blockX + random.nextInt(16);
        final int oz = blockZ + random.nextInt(16);
        final int oy = view.worldSurface(ox, oz);
        if (oy <= view.minY() || !CLIMATE.isValid(view, ox, oy, oz, random)) {
            return false;
        }
        final int size = 6 + random.nextInt(4);
        final long lo = random.nextLong();
        final Metaballs3D noise = new Metaballs3D(new XoroshiroRandomSource(lo, random.nextLong()), 6, 8,
            -0.15f * size, 0.4f * size, 0.3f * size);
        for (int x = -size; x <= size; x++) {
            for (int y = -size; y <= size; y++) {
                for (int z = -size; z <= size; z++) {
                    if (noise.inside(x, y, z) && oy + y > view.seaLevel()) {
                        visitor.visit(ox + x, oy + y, oz + z);
                    }
                }
            }
        }
        return true;
    }
}
