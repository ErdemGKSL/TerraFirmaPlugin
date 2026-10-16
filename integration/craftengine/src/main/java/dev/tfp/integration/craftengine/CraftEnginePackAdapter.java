/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.visuals.ProfileCheck.PoolUsage;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.PackRebuilder;
import dev.tfp.paper.integration.VisualPools;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.AutoStateGroup;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.pack.allocator.BlockStateCandidate;
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
                if (ce.isReloading() || ce.isReloadingPack() || ce.resourceOperations().isBusy()) {
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
        Set<Integer> ours = new HashSet<>();
        for (Map.Entry<Key, BlockDefinition> e : CraftEngineBlocks.loadedBlocks().entrySet()) {
            if (!namespace.equals(e.getKey().namespace())) {
                continue;
            }
            for (ImmutableBlockState s : e.getValue().variantProvider().states()) {
                BlockStateWrapper visual = s.visualBlockState();
                if (visual != null && usedIds.contains(visual.registryId())) {
                    ours.add(visual.registryId());
                }
            }
        }
        return Optional.of(new PoolUsage(capacity, usedIds.size(), ours.size()));
    }
}
