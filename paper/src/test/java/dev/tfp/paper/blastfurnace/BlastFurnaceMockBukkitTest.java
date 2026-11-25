/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.blastfurnace.BlastFurnaceData;
import dev.tfp.core.blastfurnace.BlastFurnaceRecipe;
import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.ItemHeatHooks;
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
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-METAL-15 on MockBukkit with fakes of the CraftEngine adapter, the world effects and the container below: the chimney
 * scan and the capacity, the intake of dropped items, lighting, the lazy burn to pig iron, the drip rate, the halt when the
 * chimney shrinks, the air and the slot rules. Charcoal (1800 ticks, 1350 C) burns, raw iron melts at 1000 C into 100 mB of
 * cast iron, gunpowder is the flux.
 */
class BlastFurnaceMockBukkitTest extends MockServerTest {
    static final String CAST_IRON = "tfp:metal/cast_iron";
    static final String PIG_IRON = "tfp:metal/pig_iron";
    static final BlastFurnaceRecipe RECIPE = new BlastFurnaceRecipe(CAST_IRON, 1, "minecraft:gunpowder", PIG_IRON, 1);
    static final Fuel CHARCOAL = new Fuel("minecraft:charcoal", 1800, 1350f, 1f);
    static final CrucibleRecipe IRON_MELT = new CrucibleRecipe("minecraft:raw_iron", 1000f, CAST_IRON, 100, false, false);

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

    static final class Drop implements BlastFurnaceEffects.Drop {
        final ItemStack stack;

        Drop(Material m, int n) {
            this.stack = new ItemStack(m, n);
        }

        @Override
        public ItemStack stack() {
            return stack;
        }

        @Override
        public int count() {
            return stack.getAmount();
        }

        @Override
        public void take(int n) {
            stack.setAmount(stack.getAmount() - n);
        }
    }

    final class Effects implements BlastFurnaceEffects {
        final List<Drop> world = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Boolean> shown = new ArrayList<>();
        final List<Long> callbacks = new ArrayList<>();
        final List<int[]> towers = new ArrayList<>();
        int moltenRemovals;

        @Override
        public boolean owns(Block block) {
            return true;
        }

        @Override
        public void show(Block furnace, boolean lit) {
            shown.add(lit);
            blocks.place(furnace, BlastFurnaceDevice.state(lit), false);
        }

        @Override
        public void callbackIn(Block furnace, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public List<BlastFurnaceEffects.Drop> dropped(Block furnace, int chimneyLevels) {
            List<BlastFurnaceEffects.Drop> out = new ArrayList<>();
            for (Drop d : world) {
                if (d.count() > 0) {
                    out.add(d);
                }
            }
            return out;
        }

        @Override
        public void spawn(Block at, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void molten(Block base, int[] layers, boolean lit) {
            towers.add(layers.clone());
        }

        @Override
        public void removeMolten(Block furnace, int maxHeight) {
            moltenRemovals++;
        }
    }

    final class Below implements BlastFurnaceBelow {
        int room = 4000;
        int given;
        float temperature;
        long lastNow;

        @Override
        public int accepts(Block furnace, String fluid) {
            return room - given;
        }

        @Override
        public void give(Block furnace, String fluid, int amount, float temperature, long now) {
            given += amount;
            this.temperature = temperature;
            lastNow = now;
        }
    }

    final FakeCustomBlocks blocks = new FakeCustomBlocks().known(BlastFurnaceDevice.ID, PaperBlastFurnaceProbe.MOLTEN);
    final MemoryStore store = new MemoryStore();
    final TableCodec codec = new TableCodec();
    final Effects effects = new Effects();
    final Below below = new Below();
    final AtomicLong clock = new AtomicLong(100_000);
    DeviceService service;
    BlastFurnaceDevice device;
    World world;
    Block furnace;
    BlastFurnaceRules.Config config = BlastFurnaceRules.Config.DEFAULT;

    final BlastFurnaceProbe probe = new BlastFurnaceProbe() {
        @Override
        public boolean readable(Block block) {
            return true;
        }

        @Override
        public boolean insulation(Block block) {
            return block.getType() == Material.STONE;
        }

        @Override
        public boolean centre(Block block) {
            return block.getType().isAir();
        }
    };

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("blast");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        BlastFurnaceDevice.Services services = new BlastFurnaceDevice.Services(
            stack -> Optional.of("minecraft:" + stack.getType().name().toLowerCase()),
            stack -> stack.getType() == Material.RAW_IRON ? Optional.of(IRON_MELT) : Optional.empty(),
            stack -> stack.getType() == Material.CHARCOAL ? Optional.of(CHARCOAL) : Optional.empty(),
            stack -> stack.getType() == Material.CHARCOAL,
            stack -> stack.getType() == Material.BLAZE_ROD, // the stand-in tuyere
            stack -> stack.getType() == Material.RAW_IRON ? 0.5f : 0f,
            HeatConfig.DEFAULT, () -> List.of(RECIPE), () -> config, probe, effects, below, blocks::stateAt);
        device = new BlastFurnaceDevice(services, codec);
        service.register(device.kind());
        furnace = world.getBlockAt(10, 64, 10);
        build(5);
    }

