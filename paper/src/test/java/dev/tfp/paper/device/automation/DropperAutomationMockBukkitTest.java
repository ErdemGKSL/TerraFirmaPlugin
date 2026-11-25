/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.device.automation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.device.automation.TfcSideTables;
import dev.tfp.core.persist.VersionedCodec;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-63: {@link DropperAutomationListener}: a dropper facing a device feeds it like a hopper from that side. */
class DropperAutomationMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:quern");
    private final HopperAutomationMockBukkitTest.MemoryStore store = new HopperAutomationMockBukkitTest.MemoryStore();
    private final HopperAutomationMockBukkitTest.TableCodec stacks = new HopperAutomationMockBukkitTest.TableCodec();
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

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private DeviceKind<HopperAutomationMockBukkitTest.Plain> quern;
    private DropperAutomationListener listener;
    private World world;
    private Inventory dropperInv;

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, m, args) -> switch (m.getName()) {
                case "isOwner" -> true;
                case "region" -> {
                    ((Runnable) args[args.length - 1]).run();
                    yield null;
                }
                default -> throw new UnsupportedOperationException(m.getName());
            });
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("dropper");
        DeviceService devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1_000L, stacks,
            new FakeCustomItems(), Logger.getLogger("test"));
        quern = devices.register(DeviceKind.builder(DeviceAutomationModule.QUERN, HopperAutomationMockBukkitTest.PLAIN,
            new HopperAutomationMockBukkitTest.Plain(0)).slots(3).slotLimit(slot -> slot == 0 ? 1 : 64)
            .filter((slot, stack) -> slot != 0 || stack.getType() == Material.STONE).build());
        DeviceAutomation automation = new DeviceAutomation();
        automation.register(DeviceAutomation.Spec.of(DeviceAutomationModule.QUERN, state -> TfcSideTables.quern())
            .withEnabled(enabled::get).withScreens(sync));
        dropperInv = server.createInventory(null, InventoryType.DROPPER);
        listener = new DropperAutomationListener(automation, new HopperMover(devices), blocks, scheduler(),
            b -> Optional.of(dropperInv), item -> {
                Inventory hand = server.createInventory(null, InventoryType.HOPPER);
                hand.setItem(0, item);
                return hand;
            }, Logger.getLogger("test"));
    }

    private Block quern(int x) {
        Block b = world.getBlockAt(x, 64, 0);
        b.setType(Material.BARRIER);
        blocks.place(b, BlockStateRef.of("tfp:quern"), false);
        store.add(b);
        return b;
    }

    /** A dropper at {@code device.getRelative(side)} facing the device. */
    private Block dropperAt(Block device, BlockFace side) {
        Block d = device.getRelative(side);
        d.setType(Material.DROPPER);
        Directional data = (Directional) Material.DROPPER.createBlockData();
        data.setFacing(side.getOppositeFace());
        d.setBlockData(data);
        return d;
    }

    private BlockDispenseEvent dispense(Block dropper, ItemStack item) {
        BlockDispenseEvent e = new BlockDispenseEvent(dropper, item, new Vector());
        listener.onDispense(e);
        return e;
    }

    @Test
    void aDropperFeedsTheQuernAndItsOwnStackShrinks() {
        Block q = quern(0);
        Block d = dropperAt(q, BlockFace.EAST);
        dropperInv.setItem(3, new ItemStack(Material.BONE, 5));
        assertThat(dispense(d, new ItemStack(Material.BONE)).isCancelled()).isTrue();
        assertThat(stacks.decode(quern.codec().decode(store.read(q).orElseThrow()).slot(1)))
            .isEqualTo(new ItemStack(Material.BONE));
        assertThat(dropperInv.getItem(3).getAmount()).isEqualTo(4);
    }

    @Test
    void aSingleItemEmptiesItsSlot() {
        Block q = quern(0);
        Block d = dropperAt(q, BlockFace.NORTH);
        dropperInv.setItem(0, new ItemStack(Material.BONE, 1));
        assertThat(dispense(d, new ItemStack(Material.BONE)).isCancelled()).isTrue();
        assertThat(dropperInv.getItem(0)).isNull();
    }

    @Test
    void whatTheDeviceRefusesIsVanillas() {
        Block q = quern(0);
        Block top = dropperAt(q, BlockFace.UP);
        dropperInv.setItem(0, new ItemStack(Material.BONE, 2));
        assertThat(dispense(top, new ItemStack(Material.BONE)).isCancelled()).as("no handler on top").isFalse();
        assertThat(dropperInv.getItem(0).getAmount()).isEqualTo(2);

        Block side = dropperAt(q, BlockFace.SOUTH);
        screen.set(true);
        assertThat(dispense(side, new ItemStack(Material.BONE)).isCancelled()).as("a screen is open").isFalse();
        screen.set(false);
        enabled.set(false);
        assertThat(dispense(side, new ItemStack(Material.BONE)).isCancelled()).as("automation off").isFalse();
        assertThat(dropperInv.getItem(0).getAmount()).isEqualTo(2);
    }

    @Test
    void aPlainBlockOrANonDropperIsLeftAlone() {
        Block stone = world.getBlockAt(10, 64, 0);
        stone.setType(Material.STONE);
        Block d = dropperAt(stone, BlockFace.WEST);
        assertThat(dispense(d, new ItemStack(Material.BONE)).isCancelled()).isFalse();
        Block dispenser = world.getBlockAt(12, 64, 0);
        dispenser.setType(Material.DISPENSER);
        assertThat(dispense(dispenser, new ItemStack(Material.BONE)).isCancelled()).isFalse();
    }
}
