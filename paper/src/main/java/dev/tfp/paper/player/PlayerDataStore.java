/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The player data store (M1-SVC-02, ARCHITECTURE.md §7): loads at async pre-login, keeps one immutable
 * {@link PlayerData} per online player, saves asynchronously on quit and on the autosave interval.
 *
 * <ul>
 *   <li><b>Reads</b> ({@link #get}): any thread, lock-free (a volatile field per player).</li>
 *   <li><b>Writes</b> ({@link #update}): on the player's entity thread by convention (gameplay changes come
 *       from the player's own events); per-player updates are serialised, so concurrent updaters never lose
 *       a change.</li>
 *   <li><b>Disk</b>: only on the {@code io} executor (or the async pre-login thread). Writes of one player
 *       are chained in order, and a load waits for that player's pending writes, so a quick quit + rejoin
 *       never reads a stale file.</li>
 * </ul>
 */
public final class PlayerDataStore {
    /** An online player's slot. */
    private static final class Entry {
        volatile PlayerData data;
        volatile boolean dirty;
        final long loadedAt;

        Entry(PlayerData data, long loadedAt) {
            this.data = data;
            this.loadedAt = loadedAt;
        }
    }

    private final PlayerDataFiles files;
    private final Executor io;
    private final Logger log;
    private final ConcurrentHashMap<UUID, Entry> online = new ConcurrentHashMap<>();
    /** Tail of each player's write chain; removed when it completes and nothing was appended. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> writes = new ConcurrentHashMap<>();

    public PlayerDataStore(PlayerDataFiles files, Executor io, Logger log) {
        this.files = files;
        this.io = io;
        this.log = log;
    }

    /**
     * Loads the player on the calling thread (the async pre-login thread). Waits for that player's pending
     * writes first. A player without a file gets {@link PlayerData#NEW}.
     *
     * @throws dev.tfp.core.persist.PersistException if the data is unreadable or from a newer plugin version
     *                                               (the login should be refused; nothing is overwritten)
     */
    public PlayerData loadBlocking(UUID id) {
        CompletableFuture<Void> pending = writes.get(id);
        if (pending != null) {
            try {
                pending.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the previous save of " + id, e);
            } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
                log.warning("Previous save of " + id + " did not finish cleanly (" + e + "); loading what is on disk");
            }
        }
        PlayerData data;
        boolean restored;
        try {
            Optional<PlayerDataFiles.Loaded> loaded = files.read(id);
            if (loaded.isPresent() && loaded.get().fromBackup()) {
                log.warning("Player data of " + id + " restored from the backup: " + loaded.get().problem());
            }
            data = loaded.map(PlayerDataFiles.Loaded::data).orElse(PlayerData.NEW);
            restored = loaded.isPresent() && loaded.get().fromBackup();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read player data of " + id, e);
        }
        Entry entry = new Entry(data, System.nanoTime());
        entry.dirty = restored; // a restored backup becomes the main file again at the next autosave
        online.put(id, entry);
        return data;
    }

    /** {@link #loadBlocking} on the I/O executor. */
    public CompletableFuture<PlayerData> load(UUID id) {
        return CompletableFuture.supplyAsync(() -> loadBlocking(id), io);
    }

    /** The online player's data, or empty if not loaded. Any thread. */
    public Optional<PlayerData> get(UUID id) {
        Entry e = online.get(id);
        return e == null ? Optional.empty() : Optional.of(e.data);
    }

    public boolean isLoaded(UUID id) {
        return online.containsKey(id);
    }

    public Set<UUID> loaded() {
        return Set.copyOf(online.keySet());
    }

    /**
     * Applies {@code fn} to the player's data and marks it for saving. {@code fn} must be pure.
     *
     * @return the new value
     * @throws IllegalStateException if the player is not loaded
     */
    public PlayerData update(UUID id, UnaryOperator<PlayerData> fn) {
        Entry e = online.get(id);
        if (e == null) {
            throw new IllegalStateException("player data of " + id + " is not loaded");
        }
        synchronized (e) {
            PlayerData next = fn.apply(e.data);
            e.data = java.util.Objects.requireNonNull(next, "update result");
            e.dirty = true;
            return next;
        }
    }

    /** Saves the player's current data asynchronously (even if unchanged). */
    public CompletableFuture<Void> save(UUID id) {
        Entry e = online.get(id);
        if (e == null) {
            return CompletableFuture.completedFuture(null);
        }
        return writeAsync(id, e);
    }

    /** Removes the player (quit) and saves the data asynchronously. */
    public CompletableFuture<Void> unload(UUID id) {
        Entry e = online.remove(id);
        if (e == null) {
            return CompletableFuture.completedFuture(null);
        }
        return writeAsync(id, e);
    }

    /** Autosave: saves every changed player. */
    public CompletableFuture<Void> saveDirty() {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        online.forEach((id, e) -> {
            if (e.dirty) {
                all.add(writeAsync(id, e));
            }
        });
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
    }

    /**
     * Unloads (and saves) players loaded at pre-login more than {@code graceNanos} ago who are not online:
     * another plugin refused their login after we loaded them, so no quit event will come.
     *
     * @return how many were unloaded
     */
    public int unloadAbsent(Set<UUID> onlineNow, long graceNanos) {
        long now = System.nanoTime();
        int n = 0;
        for (var it : online.entrySet()) {
            if (!onlineNow.contains(it.getKey()) && now - it.getValue().loadedAt > graceNanos) {
                unload(it.getKey());
                n++;
            }
        }
        return n;
    }

    /** Saves everyone and waits (plugin disable). */
    public void close(long timeout, TimeUnit unit) {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (UUID id : Set.copyOf(online.keySet())) {
            all.add(unload(id));
        }
        all.addAll(writes.values());
        try {
            CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            log.log(Level.SEVERE, "Not all player data was saved on shutdown", e);
        }
    }

    private CompletableFuture<Void> writeAsync(UUID id, Entry e) {
        // Clear first: an update racing with this save sets it again and is saved next time.
        e.dirty = false;
        PlayerData snapshot = e.data;
        CompletableFuture<Void> next = writes.compute(id, (k, prev) -> {
            CompletableFuture<Void> base = prev == null ? CompletableFuture.completedFuture(null)
                    : prev.exceptionally(t -> null);
            return base.thenRunAsync(() -> {
                try {
                    files.write(id, snapshot);
                } catch (IOException | RuntimeException ex) {
                    e.dirty = true; // retried by the next autosave while the player is online
                    log.log(Level.SEVERE, "Cannot save player data of " + id, ex);
                    throw ex instanceof IOException io2 ? new UncheckedIOException(io2) : (RuntimeException) ex;
                }
            }, io);
        });
        next.whenComplete((v, t) -> writes.remove(id, next));
        return next;
    }
}
