/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/placement/StratovolcanoPlacement.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoise.java (stratovolcano: the cells, calculateEasing,
 * calculateCenter, getVolcanoVariant, maxSafeDiameterSquared), src/main/java/net/dries007/tfc/world/volcano/VolcanoVariants.java
 * (the variant names), src/main/java/net/dries007/tfc/world/volcano/CenteredFeatureNoiseSampler.java (checkCellRarity) and
 * src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java (the stratovolcanoes(...) frequency of each biome)
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.Hash;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.noise.Cellular2D;
import java.util.Locale;

/**
 * The cells of TFC's stratovolcanoes, as the {@code tfc:stratovolcano} placement reads them: the variant of a cell (Fuji,
 * Tahoma, Crater Lake, Kelimutu, batholith), its crater centers (Crater Lake's island and Kelimutu's extra craters are
 * offset from the cell's center) and how close a column is to them. The volcanoes' heights and surfaces are not part of this
 * (ledger). Immutable and thread-safe.
 */
public final class Stratovolcanoes {
    /** {@code CenteredFeatureNoise.STRATOVOLCANO_SPREAD_FACTOR}: a float widened to a double, as in TFC. */
    static final double SPREAD_FACTOR = 0.0021f;

    /** The names of {@code VolcanoVariants}. */
    public enum Variant {
        FUJI,
        TAHOMA,
        CRATER_LAKE,
        KELIMUTU,
        BATHOLITH;

        /** {@code VolcanoVariant.name()}, e.g. {@code crater_lake}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The fields of a {@code tfc:stratovolcano} placement modifier (codec defaults: no center, no offset center, variant
     * {@code all}, easing in (0, 1), hash in [0, 1]).
     *
     * @param variant the variant the cell must have, or null for {@code all}
     */
    record Placement(Variant variant, boolean center, boolean useOffsetCenter, float minEasing, float maxEasing, float hashMin,
                     float hashMax) {
        /** A {@code center: true} placement of a variant with a hash range. */
        static Placement center(Variant variant, boolean useOffsetCenter, float hashMin, float hashMax) {
            return new Placement(variant, true, useOffsetCenter, 0f, 1f, hashMin, hashMax);
        }

        /** A distance placement: easing above {@code minEasing}, with a hash range. */
        static Placement near(Variant variant, float minEasing, float hashMin, float hashMax) {
            return new Placement(variant, false, false, minEasing, 1f, hashMin, hashMax);
        }
    }

    private final Cellular2D cellNoise;

    /** @param levelSeed the world seed */
    public Stratovolcanoes(long levelSeed) {
        this.cellNoise = new Cellular2D(levelSeed, 2).spread(SPREAD_FACTOR);
    }

    /** {@code BiomeExtension.hasStratovolcanoes}. */
    public static boolean hasStratovolcanoes(int biome) {
        return frequency(biome) != 0;
    }

    /** {@code BiomeExtension.getCenteredFeatureFrequency}: 0 for a biome without stratovolcanoes. */
    public static float frequency(int biome) {
        if (biome == TfcBiomes.OCEANIC_VOLCANIC_ARC) {
            return 0.7f;
        }
        if (biome == TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS) {
            return 1f;
        }
        if (biome == TfcBiomes.VOLCANIC_MOUNTAINS || biome == TfcBiomes.VOLCANIC_ISLAND || biome == TfcBiomes.VOLCANIC_MOUNTAIN_LAKE
            || biome == TfcBiomes.VOLCANIC_OCEANIC_MOUNTAIN_LAKE || biome == TfcBiomes.ICE_SHEET_VOLCANIC_MOUNTAINS
            || biome == TfcBiomes.ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS || biome == TfcBiomes.GLACIATED_VOLCANIC_MOUNTAINS
            || biome == TfcBiomes.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS || biome == TfcBiomes.GLACIALLY_CARVED_VOLCANIC_MOUNTAINS
            || biome == TfcBiomes.GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS) {
            return 0.8f;
        }
        return 0;
    }

    /** {@code calculateEasing}: how close a column is to a cell's center in [0, 1] (0 in a cell without a volcano). */
    public float easing(int x, int z, float frequency) {
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        if (CinderCones.checkCellRarity(cell, frequency)) {
            return Mth.clamp(Mth.map((float) cell.f1(), 0, 0.23f, 1, 0), 0, 1);
        }
        return 0;
    }

    /** The variant of the cell that holds a column (TFC reads it whether or not the cell holds a volcano). */
    public Variant variantAt(int x, int z) {
        return variant(cellNoise.cell(x, z));
    }

