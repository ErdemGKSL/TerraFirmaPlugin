/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fruittree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.FruitTreeRules;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import java.util.SplittableRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FARM-08 on MockBukkit with fakes of the CraftEngine adapter and the world effects: sapling, growing branch, leaves, picking. */
class FruitTreeMockBukkitTest extends MockServerTest {
    static final long START = 40_000_000L;
    /** Calendar ticks in the middle of May of year 3 (8 days a month): the cherry is flowering to fruiting. */
    static final long MAY = 3L * 12 * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8 + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8
        + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
    static final long GROWTH = FruitTreeRules.FRUIT_BRANCH_GROWTH_TICKS;
    static final StackCodec NO_STACKS = new StackCodec() {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return ItemBlob.EMPTY;
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return ItemStack.empty();
        }
    };

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

    final class Effects implements FruitTreeEffects {
        BerryEffects.Environment env = new BerryEffects.Environment(50, 5f, true);
        RandomGenerator random = new SplittableRandom(7);
        int removed;
        int deadBushes;
        final java.util.List<ItemStack> given = new java.util.ArrayList<>();

        @Override
        public BerryEffects.Environment environment(Block stem) {
            return env;
        }

        @Override
        public Optional<BlockStateRef> blockAt(Block block) {
            return blocks.stateAt(block);
        }

        @Override
        public boolean isAir(Block block) {
            return block.getType().isAir() && blocks.stateAt(block).isEmpty();
        }

        @Override
        public boolean plantable(Block block) {
            return block.getY() == 63;
        }

        @Override
        public boolean inBuildHeight(Block block) {
            return block.getY() < 100;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, FruitTreeState state) {
            place(block, wanted, state);
        }

        @Override
        public void place(Block target, BlockStateRef ref, FruitTreeState state) {
            blocks.place(target, ref, true);
            if (state == null) {
                store.data.remove(FakeCustomBlocks.at(target));
            } else {
                store(target, state);
            }
        }

        @Override
        public void store(Block block, FruitTreeState state) {
            store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(state, 0).withLastUpdateTick(clock.get())));
        }

        @Override
        public void replaceWithDeadBush(Block block) {
            deadBushes++;
            blocks.remove(block);
        }

        @Override
        public void remove(Block block) {
            removed++;
            blocks.remove(block);
        }

        @Override
        public void track(Block block) {
        }

        @Override
        public Optional<ItemStack> product(String itemId) {
            return Optional.of(new ItemStack(Material.SWEET_BERRIES));
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void pickSound(Block block) {
        }

        @Override
        public RandomGenerator random() {
            return random;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private DeviceService service;
    private DeviceKind<FruitTreeState> kind;
    private World world;
    private Block origin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        blocks.known(FruitTreeDevice.allIdsForTest());
        world = server.addSimpleWorld("fruit");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        for (DeviceKind<FruitTreeState> k : FruitTreeDevice.kinds(new FruitTreeDevice.Services(effects,
            () -> new ClockSnapshot(1, clock.get(), MAY + (long) (BerryClock.CALENDAR_TICK_RATE * (clock.get() - START)), 8, true),
            BerryRules.Settings.DEFAULT))) {
            kind = service.register(k);
        }
        origin = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private FruitTreeState stateAt(long updated) {
        return new FruitTreeState(0, 64, 0, updated, BerryState.NEVER_PICKED, 50, 5f, true);
    }

    private void put(Block block, BlockStateRef ref, FruitTreeState s) {
        blocks.place(block, ref, true);
        store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(s, 0).withLastUpdateTick(clock.get())));
    }

    private FruitTreeState stored(Block block) {
        return kind.codec().decode(store.read(block).orElseThrow()).state();
    }

    private static final boolean[] NO_FLAGS = new boolean[6];

    private String idAt(Block b) {
        return blocks.stateAt(b).map(BlockStateRef::id).orElse("");
    }

    private long count(String suffix) {
        return blocks.placed().entrySet().stream().filter(e -> e.getKey().world().equals("fruit"))
            .filter(e -> e.getValue().id().endsWith(suffix)).count();
    }

    @Test
    void aSaplingGrowsIntoAGrowingBranchCarryingTheExtraTicks() {
        put(origin, FruitTreeDevice.sapling(FruitTree.CHERRY, 2), stateAt(START - 300_000));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).isEqualTo(FruitTreeDevice.blockId(FruitTree.CHERRY, FruitTreeDevice.Part.GROWING));
        assertThat(blocks.stateAt(origin).get().properties()).containsEntry("saplings", "2").containsEntry("stage", "0");
        long carried = 300_000 - FruitTreeRules.ticksToGrow(FruitTree.CHERRY, 1.0);
        assertThat(stored(origin).lastUpdate()).isEqualTo(START - carried);
    }

    @Test
    void aYoungSaplingWaits() {
        put(origin, FruitTreeDevice.sapling(FruitTree.CHERRY, 2), stateAt(START - 1000));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).endsWith("_sapling");
    }

    @Test
    void aSaplingOutOfClimateWithersWhenItWouldGrow() {
        effects.env = new BerryEffects.Environment(0, 40f, true);
        put(origin, FruitTreeDevice.sapling(FruitTree.CHERRY, 1), stateAt(START - 300_000));
        // the sapling needs a plantable block below, which the fake has at y = 63
        service.neighbourChanged(origin);
        assertThat(effects.deadBushes).isEqualTo(1);
    }

    @Test
    void aGrowingBranchWithBankedCyclesBuildsATreeWithLeaves() {
        put(origin, FruitTreeDevice.growing(FruitTree.CHERRY, 0, 3, false, NO_FLAGS), stateAt(START - 8 * GROWTH));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).isEqualTo(FruitTreeDevice.blockId(FruitTree.CHERRY, FruitTreeDevice.Part.BRANCH));
        assertThat(count("cherry_branch") + count("cherry_growing_branch")).isGreaterThanOrEqualTo(2);
        assertThat(count("cherry_leaves")).isGreaterThan(0);
        // every leaf and growing branch carries the tree's stem
        blocks.placed().forEach((at, ref) -> {
            if (at.world().equals("fruit") && (ref.id().endsWith("_leaves") || ref.id().endsWith("_growing_branch"))) {
                Block b = world.getBlockAt(at.pos().x(), at.pos().y(), at.pos().z());
                assertThat(stored(b).stemY()).isEqualTo(64);
            }
        });
    }

    @Test
    void oneOwedCycleGrowsOneStepAndLeavesTheChildWaiting() {
        put(origin, FruitTreeDevice.growing(FruitTree.CHERRY, 0, 2, false, NO_FLAGS), stateAt(START - GROWTH - 10));
        service.neighbourChanged(origin);
        Block above = origin.getRelative(0, 1, 0);
        assertThat(idAt(above)).endsWith("_growing_branch");
        assertThat(stored(above).lastUpdate()).isEqualTo(START); // no cycle left to bank
        assertThat(idAt(origin)).endsWith("_branch").doesNotEndWith("growing_branch");
    }

    @Test
    void outOfClimateAGrowingBranchResetsItsCounterUnlessNatural() {
        effects.env = new BerryEffects.Environment(0, 40f, true);
        put(origin, FruitTreeDevice.growing(FruitTree.CHERRY, 0, 3, false, NO_FLAGS), stateAt(START - 8 * GROWTH));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).endsWith("_growing_branch");
        assertThat(stored(origin).lastUpdate()).isEqualTo(START);

        Block natural = world.getBlockAt(10, 64, 0);
        put(natural, FruitTreeDevice.growing(FruitTree.CHERRY, 0, 3, true, NO_FLAGS), stateAt(START - 8 * GROWTH));
        service.neighbourChanged(natural);
        assertThat(idAt(natural)).endsWith("_branch").doesNotEndWith("growing_branch");
    }

    @Test
    void leavesFollowTheMonthAndAreAPickedWhenFruiting() {
        // June: the cherry is fruiting
        clock.set(START + (long) ((1L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY) / BerryClock.CALENDAR_TICK_RATE));
        Block branch = origin.getRelative(0, 1, 0);
        put(branch, FruitTreeDevice.branch(FruitTree.CHERRY, 0, NO_FLAGS), stateAt(0)); // a static branch next to the leaves
        store.data.remove(FakeCustomBlocks.at(branch));
        put(origin, FruitTreeDevice.leaves(FruitTree.CHERRY, Lifecycle.HEALTHY, false), stateAt(clock.get() - 1));
        service.neighbourChanged(origin);
        assertThat(blocks.stateAt(origin).get().properties()).containsEntry("lifecycle", "fruiting");
        assertThat(service.use(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR))).isTrue();
        assertThat(effects.given).hasSize(1);
        assertThat(blocks.stateAt(origin).get().properties()).containsEntry("lifecycle", "healthy");
        assertThat(stored(origin).lastPicked()).isEqualTo(clock.get());
    }

    @Test
    void persistentLeavesNeverChangeAndLooseLeavesBreak() {
        clock.set(START + (long) ((1L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY) / BerryClock.CALENDAR_TICK_RATE));
        put(origin, FruitTreeDevice.leaves(FruitTree.CHERRY, Lifecycle.HEALTHY, true), stateAt(clock.get() - 1));
        service.neighbourChanged(origin);
        assertThat(blocks.stateAt(origin).get().properties()).containsEntry("lifecycle", "healthy");
        assertThat(effects.removed).isZero();

        Block loose = world.getBlockAt(10, 64, 0);
        put(loose, FruitTreeDevice.leaves(FruitTree.CHERRY, Lifecycle.HEALTHY, false), stateAt(clock.get()));
        service.neighbourChanged(loose);
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void aSaplingWithoutSupportBreaks() {
        Block high = world.getBlockAt(5, 80, 5);
        put(high, FruitTreeDevice.sapling(FruitTree.CHERRY, 1), stateAt(START));
        service.neighbourChanged(high);
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void placingASaplingOnTheGroundSetsItsOwnStem() {
        blocks.place(origin, FruitTreeDevice.sapling(FruitTree.PEACH, 1), true);
        store.data.put(FakeCustomBlocks.at(origin), new DataMap());
        service.placed(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
        FruitTreeState s = stored(origin);
        assertThat(s.stemX()).isZero();
        assertThat(s.stemY()).isEqualTo(64);
        assertThat(s.lastUpdate()).isEqualTo(START);
    }

    @Test
    void parsingMapsBlockIdsToFamilies() {
        assertThat(FruitTreeDevice.parse(BlockStateRef.of("tfp:plant/green_apple_growing_branch")).get().part())
            .isEqualTo(FruitTreeDevice.Part.GROWING);
        assertThat(FruitTreeDevice.parse(BlockStateRef.of("tfp:plant/red_apple_leaves")).get().tree()).isEqualTo(FruitTree.RED_APPLE);
        assertThat(FruitTreeDevice.parse(BlockStateRef.of("tfp:plant/cherry_branch")).get().part()).isEqualTo(FruitTreeDevice.Part.BRANCH);
        assertThat(FruitTreeDevice.parse(BlockStateRef.of("tfp:plant/rose"))).isEmpty();
        assertThat(FruitTreeDevice.parse(BlockStateRef.of("tfp:plant/potted/cherry_sapling"))).isEmpty();
        assertThat(FruitTreeDevice.productId(FruitTree.GREEN_APPLE)).isEqualTo("tfp:food/green_apple");
    }
}
