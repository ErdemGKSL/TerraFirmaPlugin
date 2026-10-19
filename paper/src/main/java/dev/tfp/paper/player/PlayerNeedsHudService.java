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
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Thirst and hunger for the HUD (M3-SURV-06, M3-SURV-03). The thirst is evaluated lazily here
 * ({@link ThirstDecay#evaluate}, the stored clock plus the player ticks since): nothing ticks for it.
 * <ol>
 *   <li><b>Throttle (global thread).</b> Every {@value #PERIOD_TICKS} ticks, for each online player, a task goes to
 *       that player's own thread (the same period and mechanism as {@code PlayerClimateService}).</li>
 *   <li><b>Evaluate and publish (player thread).</b> The stored data is evaluated at the current player tick with the
 *       ambient temperature, the new thirst clock is written back to {@link PlayerDataStore}, and the thirst and the
 *       vanilla food level go into the player's {@link HudSnapshot} in the {@link HudSnapshotCache}.</li>
 *   <li><b>Join (player thread).</b> The thirst clock restarts at the join, so the time the player was offline (while
 *       other players were online and the calendar ran) is not decayed.</li>
 * </ol>
 * Health and max health are not published here: the TFC health scale is an open decision (see the ledger).
 */
public final class PlayerNeedsHudService implements Listener {
    /** The throttle: one reading per player per second. */
    public static final long PERIOD_TICKS = 20;

    private final PlayerDataStore data;
    private final Scheduler scheduler;
    private final HudSnapshotCache hud;
    private final LongSupplier playerTicks;

    /** @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread */
    public PlayerNeedsHudService(PlayerDataStore data, Scheduler scheduler, HudSnapshotCache hud,
            LongSupplier playerTicks) {
        this.data = data;
        this.scheduler = scheduler;
        this.hud = hud;
        this.playerTicks = playerTicks;
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

    /** Player thread only: evaluates the thirst lazily, writes its clock back, then publishes. */
    void publish(Player player) {
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        Optional<PlayerData> stored = data.get(id);
        if (stored.isEmpty()) {
            return; // not loaded yet (or already unloaded): the last snapshot stays
        }
        HudSnapshot base = hud.snapshot(id);
        long now = playerTicks.getAsLong();
        float temperature = (float) base.ambientTemperature();
        PlayerData evaluated = data.update(id, d -> ThirstDecay.evaluate(d, now, temperature));
        hud.publish(player, withNeeds(base, evaluated, player.getFoodLevel()));
    }

    /** Restarts the thirst clock when the player joins (see the class comment). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (data.isLoaded(id)) {
            long now = playerTicks.getAsLong();
            data.update(id, d -> ThirstDecay.restart(d, now));
        }
    }
}
