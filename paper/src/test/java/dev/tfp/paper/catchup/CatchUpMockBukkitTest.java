/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.blockdata.InMemoryChunkDataStore;
import dev.tfp.paper.module.FeatureModules;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.pipeline.AsyncDecisionPipeline;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-11 on MockBukkit: chunk load queues each indexed block once and catches it up (decided async, applied by
 * the commit queue), interaction and sweep triggers, break and orphan pruning, unknown kinds kept, module wiring.
 * The fake kind keeps its state in a map ("the world" of the kind) for blocks that are wheat.
 */
class CatchUpMockBukkitTest extends MockServerTest {
    static final String KIND = "tfp:test/grow";
    private static final Logger LOG = Logger.getLogger("tfp-test");

    private final ManualRegions regions = new ManualRegions();
    private final InMemoryChunkDataStore store = new InMemoryChunkDataStore();
    private final AtomicLong now = new AtomicLong();
    private final Map<BlockPos, Integer> ages = new ConcurrentHashMap<>();
    private final Map<BlockPos, AtomicInteger> writes = new ConcurrentHashMap<>();
    private CatchUpService service;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("catchup");
        service = new CatchUpService(new CatchUpKinds(), store, w -> regions, Runnable::run, now::get,
                regions.clock::get, CatchUpSettings.DEFAULTS, LOG);
        service.kinds().register(KIND, (w, pos) -> w.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == Material.WHEAT
                ? ages.getOrDefault(pos, 0) : null, (w, pos, age) -> {
                    ages.put(pos, age);
                    writes.computeIfAbsent(pos, p -> new AtomicInteger()).incrementAndGet();
                }, (age, t) -> Math.max(age, ProbeCatchUp.ageAt(t)));
        server.getPluginManager().registerEvents(new CatchUpListener(service), plugin);
    }

    private Block wheat(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.WHEAT);
        return b;
    }

    private static BlockPos pos(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }

    private AsyncDecisionPipeline<?, ?> pipeline() {
        return service.pipeline(world, KIND).orElseThrow();
    }

    /** Unloads (cache dropped) and loads the chunk again through the events. */
    private void reload(Chunk chunk) {
        server.getPluginManager().callEvent(new ChunkUnloadEvent(chunk));
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
    }

    @Test
    void chunkLoadEnqueuesOncePerIndexedBlock() {
        List<Block> blocks = List.of(wheat(1, 64, 1), wheat(5, 70, 9), wheat(15, 1, 15));
        blocks.forEach(b -> assertThat(service.track(b, KIND)).isTrue());
        Chunk chunk = blocks.getFirst().getChunk();
        assertThat(store.load(chunk, CatchUpService.INDEX_KEY).orElseThrow().size()).isEqualTo(3);

        now.set(3_500);
        server.getPluginManager().callEvent(new ChunkUnloadEvent(chunk));
        // two loads before the region runs: still one catch-up per block
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        regions.settle(100);

        assertThat(pipeline().metrics().submits.sum()).isEqualTo(3);
        assertThat(service.stats().fed()).isEqualTo(3);
        blocks.forEach(b -> {
            assertThat(ages.get(pos(b))).isEqualTo(3);
            assertThat(writes.get(pos(b)).get()).isEqualTo(1);
        });
        assertThat(service.isIdle()).isTrue();

        // nothing changed since: a reload decides again but writes nothing
        reload(chunk);
        regions.settle(100);
        assertThat(pipeline().metrics().submits.sum()).isEqualTo(6);
        assertThat(pipeline().metrics().noops.sum()).isEqualTo(3);
        blocks.forEach(b -> assertThat(writes.get(pos(b)).get()).isEqualTo(1));
    }

    @Test
    void interactionCatchesUpTheClickedBlockOnly() {
        Block a = wheat(2, 64, 2);
        Block b = wheat(3, 64, 2);
        service.track(a, KIND);
        service.track(b, KIND);
        now.set(7_000);
        PlayerMock player = server.addPlayer();
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, a,
                BlockFace.UP));
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null,
                BlockFace.UP));
        regions.settle(10);
        assertThat(ages.get(pos(a))).isEqualTo(7);
        assertThat(ages).doesNotContainKey(pos(b));
        assertThat(service.stats().touches()).isEqualTo(1);
        // a block that is not indexed is ignored
        Block plain = world.getBlockAt(9, 64, 9);
        assertThat(service.touch(plain)).isFalse();
    }

    @Test
    void breakingUntracksAndGoneBlocksArePruned() {
        Block a = wheat(4, 64, 4);
        Block b = wheat(6, 64, 4);
        Block c = wheat(8, 64, 4);
        service.track(a, KIND);
        service.track(b, KIND);
        service.track(c, KIND);
        PlayerMock player = server.addPlayer();
        server.getPluginManager().callEvent(new BlockBreakEvent(a, player));
        assertThat(service.kindAt(a)).isEmpty();
        b.setType(Material.STONE); // replaced without an event (explosion, setType)
        Chunk chunk = a.getChunk();
        reload(chunk);
        regions.settle(100);
        LazyBlockIndex stored = store.load(chunk, CatchUpService.INDEX_KEY).orElseThrow();
        assertThat(stored.entries()).containsOnlyKeys(pos(c));
        assertThat(service.stats().orphans()).isEqualTo(1);
        // the last entry gone: the chunk entry is removed
        c.setType(Material.AIR);
        reload(chunk);
        regions.settle(100);
        assertThat(store.read(chunk, CatchUpService.INDEX_KEY)).isEmpty();
    }

    @Test
    void sweepCatchesUpWatchedChunksOncePerPeriod() {
        Block a = wheat(20, 64, 20);
        service.track(a, KIND);
        Chunk chunk = a.getChunk();
        reload(chunk);
        regions.settle(100);
        assertThat(service.sweep(world, chunk.getX(), chunk.getZ())).as("just loaded").isFalse();
        now.set(2_000);
        regions.clock.addAndGet(600 * ManualRegions.TICK);
        assertThat(service.sweep(world, chunk.getX(), chunk.getZ())).isTrue();
        assertThat(service.sweep(world, chunk.getX(), chunk.getZ())).as("second player, same period").isFalse();
        regions.settle(100);
        assertThat(ages.get(pos(a))).isEqualTo(2);
        assertThat(service.sweep(world, 1000, 1000)).as("unloaded chunk").isFalse();
        assertThat(world.isChunkLoaded(1000, 1000)).isFalse();
    }

    @Test
    void sweepAroundAPlayerUsesTheRadius() {
        Block near = wheat(0, 64, 0);
        Block far = wheat(16 * 6, 64, 0);
        service.track(near, KIND);
        service.track(far, KIND);
        regions.clock.addAndGet(600 * ManualRegions.TICK);
        now.set(4_000);
        ServerScheduler scheduler = new ServerScheduler(plugin, 1);
        try {
            CatchUpSweep sweep = new CatchUpSweep(service, scheduler, List::of);
            PlayerMock player = server.addPlayer();
            player.teleport(world.getBlockAt(8, 64, 8).getLocation());
            sweep.sweepAround(player);
            server.getScheduler().performTicks(2);
            regions.settle(100);
        } finally {
            scheduler.shutdown();
        }
        assertThat(ages.get(pos(near))).isEqualTo(4);
        assertThat(ages).doesNotContainKey(pos(far));
    }

    @Test
    void unknownKindsAreKeptAndSkipped() {
        Block a = wheat(1, 64, 30);
        service.track(a, "tfp:test/disabled_module");
        Chunk chunk = a.getChunk();
        reload(chunk);
        regions.settle(100);
        assertThat(service.stats().unknownKinds()).isEqualTo(1);
        assertThat(store.load(chunk, CatchUpService.INDEX_KEY).orElseThrow().kindAt(pos(a)))
                .contains("tfp:test/disabled_module");
    }

    @Test
    void directKindsRunOnTheRegionThreadAndPruneWhenGone() {
        AtomicInteger calls = new AtomicInteger();
        service.kinds().registerDirect("tfp:test/direct", (w, p) -> {
            calls.incrementAndGet();
            return w.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.FURNACE;
        });
        Block f = world.getBlockAt(40, 64, 40);
        f.setType(Material.FURNACE);
        Block gone = world.getBlockAt(41, 64, 40);
        service.track(f, "tfp:test/direct");
        service.track(gone, "tfp:test/direct");
        reload(f.getChunk());
        regions.settle(100);
        assertThat(calls).hasValue(2);
        assertThat(service.kindAt(f)).isPresent();
        assertThat(service.kindAt(gone)).isEmpty();
    }

    @Test
    void unreadableIndexIsLeftUntouched() {
        Block a = wheat(1, 64, 50);
        Chunk chunk = a.getChunk();
        store.write(chunk, CatchUpService.INDEX_KEY, new byte[] {1, 2, 3});
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        regions.settle(10);
        assertThat(service.track(a, KIND)).isFalse();
        assertThat(store.read(chunk, CatchUpService.INDEX_KEY).orElseThrow()).containsExactly(1, 2, 3);
        assertThat(service.stats().decodeErrors()).isEqualTo(2);
    }

    @Test
    void moduleProvidesTheServiceAndListens() {
        ModuleServices services = new ModuleServices();
        GlobalClockHolder clock = new GlobalClockHolder();
        ServerScheduler scheduler = new ServerScheduler(plugin, 1);
        try {
            ModuleContext ctx = new ModuleContext.Of((JavaPlugin) plugin, LOG, scheduler, null, null, null, null, null,
                    null, store, clock, null, null, services);
            CatchUpModule module = new CatchUpModule();
            FeatureModules modules = FeatureModules.of(List.of(module));
            assertThat(modules.enableAll(ctx, LOG)).containsExactly(CatchUpModule.ID);
            CatchUpService provided = services.require(CatchUpService.class);
            assertThat(provided.settings()).isEqualTo(CatchUpSettings.DEFAULTS);
            assertThat(provided.kinds().ids()).doesNotContain(CatchUpModule.DEVICE_KIND);
            assertThat(ChunkLoadEvent.getHandlerList().getRegisteredListeners())
                    .anyMatch(r -> r.getListener() instanceof CatchUpListener);
            // disable() cancels the sweep task: MockBukkit does not implement cancelling Paper tasks
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void settingsFromProperties() {
        Map<String, String> props = Map.of("tfp.catchup.sweepPeriodTicks", "0", "tfp.catchup.sweepRadiusChunks", "99",
                "tfp.catchup.feedBudgetMicros", "500", "tfp.catchup.commitBudgetMicros", "x");
        CatchUpSettings s = CatchUpSettings.fromProperties(props::get);
        assertThat(s.sweepEnabled()).isFalse();
        assertThat(s.sweepRadiusChunks()).isEqualTo(4);
        assertThat(s.feedBudgetNanos()).isEqualTo(500_000);
        assertThat(s.commitBudgetNanos()).isEqualTo(1_000_000);
    }
}
