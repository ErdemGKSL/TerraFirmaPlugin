/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import dev.tfp.core.world.BlockPos;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

/**
 * In-memory {@link CustomBlocks}: a map from (world name, position) to state. Only {@code getX/Y/Z} and
 * {@code getWorld().getName()} of the given blocks are read, so tests may pass proxies. No thread checks.
 */
public final class FakeCustomBlocks implements CustomBlocks {
    /** A block position in a named world ({@code null} world allowed for single-world tests). */
    public record At(String world, BlockPos pos) {
    }

    private final Set<String> known = ConcurrentHashMap.newKeySet();
    private final Map<At, BlockStateRef> world = new ConcurrentHashMap<>();
    private final List<Consumer<Chunk>> loadHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<Chunk>> unloadHooks = new CopyOnWriteArrayList<>();

    public FakeCustomBlocks known(String... ids) {
        known.addAll(List.of(ids));
        return this;
    }

    public static At at(Block block) {
        return new At(block.getWorld() == null ? null : block.getWorld().getName(),
                new BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    /** Everything placed and not removed. */
    public Map<At, BlockStateRef> placed() {
        return Map.copyOf(world);
    }

    public void fireChunkLoad(Chunk chunk) {
        loadHooks.forEach(h -> h.accept(chunk));
    }

    public void fireChunkUnload(Chunk chunk) {
        unloadHooks.forEach(h -> h.accept(chunk));
    }

    @Override
    public boolean exists(String id) {
        return known.contains(id);
    }

    @Override
    public Optional<BlockStateRef> stateAt(Block block) {
        return Optional.ofNullable(world.get(at(block)));
    }

    @Override
    public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
        if (!known.contains(state.id())) {
            return false;
        }
        world.put(at(block), state);
        return true;
    }

    @Override
    public boolean remove(Block block) {
        return world.remove(at(block)) != null;
    }

    @Override
    public void onChunkLoad(Consumer<Chunk> hook) {
        loadHooks.add(hook);
    }

    @Override
    public void onChunkUnload(Consumer<Chunk> hook) {
        unloadHooks.add(hook);
    }
}
