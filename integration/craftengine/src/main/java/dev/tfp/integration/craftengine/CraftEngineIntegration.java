/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.Integration;
import dev.tfp.paper.integration.IntegrationContext;
import dev.tfp.paper.integration.PackRebuilder;
import dev.tfp.paper.integration.VisualPools;
import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * CraftEngine adapter base (M1-INT-02, {@code craftengine-java-api} skill):
 * <ul>
 *   <li>behaviour registration in our {@code onLoad()} ({@link CraftEngineBehaviours});</li>
 *   <li>a state cache ({@link BlockVisuals}) rebuilt on <b>every</b> {@link CraftEngineReloadEvent}; the
 *       first one also signals {@link ReadyGate.Signal#CRAFTENGINE_RELOADED};</li>
 *   <li>region-thread-only world calls ({@link CustomBlocks}): refused off the owning region thread;</li>
 *   <li>item lookups ({@link CustomItems}, served by {@link CraftEngineItemAdapter});</li>
 *   <li>pack rebuild and solid-pool counts ({@link PackRebuilder}, {@link VisualPools}, served by
 *       {@link CraftEnginePackAdapter}, M1-INST-03);</li>
 *   <li>chunk hooks at priority LOW on load (after CraftEngine restores at LOWEST) and HIGH on unload
 *       (before CraftEngine saves at HIGHEST);</li>
 *   <li>{@link CraftEngineIssueLog} for configuration issues in our pack (appender wiring: M1-TEST-01).</li>
 * </ul>
 * Never caches CraftEngine behaviour objects across reloads: {@code /ce reload} replaces them.
 */
public final class CraftEngineIntegration implements Integration, BlockVisuals, CustomBlocks, Listener {
    /** Our CraftEngine namespace and pack folder. */
    public static final String NAMESPACE = "tfp";

    /** One build of the state cache. Lookups not precomputed at reload are resolved lazily and memoised. */
    private record Table(long generation, Map<BlockStateRef, Optional<BlockData>> data) {
        static final Table EMPTY = new Table(0, new ConcurrentHashMap<>());
    }

    private final IntegrationContext ctx;
    private final CraftEngineBehaviours behaviours;
    private final CraftEngineIssueLog issues = new CraftEngineIssueLog(NAMESPACE);
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Chunk>> chunkLoadHooks = new CopyOnWriteArrayList<>();
    private final List<Consumer<Chunk>> chunkUnloadHooks = new CopyOnWriteArrayList<>();
    private volatile Table table = Table.EMPTY;
    private final CraftEngineItemAdapter items = new CraftEngineItemAdapter();
    private final CraftEnginePackAdapter pack;
    private volatile boolean loaded;

    CraftEngineIntegration(IntegrationContext ctx, CraftEngineBehaviours behaviours) {
        this.ctx = ctx;
        this.behaviours = behaviours;
        this.pack = new CraftEnginePackAdapter(ctx, NAMESPACE);
    }

    /** Where feature adapters add their behaviours before {@code onLoad}. */
    public CraftEngineBehaviours behaviours() {
        return behaviours;
    }

    public CraftEngineIssueLog issues() {
        return issues;
    }

    @Override
    public String pluginName() {
        return CraftEngineProvider.PLUGIN;
    }

    @Override
    public <T> Optional<T> service(Class<T> type) {
        if (type == CustomItems.class) {
            return Optional.of(type.cast(items));
        }
        if (type == PackRebuilder.class || type == VisualPools.class) {
            return Optional.of(type.cast(pack));
        }
        return Integration.super.service(type);
    }

    @Override
    public Optional<ReadyGate.Signal> readySignal() {
        return Optional.of(ReadyGate.Signal.CRAFTENGINE_RELOADED);
    }

    @Override
    public void onLoad() {
        int n = behaviours.registerAll();
        ctx.logger().info("CraftEngine: registered " + n + " block behaviour(s)");
    }

    @Override
    public void onEnable() {
        // CraftEngine's first CraftEngineReloadEvent comes after our onEnable (M0-CE-01, all targets), so
        // registering here does not miss it.
        ctx.plugin().getServer().getPluginManager().registerEvents(this, ctx.plugin());
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
    }

    // ---- reload: rebuild the state cache ------------------------------------------------------------

    /** Main thread on Paper, global region on Folia. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        Table next = new Table(table.generation() + 1, new ConcurrentHashMap<>());
        int blocks = 0;
        for (Map.Entry<Key, BlockDefinition> e : CraftEngineBlocks.loadedBlocks().entrySet()) {
            if (NAMESPACE.equals(e.getKey().namespace())) {
                BlockData data = CraftEngineBlocks.getBukkitBlockData(e.getValue().defaultState());
                next.data().put(BlockStateRef.of(e.getKey().toString()), Optional.ofNullable(data));
                blocks++;
            }
        }
        int items = 0;
        for (Key k : CraftEngineItems.loadedItems().keySet()) {
            if (NAMESPACE.equals(k.namespace())) {
                items++;
            }
        }
        table = next;
        loaded = true;
        ctx.logger().info("CraftEngine " + (event.isFirstReload() ? "loaded" : "reloaded") + ": " + blocks
                + " " + NAMESPACE + " block(s), " + items + " item(s); state cache generation " + next.generation()
                + (issues.total() > 0 ? "; " + issues.total() + " configuration issue(s) in our pack" : ""));
        for (Runnable r : reloadListeners) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                ctx.logger().log(Level.SEVERE, "CraftEngine reload listener failed", ex);
            }
        }
        ctx.readyGate().signal(ReadyGate.Signal.CRAFTENGINE_RELOADED);
    }

    // ---- BlockVisuals (any thread) ------------------------------------------------------------------

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public Optional<BlockData> blockData(BlockStateRef state) {
        return table.data().computeIfAbsent(state, s -> {
            ImmutableBlockState resolved = resolve(s);
            return resolved == null ? Optional.empty() : Optional.ofNullable(CraftEngineBlocks.getBukkitBlockData(resolved));
        });
    }

    @Override
    public long generation() {
        return table.generation();
    }

    @Override
    public void onReload(Runnable listener) {
        reloadListeners.add(listener);
    }

    /** Our ref → CraftEngine state, or null if the block, a property or a value is unknown. Any thread. */
    static ImmutableBlockState resolve(BlockStateRef ref) {
        String id = ref.id();
        int colon = id.indexOf(':');
        BlockDefinition def = CraftEngineBlocks.byId(Key.of(id.substring(0, colon), id.substring(colon + 1)));
        if (def == null) {
            return null;
        }
        ImmutableBlockState state = def.defaultState();
        for (Map.Entry<String, String> e : ref.properties().entrySet()) {
            Property<?> property = def.getProperty(e.getKey());
            if (property == null) {
                return null;
            }
            state = withValue(state, property, e.getValue());
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> ImmutableBlockState withValue(ImmutableBlockState state, Property<T> property,
            String value) {
        Optional<T> v = property.optional(value);
        return v.isEmpty() ? null : state.with(property, v.get());
    }

    /** CraftEngine state → our ref. */
    static BlockStateRef toRef(ImmutableBlockState state) {
        TreeMap<String, String> props = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> e : state.propertyEntries().entrySet()) {
            props.put(e.getKey().name(), Property.formatValue(e.getKey(), e.getValue()));
        }
        return BlockStateRef.of(state.owner().value().id().toString(), props);
    }

    // ---- CustomBlocks (region thread) ---------------------------------------------------------------

    @Override
    public boolean exists(String id) {
        int colon = id.indexOf(':');
        return colon > 0 && CraftEngineBlocks.byId(Key.of(id.substring(0, colon), id.substring(colon + 1))) != null;
    }

    @Override
    public Optional<BlockStateRef> stateAt(Block block) {
        requireOwner(block, "read a custom block");
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        return state == null || state.isEmpty() ? Optional.empty() : Optional.of(toRef(state));
    }

    @Override
    public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
        requireOwner(block, "place a custom block");
        ImmutableBlockState resolved = resolve(state);
        return resolved != null && CraftEngineBlocks.place(block.getLocation(), resolved, applyPhysics);
    }

    @Override
    public boolean remove(Block block) {
        requireOwner(block, "remove a custom block");
        return CraftEngineBlocks.remove(block);
    }

    /** CraftEngine world calls corrupt state off the owning region thread, so this check is always on. */
    private void requireOwner(Block block, String what) {
        Scheduler s = ctx.requireScheduler();
        if (!s.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            throw new IllegalStateException("cannot " + what + " at " + block.getWorld().getName() + " " + block.getX()
                    + "," + block.getY() + "," + block.getZ() + " from " + Thread.currentThread().getName()
                    + ": not the owning region thread (use Scheduler.region)");
        }
    }

    @Override
    public void onChunkLoad(Consumer<Chunk> hook) {
        chunkLoadHooks.add(hook);
    }

    @Override
    public void onChunkUnload(Consumer<Chunk> hook) {
        chunkUnloadHooks.add(hook);
    }

    /** After CraftEngine restored the chunk's custom blocks (LOWEST). */
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent event) {
        runHooks(chunkLoadHooks, event.getChunk(), "chunk load");
    }

    /** Before CraftEngine saves the chunk (HIGHEST). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkUnload(ChunkUnloadEvent event) {
        runHooks(chunkUnloadHooks, event.getChunk(), "chunk unload");
    }

    private void runHooks(List<Consumer<Chunk>> hooks, Chunk chunk, String what) {
        for (Consumer<Chunk> h : hooks) {
            try {
                h.accept(chunk);
            } catch (RuntimeException ex) {
                ctx.logger().log(Level.SEVERE, what + " hook failed for chunk " + chunk.getX() + "," + chunk.getZ(), ex);
            }
        }
    }
}
