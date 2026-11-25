/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fruittree;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.banana.BananaDevice;
import dev.tfp.paper.banana.BananaEffects;
import dev.tfp.paper.banana.GenBananas;
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
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FARM-27 on MockBukkit with fakes of the CraftEngine adapter: the committers write natural fruit trees and bananas. */
class GenCommitTest extends MockServerTest {
    static final long START = 40_000_000L;
    /** Calendar ticks in the middle of May of year 3 (8 days a month). */
    static final long MAY = 3L * 12 * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8 + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY * 8
        + 4L * CalendarTicks.CALENDAR_TICKS_IN_DAY;
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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final MemoryStore store = new MemoryStore();
    private final AtomicLong clock = new AtomicLong(START);
    private final RandomGenerator random = new SplittableRandom(7);
    private DeviceService service;
    private DeviceKind<FruitTreeState> kind;
    private World world;

    /** Block placement of a custom block creates its (fresh) device record, as CraftEngine's block entity does. */
    private void place(Block target, BlockStateRef ref, FruitTreeState state) {
        blocks.place(target, ref, true);
        FruitTreeState s = state == null ? new FruitTreeState(0, 0, 0, clock.get(), 0L, 50, 5f, true) : state;
        store.data.put(FakeCustomBlocks.at(target), kind.codec().encode(DeviceRecord.fresh(s, 0).withLastUpdateTick(clock.get())));
    }

    final class TreeFx implements FruitTreeEffects {
        @Override
        public BerryEffects.Environment environment(Block stem) {
            return new BerryEffects.Environment(50, 5f, true);
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
            GenCommitTest.this.place(target, ref, state);
        }

