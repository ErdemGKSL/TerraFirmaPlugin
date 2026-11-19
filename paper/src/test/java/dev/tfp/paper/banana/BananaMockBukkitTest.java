/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.banana;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
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

/** PAR-FARM-09 on MockBukkit with fakes of the CraftEngine adapter and the world effects: sapling, growing stalk, climate, picking. */
class BananaMockBukkitTest extends MockServerTest {
    static final long START = 40_000_000L;
    static final long GROWTH = BananaRules.PLANT_GROWTH_TICKS;
    static final long CTD = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    static final int JANUARY = 0;
    static final int JUNE = 5;
    static final int JULY = 6;
    static final int SEPTEMBER = 8;
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

    final class Effects implements BananaEffects {
        BerryEffects.Environment env = new BerryEffects.Environment(60, 25f, true);
        RandomGenerator random = new SplittableRandom(7);
        int removed;
        int deadBushes;
        final List<ItemStack> given = new ArrayList<>();

        @Override
        public BerryEffects.Environment environment(Block stem, Block root) {
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
        public boolean canSeeSky(Block block) {
            return true;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, FruitTreeState state) {
            place(block, wanted, state);
        }

        @Override
        public void place(Block target, BlockStateRef ref, FruitTreeState state) {
            blocks.place(target, ref, true);
            store(target, state);
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
        public Optional<ItemStack> product(String itemId, int count) {
            return Optional.of(new ItemStack(Material.SWEET_BERRIES, count));
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
    private long calendarBase = month(JUNE, 2);
    private DeviceService service;
    private DeviceKind<FruitTreeState> kind;
    private World world;
    private Block origin;
    private PlayerMock player;

    /** The calendar tick at {@code day} days into the month with ordinal {@code month}, year 3 (8-day months). */
    private static long month(int month, int day) {
        return (3L * 12 + month) * 8 * CTD + day * CTD;
    }

    @BeforeEach
    void setUp() {
        blocks.known(BananaDevice.allIdsForTest());
        world = server.addSimpleWorld("banana");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        for (DeviceKind<FruitTreeState> k : BananaDevice.kinds(new BananaDevice.Services(effects,
            () -> new ClockSnapshot(1, clock.get(), calendarBase + (long) (BerryClock.CALENDAR_TICK_RATE * (clock.get() - START)), 8, true),
            BerryRules.Settings.DEFAULT))) {
            kind = service.register(k);
        }
        origin = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    private FruitTreeState stateAt(long updated) {
        return new FruitTreeState(0, 64, 0, updated, BerryState.NEVER_PICKED, 60, 25f, true);
    }

    private void put(Block block, BlockStateRef ref, FruitTreeState s) {
        blocks.place(block, ref, true);
        store.data.put(FakeCustomBlocks.at(block), kind.codec().encode(DeviceRecord.fresh(s, 0).withLastUpdateTick(clock.get())));
    }

    private FruitTreeState stored(Block block) {
        return kind.codec().decode(store.read(block).orElseThrow()).state();
    }

    private String idAt(Block b) {
        return blocks.stateAt(b).map(BlockStateRef::id).orElse("");
    }

    private String prop(Block b, String key) {
        return blocks.stateAt(b).orElseThrow().properties().get(key);
    }

    private Block at(int y) {
        return world.getBlockAt(0, y, 0);
    }

    @Test
    void aSaplingGrowsIntoAStageOnePlantCarryingTheExtraTicks() {
        put(origin, BananaDevice.sapling(), stateAt(START - BananaRules.SAPLING_GROWTH_TICKS - 1000));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).isEqualTo(BananaDevice.PLANT_ID);
        assertThat(prop(origin, "stage")).isEqualTo("1");
        assertThat(prop(origin, "lifecycle")).isEqualTo("healthy");
        assertThat(stored(origin).lastUpdate()).isEqualTo(START - 1000);
    }

    @Test
    void aYoungSaplingWaitsAndOneInADormantMonthToo() {
        put(origin, BananaDevice.sapling(), stateAt(START - 1000));
        service.neighbourChanged(origin);
        assertThat(idAt(origin)).isEqualTo(BananaDevice.SAPLING_ID);

        calendarBase = month(JANUARY, 2);
        Block other = world.getBlockAt(10, 64, 0);
        put(other, BananaDevice.sapling(), stateAt(START - 3 * BananaRules.SAPLING_GROWTH_TICKS));
        service.neighbourChanged(other);
        assertThat(idAt(other)).isEqualTo(BananaDevice.SAPLING_ID);
    }

    @Test
    void aSaplingOutOfClimateWithersWhenItWouldGrow() {
        effects.env = new BerryEffects.Environment(10, 25f, true);
        put(origin, BananaDevice.sapling(), stateAt(START - BananaRules.SAPLING_GROWTH_TICKS - 1000));
        service.neighbourChanged(origin);
        assertThat(effects.deadBushes).isEqualTo(1);
    }

    @Test
    void owedCyclesGrowATrunkUpwardsAndBankTheRest() {
        put(origin, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START - 3 * GROWTH));
        service.neighbourChanged(origin);
        // 3 cycles: the first step banks 1, the child's own step banks -1 (TFC subtracts two), the third block waits
        assertThat(prop(origin, "stage")).isEqualTo("0");
        assertThat(prop(at(65), "stage")).isEqualTo("0");
        assertThat(prop(at(66), "stage")).isEqualTo("1");
        assertThat(idAt(at(67))).isEmpty();
        assertThat(stored(at(66)).lastUpdate()).isEqualTo(START + GROWTH);
        assertThat(stored(origin).lastUpdate()).isEqualTo(START);
        assertThat(stored(at(66)).stemY()).isEqualTo(64);
    }

    @Test
    void aTallStalkEndsInATopBlock() {
        for (int y = 64; y <= 67; y++) {
            put(at(y), BananaDevice.plant(0, Lifecycle.HEALTHY), stateAt(START));
        }
        put(at(68), BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START - GROWTH));
        service.neighbourChanged(at(68));
        assertThat(prop(at(68), "stage")).isEqualTo("2");
        assertThat(idAt(at(69))).isEmpty();
    }

    @Test
    void aBlockedTopDoesNotGrowButResetsItsCounter() {
        put(origin, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START - 2 * GROWTH));
        at(65).setType(Material.STONE);
        service.neighbourChanged(origin);
        assertThat(prop(origin, "stage")).isEqualTo("1");
        assertThat(stored(origin).lastUpdate()).isEqualTo(START);
    }

