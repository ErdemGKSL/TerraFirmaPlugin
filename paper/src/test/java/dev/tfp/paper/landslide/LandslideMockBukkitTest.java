/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pipeline.BlockChange;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.pipeline.NeighbourSnapshot;
import dev.tfp.paper.pipeline.RegionExecutor;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-03 on MockBukkit: the 2-tick queue (nothing is read before the delay ends, repeated triggers collapse), dirt
 * on a ledge falls when its support goes, dirt on a pillar slides sideways into the drawn open side as its recipe result
 * (grass becomes dirt), two supported sides or a sturdy block above hold it, a torch in the way is broken, blocks that
 * cannot landslide cost nothing, creative placements are checked, cancelled ones are not, and a block changed between
 * decision and commit is left alone.
 */
class LandslideMockBukkitTest extends MockServerTest {
    private static final String DIRT = "tfp:dirt/loam";
    private static final String GRASS = "tfp:grass/loam";

    /** Region tasks run on the next "tick"; delayed ones after their delay. The test thread owns every region. */
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(DIRT, GRASS);
    private final FakeWorld fake = new FakeWorld();
    private final int[] draw = {0};
    private LandslideService service;
    private WorldMock world;
    private PlayerMock player;

    private static final class Delayed {
        long ticks;
        final Runnable task;

        Delayed(long ticks, Runnable task) {
            this.ticks = ticks;
            this.task = task;
        }
    }

    private record Spawn(Block at, String state) {}

    /** Dirt and grass are full custom blocks; stone is sturdy; a torch is not; air, water and lava are air. */
    private final class FakeWorld implements LandslideWorld {
        final List<Spawn> spawns = new ArrayList<>();
        final List<Block> destroyed = new ArrayList<>();
        final List<Block> sounds = new ArrayList<>();
        int cellReads;

        @Override
        public boolean canLandslide(Block block) {
            return custom.stateAt(block).isPresent();
        }

        @Override
        public SlideState.Cell cell(Block block) {
            cellReads++;
            var state = custom.stateAt(block);
            if (state.isPresent()) {
                return new SlideState.Cell(state.get().id(), EnumSet.allOf(Direction.class), 0, false, false, DIRT);
            }
            return switch (block.getType()) {
                case AIR, CAVE_AIR, WATER, LAVA -> SlideState.Cell.AIR;
                case STONE -> new SlideState.Cell("minecraft:stone", EnumSet.allOf(Direction.class), 2, false, false,
                        null);
                case BEDROCK -> new SlideState.Cell("minecraft:bedrock", EnumSet.allOf(Direction.class), 4, true, false,
                        null);
                case FARMLAND -> new SlideState.Cell("minecraft:farmland", Set.of(), 0, false, true, null);
                default -> new SlideState.Cell(block.getType().getKey().toString(), Set.of(), 0, false, false, null);
            };
        }

        @Override
        public boolean canFall(String state) {
            return custom.exists(BlockStateRef.parse(state).id());
        }

        @Override
        public boolean spawnFalling(Block at, String state) {
            spawns.add(new Spawn(at, state));
            return true;
        }

        @Override
        public void remove(Block block) {
            if (!custom.remove(block)) {
                block.setType(Material.AIR);
            }
        }

        @Override
        public void destroy(Block block) {
            destroyed.add(block);
            remove(block);
        }

