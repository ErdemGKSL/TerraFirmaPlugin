/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * PAR-INFRA-32: server-time break progress for blocks the client plugin mines server-side (renderer cubes and other
 * CraftEngine blocks). A dig start ({@link BlockDamageEvent}) starts a per-tick task on the player's scheduler; every
 * server tick in which the client is still digging counts once ({@link MiningSession}), so the break takes TFC's
 * number of server ticks whatever the client's frame or swing rate. The counting itself stays in the client plugin
 * ({@link MiningDriver#advance}), so its crack stages and break handling stay in step.
 *
 * <p>Threading: the events and the task run on the player's thread. The per-tick decision is a few field reads; it is
 * made on that thread because moving it to the worker pool would delay every count by a tick, the error this item
 * removes. No world state is written here (the client plugin breaks the block on its own path).
 */
public final class ServerTimeMining implements Listener {
    /** Starts {@code tick} every server tick on {@code player}'s thread; {@code retired} when the player is gone. */
    @FunctionalInterface
    public interface Ticker {
        Scheduler.TaskHandle start(Player player, Runnable tick, Runnable retired);
    }

    private static final class Entry {
        final MiningSession session = new MiningSession();
        volatile Scheduler.TaskHandle handle;
    }

    private final Supplier<MiningDriver> driver;
    private final Ticker ticker;
    private final Map<UUID, Entry> digs = new ConcurrentHashMap<>();

    public ServerTimeMining(Supplier<MiningDriver> driver, Ticker ticker) {
        this.driver = driver;
        this.ticker = ticker;
    }

    /** Survival and adventure dig; creative breaks instantly and spectators cannot dig. */
    static boolean digs(GameMode mode) {
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    /** Players with a running per-tick task. */
    public int active() {
        return digs.size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDigStart(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (event.getInstaBreak() || !digs(player.getGameMode()) || !driver.get().available()) {
            return;
        }
        UUID id = player.getUniqueId();
        Entry existing = digs.get(id);
        if (existing != null) {
            existing.session.start();
            return;
        }
        Entry entry = new Entry();
        entry.session.start();
        digs.put(id, entry);
        Scheduler.TaskHandle handle = ticker.start(player, () -> tick(player, entry), () -> forget(id, entry));
        if (handle == null) {
            digs.remove(id, entry);
            return;
        }
        entry.handle = handle;
    }

    /** One server tick of {@code player}'s dig. */
    void tick(Player player, Entry entry) {
        MiningDriver d = driver.get();
        MiningSession.Action action = entry.session.tick(digs(player.getGameMode()) && d.available(),
                d.available() && d.isServerMining(player));
        switch (action) {
            case ADVANCE -> d.advance(player);
            case WAIT -> {
            }
            case STOP -> {
                Scheduler.TaskHandle h = entry.handle;
                if (h != null) {
                    h.cancel();
                }
                forget(player.getUniqueId(), entry);
            }
        }
    }

    /** Test hook: one server tick of {@code player}'s dig, if there is one. */
    void tick(Player player) {
        Entry entry = digs.get(player.getUniqueId());
        if (entry != null) {
            tick(player, entry);
        }
    }

    private void forget(UUID id, Entry entry) {
        digs.remove(id, entry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Entry entry = digs.get(event.getPlayer().getUniqueId());
        if (entry != null) {
            entry.session.swing();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDigAbort(BlockDamageAbortEvent event) {
        abort(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        abort(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Entry entry = digs.remove(event.getPlayer().getUniqueId());
        if (entry != null && entry.handle != null) {
            entry.handle.cancel();
        }
    }

    private void abort(Player player) {
        Entry entry = digs.get(player.getUniqueId());
        if (entry != null) {
            entry.session.abort();
        }
    }
}
