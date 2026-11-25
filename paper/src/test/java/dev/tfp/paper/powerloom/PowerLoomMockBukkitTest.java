/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.powerloom.PowerLoomState;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.loom.LoomDevice;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-MECH-08 on MockBukkit with fakes of the CraftEngine adapter. Stand-ins: string = wool yarn, 4 per recipe, 3 steps;
 * white wool = the cloth. The rotation is set the way the service sets it ({@link PowerLoomState#withSpeed}).
 */
class PowerLoomMockBukkitTest extends MockServerTest {
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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(PowerLoomDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicLong clock = new AtomicLong(1000);
    private DeviceService service;
    private DeviceKind<PowerLoomState> kind;
    private Block loom;
    private PlayerMock player;

    static Optional<LoomDevice.Recipe> recipe(ItemStack stack) {
        if (StackCodec.isEmpty(stack) || stack.getType() != Material.STRING) {
            return Optional.empty();
        }
        return Optional.of(new LoomDevice.Recipe(4, 3, in -> new ItemStack(Material.WHITE_WOOL)));
    }

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("powerloom");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        PowerLoomDevice device = new PowerLoomDevice(new PowerLoomDevice.Services(PowerLoomMockBukkitTest::recipe), stacks);
        kind = service.register(device.kind());
        loom = world.getBlockAt(0, 64, 0);
        blocks.place(loom, BlockStateRef.of(PowerLoomDevice.ID).with("facing", "north"), true);
        store.data.put(FakeCustomBlocks.at(loom), kind.codec().encode(kind.fresh().withLastUpdateTick(clock.get())));
        player = server.addPlayer();
    }

    private DeviceRecord<PowerLoomState> record() {
        return kind.codec().decode(store.read(loom).orElseThrow());
    }

    private ItemStack slot(int i) {
        return stacks.decode(record().slot(i));
    }

    private boolean click(Material held, int amount) {
        player.getInventory().setItem(EquipmentSlot.HAND, amount == 0 ? null : new ItemStack(held, amount));
        return service.use(loom, player, EquipmentSlot.HAND, player.getInventory().getItem(EquipmentSlot.HAND));
    }

    private void fill() {
        assertThat(click(Material.STRING, 10)).isTrue();
        for (int i = 0; i < 3; i++) {
            assertThat(click(Material.STRING, 10 - i - 1)).isTrue();
        }
        assertThat(slot(PowerLoomDevice.SLOT_RECIPE).getAmount()).isEqualTo(4);
    }

    /** What {@link PowerLoomService} does at a look: stores the speed, after catching the loom up at the old one. */
    private void turn(float speed) {
        service.edit(loom, kind, (record, now) -> record.withState(record.state().withSpeed(speed)));
    }

    private void pass(long ticks) {
        clock.addAndGet(ticks);
        service.neighbourChanged(loom);
    }

    @Test
    void yarnIsAddedOneAtATimeAndTheStateHoldsTheRecipeNumbers() {
        fill();
        PowerLoomState s = record().state();
        assertThat(s.steps()).isEqualTo(3);
        assertThat(s.inputCount()).isEqualTo(4);
        assertThat(s.slotCount()).isEqualTo(4);
        assertThat(click(Material.STRING, 5)).as("the recipe count is reached").isFalse();
        assertThat(click(Material.DIRT, 1)).as("an item without a recipe is not taken").isFalse();
    }

    @Test
    void anEmptyHandDoesNotPushThePowerLoom() {
        fill();
        assertThat(click(Material.AIR, 0)).isFalse();
        assertThat(record().state().pending()).isFalse();
        assertThat(record().state().pushes()).isZero();
    }

    @Test
    void aTurningLoomWeavesTheClothByItself() {
        fill();
        turn(0.1f); // pushes at 16, 48 and 80 after the stamp, the last step counts at 100
        pass(99);
        assertThat(slot(PowerLoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.AIR);
        assertThat(record().state().progress()).isEqualTo(2);
        pass(1);
        assertThat(slot(PowerLoomDevice.SLOT_RECIPE).getType()).isEqualTo(Material.AIR);
        assertThat(slot(PowerLoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.WHITE_WOOL);
        assertThat(record().state().pushes()).isEqualTo(3);
        assertThat(click(Material.AIR, 0)).as("the cloth is taken by hand").isTrue();
        assertThat(player.getInventory().contains(Material.WHITE_WOOL)).isTrue();
        assertThat(record().state().progress()).isZero();
        assertThat(slot(PowerLoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aStillLoomWeavesNothingHoweverLongItWaits() {
        fill();
        pass(60);
        pass(60);
        assertThat(record().state().pushes()).isZero();
        assertThat(slot(PowerLoomDevice.SLOT_RECIPE).getAmount()).isEqualTo(4);
    }

    @Test
    void aLoomWithTooLittleYarnIsNotPushed() {
        assertThat(click(Material.STRING, 10)).isTrue();
        turn(0.1f);
        pass(90);
        assertThat(record().state().pushes()).isZero();
    }

    @Test
    void yarnPutInBesideTheHandCountsToo() {
        // a hopper or a device edit fills the slot; the next catch-up reads the numbers from the slots
        service.edit(loom, kind, (record, now) -> new DeviceInventory<>(kind, record, stacks)
            .set(PowerLoomDevice.SLOT_RECIPE, new ItemStack(Material.STRING, 4)).record()
            .withState(record.state().withSpeed(0.1f)));
        pass(100);
        assertThat(slot(PowerLoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.WHITE_WOOL);
    }

    @Test
    void aTimeJumpIsNotWovenThroughButAWaitingStepStillCounts() {
        fill();
        turn(0.1f);
        pass(16); // the first push
        assertThat(record().state().pending()).isTrue();
        pass(100_000); // the chunk was away: TFC does not tick it
        assertThat(record().state().progress()).isEqualTo(1);
        assertThat(record().state().pushes()).isEqualTo(1);
    }

    @Test
    void sneakingTakesTheYarnBackAndResetsTheProgress() {
        fill();
        turn(0.1f);
        pass(40);
        assertThat(record().state().progress()).isEqualTo(1);
        player.setSneaking(true);
        assertThat(click(Material.AIR, 0)).isTrue();
        assertThat(player.getInventory().contains(Material.STRING)).isTrue();
        assertThat(slot(PowerLoomDevice.SLOT_RECIPE).getType()).isEqualTo(Material.AIR);
        assertThat(record().state().progress()).isZero();
        assertThat(click(Material.AIR, 0)).as("nothing left to take").isFalse();
    }

    @Test
    void placementFacesTheWayThePlayerLooks() {
        assertThat(PowerLoomListener.facing(0f)).isEqualTo("south");
        assertThat(PowerLoomListener.facing(90f)).isEqualTo("west");
        assertThat(PowerLoomListener.facing(180f)).isEqualTo("north");
        assertThat(PowerLoomListener.facing(-90f)).isEqualTo("east");
    }
}
