/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device.automation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-60: {@link HopperAutomationListener} on a {@link HopperInventorySearchEvent} next to device blocks, with the
 * defaults of {@link DeviceAutomationModule} and stand-in kinds under the real kind ids and TFC's slot counts.
 */
class HopperAutomationMockBukkitTest extends MockServerTest {
    record Plain(int n) {
    }

    static final VersionedCodec<Plain> PLAIN = VersionedCodec.<Plain>builder("plain", 1)
        .build((p, out) -> out.putInt("n", p.n()), in -> new Plain(in.getInt("n", 0)));

    static final class MemoryStore implements DeviceStore {
        final Map<FakeCustomBlocks.At, DataMap> data = new ConcurrentHashMap<>();

        void add(Block block) {
            data.put(FakeCustomBlocks.at(block), new DataMap());
        }

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

    /** MockBukkit cannot serialise every meta: slots hold an index into this table. */
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

    static final class Port implements HopperAutomationListener.HopperPort {
        final Inventory inventory;
        int cooldown = -1;

        Port(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory inventory() {
            return inventory;
        }

        @Override
        public void cooldown(int ticks) {
            cooldown = ticks;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks()
        .known("tfp:wood/barrel/oak", "tfp:quern", "tfp:firepit");
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicBoolean owner = new AtomicBoolean(true);
    private final AtomicBoolean screen = new AtomicBoolean(false);
    /** A screen of every device kind: {@code screen} says its viewer is in another region; edits and reloads are counted. */
    private final java.util.concurrent.atomic.AtomicInteger edits = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger reloads = new java.util.concurrent.atomic.AtomicInteger();
    private final DeviceAutomation.ScreenSync sync = new DeviceAutomation.ScreenSync() {
        @Override
        public boolean unsyncable(org.bukkit.block.Block device) {
            return screen.get();
        }

        @Override
        public void edit(org.bukkit.block.Block device) {
            edits.incrementAndGet();
        }

        @Override
        public void reload(org.bukkit.block.Block device) {
            reloads.incrementAndGet();
        }
    };

    private DeviceService devices;
    private DeviceKind<BarrelData> barrel;
    private DeviceKind<Plain> quern;
    private DeviceKind<Plain> firepit;
    private World world;
    private Port port;
    private HopperAutomationListener listener;

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, m, args) -> {
                if (m.getName().equals("isOwner")) {
                    return owner.get();
                }
                throw new UnsupportedOperationException(m.getName());
            });
    }

    /** What {@code config.yml}'s automation section reads as right now (a reload replaces it). */
    private final AtomicReference<AutomationRules.Switches> switches =
        new AtomicReference<>(AutomationRules.Switches.TFC_DEFAULTS);

    private void wire(AutomationRules.Switches initial) {
        switches.set(initial);
        DeviceAutomation automation = new DeviceAutomation();
        DeviceAutomationModule.registerDefaults(automation, switches::get, new ArrayList<>(), kind -> sync,
            DeviceAutomation.Settle.NONE);
        port = new Port(server.createInventory(null, InventoryType.HOPPER));
        listener = new HopperAutomationListener(automation, new HopperMover(devices), blocks, scheduler(),
            b -> Optional.of(port), Logger.getLogger("test"));
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("automation");
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1_000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        barrel = devices.register(DeviceKind.builder(DeviceAutomationModule.BARREL, BarrelData.CODEC, BarrelData.FRESH)
            .slots(35).build());
        quern = devices.register(DeviceKind.builder(DeviceAutomationModule.QUERN, PLAIN, new Plain(0)).slots(3)
            .slotLimit(slot -> slot == 0 ? 1 : 64)
            .filter((slot, stack) -> slot != 0 || stack.getType() == Material.STONE).build());
        firepit = devices.register(DeviceKind.builder(DeviceAutomationModule.FIREPIT, PLAIN, new Plain(0)).slots(7)
            .slotLimit(slot -> slot == 5 || slot == 6 ? 64 : 1).build());
        wire(AutomationRules.Switches.TFC_DEFAULTS);
    }

