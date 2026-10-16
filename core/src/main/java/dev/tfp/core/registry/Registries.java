/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import dev.tfp.core.concurrent.Snapshot;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Holder of the current {@link RegistrySnapshot} (ARCHITECTURE.md §6). Reads are a single
 * volatile load from any thread; a reload builds a complete new snapshot off-thread and swaps it
 * in atomically, so a reader sees either the old or the new data, never a mix. Overlapping
 * reloads are ordered by start: a reload that started earlier never replaces one that started
 * later, whatever order they finish in. A failed reload leaves the current snapshot in place.
 */
public final class Registries {
    /** Outcome of one reload. {@code published} is false when a later reload already won. */
    public record ReloadResult(RegistrySnapshot snapshot, long version, boolean published) {
    }

    private final Snapshot<RegistrySnapshot> current = new Snapshot<>(RegistrySnapshot.EMPTY);
    private final AtomicLong tickets = new AtomicLong();

    /** The current snapshot. Take it once per operation and use only it. */
    public RegistrySnapshot get() {
        return current.get();
    }

    /** Version of the current snapshot (0 = nothing loaded yet). */
    public long version() {
        return current.version();
    }

    /**
     * Builds a snapshot with {@code loader} on {@code executor} and publishes it if no later reload
     * has published first. The future fails with the loader's exception (e.g. {@link DataLoadException})
     * and the current snapshot stays.
     */
    public CompletableFuture<ReloadResult> reload(Executor executor, Supplier<RegistrySnapshot> loader) {
        long ticket = tickets.incrementAndGet();
        return CompletableFuture.supplyAsync(loader, executor)
                .thenApply(snapshot -> new ReloadResult(snapshot, ticket, current.publishIfNewer(ticket, snapshot)));
    }
}
