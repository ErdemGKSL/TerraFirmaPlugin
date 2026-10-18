/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LampBlockEntity.java @ c3b4d9536 (checkHasRanOut),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (lightLevel, LIT),
 *       src/main/java/net/dries007/tfc/util/data/LampFuel.java @ c3b4d9536 (burnRate),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (lampCapacity),
 *       src/generated/resources/data/tfc/tfc/lamp_fuel/*.json @ c3b4d9536 (burn rates)
 */
package dev.tfp.core.light;

/**
 * Lamp fuel burn ({@code LampBlockEntity.checkHasRanOut}). Pure: the caller applies the drain and the lit flag.
 *
 * <p>A lamp burns {@code floor(ticks / burnRate)} mB at each check, where {@code ticks} is the time since the last check
 * (the counter is reset on every check that burns fuel). The remainder of the division is dropped, as in TFC, so the
 * result depends on how often the check runs. {@link #check} is TFC's single check; it is not cadence-invariant.
 */
public final class LampRules {
    /** TFC {@code lampCapacity} default, in mB. */
    public static final int DEFAULT_CAPACITY_MB = 250;
    /** Burn rate of a fuel that never runs out (lava): the lamp keeps its state until the fluid is removed. */
    public static final int NO_BURN = -1;

    /**
     * A fuel from {@code data/tfc/lamp_fuel}.
     *
     * @param fluid    the fluid id in the data file
     * @param burnRate ticks per mB, or {@link #NO_BURN}
     */
    public record Fuel(String fluid, int burnRate) {
    }

    /** {@code tfc:canola_oil}: 6000 ticks per mB. */
    public static final Fuel CANOLA_OIL = new Fuel("tfc:canola_oil", 6000);
    /** {@code tfc:olive_oil}: 7200 ticks per mB. */
    public static final Fuel OLIVE_OIL = new Fuel("tfc:olive_oil", 7200);
    /** {@code tfc:tallow}: 2400 ticks per mB. */
    public static final Fuel TALLOW = new Fuel("tfc:tallow", 2400);
    /** {@code minecraft:lava}: burn rate -1, so it never runs out (blue and red steel lamps only). */
    public static final Fuel LAVA = new Fuel("minecraft:lava", NO_BURN);

    /**
     * The result of one check.
     *
     * @param usage    mB the check asked for ({@code floor(ticks / burnRate)}, 0 when nothing burns)
     * @param drained  mB actually removed, at most the tank contents
     * @param tankAfter mB left in the tank
     * @param lit      the lit flag after the check
     * @param reset    whether the lamp's counter is reset to now (TFC resets whenever fuel was burnt)
     */
    public record Check(long usage, int drained, int tankAfter, boolean lit, boolean reset) {
    }

    private LampRules() {
    }

    /**
     * TFC {@code checkHasRanOut} for a lit lamp: spend {@code floor(ticksSinceUpdate / burnRate)} mB. An unlit lamp is left
     * alone. A lamp whose tank is emptied, or cannot cover the usage, goes out.
     *
     * @param ticksSinceUpdate calendar ticks since the last check, {@code >= 0}
     * @param burnRate         ticks per mB; {@link #NO_BURN} or any negative rate never consumes fuel
     * @param tankMb           mB in the tank, {@code >= 0}
     * @param lit              the lamp's current lit flag
     */
    public static Check check(long ticksSinceUpdate, int burnRate, int tankMb, boolean lit) {
        if (ticksSinceUpdate < 0) {
            throw new IllegalArgumentException("ticksSinceUpdate must be >= 0: " + ticksSinceUpdate);
        }
        if (tankMb < 0) {
            throw new IllegalArgumentException("tankMb must be >= 0: " + tankMb);
        }
        if (burnRate == 0) {
            throw new IllegalArgumentException("burnRate must not be 0");
        }
        if (!lit) {
            return new Check(0, 0, tankMb, false, false);
        }
        if (burnRate < 0) {
            return new Check(0, 0, tankMb, true, false);
        }
        final long usage = ticksSinceUpdate / burnRate;
        if (usage < 1) {
            return new Check(usage, 0, tankMb, true, false);
        }
        final int drained = (int) Math.min(usage, tankMb);
        final int tankAfter = tankMb - drained;
        return new Check(usage, drained, tankAfter, tankAfter > 0 && drained == usage, true);
    }

    /**
     * The time one full drain of {@code mb} mB of this fuel lasts, in ticks: {@code burnRate * mb}. A fuel that never runs
     * out reports {@link LightSourceRules#NEVER}.
     */
    public static long durationTicks(int burnRate, int mb) {
        if (mb < 0) {
            throw new IllegalArgumentException("mb must be >= 0: " + mb);
        }
        if (burnRate < 0) {
            return LightSourceRules.NEVER;
        }
        return (long) burnRate * mb;
    }

    /** The lamp light level: TFC {@code LIT ? 15 : 0}. */
    public static int lampLight(boolean lit) {
        return lit ? 15 : 0;
    }
}