    /** {@code getVolcanoVariant}: by the cell's room (the distance to the next cell) and its hash. */
    Variant variant(Cellular2D.Cell cell) {
        final double maxDiameter = Math.sqrt(Math.min(1, maxSafeDiameterSquared(cell)));
        if (maxDiameter >= 1) {
            final double noise = Hash.hashDouble(cell.noise(), 317);
            if (noise > 0.4) {
                return Variant.KELIMUTU;
            }
            return Variant.CRATER_LAKE;
        } else if (maxDiameter >= 0.7) {
            final double noise = Hash.hashDouble(cell.noise(), 317);
            if (noise > 0.7) {
                return Variant.KELIMUTU;
            }
            if (noise > 0.45) {
                return Variant.CRATER_LAKE;
            }
            if (noise > 0.2) {
                return Variant.TAHOMA;
            }
            return Variant.FUJI;
        } else if (maxDiameter < 0.4) {
            return Variant.BATHOLITH;
        } else if (maxDiameter < 0.55) {
            final double noise = Hash.hashDouble(cell.noise(), 317);
            if (noise > 0.5) {
                return Variant.BATHOLITH;
            } else {
                return Variant.FUJI;
            }
        } else {
            return Variant.FUJI;
        }
    }

    /** {@code CenteredFeatureNoise.maxSafeDiameterSquared}: the smallest second distance one block around the cell's center. */
    private double maxSafeDiameterSquared(Cellular2D.Cell cell) {
        double f2 = Math.min(cellNoise.cell(cell.x() + 1, cell.y()).f2(), cellNoise.cell(cell.x() - 1, cell.y()).f2());
        f2 = Math.min(f2, cellNoise.cell(cell.x(), cell.y() + 1).f2());
        f2 = Math.min(f2, cellNoise.cell(cell.x(), cell.y() - 1).f2());
        return f2;
    }

    /**
     * {@code StratovolcanoPlacement.getPositions} of a {@code center: true} placement at the origin column {@code (x, z)}:
     * the center column ({@link CinderCones#pack}) when it is in the origin's chunk and its biome has stratovolcanoes, or
     * {@link Long#MIN_VALUE}.
     */
    long center(Placement placement, VolcanoView view, int x, int z) {
        final int biome = view.biome(x, z);
        if (!placement.center() || !hasStratovolcanoes(biome)) {
            return Long.MIN_VALUE;
        }
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        final Variant variantAt = variant(cell);
        if (placement.variant() != null && placement.variant() != variantAt) {
            return Long.MIN_VALUE;
        }
        // Special handling for volcanoes with crater centers offset from the cell center
        if (placement.useOffsetCenter() && variantAt == Variant.CRATER_LAKE) {
            final double[] c = craterLakeCenter(cell);
            return offsetCenter(placement, view, x, z, c[0], c[1], Hash.hashDouble(cell.noise(), 3200));
        }
        if (placement.useOffsetCenter() && variantAt == Variant.KELIMUTU) {
            final double[] c = kelimutuCenter(cell, x, z);
            return offsetCenter(placement, view, x, z, c[0], c[1], c[3]);
        }
        // The hash is read here, as offset craters use their distinct hash values
        final double hash = Hash.hashDouble(cell.noise(), 3199);
        if (hash >= placement.hashMin() && hash <= placement.hashMax() && CinderCones.checkCellRarity(cell, frequency(biome))) {
            return sameChunkCenter(view, x, z, (int) cell.x(), (int) cell.y());
        }
        return Long.MIN_VALUE;
    }

    /**
     * {@code StratovolcanoPlacement.getPositions} of a distance placement at the column {@code (x, z)}: whether the column
     * passes (its biome, the cell's variant and hash, and the easing strictly inside the bounds; near an offset center,
     * above the lower bound only, as in TFC).
     */
    boolean test(Placement placement, VolcanoView view, int x, int z) {
        final int biome = view.biome(x, z);
        if (placement.center() || !hasStratovolcanoes(biome)) {
            return false;
        }
        final Cellular2D.Cell cell = cellNoise.cell(x, z);
        final Variant variantAt = variant(cell);
        if (placement.variant() != null && placement.variant() != variantAt) {
            return false;
        }
        if (placement.useOffsetCenter() && variantAt == Variant.CRATER_LAKE) {
            final double[] c = craterLakeCenter(cell);
            final double hash = Hash.hashDouble(cell.noise(), 3200);
            final double distance = (x - c[0]) * (x - c[0]) + (z - c[1]) * (z - c[1]);
            // Within 30 blocks of the center
            return hash >= placement.hashMin() && hash <= placement.hashMax()
                && Mth.clampedMap(distance, 0, 900, 1, 0) > placement.minEasing();
        }
        if (placement.useOffsetCenter() && variantAt == Variant.KELIMUTU) {
            final double[] c = kelimutuCenter(cell, x, z);
            // Within 50 blocks of the nearest center
            return c[3] >= placement.hashMin() && c[3] <= placement.hashMax()
                && Mth.clampedMap(c[2], 0, 2500, 1, 0) > placement.minEasing();
        }
        final double hash = Hash.hashDouble(cell.noise(), 3199);
        if (hash >= placement.hashMin() && hash <= placement.hashMax()) {
            final double easing = easing(x, z, frequency(biome));
            return easing > placement.minEasing() && easing < placement.maxEasing();
        }
        return false;
    }

