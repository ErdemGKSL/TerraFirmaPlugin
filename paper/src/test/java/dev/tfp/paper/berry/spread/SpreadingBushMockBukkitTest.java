/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.berry.spread;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.BerryBush;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.berry.SpreadingSpecies;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
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

/** PAR-FARM-07 on MockBukkit with fakes of the CraftEngine adapter and the world effects: spreading, canes, withering, picking, cranberry. */
class SpreadingBushMockBukkitTest extends MockServerTest {
    static final long START = 20_000_000L;
    /** Calendar ticks in the middle of May of year 3 (8 days a month): the raspberry is healthy. */
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

    /** A random source with fixed draws: side index 1 (east) and a false stop flag. */
    static final class FixedRandom implements RandomGenerator {
        int side = 1;
        boolean flag;

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return bound == 4 ? side : 0;
        }

        @Override
        public boolean nextBoolean() {
            return flag;
        }
    }

    final class Effects implements SpreadEffects {
        BerryEffects.Environment env = new BerryEffects.Environment(50, 5f, true);
        boolean groundOk = true;
        boolean wet = true;
        final FixedRandom random = new FixedRandom();
        final List<BlockStateRef> shown = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        int removed;

        @Override
        public BerryEffects.Environment environment(Block block) {
            return env;
        }

        @Override
        public Optional<BlockStateRef> blockAt(Block block) {
            return blocks.stateAt(block);
        }

        @Override
        public boolean isEmpty(Block block) {
            return block.getType().isAir() && blocks.stateAt(block).isEmpty();
        }

        @Override
        public boolean mayPlaceOn(Block below) {
            return groundOk;
        }

        @Override
        public boolean canPlantCranberry(Block target) {
            return wet && isEmpty(target);
        }

        @Override
        public boolean waterlogged(Block block) {
            return wet;
        }

        @Override
        public int countSame(Block center, String blockId, int radius, int dyMin, int dyMax) {
            int n = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = dyMin; dy <= dyMax; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (blocks.stateAt(center.getRelative(dx, dy, dz)).map(s -> s.id().equals(blockId)).orElse(false)) {
                            n++;
                        }
                    }
                }
            }
            return n;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, BerryState state) {
            shown.add(wanted);
            if (blocks.stateAt(block).map(s -> !s.id().equals(wanted.id())).orElse(false)) {
                place(block, wanted, state);
            }
        }

        @Override
        public void place(Block target, BlockStateRef ref, BerryState state) {
            blocks.place(target, ref, true);
            store(target, state);
        }

        @Override
        public void store(Block block, BerryState state) {
            store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(state, 0).withLastUpdateTick(clock.get())));
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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(SpreadingBushDevice.bushId(SpreadingSpecies.RASPBERRY),
        SpreadingBushDevice.caneId(SpreadingSpecies.RASPBERRY), SpreadingBushDevice.DEAD_ID, SpreadingBushDevice.DEAD_CANE_ID,
        SpreadingBushDevice.CRANBERRY_ID);
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private DeviceService service;
    private DeviceKind<BerryState> kind;
    private World world;
    private Block origin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("spread");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(SpreadingBushDevice.kind(new SpreadingBushDevice.Services(effects,
            () -> new ClockSnapshot(1, clock.get(), MAY + (long) (BerryClock.CALENDAR_TICK_RATE * (clock.get() - START)), 8, true),
            BerryRules.Settings.DEFAULT)));
        origin = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private static BerryState state(Lifecycle l, int stage, int growths, long updated) {
        return new BerryState(false, new BerryBush(l, stage, growths, updated, BerryState.NEVER_PICKED), 0L, 50, 5f, true);
    }

    private void put(Block block, BlockStateRef ref, BerryState s) {
        blocks.place(block, ref, true);
        store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(s, 0).withLastUpdateTick(clock.get())));
    }

    private BerryState stored(Block block) {
        return kind.codec().decode(store.read(block).orElseThrow()).state();
    }

    private static BlockStateRef bushRef(String lifecycle, int stage) {
        return BlockStateRef.of(SpreadingBushDevice.bushId(SpreadingSpecies.RASPBERRY)).with("lifecycle", lifecycle).with("stage", Integer.toString(stage));
    }

    private static BlockStateRef caneRef(String lifecycle, int stage, String facing) {
        return BlockStateRef.of(SpreadingBushDevice.caneId(SpreadingSpecies.RASPBERRY)).with("lifecycle", lifecycle)
            .with("stage", Integer.toString(stage)).with("facing", facing);
    }

    @Test
    void placementIsHealthyInSeasonAtStageZero() {
        blocks.place(origin, bushRef("healthy", 0), true);
        store.data.put(FakeCustomBlocks.at(origin), new DataMap());
        service.placed(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
        BerryState s = stored(origin);
        assertThat(s.bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(s.bush().stage()).isZero();
        assertThat(s.bush().growthsRemaining()).isEqualTo(BerryRules.GROWTHS_INITIAL);
    }

    @Test
    void aStageOneBushSpreadsACaneOnTheDrawnSideAndMatures() {
        put(origin, bushRef("healthy", 1), state(Lifecycle.HEALTHY, 1, 24, START - BerryRules.GROWTH_TICKS));
        service.neighbourChanged(origin);
        Block east = origin.getRelative(1, 0, 0);
        assertThat(blocks.stateAt(east)).isPresent();
        assertThat(blocks.stateAt(east).get().id()).isEqualTo(SpreadingBushDevice.caneId(SpreadingSpecies.RASPBERRY));
        assertThat(blocks.stateAt(east).get().properties()).containsEntry("facing", "east");
        assertThat(stored(origin).bush().stage()).isEqualTo(2); // stop flag false: the bush stops growing
        assertThat(stored(east).bush().growthsRemaining()).isEqualTo(23);
    }

    @Test
    void aBlockedSideGrowsUpwardsAndTheSourceMatures() {
        blocks.known("tfp:other");
        blocks.place(origin.getRelative(1, 0, 0), BlockStateRef.of("tfp:other"), true);
        put(origin, bushRef("healthy", 1), state(Lifecycle.HEALTHY, 1, 24, START - BerryRules.GROWTH_TICKS));
        service.neighbourChanged(origin);
        Block above = origin.getRelative(0, 1, 0);
        assertThat(blocks.stateAt(above).get().id()).isEqualTo(SpreadingBushDevice.bushId(SpreadingSpecies.RASPBERRY));
        assertThat(blocks.stateAt(above).get().properties()).containsEntry("stage", "1");
        assertThat(stored(origin).bush().stage()).isEqualTo(2);
    }

    @Test
    void aLongSpanSpreadsAcrossAFewBlocksAndStaysBounded() {
        put(origin, bushRef("healthy", 0), state(Lifecycle.HEALTHY, 0, 24, 0L));
        service.neighbourChanged(origin);
        long placed = blocks.placed().keySet().stream().filter(a -> a.world().equals("spread")).count();
        assertThat(placed).isBetween(2L, 40L);
        assertThat(stored(origin).bush().stage()).isGreaterThan(0);
    }

    @Test
    void aFullyGrownCaneBecomesABushBlock() {
        Block bush = origin;
        put(bush, bushRef("healthy", 2), state(Lifecycle.HEALTHY, 2, 24, START));
        Block cane = origin.getRelative(1, 0, 0);
        put(cane, caneRef("healthy", 2, "east"), state(Lifecycle.HEALTHY, 2, 20, START - BerryRules.GROWTH_TICKS));
        service.neighbourChanged(cane);
        assertThat(blocks.stateAt(cane).get().id()).isEqualTo(SpreadingBushDevice.bushId(SpreadingSpecies.RASPBERRY));
        assertThat(stored(cane).bush().stage()).isEqualTo(1);
    }

    @Test
    void aCaneWithoutABushBehindItBreaks() {
        Block cane = origin.getRelative(1, 0, 0);
        put(cane, caneRef("healthy", 1, "east"), state(Lifecycle.HEALTHY, 1, 20, START));
        service.neighbourChanged(cane);
        assertThat(effects.removed).isEqualTo(1);
        assertThat(blocks.stateAt(cane)).isEmpty();
    }

    @Test
    void aBushOnBadGroundBreaks() {
        effects.groundOk = false;
        put(origin, bushRef("healthy", 1), state(Lifecycle.HEALTHY, 1, 20, START));
        service.neighbourChanged(origin);
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void outOfClimateTheBushWithersAndACaneBecomesADeadCane() {
        effects.env = new BerryEffects.Environment(0, 50f, true);
        put(origin, bushRef("healthy", 1), state(Lifecycle.HEALTHY, 1, 20, START));
        service.neighbourChanged(origin);
        assertThat(blocks.stateAt(origin).get().id()).isEqualTo(SpreadingBushDevice.DEAD_ID);
        assertThat(stored(origin).dead()).isTrue();

        Block cane = origin.getRelative(0, 0, 3);
        put(origin.getRelative(0, 0, 2), bushRef("healthy", 2), state(Lifecycle.HEALTHY, 2, 20, START));
        put(cane, caneRef("healthy", 1, "south"), state(Lifecycle.HEALTHY, 1, 20, START));
        // the cane's bush is the block at z+2 behind it (facing south means the bush is north of it): z+2 is north of z+3
        service.neighbourChanged(cane);
        assertThat(blocks.stateAt(cane).get().id()).isEqualTo(SpreadingBushDevice.DEAD_CANE_ID);
        assertThat(blocks.stateAt(cane).get().properties()).containsEntry("facing", "south").containsEntry("stage", "1");
    }

    @Test
    void aDeadBushIsRemovedByTheNextCatchUp() {
        BerryState dead = new BerryState(true, new BerryBush(Lifecycle.DORMANT, 1, 20, START, 0L), START - 10, 50, 5f, true);
        put(origin, BlockStateRef.of(SpreadingBushDevice.DEAD_ID).with("stage", "1"), dead);
        service.neighbourChanged(origin);
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void aFruitingBushIsPickedWithAnItem() {
        long august = 3L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY; // May + 3 months = August: raspberry fruits Oct, flowers Aug-Sep
        clock.set(START + (long) ((5L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY) / BerryClock.CALENDAR_TICK_RATE)); // October
        put(origin, bushRef("fruiting", 2), state(Lifecycle.FRUITING, 2, 20, clock.get()));
        assertThat(august).isPositive();
        assertThat(service.use(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR))).isTrue();
        assertThat(effects.given).hasSize(1);
        assertThat(stored(origin).bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
    }

    @Test
    void aWaterloggedCranberryGrowsAndADryOneStaysDormant() {
        BlockStateRef ref = BlockStateRef.of(SpreadingBushDevice.CRANBERRY_ID).with("lifecycle", "healthy").with("stage", "0");
        // June: the cranberry is healthy
        clock.set(START + (long) ((1L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY) / BerryClock.CALENDAR_TICK_RATE));
        put(origin, ref, new BerryState(false, new BerryBush(Lifecycle.HEALTHY, 0, 24, clock.get() - 3 * BerryRules.GROWTH_TICKS, 0L), 0L, 50, 0f, true));
        service.neighbourChanged(origin);
        assertThat(stored(origin).bush().stage()).isGreaterThan(0);

        effects.wet = false;
        Block dry = origin.getRelative(0, 0, 5);
        put(dry, ref, new BerryState(false, new BerryBush(Lifecycle.HEALTHY, 0, 24, clock.get() - 3 * BerryRules.GROWTH_TICKS, 0L), 0L, 50, 0f, true));
        service.neighbourChanged(dry);
        assertThat(stored(dry).bush().lifecycle()).isEqualTo(Lifecycle.DORMANT);
        assertThat(stored(dry).bush().stage()).isZero();
    }

    @Test
    void parsingMapsBlockIdsToFamilies() {
        assertThat(SpreadingBushDevice.parse(caneRef("healthy", 1, "west")).get().part()).isEqualTo(SpreadingBushDevice.Part.CANE);
        assertThat(SpreadingBushDevice.parse(bushRef("healthy", 1)).get().species()).isEqualTo(SpreadingSpecies.RASPBERRY);
        assertThat(SpreadingBushDevice.parse(BlockStateRef.of(SpreadingBushDevice.CRANBERRY_ID)).get().part())
            .isEqualTo(SpreadingBushDevice.Part.CRANBERRY);
        assertThat(SpreadingBushDevice.parse(BlockStateRef.of("tfp:berry_bush/snowberry"))).isEmpty();
        assertThat(SpreadingBushDevice.productId(SpreadingSpecies.BLACKBERRY)).isEqualTo("tfp:food/blackberry");
    }
}
