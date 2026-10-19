/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Thirst and hunger for the HUD (M3-SURV-06). Nothing here decides anything: the values are already on the
 * player's thread, so the publish is a copy, not an async computation.
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, for each online player, a task goes to
 *       that player's own thread (the same period and mechanism as {@code PlayerClimateService}).</li>
 *   <li><b>Read and publish (player thread).</b> The stored thirst from {@link PlayerDataStore#get} (in memory, safe
 *       from any thread) and the vanilla food level, which is only read on the player's thread. Both go into the
 *       player's {@link HudSnapshot} in the {@link HudSnapshotCache}, leaving the other fields as they are.</li>
 * </ol>
 * Health and max health are not published here: the TFC health scale is an open decision (see the ledger).
 */
public final class PlayerNeedsHudService {
    /** The throttle: one reading per player per second. */
    public static final long PERIOD_TICKS = 20;

    private final PlayerDataStore data;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;

    public PlayerNeedsHudService(PlayerDataStore data, Scheduler scheduler, HudSnapshotCache hud) {
        this.data = data;
        this.scheduler = scheduler;
        this.hud = hud;
    }

    /**
     * The pure part: the snapshot with the stored thirst and the vanilla food level in place of the old ones.
     * Thirst is {@link PlayerData#thirst()} (already 0..{@link PlayerData#MAX_THIRST}); the food level is clamped to
     * vanilla's 0..20.
     */
    static HudSnapshot withNeeds(HudSnapshot base, PlayerData stored, int foodLevel) {
        double thirst = Math.clamp(stored.thirst(), 0f, PlayerData.MAX_THIRST);
        double hunger = Math.clamp(foodLevel, 0, 20);
        return base.withThirst(thirst).withHunger(hunger);
    }

    /** Starts the throttle on the global thread. Call once, after the scheduler exists. */
    public void start(Supplier<Collection<? extends Player>> online) {
        scheduler.globalRepeating(1, PERIOD_TICKS, () -> {
            if (!hud.isActive()) {
                return; // nothing renders the values: no work
            }
            for (Player player : online.get()) {
                refresh(player);
            }
        });
    }

    /** Any thread: hops to the player's thread to read and publish. */
    void refresh(Player player) {
        scheduler.entity(player, () -> publish(player), null);
    }

    /** Player thread only: reads the stored thirst and the food level, then publishes. */
    void publish(Player player) {
        ThreadAsserts.owned(player);
        Optional<PlayerData> stored = data.get(player.getUniqueId());
        if (stored.isEmpty()) {
            return; // not loaded yet (or already unloaded): the last snapshot stays
        }
        HudSnapshot base = hud.snapshot(player.getUniqueId());
        hud.publish(player, withNeeds(base, stored.get(), player.getFoodLevel()));
    }
}