    private Block device(int x, int y, int z, BlockStateRef state) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.BARRIER);
        blocks.place(b, state, false);
        store.add(b);
        return b;
    }

    private boolean search(Block hopper, Block device, HopperInventorySearchEvent.ContainerType type) {
        hopper.setType(Material.HOPPER);
        HopperInventorySearchEvent e = new HopperInventorySearchEvent(null, type, hopper, device);
        listener.onSearch(e);
        assertThat(e.getInventory()).as("the hopper itself never gets an inventory").isNull();
        return port.cooldown == AutomationRules.HOPPER_COOLDOWN;
    }

    private <S> DeviceRecord<S> record(Block b, DeviceKind<S> kind) {
        return kind.codec().decode(store.read(b).orElseThrow());
    }

    private ItemStack slot(Block b, DeviceKind<?> kind, int slot) {
        return stacks.decode(record(b, kind).slot(slot));
    }

    @Test
    void barrelTakesTheItemFromAboveAndTheContainerFromASide() {
        Block b = device(0, 64, 0, BlockStateRef.of("tfp:wood/barrel/oak").with("facing", "up"));
        port.inventory.setItem(0, new ItemStack(Material.SUGAR, 4));
        assertThat(search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        assertThat(slot(b, barrel, 2)).isEqualTo(new ItemStack(Material.SUGAR, 1));
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(3);

        port.inventory.clear();
        port.cooldown = -1;
        port.inventory.setItem(0, new ItemStack(Material.WATER_BUCKET));
        assertThat(search(b.getRelative(1, 0, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        assertThat(slot(b, barrel, 0).getType()).isEqualTo(Material.WATER_BUCKET);
        assertThat(slot(b, barrel, 2).getAmount()).as("a side does not reach the item slot").isEqualTo(1);
        assertThat(port.inventory.isEmpty()).isTrue();
    }

    @Test
    void barrelGivesTheItemDownAndNothingWhileSealed() {
        Block b = device(0, 64, 0, BlockStateRef.of("tfp:wood/barrel/oak").with("facing", "up"));
        devices.edit(b, barrel, (r, now) -> r.withSlot(2, stacks.encode(new ItemStack(Material.SUGAR, 2))));
        assertThat(search(b.getRelative(0, -1, 0), b, HopperInventorySearchEvent.ContainerType.SOURCE)).isTrue();
        assertThat(port.inventory.getItem(0)).isEqualTo(new ItemStack(Material.SUGAR, 1));
        assertThat(slot(b, barrel, 2).getAmount()).isEqualTo(1);

        port.cooldown = -1;
        devices.edit(b, barrel, (r, now) -> r.withState(new BarrelData(r.state().fluid(), true, 0L, -1L, -1L, false,
            -1L, false, false)));
        assertThat(search(b.getRelative(0, -1, 0), b, HopperInventorySearchEvent.ContainerType.SOURCE)).isFalse();
        assertThat(slot(b, barrel, 2).getAmount()).as("sealed: TFC canModify").isEqualTo(1);
    }

    @Test
    void quernFillsFromTheSideAndEmptiesDownOnly() {
        Block q = device(4, 64, 0, BlockStateRef.of("tfp:quern"));
        port.inventory.setItem(0, new ItemStack(Material.BONE, 8));
        assertThat(search(q.getRelative(0, 0, 1), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        assertThat(slot(q, quern, 1)).as("a bone is no handstone: the input slot").isEqualTo(new ItemStack(Material.BONE));
        port.cooldown = -1;
        assertThat(search(q.getRelative(0, 1, 0), q, HopperInventorySearchEvent.ContainerType.DESTINATION))
            .as("no handler on top").isFalse();

        devices.edit(q, quern, (r, now) -> r.withSlot(2, stacks.encode(new ItemStack(Material.BONE_MEAL, 3))));
        port.inventory.clear();
        port.inventory.setItem(0, new ItemStack(Material.BONE_MEAL, 64));
        assertThat(search(q.getRelative(0, -1, 0), q, HopperInventorySearchEvent.ContainerType.SOURCE)).isTrue();
        assertThat(port.inventory.getItem(1)).as("a full hopper slot is skipped")
            .isEqualTo(new ItemStack(Material.BONE_MEAL));
        assertThat(slot(q, quern, 2).getAmount()).isEqualTo(2);
    }

    @Test
    void firepitFuelCascadesAndTheInputComesFromAbove() {
        Block f = device(8, 64, 0, BlockStateRef.of("tfp:firepit"));
        port.inventory.setItem(0, new ItemStack(Material.OAK_LOG, 4));
        assertThat(search(f.getRelative(-1, 0, 0), f, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        port.cooldown = -1;
        assertThat(search(f.getRelative(-1, 0, 0), f, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        assertThat(slot(f, firepit, 0).getType()).as("cascaded from slot 3").isEqualTo(Material.OAK_LOG);
        assertThat(slot(f, firepit, 1).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(record(f, firepit).slot(3).isEmpty()).isTrue();

        port.inventory.clear();
        port.cooldown = -1;
        port.inventory.setItem(0, new ItemStack(Material.BEEF));
        assertThat(search(f.getRelative(0, 1, 0), f, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        assertThat(slot(f, firepit, 4).getType()).isEqualTo(Material.BEEF);
    }

    @Test
    void automationOffAScreenOrAnotherRegionLeaveTheEventUntouched() {
        wire(new AutomationRules.Switches(false, true, true, true, true));
        Block b = device(0, 64, 0, BlockStateRef.of("tfp:wood/barrel/oak").with("facing", "up"));
        port.inventory.setItem(0, new ItemStack(Material.SUGAR, 4));
        assertThat(search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(4);

        Block q = device(4, 64, 0, BlockStateRef.of("tfp:quern"));
        screen.set(true);
        assertThat(search(q.getRelative(1, 0, 0), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        screen.set(false);
        owner.set(false);
        assertThat(search(q.getRelative(1, 0, 0), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(4);
        owner.set(true);
        assertThat(search(q.getRelative(1, 0, 0), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
    }

    @Test
    void aRealContainerOrAPlainBlockIsVanillas() {
        Block plain = world.getBlockAt(20, 64, 0);
        plain.setType(Material.STONE);
        Block hopper = plain.getRelative(0, 1, 0);
        hopper.setType(Material.HOPPER);
        Inventory chest = server.createInventory(null, 27);
        HopperInventorySearchEvent e = new HopperInventorySearchEvent(chest,
            HopperInventorySearchEvent.ContainerType.DESTINATION, hopper, plain);
        listener.onSearch(e);
        assertThat(e.getInventory()).isSameAs(chest);
        port.inventory.setItem(0, new ItemStack(Material.SUGAR));
        assertThat(search(hopper, plain, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
    }

    @Test
    void switchesComeFromTheTypedAutomationSection() {
        assertThat(DeviceAutomationModule.switches(TfpConfig.Automation.DEFAULTS))
            .isEqualTo(AutomationRules.Switches.TFC_DEFAULTS);
        assertThat(DeviceAutomationModule.switches(new TfpConfig.Automation(true, true, false, true, true)))
            .isEqualTo(new AutomationRules.Switches(true, true, false, true, true));
    }

    @Test
    void aReloadSwitchesAutomationOffAndOnAtTheNextMove() {
        Block q = device(4, 64, 0, BlockStateRef.of("tfp:quern"));
        port.inventory.setItem(0, new ItemStack(Material.BONE, 8));
        assertThat(search(q.getRelative(0, 0, 1), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
        port.cooldown = -1;
        switches.set(new AutomationRules.Switches(true, true, false, true, true)); // /tfp reload config with quern: false
        assertThat(search(q.getRelative(0, 0, 1), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        assertThat(port.inventory.getItem(0).getAmount()).as("nothing moved while off").isEqualTo(7);
        switches.set(AutomationRules.Switches.TFC_DEFAULTS);
        assertThat(search(q.getRelative(0, 0, 1), q, HopperInventorySearchEvent.ContainerType.DESTINATION)).isTrue();
    }

    @Test
    void registrationApiFindsTheLongestKindAndReplaces() throws Exception {
        DeviceAutomation a = new DeviceAutomation();
        DeviceAutomation.Spec wide = DeviceAutomation.Spec.of("tfp:wood", s -> dev.tfp.core.device.automation.SideTable.NONE);
        DeviceAutomation.Spec narrow = DeviceAutomation.Spec.of("tfp:wood/barrel",
            s -> dev.tfp.core.device.automation.TfcSideTables.barrel(dev.tfp.core.device.automation.Face.UP));
        a.register(wide);
        AutoCloseable r = a.register(narrow);
        assertThat(a.find("tfp:wood/barrel/oak")).contains(narrow);
        assertThat(a.find("tfp:woodpile")).isEmpty();
        r.close();
        assertThat(a.find("tfp:wood/barrel/oak")).contains(wide);
    }
}
