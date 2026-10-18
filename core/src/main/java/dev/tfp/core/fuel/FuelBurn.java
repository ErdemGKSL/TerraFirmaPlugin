/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (consumeFuelForTicks),
 *       src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (getSlotStackLimit)
 */
package dev.tfp.core.fuel;

import java.util.List;

/**
 * The burn-time rule of fuel-burning devices ({@code HeatCapability.consumeFuelForTicks}), evaluated lazily over a span of
 * calendar ticks. Pure: the caller removes the fuel items it reports as consumed.
 *
 * <p>Fuel slots hold one item each (TFC's {@code AbstractFirepitBlockEntity.getSlotStackLimit} is 1 for fuel), so each
 * item is one burn. A stack of {@code n} identical items is therefore {@code n} burns of {@code duration} ticks, one after
 * the other. Non-fuel items are skipped by TFC and never removed; the caller passes fuel items only, in slot order.
 *
 * <p><b>Float and int.</b> The burn counter is an {@code int} of ticks, as in TFC, so partial burns round down with
 * {@code (int)}.
 */
public final class FuelBurn {
    private FuelBurn() {
    }

    /**
     * The state after a span of ticks.
     *
     * @param burnTicks       ticks left on the fuel that is burning now
     * @param burnTemperature the temperature of the fuel that is burning now (unchanged when nothing was consumed)
     * @param ticks           ticks that could not be spent: positive when the fuel ran out before the span ended
     * @param consumed        how many fuel items, from the front of the queue, were used up (including a partly burnt one)
     */
    public record Remainder(int burnTicks, float burnTemperature, long ticks, int consumed) {
    }

    /**
     * TFC {@code consumeFuelForTicks}: spend {@code ticks} on the burning fuel, then on the queued fuel items in order.
     *
     * @param ticks           ticks since the last update, {@code >= 0}
     * @param burnTicks       ticks left on the burning fuel
     * @param burnTemperature temperature of the burning fuel
     * @param queue           the fuel items in slot order (non-fuel items already removed)
     */
    public static Remainder consume(long ticks, int burnTicks, float burnTemperature, List<Fuel> queue) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        if (burnTicks > ticks) {
            return new Remainder((int) (burnTicks - ticks), burnTemperature, 0L, 0);
        }
        ticks -= burnTicks;
        burnTicks = 0;
        int consumed = 0;
        for (Fuel fuel : queue) {
            consumed++;
            if (fuel.duration() > ticks) {
                return new Remainder((int) (fuel.duration() - ticks), fuel.temperature(), 0L, consumed);
            }
            ticks -= fuel.duration();
        }
        return new Remainder(burnTicks, burnTemperature, ticks, consumed);
    }

    /** The total ticks of a stack of identical fuel items: {@code count} burns of {@code fuel.duration()}. */
    public static long stackDuration(Fuel fuel, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        return (long) fuel.duration() * count;
    }
}
