/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import dev.tfp.core.prey.Sighting;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.entity.LivingEntity;

/**
 * Each entity's {@link Sighting} built at most once per tick and thread, however many animals see it in that tick
 * (PAR-FAUNA-26). Dropped when the stamp changes.
 */
public final class Sightings {
    private static final class State {
        long stamp = Long.MIN_VALUE;
        final Map<LivingEntity, Sighting> built = new IdentityHashMap<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private Sightings() {
    }

    /** The sighting of {@code e} for this stamp, built by {@code build} the first time it is asked for. */
    public static Sighting of(LivingEntity e, long stamp, Function<LivingEntity, Sighting> build) {
        State s = STATE.get();
        if (s.stamp != stamp) {
            s.built.clear();
            s.stamp = stamp;
        }
        Sighting v = s.built.get(e);
        if (v == null) {
            v = build.apply(e);
            s.built.put(e, v);
        }
        return v;
    }
}
