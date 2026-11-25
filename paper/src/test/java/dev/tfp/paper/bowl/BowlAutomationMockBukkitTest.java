/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bowl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.automation.AutomationRules;
import dev.tfp.core.device.automation.Face;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.device.automation.DeviceAutomation;
import dev.tfp.paper.device.automation.HopperAutomationListener;
import dev.tfp.paper.device.automation.HopperMover;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.core.device.ItemBlob;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * PAR-POT-15: hoppers on the powder bowls (TFC {@code powderBowlEnableAutomation}): a hopper above fills the slot up to 16,
 * a hopper beside or below empties it, the switch off refuses both, and the powder layer is redrawn after each move.
 */
class BowlAutomationMockBukkitTest extends MockServerTest {
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

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(BowlDevices.CERAMIC, BowlDevices.WOODEN);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Map<String, ItemStack> shown = new HashMap<>();
    private World world;
    private DeviceService devices;
    private Port port;
    private HopperAutomationListener listener;

    private final BowlEffects effects = new BowlEffects() {
        @Override
        public void show(Block block, ItemStack content) {
            shown.put(key(block), content == null ? ItemStack.empty() : content.clone());
        }

        @Override
        public void hide(Block block) {
            shown.remove(key(block));
        }

        @Override
        public void sand(Block block) {
        }

        @Override
        public void placed(Block block, boolean wooden) {
        }
    };

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("bowl_automation");
        CustomItems items = new FakeCustomItems();
        devices = new DeviceService(new DeviceKinds(), blocks, () -> store, () -> 1_000L, stacks, items, Logger.getLogger("test"));
        new BowlDevices((s, tag) -> s.getType() == Material.REDSTONE || s.getType() == Material.GUNPOWDER,
            items, effects, new BowlFood() {
                @Override
                public boolean canSalt(ItemStack stack) {
                    return false;
                }

                @Override
                public void salt(ItemStack stack) {
                }
            }, BowlGlass.NONE).kinds().forEach(devices::register);
        DeviceAutomation automation = new DeviceAutomation();
        automation.register(BowlAutomation.spec(BowlDevices.CERAMIC, enabled::get, effects, stacks));
        automation.register(BowlAutomation.spec(BowlDevices.WOODEN, enabled::get, effects, stacks));
        port = new Port(server.createInventory(null, InventoryType.HOPPER));
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
            new Class<?>[] {Scheduler.class}, (proxy, m, args) -> {
                if (m.getName().equals("isOwner")) {
                    return true;
                }
                throw new UnsupportedOperationException(m.getName());
            });
        listener = new HopperAutomationListener(automation, new HopperMover(devices), blocks, scheduler,
            b -> Optional.of(port), Logger.getLogger("test"));
    }

    private Block bowl(String id) {
        Block b = world.getBlockAt(0, 64, 0);
        b.setType(Material.BARRIER);
        blocks.place(b, BlockStateRef.of(id), true);
        store.data.put(FakeCustomBlocks.at(b), new DataMap());
        return b;
    }

    private boolean search(Block hopper, Block device, HopperInventorySearchEvent.ContainerType type) {
        hopper.setType(Material.HOPPER);
        port.cooldown = -1;
        HopperInventorySearchEvent e = new HopperInventorySearchEvent(null, type, hopper, device);
        listener.onSearch(e);
        return port.cooldown == AutomationRules.HOPPER_COOLDOWN;
    }

    private ItemStack content(Block b) {
        return stacks.decode(devices.catchUp(b).orElseThrow().slot(0));
    }

    @Test
    void sidesAreInsertFromAboveAndExtractFromEveryOtherSide() {
        var t = BowlAutomation.sides();
        assertThat(t.insertSlots(Face.UP)).containsExactly(0);
        assertThat(t.extractSlots(Face.UP)).isEmpty();
        for (Face f : Face.values()) {
            if (f != Face.UP) {
                assertThat(t.insertSlots(f)).as(f.name()).isEmpty();
                assertThat(t.extractSlots(f)).as(f.name()).containsExactly(0);
            }
        }
    }

    @Test
    void aHopperAboveFillsTheBowlUpToSixteenAndTheLayerFollows() {
        Block b = bowl(BowlDevices.CERAMIC);
        port.inventory.setItem(0, new ItemStack(Material.REDSTONE, 20));
        Block above = b.getRelative(0, 1, 0);
        for (int i = 1; i <= 16; i++) {
            assertThat(search(above, b, HopperInventorySearchEvent.ContainerType.DESTINATION)).as("move " + i).isTrue();
            assertThat(content(b).getAmount()).isEqualTo(i);
            assertThat(shown.get(key(b)).getAmount()).as("layer after move " + i).isEqualTo(i);
        }
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(4);
        assertThat(search(above, b, HopperInventorySearchEvent.ContainerType.DESTINATION)).as("full").isFalse();
        assertThat(content(b).getAmount()).isEqualTo(16);
    }

    @Test
    void aNonPowderIsRefused() {
        Block b = bowl(BowlDevices.WOODEN);
        port.inventory.setItem(0, new ItemStack(Material.STONE, 4));
        assertThat(search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        assertThat(content(b).getType().isAir()).isTrue();
    }

    @Test
    void aHopperBesideOrBelowEmptiesTheBowlAndTheLayerGoesWhenEmpty() {
        Block b = bowl(BowlDevices.WOODEN);
        port.inventory.setItem(0, new ItemStack(Material.GUNPOWDER, 2));
        search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION);
        search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION);
        assertThat(content(b).getAmount()).isEqualTo(2);
        assertThat(shown.get(key(b)).getAmount()).isEqualTo(2);
        port.inventory.clear();

        assertThat(search(b.getRelative(1, 0, 0), b, HopperInventorySearchEvent.ContainerType.SOURCE)).isTrue();
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(1);
        assertThat(content(b).getAmount()).isEqualTo(1);
        assertThat(shown.get(key(b)).getAmount()).isEqualTo(1);

        assertThat(search(b.getRelative(0, -1, 0), b, HopperInventorySearchEvent.ContainerType.SOURCE)).isTrue();
        assertThat(port.inventory.getItem(0).getAmount()).isEqualTo(2);
        assertThat(content(b).getType().isAir()).isTrue();
        assertThat(shown.get(key(b)).getType().isAir()).as("layer removed").isTrue();
    }

    @Test
    void theSwitchOffRefusesBothWays() {
        Block b = bowl(BowlDevices.CERAMIC);
        port.inventory.setItem(0, new ItemStack(Material.REDSTONE, 4));
        search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION);
        assertThat(content(b).getAmount()).isEqualTo(1);
        enabled.set(false);
        assertThat(search(b.getRelative(0, 1, 0), b, HopperInventorySearchEvent.ContainerType.DESTINATION)).isFalse();
        port.inventory.clear();
        assertThat(search(b.getRelative(1, 0, 0), b, HopperInventorySearchEvent.ContainerType.SOURCE)).isFalse();
        assertThat(content(b).getAmount()).isEqualTo(1);
    }
}
