/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.stove;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.pot.PotData;
import dev.tfp.core.pot.PotSim;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.grill.GrillDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.pot.PotConversion;
import dev.tfp.paper.pot.PotDevice;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-31 on MockBukkit with fakes of the CraftEngine adapter: a ceramic pot turns a stove into a stove pot and the pot
 * taken back turns it into the stove again (facing, lit and the fuel kept), a grill is refused on a stove, and the firepit
 * device runs the stove's block id.
 */
class StoveMockBukkitTest extends MockServerTest {
    private static final NamespacedKey ID = new NamespacedKey("tfptest", "id");

    private static final class PdcItems implements CustomItems {
        @Override
        public boolean exists(String id) {
            return id.startsWith("tfp:");
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            ItemStack s = new ItemStack(Material.PAPER, amount);
            ItemMeta m = s.getItemMeta();
            m.getPersistentDataContainer().set(ID, PersistentDataType.STRING, id);
            s.setItemMeta(m);
            return Optional.of(s);
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            if (stack == null || !stack.hasItemMeta()) {
                return Optional.empty();
            }
            return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
        }
    }

    private static final class TableCodec implements StackCodec {
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

    private static final class MemoryStore implements DeviceStore {
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

    private final PdcItems items = new PdcItems();
    private final TableCodec codec = new TableCodec();
    private final MemoryStore store = new MemoryStore();
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FirepitDevice.ID, FirepitDevice.STOVE_ID,
        PotDevice.ID, PotDevice.STOVE_ID);
    private DeviceService service;
    private DeviceKind<FirepitData> stoveKind;
    private DeviceKind<PotData> stovePotKind;
    private PotConversion conversion;
    private World world;
    private Block block;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("stove");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 0L, codec, items, Logger.getLogger("test"));
        // the kinds the modules register, without their handlers: the conversion only edits records
        DeviceKind<FirepitData> firepit = firepitKind(FirepitDevice.ID);
        stoveKind = firepitKind(FirepitDevice.STOVE_ID);
        DeviceKind<PotData> pot = potKind(PotDevice.ID);
        stovePotKind = potKind(PotDevice.STOVE_ID);
        service.register(firepit);
        service.register(stoveKind);
        service.register(pot);
        service.register(stovePotKind);
        conversion = new PotConversion(service, blocks, pot, this::place);
        block = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.teleport(new Location(world, 2.5, 64, 0.5));
    }

    private static DeviceKind<FirepitData> firepitKind(String id) {
        return DeviceKind.builder(id, FirepitData.CODEC, FirepitData.FRESH).slots(FirepitSim.SLOTS).build();
    }

    private static DeviceKind<PotData> potKind(String id) {
        return DeviceKind.builder(id, PotData.CODEC, PotData.FRESH).slots(PotSim.SLOTS).build();
    }

    /** CraftEngine replacing a custom block: the old block entity's removal, then a fresh record for the new one. */
    private boolean place(Block at, BlockStateRef state) {
        Optional<BlockStateRef> old = blocks.stateAt(at);
        if (old.isPresent()) {
            DataMap data = store.data.remove(FakeCustomBlocks.at(at));
            service.removed(at.getWorld(), at.getX(), at.getY(), at.getZ(), old.get().id(), data == null ? new DataMap() : data);
        }
        blocks.place(at, state, true);
        store.data.put(FakeCustomBlocks.at(at), service.created(state.id()).orElse(new DataMap()));
        return true;
    }

    private void placeStove(String facing, boolean lit) {
        place(block, BlockStateRef.of(FirepitDevice.STOVE_ID).with("facing", facing).with("lit", Boolean.toString(lit))
            .with("smoke_level", "0").with("axis", "z"));
    }

    @Test
    void aPotTurnsTheStoveIntoAStovePotAndBackKeepingFacingLitAndFuel() {
        placeStove("west", true);
        ItemStack log = new ItemStack(Material.OAK_LOG);
        service.edit(block, stoveKind, (record, now) -> record.withSlot(FirepitSim.SLOT_FUEL_CONSUME, codec.encode(log)));
        assertThat(conversion.isFirepit(block)).isTrue();
        assertThat(conversion.isPot(block)).isFalse();

        assertThat(conversion.toPot(block)).isTrue();
        BlockStateRef pot = blocks.stateAt(block).orElseThrow();
        assertThat(pot.id()).isEqualTo(PotDevice.STOVE_ID);
        assertThat(pot.properties()).containsEntry("facing", "west").containsEntry("lit", "true").containsEntry("axis", "z");
        assertThat(conversion.isPot(block)).isTrue();
        DeviceRecord<PotData> potRecord = service.edit(block, stovePotKind, (r, now) -> r).orElseThrow();
        assertThat(codec.decode(potRecord.slot(FirepitSim.SLOT_FUEL_CONSUME)).getType()).isEqualTo(Material.OAK_LOG);

        assertThat(conversion.toFirepit(block)).isTrue();
        BlockStateRef back = blocks.stateAt(block).orElseThrow();
        assertThat(back.id()).isEqualTo(FirepitDevice.STOVE_ID);
        assertThat(back.properties()).containsEntry("facing", "west").containsEntry("lit", "true");
        DeviceRecord<FirepitData> stoveRecord = service.edit(block, stoveKind, (r, now) -> r).orElseThrow();
        assertThat(codec.decode(stoveRecord.slot(FirepitSim.SLOT_FUEL_CONSUME)).getType()).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void aFirepitStillBecomesAPotNotAStovePot() {
        place(block, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "x"));
        assertThat(conversion.toPot(block)).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(PotDevice.ID);
        assertThat(conversion.toFirepit(block)).isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(FirepitDevice.ID);
    }

    @Test
    void theFirepitDeviceCanRunTheStoveBlockId() {
        assertThat(FirepitDevice.isFirepit(FirepitDevice.STOVE_ID)).isTrue();
        assertThat(FirepitDevice.isFirepit(GrillDevice.ID)).isFalse();
        assertThat(PotDevice.isPot(PotDevice.STOVE_ID)).isTrue();
        assertThat(PotDevice.isPot(FirepitDevice.STOVE_ID)).isFalse();
        assertThat(service.kinds().find(FirepitDevice.STOVE_ID).orElseThrow().id()).isEqualTo(FirepitDevice.STOVE_ID);
    }

    @Test
    void aWroughtIronGrillIsRefusedOnAStoveButNotOnAFirepit() {
        Scheduler owner = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> method.getName().equals("isOwner") ? Boolean.TRUE : null);
        StoveListener listener = new StoveListener(blocks, items, owner);
        placeStove("north", false);
        ItemStack grill = items.create(GrillDevice.GRILL_ITEM, 1).orElseThrow();
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, grill, block, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(e);
        assertThat(e.isCancelled()).as("TFC StoveBlock.useItemOn returns FAIL for the grill").isTrue();
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo(FirepitDevice.STOVE_ID);

        PlayerInteractEvent other = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            new ItemStack(Material.STICK), block, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteract(other);
        assertThat(other.isCancelled()).isFalse();

        Block pit = world.getBlockAt(4, 64, 4);
        blocks.place(pit, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "x"), true);
        PlayerInteractEvent onPit = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, grill, pit, BlockFace.UP,
            EquipmentSlot.HAND);
        listener.onInteract(onPit);
        assertThat(onPit.isCancelled()).as("the firepit's own listener turns it into a grill").isFalse();
    }
}
