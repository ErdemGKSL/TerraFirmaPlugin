/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/TickingPlantBlockEntity.java @ c3b4d9536 (stemPos, the update counter),
 *       src/main/java/net/dries007/tfc/common/blockentities/BerryBushBlockEntity.java @ c3b4d9536 (stemPos, lastPickedTick of a leaf)
 */
package dev.tfp.core.berry;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The stored state of a fruit tree block that has one (sapling, growing branch, leaves): where the tree's stem is (TFC {@code stemPos},
 * the base the climate is read at), the player tick its counter counts from, the last pick (leaves) and the last environment sample. The
 * stage, sapling count, natural flag, lifecycle and persistence live in the block state.
 *
 * @param stemX      the stem's block position
 * @param lastUpdate the counter's origin; a banked past value stands for cycles that are still owed
 * @param lastPicked the player tick of the last pick, {@link BerryState#NEVER_PICKED} for none
 */
public record FruitTreeState(int stemX, int stemY, int stemZ, long lastUpdate, long lastPicked, int hydration, float temperature,
                             boolean northern) {
    public static final VersionedCodec<FruitTreeState> CODEC = VersionedCodec.<FruitTreeState>builder("fruit_tree", 1)
        .build((s, out) -> out.putInt("sx", s.stemX()).putInt("sy", s.stemY()).putInt("sz", s.stemZ())
                .putLong("updated", s.lastUpdate()).putLong("picked", s.lastPicked())
                .putInt("hydration", s.hydration()).putFloat("temperature", s.temperature()).putBoolean("northern", s.northern()),
            in -> new FruitTreeState(in.getInt("sx", 0), in.getInt("sy", 0), in.getInt("sz", 0), in.getLong("updated", 0L),
                in.getLong("picked", BerryState.NEVER_PICKED), in.getInt("hydration", 50), in.getFloat("temperature", 10f),
                in.getBoolean("northern", true)));

    public FruitTreeState {
        if (hydration < 0 || hydration > 100) {
            throw new IllegalArgumentException("hydration must be 0..100: " + hydration);
        }
    }

    public FruitTreeState withLastUpdate(long tick) {
        return new FruitTreeState(stemX, stemY, stemZ, tick, lastPicked, hydration, temperature, northern);
    }

    public FruitTreeState withLastPicked(long tick) {
        return new FruitTreeState(stemX, stemY, stemZ, lastUpdate, tick, hydration, temperature, northern);
    }

    public FruitTreeState withEnvironment(int newHydration, float newTemperature, boolean newNorthern) {
        return new FruitTreeState(stemX, stemY, stemZ, lastUpdate, lastPicked, newHydration, newTemperature, newNorthern);
    }

    /** The same counters for a block of the same tree whose stem is elsewhere. */
    public FruitTreeState withStem(int x, int y, int z) {
        return new FruitTreeState(x, y, z, lastUpdate, lastPicked, hydration, temperature, northern);
    }
}
