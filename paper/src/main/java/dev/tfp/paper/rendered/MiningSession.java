/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

/**
 * PAR-INFRA-32: one player's dig, counted in server ticks (pure, no Bukkit). Vanilla's server measures a dig by the
 * ticks elapsed since it started; CraftEngine measures a custom block's dig by the ticks in which a swing packet
 * arrived, so a client below 20 ticks/s (or two swings landing in one tick) loses progress. {@link #tick} decides,
 * once per server tick, whether this tick counts: it does while the client is still digging (it swung within
 * {@link #SWING_GRACE} ticks and sent no abort) and the client plugin runs the dig server-side.
 *
 * <p>All methods run on the player's thread; {@code synchronized} only guards a player handed between threads.
 */
public final class MiningSession {
    /** What one server tick does. */
    public enum Action {
        /** Count this tick. */
        ADVANCE,
        /** Do not count it, keep watching. */
        WAIT,
        /** The dig is over: stop the per-tick task. */
        STOP
    }

    /**
     * Ticks a swing keeps the dig alive. A digging client swings every client tick; 6 covers a client at 4 ticks/s
     * or a 300 ms hitch, while a released button is reported at once by the abort.
     */
    public static final int SWING_GRACE = 6;
    /** Ticks without any swing after which the task stops (the next dig start restarts it). */
    public static final int IDLE_TIMEOUT = 40;
    /** Ticks the plugin may take to start (or restart, after a break) its own dig before the task stops. */
    public static final int NOT_MINING_TIMEOUT = 10;

    private int run;
    private int lastSwing;
    private int lastMining;
    private boolean aborted;

    /** A dig started (the client's first swing comes with it). */
    public synchronized void start() {
        run = 0;
        lastSwing = 0;
        lastMining = 0;
        aborted = false;
    }

    /** The client swung its arm. */
    public synchronized void swing() {
        lastSwing = run;
    }

    /** The client released the button or the game mode changed: the dig is over. */
    public synchronized void abort() {
        aborted = true;
    }

    /**
     * One server tick.
     *
     * @param modeAllowed survival or adventure (creative breaks instantly, spectator cannot dig)
     * @param serverMining the client plugin runs a server-side dig for this player now
     */
    public synchronized Action tick(boolean modeAllowed, boolean serverMining) {
        run++;
        if (aborted || !modeAllowed) {
            return Action.STOP;
        }
        if (serverMining) {
            lastMining = run;
        } else {
            return run - lastMining > NOT_MINING_TIMEOUT ? Action.STOP : Action.WAIT;
        }
        int sinceSwing = run - lastSwing;
        if (sinceSwing > IDLE_TIMEOUT) {
            return Action.STOP;
        }
        return sinceSwing > SWING_GRACE ? Action.WAIT : Action.ADVANCE;
    }
}
