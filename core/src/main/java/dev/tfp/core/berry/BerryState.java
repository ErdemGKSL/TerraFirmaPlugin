/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BerryBushBlockEntity.java @ c3b4d9536 (growthsRemaining),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBerryBushBlock.java @ c3b4d9536 (the dead bush keeps STAGE)
 */
package dev.tfp.core.berry;

import dev.tfp.core.persist.VersionedCodec;

/**
 * What the plugin stores for one stationary berry bush block: the bush ({@link BerryBush}), whether it has withered, and the
 * environment sampled at the bush at its last access. TFC reads hydration and temperature from the world at every random tick; a lazy
 * bush samples them when it is placed or touched and keeps them here, so a catch-up without a world (chunk load) can still run
 * {@link BerryRules#catchUp} (the same device as the composter's stored ready ticks).
 *
 * @param dead        whether the bush has withered (TFC's dead berry bush block)
 * @param bush        the bush; a dead bush keeps its stage and counters
 * @param witherTick  the player tick the bush withered at, or {@code 0} while alive
 * @param hydration   the hydration at the bush's root, {@code 0..100}
 * @param temperature the average temperature at the bush
 * @param northern    whether the bush is in the northern hemisphere
 */
public record BerryState(boolean dead, BerryBush bush, long witherTick, int hydration, float temperature, boolean northern) {
    /** TFC {@code lastPickedTick} of a bush that was never picked or placed by a player ({@code Integer.MIN_VALUE}). */
    public static final long NEVER_PICKED = Integer.MIN_VALUE;

    public static final VersionedCodec<BerryState> CODEC = VersionedCodec.<BerryState>builder("berry_bush", 1)
        .build((s, out) -> out.putBoolean("dead", s.dead())
                .putString("lifecycle", s.bush().lifecycle().name())
                .putInt("stage", s.bush().stage())
                .putInt("growths", s.bush().growthsRemaining())
                .putLong("updated", s.bush().lastUpdateTick())
                .putLong("picked", s.bush().lastPickedTick())
                .putLong("wither", s.witherTick())
                .putInt("hydration", s.hydration())
                .putFloat("temperature", s.temperature())
                .putBoolean("northern", s.northern()),
            in -> new BerryState(in.getBoolean("dead", false),
                new BerryBush(Lifecycle.valueOf(in.getString("lifecycle", Lifecycle.HEALTHY.name())), in.getInt("stage", 0),
                    in.getInt("growths", BerryRules.GROWTHS_INITIAL), in.getLong("updated", 0L), in.getLong("picked", NEVER_PICKED)),
                in.getLong("wither", 0L), in.getInt("hydration", 50), in.getFloat("temperature", 10f), in.getBoolean("northern", true)));

    public BerryState {
        if (bush == null) {
            throw new IllegalArgumentException("bush");
        }
        if (hydration < 0 || hydration > 100) {
            throw new IllegalArgumentException("hydration must be 0..100: " + hydration);
        }
    }

    /** A bush with the same environment and a living or dead {@code next}. */
    public BerryState withBush(BerryBush next) {
        return new BerryState(dead, next, witherTick, hydration, temperature, northern);
    }

    /** The same state with a new environment sample. */
    public BerryState withEnvironment(int newHydration, float newTemperature, boolean newNorthern) {
        return new BerryState(dead, bush, witherTick, newHydration, newTemperature, newNorthern);
    }

    /** The same bush, withered at {@code tick}. */
    public BerryState withered(long tick, BerryBush kept) {
        return new BerryState(true, kept, tick, hydration, temperature, northern);
    }

    /** The climate source of {@link BerryRules#catchUp}: the stored sample at every tick. */
    public BerryRules.ClimateSource climate() {
        BerryRules.Sample sample = new BerryRules.Sample(hydration, temperature);
        return tick -> sample;
    }
}
