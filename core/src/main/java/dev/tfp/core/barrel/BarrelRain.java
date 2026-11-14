/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: an unsealed
 * upright barrel fills 1 mB of minecraft:water on every game tick divisible by 4 while it rains at the block above)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;

/**
 * PAR-PROC-35: TFC's barrel rain fill, lazily. TFC checks the rain at the block above every fourth tick; nothing ticks here.
 * The record keeps a rain clock ({@link BarrelData#rainTick()}, {@link BarrelData#rainSeen()}): every access that can see
 * the world (a click, the open screen, the debug command) reads the rain now and calls {@link #settle}, which credits the
 * interval since the last settle only when it rained at both ends (ledger divergence: a shower that starts and stops
 * between two accesses is missed, a dry spell between two rainy accesses is counted). The seal and the facing only change
 * at accesses, so they hold over the whole interval. Pure; any thread.
 */
public final class BarrelRain {
    /** TFC: 1 mB on every game tick divisible by this. */
    public static final int PERIOD_TICKS = 4;

    private BarrelRain() {
    }

    /** The ticks in {@code [from, to)} on which TFC's rain check runs ({@code tick % 4 == 0}). */
    public static long checks(long from, long to) {
        if (to <= from) {
            return 0;
        }
        return Math.floorDiv(to - 1, PERIOD_TICKS) - Math.floorDiv(from - 1, PERIOD_TICKS);
    }

    /**
     * The tank after {@code checks} rain checks that saw rain: TFC fills 1 mB of {@code minecraft:water} each time
     * ({@code inventory.fill}); the tank takes it only when empty or holding water, up to {@code capacity}.
     */
    public static FluidAmount filled(FluidAmount tank, int capacity, long checks) {
        if (checks <= 0 || (!tank.isEmpty() && !TfcFluids.WATER.equals(tank.fluid()))) {
            return tank;
        }
        long next = Math.min((long) capacity, (long) tank.amount() + checks);
        if (next <= tank.amount()) {
            return tank;
        }
        return new FluidAmount(TfcFluids.WATER, (int) next);
    }

    /**
     * Settles the rain fill up to {@code now}.
     *
     * @param data     the record's state, caught up to {@code now}
     * @param now      the access tick
     * @param raining  whether it rains at the block above now ({@code PrecipitationService.isRainingAt})
     * @param upright  whether the barrel faces up (TFC {@code facing == UP})
     * @param capacity the tank's capacity
     * @return the state with the credited water and the rain clock at {@code now}
     */
    public static BarrelData settle(BarrelData data, long now, boolean raining, boolean upright, int capacity) {
        if (data.rainTick() == now && data.rainSeen() == raining) {
            return data;
        }
        BarrelData next = data;
        if (data.rainTick() != BarrelRules.NO_TICK && now > data.rainTick() && data.rainSeen() && raining
            && !data.sealed() && upright) {
            next = next.withFluid(filled(data.fluid(), capacity, checks(data.rainTick(), now)));
        }
        return next.withRain(now, raining);
    }
}
