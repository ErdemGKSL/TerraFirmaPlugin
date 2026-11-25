/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device.automation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-64: {@link HopperMinecartAutomation#step} for a hopper minecart one block under a device: it empties the
 * device through the bottom face with the hopper's gates, never fills it, and a disabled cart or a disabled switch moves
 * nothing.
 */
class HopperMinecartAutomationMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:quern", "tfp:firepit");
    private final HopperAutomationMockBukkitTest.MemoryStore store = new HopperAutomationMockBukkitTest.MemoryStore();
    private final HopperAutomationMockBukkitTest.TableCodec stacks = new HopperAutomationMockBukkitTest.TableCodec();
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

    private final AtomicReference<AutomationRules.Switches> switches =
        new AtomicReference<>(AutomationRules.Switches.TFC_DEFAULTS);
    private DeviceService devices;
    private DeviceKind<HopperAutomationMockBukkitTest.Plain> quern;
    private World world;
    private HopperMinecartAutomation automation;

    /** A cart standing one block under {@code y}, with its own inventory. */
    private static final class TestCart implements HopperMinecartAutomation.Cart {
        final Location at;
        final Inventory inventory;
        boolean enabled = true;

        TestCart(Location at, Inventory inventory) {
            this.at = at;
            this.inventory = inventory;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public Location location() {
            return at;
        }

        @Override
        public Inventory inventory() {
            return inventory;
        }
    }

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, m, args) -> {
                if (m.getName().equals("isOwner")) {
                    return owner.get();
                }
                throw new UnsupportedOperationException(m.getName());
            });
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("minecart");
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1_000L, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        quern = devices.register(DeviceKind.builder(DeviceAutomationModule.QUERN, HopperAutomationMockBukkitTest.PLAIN,
            new HopperAutomationMockBukkitTest.Plain(0)).slots(3).slotLimit(slot -> slot == 0 ? 1 : 64).build());
        DeviceAutomation registry = new DeviceAutomation();
        DeviceAutomationModule.registerDefaults(registry, switches::get, new ArrayList<>(), kind -> sync,
            DeviceAutomation.Settle.NONE);
        automation = new HopperMinecartAutomation(registry, new HopperMover(devices), blocks, scheduler(),
            Logger.getLogger("test"));
    }

    private Block quernAt(int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        b.setType(Material.BARRIER);
        blocks.place(b, BlockStateRef.of("tfp:quern"), false);
        store.add(b);
        return b;
    }

    private TestCart cartUnder(Block device) {
        // a minecart on a rail one block under the device: its y is the rail block's y plus a little
        return new TestCart(new Location(world, device.getX() + 0.5, device.getY() - 1 + 0.0625, device.getZ() + 0.5),
            server.createInventory(null, InventoryType.HOPPER));
    }

    private ItemStack output(Block q) {
        DeviceRecord<HopperAutomationMockBukkitTest.Plain> r = quern.codec().decode(store.read(q).orElseThrow());
        return stacks.decode(r.slot(2));
    }

    private void fillOutput(Block q, ItemStack stack) {
        devices.edit(q, quern, (r, now) -> r.withSlot(2, stacks.encode(stack)));
    }

    @Test
    void aCartUnderAQuernEmptiesItsOutputOneItemAtATime() {
        Block q = quernAt(4, 64, 0);
        fillOutput(q, new ItemStack(Material.BONE_MEAL, 3));
        TestCart cart = cartUnder(q);
        assertThat(automation.step(cart)).isTrue();
        assertThat(cart.inventory.getItem(0)).isEqualTo(new ItemStack(Material.BONE_MEAL));
        assertThat(output(q).getAmount()).isEqualTo(2);
        assertThat(automation.step(cart)).isTrue();
        assertThat(cart.inventory.getItem(0).getAmount()).isEqualTo(2);
        assertThat(output(q).getAmount()).isEqualTo(1);
    }

    @Test
    void aCartNeverFillsTheDeviceAboveIt() {
        Block q = quernAt(4, 64, 0);
        TestCart cart = cartUnder(q);
        cart.inventory.setItem(0, new ItemStack(Material.BONE, 4));
        assertThat(automation.step(cart)).as("nothing to take: a minecart does not push").isFalse();
        assertThat(cart.inventory.getItem(0).getAmount()).isEqualTo(4);
    }

    @Test
    void aDisabledCartTheSwitchAScreenOrAnotherRegionMoveNothing() {
        Block q = quernAt(4, 64, 0);
        fillOutput(q, new ItemStack(Material.BONE_MEAL, 3));
        TestCart cart = cartUnder(q);
        cart.enabled = false;
        assertThat(automation.step(cart)).as("activator rail").isFalse();
        cart.enabled = true;
        switches.set(new AutomationRules.Switches(true, true, false, true, true));
        assertThat(automation.step(cart)).as("quern automation off").isFalse();
        switches.set(AutomationRules.Switches.TFC_DEFAULTS);
        screen.set(true);
        assertThat(automation.step(cart)).as("an open TFP screen").isFalse();
        screen.set(false);
        owner.set(false);
        assertThat(automation.step(cart)).as("another region owns the device").isFalse();
        owner.set(true);
        assertThat(output(q).getAmount()).as("nothing was taken").isEqualTo(3);
        assertThat(automation.step(cart)).isTrue();
    }

    @Test
    void aCartNextToAPlainBlockOrADifferentBlockAboveDoesNothing() {
        Block stone = world.getBlockAt(10, 65, 0);
        stone.setType(Material.STONE);
        TestCart cart = new TestCart(new Location(world, 10.5, 64.0625, 0.5),
            server.createInventory(null, InventoryType.HOPPER));
        assertThat(automation.step(cart)).isFalse();
        Block air = world.getBlockAt(20, 65, 0);
        air.setType(Material.AIR);
        assertThat(automation.step(new TestCart(new Location(world, 20.5, 64.0625, 0.5),
            server.createInventory(null, InventoryType.HOPPER)))).isFalse();
    }
}
