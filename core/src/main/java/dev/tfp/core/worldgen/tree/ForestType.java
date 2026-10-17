/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/chunkdata/ForestType.java @ c3b4d9536 (codecs and stream codec removed; the
 * vanilla ConstantInt / UniformInt providers are a min and max)
 */
package dev.tfp.core.worldgen.tree;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;

/**
 * The forest of a chunk: how many trees, bushes and groundcover tries the forest feature makes, how many tree species it
 * mixes, and the chance that it places anything at all. Chosen per chunk by the forest layer ({@link ForestLayers}); the
 * ordinals are TFC's (the layer works on them).
 */
public enum ForestType {
    GRASSLAND(SubType.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0),
    CLEARING(SubType.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0),
    SHRUBLAND(SubType.NONE, 0, 0, 0, 10, 10, 0, 1, 2, 7, 2, 1, 0),
    SPARSE(SubType.NONE, 0, 2, 2, 6, 6, 0, 0, 0, 2, 2, 0.08f, 0),
    SAVANNA_MONOCULTURE(SubType.SAVANNA, 1, 3, 3, 6, 6, 0, 0, 0, 2, 1, 0.55f, 0),
    SAVANNA_DIVERSE(SubType.SAVANNA, 1, 3, 3, 6, 6, 0, 0, 0, 2, 2, 0.65f, 0),
    SAVANNA_ALTERNATE(SubType.SAVANNA, 1, 3, 3, 6, 6, 0, 0, 0, 2, 3, 0.40f, 2),
    SAVANNA_SHRUB_MONOCULTURE(SubType.SAVANNA, 1, 1, 1, 6, 6, 0, 0, 3, 6, 1, 0.9f, 0),
    SAVANNA_SHRUB_DIVERSE(SubType.SAVANNA, 1, 1, 1, 6, 6, 0, 0, 3, 6, 2, 1f, 0),
    SAVANNA_SHRUB_ALTERNATE(SubType.SAVANNA, 1, 1, 1, 6, 6, 0, 0, 3, 6, 3, 0.8f, 2),
    PRIMARY_MONOCULTURE(SubType.PRIMARY, 3, 5, 5, 25, 25, 0, 1, 0, 0, 1, 1, 0),
    PRIMARY_DIVERSE(SubType.PRIMARY, 4, 7, 7, 40, 40, 0, 1, 0, 3, 2, 1, 0),
    PRIMARY_ALTERNATE(SubType.PRIMARY, 4, 7, 7, 40, 40, 0, 1, 0, 3, 3, 1, 2),
    SECONDARY_MONOCULTURE(SubType.SECONDARY, 3, 5, 5, 25, 25, 0, 0, 1, 2, 1, 1, 0),
    SECONDARY_MONOCULTURE_TALL(SubType.SECONDARY, 3, 5, 5, 25, 25, 0, 0, 1, 2, 1, 1, 0),
    SECONDARY_DIVERSE(SubType.SECONDARY, 3, 5, 5, 25, 25, 0, 0, 1, 2, 2, 1, 0),
    SECONDARY_BAMBOO(SubType.SECONDARY, 3, 1, 1, 25, 25, 0, 1, 0, 1, 2, 0.3f, 0),
    SECONDARY_DIVERSE_TALL(SubType.SECONDARY, 3, 5, 5, 25, 25, 0, 0, 1, 2, 2, 1, 0),
    SECONDARY_DENSE(SubType.SECONDARY, 4, 7, 7, 40, 40, 0, 1, 3, 3, 2, 1, 0),
    SECONDARY_DENSE_TALL(SubType.SECONDARY, 4, 7, 7, 40, 40, 0, 1, 3, 3, 2, 1, 0),
    SECONDARY_ALTERNATE(SubType.SECONDARY, 3, 5, 5, 25, 25, 0, 0, 1, 2, 3, 1, 2),
    EDGE_MONOCULTURE(SubType.EDGE, 2, 2, 2, 10, 10, 0, 1, 0, 1, 1, 1, 0),
    EDGE_DIVERSE(SubType.EDGE, 2, 2, 2, 10, 10, 0, 1, 0, 1, 2, 1, 0),
    EDGE_ALTERNATE(SubType.EDGE, 2, 2, 2, 10, 10, 0, 1, 0, 1, 3, 1, 2),
    EDGE_BAMBOO(SubType.EDGE, 2, 1, 1, 10, 10, 0, 1, 0, 1, 1, 0.7f, 0),
    DEAD_MONOCULTURE(SubType.DEAD, 2, 5, 5, 25, 25, 0, 0, 2, 4, 1, 1, 0),
    DEAD_DIVERSE(SubType.DEAD, 2, 5, 5, 25, 25, 0, 0, 2, 4, 2, 1, 0),
    DEAD_ALTERNATE(SubType.DEAD, 3, 4, 4, 40, 40, 0, 1, 0, 3, 3, 1, 2),
    DEAD_BAMBOO(SubType.DEAD, 3, 4, 4, 25, 25, 0, 1, 2, 4, 2, 1, 0);

    /** TFC's {@code ForestSubType}. */
    public enum SubType { NONE, PRIMARY, SECONDARY, EDGE, DEAD, SAVANNA }

