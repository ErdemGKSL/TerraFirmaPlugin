/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.firepit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.fuel.FuelTable;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
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
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HEAT-06 on MockBukkit with fakes of the CraftEngine adapter and the world effects: building and lighting from a
 * stick and log pile, the fuel burn catch-up over two in-game hours against {@link FirepitRules}, cooking raw beef,
 * inserting logs, dousing with a shovel and a water bucket, the ash, the burn-out callback and the lost floor.
 *
 * <p>Stand-ins: oak log = TFC oak logs fuel, stick = a pine log (dirty fuel), beef = raw beef (capacity 1, recipe 200 C,
 * result cooked beef), gunpowder = wood ash, iron shovel = a shovel.
 */
class FirepitMockBukkitTest extends MockServerTest {
    static final long START = 10_000;
    static final Fuel OAK = FuelTable.byId("oak_logs").orElseThrow();
    static final Fuel PINE = FuelTable.byId("pine_logs").orElseThrow();

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

    static final class Effects implements FirepitEffects {
        FakeCustomBlocks world;
        final List<Sound> sounds = new ArrayList<>();
        final List<ItemStack> given = new ArrayList<>();
        final List<ItemStack> spawned = new ArrayList<>();
        final List<Long> callbacks = new ArrayList<>();
        final List<Douse> doused = new ArrayList<>();
        int smoke;
        boolean broken;

        @Override
        public void show(Block block, BlockStateRef state) {
            world.place(block, state, false);
        }

        @Override
        public void sound(Block block, Sound sound) {
            sounds.add(sound);
        }

        @Override
        public void smoke(Block block, int smokeLevel) {
            smoke++;
        }

        @Override
        public void spawn(Block block, ItemStack stack) {
            spawned.add(stack);
        }

        @Override
        public void give(Player player, ItemStack stack) {
            given.add(stack);
        }

        @Override
        public void useDouse(Player player, EquipmentSlot hand, Douse douse) {
            doused.add(douse);
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public void breakBlock(Block block) {
            broken = true;
        }
    }

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(FirepitDevice.ID);
    private final MemoryStore store = new MemoryStore();
    private final TableCodec codec = new TableCodec();
    private final Effects effects = new Effects();
    private final AtomicLong clock = new AtomicLong(START);
    private boolean raining;
    private boolean floor = true;
    private DeviceService service;
    private FirepitDevice device;
    private Block pit;
    private PlayerMock player;

    {
        effects.world = blocks;
    }

    static Optional<Fuel> fuel(ItemStack s) {
        return switch (s.getType()) {
            case OAK_LOG -> Optional.of(OAK);
            case STICK -> Optional.of(PINE);
            default -> Optional.empty();
        };
    }

