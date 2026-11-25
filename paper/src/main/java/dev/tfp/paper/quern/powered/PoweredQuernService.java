/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (serverTick: finish, immediate
 *       restart while connected, start check every 10 ticks; clientTick: the timer rate)
 */
package dev.tfp.paper.quern.powered;

import dev.tfp.paper.quern.powered.PoweredQuernAccess.Key;
import dev.tfp.paper.quern.powered.PoweredQuernAccess.View;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drives the axle-driven querns (PAR-MECH-06), "decide async, apply sync" and lazy. A quern is <em>watched</em> while a rotation
 * node may reach it (an axle-like block above it) or it grinds; a watched quern is looked at every {@link PoweredQuern#CHECK_PERIOD}
 * ticks (TFC's own idle check), or at its planned finish tick when that comes sooner. A look is: ask the rotation actor for the
 * speed above (async), then on the region thread settle a due grind, re-plan a running one whose rate changed, or start the next.
 * Between looks nothing runs: a grind is a planned finish tick stored in the quern's own state, finished lazily by the manual
 * quern's code.
 *
 * <p>Thread contract: every method except the future of {@link PoweredQuernAccess#speed} runs on the quern's region thread.
 */
public final class PoweredQuernService {
    /** Grinds finished in one look at most (a long gap with a big stack and a fast network), so a look stays short. */
    static final int MAX_CHAIN = 64;

    private final PoweredQuernAccess access;
    private final Logger logger;
    private final Map<Key, Watch> watched = new ConcurrentHashMap<>();

    public PoweredQuernService(PoweredQuernAccess access, Logger logger) {
        this.access = Objects.requireNonNull(access, "access");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** What the service remembers of a watched quern: the timer of the running grind as TFC's {@code recipeTimer}. */
    private static final class Watch {
        /** Whether the plan below is known (it is not after a restart or a first sight of a running grind). */
        boolean known;
        /** The timer left at {@link #stamp}, in grind units. */
        float timer;
        long stamp;
        /** The units that go off per tick since {@link #stamp}. */
        float perTick;

        void plan(float timer, long stamp, float perTick) {
            this.known = true;
            this.timer = timer;
            this.stamp = stamp;
            this.perTick = perTick;
        }
    }

    /** Whether {@code key} is watched. */
    public boolean isWatched(Key key) {
        return watched.containsKey(key);
    }

    public int watchedCount() {
        return watched.size();
    }

    /** Starts watching {@code key}: the first look comes next tick. Region thread; a quern already watched is left alone. */
    public void watch(Key key) {
        Watch w = new Watch();
        if (watched.putIfAbsent(key, w) == null) {
            access.track(key);
            schedule(key, w, 1);
        }
    }

    /** Stops watching without touching the persisted list (the chunk unloaded). */
    public void drop(Key key) {
        watched.remove(key);
    }

    private void unwatch(Key key, Watch w) {
        if (watched.remove(key, w)) {
            access.untrack(key);
        }
    }

    private void schedule(Key key, Watch w, long delay) {
        access.later(key, Math.max(1, delay), () -> look(key, w));
    }

    /** One look (region thread): snapshot, ask the speed asynchronously, then commit on the region thread. */
    private void look(Key key, Watch w) {
        if (watched.get(key) != w) {
            return;
        }
        if (access.view(key).isEmpty()) {
            unwatch(key, w);
            return;
        }
        access.speed(key).whenComplete((speed, err) -> access.onRegion(key, () -> {
            try {
                commit(key, w, err != null ? Optional.empty() : speed);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Powered quern at " + key + ": a look failed", e);
                if (watched.get(key) == w) {
                    schedule(key, w, PoweredQuern.CHECK_PERIOD);
                }
            }
        }));
    }

    /** The decision applied, region thread: re-reads the quern, so a change since the snapshot is honoured. */
    void commit(Key key, Watch w, Optional<Float> speed) {
        if (watched.get(key) != w) {
            return;
        }
        Optional<View> first = access.view(key);
        if (first.isEmpty()) {
            unwatch(key, w);
            return;
        }
        View v = first.get();
        boolean axle = speed.isPresent();
        boolean connected = PoweredQuern.connected(axle, v.handstone());
        float networkSpeed = speed.orElse(0f);
        float perTick = connected ? PoweredQuern.rate(networkSpeed) : PoweredQuern.MANUAL_PER_TICK;
        long now = v.now();
        long start = now; // where a new grind starts: now, or the finish tick of the grind just settled

        for (int i = 0; i < MAX_CHAIN && v.due(); i++) {
            long finished = v.finishTick();
            access.settle(key);
            w.known = false;
            Optional<View> after = access.view(key);
            if (after.isEmpty()) {
                unwatch(key, w);
                return;
            }
            v = after.get();
            start = PoweredQuern.nextStart(finished, now);
            if (!startNext(key, w, v, connected, networkSpeed, start)) {
                break;
            }
            v = access.view(key).orElse(v);
        }
        if (v.started() && !v.due()) {
            replan(key, w, v, perTick);
        } else if (!v.started()) {
            if (startNext(key, w, v, connected, networkSpeed, start)) {
                v = access.view(key).orElse(v);
            }
        }

        if (v.started()) {
            schedule(key, w, Math.min(PoweredQuern.CHECK_PERIOD, Math.max(1, v.finishTick() - now)));
        } else if (axle) {
            schedule(key, w, PoweredQuern.CHECK_PERIOD);
        } else {
            unwatch(key, w);
        }
    }

    /** TFC's start: input with a recipe and a handstone, on a network that turns. Returns whether a grind was stored. */
    private boolean startNext(Key key, Watch w, View v, boolean connected, float networkSpeed, long start) {
        if (v.started() || !connected || !v.grindable()) {
            return false;
        }
        int ticks = PoweredQuern.grindTicks(networkSpeed);
        if (ticks <= 0) {
            return false;
        }
        long finish = start + ticks;
        access.setFinish(key, finish);
        w.plan(PoweredQuern.GRIND_UNITS, start, PoweredQuern.rate(networkSpeed));
        return true;
    }

    /**
     * A running grind whose rate changed (speed change; the axle lost, when TFC's timer goes on at the manual 1 per tick) gets
     * a new finish tick from the timer it has left. A rate of 0 (a stalled network) parks the finish far ahead; the timer stays.
     */
    private void replan(Key key, Watch w, View v, float perTick) {
        long now = v.now();
        long left = Math.max(0, v.finishTick() - now);
        float timer;
        if (w.known) {
            if (Math.abs(w.perTick - perTick) <= 1e-6f) {
                return; // same rate: the planned finish stands
            }
            timer = Math.max(0f, w.timer - (now - w.stamp) * w.perTick);
        } else {
            // found running without a plan (after a restart): the stored finish is the plan, a parked or impossible one is repaired
            timer = Math.min(PoweredQuern.GRIND_UNITS, left * (perTick > 0 ? perTick : 1f));
        }
        long finish = now + parkedOrTicks(timer, perTick);
        w.plan(timer, now, perTick);
        if (Math.abs(finish - v.finishTick()) > 1) {
            access.setFinish(key, finish);
        }
    }

    private static long parkedOrTicks(float timer, float perTick) {
        int ticks = perTick > 0 ? PoweredQuern.ticksFor(timer, perTick) : -1;
        return ticks < 0 ? PoweredQuern.MAX_TICKS : ticks;
    }
}
