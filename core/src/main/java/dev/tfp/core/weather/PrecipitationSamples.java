/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The newest precipitation sample per player (PAR-CLIM-02). Decisions finish on workers and are applied on the
 * player's thread, possibly out of order, so {@link #offer} keeps a sample only when it comes from a later clock reading
 * than the stored one. Thread-safe; a reader on any thread sees a complete {@link PrecipitationSample}.
 */
public final class PrecipitationSamples {
    private final Map<UUID, PrecipitationSample> latest = new ConcurrentHashMap<>();

    /**
     * Stores {@code sample} for {@code player} unless a sample from the same or a later clock reading is stored.
     *
     * @return true if the sample was stored
     */
    public boolean offer(UUID player, PrecipitationSample sample) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(sample, "sample");
        boolean[] stored = {false};
        latest.compute(player, (id, old) -> {
            if (old != null && !sample.isNewerThan(old)) {
                return old;
            }
            stored[0] = true;
            return sample;
        });
        return stored[0];
    }

    /** The newest sample of {@code player}, or empty if none was made yet (or the player left). */
    public Optional<PrecipitationSample> latest(UUID player) {
        return Optional.ofNullable(latest.get(player));
    }

    /** Forgets a player who left. */
    public void remove(UUID player) {
        latest.remove(player);
    }

    public int size() {
        return latest.size();
    }
}