    private long offsetCenter(Placement placement, VolcanoView view, int x, int z, double centerX, double centerZ, double hash) {
        if (hash >= placement.hashMin() && hash <= placement.hashMax()) {
            return sameChunkCenter(view, x, z, (int) centerX, (int) centerZ);
        }
        return Long.MIN_VALUE;
    }

    /** The center when it is in the chunk of {@code (x, z)} and its biome has stratovolcanoes. */
    private static long sameChunkCenter(VolcanoView view, int x, int z, int cx, int cz) {
        if (cx >> 4 == x >> 4 && cz >> 4 == z >> 4 && hasStratovolcanoes(view.biome(cx, cz))) {
            return CinderCones.pack(cx, cz);
        }
        return Long.MIN_VALUE;
    }

    /** {@code getCraterLakePositions}: the center of Crater Lake's island (Wizard Island), up to 45 blocks off the cell's. */
    static double[] craterLakeCenter(Cellular2D.Cell cell) {
        final double xOffset = -45 + 90 * Hash.hashDouble(cell.noise(), 6);
        final double zOffset = -45 + 90 * Hash.hashDouble(cell.noise(), 7);
        return new double[] {cell.x() + xOffset, cell.y() + zOffset};
    }

    /**
     * {@code getKelimutuPositions}: of Kelimutu's two or three craters, the nearest to the column {@code (x, z)}, as
     * {@code {centerX, centerZ, squared distance, hash}}.
     */
    static double[] kelimutuCenter(Cellular2D.Cell cell, int x, int z) {
        final double noise = cell.noise();
        final double center0X = cell.x();
        final double center0Z = cell.y();
        final double distance0 = (x - center0X) * (x - center0X) + (z - center0Z) * (z - center0Z);

        final double randOffsetX1 = 2 * (0.5 - Hash.hashDouble(noise, 68));
        final double randOffsetZ1 = 2 * (0.5 - Hash.hashDouble(noise, 69));
        final double xOffset1 = randOffsetX1 > 0 ? 20 + randOffsetX1 * 70 : -20 + randOffsetX1 * 70;
        final double zOffset1 = randOffsetZ1 > 0 ? 20 + randOffsetZ1 * 70 : -20 + randOffsetZ1 * 70;
        final double center1X = center0X + xOffset1;
        final double center1Z = center0Z + zOffset1;
        final double distance1 = (x - center1X) * (x - center1X) + (z - center1Z) * (z - center1Z);

        double distance;
        double centerX;
        double centerZ;
        double hash;
        if (distance0 < distance1) {
            distance = distance0;
            centerX = center0X;
            centerZ = center0Z;
            hash = Hash.hashDouble(noise, 3199);
        } else {
            distance = distance1;
            centerX = center1X;
            centerZ = center1Z;
            hash = Hash.hashDouble(noise, 3201);
        }

        if (Hash.hashDouble(noise, 1066) > 0.3) {
            final double randOffsetX2 = 2 * (0.5 - Hash.hashDouble(noise, 71));
            final double randOffsetZ2 = 2 * (0.5 - Hash.hashDouble(noise, 72));
            // 73 and 74 are TFC's factors on the negative side
            final double xOffset2 = randOffsetX2 > 0 ? 20 + randOffsetX2 * 70 : -20 + randOffsetX2 * 73;
            final double zOffset2 = randOffsetZ2 > 0 ? 20 + randOffsetZ2 * 70 : -20 + randOffsetZ2 * 74;
            final double center2X = center0X + xOffset2;
            final double center2Z = center0Z + zOffset2;
            final double distance2 = (x - center2X) * (x - center2X) + (z - center2Z) * (z - center2Z);

            if (distance2 < distance) {
                distance = distance2;
                centerX = center2X;
                centerZ = center2Z;
                hash = Hash.hashDouble(noise, 3202);
            }
        }
        return new double[] {centerX, centerZ, distance, hash};
    }
}
