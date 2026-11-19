/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.BerryBush;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
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

/** PAR-FARM-06 on MockBukkit with fakes of the CraftEngine adapter and the world effects: placement, picking, growth, withering. */
class BerryBushMockBukkitTest extends MockServerTest {
    static final long START = 20_000_000L;
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

    final class Effects implements BerryEffects {
        Environment env = new Environment(50, 0f, true);
        final List<BlockStateRef> shown = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        int sounds;
        int removed;

        @Override
        public Environment environment(Block block) {
            return env;
        }

        @Override
        public void show(Block block, BlockStateRef wanted, BerryState state) {
            shown.add(wanted);
        }

        @Override
        public void remove(Block block) {
            removed++;
        }

        @Override
        public int bushesInBox(Block block, String blockId) {
            return 99; // crowded: no propagation
        }

        @Override
        public boolean canPlant(Block target) {
            return false;
        }

        @Override
        public void plant(Block target, BlockStateRef ref, BerryState state) {
        }

        @Override
        public void track(Block block) {
        }

        @Override
        public Optional<ItemStack> product(BushSpecies species) {
            return Optional.of(new ItemStack(Material.SWEET_BERRIES));
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void pickSound(Block block) {
            sounds++;
        }

        @Override
        public RandomGenerator random() {
            return new java.util.SplittableRandom(1);
        }
    }

    private final String id = BerryBushDevice.blockId(BushSpecies.SNOWBERRY);
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(id, BerryBushDevice.DEAD_ID);
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private DeviceService service;
    private DeviceKind<BerryState> kind;
    private Block bush;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("berry");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, NO_STACKS, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(BerryBushDevice.kind(new BerryBushDevice.Services(effects,
            () -> new ClockSnapshot(1, clock.get(), MAY + (long) (BerryRulesClock.RATE * (clock.get() - START)), 8, true),
            BerryRules.Settings.DEFAULT)));
        bush = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** Calendar ticks per player tick, as {@code BerryClock}. */
    static final class BerryRulesClock {
        static final double RATE = dev.tfp.core.berry.BerryClock.CALENDAR_TICK_RATE;
    }

    private void put(BushSpecies species, BerryState state) {
        blocks.place(bush, BerryBushDevice.state(species, state), true);
        store.data.put(FakeCustomBlocks.at(bush), kind.codec().encode(DeviceRecord.fresh(state, 0).withLastUpdateTick(clock.get())));
    }

    private BerryState stored() {
        return kind.codec().decode(store.read(bush).orElseThrow()).state();
    }

    private static BerryState bush(Lifecycle l, int stage, long updated, long picked) {
        return new BerryState(false, new BerryBush(l, stage, 24, updated, picked), 0L, 50, 0f, true);
    }

    private boolean click() {
        return service.use(bush, player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
    }

    @Test
    void placementIsHealthyInSeasonAndStartsAtStageZero() {
        blocks.place(bush, BlockStateRef.of(id).with("lifecycle", "healthy").with("stage", "0"), true);
        store.data.put(FakeCustomBlocks.at(bush), new DataMap());
        service.placed(bush, player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
        BerryState s = stored();
        assertThat(s.bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(s.bush().stage()).isZero();
        assertThat(s.bush().growthsRemaining()).isEqualTo(BerryRules.GROWTHS_INITIAL);
        assertThat(s.bush().lastPickedTick()).isEqualTo(START);
        assertThat(effects.shown).isNotEmpty();
    }

    @Test
    void aFruitingBushIsPickedAndTurnsHealthy() {
        // September is fruiting for the snowberry: 4 months after May.
        long september = 4L * 8 * CalendarTicks.CALENDAR_TICKS_IN_DAY;
        clock.set(START + (long) (september / dev.tfp.core.berry.BerryClock.CALENDAR_TICK_RATE));
        put(BushSpecies.SNOWBERRY, bush(Lifecycle.FRUITING, 2, clock.get(), 0L));
        assertThat(click()).isTrue();
        assertThat(effects.given).hasSize(1);
        assertThat(effects.sounds).isEqualTo(1);
        assertThat(stored().bush().lifecycle()).isEqualTo(Lifecycle.HEALTHY);
        assertThat(stored().bush().lastPickedTick()).isEqualTo(clock.get());
    }

    @Test
    void aBushThatIsNotFruitingPassesTheClick() {
        put(BushSpecies.SNOWBERRY, bush(Lifecycle.HEALTHY, 2, START, 0L));
        assertThat(click()).isFalse();
        assertThat(effects.given).isEmpty();
    }

    @Test
    void outOfClimateTheBushWithersAndShowsTheDeadBlock() {
        effects.env = new BerryEffects.Environment(0, 50f, true);
        put(BushSpecies.SNOWBERRY, bush(Lifecycle.HEALTHY, 1, START - 100, 0L));
        service.neighbourChanged(bush);
        assertThat(stored().dead()).isTrue();
        assertThat(effects.shown.getLast()).isEqualTo(BlockStateRef.of(BerryBushDevice.DEAD_ID).with("stage", "1"));
    }

    @Test
    void speciesAndStateMapping() {
        assertThat(BerryBushDevice.species(id)).contains(BushSpecies.SNOWBERRY);
        assertThat(BerryBushDevice.species(BerryBushDevice.DEAD_ID)).isEmpty();
        assertThat(BerryBushDevice.species("tfp:berry_bush/cranberry")).isEmpty();
        assertThat(BerryBushDevice.species()).doesNotContain(BushSpecies.CRANBERRY).hasSize(6);
        assertThat(BerryBushDevice.state(BushSpecies.WINTERGREEN_BERRY, bush(Lifecycle.DORMANT, 2, 0, 0)))
            .isEqualTo(BlockStateRef.of("tfp:berry_bush/wintergreen_berry").with("lifecycle", "dormant").with("stage", "2"));
        assertThat(BerryBushDevice.productId(BushSpecies.SNOWBERRY)).isEqualTo("tfp:food/snowberry");
    }
}
