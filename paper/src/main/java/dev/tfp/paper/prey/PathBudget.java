/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.concurrent.atomic.LongAdder;

/**
 * A per-thread token bucket for the path computations our animal goals start (PAR-FAUNA-33). Paper's
 * {@code Pathfinder.moveTo} computes the whole vanilla path in the call, so a herd whose goals all start a path in the same
 * tick costs that tick several milliseconds. Each region thread (the Paper main thread, or one Folia region thread) may
 * start at most {@link #PER_TICK} deferrable paths per tick; a goal whose start is refused does not start (its
 * {@code shouldActivate} answers false, or a re-path is skipped) and asks again on its next period, as vanilla's goal
 * selector retries any goal that did not start. Urgent starts (panic, flight from a threat, the ram charge) are never
 * refused, but they use up the tick's tokens, so idle and follow paths yield to them.
 *
 * <p>Targets, speeds and ranges are unchanged: only the tick in which a path is computed moves. Owning thread only; the
 * counters are thread-safe and only read by the load tests.
 */
public final class PathBudget {
    /** Deferrable path starts per region thread and tick. */
    public static final int PER_TICK = 3;

    private static final class State {
        long stamp = Long.MIN_VALUE;
        int used;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static final LongAdder STARTED = new LongAdder();
    private static final LongAdder DEFERRED = new LongAdder();
    private static volatile int limit = PER_TICK;

    private PathBudget() {
    }

    private static State at(long stamp) {
        State s = STATE.get();
        if (s.stamp != stamp) {
            s.stamp = stamp;
            s.used = 0;
        }
        return s;
    }

    /**
     * Takes a token for a deferrable path start in tick {@code stamp}.
     *
     * @return false if this tick's tokens are used up: the caller must not start the path now
     */
    public static boolean tryStart(long stamp) {
        State s = at(stamp);
        if (s.used >= limit) {
            DEFERRED.increment();
            return false;
        }
        s.used++;
        STARTED.increment();
        return true;
    }

    /**
     * Takes a token for a re-path of an animal that already walks a path (a follower refreshing its path to a moving
     * target). It may use all but the last token of the tick, which stays for an animal that has no path yet, so refreshes
     * of animals ticked earlier cannot starve the first starts of the others. A refused refresh is simply skipped: the
     * current path goes on until the next re-path.
     *
     * @return false if the refresh must be skipped this tick
     */
    public static boolean tryRefresh(long stamp) {
        State s = at(stamp);
        if (s.used >= limit - 1) {
            DEFERRED.increment();
            return false;
        }
        s.used++;
        STARTED.increment();
        return true;
    }

    /**
     * Takes a token for a deferrable path of an animal that {@code hasPath} or not: {@link #tryRefresh} if it already walks
     * one, else {@link #tryStart}.
     */
    public static boolean tryPath(long stamp, boolean hasPath) {
        return hasPath ? tryRefresh(stamp) : tryStart(stamp);
    }

    /** Records an urgent path start in tick {@code stamp}: never refused, but it uses a token. */
    public static void urgent(long stamp) {
        at(stamp).used++;
        STARTED.increment();
    }

    /**
     * Whether a deferrable start would be allowed in tick {@code stamp}; no token is taken (a goal asks this before it
     * draws its target, then takes the token with {@link #tryStart} once it has one). A false answer counts as deferred.
     */
    public static boolean mayStart(long stamp) {
        if (at(stamp).used < limit) {
            return true;
        }
        DEFERRED.increment();
        return false;
    }

    /** {@link #tryStart(long)} in the current server tick ({@link Ticks#now}). */
    public static boolean tryStart() {
        return tryStart(Ticks.now());
    }

    /** {@link #tryPath(long, boolean)} in the current server tick. */
    public static boolean tryPath(boolean hasPath) {
        return tryPath(Ticks.now(), hasPath);
    }

    /** {@link #mayStart(long)} in the current server tick. */
    public static boolean mayStart() {
        return mayStart(Ticks.now());
    }

    /** {@link #urgent(long)} in the current server tick. */
    public static void urgent() {
        urgent(Ticks.now());
    }

    /** Path starts allowed since the counters were last reset (all threads). */
    public static long started() {
        return STARTED.sum();
    }

    /** Deferrable path starts refused since the counters were last reset (all threads). */
    public static long deferred() {
        return DEFERRED.sum();
    }

    public static void resetCounters() {
        STARTED.reset();
        DEFERRED.reset();
    }

    /** Test seam: the per-tick limit ({@link #PER_TICK} by default); also forgets this thread's current tick. */
    public static void limitForTest(int perTick) {
        limit = perTick;
        STATE.remove();
    }
}
