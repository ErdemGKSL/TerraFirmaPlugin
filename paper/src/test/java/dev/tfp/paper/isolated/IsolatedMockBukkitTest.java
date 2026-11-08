/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.isolated;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.isolated.IsolatedRule;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTableParser;
import dev.tfp.paper.loot.LootTables;
import dev.tfp.paper.loot.LootStackHooks;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-06 on MockBukkit: a placed raw granite with no collision neighbour pops on a later tick and drops itself
 * (TFC's raw-rock table with {@code isolated} set), a supported one stays, cancelled placements queue nothing, breaking
 * the support (the neighbour update) pops it, creative placements are checked too, {@code doTileDrops=false} pops
 * without a drop, and a support placed between decision and commit keeps the block.
 */
class IsolatedMockBukkitTest extends MockServerTest {
    private static final String RAW = "tfp:rock/raw/granite";
    private static final String LOOSE = "tfp:rock/loose/granite";
    /** TFC's {@code src/main/resources/data/tfc/loot_table/blocks/rock/raw/granite.json} (datagen renames tfc: to tfp:). */
    private static final String RAW_GRANITE_TABLE = "{\"type\":\"minecraft:block\",\"pools\":[{\"name\":\"loot_pool\","
            + "\"rolls\":1,\"entries\":[{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\","
            + "\"name\":\"tfc:rock/raw/granite\",\"conditions\":[{\"condition\":\"tfc:is_isolated\"}]},"
            + "{\"type\":\"minecraft:item\",\"name\":\"tfc:rock/loose/granite\",\"functions\":[{\"function\":"
            + "\"minecraft:set_count\",\"count\":{\"min\":1,\"max\":4,\"type\":\"minecraft:uniform\"}}]}]}],"
            + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";

    /** One queue for region tasks; "a tick" runs what is queued. The test thread owns every region. */
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(RAW);
    private final FakeCustomItems items = new FakeCustomItems();
    private IsolatedService service;
    private WorldMock world;
    private PlayerMock player;

    private final RegionExecutor regions = new RegionExecutor() {
        @Override
        public void run(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void runNextTick(int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public boolean isOwner(int chunkX, int chunkZ) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        items.define(RAW, n -> new ItemStack(Material.GRANITE, n));
        items.define(LOOSE, n -> new ItemStack(Material.COBBLESTONE, n));
        LootTables tables = new LootTables(Map.of("tfp:blocks/rock/raw/granite", LootTableParser.parse(
                "tfp:blocks/rock/raw/granite", RAW_GRANITE_TABLE.replace("\"tfc:", "\"tfp:"))), List.of());
        TestScheduler scheduler = new TestScheduler();
        LootService loot = new LootService(() -> tables, items,
                stack -> items.idOf(stack).or(() -> Optional.of(stack.getType().getKey().toString())),
                (item, tag) -> false, scheduler, () -> LootStackHooks.NONE, () -> () -> 0L,
                Logger.getLogger("isolated-test"));
        service = new IsolatedService(w -> regions,
                b -> blocks.stateAt(b).map(s -> s.id().startsWith("tfp:rock/raw/")).orElse(false),
                b -> b.getType().isSolid(), // MockBukkit has no isPassable; solid = non-empty collision here
                new LootPopper(loot, blocks, Logger.getLogger("isolated-test")), Runnable::run,
                new CommitBudget(1_000_000_000L, System::nanoTime));
        server.getPluginManager().registerEvents(new IsolatedListener(service, scheduler), plugin());
        world = server.addSimpleWorld("isolated");
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private int tick() {
        int n = tasks.size();
        for (int i = 0; i < n; i++) {
            tasks.poll().run();
        }
        return n;
    }

    private void runAll() {
        for (int i = 0; i < 100 && !tasks.isEmpty(); i++) {
            tick();
        }
        assertThat(tasks).as("settled").isEmpty();
    }

    private Block rawAt(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.STONE); // the carrier's server state: full collision
        assertThat(blocks.place(block, BlockStateRef.of(RAW, Map.of()), false)).isTrue();
        return block;
    }

    private BlockPlaceEvent place(Block block, boolean cancel) {
        Block against = block.getRelative(0, -1, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), against, new ItemStack(Material.GRANITE),
                player, true, EquipmentSlot.HAND);
        event.setCancelled(cancel);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (Entity e : world.getEntities()) {
            if (e instanceof Item item) {
                out.add(item.getItemStack());
            }
        }
        return out;
    }

    private boolean isRaw(Block block) {
        return blocks.stateAt(block).isPresent();
    }

    @Test
    void floatingRawGranitePopsOnALaterTickAndDropsItself() {
        Block raw = rawAt(0, 120, 0);
        place(raw, false);
        assertThat(isRaw(raw)).as("not inline in the event").isTrue();
        assertThat(tasks).hasSize(1);
        runAll();
        assertThat(isRaw(raw)).isFalse();
        assertThat(raw.getType()).isEqualTo(Material.AIR);
        assertThat(drops()).singleElement().satisfies(s -> {
            assertThat(s.getType()).as("the raw block, not loose rocks").isEqualTo(Material.GRANITE);
            assertThat(s.getAmount()).isEqualTo(1);
        });
        assertThat(service.pops(world)).isEqualTo(1);
    }

    @Test
    void aStoneNeighbourOnAnySideKeepsIt() {
        int[][] sides = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        int x = 0;
        for (int[] s : sides) {
            Block raw = rawAt(x, 120, 0);
            raw.getRelative(s[0], s[1], s[2]).setType(Material.STONE);
            place(raw, false);
            runAll();
            assertThat(isRaw(raw)).as("side %d,%d,%d", s[0], s[1], s[2]).isTrue();
            x += 4;
        }
        assertThat(drops()).isEmpty();
        assertThat(service.requests()).isEqualTo(6);
    }

    @Test
    void aTorchOrAFlowerIsNoSupport() {
        Block onTorch = rawAt(0, 120, 0);
        onTorch.getRelative(0, -1, 0).setType(Material.TORCH);
        Block onFlower = rawAt(4, 120, 0);
        onFlower.getRelative(1, 0, 0).setType(Material.POPPY);
        place(onTorch, false);
        place(onFlower, false);
        runAll();
        assertThat(isRaw(onTorch)).isFalse();
        assertThat(isRaw(onFlower)).isFalse();
        assertThat(drops()).hasSize(2).allSatisfy(s -> assertThat(s.getType()).isEqualTo(Material.GRANITE));
    }

    @Test
    void aCancelledPlacementQueuesNothing() {
        Block raw = rawAt(0, 120, 0);
        place(raw, true);
        assertThat(tasks).isEmpty();
        assertThat(service.requests()).isZero();
        assertThat(isRaw(raw)).isTrue();
    }

    @Test
    void breakingTheSupportPopsItThroughTheNeighbourUpdate() {
        Block raw = rawAt(0, 120, 0);
        Block support = raw.getRelative(0, -1, 0);
        support.setType(Material.STONE);
        place(raw, false);
        runAll();
        assertThat(isRaw(raw)).isTrue();
        support.setType(Material.AIR);
        server.getPluginManager().callEvent(new BlockPhysicsEvent(raw, raw.getBlockData()));
        runAll();
        assertThat(isRaw(raw)).isFalse();
        assertThat(drops()).singleElement().satisfies(s -> assertThat(s.getType()).isEqualTo(Material.GRANITE));
    }

    @Test
    void neighbourUpdatesOfBlocksOutsideTheTagCostNothing() {
        Block stone = world.getBlockAt(0, 120, 0);
        stone.setType(Material.STONE);
        server.getPluginManager().callEvent(new BlockPhysicsEvent(stone, stone.getBlockData()));
        Block air = world.getBlockAt(0, 121, 0);
        server.getPluginManager().callEvent(new BlockPhysicsEvent(air, air.getBlockData()));
        assertThat(tasks).isEmpty();
        assertThat(service.requests()).isZero();
        assertThat(stone.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void creativePlacementsAreCheckedToo() {
        player.setGameMode(GameMode.CREATIVE);
        Block raw = rawAt(0, 120, 0);
        place(raw, false);
        runAll();
        assertThat(isRaw(raw)).isFalse();
        assertThat(drops()).hasSize(1);
    }

    @Test
    void doTileDropsFalsePopsWithoutADrop() {
        assertThat(LootPopper.tileDropsRule()).as("doTileDrops / block_drops found by name").isNotNull();
        world.setGameRule(LootPopper.tileDropsRule(), false);
        Block raw = rawAt(0, 120, 0);
        place(raw, false);
        runAll();
        assertThat(isRaw(raw)).isFalse();
        assertThat(drops()).isEmpty();
    }

    @Test
    void aSupportPlacedBeforeTheCommitKeepsIt() {
        Block raw = rawAt(0, 120, 0);
        place(raw, false);
        tick(); // the placement task: snapshot and decision (inline workers); the commit is still queued
        assertThat(isRaw(raw)).isTrue();
        raw.getRelative(0, 0, 1).setType(Material.OAK_PLANKS);
        runAll();
        assertThat(isRaw(raw)).isTrue();
        assertThat(service.keptOnCommit(world)).isEqualTo(1);
        assertThat(drops()).isEmpty();
    }

    @Test
    void aBlockReplacedBeforeTheCommitIsNotPopped() {
        Block raw = rawAt(0, 120, 0);
        place(raw, false);
        tick();
        blocks.remove(raw);
        raw.setType(Material.GLASS);
        runAll();
        assertThat(raw.getType()).isEqualTo(Material.GLASS);
        assertThat(service.pops(world)).isZero();
    }

    @Test
    void twoFloatingRawRocksHoldEachOther() {
        Block a = rawAt(0, 121, 0);
        Block b = rawAt(0, 120, 0);
        place(a, false);
        place(b, false);
        runAll();
        assertThat(isRaw(a)).isTrue();
        assertThat(isRaw(b)).isTrue();
        assertThat(service.requests()).isEqualTo(2);
    }

    @Test
    void wiredThroughTheModuleServiceFile() throws Exception {
        String services = new String(IsolatedModule.class.getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule").readAllBytes(),
                StandardCharsets.UTF_8);
        List<String> lines = services.lines().toList();
        assertThat(lines).contains(IsolatedModule.class.getName());
        assertThat(lines.indexOf(IsolatedModule.class.getName()))
                .isGreaterThan(lines.indexOf("dev.tfp.paper.loot.LootModule"))
                .isGreaterThan(lines.indexOf("dev.tfp.paper.tags.TagsModule"));
        assertThat(new IsolatedModule().id()).isEqualTo("isolated");
        assertThat(IsolatedRule.BREAKS_WHEN_ISOLATED).isEqualTo("tfp:breaks_when_isolated");
    }

    /** Region tasks join the test's tick queue; CPU work runs inline. */
    private final class TestScheduler implements Scheduler {
        @Override
        public void region(Location location, Runnable task) {
            tasks.add(task);
        }

        @Override
        public void region(World w, int chunkX, int chunkZ, Runnable task) {
            tasks.add(task);
        }

        @Override
        public Scheduler.TaskHandle regionDelayed(World w, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler.TaskHandle regionRepeating(World w, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler.TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler.TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Executor ioExecutor() {
            return Runnable::run;
        }

        @Override
        public boolean isOwner(World w, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }
}
