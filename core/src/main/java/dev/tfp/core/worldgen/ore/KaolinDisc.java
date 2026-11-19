/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/vein/KaolinDiscVeinFeature.java @ c3b4d9536 (getStateToGenerate), with
 * the roll of VeinFeature.java (place) of the same package.
 */
package dev.tfp.core.worldgen.ore;

import dev.tfp.core.worldgen.random.PositionalRandom;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.List;

/**
 * The kaolin clay disc ({@code tfc:kaolin_disc_vein}): a projected disc vein that turns the soil near the surface into kaolin
 * clay, in three colours by depth. Pure, immutable and thread-safe. The shape is {@link Veins}'; the roll is positional, as in
 * {@link OreVeins} (ledger).
 *
 * <p>In TFC the feature runs only in the biomes of {@code #tfc:in_biome/veins/kaolin}, and its placed feature asks for a
 * climate (at least 18 °C average temperature and 300 mm groundwater at the chunk); the caller checks both.
 */
public final class KaolinDisc {
    /** {@code placed_feature/vein/kaolin_disc}: the least average temperature of {@code tfc:climate}. */
    public static final float MIN_TEMPERATURE = 18f;
    /** {@code placed_feature/vein/kaolin_disc}: the least groundwater of {@code tfc:climate}. */
    public static final float MIN_GROUNDWATER = 300f;

    /** What the block at a position is, for the kaolin disc. */
    public enum Soil {
        /** A block of {@code #tfc:grass}. */
        GRASS,
        /** A block of {@code #tfc:kaolin_clay_replaceable} that is not grass (dirt, stones, gravels, snow blocks). */
        REPLACEABLE,
        /** Anything else: kept. */
        OTHER
    }

    /** The blocks the kaolin disc writes. */
    public enum Clay {
        KAOLIN_CLAY_GRASS("kaolin_clay_grass"),
        RED_KAOLIN_CLAY("red_kaolin_clay"),
        PINK_KAOLIN_CLAY("pink_kaolin_clay"),
        WHITE_KAOLIN_CLAY("white_kaolin_clay");

        private final String path;

        Clay(String path) {
            this.path = path;
        }

        /** The block's path, as in TFC ({@code tfc:<path>}). */
        public String path() {
            return path;
        }
    }

    /**
     * {@code KaolinDiscVeinFeature.getStateToGenerate}: the clay that replaces a block, or null to keep it.
     *
     * @param relativeY the position's y minus the projected height, minus the vein centre's y
     */
    public static Clay stateToGenerate(Soil soil, int relativeY) {
        if (soil == Soil.GRASS) {
            return Clay.KAOLIN_CLAY_GRASS;
        } else if (soil == Soil.REPLACEABLE) {
            if (relativeY > -2) {
                return Clay.RED_KAOLIN_CLAY;
            }
            if (relativeY > -4) {
                return Clay.PINK_KAOLIN_CLAY;
            }
            return Clay.WHITE_KAOLIN_CLAY;
        }
        return null;
    }

    private final long levelSeed;
    private final DiscVeinConfig config;
    private final PositionalRandom rolls;

    public KaolinDisc(long levelSeed, DiscVeinConfig config) {
        if (!config.kaolin()) {
            throw new IllegalArgumentException(config.name() + " is not a kaolin disc");
        }
        this.levelSeed = levelSeed;
        this.config = config;
        this.rolls = PositionalRandom.fork(new XoroshiroRandomSource(levelSeed ^ config.seed()));
    }

    public DiscVeinConfig config() {
        return config;
    }

    /** The kaolin discs that reach a chunk. */
    public Chunk chunk(int chunkX, int chunkZ, OreVeins.WorldView view) {
        final List<OreVeins.Placed> placed = new ArrayList<>();
        for (Vein vein : Veins.nearbyVeins(levelSeed, chunkX, chunkZ, config)) {
            final OreVeins.Placed p = OreVeins.Placed.of(chunkX, chunkZ, vein, config);
            if (p != null) {
                placed.add(p);
            }
        }
        return new Chunk(chunkX, chunkZ, placed.toArray(OreVeins.Placed[]::new), view);
    }

    /** The decisions of one chunk. Any thread. */
    public final class Chunk {
        private final int chunkX;
        private final int chunkZ;
        private final OreVeins.Placed[] veins;
        private final OreVeins.WorldView view;

        private Chunk(int chunkX, int chunkZ, OreVeins.Placed[] veins, OreVeins.WorldView view) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.veins = veins;
            this.view = view;
        }

        /** Whether no disc reaches this chunk. */
        public boolean isEmpty() {
            return veins.length == 0;
        }

        /**
         * The clay that replaces the block at a position of this chunk, or null to keep it. A block that an earlier disc
         * turned into clay is not replaced again, as in TFC (clay is neither grass nor replaceable).
         */
        public Clay decide(int x, int y, int z, Soil soil) {
            if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                throw new IllegalArgumentException("position " + x + "," + z + " is outside chunk " + chunkX + "," + chunkZ);
            }
            if (soil == Soil.OTHER) {
                return null;
            }
            XoroshiroRandomSource roll = null;
            for (OreVeins.Placed p : veins) {
                final float chance = p.chance(x, y, z, config, view);
                if (chance <= 0) {
                    continue;
                }
                if (roll == null) {
                    roll = rolls.at(x, y, z);
                }
                if (roll.nextFloat() < chance) {
                    final Clay clay = stateToGenerate(soil, p.relativeY(x, y, z, config, view));
                    if (clay != null) {
                        return clay;
                    }
                }
            }
            return null;
        }
    }
}
