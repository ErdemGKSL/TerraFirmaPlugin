/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sapling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.sapling.SaplingRules;
import dev.tfp.core.sapling.SaplingState;
import dev.tfp.core.worldgen.tree.BranchDirection;
import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.Wood;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FARM-10 on MockBukkit with fakes of the CraftEngine adapter and the tree planning: catch-up, light, refusal, the modifier. */
class SaplingMockBukkitTest extends MockServerTest {
    static final long CTD = SaplingRules.CALENDAR_TICKS_IN_DAY;
    static final long OAK = SaplingRules.ticksToGrow(Wood.OAK, 1.0);
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

    final class Effects implements SaplingEffects {
        int light = 15;
        boolean fits = true;
        final List<Tree> grown = new ArrayList<>();
        final List<Block> tracked = new ArrayList<>();
        final List<Long> seeds = new ArrayList<>();

        @Override
        public Optional<BlockStateRef> blockAt(Block block) {
            return blocks.stateAt(block);
        }

        @Override
        public int lightAbove(Block sapling) {
            return light;
        }

        @Override
        public Optional<Tree> plan(Block sapling, Wood wood, long seed) {
            seeds.add(seed);
            return fits ? Optional.of(new Tree(sapling.getX(), sapling.getY(), sapling.getZ(), wood, Tree.Variant.NORMAL,
                List.of(new TreeBlock(0, 0, 0, TreeBlock.Kind.LOG, wood, TreeBlock.Axis.Y, BranchDirection.DOWN, true)))) : Optional.empty();
        }

        @Override
        public void grow(Block sapling, Tree tree) {
            grown.add(tree);
            blocks.place(sapling, BlockStateRef.parse("tfp:wood/log/" + tree.wood().serializedName() + "[axis=y]"), false);
        }

        @Override
        public void track(Block block) {
            tracked.add(block);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final MemoryStore store = new MemoryStore();
    private final Effects effects = new Effects();
    private final AtomicLong calendar = new AtomicLong(50 * CTD);
    private double modifier = 1.0;
    private DeviceService service;
    private DeviceKind<SaplingState> oakKind;
    private World world;
    private Block origin;

    @BeforeEach
    void setUp() {
        blocks.known(SaplingDevice.allIds());
        blocks.known("tfp:wood/log/oak");
        world = server.addSimpleWorld("sapling");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 0L, NO_STACKS, new FakeCustomItems(), Logger.getLogger("test"));
        SaplingDevice.Services services = new SaplingDevice.Services(effects,
            () -> new ClockSnapshot(1, 0L, calendar.get(), 8, true), () -> modifier);
        for (DeviceKind<SaplingState> k : SaplingDevice.kinds(services)) {
            DeviceKind<SaplingState> registered = service.register(k);
            if (registered.id().equals("tfp:wood/sapling/oak")) {
                oakKind = registered;
            }
        }
        origin = world.getBlockAt(0, 64, 0);
    }

    private void plant(Block block, String wood, long plantedAt) {
        blocks.place(block, BlockStateRef.of("tfp:wood/sapling/" + wood), true);
        store.data.put(FakeCustomBlocks.at(block), oakKind.codec().encode(DeviceRecord.fresh(new SaplingState(plantedAt), 0)));
    }

    private String idAt(Block b) {
        return blocks.stateAt(b).map(BlockStateRef::id).orElse("");
    }

    @Test
    void anOakSaplingBecomesATreeAfterItsDays() {
        plant(origin, "oak", calendar.get() - OAK - 1);
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
        assertThat(effects.grown.getFirst().wood()).isEqualTo(Wood.OAK);
        assertThat(idAt(origin)).isEqualTo("tfp:wood/log/oak");
    }

    @Test
    void aYoungSaplingKeepsWaitingAndExactlyTheRequiredTicksIsNotEnough() {
        plant(origin, "oak", calendar.get() - OAK);
        service.neighbourChanged(origin);
        assertThat(effects.grown).isEmpty();
        assertThat(idAt(origin)).isEqualTo("tfp:wood/sapling/oak");
        calendar.addAndGet(1);
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
    }

    @Test
    void woodsGrowAtTheirOwnPace() {
        Block ash = world.getBlockAt(5, 64, 0);
        plant(ash, "ash", calendar.get() - 8 * CTD); // 7 days for ash, 10 for oak
        plant(origin, "oak", calendar.get() - 8 * CTD);
        service.neighbourChanged(ash);
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
        assertThat(effects.grown.getFirst().wood()).isEqualTo(Wood.ASH);
    }

    @Test
    void darknessStopsGrowthUntilThereIsLight() {
        plant(origin, "oak", calendar.get() - OAK - 1);
        effects.light = 8;
        service.neighbourChanged(origin);
        assertThat(effects.grown).isEmpty();
        effects.light = 9;
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
    }

    @Test
    void aTreeThatDoesNotFitLeavesTheSaplingStanding() {
        plant(origin, "oak", calendar.get() - 3 * OAK);
        effects.fits = false;
        service.neighbourChanged(origin);
        assertThat(effects.grown).isEmpty();
        assertThat(idAt(origin)).isEqualTo("tfp:wood/sapling/oak");
        effects.fits = true;
        calendar.addAndGet(1);
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
        assertThat(effects.seeds.get(0)).isNotEqualTo(effects.seeds.get(1));
    }

    @Test
    void theGrowthModifierScalesTheDays() {
        modifier = 2.0;
        plant(origin, "oak", calendar.get() - OAK - 1);
        service.neighbourChanged(origin);
        assertThat(effects.grown).isEmpty();
        calendar.addAndGet(OAK);
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
    }

    @Test
    void aSaplingWithoutAPlantingTickStartsCountingWhenFirstSettled() {
        plant(origin, "oak", -1);
        service.neighbourChanged(origin);
        assertThat(effects.grown).isEmpty();
        assertThat(oakKind.codec().decode(store.read(origin).orElseThrow()).state().plantedAt()).isEqualTo(calendar.get());
    }

    @Test
    void ticksPassedWhileUnloadedCountOnTheNextSettle() {
        plant(origin, "oak", calendar.get());
        calendar.addAndGet(30 * CTD); // the chunk was unloaded for a month
        service.neighbourChanged(origin);
        assertThat(effects.grown).hasSize(1);
    }

    @Test
    void placingASaplingStartsItsCounterAndListsItForCatchUp() {
        blocks.place(origin, BlockStateRef.of("tfp:wood/sapling/oak"), true);
        store.data.put(FakeCustomBlocks.at(origin), oakKind.codec().encode(DeviceRecord.fresh(SaplingDevice.UNSET, 0)));
        service.placed(origin, server.addPlayer(), org.bukkit.inventory.EquipmentSlot.HAND, new ItemStack(org.bukkit.Material.STICK));
        assertThat(oakKind.codec().decode(store.read(origin).orElseThrow()).state().plantedAt()).isEqualTo(calendar.get());
        assertThat(effects.tracked).containsExactly(origin);
    }
}
