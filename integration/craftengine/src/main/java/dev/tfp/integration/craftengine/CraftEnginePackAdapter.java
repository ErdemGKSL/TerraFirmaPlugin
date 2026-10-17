/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.visuals.ProfileCheck;
import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.PackRebuilder;
import dev.tfp.paper.integration.VisualPools;
import dev.tfp.paper.scheduler.Scheduler;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.block.BukkitBlockManager;
import net.momirealms.craftengine.core.block.AutoStateGroup;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.pack.allocator.BlockStateCandidate;
import net.momirealms.craftengine.core.pack.allocator.VisualBlockStateAllocator;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;

/**
 * M1-INST-03 on CraftEngine: the pack rebuild ({@link PackRebuilder}) and the solid-pool counts
 * ({@link VisualPools}).
 *
 * <p>The rebuild mirrors CraftEngine's own {@code /ce reload all} ({@code ReloadCommand}, CE 26.9.2,
 * read from the bytecode): {@code reloadPlugin(async, platform, true, true, true, false)}, then on success
 * {@code packManager().triggerWorkflows("reload_pack")} on CraftEngine's async executor. It waits (polling
 * once a second on the global region) until CraftEngine is neither reloading nor busy with resources, so it
 * never starts inside the reload that triggered it.
 */
final class CraftEnginePackAdapter implements PackRebuilder, VisualPools {
    private static final long POLL_TICKS = 20;
    private static final int MAX_POLLS = 600; // 10 minutes

    private final IntegrationContext ctx;
    private final String namespace;

    CraftEnginePackAdapter(IntegrationContext ctx, String namespace) {
        this.ctx = ctx;
        this.namespace = namespace;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public boolean busy() {
        CraftEngine ce = CraftEngine.instance();
        return ce.isReloading() || ce.isReloadingPack() || ce.resourceOperations().isBusy();
    }

    @Override
    public CompletableFuture<Boolean> rebuild() {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        Scheduler scheduler = ctx.requireScheduler();
        AtomicReference<Scheduler.TaskHandle> handle = new AtomicReference<>();
        int[] polls = {0};
        handle.set(scheduler.globalRepeating(1, POLL_TICKS, () -> {
            if (out.isDone()) {
                cancel(handle);
                return;
            }
            try {
                CraftEngine ce = CraftEngine.instance();
                if (busy()) {
                    if (++polls[0] >= MAX_POLLS) {
                        cancel(handle);
                        out.completeExceptionally(new IllegalStateException("CraftEngine stayed busy; run /ce reload all"));
                    }
                    return;
                }
                cancel(handle);
                start(ce, out);
            } catch (RuntimeException | LinkageError e) {
                cancel(handle);
                out.completeExceptionally(e);
            }
        }));
        return out;
    }

    private static void cancel(AtomicReference<Scheduler.TaskHandle> handle) {
        Scheduler.TaskHandle h = handle.get();
        if (h != null) {
            h.cancel();
        }
    }

    private void start(CraftEngine ce, CompletableFuture<Boolean> out) {
        ce.reloadPlugin(ce.scheduler().async(), r -> ce.scheduler().platform().run(r), true, true, true, false)
                .thenAcceptAsync(result -> {
                    if (!result.success()) {
                        out.complete(false);
                        return;
                    }
                    try {
                        ce.packManager().triggerWorkflows("reload_pack");
                        out.complete(true);
                    } catch (Exception e) {
                        ctx.logger().log(Level.SEVERE, "CraftEngine pack workflows failed", e);
                        out.completeExceptionally(e);
                    }
                }, ce.scheduler().async())
                .exceptionally(e -> {
                    out.completeExceptionally(e);
                    return null;
                });
    }

    /**
     * Also drops our stale entries from CraftEngine's visual-state allocation cache ({@link ProfileCheck#staleClaims}):
     * CraftEngine keeps every share id it ever assigned and marks it used on load, so after a profile switch the
     * previous profile's ids would hold their slots (and count as other packs') for good. Dropping them frees the
     * slots at the next CraftEngine reload; this run keeps them (counted as {@code stale}).
     */
    @Override
    public Optional<PoolUsage> solid() {
        AutoStateGroup group = AutoStateGroup.SOLID;
        Set<Integer> usedIds = new HashSet<>();
        int capacity = 0;
        for (BlockStateCandidate c : group.candidates()) {
            capacity++;
            if (c.isUsed()) {
                usedIds.add(c.blockState().registryId());
            }
        }
        if (capacity == 0) {
            return Optional.empty();
        }
        Set<Integer> shown = new HashSet<>();
        for (Map.Entry<Key, BlockDefinition> e : CraftEngineBlocks.loadedBlocks().entrySet()) {
            if (!namespace.equals(e.getKey().namespace())) {
                continue;
            }
            for (ImmutableBlockState s : e.getValue().variantProvider().states()) {
                BlockStateWrapper visual = s.visualBlockState();
                if (visual != null) {
                    shown.add(visual.registryId());
                }
            }
        }
        Set<Integer> ours = new HashSet<>(shown);
        ours.retainAll(usedIds);
        Set<Integer> stale = releaseStale(shown);
        stale.retainAll(usedIds);
        stale.removeAll(ours);
        return Optional.of(new PoolUsage(capacity, usedIds.size(), ours.size(), stale.size()));
    }

    /** Drops our cache entries no loaded block of ours shows; returns their registry ids. */
    private Set<Integer> releaseStale(Set<Integer> shown) {
        VisualBlockStateAllocator allocator = BukkitBlockManager.instance().visualBlockStateAllocator();
        Map<String, Integer> cache = new HashMap<>();
        for (Map.Entry<String, BlockStateWrapper> e : allocator.cachedBlockStates().entrySet()) {
            cache.put(e.getKey(), e.getValue().registryId());
        }
        Set<Integer> staleIds = new HashSet<>();
        for (String k : ProfileCheck.staleClaims(cache, shown, namespace)) {
            staleIds.add(cache.get(k));
        }
        if (staleIds.isEmpty()) {
            return staleIds;
        }
        allocator.cleanupUnusedIds(state -> staleIds.contains(state.registryId()));
        try {
            allocator.saveToCache();
        } catch (IOException e) {
            ctx.logger().log(Level.WARNING, "Cannot save CraftEngine's visual-state cache; stale TerraFirmaPlugin "
                    + "states stay held until it is saved", e);
        }
        return staleIds;
    }
}
