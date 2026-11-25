/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.drying;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.drying.DryingRules;
import dev.tfp.core.drying.DryingState;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-POT-08 on MockBukkit with fakes of the CraftEngine adapter, a memory device store, a queued region scheduler and a
 * player-tick clock: a pile dries lazily after {@code mudBricksTicks} in clear weather (one evaluation after a calendar jump
 * included), rain resets the counter, a brick added resets it, a brick taken does not, the loot drops by count and dried state,
 * a creative break and the last brick taken drop nothing extra, and a pile without a sturdy base pops.
 *
 * <p>Stand-ins: clay ball = the wet brick item, brick = the dry mud brick, stone = a sturdy base.
 */
class DryingBricksMockBukkitTest extends MockServerTest {
    static final String SOIL_ID = "tfp:drying_bricks/alfisol";
    static final int T = DryingRules.DEFAULT_THRESHOLD_TICKS;

    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        @Override
        public Optional<DataMap> read(Block block) {
            DataMap m = data.get(FakeCustomBlocks.at(block));
            return m == null ? Optional.empty() : Optional.of(m.copy());
        }

        @Override
        public boolean write(Block block, DataMap map) {
            if (!data.containsKey(FakeCustomBlocks.at(block))) {
                return false;
            }
            data.put(FakeCustomBlocks.at(block), map.copy());
            return true;
        }
    }

    static final class TableCodec implements StackCodec {
        final List<ItemStack> table = new ArrayList<>();

        @Override
        public dev.tfp.core.device.ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return dev.tfp.core.device.ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return dev.tfp.core.device.ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(dev.tfp.core.device.ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

    /** The two items by material: wet bricks are clay balls, dry mud bricks are bricks. */
    static final class Items implements CustomItems {
        @Override
        public boolean exists(String id) {
            return id.equals(DryingBricks.itemId("alfisol", false)) || id.equals(DryingBricks.itemId("alfisol", true));
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            if (id.equals(DryingBricks.itemId("alfisol", false))) {
                return Optional.of(new ItemStack(Material.CLAY_BALL, amount));
            }
            return id.equals(DryingBricks.itemId("alfisol", true)) ? Optional.of(new ItemStack(Material.BRICK, amount))
                : Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null) {
                return Optional.empty();
            }
            return switch (stack.getType()) {
                case CLAY_BALL -> Optional.of(DryingBricks.itemId("alfisol", false));
                case BRICK -> Optional.of(DryingBricks.itemId("alfisol", true));
                default -> Optional.empty();
            };
        }
    }

    /** CraftEngine stand-in: removing a block runs its device removal. */
    final class Blocks implements CustomBlocks {
        final FakeCustomBlocks fake = new FakeCustomBlocks().known(SOIL_ID);

        @Override
        public boolean exists(String id) {
            return fake.exists(id);
        }

        @Override
        public Optional<BlockStateRef> stateAt(Block block) {
            return fake.stateAt(block);
        }

        @Override
        public boolean place(Block block, BlockStateRef state, boolean applyPhysics) {
            if (!fake.exists(state.id())) {
                return false;
            }
            fake.place(block, state, applyPhysics);
            block.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
            store.data.put(FakeCustomBlocks.at(block), devices.created(state.id()).orElseThrow());
            return true;
        }

        @Override
        public boolean remove(Block block) {
            Optional<BlockStateRef> was = fake.stateAt(block);
            if (was.isEmpty() || !fake.remove(block)) {
                return false;
            }
            block.setType(Material.AIR);
            DataMap data = store.data.remove(FakeCustomBlocks.at(block));
            devices.removed(block.getWorld(), block.getX(), block.getY(), block.getZ(), was.get().id(), data);
            return true;
        }

        @Override
        public void onChunkLoad(Consumer<Chunk> hook) {
        }

        @Override
        public void onChunkUnload(Consumer<Chunk> hook) {
        }
    }

    /** Region tasks wait in a queue. */
    static final class QueueScheduler implements Scheduler {
        final List<Runnable> queue = new ArrayList<>();

        void drain() {
            for (int i = 0; i < 100 && !queue.isEmpty(); i++) {
                List<Runnable> now = new ArrayList<>(queue);
                queue.clear();
                now.forEach(Runnable::run);
            }
        }

        @Override
        public void region(Location location, Runnable task) {
            queue.add(task);
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            queue.add(task);
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Executor ioExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    final MemoryStore store = new MemoryStore();
    final TableCodec stacks = new TableCodec();
    final QueueScheduler scheduler = new QueueScheduler();
    final AtomicLong now = new AtomicLong(10_000);
    final AtomicBoolean raining = new AtomicBoolean();
    final DryingClock clock = new DryingClock();
    DeviceService devices;
    Blocks blocks;
    DryingBricks bricks;
    DryingListener listener;
    World world;
    Block pile;
    PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("drying");
        blocks = new Blocks();
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, now::get, stacks, new Items(),
            Logger.getLogger("test"));
        bricks = new DryingBricks(new DryingBricks.Services(blocks, new Items(), devices,
            (block, state) -> blocks.fake.place(block, state, false), b -> b.getRelative(BlockFace.DOWN).getType().isSolid(),
            b -> raining.get(), now::get, () -> T, clock, scheduler));
        bricks.kind(devices.register(DryingDevice.kind(bricks)));
        listener = new DryingListener(bricks, blocks, scheduler);
        pile = world.getBlockAt(10, 64, 4);
        pile.getRelative(BlockFace.DOWN).setType(Material.STONE);
        player = server.addPlayer();
        player.teleport(new Location(world, 10.5, 65, 6.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** A player placed one brick: the block, then the device's placement callback. */
    void place(int count) {
        assertThat(blocks.place(pile, DryingBricks.state("alfisol", count, false), false)).isTrue();
        devices.placed(pile, player, EquipmentSlot.HAND, new ItemStack(Material.CLAY_BALL));
        // a pile of more bricks (the clicks that added them are tested separately): the record's count follows the block's
        devices.edit(pile, bricks.kind(), (record, t) -> record.withState(record.state().withCount(count)));
    }

    DryingState state() {
        return bricks.kind().codec().decode(store.read(pile).orElseThrow()).state();
    }

    BlockStateRef ref() {
        return blocks.stateAt(pile).orElseThrow();
    }

    void hold(Material type, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, type == Material.AIR ? null : new ItemStack(type, amount));
    }

    PlayerInteractEvent click() {
        ItemStack held = player.getInventory().getItem(EquipmentSlot.HAND);
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            StackCodec.isEmpty(held) ? null : held, pile, BlockFace.UP, EquipmentSlot.HAND, new Vector(0.5, 1, 0.5));
        listener.onInteract(e);
        return e;
    }

    static boolean denied(PlayerInteractEvent e) {
        return e.useInteractedBlock() == Event.Result.DENY && e.useItemInHand() == Event.Result.DENY;
    }

    /** The clock hands the due piles to their region threads, as the module's task does. */
    void tick() {
        DryingModule.period(clock, bricks, scheduler, now.get());
        scheduler.drain();
    }

    List<ItemStack> dropped() {
        return world.getEntitiesByClass(Item.class).stream().map(Item::getItemStack).toList();
    }

    @Test
    void placingResetsTheCounterAndListsThePileOnTheClock() {
        place(1);
        assertThat(state().lastUpdateTick()).isEqualTo(10_000);
        assertThat(state().count()).isEqualTo(1);
        assertThat(clock.tracked(pile)).isTrue();
        assertThat(DryingBricks.dried(ref())).isFalse();
    }

    @Test
    void dryInClearWeatherAfterTheThreshold() {
        place(2);
        now.set(10_000 + T);
        tick();
        assertThat(DryingBricks.dried(ref())).isFalse();
        now.set(10_000 + T + 1);
        tick();
        assertThat(DryingBricks.dried(ref())).isTrue();
        assertThat(DryingBricks.count(ref())).isEqualTo(2);
        assertThat(state().driedTick()).isEqualTo(10_000 + T + 1);
        assertThat(clock.tracked(pile)).isFalse();
    }

    @Test
    void aTwoDayJumpDriesInOneEvaluation() {
        place(1);
        now.addAndGet(48_000);
        bricks.evaluate(pile);
        assertThat(DryingBricks.dried(ref())).isTrue();
    }

    @Test
    void theClockRechecksOftenerThanTheDryTick() {
        place(1);
        // listed for the re-check (rain sampling), not for the far dry tick
        now.addAndGet(DryingBricks.RECHECK_TICKS);
        assertThat(clock.due(now.get())).hasSize(1);
    }

    @Test
    void rainAtTheCheckResetsTheCounter() {
        place(1);
        now.set(10_000 + T + 1);
        raining.set(true);
        bricks.evaluate(pile);
        assertThat(DryingBricks.dried(ref())).isFalse();
        assertThat(state().lastUpdateTick()).isEqualTo(10_000 + T + 1);
        raining.set(false);
        now.addAndGet(T);
        bricks.evaluate(pile);
        assertThat(DryingBricks.dried(ref())).isFalse();
        now.addAndGet(1);
        bricks.evaluate(pile);
        assertThat(DryingBricks.dried(ref())).isTrue();
    }

    @Test
    void addingABrickCountsUpResetsTheCounterAndStopsAtFour() {
        place(1);
        now.set(20_000);
        hold(Material.CLAY_BALL, 8);
        assertThat(denied(click())).isTrue();
        assertThat(DryingBricks.count(ref())).isEqualTo(2);
        assertThat(state().lastUpdateTick()).isEqualTo(20_000);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(7);
        assertThat(denied(click())).isTrue();
        assertThat(denied(click())).isTrue();
        assertThat(DryingBricks.count(ref())).isEqualTo(4);
        assertThat(denied(click())).isFalse();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(5);
    }

    @Test
    void creativeKeepsTheItem() {
        place(1);
        player.setGameMode(GameMode.CREATIVE);
        hold(Material.CLAY_BALL, 1);
        assertThat(denied(click())).isTrue();
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
    }

    @Test
    void aDryPileTakesNoBricksAndAnotherItemIsNotUsed() {
        place(1);
        now.addAndGet(T + 1);
        bricks.evaluate(pile);
        hold(Material.CLAY_BALL, 4);
        assertThat(denied(click())).isFalse();
        place(1);
        hold(Material.STICK, 1);
        assertThat(denied(click())).isFalse();
    }

    @Test
    void sneakingWithAnEmptyHandTakesOneBrickWithoutResettingTheCounter() {
        place(3);
        now.set(20_000);
        player.setSneaking(true);
        hold(Material.AIR, 0);
        assertThat(denied(click())).isTrue();
        assertThat(DryingBricks.count(ref())).isEqualTo(2);
        assertThat(state().lastUpdateTick()).isEqualTo(10_000);
        assertThat(player.getInventory().contains(Material.CLAY_BALL)).isTrue();
    }

    @Test
    void aDryPileGivesTheMudBrickAndTheLastBrickRemovesThePileWithoutAnExtraDrop() {
        place(2);
        now.addAndGet(T + 1);
        bricks.evaluate(pile);
        player.setSneaking(true);
        hold(Material.AIR, 0);
        click();
        player.getInventory().setItem(10, player.getInventory().getItem(EquipmentSlot.HAND)); // the first brick landed in the hand
        player.getInventory().setItem(EquipmentSlot.HAND, null);
        click();
        assertThat(blocks.stateAt(pile)).isEmpty();
        assertThat(player.getInventory().all(Material.BRICK).values().stream().mapToInt(ItemStack::getAmount).sum()).isEqualTo(2);
        assertThat(dropped()).isEmpty();
    }

    @Test
    void breakingDropsTheBricksByCountAndDriedState() {
        place(3);
        blocks.remove(pile);
        assertThat(dropped()).hasSize(1);
        assertThat(dropped().getFirst().getType()).isEqualTo(Material.CLAY_BALL);
        assertThat(dropped().getFirst().getAmount()).isEqualTo(3);
        world.getEntitiesByClass(Item.class).forEach(Entity::remove);
        place(2);
        now.addAndGet(T + 1);
        bricks.evaluate(pile);
        blocks.remove(pile);
        assertThat(dropped()).hasSize(1);
        assertThat(dropped().getFirst().getType()).isEqualTo(Material.BRICK);
        assertThat(dropped().getFirst().getAmount()).isEqualTo(2);
    }

    @Test
    void changingTheStateOfAPileDropsNothing() {
        place(2);
        bricks.removed(pile, SOIL_ID, new DeviceRecord<>(DryingState.reset(1, false).added(2, false), List.of(), 0));
        assertThat(dropped()).isEmpty(); // the block is still a pile
    }

    @Test
    void aCreativeBreakDropsNothing() {
        place(2);
        player.setGameMode(GameMode.CREATIVE);
        listener.onBreak(new BlockBreakEvent(pile, player));
        blocks.remove(pile);
        assertThat(dropped()).isEmpty();
    }

    @Test
    void aPileWithoutASturdyBasePops() {
        place(2);
        pile.getRelative(BlockFace.DOWN).setType(Material.AIR);
        devices.neighbourChanged(pile);
        scheduler.drain();
        assertThat(blocks.stateAt(pile)).isEmpty();
        assertThat(dropped()).hasSize(1);
        assertThat(dropped().getFirst().getAmount()).isEqualTo(2);
    }

    @Test
    void disabledNeverDriesAndIsNotListed() {
        DryingBricks off = new DryingBricks(new DryingBricks.Services(blocks, new Items(), devices,
            (block, state) -> blocks.fake.place(block, state, false), b -> true, b -> false, now::get, () -> DryingRules.DISABLED, clock,
            scheduler));
        off.kind(bricks.kind());
        place(1);
        now.addAndGet(10_000_000);
        off.evaluate(pile);
        assertThat(DryingBricks.dried(ref())).isFalse();
        assertThat(clock.tracked(pile)).isFalse();
    }

    @Test
    void theThresholdConfigAcceptsMinusOneAndFallsBackBelowIt() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        Logger log = Logger.getLogger("test");
        assertThat(DryingModule.threshold(yaml, log)).isEqualTo(28_800);
        yaml.set(DryingModule.TICKS_KEY, -1);
        assertThat(DryingModule.threshold(yaml, log)).isEqualTo(-1);
        yaml.set(DryingModule.TICKS_KEY, 1200);
        assertThat(DryingModule.threshold(yaml, log)).isEqualTo(1200);
        yaml.set(DryingModule.TICKS_KEY, -5);
        assertThat(DryingModule.threshold(yaml, log)).isEqualTo(28_800);
    }

    @Test
    void stateHelpersReadTheBlockState() {
        BlockStateRef ref = DryingBricks.state("podzol", 3, true);
        assertThat(ref.id()).isEqualTo("tfp:drying_bricks/podzol");
        assertThat(DryingBricks.count(ref)).isEqualTo(3);
        assertThat(DryingBricks.dried(ref)).isTrue();
        assertThat(DryingBricks.soil(ref.id())).isEqualTo("podzol");
        assertThat(DryingBricks.itemId("podzol", true)).isEqualTo("tfp:mud_brick/podzol");
        assertThat(DryingBricks.itemId("podzol", false)).isEqualTo("tfp:drying_bricks/podzol");
    }
}