        @Override
        public void slideSound(Block block) {
            sounds.add(block);
        }
    }

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
        service = new LandslideService(w -> regions, fake, new TestScheduler(), Runnable::run,
                new CommitBudget(1_000_000_000L, System::nanoTime), new SupportTable(List.of()), bound -> draw[0]);
        server.getPluginManager().registerEvents(new LandslideListener(service), plugin);
        world = server.addSimpleWorld("landslide");
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** One server tick: delayed tasks count down, then everything queued before the tick runs. */
    private void tick() {
        Iterator<Delayed> it = delayed.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Delayed d = it.next();
            if (--d.ticks <= 0) {
                it.remove();
                due.add(d.task);
            }
        }
        int n = tasks.size();
        for (int i = 0; i < n; i++) {
            tasks.poll().run();
        }
        due.forEach(Runnable::run);
    }

    private void runAll() {
        for (int i = 0; i < 100 && (!tasks.isEmpty() || !delayed.isEmpty()); i++) {
            tick();
        }
        assertThat(tasks).as("settled").isEmpty();
        assertThat(delayed).as("settled").isEmpty();
    }

    private Block soil(String id, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.DIRT); // the carrier's server state
        assertThat(custom.place(block, BlockStateRef.of(id), false)).isTrue();
        return block;
    }

    private boolean isSoil(Block block) {
        return custom.stateAt(block).isPresent();
    }

    private void place(Block block, boolean cancel) {
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
                new ItemStack(Material.DIRT), player, true, EquipmentSlot.HAND);
        event.setCancelled(cancel);
        server.getPluginManager().callEvent(event);
    }

    private void notify(Block block) {
        server.getPluginManager().callEvent(new BlockPhysicsEvent(block, block.getBlockData()));
    }

    @Test
    void dirtOnALedgeFallsWhenItsSupportIsRemoved() {
        Block dirt = soil(DIRT, 0, 100, 0);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                dirt.getRelative(x, -1, z).setType(Material.STONE); // a stone shelf: no side has a drop
            }
        }
        Block ledge = dirt.getRelative(0, -1, 0);
        place(dirt, false);
        runAll();
        assertThat(isSoil(dirt)).as("supported from below").isTrue();
        assertThat(fake.spawns).isEmpty();

        ledge.setType(Material.AIR);
        notify(dirt);
        runAll();
        assertThat(isSoil(dirt)).isFalse();
        assertThat(fake.spawns).singleElement().satisfies(s -> {
            assertThat(s.at()).isEqualTo(dirt);
            assertThat(s.state()).isEqualTo(DIRT);
        });
        assertThat(service.falls(world)).isEqualTo(1);
        assertThat(fake.sounds).containsExactly(dirt);
    }

    @Test
    void theCheckWaitsTwoTicksAndRepeatedTriggersCollapse() {
        Block dirt = soil(DIRT, 0, 100, 0);
        notify(dirt);
        notify(dirt);
        notify(dirt);
        assertThat(service.requests()).isEqualTo(1);
        assertThat(service.coalesced()).isEqualTo(2);
        fake.cellReads = 0;
        tick();
        assertThat(fake.cellReads).as("nothing read after one tick").isZero();
        assertThat(service.metrics(world).submits.sum()).isZero();
        assertThat(isSoil(dirt)).isTrue();
        tick();
        assertThat(service.metrics(world).submits.sum()).as("checked on the second tick").isEqualTo(1);
        runAll();
        assertThat(isSoil(dirt)).isFalse();
        assertThat(fake.spawns).hasSize(1);
        assertThat(service.isIdle()).isTrue();
        notify(dirt); // now air: costs nothing
        assertThat(service.requests()).isEqualTo(1);
    }

    @Test
    void grassOnAPillarSlidesIntoTheDrawnOpenSideAsDirt() {
        Block grass = soil(GRASS, 0, 100, 0);
        grass.getRelative(0, -1, 0).setType(Material.STONE);
        draw[0] = 1; // open sides in TFC's order north, east, south, west: index 1 is east
        notify(grass);
        runAll();
        assertThat(isSoil(grass)).isFalse();
        assertThat(fake.spawns).singleElement().satisfies(s -> {
            assertThat(s.at()).isEqualTo(grass.getRelative(1, 0, 0));
            assertThat(s.state()).as("the recipe result").isEqualTo(DIRT);
        });
        assertThat(service.slides(world)).isEqualTo(1);
        assertThat(fake.destroyed).as("air in the way breaks nothing").isEmpty();
    }

    @Test
    void aTorchInTheWayIsBroken() {
        Block dirt = soil(DIRT, 0, 100, 0);
        dirt.getRelative(0, -1, 0).setType(Material.STONE);
        Block torch = dirt.getRelative(0, 0, -1); // north: open, with a drop below
        torch.setType(Material.TORCH);
        dirt.getRelative(1, 0, 0).setType(Material.STONE); // east: one supported side
        dirt.getRelative(0, -1, 1).setType(Material.STONE); // south: open, but no drop below
        dirt.getRelative(-1, -1, 0).setType(Material.STONE); // west: open, but no drop below
        notify(dirt);
        runAll();
        assertThat(fake.destroyed).containsExactly(torch);
        assertThat(fake.spawns).singleElement().satisfies(s -> assertThat(s.at()).isEqualTo(torch));
    }

    @Test
    void twoSupportedSidesOrASturdyBlockAboveHoldIt() {
        Block a = soil(DIRT, 0, 100, 0);
        a.getRelative(0, -1, 0).setType(Material.STONE);
        a.getRelative(0, 0, -1).setType(Material.STONE);
        a.getRelative(1, 0, 0).setType(Material.STONE);
        Block b = soil(DIRT, 8, 100, 0);
        b.getRelative(0, -1, 0).setType(Material.STONE);
        b.getRelative(0, 1, 0).setType(Material.STONE);
        Block c = soil(DIRT, 0, 100, 8);
        c.getRelative(0, -1, 0).setType(Material.STONE);
        c.getRelative(0, 0, -1).setType(Material.FARMLAND); // #supports_landslide counts though it is not sturdy
        c.getRelative(-1, 0, 0).setType(Material.FARMLAND);
        notify(a);
        notify(b);
        notify(c);
        runAll();
        assertThat(isSoil(a)).isTrue();
        assertThat(isSoil(b)).isTrue();
        assertThat(isSoil(c)).isTrue();
        assertThat(fake.spawns).isEmpty();
    }

    @Test
    void dirtOnBedrockOrOverWaterBehavesAsTfc() {
        Block onBedrock = soil(DIRT, 0, 100, 0);
        onBedrock.getRelative(0, -1, 0).setType(Material.BEDROCK);
        for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            onBedrock.getRelative(s[0], -1, s[1]).setType(Material.BEDROCK);
        }
        Block overWater = soil(DIRT, 8, 100, 0);
        overWater.getRelative(0, -1, 0).setType(Material.WATER);
        notify(onBedrock);
        notify(overWater);
        runAll();
        assertThat(isSoil(onBedrock)).as("no open side with a drop").isTrue();
        assertThat(isSoil(overWater)).as("water is an open block").isFalse();
        assertThat(fake.spawns).singleElement().satisfies(s -> assertThat(s.at()).isEqualTo(overWater));
    }

    @Test
    void blocksThatCannotLandslideCostNothing() {
        Block stone = world.getBlockAt(0, 100, 0);
        stone.setType(Material.STONE);
        notify(stone);
        Block air = world.getBlockAt(0, 101, 0);
        notify(air);
        assertThat(tasks).isEmpty();
        assertThat(delayed).isEmpty();
        assertThat(service.requests()).isZero();
    }

    @Test
    void placementsAreCheckedInEveryGameModeButNotWhenCancelled() {
        player.setGameMode(GameMode.CREATIVE);
        Block creative = soil(DIRT, 0, 100, 0);
        place(creative, false);
        Block cancelled = soil(DIRT, 8, 100, 0);
        place(cancelled, true);
        runAll();
        assertThat(isSoil(creative)).as("TFC exempts no game mode").isFalse();
        assertThat(isSoil(cancelled)).isTrue();
        assertThat(service.requests()).isEqualTo(1);
    }

    @Test
    void aPlacedBlockThatIsNotSoilAnyMoreWhenTheDelayEndsStays() {
        Block block = world.getBlockAt(0, 100, 0);
        block.setType(Material.STONE);
        place(block, false);
        runAll();
        assertThat(block.getType()).isEqualTo(Material.STONE);
        assertThat(fake.spawns).isEmpty();
        assertThat(service.metrics(world).noops.sum()).isEqualTo(1);
    }

    @Test
    void aBlockChangedBetweenDecisionAndCommitIsLeftAlone() {
        Block dirt = soil(DIRT, 0, 100, 0);
        notify(dirt);
        tick();
        tick(); // snapshot and decision (inline workers); the commit is queued for the next tick
        assertThat(isSoil(dirt)).isTrue();
        dirt.getRelative(0, -1, 0).setType(Material.STONE);
        custom.remove(dirt);
        dirt.setType(Material.GLASS);
        runAll();
        assertThat(dirt.getType()).isEqualTo(Material.GLASS);
        assertThat(fake.spawns).isEmpty();
        assertThat(service.falls(world)).isZero();
    }

    @Test
    void unloadedNeighboursReadAsSolidSoNothingSlidesIntoThem() {
        BlockPos c = new BlockPos(0, 100, 0);
        SlideState.Cell dirt = new SlideState.Cell(DIRT, EnumSet.allOf(Direction.class), 0, false, false, DIRT);
        SlideState.Cell stone = new SlideState.Cell("minecraft:stone", EnumSet.allOf(Direction.class), 2, false, false,
                null);
        Map<BlockPos, SlideState> states = new HashMap<>();
        states.put(c, dirt);
        states.put(c.offset(Direction.DOWN), stone);
        states.put(c.offset(Direction.UP), SlideState.Cell.AIR);
        // north and south are open but have no drop; east is open with a drop; west is in an unloaded chunk
        states.put(c.offset(Direction.NORTH), SlideState.Cell.AIR);
        states.put(c.offset(Direction.NORTH).offset(Direction.DOWN), stone);
        states.put(c.offset(Direction.SOUTH), SlideState.Cell.AIR);
        states.put(c.offset(Direction.SOUTH).offset(Direction.DOWN), stone);
        states.put(c.offset(Direction.EAST), SlideState.Cell.AIR);
        states.put(c.offset(Direction.EAST).offset(Direction.DOWN), SlideState.Cell.AIR);
        List<BlockChange<SlideState>> changes = LandslideService.decide(new NeighbourSnapshot<>(c, states, 0L),
                new SupportTable(List.of()), bound -> {
                    assertThat(bound).as("one open side").isEqualTo(1);
                    return 0;
                });
        assertThat(changes).singleElement().satisfies(ch -> {
            assertThat(ch.expectedOld()).isEqualTo(dirt);
            assertThat(ch.next()).isEqualTo(new SlideState.Move(Direction.EAST, DIRT));
        });
        states.remove(c.offset(Direction.EAST).offset(Direction.DOWN)); // the drop is unloaded now
        assertThat(LandslideService.decide(new NeighbourSnapshot<>(c, states, 0L), new SupportTable(List.of()),
                bound -> 0)).isEmpty();
    }

    @Test
    void wiredThroughTheModuleServiceFile() throws Exception {
        String services = new String(LandslideModule.class.getClassLoader()
                .getResourceAsStream("META-INF/services/dev.tfp.paper.module.FeatureModule").readAllBytes(),
                StandardCharsets.UTF_8);
        List<String> lines = services.lines().toList();
        assertThat(lines).contains(LandslideModule.class.getName());
        int at = lines.indexOf(LandslideModule.class.getName());
        assertThat(at).isGreaterThan(lines.indexOf("dev.tfp.paper.falling.FallingModule"))
                .isGreaterThan(lines.indexOf("dev.tfp.paper.recipe.RecipeModule"))
                .isGreaterThan(lines.indexOf("dev.tfp.paper.loot.LootModule"))
                .isGreaterThan(lines.indexOf("dev.tfp.paper.tags.TagsModule"));
        assertThat(lines.indexOf("dev.tfp.paper.falling.FallingModule")).isPositive();
        assertThat(new LandslideModule().id()).isEqualTo("landslide");
    }

    /** Region tasks join the test's tick queue; delayed ones count down; CPU work runs inline. */
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
            Delayed d = new Delayed(Math.max(1, delayTicks), task);
            delayed.add(d);
            return new Scheduler.TaskHandle() {
                @Override
                public void cancel() {
                    delayed.remove(d);
                }

                @Override
                public boolean isCancelled() {
                    return !delayed.contains(d);
                }
            };
        }

        @Override
        public Scheduler.TaskHandle regionRepeating(World w, int chunkX, int chunkZ, long initialDelayTicks,
                long periodTicks, Runnable task) {
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
        public Scheduler.TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks,
                Runnable task, Runnable retired) {
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