    @Test
    void aDormantMonthStopsGrowthAndTheLifecycle() {
        calendarBase = month(JANUARY, 2);
        put(origin, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START - 3 * GROWTH));
        service.neighbourChanged(origin);
        assertThat(prop(origin, "lifecycle")).isEqualTo("dormant");
        assertThat(prop(origin, "stage")).isEqualTo("1");
        assertThat(idAt(at(65))).isEmpty();
    }

    @Test
    void theLifecycleFollowsTheMonth() {
        calendarBase = month(JULY, 2);
        put(origin, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START));
        service.neighbourChanged(origin);
        assertThat(prop(origin, "lifecycle")).isEqualTo("flowering");
    }

    @Test
    void outOfClimateKillsTheStalkBelowButNotTheBlockItself() {
        put(origin, BananaDevice.plant(0, Lifecycle.HEALTHY), stateAt(START));
        put(at(65), BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START));
        effects.env = new BerryEffects.Environment(5, 25f, true);
        service.neighbourChanged(at(65));
        assertThat(idAt(origin)).isEqualTo(BananaDevice.DEAD_ID);
        assertThat(prop(origin, "stage")).isEqualTo("0");
        assertThat(idAt(at(65))).isEqualTo(BananaDevice.PLANT_ID);
        // the block above the dead one loses its support on the next update
        service.neighbourChanged(at(65));
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void pickingAFruitingBlockGivesBananasAndKillsTheStalk() {
        calendarBase = month(SEPTEMBER, 2);
        put(origin, BananaDevice.plant(0, Lifecycle.FRUITING), stateAt(START));
        put(at(65), BananaDevice.plant(1, Lifecycle.FRUITING), stateAt(START));
        assertThat(service.use(at(65), player, EquipmentSlot.HAND, new ItemStack(Material.AIR))).isTrue();
        assertThat(effects.given).hasSize(1);
        assertThat(effects.given.get(0).getAmount()).isBetween(3, 6);
        assertThat(idAt(at(65))).isEqualTo(BananaDevice.DEAD_ID);
        assertThat(prop(at(65), "stage")).isEqualTo("2");
        assertThat(idAt(origin)).isEqualTo(BananaDevice.DEAD_ID);
        assertThat(prop(origin, "stage")).isEqualTo("0");
    }

    @Test
    void aBlockThatIsNotFruitingIsNotPicked() {
        put(origin, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START));
        assertThat(service.use(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR))).isFalse();
        assertThat(effects.given).isEmpty();
        assertThat(idAt(origin)).isEqualTo(BananaDevice.PLANT_ID);
    }

    @Test
    void deadPlantsLingerThenGo() {
        put(origin, BananaDevice.dead(2), stateAt(START - 1000));
        service.neighbourChanged(origin);
        assertThat(effects.removed).isZero();
        Block old = world.getBlockAt(10, 64, 0);
        put(old, BananaDevice.dead(2), stateAt(START - BananaRules.DEAD_LINGER_TICKS - 1));
        // dead plants need support: put it on the plantable layer
        Block ground = world.getBlockAt(20, 63, 0);
        put(ground, BananaDevice.dead(2), stateAt(START - BananaRules.DEAD_LINGER_TICKS - 1));
        service.neighbourChanged(ground);
        assertThat(effects.removed).isEqualTo(1);
    }

    @Test
    void unsupportedBlocksBreak() {
        Block high = world.getBlockAt(5, 80, 5);
        put(high, BananaDevice.sapling(), stateAt(START));
        service.neighbourChanged(high);
        assertThat(effects.removed).isEqualTo(1);
        Block floating = world.getBlockAt(6, 80, 5);
        put(floating, BananaDevice.plant(1, Lifecycle.HEALTHY), stateAt(START));
        service.neighbourChanged(floating);
        assertThat(effects.removed).isEqualTo(2);
    }

    @Test
    void placingASaplingSetsItsOwnStem() {
        blocks.place(origin, BananaDevice.sapling(), true);
        store.data.put(FakeCustomBlocks.at(origin), new DataMap());
        service.placed(origin, player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
        FruitTreeState s = stored(origin);
        assertThat(s.stemX()).isZero();
        assertThat(s.stemY()).isEqualTo(64);
        assertThat(s.lastUpdate()).isEqualTo(START);
    }

    @Test
    void parsingMapsBlockIds() {
        assertThat(BananaDevice.parse(BlockStateRef.of(BananaDevice.DEAD_ID)).get().part()).isEqualTo(BananaDevice.Part.DEAD);
        assertThat(BananaDevice.parse(BlockStateRef.of("tfp:plant/cherry_sapling"))).isEmpty();
    }
}
