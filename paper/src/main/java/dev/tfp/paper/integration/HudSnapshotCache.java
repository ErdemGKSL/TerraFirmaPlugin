/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/**
 * The per-player snapshot store behind {@link HudBridge} (betterhud skill, "Registering placeholders"):
 * writers are the players' own threads, readers are BetterHud's async placeholder evaluation. A
 * {@link ConcurrentHashMap} of immutable records, so a reader always sees a complete snapshot.
 */
public final class HudSnapshotCache implements HudBridge {
    private final Map<UUID, HudSnapshot> snapshots = new ConcurrentHashMap<>();
    private final BooleanSupplier active;

    /** @param active whether a HUD plugin currently renders snapshots */
    public HudSnapshotCache(BooleanSupplier active) {
        this.active = active;
    }

    @Override
    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    public void publish(Player player, HudSnapshot snapshot) {
        ThreadAsserts.owned(player);
        put(player.getUniqueId(), snapshot);
    }

    /** Stores without the thread check; for callers that already proved ownership, and tests. */
    public void put(UUID player, HudSnapshot snapshot) {
        snapshots.put(Objects.requireNonNull(player), Objects.requireNonNull(snapshot));
    }

    @Override
    public HudSnapshot snapshot(UUID player) {
        return snapshots.getOrDefault(player, HudSnapshot.DEFAULT);
    }

    @Override
    public void remove(UUID player) {
        snapshots.remove(player);
    }

    public int size() {
        return snapshots.size();
    }
}
