/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.RegistrySnapshot;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Fluid;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Tag lookups from live Paper objects (PAR-INFRA-07): TFC's item/block/fluid/entity tags ({@code TFCTags}) asked of
 * an {@link ItemStack}, a {@link Block}, a CraftEngine state, a {@link Fluid} or an {@link EntityType}. CraftEngine
 * content is named through the {@link CustomItems}/{@link CustomBlocks} adapters; vanilla content by its key, which
 * also falls through to the server's own tags (see {@link TagSnapshot}).
 *
 * <p><b>Snapshots and reload:</b> every lookup reads one immutable {@link TagSnapshot}. When the registries publish a
 * new snapshot ({@code /tfp reload data}) the next lookup pairs it with the current {@link TagRules} in one atomic
 * swap, and the rules are re-read from the data root off-thread; lookups never wait for that.
 *
 * <p><b>Threads:</b> any thread, except {@link #is(Block, String)}, which reads a world block and must run on the
 * block's region thread.
 */
public final class Tags {
    private final Registries registries;
    private final CustomItems items;
    private final CustomBlocks blocks;
    private final ServerTags server;
    private final Supplier<TagRules> rulesLoader;
    private final Executor executor;
    private final Consumer<Throwable> onRulesError;

    private final AtomicReference<TagRules> rules;
    private final AtomicReference<TagSnapshot> current;
    /** Highest registry version a rules reload was started for. */
    private final AtomicLong rulesRequested;

    /**
     * @param initialRules  rules for the registry state at construction (usually {@code rulesLoader.get()})
     * @param rulesLoader   re-reads the rules after a data reload; called on {@code executor}
     * @param onRulesError  told when a rules reload fails (the previous rules stay)
     */
    public Tags(Registries registries, CustomItems items, CustomBlocks blocks, ServerTags server,
            TagRules initialRules, Supplier<TagRules> rulesLoader, Executor executor, Consumer<Throwable> onRulesError) {
        this.registries = registries;
        this.items = items;
        this.blocks = blocks;
        this.server = server;
        this.rulesLoader = rulesLoader;
        this.executor = executor;
        this.onRulesError = onRulesError;
        this.rules = new AtomicReference<>(initialRules);
        long v = registries.version();
        this.rulesRequested = new AtomicLong(v);
        this.current = new AtomicReference<>(new TagSnapshot(v, registries.get().tags(), initialRules, server));
    }

    /** The current snapshot; take it once per operation when asking several questions. */
    public TagSnapshot snapshot() {
        TagSnapshot s = current.get();
        long v = registries.version();
        if (s.registryVersion() == v) {
            return s;
        }
        RegistrySnapshot latest = registries.get();
        TagSnapshot next = new TagSnapshot(v, latest.tags(), rules.get(), server);
        current.compareAndSet(s, next);
        if (rulesRequested.getAndAccumulate(v, Math::max) < v) {
            reloadRules(v);
        }
        return next;
    }

    /** Re-reads the rules off-thread; the next lookup after it completes uses them. */
    public CompletableFuture<TagRules> reloadRules() {
        return reloadRules(registries.version());
    }

    private CompletableFuture<TagRules> reloadRules(long version) {
        return CompletableFuture.supplyAsync(rulesLoader, executor).whenComplete((r, err) -> {
            if (err != null) {
                onRulesError.accept(err);
                return;
            }
            rules.set(r);
            // Re-pair on the next lookup: a snapshot that cannot match any registry version. Retried until it holds,
            // so a concurrent snapshot() that read the old rules before the set cannot win the swap with them.
            current.updateAndGet(s -> s.rules() == r ? s : new TagSnapshot(-1, s.registry(), r, server));
        });
    }

    /** Whether {@code id} of {@code type} is in {@code tag}. Any thread. */
    public boolean is(TagType type, String id, String tag) {
        return snapshot().is(type, id, tag);
    }

    public boolean is(ItemStack stack, String tag) {
        Optional<String> id = itemId(stack);
        return id.isPresent() && is(TagType.ITEM, id.get(), tag);
    }

    /** The block at {@code block} (custom state first). Region thread of the block. */
    public boolean is(Block block, String tag) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        return state.isPresent() ? is(state.get(), tag) : is(TagType.BLOCK, block.getType().getKey().toString(), tag);
    }

    public boolean is(BlockStateRef state, String tag) {
        return is(TagType.BLOCK, state.id(), tag);
    }

    /** A vanilla block state (a captured snapshot's block data). For live custom blocks use {@link #is(Block, String)}. */
    public boolean is(BlockData data, String tag) {
        return is(TagType.BLOCK, data.getMaterial().getKey().toString(), tag);
    }

    public boolean is(Material blockOrItem, String tag, TagType type) {
        return is(type, blockOrItem.getKey().toString(), tag);
    }

    public boolean is(Fluid fluid, String tag) {
        return is(TagType.FLUID, fluid.getKey().toString(), tag);
    }

    public boolean is(EntityType entityType, String tag) {
        return entityType != EntityType.UNKNOWN && is(TagType.ENTITY_TYPE, entityType.getKey().toString(), tag);
    }

    /** The item id of {@code stack}: its CraftEngine id, else the vanilla material key; empty for null/air. */
    public Optional<String> itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        Optional<String> custom = items.idOf(stack);
        return custom.isPresent() ? custom : Optional.of(stack.getType().getKey().toString());
    }
}
