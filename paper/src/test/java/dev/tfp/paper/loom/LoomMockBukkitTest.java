/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loom;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.loom.LoomState;
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
 * PAR-PROC-06 on MockBukkit with fakes of the CraftEngine adapter and the world effects. Stand-ins: string = wool yarn,
 * 4 per recipe, 3 steps; white wool = the cloth.
 */
class LoomMockBukkitTest extends MockServerTest {
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


    static final class Effects implements LoomEffects {
        int weaves;
        final List<Long> wakes = new ArrayList<>();

        @Override
        public void weave(Block block) {
            weaves++;
        }

        @Override
        public void wake(Block block, long ticks) {
            wakes.add(ticks);
        }

        @Override
        public void state(Block block, BlockStateRef wanted) {
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LoomDevice.ID + "/oak");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(1000);
    private DeviceService service;
    private DeviceKind<LoomState> kind;
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
        World world = server.addSimpleWorld("loom");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        kind = service.register(LoomDevice.kind(new LoomDevice.Services(LoomMockBukkitTest::recipe, effects)));
        loom = world.getBlockAt(0, 64, 0);
        blocks.place(loom, BlockStateRef.of(LoomDevice.ID + "/oak").with("facing", "north"), true);
        store.data.put(FakeCustomBlocks.at(loom), kind.codec().encode(kind.fresh().withLastUpdateTick(clock.get())));
        player = server.addPlayer();
    }

    private DeviceRecord<LoomState> record() {
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
        assertThat(slot(LoomDevice.SLOT_RECIPE).getAmount()).isEqualTo(4);
    }

    @Test
    void yarnIsAddedOneAtATimeUpToTheRecipeCount() {
        fill();
        assertThat(click(Material.STRING, 5)).as("the recipe count is reached: a held yarn does not push").isFalse();
        assertThat(slot(LoomDevice.SLOT_RECIPE).getAmount()).isEqualTo(4);
        assertThat(player.getInventory().getItem(EquipmentSlot.HAND).getAmount()).isEqualTo(5);
        assertThat(click(Material.DIRT, 1)).as("an item without a recipe is not taken").isFalse();
    }

    @Test
    void pushingWeavesInStepsAndFinishesWithTheCloth() {
        fill();
        for (int step = 1; step <= 3; step++) {
            assertThat(click(Material.AIR, 0)).as("push " + step).isTrue();
            assertThat(record().state().pending()).isTrue();
            assertThat(click(Material.AIR, 0)).as("a second push while a step is pending passes").isFalse();
            clock.addAndGet(20);
            service.neighbourChanged(loom); // the callback the device asked for
            assertThat(record().state().progress()).isEqualTo(step == 3 ? 3 : step);
            clock.addAndGet(1);
        }
        assertThat(effects.weaves).isEqualTo(3);
        assertThat(slot(LoomDevice.SLOT_RECIPE).getType()).isEqualTo(Material.AIR);
        assertThat(slot(LoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.WHITE_WOOL);
        assertThat(click(Material.AIR, 0)).as("the cloth is taken by hand").isTrue();
        assertThat(player.getInventory().contains(Material.WHITE_WOOL)).isTrue();
        assertThat(record().state().progress()).isZero();
        assertThat(slot(LoomDevice.SLOT_OUTPUT).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aLateLookCountsThePendingStepFirst() {
        fill();
        assertThat(click(Material.AIR, 0)).isTrue();
        clock.addAndGet(5000); // nobody looked: the next click settles, then pushes again
        assertThat(click(Material.AIR, 0)).isTrue();
        assertThat(record().state().progress()).isEqualTo(1);
        assertThat(record().state().pending()).isTrue();
    }

    @Test
    void sneakingTakesTheYarnBackAndResetsTheProgress() {
        fill();
        click(Material.AIR, 0);
        clock.addAndGet(21);
        player.setSneaking(true);
        assertThat(click(Material.AIR, 0)).isTrue();
        assertThat(player.getInventory().contains(Material.STRING)).isTrue();
        assertThat(slot(LoomDevice.SLOT_RECIPE).getType()).isEqualTo(Material.AIR);
        assertThat(record().state().progress()).isZero();
        assertThat(click(Material.AIR, 0)).as("nothing left to take").isFalse();
    }

    @Test
    void placementFacingIsOppositeTheLookDirection() {
        assertThat(LoomListener.facing(0f)).isEqualTo("north");
        assertThat(LoomListener.facing(90f)).isEqualTo("east");
        assertThat(LoomListener.facing(180f)).isEqualTo("south");
        assertThat(LoomListener.facing(-90f)).isEqualTo("west");
    }
}
