/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.block.data.BlockData;

/** In-memory {@link BlockVisuals}: tests define which states resolve to which data, and trigger reloads. */
public final class FakeBlockVisuals implements BlockVisuals {
    private final Map<BlockStateRef, BlockData> table = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong generation = new AtomicLong();

    public FakeBlockVisuals define(BlockStateRef state, BlockData data) {
        table.put(state, data);
        return this;
    }

    /** Simulates a CraftEngine reload: bumps the generation and notifies listeners. */
    public void reload() {
        generation.incrementAndGet();
        listeners.forEach(Runnable::run);
    }

    @Override
    public boolean isLoaded() {
        return generation.get() > 0;
    }

    @Override
    public Optional<BlockData> blockData(BlockStateRef state) {
        return Optional.ofNullable(table.get(state));
    }

    @Override
    public long generation() {
        return generation.get();
    }

    @Override
    public void onReload(Runnable listener) {
        listeners.add(listener);
    }
}
