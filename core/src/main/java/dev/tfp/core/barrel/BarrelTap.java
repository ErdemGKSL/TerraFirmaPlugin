/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (tickPouring: an unsealed
 * barrel on its side with fluid moves exactly 1 mB per tick through its tap, when the block in front of it is air, into the
 * fluid handler below the tap; the barrel's handler on the up side: inventory.fill only while unsealed, the tank's
 * validator), src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferExact: both sides must
 * take the whole amount)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;

/**
 * PAR-PROC-38: TFC's barrel tap, lazily. TFC moves 1 mB on every tick while the tap can pour (the source barrel on its side,
 * unsealed, holding fluid, the block in front of its tap air, a fluid handler below that block taking the fluid). Nothing
 * ticks here: the source record keeps a tap clock ({@link BarrelData#pourTick()}, {@link BarrelData#pourSeen()}), and every
 * access of the source or of the barrel below its tap reads whether the tap is open now and calls {@link #settle}. The
 * interval since the last settle is credited only when the tap was open at both ends (as {@link BarrelRain}; ledger: a
 * block put in front of the tap and taken away between two accesses is missed). The fluids are part of the records, so the
 * amount moved is exact: one mB per tick, until the source is empty or the target full. Pure; any thread.
 */
public final class BarrelTap {
    /** TFC {@code transferExact(tank, handler, 1)}: one mB per tick. */
    public static final int PER_TICK = 1;

    private BarrelTap() {
    }

    /**
     * The result of a settle.
     *
     * @param source the source's state, its tap clock at {@code now}
     * @param target the target tank after the pour (the input tank when nothing moved)
     * @param moved  the mB that went through the tap
     */
    public record Result(BarrelData source, FluidAmount target, int moved) {
        public Result {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    /**
     * Whether the source's own state lets the tap pour (TFC {@code !sealed && !tank.isEmpty() && facing != UP}); the world
     * half (air in front, a handler below) is the caller's.
     */
    public static boolean canPour(BarrelData source, boolean sideways) {
        return sideways && !source.sealed() && !source.fluid().isEmpty();
    }

    /**
     * How much of {@code fluid} a barrel tank takes (TFC's tank {@code fill}: empty or the same fluid, up to
     * {@code capacity}; the tank's validator is the caller's {@code usable}).
     */
    public static int room(FluidAmount tank, String fluid, int capacity, boolean usable) {
        if (!usable || (!tank.isEmpty() && !tank.fluid().equals(fluid))) {
            return 0;
        }
        return Math.max(0, capacity - tank.amount());
    }

    /**
     * Settles the tap up to {@code now}.
     *
     * @param source       the source's state, caught up to {@code now}
     * @param now          the access tick
     * @param open         whether the tap is open now: the source on its side, the block in front of the tap air and a
     *                     fluid handler below it (the source's own seal and fluid are read from {@code source})
     * @param target       the target's tank now ({@link FluidAmount#EMPTY} when {@code open} is false)
     * @param targetRoom   what the target takes of the source's fluid now ({@link #room}; 0 when sealed)
     * @return the source with its tap clock at {@code now}, the target tank and the mB moved
     */
    public static Result settle(BarrelData source, long now, boolean open, FluidAmount target, int targetRoom) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        boolean seen = open && !source.sealed();
        int moved = 0;
        FluidAmount after = target;
        BarrelData next = source;
        if (source.pourTick() != BarrelRules.NO_TICK && now > source.pourTick() && source.pourSeen() && seen
            && !source.fluid().isEmpty() && targetRoom > 0) {
            long ticks = now - source.pourTick();
            moved = (int) Math.min(Math.min(ticks * PER_TICK, source.fluid().amount()), targetRoom);
            if (moved > 0) {
                String fluid = source.fluid().fluid();
                int left = source.fluid().amount() - moved;
                next = next.withFluid(left == 0 ? FluidAmount.EMPTY : new FluidAmount(fluid, left));
                after = new FluidAmount(fluid, (target.isEmpty() ? 0 : target.amount()) + moved);
            }
        }
        if (next.pourTick() == now && next.pourSeen() == seen && moved == 0) {
            return new Result(next, after, 0);
        }
        return new Result(next.withPour(now, seen), after, moved);
    }
}
