/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (shift-click quench, lava lights),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a lamp with fuel lights),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (lampCapacity 250)
 */
package dev.tfp.core.lamp;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Optional;

/**
 * The rules of a metal lamp. Pure.
 *
 * <p>TFC's {@code checkHasRanOut} runs on random ticks (and on clicks): it takes
 * {@code floor(ticksSinceUpdate / burnRate)} mB out of the tank, and only when that is at least 1 it resets the counter,
 * dropping the remainder; the lamp goes out when the tank is empty, when less than that could be taken, or when the fluid
 * is no fuel of the lamp. A lamp is never ticked here: {@link #advance} is that one check over a whole span, run whenever
 * the lamp is looked at. The carry keeps the ticks of a span that were too short to use a mB, as TFC's counter does.
 */
public final class LampRules {
    /** TFC {@code lampCapacity} (mB). */
    public static final int CAPACITY = 250;

    private LampRules() {
    }

    /** TFC {@code getFuel()}: the fuel in the tank of the lamp of {@code metal}. */
    public static Optional<LampFuel> fuel(LampData data, String metal) {
        return data.tank().isEmpty() ? Optional.empty() : LampFuel.get(data.tank().fluid(), metal);
    }

    /** TFC {@code InventoryFluidTank} validator: a fluid the lamp of {@code metal} burns. */
    public static boolean accepts(String fluid, String metal) {
        return LampFuel.get(fluid, metal).isPresent();
    }

    /** TFC {@code checkHasRanOut} over {@code ticks} ticks of a lit lamp; an unlit lamp does not change. */
    public static LampData advance(LampData data, String metal, long ticks) {
        if (!data.lit() || ticks <= 0) {
            return data;
        }
        Optional<LampFuel> fuel = fuel(data, metal);
        if (fuel.isEmpty()) {
            return data.withLit(false); // no fuel: always unlit, the counter reset
        }
        if (fuel.get().endless()) {
            return data; // floor(ticks / -1) is never 1 or more: nothing is used
        }
        long total = data.carry() + ticks;
        long usage = total / fuel.get().burnRate();
        if (usage < 1) {
            return new LampData(data.tank(), true, total);
        }
        int amount = data.tank().amount();
        FluidAmount left = usage >= amount ? FluidAmount.EMPTY : new FluidAmount(data.tank().fluid(), (int) (amount - usage));
        // TFC: out when the tank is empty or less than the usage could be taken; the counter is reset either way
        return new LampData(left, !left.isEmpty(), 0);
    }

    /**
     * The ticks until a lit lamp goes out if nobody touches it, or empty when it burns for ever (or is not lit). A lamp that
     * burns {@code n} mB goes out at the check that finds the tank empty: after {@code amount x burnRate - carry} ticks.
     */
    public static Optional<Long> ticksUntilOut(LampData data, String metal) {
        if (!data.lit()) {
            return Optional.empty();
        }
        Optional<LampFuel> fuel = fuel(data, metal);
        if (fuel.isEmpty()) {
            return Optional.of(1L);
        }
        if (fuel.get().endless()) {
            return Optional.empty();
        }
        return Optional.of(Math.max(1L, (long) data.tank().amount() * fuel.get().burnRate() - data.carry()));
    }

    /** TFC {@code ForgeEventHandler.onFireStart}: an unlit lamp with a fuel in it lights; its counter starts at zero. */
    public static LampData light(LampData data, String metal) {
        if (data.lit() || fuel(data, metal).isEmpty()) {
            return data;
        }
        return new LampData(data.tank(), true, 0);
    }

    /** TFC {@code useItemOn}: a sneak click with an empty hand puts a lit lamp out. */
    public static LampData quench(LampData data) {
        return data.lit() ? data.withLit(false) : data;
    }

    /**
     * TFC {@code useItemOn} after a fluid moved: a lamp filled with an endless fuel (lava) lights at once.
     *
     * @param byPlayer true when the move came from a player, as TFC's {@code instanceof ServerPlayer}
     */
    public static LampData afterFill(LampData data, String metal, boolean byPlayer) {
        Optional<LampFuel> fuel = fuel(data, metal);
        if (byPlayer && fuel.isPresent() && fuel.get().endless()) {
            return new LampData(data.tank(), true, 0);
        }
        return data;
    }
}
