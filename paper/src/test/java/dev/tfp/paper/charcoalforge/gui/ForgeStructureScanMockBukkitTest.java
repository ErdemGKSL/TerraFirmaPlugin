/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.charcoalforge.gui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.charcoalforge.CharcoalForgeDevice;
import dev.tfp.paper.charcoalforge.CharcoalForgeEffects;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-METAL-09: the structure scan puts a lit forge out when its insulation or chimney is gone (TFC {@code updateShape} and
 * {@code randomTick}), and the screen link round-trips the fourteen slots over the device record.
 */
class ForgeStructureScanMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final Fuel CHARCOAL = new Fuel("minecraft:charcoal", 1800, 1350f, 1f);

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

    static final class Effects implements CharcoalForgeEffects {
        Integer shown;
        final List<Long> callbacks = new ArrayList<>();

        @Override
        public void show(Block block, int heatLevel) {
            shown = heatLevel;
        }

        @Override
        public void give(Player player, ItemStack stack) {
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(CharcoalForgeDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec stacks = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private final AtomicBoolean valid = new AtomicBoolean(true);
    private DeviceService service;
    private CharcoalForgeDevice device;
    private DeviceKind<CharcoalForgeData> kind;
    private Block forge;

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("forgescan");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, stacks, new FakeCustomItems(),
            Logger.getLogger("test"));
        ItemHeatHooks.Capacities capacities = s -> s.getType() == Material.IRON_INGOT ? 0.5f : 0f;
        device = new CharcoalForgeDevice(new CharcoalForgeDevice.Services(
            s -> s.getType() == Material.CHARCOAL ? Optional.of(CHARCOAL) : Optional.empty(),
            s -> s.getType() == Material.CHARCOAL, capacities, s -> false,
            new CharcoalForgeDevice.Converter() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return Float.NaN;
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float temperature) {
                    return Optional.empty();
                }
            }, HeatConfig.DEFAULT, b -> false, new HeatConsumers(), b -> blocks.stateAt(b).map(BlockStateRef::id), effects),
            stacks);
        kind = service.register(device.kind());
        device.structure(b -> valid.get());
        forge = world.getBlockAt(0, 64, 0);
        blocks.place(forge, CharcoalForgeDevice.state(0), true);
        store.data.put(FakeCustomBlocks.at(forge), new DataMap());
    }

    private DeviceRecord<CharcoalForgeData> record() {
        return kind.codec().decode(store.read(forge).orElseThrow());
    }

    @Test
    void aLitForgeWithABrokenStructureIsPutOutOnTheNextNeighbourChange() {
        service.edit(forge, kind, (r, now) -> r.withSlot(0, stacks.encode(new ItemStack(Material.CHARCOAL))));
        service.edit(forge, kind, (r, now) -> r.withSlot(5, stacks.encode(new ItemStack(Material.IRON_INGOT))));
        assertThat(device.lightAt(service, forge)).isTrue();
        clock.set(START + 100);
        service.neighbourChanged(forge);
        assertThat(record().state().state().lit()).as("valid: stays lit").isTrue();
        blocks.place(forge, CharcoalForgeDevice.state(effects.shown), true); // what the real effects wrote
        valid.set(false); // a side block was broken
        clock.set(START + 110);
        service.neighbourChanged(forge);
        CharcoalForgeData d = record().state();
        assertThat(d.state().lit()).isFalse();
        assertThat(d.state().burnTicks()).isZero();
        assertThat(d.state().burnTemperature()).isZero();
        assertThat(d.temperature()).as("the forge cools, it does not vanish cold").isGreaterThan(0f);
        assertThat(effects.shown).isZero();
        assertThat(stacks.decode(record().slot(5)).getType()).as("the input stays").isEqualTo(Material.IRON_INGOT);
        // out for good: later accesses keep it out and it cools
        clock.set(START + 5000);
        service.neighbourChanged(forge);
        assertThat(record().state().state().lit()).isFalse();
        assertThat(record().state().temperature()).isLessThan(d.temperature());
    }

    @Test
    void anUnlitForgeIsNotAffectedAndAValidOneIsNotPutOut() {
        valid.set(false);
        service.neighbourChanged(forge);
        assertThat(record().state().state().lit()).isFalse();
        valid.set(true);
        device.createFromPile(service, forge);
        clock.set(START + 50);
        service.neighbourChanged(forge);
        assertThat(record().state().state().lit()).isTrue();
    }

    @Test
    void aLitForgeRechecksItsStructureAtLeastEveryMinute() {
        device.createFromPile(service, forge);
        service.edit(forge, kind, (r, now) -> r.withSlot(0, stacks.encode(new ItemStack(Material.CHARCOAL))));
        service.edit(forge, kind, (r, now) -> r.withState(r.state().withState(
            new dev.tfp.core.charcoalforge.CharcoalForgeState(true, 100_000, 1350f, 1350f, 0))));
        effects.callbacks.clear();
        clock.set(START + 10);
        service.neighbourChanged(forge);
        assertThat(effects.callbacks).isNotEmpty();
        assertThat(effects.callbacks.getLast()).isLessThanOrEqualTo(CharcoalForgeDevice.STRUCTURE_PERIOD);
    }

    @Test
    void theScreenLinkReadsAndStoresTheFourteenSlots() {
        Scheduler scheduler = (Scheduler) java.lang.reflect.Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
            new Class<?>[] {Scheduler.class}, (proxy, method, args) -> {
                if (method.getName().equals("isOwner")) {
                    return true;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        ForgeScreenLink link = new ForgeScreenLink(new ForgeScreenLink.Env(service, device, scheduler), forge);
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < CharcoalForgeSim.SLOTS; i++) {
            slots.add(ItemStack.empty());
        }
        slots.set(0, new ItemStack(Material.CHARCOAL));
        slots.set(5, new ItemStack(Material.IRON_INGOT));
        ForgeSnapshot after = link.commit(slots).orElseThrow();
        assertThat(after.slot(0).getType()).isEqualTo(Material.CHARCOAL);
        assertThat(after.slot(5).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(after.slot(1).isEmpty()).isTrue();
        assertThat(stacks.decode(record().slot(5)).getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(link.snapshot().orElseThrow().temperature()).isZero();
        assertThat(link.isItemValid(0, new ItemStack(Material.CHARCOAL))).isTrue();
        assertThat(link.isItemValid(0, new ItemStack(Material.IRON_INGOT))).isFalse();
        assertThat(link.isItemValid(5, new ItemStack(Material.IRON_INGOT))).isTrue();
    }
}