    /** The furnace at (10, 64, 10) with {@code levels} formed chimney levels above it. */
    void build(int levels) {
        for (int y = 1; y <= 8; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        furnace.getRelative(dx, y, dz).setType(y <= levels ? Material.STONE : Material.AIR);
                    }
                }
            }
        }
        if (!store.data.containsKey(FakeCustomBlocks.at(furnace))) {
            blocks.place(furnace, BlastFurnaceDevice.state(false), true);
            store.data.put(FakeCustomBlocks.at(furnace), new DataMap());
        }
    }

    void drop(Material m, int n) {
        if (n > 0) {
            effects.world.add(new Drop(m, n));
        }
    }

    DeviceRecord<BlastFurnaceData> record() {
        return device.kind().codec().decode(store.read(furnace).orElseThrow());
    }

    void tick() {
        service.neighbourChanged(furnace);
    }

    int count(int from) {
        return device.group(record(), from).size();
    }

    /** Loads the chimney and runs the device once. */
    void load(int ore, int flux, int fuel) {
        drop(Material.RAW_IRON, ore);
        drop(Material.GUNPOWDER, flux);
        drop(Material.CHARCOAL, fuel);
        tick();
    }

    // ---- intake and capacity ---------------------------------------------------------------------------------

    @Test
    void theChimneyTakesItemsUpToItsCapacity() {
        load(30, 30, 30);
        assertThat(count(BlastFurnaceDevice.INPUT)).isEqualTo(20); // 5 levels x 4
        assertThat(count(BlastFurnaceDevice.CATALYST)).isEqualTo(20);
        assertThat(count(BlastFurnaceDevice.FUEL)).isEqualTo(20);
        assertThat(effects.world.get(0).count()).isEqualTo(10); // what did not fit stays in the world
    }

    @Test
    void aShorterChimneyHoldsLess() {
        build(2);
        load(30, 30, 30);
        assertThat(count(BlastFurnaceDevice.INPUT)).isEqualTo(8);
        assertThat(count(BlastFurnaceDevice.FUEL)).isEqualTo(8);
    }

    @Test
    void withoutAChimneyNothingIsTaken() {
        build(0);
        load(5, 5, 5);
        assertThat(count(BlastFurnaceDevice.INPUT)).isZero();
        assertThat(count(BlastFurnaceDevice.FUEL)).isZero();
    }

    @Test
    void inputsAndFluxAreTakenInPairsAndFuelFollowsTheRecipe() {
        load(4, 0, 4);
        assertThat(count(BlastFurnaceDevice.INPUT)).isZero(); // TFC takes inputs and catalysts in pairs
        assertThat(count(BlastFurnaceDevice.FUEL)).isEqualTo(4); // the ore in the chimney picked the recipe
    }

    @Test
    void theMoltenTowerShowsInputsAndFuel() {
        load(8, 8, 8);
        // 16 units, 2 x 4 per layer: 4 x 16 / 8 = 8 layers over two blocks
        assertThat(effects.towers.getLast()).containsExactly(4, 4, 0, 0, 0);
    }

    @Test
    void theChimneyBreakingPopsTheItemsAboveTheCapacityAndPutsItOut() {
        load(20, 20, 20);
        assertThat(device.lightAt(service, furnace)).isTrue();
        build(1);
        tick();
        assertThat(count(BlastFurnaceDevice.INPUT)).isEqualTo(4);
        assertThat(count(BlastFurnaceDevice.FUEL)).isEqualTo(4);
        assertThat(effects.spawned).hasSize(48); // 16 inputs, 16 catalysts, 16 fuel
        assertThat(record().state().lit()).isFalse();
        assertThat(blocks.stateAt(furnace).orElseThrow().properties()).containsEntry("lit", "false");
    }

    // ---- lighting and the burn ---------------------------------------------------------------------------------

    @Test
    void aFurnaceWithoutFuelDoesNotLight() {
        assertThat(device.lightAt(service, furnace)).isFalse();
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void lightingShowsTheBlockAndQueuesACallback() {
        load(1, 1, 4);
        assertThat(device.lightAt(service, furnace)).isTrue();
        assertThat(BlastFurnaceDevice.lit(blocks.stateAt(furnace).orElseThrow())).isTrue();
        assertThat(effects.callbacks).contains(BlastFurnaceDevice.PERIOD);
    }

    @Test
    void theFuelBurnsLazilyAtTheMultiplierAndThenTheFurnaceGoesOut() {
        load(1, 1, 2);
        device.lightAt(service, furnace);
        clock.addAndGet(100);
        tick();
        assertThat(record().state().state().lit()).isTrue();
        assertThat(count(BlastFurnaceDevice.FUEL)).isEqualTo(1); // one charcoal taken, 1800 ticks / 4 per tick left
        clock.addAndGet(2 * 450 + 5);
        tick();
        assertThat(record().state().lit()).isFalse();
        assertThat(count(BlastFurnaceDevice.FUEL)).isZero();
        assertThat(blocks.stateAt(furnace).orElseThrow().properties()).containsEntry("lit", "false");
    }

    @Test
    void oreMeltsIntoPigIronThatDripsIntoTheContainerBelowAtOneMbPerTick() {
        load(2, 2, 12);
        device.lightAt(service, furnace);
        clock.addAndGet(1500);
        tick();
        BlastFurnaceData d = record().state();
        int made = d.output().amount() + below.given;
        assertThat(made).isEqualTo(200); // two ores, 100 mB of cast iron each, one to one into pig iron
        assertThat(d.output().isEmpty() || d.output().fluid().equals(PIG_IRON)).isTrue();
        assertThat(below.given).isLessThanOrEqualTo(1500).isPositive();
        assertThat(count(BlastFurnaceDevice.INPUT)).isZero();
        assertThat(below.temperature).isPositive();
        clock.addAndGet(1000);
        tick();
        assertThat(below.given).isEqualTo(200);
        assertThat(record().state().output().isEmpty()).isTrue();
    }

    @Test
    void aFullContainerKeepsTheOutputInTheTank() {
        below.room = 50;
        load(2, 2, 12);
        device.lightAt(service, furnace);
        clock.addAndGet(3000);
        tick();
        assertThat(below.given).isEqualTo(50);
        assertThat(record().state().output().amount()).isEqualTo(150);
    }

    @Test
    void anUnhotFurnaceDoesNotMelt() {
        load(1, 1, 4);
        clock.addAndGet(5000);
        tick();
        assertThat(count(BlastFurnaceDevice.INPUT)).isEqualTo(1);
        assertThat(record().state().output().isEmpty()).isTrue();
    }

    // ---- air and the tuyere --------------------------------------------------------------------------------------

    @Test
    void airNeedsATuyereAndIsCapped() {
        assertThat(device.canAcceptAir(service, furnace)).isFalse();
        service.edit(furnace, device.kind(), (r, now) -> r.withSlot(BlastFurnaceDevice.TUYERE,
            codec.encode(new ItemStack(Material.BLAZE_ROD))));
        assertThat(device.canAcceptAir(service, furnace)).isTrue();
        device.intakeAir(service, furnace, 200);
        assertThat(record().state().state().airTicks()).isEqualTo(200);
        device.intakeAir(service, furnace, 900);
        assertThat(record().state().state().airTicks()).isEqualTo(600);
    }

    @Test
    void onlyATuyereGoesIntoSlotZero() {
        var filter = device.kind().filter();
        assertThat(filter.canInsert(BlastFurnaceDevice.TUYERE, new ItemStack(Material.BLAZE_ROD))).isTrue();
        assertThat(filter.canInsert(BlastFurnaceDevice.TUYERE, new ItemStack(Material.CHARCOAL))).isFalse();
        assertThat(filter.canInsert(BlastFurnaceDevice.FUEL, new ItemStack(Material.BLAZE_ROD))).isFalse();
    }

    @Test
    void theTuyereWearsWhileTheAirBlowsOnAHotFurnace() {
        load(4, 4, 8);
        service.edit(furnace, device.kind(), (r, now) -> r.withSlot(BlastFurnaceDevice.TUYERE,
            codec.encode(new ItemStack(Material.IRON_PICKAXE))));
        device.lightAt(service, furnace);
        clock.addAndGet(1000); // hot above 400 C
        tick();
        device.intakeAir(service, furnace, 600);
        clock.addAndGet(700);
        tick();
        ItemStack tuyere = device.tuyere(record());
        assertThat(((org.bukkit.inventory.meta.Damageable) tuyere.getItemMeta()).getDamage()).isPositive();
    }

    @Test
    void breakingTheFurnaceRemovesTheMoltenTower() {
        load(4, 4, 4);
        blocks.remove(furnace);
        service.removed(world, 10, 64, 10, BlastFurnaceDevice.ID, store.data.get(FakeCustomBlocks.at(furnace)));
        assertThat(effects.moltenRemovals).isPositive();
    }

    @Test
    void configFallsBackWhenTheProductExceedsTheSlots() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set(BlastFurnaceModule.CAPACITY_KEY, 16);
        yaml.set(BlastFurnaceModule.HEIGHT_KEY, 5);
        yaml.set(BlastFurnaceModule.MULTIPLIER_KEY, 0);
        BlastFurnaceRules.Config c = BlastFurnaceModule.config(yaml, Logger.getLogger("test"));
        assertThat(c).isEqualTo(BlastFurnaceRules.Config.DEFAULT);
        yaml.set(BlastFurnaceModule.CAPACITY_KEY, 6);
        yaml.set(BlastFurnaceModule.HEIGHT_KEY, 5);
        yaml.set(BlastFurnaceModule.MULTIPLIER_KEY, 2);
        yaml.set(BlastFurnaceModule.FLUID_KEY, 500);
        assertThat(BlastFurnaceModule.config(yaml, Logger.getLogger("test"))).isEqualTo(new BlastFurnaceRules.Config(6, 5, 500, 2));
    }

    @Test
    void blockStatesFollowTheLitProperty() {
        assertThat(BlastFurnaceDevice.state(true).properties()).containsEntry("lit", "true");
        BlockStateRef s = BlastFurnaceDevice.state(false);
        assertThat(s.id()).isEqualTo(BlastFurnaceDevice.ID);
        assertThat(BlastFurnaceDevice.lit(s)).isFalse();
    }
}