    private static final List<ForestType> EDGE_DENSITY = List.of(EDGE_MONOCULTURE, EDGE_DIVERSE, EDGE_ALTERNATE, EDGE_BAMBOO);
    private static final List<ForestType> SECONDARY_FORESTS = List.of(SECONDARY_MONOCULTURE, SECONDARY_DENSE, SECONDARY_DIVERSE,
        SECONDARY_DENSE_TALL, SECONDARY_MONOCULTURE_TALL, SECONDARY_DIVERSE_TALL, SECONDARY_BAMBOO);
    private static final List<ForestType> PRIMARY_FORESTS = List.of(PRIMARY_DIVERSE, PRIMARY_MONOCULTURE);
    private static final List<ForestType> DEAD_FORESTS = List.of(DEAD_MONOCULTURE, DEAD_DIVERSE, DEAD_ALTERNATE, DEAD_BAMBOO);
    private static final List<ForestType> SAVANNA_FORESTS = List.of(SAVANNA_MONOCULTURE, SAVANNA_ALTERNATE, SAVANNA_DIVERSE,
        SAVANNA_SHRUB_MONOCULTURE, SAVANNA_SHRUB_ALTERNATE, SAVANNA_SHRUB_DIVERSE);

    private static final ForestType[] VALUES = values();

    static int edgeForestType(XoroshiroRandomSource random) {
        return EDGE_DENSITY.get(random.nextInt(EDGE_DENSITY.size())).ordinal();
    }

    static int secondaryForestType(XoroshiroRandomSource random) {
        return SECONDARY_FORESTS.get(random.nextInt(SECONDARY_FORESTS.size())).ordinal();
    }

    static int primaryForestType(XoroshiroRandomSource random) {
        return PRIMARY_FORESTS.get(random.nextInt(PRIMARY_FORESTS.size())).ordinal();
    }

    static int deadForestType(XoroshiroRandomSource random) {
        return DEAD_FORESTS.get(random.nextInt(DEAD_FORESTS.size())).ordinal();
    }

    static int savannaForestType(XoroshiroRandomSource random) {
        return SAVANNA_FORESTS.get(random.nextInt(SAVANNA_FORESTS.size())).ordinal();
    }

    /** {@code ForestType.valueOf(int)}: the type of an ordinal, {@link #GRASSLAND} when out of range. */
    public static ForestType valueOf(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : GRASSLAND;
    }

    private final SubType subType;
    private final int density;
    private final int minTrees;
    private final int maxTrees;
    private final int minGroundcover;
    private final int maxGroundcover;
    private final int minLeafPiles;
    private final int maxLeafPiles;
    private final int minBushes;
    private final int maxBushes;
    private final int maxTreeTypes;
    private final float perChunkChance;
    private final int alternateSize;

    ForestType(SubType subType, int density, int minTrees, int maxTrees, int minGroundcover, int maxGroundcover, int minLeafPiles,
               int maxLeafPiles, int minBushes, int maxBushes, int maxTreeTypes, float perChunkChance, int alternateSize) {
        this.subType = subType;
        this.density = density;
        this.minTrees = minTrees;
        this.maxTrees = maxTrees;
        this.minGroundcover = minGroundcover;
        this.maxGroundcover = maxGroundcover;
        this.minLeafPiles = minLeafPiles;
        this.maxLeafPiles = maxLeafPiles;
        this.minBushes = minBushes;
        this.maxBushes = maxBushes;
        this.maxTreeTypes = maxTreeTypes;
        this.perChunkChance = perChunkChance;
        this.alternateSize = alternateSize;
    }

    /** {@code getAlternate}: the alternating variant of the sub type (savanna and none keep their own type). */
    public ForestType alternate() {
        if (isSavanna()) {
            return this;
        }
        if (isEdge()) {
            return EDGE_ALTERNATE;
        }
        if (isSecondary()) {
            return SECONDARY_ALTERNATE;
        }
        if (isPrimary()) {
            return PRIMARY_ALTERNATE;
        }
        if (isDead()) {
            return DEAD_ALTERNATE;
        }
        return this;
    }

    public SubType subType() {
        return subType;
    }

    public boolean isPrimary() {
        return subType == SubType.PRIMARY;
    }

    public boolean isEdge() {
        return subType == SubType.EDGE;
    }

    public boolean isSecondary() {
        return subType == SubType.SECONDARY;
    }

    public boolean isDead() {
        return subType == SubType.DEAD;
    }

    public boolean isNone() {
        return subType == SubType.NONE;
    }

    public boolean isSavanna() {
        return subType == SubType.SAVANNA;
    }

    /** {@code sampleTrees}: a {@code ConstantInt} draws nothing, a {@code UniformInt} draws {@code nextInt(max - min + 1)}. */
    public int sampleTrees(XoroshiroRandomSource random) {
        return sample(random, minTrees, maxTrees);
    }

    public int sampleGroundcover(XoroshiroRandomSource random) {
        return sample(random, minGroundcover, maxGroundcover);
    }

    public int sampleLeafPiles(XoroshiroRandomSource random) {
        return sample(random, minLeafPiles, maxLeafPiles);
    }

    public int sampleBushes(XoroshiroRandomSource random) {
        return sample(random, minBushes, maxBushes);
    }

    /** The most trees a chunk of this type tries to place. */
    public int maxTrees() {
        return maxTrees;
    }

    public int density() {
        return density;
    }

    public int maxTreeTypes() {
        return maxTreeTypes;
    }

    public float perChunkChance() {
        return perChunkChance;
    }

    public int alternateSize() {
        return alternateSize;
    }

    /** Vanilla's {@code Mth.randomBetweenInclusive} for a uniform provider; a constant provider ({@code min == max}) draws nothing. */
    private static int sample(XoroshiroRandomSource random, int min, int max) {
        return min == max ? min : random.nextInt(max - min + 1) + min;
    }
}