    @BeforeEach
    void setUp() {
        World world = server.addSimpleWorld("firepit");
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, codec, new FakeCustomItems(),
            Logger.getLogger("test"));
        RandomGenerator ash = () -> 0L; // every ash draw lands
        device = new FirepitDevice(new FirepitDevice.Services(FirepitMockBukkitTest::fuel,
            s -> s.getType() == Material.OAK_LOG || s.getType() == Material.STICK,
            s -> s.getType() == Material.BEEF ? 1.0 : 0.0,
            new FirepitDevice.Cooker() {
                @Override
                public float recipeTemperature(ItemStack stack) {
                    return stack.getType() == Material.BEEF ? 200f : Float.NaN;
                }

                @Override
                public Optional<ItemStack> convert(ItemStack heated, float before) {
                    if (heated.getType() != Material.BEEF || before < 200f) {
                        return Optional.empty();
                    }
                    ItemStack out = heated.withType(Material.COOKED_BEEF);
                    out.setAmount(1);
                    return Optional.of(out);
                }
            },
            s -> s.getType() == Material.IRON_SHOVEL ? FirepitEffects.Douse.SHOVEL
                : s.getType() == Material.WATER_BUCKET ? FirepitEffects.Douse.WATER : FirepitEffects.Douse.NONE,
            b -> raining, b -> floor, n -> new ItemStack(Material.GUNPOWDER, n), () -> ash, HeatConfig.DEFAULT, effects),
            codec);
        service.register(device.kind());
        pit = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
        player.teleport(new Location(world, 3.5, 64, 0.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    /** TFC StartFireEvent: the block placed unlit, the logs in, then lit (the factory's steps). */
    private void build(Material... logs) {
        blocks.place(pit, BlockStateRef.of(FirepitDevice.ID).with("lit", "false").with("smoke_level", "0").with("axis", "z"),
            true);
        store.data.put(FakeCustomBlocks.at(pit), new DataMap());
        List<ItemStack> stacks = new ArrayList<>();
        for (Material m : logs) {
            stacks.add(new ItemStack(m));
        }
        device.build(pit, stacks, service::neighbourChanged);
    }

    private DeviceRecord<FirepitData> record() {
        return device.kind().codec().decode(store.read(pit).orElseThrow());
    }

    private ItemStack slot(int i) {
        return codec.decode(record().slot(i));
    }

    private boolean click(ItemStack held) {
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(pit, player, EquipmentSlot.HAND, held);
    }

    @Test
    void buildingLightsTheFirstLogAndShowsTheLitState() {
        build(Material.OAK_LOG, Material.OAK_LOG);
        FirepitData d = record().state();
        assertThat(d.lit()).isTrue();
        assertThat(d.heat().burnTicks()).isEqualTo(2250);
        assertThat(d.ash()).isEqualTo(1);
        assertThat(slot(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(slot(1).getType()).isEqualTo(Material.AIR);
        assertThat(blocks.stateAt(pit).orElseThrow()).isEqualTo(BlockStateRef.parse("tfp:firepit[axis=z,lit=true,smoke_level=0]"));
        assertThat(effects.callbacks).containsExactly(2250L + 2250L);
    }

    @Test
    void fuelBurnCatchUpOverTwoInGameHoursEqualsFirepitRules() {
        build(Material.OAK_LOG, Material.STICK, Material.OAK_LOG);
        FirepitData lit = record().state();
        clock.addAndGet(2000); // two in-game hours (TFC: 1000 ticks per hour)
        service.catchUp(pit);

        FirepitRules.Advance rules = FirepitRules.advance(lit.heat(), 2000, false, List.of(PINE, OAK),
            FirepitRules.DeviceConfig.DEFAULT);
        FirepitData d = record().state();
        assertThat(d.heat().burnTicks()).isEqualTo(rules.state().burnTicks());
        assertThat(d.heat().burnTemperature()).isEqualTo(rules.state().burnTemperature());
        assertThat(d.temperature()).isCloseTo(rules.state().temperature(), within(0.01f));
        assertThat(rules.consumed()).isZero(); // oak: 2250 > 2000
        assertThat(slot(0).getType()).isEqualTo(Material.STICK);

        clock.addAndGet(1000); // past the first log: the pine (slot 0) is taken, the oak cascades down
        service.catchUp(pit);
        assertThat(record().state().heat().burnTemperature()).isEqualTo(PINE.temperature());
        assertThat(slot(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(slot(1).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aLitFirepitCooksRawBeefAndTheOutputIsTakenByHand() {
        build(Material.OAK_LOG);
        assertThat(click(new ItemStack(Material.BEEF, 3))).isTrue();
        assertThat(slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.BEEF);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        assertThat(effects.smoke).isPositive();

        clock.addAndGet(201); // the device climbs 1 C per tick; beef converts on the 202nd tick after lighting
        service.catchUp(pit);
        assertThat(slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.BEEF);
        assertThat(HeatPdc.read(slot(FirepitSim.SLOT_ITEM_INPUT)).orElseThrow().temperature()).isEqualTo(201f);

        clock.addAndGet(1);
        service.catchUp(pit);
        assertThat(slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.AIR);
        ItemStack cooked = slot(FirepitSim.SLOT_OUTPUT_1);
        assertThat(cooked.getType()).isEqualTo(Material.COOKED_BEEF);
        assertThat(HeatPdc.read(cooked).orElseThrow().temperature()).isEqualTo(202f);

        assertThat(click(ItemStack.empty())).isTrue();
        assertThat(effects.given).extracting(ItemStack::getType).containsExactly(Material.COOKED_BEEF);
        assertThat(slot(FirepitSim.SLOT_OUTPUT_1).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void logsGoIntoTheFirstEmptyFuelSlotOneAtATime() {
        build(Material.OAK_LOG);
        assertThat(click(new ItemStack(Material.OAK_LOG, 5))).isTrue();
        assertThat(click(new ItemStack(Material.OAK_LOG, 4))).isTrue();
        assertThat(slot(0).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(slot(1).getType()).isEqualTo(Material.OAK_LOG);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        assertThat(effects.sounds).containsExactly(FirepitEffects.Sound.LOG, FirepitEffects.Sound.LOG);
        // a cold item without a heat capacity is no fuel and no input: nothing moves
        assertThat(click(new ItemStack(Material.DIRT))).isTrue();
        assertThat(slot(2).getType()).isEqualTo(Material.AIR);
        assertThat(slot(FirepitSim.SLOT_ITEM_INPUT).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aShovelOrAWaterBucketDousesAWarmFirepitThenTheAshCanBeTaken() {
        build(Material.OAK_LOG);
        clock.addAndGet(50);
        assertThat(click(new ItemStack(Material.IRON_SHOVEL))).isTrue();
        FirepitData d = record().state();
        assertThat(d.lit()).isFalse();
        assertThat(d.temperature()).isEqualTo(50f); // extinguish keeps the heat; the firepit cools from here
        assertThat(effects.sounds).containsExactly(FirepitEffects.Sound.EXTINGUISH);
        assertThat(effects.doused).containsExactly(FirepitEffects.Douse.SHOVEL);
        assertThat(blocks.stateAt(pit).orElseThrow().properties()).containsEntry("lit", "false");

        // still warm: the water bucket douses again (no sound: it is no longer lit)
        assertThat(click(new ItemStack(Material.WATER_BUCKET))).isTrue();
        assertThat(effects.doused).containsExactly(FirepitEffects.Douse.SHOVEL, FirepitEffects.Douse.WATER);

        clock.addAndGet(100); // cooled to 0: the shovel no longer douses; an empty hand takes the ash
        assertThat(click(ItemStack.empty())).isTrue();
        assertThat(effects.given).hasSize(1);
        assertThat(effects.given.getFirst().getType()).isEqualTo(Material.GUNPOWDER);
        assertThat(record().state().ash()).isZero();
        assertThat(effects.sounds).endsWith(FirepitEffects.Sound.ASH);
    }

    @Test
    void creativeKeepsTheHeldStack() {
        build(Material.OAK_LOG);
        player.setGameMode(GameMode.CREATIVE);
        assertThat(click(new ItemStack(Material.OAK_LOG, 2))).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        clock.addAndGet(10);
        assertThat(click(new ItemStack(Material.IRON_SHOVEL))).isTrue();
        assertThat(effects.doused).isEmpty();
        assertThat(record().state().lit()).isFalse();
    }

    @Test
    void rainBurnsTwiceAsFastFromTheNextCallback() {
        build(Material.OAK_LOG);
        raining = true;
        service.neighbourChanged(pit); // the callback samples the rain
        clock.addAndGet(1000);
        service.catchUp(pit);
        assertThat(record().state().heat().burnTicks()).isEqualTo(2250 - 2000);
        assertThat(record().state().temperature()).isEqualTo(728f - 300f);
    }

    @Test
    void burnOutCallbackTurnsTheBlockUnlit() {
        build(Material.STICK);
        clock.addAndGet(PINE.duration());
        service.neighbourChanged(pit);
        assertThat(record().state().lit()).isFalse();
        assertThat(blocks.stateAt(pit).orElseThrow().properties()).containsEntry("lit", "false");
    }

    @Test
    void lightingAnUnlitFirepitTakesFuel() {
        build(Material.OAK_LOG);
        click(new ItemStack(Material.OAK_LOG));
        clock.addAndGet(10);
        click(new ItemStack(Material.IRON_SHOVEL));
        assertThat(record().state().lit()).isFalse();
        assertThat(device.light(pit, service::neighbourChanged)).isTrue();
        assertThat(record().state().lit()).isTrue();
        assertThat(slot(0).getType()).isEqualTo(Material.AIR);
        assertThat(device.light(pit, service::neighbourChanged)).as("already lit").isTrue();
    }

    @Test
    void aFirepitThatLosesItsFloorBreaks() {
        build(Material.OAK_LOG);
        floor = false;
        service.neighbourChanged(pit);
        assertThat(effects.broken).isTrue();
    }

    @Test
    void removalDropsTheAsh() {
        build(Material.OAK_LOG);
        DataMap data = store.read(pit).orElseThrow();
        service.removed(pit.getWorld(), 0, 64, 0, FirepitDevice.ID, data);
        assertThat(effects.spawned).extracting(ItemStack::getType).contains(Material.GUNPOWDER);
    }
}
