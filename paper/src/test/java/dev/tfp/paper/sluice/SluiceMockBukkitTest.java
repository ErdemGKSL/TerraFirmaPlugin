/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sluice;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.pan.SluiceRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.sluice.SluiceState;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-13 on MockBukkit with fakes of the CraftEngine adapter and the world effects: soil taken in only while the
 * sluice works, the lazy catch-up producing the right number of deposits for a long gap, the countdown standing still
 * without water, and the geometry of TFC's input, output and gathering box.
 */
class SluiceMockBukkitTest extends MockServerTest {
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
        public ItemBlob encode(ItemStack stack) {
            if (StackCodec.isEmpty(stack)) {
                return ItemBlob.EMPTY;
            }
            table.add(stack.clone());
            return ItemBlob.of(ByteBuffer.allocate(4).putInt(table.size() - 1).array());
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            return blob.isEmpty() ? ItemStack.empty() : table.get(ByteBuffer.wrap(blob.bytes()).getInt()).clone();
        }
    }

    static final class Effects implements SluiceEffects {
        SluiceRules.State flow = SluiceRules.State.BOTH;
        final List<String> output = new ArrayList<>();
        final List<SluiceRules.State> water = new ArrayList<>();
        final List<Long> wakes = new ArrayList<>();
        int used;
        final List<BlockStateRef> verified = new ArrayList<>();

        @Override
        public SluiceRules.State flow(Block upper) {
            return flow;
        }

        @Override
        public void water(Block upper, SluiceRules.State state) {
            water.add(state);
        }

        @Override
        public void output(Block upper, List<String> deposits) {
            output.addAll(deposits);
        }

        @Override
        public void wake(Block upper, long ticks) {
            wakes.add(ticks);
        }

        @Override
        public void used(Block upper) {
            used++;
        }

        @Override
        public void place(Block upper, BlockStateRef base, BlockFace facing) {
        }

        @Override
        public void verify(Block block, BlockStateRef state) {
            verified.add(state);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(SluiceDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(1000);
    private DeviceService service;
    private DeviceKind<SluiceState> kind;
    private World world;
    private Block upper;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("sluice");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(SluiceDevice.kind(new SluiceDevice.Services(
            s -> s.getType() == Material.GRAVEL ? Optional.of("tfp:deposit/gravel") : Optional.empty(), 100, effects, stacks)));
        upper = world.getBlockAt(0, 64, 0);
        blocks.place(upper, BlockStateRef.of(SluiceDevice.ID).with("upper", "true").with("facing", "north"), true);
        DataMap fresh = kind.codec().encode(kind.fresh().withLastUpdateTick(clock.get()));
        store.data.put(FakeCustomBlocks.at(upper), fresh);
    }

    private DeviceRecord<SluiceState> record() {
        return kind.codec().decode(store.read(upper).orElseThrow());
    }

    private void soil(int count) {
        service.edit(upper, kind, (record, now) -> {
            DeviceInventory<SluiceState> inv = new DeviceInventory<>(kind, record, stacks);
            for (int i = 0; i < count; i++) {
                inv = inv.insert(new ItemStack(Material.GRAVEL)).inventory();
            }
            return inv.record();
        });
    }

    private long soilCount() {
        return record().slots().stream().filter(b -> !b.isEmpty()).count();
    }

    @Test
    void aWorkingSluiceProducesOneDepositPerCycleLazily() {
        service.neighbourChanged(upper); // the water is read: working
        assertThat(record().state().working()).isTrue();
        soil(5);
        service.neighbourChanged(upper);
        assertThat(effects.wakes).as("with soil it asks to be looked at again").isNotEmpty();

        clock.addAndGet(100 * 3 + 40); // three cycles and a bit, nobody looked meanwhile
        service.neighbourChanged(upper);
        assertThat(effects.output).containsExactly("tfp:deposit/gravel", "tfp:deposit/gravel", "tfp:deposit/gravel");
        assertThat(soilCount()).isEqualTo(2);
        assertThat(record().state().pending()).isEmpty();
        assertThat(effects.used).isEqualTo(1);

        clock.addAndGet(10_000); // far more cycles than soil
        service.neighbourChanged(upper);
        assertThat(effects.output).hasSize(5);
        assertThat(soilCount()).isZero();
    }

    @Test
    void withoutWaterTheCountdownStandsStill() {
        effects.flow = SluiceRules.State.INPUT_ONLY;
        service.neighbourChanged(upper);
        assertThat(effects.water).containsExactly(SluiceRules.State.INPUT_ONLY);
        soil(3);
        clock.addAndGet(5_000);
        service.neighbourChanged(upper);
        assertThat(effects.output).isEmpty();
        assertThat(soilCount()).isEqualTo(3);
        assertThat(record().state().ticksRemaining()).isEqualTo(100);
    }

    @Test
    void theLowerHalfOnlyVerifiesAndNeverRuns() {
        Block lower = world.getBlockAt(0, 64, -1);
        blocks.place(lower, BlockStateRef.of(SluiceDevice.ID).with("upper", "false").with("facing", "north"), true);
        store.data.put(FakeCustomBlocks.at(lower), kind.codec().encode(kind.fresh().withLastUpdateTick(clock.get())));
        service.neighbourChanged(lower);
        assertThat(kind.codec().decode(store.read(lower).orElseThrow()).state().working()).isFalse();
        assertThat(effects.verified).hasSize(1);
    }

    @Test
    void intakeTakesOneItemPerSlotOnlyWhileWorking() {
        SluiceIntake intake = new SluiceIntake(service, kind, blocks, new SluiceDevice.Services(
            s -> s.getType() == Material.GRAVEL ? Optional.of("tfp:deposit/gravel") : Optional.empty(), 100, effects, stacks),
            null);
        Location in = upper.getLocation().add(0.5, 0.9, 0.5);
        Item item = world.dropItem(in, new ItemStack(Material.GRAVEL, 3));
        assertThat(intake.find(in)).contains(upper);
        effects.flow = SluiceRules.State.INPUT_ONLY;
        assertThat(intake.take(upper, item)).as("a sluice without water takes nothing").isFalse();
        assertThat(item.getItemStack().getAmount()).isEqualTo(3);

        effects.flow = SluiceRules.State.BOTH;
        assertThat(intake.take(upper, item)).isTrue();
        assertThat(soilCount()).isEqualTo(3);
        assertThat(item.isValid()).as("an emptied item entity is removed").isFalse();
        assertThat(intake.find(upper.getLocation().add(0.5, 3, 0.5))).isEmpty();
    }

    @Test
    void facingFromYawIsTheLookDirection() {
        assertThat(SluiceListener.facing(0f)).isEqualTo(BlockFace.SOUTH);
        assertThat(SluiceListener.facing(90f)).isEqualTo(BlockFace.WEST);
        assertThat(SluiceListener.facing(180f)).isEqualTo(BlockFace.NORTH);
        assertThat(SluiceListener.facing(-90f)).isEqualTo(BlockFace.EAST);
        assertThat(SluiceListener.facing(-180f)).isEqualTo(BlockFace.NORTH);
    }

    @Test
    void everyHalfChecksItsPartnerOnANeighbourUpdate() {
        service.neighbourChanged(upper);
        assertThat(effects.verified).hasSize(1);
        assertThat(SluiceDevice.isUpper(effects.verified.getFirst())).isTrue();
        assertThat(SluiceDevice.facing(effects.verified.getFirst())).isEqualTo(BlockFace.NORTH);
    }

    @Test
    void geometryFollowsTfc() {
        assertThat(SluiceDevice.outputOffset(BlockFace.NORTH)).containsExactly(0, -1, -2);
        assertThat(SluiceDevice.outputOffset(BlockFace.EAST)).containsExactly(2, -1, 0);
        assertThat(SluiceDevice.inputOffset(BlockFace.NORTH)).containsExactly(0, 1, 1);
        assertThat(SluiceDevice.inputOffset(BlockFace.WEST)).containsExactly(1, 1, 0);
        assertThat(SluiceDevice.inGatherBox(0.5, 0.9, 0.5)).isTrue();
        assertThat(SluiceDevice.inGatherBox(0.5, -0.1, 0.5)).isFalse();
        assertThat(SluiceDevice.inGatherBox(0.5, 0.1, 0.5)).as("items rest at the block's base on our carrier").isTrue();
        assertThat(SluiceDevice.inGatherBox(1.3, 0.9, 0.5)).isFalse();
    }

    @Test
    void waterArrivingAtTheInputWakesOnlyTheSluiceItBelongsTo() {
        // upper at 0,64,0 facing north: its input cell is one up and one back (south): 0,65,1
        SluiceWater water = new SluiceWater(blocks, effects, null);
        water.wakeAround(world.getBlockAt(0, 65, 1));
        assertThat(effects.wakes).hasSize(1);
        water.wakeAround(world.getBlockAt(0, 65, -1)); // the cell in front of it is no input of this sluice
        water.wakeAround(world.getBlockAt(5, 65, 5));
        assertThat(effects.wakes).hasSize(1);
    }

    @Test
    void theIntakeFindsTheRealBlockFamilyAndNotAnotherBlock() {
        assertThat(SluiceIntake.isSluice("tfp:wood/sluice/oak")).isTrue();
        assertThat(SluiceIntake.isSluice(SluiceDevice.ID)).isTrue();
        assertThat(SluiceIntake.isSluice("tfp:wood/sluice_other/oak")).isFalse();
        Block real = world.getBlockAt(10, 64, 10);
        blocks.known("tfp:wood/sluice/oak").place(real,
            BlockStateRef.of("tfp:wood/sluice/oak").with("upper", "true").with("facing", "south"), true);
        SluiceIntake intake = new SluiceIntake(service, kind, blocks, new SluiceDevice.Services(s -> Optional.empty(), 100,
            effects, stacks), null);
        assertThat(intake.find(real.getLocation().add(0.5, 0.9, 0.5))).contains(real);
    }
}
