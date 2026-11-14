/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/render/blockentity/BarrelBlockEntityRenderer.java @ c3b4d9536 (the fluid
 * face: inset 0.1875 grown by (0.03 - fill) * 7 below 3 %, height 0.140625 + 0.734375 * fill),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (serverTick: an unsealed
 * upright barrel gains 1 mB of water on every fourth game tick while it rains at the block above)
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.vessel.FluidAmount;

/**
 * PAR-PROC-09: the numbers behind the barrel's in-world fluid surface and its rain fill, ported from TFC for the barrel
 * block's wiring (PAR-PROC-35: the surface becomes a tinted {@code entity_renderer} element of the barrel, the rain fill a
 * lazy catch-up of the device record). Pure; any thread.
 */
public final class BarrelFluidVisuals {
    /** The fluid surface rectangle and height in block units (0..1). */
    public record Surface(float minX, float minZ, float maxX, float maxZ, float y) {
    }

    /** TFC rain fill: 1 mB on every game tick divisible by this. */
    public static final int RAIN_PERIOD_TICKS = 4;

    private BarrelFluidVisuals() {
    }

    /** TFC {@code BarrelBlockEntityRenderer.render}: the surface of {@code amount} mB, or null for an empty tank. */
    public static Surface surface(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) {
            return null;
        }
        float fill = (float) amount / capacity;
        float subtract = fill > 0.03 ? 0f : (0.03f - fill) * 7f;
        return new Surface(0.1875F + subtract, 0.1875F + subtract, 0.8125F - subtract, 0.8125F - subtract,
            0.140625F + (0.75F - 0.015625F) * fill);
    }

    /** The game ticks in {@code [from, to)} on which TFC's rain check runs ({@code gameTime % 4 == 0}). */
    public static long rainChecks(long from, long to) {
        if (to <= from) {
            return 0;
        }
        return Math.floorDiv(to - 1, RAIN_PERIOD_TICKS) - Math.floorDiv(from - 1, RAIN_PERIOD_TICKS);
    }

    /**
     * The tank after {@code checks} rain checks that saw rain: TFC fills 1 mB of {@code minecraft:water} each time; the tank
     * takes it only when it is empty or holds water, up to {@code capacity}. A sealed or sideways barrel gets nothing
     * (the caller does not count its checks).
     */
    public static FluidAmount rainFilled(FluidAmount tank, int capacity, long checks) {
        if (checks <= 0 || (!tank.isEmpty() && !TfcFluids.WATER.equals(tank.fluid()))) {
            return tank;
        }
        long next = Math.min((long) capacity, (long) tank.amount() + checks);
        if (next <= tank.amount()) {
            return tank;
        }
        return new FluidAmount(TfcFluids.WATER, (int) next);
    }
}
