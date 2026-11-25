/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bloomery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bloomery.BloomData;
import dev.tfp.core.bloomery.BloomeryData;
import dev.tfp.core.bloomery.BloomeryRecipe;
import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-METAL-14 on MockBukkit with fakes: the chimney scan, the intake of dropped items, the lit phase to the bloom, the
 * halt when the structure shrinks, the door, the gate, the molten tower and the bloom block.
 */
class BloomeryMockBukkitTest extends MockServerTest {
    static final String IRON = "tfp:metal/cast_iron";
    static final BloomeryRecipe RECIPE = new BloomeryRecipe(IRON, 100, "minecraft:charcoal", 2, "tfp:raw_iron_bloom", 14_400);

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

    /** A stack as a blob: the material name and the amount. */
    static final class Codec implements StackCodec {
        @Override
        public ItemBlob encode(ItemStack stack) {
            return StackCodec.isEmpty(stack) ? ItemBlob.EMPTY
                : ItemBlob.of((stack.getType().name() + ":" + stack.getAmount()).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public ItemStack decode(ItemBlob blob) {
            String[] p = new String(blob.bytes(), StandardCharsets.UTF_8).split(":");
            return new ItemStack(Material.valueOf(p[0]), Integer.parseInt(p[1]));
        }
    }

    final class Drop implements BloomeryEffects.Drop {
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

    final class Effects implements BloomeryEffects {
        final List<Drop> world = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Block> spawnedAt = new ArrayList<>();
        final List<String> shown = new ArrayList<>();
        final List<Long> callbacks = new ArrayList<>();
        final List<int[]> towers = new ArrayList<>();
        final List<Boolean> towerLit = new ArrayList<>();
        final List<String> blooms = new ArrayList<>();
        final List<Integer> bloomLayers = new ArrayList<>();
        final List<Block> removedBlooms = new ArrayList<>();
        final List<Block> brokenGates = new ArrayList<>();
        int moltenRemovals;
        int sounds;

        @Override
        public boolean owns(Block block) {
            return true;
        }

        @Override
        public void show(Block gate, String facing, boolean lit, boolean open) {
            shown.add(facing + "," + lit + "," + open);
            blocks.place(gate, BloomeryDevice.state(facing, lit, open), false);
        }

        @Override
        public void doorSound(Block gate, boolean open) {
            sounds++;
        }

        @Override
        public void callbackIn(Block gate, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public List<BloomeryEffects.Drop> dropped(Block inside, int chimneyLevels) {
            List<BloomeryEffects.Drop> out = new ArrayList<>();
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
            spawnedAt.add(at);
        }

        @Override
        public void molten(Block inside, int[] layers, boolean lit) {
            towers.add(layers.clone());
            towerLit.add(lit);
        }

        @Override
        public void removeMolten(Block gate, int maxHeight) {
            moltenRemovals++;
        }

        @Override
        public void placeBloom(Block inside, ItemStack item, int count) {
            blooms.add(item.getType().name() + "x" + count);
            blocks.place(inside, BlockStateRef.of(BloomeryWorld.BLOOM).with("layers",
                Integer.toString(BloomData.made(count).layers())), false);
            store.data.put(FakeCustomBlocks.at(inside), new DataMap());
            bloom.seed(service, inside, item, count);
        }

        @Override
        public void showBloom(Block b, int layers) {
            bloomLayers.add(layers);
        }

        @Override
        public void removeBloom(Block b) {
            removedBlooms.add(b);
            blocks.remove(b);
        }

        @Override
        public void breakGate(Block gate) {
            brokenGates.add(gate);
        }
    }

    final FakeCustomBlocks blocks = new FakeCustomBlocks().known(BloomeryDevice.ID, BloomeryWorld.BLOOM, BloomeryWorld.MOLTEN);
    final MemoryStore store = new MemoryStore();
    final Effects effects = new Effects();
    final AtomicLong clock = new AtomicLong(100_000);
    DeviceService service;
    BloomeryDevice device;
    BloomDevice bloom;
    World world;
    Block inside;
    Block gate;
    PlayerMock player;
    BloomeryRules.Config config = BloomeryRules.Config.DEFAULT;

    final BloomeryProbe probe = new BloomeryProbe() {
        @Override
        public boolean readable(Block block) {
            return true;
        }

        @Override
        public Optional<String> blockId(Block block) {
            return blocks.stateAt(block).map(BlockStateRef::id);
        }

        @Override
        public boolean air(Block block) {
            return block.getType().isAir() && blocks.stateAt(block).isEmpty();
        }

        @Override
        public boolean insulation(Block block) {
            return block.getType() == Material.STONE;
        }
    };

    final BloomeryDevice.Outputs outputs = new BloomeryDevice.Outputs() {
        @Override
        public Optional<String> itemId(ItemStack stack) {
            return Optional.of("minecraft:" + stack.getType().name().toLowerCase());
        }

        @Override
        public BloomeryDevice.Melt melt(ItemStack stack) {
            return stack.getType() == Material.RAW_IRON ? new BloomeryDevice.Melt(IRON, 100) : BloomeryDevice.Melt.NONE;
        }

        @Override
        public Optional<ItemStack> create(String id) {
            return Optional.of(new ItemStack(Material.IRON_INGOT));
        }

        @Override
        public Optional<Float> meltTemperature(ItemStack stack) {
            return Optional.of(1535f);
        }

        @Override
        public void heat(ItemStack stack, float temperature, long now) {
            heated = temperature;
        }
    };
    float heated = Float.NaN;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("bloomery");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, new Codec(), new FakeCustomItems(),
            Logger.getLogger("test"));
        BloomeryDevice.Services services = new BloomeryDevice.Services(outputs, () -> List.of(RECIPE), () -> config, probe,
            effects, blocks::stateAt);
        device = new BloomeryDevice(services, new Codec());
        bloom = new BloomDevice(new Codec(), effects, probe);
        service.register(device.kind());
        service.register(bloom.kind());
        inside = world.getBlockAt(10, 64, 10);
        build("north", 3);
        player = server.addPlayer();
        player.teleport(new Location(world, 10.5, 64, 14.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** TFC's bloomery with the inside block at (10, 64, 10) and {@code levels} chimney levels. */
    void build(String facing, int levels) {
        int[][] ring = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] names = {"north", "east", "south", "west"};
        int[] g = ring[java.util.Arrays.asList(names).indexOf(facing)];
        set(0, -1, 0);
        for (int i = 0; i < 4; i++) {
            if (!names[i].equals(facing)) {
                set(ring[i][0], 0, ring[i][1]);
            }
        }
        boolean z = facing.equals("north") || facing.equals("south");
        for (int[] side : z ? new int[][] {{1, 0}, {-1, 0}} : new int[][] {{0, -1}, {0, 1}}) {
            set(g[0] + side[0], 0, g[1] + side[1]);
        }
        set(g[0], -1, g[1]);
        for (int level = 1; level <= levels; level++) {
            for (int[] r : ring) {
                set(r[0], level, r[1]);
            }
        }
        gate = inside.getRelative(g[0], 0, g[1]);
        blocks.place(gate, BloomeryDevice.state(facing, false, false), true);
        store.data.put(FakeCustomBlocks.at(gate), new DataMap());
    }

    void set(int dx, int dy, int dz) {
        inside.getRelative(dx, dy, dz).setType(Material.STONE);
    }

    void drop(Material m, int n) {
        effects.world.add(new Drop(m, n));
    }

    DeviceRecord<BloomeryData> record() {
        return device.kind().codec().decode(store.read(gate).orElseThrow());
    }

    List<Material> contents() {
        List<Material> out = new ArrayList<>();
        for (ItemStack s : device.contents(record())) {
            out.add(s.getType());
        }
        return out;
    }

    void tick() {
        service.neighbourChanged(gate);
    }

    // ---- intake -----------------------------------------------------------------------------------------------

    @Test
    void aFormedBloomeryTakesTheChimneyItemsAlternatingCatalystAndInput() {
        drop(Material.RAW_IRON, 3);
        drop(Material.CHARCOAL, 4);
        tick();
        assertThat(contents()).containsExactly(Material.CHARCOAL, Material.RAW_IRON, Material.CHARCOAL, Material.RAW_IRON,
            Material.CHARCOAL, Material.RAW_IRON, Material.CHARCOAL);
        assertThat(effects.world.get(0).count()).isZero();
        assertThat(effects.world.get(1).count()).isZero();
        // 7 units of 48: four layers a block, 16 items a block of molten metal: 4 * 7 / 16 = 1 layer
        assertThat(effects.towers.getLast()).containsExactly(1, 0, 0);
        assertThat(effects.towerLit.getLast()).isFalse();
    }

    @Test
    void anInputWithoutACatalystIsLeftAloneAndAnUnformedBloomeryTakesNothing() {
        drop(Material.RAW_IRON, 3);
        tick();
        assertThat(contents()).isEmpty();
        drop(Material.CHARCOAL, 2);
        inside.getRelative(0, -1, 0).setType(Material.AIR); // the floor of the inside block is needed
        tick();
        assertThat(contents()).as("capacity 0").isEmpty();
    }

    @Test
    void theCapacityIsTheChimneyLevelsTimesSixteen() {
        for (int i = 0; i < 10; i++) {
            drop(Material.RAW_IRON, 64);
            drop(Material.CHARCOAL, 64);
        }
        tick();
        assertThat(contents()).hasSize(48);
        assertThat(effects.towers.getLast()).containsExactly(4, 4, 4);
        inside.getRelative(0, 3, 0).getRelative(1, 0, 0).setType(Material.AIR); // level 3 is open: capacity 32
        effects.spawned.clear();
        tick();
        assertThat(contents()).as("16 units popped off the end").hasSize(32);
        assertThat(effects.spawned).hasSize(16);
        assertThat(effects.spawnedAt).containsOnly(gate);
    }

    @Test
    void theConfiguredHeightLimitsTheCapacity() {
        config = new BloomeryRules.Config(16, 1);
        drop(Material.RAW_IRON, 64);
        drop(Material.CHARCOAL, 64);
        tick();
        assertThat(contents()).hasSize(16);
    }

    // ---- light, burn, bloom -------------------------------------------------------------------------------------

    private void fill(int ore, int coal) {
        drop(Material.RAW_IRON, ore);
        drop(Material.CHARCOAL, coal);
        tick();
    }

    @Test
    void lightingNeedsARecipeForTheContents() {
        assertThat(device.lightAt(service, gate)).as("empty").isFalse();
        drop(Material.CHARCOAL, 5);
        assertThat(device.lightAt(service, gate)).as("charcoal alone").isFalse();
        assertThat(record().state().lit()).isFalse();
        drop(Material.RAW_IRON, 5);
        assertThat(device.lightAt(service, gate)).isTrue();
        BloomeryData data = record().state();
        assertThat(data.lit()).isTrue();
        assertThat(data.litTick()).isEqualTo(100_000L);
        assertThat(data.duration()).isEqualTo(14_400);
        assertThat(effects.shown.getLast()).isEqualTo("north,true,false");
        assertThat(effects.towerLit.getLast()).isTrue();
        assertThat(effects.callbacks.getLast()).as("the structure re-check period").isEqualTo(BloomeryDevice.STRUCTURE_PERIOD);
    }

    @Test
    void aLitBloomeryLightsOnceMoreAsAlreadyLit() {
        fill(5, 5);
        assertThat(device.lightAt(service, gate)).isTrue();
        long lit = record().state().litTick();
        clock.addAndGet(100);
        assertThat(device.lightAt(service, gate)).isTrue();
        assertThat(record().state().litTick()).isEqualTo(lit);
    }

    @Test
    void afterTheBurnTheBloomIsMadeFromTheLeastBatchCount() {
        fill(10, 12); // 10 units of ore: 1000 mB: 10 batches; 12 units of charcoal: 6 batches
        // the alternation takes a catalyst, then an input, until the ore runs out, then the rest of the charcoal
        assertThat(contents()).hasSize(22);
        assertThat(device.lightAt(service, gate)).isTrue();
        clock.addAndGet(14_399);
        tick();
        assertThat(record().state().lit()).as("one tick short").isTrue();
        assertThat(effects.blooms).isEmpty();
        clock.addAndGet(1);
        tick();
        assertThat(effects.blooms).containsExactly("IRON_INGOTx6"); // 12 coal / 2 = 6 batches, 1000 / 100 = 10
        assertThat(heated).as("1 degree below the melting temperature").isEqualTo(1534f);
        assertThat(record().state().lit()).isFalse();
        assertThat(contents()).isEmpty();
        assertThat(effects.moltenRemovals).isPositive();
        assertThat(effects.shown.getLast()).isEqualTo("north,false,false");
        BlockStateRef at = blocks.stateAt(inside).orElseThrow();
        assertThat(at.id()).isEqualTo(BloomeryWorld.BLOOM);
        assertThat(at.properties().get("layers")).isEqualTo("6");
        assertThat(bloom.read(service, inside).orElseThrow().state()).isEqualTo(BloomData.made(6));
    }

    @Test
    void aFinishedBurnCompletesEvenWhenTheStructureIsGone() {
        fill(4, 4);
        assertThat(device.lightAt(service, gate)).isTrue();
        inside.getRelative(1, 1, 0).setType(Material.AIR);
        inside.getRelative(0, -1, 0).setType(Material.AIR);
        clock.addAndGet(20_000);
        tick();
        assertThat(effects.blooms).hasSize(1);
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void aBurnThatMakesNoBatchStillConsumesTheContents() {
        fill(2, 2); // 200 mB of iron, 2 charcoal
        BloomeryRecipe dear = new BloomeryRecipe(IRON, 300, "minecraft:charcoal", 2, "tfp:raw_iron_bloom", 14_400);
        BloomeryDevice other = new BloomeryDevice(new BloomeryDevice.Services(outputs, () -> List.of(dear), () -> config, probe,
            effects, blocks::stateAt), new Codec());
        DeviceRecord<BloomeryData> lit = record().withState(new BloomeryData(100_000L, 14_400));
        DeviceRecord<BloomeryData> after = other.run(gate, "north", false, true, lit, 120_000L);
        assertThat(effects.blooms).as("200 mB is not a batch of 300").isEmpty();
        assertThat(other.contents(after)).isEmpty();
        assertThat(after.state().lit()).isFalse();
    }

    @Test
    void aShrunkenStructureHaltsALitBloomeryAndKeepsTheRest() {
        for (int i = 0; i < 10; i++) {
            drop(Material.RAW_IRON, 64);
            drop(Material.CHARCOAL, 64);
        }
        tick();
        assertThat(device.lightAt(service, gate)).isTrue();
        inside.getRelative(1, 3, 0).setType(Material.AIR); // the top level opens: 48 -> 32
        clock.addAndGet(500);
        tick();
        assertThat(record().state().lit()).as("halted").isFalse();
        assertThat(contents()).hasSize(32);
        assertThat(effects.shown.getLast()).isEqualTo("north,false,false");
        assertThat(effects.blooms).isEmpty();
    }

    @Test
    void aLitBloomeryKeepsBurningWhileItsStructureHolds() {
        fill(8, 8);
        assertThat(device.lightAt(service, gate)).isTrue();
        clock.addAndGet(1_200);
        tick();
        assertThat(record().state().lit()).isTrue();
        assertThat(effects.callbacks.getLast()).isEqualTo(BloomeryDevice.STRUCTURE_PERIOD);
        clock.addAndGet(12_500); // 13700 of 14400 ticks burnt: 700 left
        tick();
        assertThat(effects.callbacks.getLast()).as("the last stretch is the rest of the burn").isEqualTo(700L);
    }

    @Test
    void aLitBloomeryTakesNoMoreItems() {
        fill(4, 4);
        assertThat(device.lightAt(service, gate)).isTrue();
        int size = contents().size();
        drop(Material.RAW_IRON, 8);
        drop(Material.CHARCOAL, 8);
        clock.addAndGet(100);
        tick();
        assertThat(contents()).hasSize(size);
    }

    @Test
    void contentsNoRecipeMatchesAreEjectedInFront() {
        fill(4, 4);
        assertThat(contents()).isNotEmpty();
        List<BloomeryRecipe> none = new ArrayList<>();
        BloomeryDevice other = new BloomeryDevice(new BloomeryDevice.Services(outputs, () -> none, () -> config, probe, effects,
            blocks::stateAt), new Codec());
        // a reload dropped the recipe: the held units match nothing and go out at the front (north of the gate)
        DeviceRecord<BloomeryData> held = record();
        DeviceRecord<BloomeryData> after = other.run(gate, "north", false, false, held, clock.get());
        assertThat(other.contents(after)).isEmpty();
        assertThat(effects.spawnedAt).contains(gate.getRelative(0, 0, -1));
    }

    // ---- the door, the gate, removal ----------------------------------------------------------------------------

    @Test
    void rightClickTogglesTheDoorOfAnUnlitBloomeryButNotALitOne() {
        assertThat(service.use(gate, player, EquipmentSlot.HAND, ItemStack.empty())).isTrue();
        assertThat(effects.shown.getLast()).isEqualTo("north,false,true");
        assertThat(effects.sounds).isEqualTo(1);
        fill(4, 4);
        assertThat(device.lightAt(service, gate)).isTrue();
        assertThat(effects.shown.getLast()).as("lighting closes the door").isEqualTo("north,true,false");
        assertThat(service.use(gate, player, EquipmentSlot.HAND, ItemStack.empty())).as("lit: passed on").isFalse();
        assertThat(effects.sounds).isEqualTo(1);
    }

    @Test
    void aGateWithoutItsSideWallsBreaks() {
        inside.getRelative(1, 0, -1).setType(Material.AIR); // the wall east of the gate
        tick();
        assertThat(effects.brokenGates).containsExactly(gate);
    }

    @Test
    void anIntactGateStays() {
        tick();
        assertThat(effects.brokenGates).isEmpty();
    }

    @Test
    void removingTheBloomeryEjectsItsContentsInFrontAndClearsTheMolten() {
        fill(4, 4);
        DataMap data = store.read(gate).orElseThrow();
        effects.spawned.clear();
        effects.spawnedAt.clear();
        blocks.remove(gate);
        service.removed(world, gate.getX(), gate.getY(), gate.getZ(), BloomeryDevice.ID, data);
        // the removed state is the default one: facing north, so the front is north of the gate
        assertThat(effects.spawned).hasSize(8);
        assertThat(effects.spawnedAt).containsOnly(gate.getRelative(0, 0, -1));
        assertThat(effects.moltenRemovals).isPositive();
    }

    @Test
    void theDeviceDataSurvivesAReload() {
        fill(4, 4);
        assertThat(device.lightAt(service, gate)).isTrue();
        DeviceRecord<BloomeryData> r = record();
        assertThat(r.state()).isEqualTo(new BloomeryData(100_000L, 14_400));
        assertThat(device.contents(r)).hasSize(8);
    }

    // ---- the bloom block ----------------------------------------------------------------------------------------

    @Test
    void eachBreakOfABloomDropsOneItemAndShrinksItUntilItIsGone() {
        Block at = world.getBlockAt(30, 64, 30);
        blocks.place(at, BlockStateRef.of(BloomeryWorld.BLOOM).with("layers", "3"), true);
        store.data.put(FakeCustomBlocks.at(at), new DataMap());
        assertThat(bloom.seed(service, at, new ItemStack(Material.IRON_INGOT), 3)).isTrue();
        assertThat(bloom.dropOne(service, at)).isTrue();
        assertThat(effects.spawned.getLast().getType()).isEqualTo(Material.IRON_INGOT);
        assertThat(effects.spawned.getLast().getAmount()).isEqualTo(1);
        assertThat(effects.spawnedAt.getLast()).isEqualTo(at);
        assertThat(effects.bloomLayers).containsExactly(2);
        assertThat(bloom.dropOne(service, at)).isTrue();
        assertThat(effects.bloomLayers).containsExactly(2, 1);
        assertThat(bloom.dropOne(service, at)).isTrue();
        assertThat(effects.removedBlooms).containsExactly(at);
        assertThat(effects.spawned).hasSize(3);
    }

    @Test
    void aBloomBesideABloomeryDropsAtTheBloomery() {
        assertThat(bloom.dropBlock(inside)).isEqualTo(gate);
        assertThat(bloom.dropBlock(world.getBlockAt(40, 64, 40))).isEqualTo(world.getBlockAt(40, 64, 40));
    }

    @Test
    void aLargeBloomScalesItsLayers() {
        Block at = world.getBlockAt(31, 64, 31);
        blocks.place(at, BlockStateRef.of(BloomeryWorld.BLOOM).with("layers", "8"), true);
        store.data.put(FakeCustomBlocks.at(at), new DataMap());
        bloom.seed(service, at, new ItemStack(Material.IRON_INGOT), 16);
        bloom.dropOne(service, at);
        assertThat(effects.bloomLayers).containsExactly(7);
    }

    @Test
    void aBlockWithoutABloomRecordLetsTheBreakHappen() {
        Block at = world.getBlockAt(32, 64, 32);
        blocks.place(at, BlockStateRef.of(BloomeryWorld.BLOOM).with("layers", "1"), true);
        store.data.put(FakeCustomBlocks.at(at), new DataMap());
        assertThat(bloom.dropOne(service, at)).isFalse();
        assertThat(effects.spawned).isEmpty();
    }

    @Test
    void lookingOrderPutsTheNearestHorizontalFirst() {
        player.teleport(new Location(world, 10.5, 64, 14.5, 0f, 0f)); // yaw 0 looks south (+z)
        assertThat(BloomeryDevice.lookingOrder(player).getFirst()).isEqualTo("south");
        player.teleport(new Location(world, 10.5, 64, 14.5, 90f, 0f)); // yaw 90 looks west
        assertThat(BloomeryDevice.lookingOrder(player).getFirst()).isEqualTo("west");
        player.teleport(new Location(world, 10.5, 64, 14.5, 180f, 0f));
        assertThat(BloomeryDevice.lookingOrder(player).getFirst()).isEqualTo("north");
        assertThat(BloomeryDevice.lookingOrder(null)).hasSize(4);
    }
}
