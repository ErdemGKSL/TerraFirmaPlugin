/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/PowerLoomBlockEntity.java @ c3b4d9536 (powerLoomTick: the
 *       sound on a push)
 */
package dev.tfp.paper.powerloom;

import dev.tfp.paper.powerloom.PowerLoomAccess.View;
import dev.tfp.paper.powerloom.PowerLoomIndex.Key;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Looks after the power looms (PAR-MECH-08), "decide async, apply sync" and lazy. A loom is <em>watched</em> while a rotation
 * node may reach it (a network block on its sink face) or a step waits; a watched loom is looked at every {@link #LOOK_PERIOD}
 * ticks. A look asks the rotation actor for the speed on the sink face (async), then on the region thread stores that speed in
 * the loom's record, which first catches the record up at the old speed ({@link PowerLoomDevice}'s catch-up, TFC's per-tick
 * {@code powerLoomTick}), and plays the weave sound when pushes were made. Between looks nothing runs.
 *
 * <p>Thread contract: every method except the future of {@link PowerLoomAccess#speed} runs on the loom's region thread.
 */
public final class PowerLoomService {
    /** Ticks between looks of a watched loom: a speed change reaches the loom at most this late. */
    public static final long LOOK_PERIOD = 10;

    private final PowerLoomAccess access;
    private final Logger logger;
    private final Map<Key, Object> watched = new ConcurrentHashMap<>();

    PowerLoomService(PowerLoomAccess access, Logger logger) {
        this.access = Objects.requireNonNull(access, "access");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean isWatched(Key key) {
        return watched.containsKey(key);
    }

    public int watchedCount() {
        return watched.size();
    }

    /** Starts watching {@code key}: the first look comes next tick. Region thread; a loom already watched is left alone. */
    public void watch(Key key) {
        Object token = new Object();
        if (watched.putIfAbsent(key, token) == null) {
            access.track(key);
            access.later(key, 1, () -> look(key, token));
        }
    }

    /** Stops watching without touching the persisted list (the chunk unloaded). */
    public void drop(Key key) {
        watched.remove(key);
    }

    private void unwatch(Key key, Object token) {
        if (watched.remove(key, token)) {
            access.untrack(key);
        }
    }

    private void look(Key key, Object token) {
        if (watched.get(key) != token) {
            return;
        }
        if (access.view(key).isEmpty()) {
            unwatch(key, token);
            return;
        }
        access.speed(key).whenComplete((speed, err) -> access.onRegion(key, () -> {
            try {
                commit(key, token, err != null ? Optional.empty() : speed);
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Power loom at " + key + ": a look failed", e);
                if (watched.get(key) == token) {
                    access.later(key, LOOK_PERIOD, () -> look(key, token));
                }
            }
        }));
    }

    /** The decision applied, region thread: re-reads the loom, so a change since the snapshot is honoured. */
    void commit(Key key, Object token, Optional<Float> speed) {
        if (watched.get(key) != token) {
            return;
        }
        Optional<View> before = access.view(key);
        if (before.isEmpty()) {
            unwatch(key, token);
            return;
        }
        access.apply(key, speed.orElse(0f));
        View after = access.view(key).orElse(before.get());
        if (after.pushes() != before.get().pushes()) {
            access.weave(key);
        }
        if (speed.isPresent() || after.active()) {
            access.later(key, LOOK_PERIOD, () -> look(key, token));
        } else {
            unwatch(key, token);
        }
    }
}