        @Override
        public void store(Block block, FruitTreeState state) {
            store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(state, 0).withLastUpdateTick(clock.get())));
        }

        @Override
        public void replaceWithDeadBush(Block block) {
            blocks.remove(block);
        }

        @Override
        public void remove(Block block) {
            blocks.remove(block);
        }

        @Override
        public void track(Block block) {
        }

        @Override
        public Optional<ItemStack> product(String itemId) {
            return Optional.empty();
        }

        @Override
        public void give(Player player, ItemStack stack) {
        }

        @Override
        public void pickSound(Block block) {
        }

        @Override
        public RandomGenerator random() {
            return random;
        }
    }

    final class BananaFx implements BananaEffects {
        @Override
        public BerryEffects.Environment environment(Block stem, Block root) {
            return new BerryEffects.Environment(60, 25f, true);
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
        public boolean canSeeSky(Block block) {
            return true;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, FruitTreeState state) {
            place(block, wanted, state);
        }

        @Override
        public void place(Block target, BlockStateRef ref, FruitTreeState state) {
            GenCommitTest.this.place(target, ref, state);
        }

        @Override
        public void store(Block block, FruitTreeState state) {
            store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(state, 0).withLastUpdateTick(clock.get())));
        }

        @Override
        public void replaceWithDeadBush(Block block) {
            blocks.remove(block);
        }

        @Override
        public void remove(Block block) {
            blocks.remove(block);
        }

        @Override
        public void track(Block block) {
        }

        @Override
        public Optional<ItemStack> product(String itemId, int count) {
            return Optional.empty();
        }

        @Override
        public void give(Player player, ItemStack stack) {
        }

        @Override
        public void pickSound(Block block) {
        }

        @Override
        public RandomGenerator random() {
            return random;
        }
    }

    private GenTrees trees;
    private GenBananas bananas;

    @BeforeEach
    void setUp() {
        blocks.known(FruitTreeDevice.allIdsForTest());
        blocks.known(BananaDevice.allIdsForTest());
        world = server.addSimpleWorld("gen");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        Supplier<ClockSnapshot> snapshot = () -> new ClockSnapshot(1, clock.get(), MAY + (long) (BerryClock.CALENDAR_TICK_RATE * (clock.get() - START)), 8, true);
        FruitTreeDevice.Services treeServices = new FruitTreeDevice.Services(new TreeFx(), snapshot, BerryRules.Settings.DEFAULT);
        for (DeviceKind<FruitTreeState> k : FruitTreeDevice.kinds(treeServices)) {
            kind = service.register(k);
        }
        BananaDevice.Services bananaServices = new BananaDevice.Services(new BananaFx(), snapshot, BerryRules.Settings.DEFAULT);
        for (DeviceKind<FruitTreeState> k : BananaDevice.kinds(bananaServices)) {
            service.register(k);
        }
        trees = new GenTrees(treeServices, service, blocks);
        bananas = new GenBananas(bananaServices, service, blocks);
    }

    private long count(String suffix) {
        return blocks.placed().entrySet().stream().filter(e -> e.getKey().world().equals("gen"))
            .filter(e -> e.getValue().id().endsWith(suffix)).count();
    }

    private GenPlanner.Spot spot(GenFeature f, int... draws) {
        return new GenPlanner.Spot(f, 0, 64, 0, java.util.Arrays.stream(draws).boxed().toList());
    }

    @Test
    void aNaturalTreeGrowsAtOnceFromItsGrowingBranch() {
        assertThat(trees.commit(world, spot(GenPlannerTest.cherry(), 3))).isTrue();
        // the origin was a natural growing branch with 3 saplings; the 300-day head start grew it into branches and leaves
        assertThat(count("cherry_branch") + count("cherry_growing_branch")).isGreaterThanOrEqualTo(2);
        assertThat(count("cherry_leaves")).isGreaterThan(0);
        // every growing branch of the natural tree is natural, and each carries the origin as its stem
        blocks.placed().forEach((at, ref) -> {
            if (at.world().equals("gen") && ref.id().endsWith("_growing_branch")) {
                assertThat(ref.properties()).containsEntry("natural", "true");
            }
        });
    }

    @Test
    void aTreeNeedsPlantableGroundAndTenFreeCells() {
        // the fake's plantable ground is y = 63: an origin one higher has none
        assertThat(trees.commit(world, new GenPlanner.Spot(GenPlannerTest.cherry(), 0, 65, 0, List.of(3)))).isFalse();
        world.getBlockAt(0, 70, 0).setType(Material.STONE);
        assertThat(trees.commit(world, spot(GenPlannerTest.cherry(), 3))).isFalse();
        assertThat(count("cherry_branch") + count("cherry_growing_branch")).isZero();
    }

    @Test
    void aTreeDoesNotOverwriteACustomBlock() {
        blocks.place(world.getBlockAt(0, 68, 0), BlockStateRef.of(FruitTreeDevice.blockId(FruitTree.PLUM, FruitTreeDevice.Part.BRANCH)), true);
        assertThat(trees.commit(world, spot(GenPlannerTest.cherry(), 3))).isFalse();
    }

    @Test
    void aBananaStalkIsStackedByStageOnPlantableGround() {
        assertThat(bananas.commit(world, spot(GenPlannerTest.banana(), 3, 2, 3))).isTrue();
        assertThat(blocks.stateAt(world.getBlockAt(0, 64, 0)).orElseThrow().properties()).containsEntry("stage", "0");
        assertThat(blocks.stateAt(world.getBlockAt(0, 65, 0)).orElseThrow().properties()).containsEntry("stage", "0");
        assertThat(blocks.stateAt(world.getBlockAt(0, 66, 0)).orElseThrow().properties()).containsEntry("stage", "1");
        assertThat(blocks.stateAt(world.getBlockAt(0, 67, 0)).orElseThrow().properties()).containsEntry("stage", "2");
        assertThat(blocks.stateAt(world.getBlockAt(0, 68, 0))).isEmpty();
        assertThat(count("banana_plant")).isEqualTo(4);
    }

    @Test
    void aBananaNeedsPlantableGroundAndFreeCells() {
        assertThat(bananas.commit(world, new GenPlanner.Spot(GenPlannerTest.banana(), 0, 65, 0, List.of(2, 2, 2)))).isFalse();
        world.getBlockAt(0, 65, 0).setType(Material.STONE);
        assertThat(bananas.commit(world, spot(GenPlannerTest.banana(), 3, 2, 3))).isFalse();
        assertThat(count("banana_plant")).isZero();
    }
}
