/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (the BarrelInventory fill and
 * drain, canModify), src/main/java/net/dries007/tfc/common/capabilities/InventoryFluidTank.java @ c3b4d9536 (the tank is a
 * NeoForge FluidTank with the USABLE_IN_BARREL validator), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536
 * (barrelCapacity, default 10000 mB)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.vessel.FluidAmount;

/**
 * The barrel's tank rules and constants. A sealed barrel accepts no fluid and gives none out, as TFC's {@code canModify}
 * gates every fill and drain. An unsealed barrel fills like a NeoForge {@code FluidTank}: an empty tank takes up to the
 * capacity, a tank holding the same fluid takes up to the remaining room, and any other fluid is refused.
 */
public final class BarrelRules {

    /** TFC's default {@code barrelCapacity}, in mB. */
    public static final int DEFAULT_CAPACITY = 10_000;

    /** The recipe tick of a barrel with no active recipe (TFC's {@code BarrelComponent} default). */
    public static final long NO_TICK = -1L;

    private BarrelRules() {
    }

    /**
     * How much of {@code offered} the tank takes.
     *
     * @param stored   the fluid in the tank
     * @param sealed   whether the barrel is sealed
     * @param usable   whether the fluid is in {@code tfc:usable_in_barrel} (the tank's validator)
     * @param offered  the fluid offered
     * @param capacity the tank capacity, {@code >= 0}
     * @return the accepted amount in mB, {@code 0..offered.amount()}
     */
    public static int fillAccepted(FluidAmount stored, boolean sealed, boolean usable, FluidAmount offered, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
        }
        if (sealed || !usable || offered.isEmpty()) {
            return 0;
        }
        if (stored.isEmpty()) {
            return Math.min(capacity, offered.amount());
        }
        if (!stored.fluid().equals(offered.fluid())) {
            return 0;
        }
        return Math.max(0, Math.min(capacity - stored.amount(), offered.amount()));
    }

    /**
     * How much the tank gives out. A sealed barrel gives nothing.
     *
     * @param stored   the fluid in the tank
     * @param sealed   whether the barrel is sealed
     * @param maxDrain the most the caller asks for, {@code >= 0}
     * @return the drained amount in mB
     */
    public static int drainAccepted(FluidAmount stored, boolean sealed, int maxDrain) {
        if (maxDrain < 0) {
            throw new IllegalArgumentException("maxDrain must be >= 0: " + maxDrain);
        }
        if (sealed) {
            return 0;
        }
        return Math.min(stored.amount(), maxDrain);
    }
}
