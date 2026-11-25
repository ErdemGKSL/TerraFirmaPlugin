/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (MANUAL_TICKS, MANUAL_RECIPE_PER_TICK,
 *       NETWORK_RECIPE_PER_SPEED, clientTick: recipeTimer -= speed * NETWORK_RECIPE_PER_SPEED when connected, 1 when not;
 *       serverTick: the immediate restart after a finish and the 10-tick start check; isConnectedToNetwork)
 */
package dev.tfp.paper.quern.powered;

/**
 * The rules of an axle-driven quern (PAR-MECH-06), pure and platform-free. TFC's quern counts a {@code recipeTimer} down from
 * 90 each tick by {@code |speed| * 90 / 2 pi} while it is on a network, by 1 when it is not (a grind that lost its axle carries
 * on by hand), and finishes when the timer reaches 0. Nothing here ticks: the number of ticks a grind takes is computed from the
 * speed ({@link #ticksFor}), the finish tick is planned, and a change of the speed re-plans the rest ({@link #retarget}).
 *
 * <p>The quern's stored {@code QuernState} holds only the start tick of a manual grind, finished at {@code start + 90}. A powered
 * grind with a planned finish tick {@code f} is stored as the start {@code f - 90} ({@link #startFor}), so the manual quern's own
 * lazy finish ({@code QuernDevice.settle}) completes it, with TFC's handstone damage and sounds, with no second code path.
 */
public final class PoweredQuern {
    /** TFC {@code QuernBlockEntity.MANUAL_TICKS}: the timer a grind starts at, and the manual grind's length in ticks. */
    public static final int GRIND_UNITS = 90;

    /** TFC {@code Mth.TWO_PI}. */
    public static final float TWO_PI = 6.2831855f;

    /** TFC {@code NETWORK_RECIPE_PER_SPEED}: timer units per radian of the network speed. */
    public static final float PER_SPEED = GRIND_UNITS / TWO_PI;

    /** TFC {@code MANUAL_RECIPE_PER_TICK}: timer units per tick of a grind that is not on a network. */
    public static final float MANUAL_PER_TICK = 1f;

    /** TFC's idle start check: a connected, idle quern tries to start a grind every 10 ticks. */
    public static final int CHECK_PERIOD = 10;

    /**
     * A grind that is found due up to this many ticks late (a check period and a little more) restarts from its planned finish
     * tick, so the throughput does not depend on how often it was looked at. A later one (the chunk was unloaded) restarts from
     * now: TFC does not tick an unloaded quern, so no grinds are made up for the time away.
     */
    public static final long CHAIN_WINDOW = 2L * CHECK_PERIOD;

    /** The most ticks a grind may be planned for; a slower network than this never finishes in practice. */
    public static final int MAX_TICKS = 200_000;

    private PoweredQuern() {
    }

    /** TFC {@code QuernBlockEntity.getRotationSpeed}: the magnitude of the network speed. */
    public static float rate(float networkSpeed) {
        return Math.abs(networkSpeed) * PER_SPEED;
    }

    /**
     * How many ticks until a timer of {@code timer} units reaches 0 when {@code perTick} units go off each tick, counted as TFC
     * counts it: one float subtraction per tick.
     *
     * @return the number of ticks (at least 1 for a positive timer), or -1 when {@code perTick} is not positive (the grind
     *         never finishes) or it would take over {@link #MAX_TICKS}
     */
    public static int ticksFor(float timer, float perTick) {
        if (!(perTick > 0) || Float.isNaN(perTick)) {
            return -1;
        }
        if (timer <= 0) {
            return 0;
        }
        // the same float arithmetic as TFC's per-tick subtraction; at most MAX_TICKS steps (a plain loop, microseconds)
        float t = timer;
        int n = 0;
        while (t > 0) {
            if (n >= MAX_TICKS) {
                return -1;
            }
            t -= perTick;
            n++;
        }
        return n;
    }

    /** The ticks a fresh grind takes on a network of {@code networkSpeed}; -1 when the quern does not turn. */
    public static int grindTicks(float networkSpeed) {
        return ticksFor(GRIND_UNITS, rate(networkSpeed));
    }

    /**
     * Re-plans a running grind. {@code remainingUnits} is the timer left under the old rate; the new finish is
     * {@code now + ticksFor(remaining, newPerTick)}.
     *
     * @param finish      the planned finish tick of the running grind
     * @param now         the tick of the change
     * @param oldPerTick  the rate the grind was planned with
     * @param newPerTick  the rate it runs at from now (the {@link #rate} of the network, or {@link #MANUAL_PER_TICK} when the quern
     *                    left the network)
     * @return the new finish tick, or -1 when it stalls ({@code newPerTick} is 0)
     */
    public static long retarget(long finish, long now, float oldPerTick, float newPerTick) {
        float remaining = Math.max(0, finish - now) * oldPerTick;
        int ticks = ticksFor(remaining, newPerTick);
        return ticks < 0 ? -1 : now + ticks;
    }

    /** The {@code QuernState} start tick that makes the manual quern finish at {@code finish} ({@code start + 90}). */
    public static long startFor(long finish) {
        return finish - GRIND_UNITS;
    }

    /**
     * Where the next grind starts after one finished: from the finish tick when it is found at most {@link #CHAIN_WINDOW}
     * ticks late, else from {@code now}.
     */
    public static long nextStart(long finish, long now) {
        return now - finish <= CHAIN_WINDOW ? Math.min(finish, now) : now;
    }

    /** TFC {@code isConnectedToNetwork}: the quern is on a network (a rotation arrives) and has its handstone. */
    public static boolean connected(boolean rotation, boolean handstone) {
        return rotation && handstone;
    }
}
