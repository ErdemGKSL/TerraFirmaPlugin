/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lamp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.lamp.LampData;
import dev.tfp.core.lamp.LampRules;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceKinds;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.DeviceStore;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.testing.MockServerTest;
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
 * PAR-METAL-19 on MockBukkit with fakes: filling and emptying by container, the fuels a lamp takes, lighting and quenching,
 * the lazy burn (catch-up and the run-dry callback), placement and support. Stand-ins: a clay ball is the jug, a bucket the
 * empty bucket, a lava bucket the vanilla lava bucket.
 */
class LampDeviceMockBukkitTest extends MockServerTest {
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

    /** The effects as a log; {@code show} writes the state like the real one. */
    final class Log implements LampEffects {
        final List<String> events = new ArrayList<>();
        final List<Long> callbacks = new ArrayList<>();

        @Override
        public void show(Block block, BlockStateRef state) {
            events.add("show " + state.properties());
            blocks.place(block, state, false);
        }

        @Override
        public void extinguished(Block block) {
            events.add("extinguished");
        }

        @Override
        public void transfer(Block block, FluidAmount fluid, boolean intoItem) {
            events.add("transfer " + fluid.fluid() + " " + fluid.amount() + (intoItem ? " into item" : " into lamp"));
        }

        @Override
        public void callbackIn(Block block, long ticks) {
            callbacks.add(ticks);
        }

        @Override
        public void breakBlock(Block block) {
            events.add("break");
        }
    }

    private static final String OIL = "tfp:olive_oil";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks();
    private final MemoryStore store = new MemoryStore();
    private final AtomicLong clock = new AtomicLong();
    private final Log effects = new Log();
    private boolean supported = true;
    private DeviceService service;
    private LampDevice lamp;
    private List<DeviceKind<LampData>> kinds;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("lamp");
        for (String metal : LampModule.METALS) {
            blocks.known(LampDevice.id(metal));
        }
        StackCodec none = new StackCodec() {
            @Override
            public ItemBlob encode(ItemStack stack) {
                return ItemBlob.EMPTY;
            }

            @Override
            public ItemStack decode(ItemBlob blob) {
                return ItemStack.empty();
            }
        };
        service = new DeviceService(new DeviceKinds(), blocks, () -> store, clock::get, none, new FakeCustomItems(),
            Logger.getLogger("test"));
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(
            stack -> switch (stack.getType()) {
                case CLAY_BALL -> Optional.of("tfp:ceramic/jug");
                case BUCKET -> Optional.of("minecraft:bucket");
                case LAVA_BUCKET -> Optional.of("minecraft:lava_bucket");
                default -> Optional.empty();
            }, (kind, fluid) -> true, id -> Optional.empty(), id -> false));
        lamp = new LampDevice(new LampDevice.Services(tanks, effects, (block, hanging) -> supported), blocks);
        kinds = lamp.kinds(LampModule.METALS);
        kinds.forEach(service::register);
        player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 65, 5.5));
        player.setGameMode(GameMode.SURVIVAL);
    }

    private Block place(String metal, int x, LampData data) {
        Block b = world.getBlockAt(x, 65, 0);
        blocks.place(b, BlockStateRef.of(LampDevice.id(metal)).with("hanging", "false").with("lit", Boolean.toString(data.lit())), true);
        DeviceKind<LampData> kind = kinds.stream().filter(k -> k.id().equals(LampDevice.id(metal))).findFirst().orElseThrow();
        store.data.put(FakeCustomBlocks.at(b), kind.codec().encode(kind.fresh().withState(data).withLastUpdateTick(clock.get())));
        return b;
    }

    private LampData data(Block b) {
        DeviceService.Inspection i = service.inspect(b).orElseThrow();
        return (LampData) i.record().orElseThrow().state();
    }

    private ItemStack jug(String fluid, int amount) {
        ItemStack s = new ItemStack(Material.CLAY_BALL);
        if (amount > 0) {
            ItemFluids.fill(s, new FluidAmount(fluid, amount), 100, true);
        }
        return s;
    }

    private boolean click(Block b, ItemStack held, boolean sneak) {
        player.setSneaking(sneak);
        player.getInventory().setItem(EquipmentSlot.HAND, held);
        return service.use(b, player, EquipmentSlot.HAND, held);
    }

    @Test
    void oneKindPerMetalSavesTheFuelOnTheItem() {
        assertThat(kinds).hasSize(9);
        assertThat(kinds).allSatisfy(k -> assertThat(k.removal()).isEqualTo(DeviceKind.RemoveBehaviour.SAVE));
        assertThat(LampDevice.metal("tfp:metal/lamp/red_steel")).isEqualTo("red_steel");
        assertThat(LampDevice.metal("tfp:metal/bucket/red_steel")).isNull();
    }

    @Test
    void aJugOfOilEmptiesIntoTheLampAndAnEmptyJugFillsFromIt() {
        Block b = place("copper", 0, LampData.FRESH);
        assertThat(click(b, jug(OIL, 60), false)).isTrue();
        assertThat(data(b).tank()).isEqualTo(new FluidAmount(OIL, 60));
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).isEmpty()).isTrue();
        assertThat(effects.events).contains("transfer " + OIL + " 60 into lamp");
        assertThat(click(b, jug("", 0), false)).isTrue();
        assertThat(data(b).tank().isEmpty()).isTrue();
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand())).isEqualTo(new FluidAmount(OIL, 60));
    }

    @Test
    void theLampHoldsTwoHundredFiftyAndTheRestStaysInTheContainer() {
        Block b = place("copper", 0, new LampData(new FluidAmount(OIL, 230), false, 0));
        click(b, jug(OIL, 100), false);
        assertThat(data(b).tank().amount()).isEqualTo(250);
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(80);
    }

    @Test
    void aFluidThatIsNoFuelIsNotTakenButTheClickIsSwallowed() {
        Block b = place("copper", 0, LampData.FRESH);
        ItemStack water = jug("minecraft:water", 50);
        assertThat(click(b, water, false)).isTrue();
        assertThat(data(b).tank().isEmpty()).isTrue();
        assertThat(ItemFluids.content(player.getInventory().getItemInMainHand()).amount()).isEqualTo(50);
        // lava is a fuel of the red and blue steel lamps only
        Block copper = place("copper", 2, LampData.FRESH);
        assertThat(click(copper, new ItemStack(Material.LAVA_BUCKET), false)).isTrue();
        assertThat(data(copper).tank().isEmpty()).isTrue();
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.LAVA_BUCKET);
    }

    @Test
    void lavaInARedSteelLampLightsItAtOnce() {
        Block b = place("red_steel", 0, LampData.FRESH);
        assertThat(click(b, new ItemStack(Material.LAVA_BUCKET), false)).isTrue();
        assertThat(data(b).tank()).isEqualTo(new FluidAmount("minecraft:lava", 250));
        assertThat(data(b).lit()).isTrue();
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("lit", "true");
        assertThat(player.getInventory().getItemInMainHand().getType()).isEqualTo(Material.BUCKET);
        assertThat(effects.callbacks).as("lava never runs dry").isEmpty();
    }

    @Test
    void anEmptyHandThatSneaksPutsALitLampOutAndKeepsItsFuel() {
        Block b = place("bronze", 0, new LampData(new FluidAmount(OIL, 100), true, 0));
        assertThat(click(b, ItemStack.empty(), true)).isTrue();
        assertThat(data(b).lit()).isFalse();
        assertThat(data(b).tank().amount()).isEqualTo(100);
        assertThat(effects.events).contains("extinguished");
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("lit", "false");
        assertThat(click(b, ItemStack.empty(), false)).as("a plain click does nothing").isFalse();
    }

    @Test
    void aFireStartLightsALampWithFuelAndCancelsForAnyUnlitLamp() {
        Block fuelled = place("copper", 0, new LampData(new FluidAmount(OIL, 5), false, 0));
        Block dry = place("copper", 2, LampData.FRESH);
        assertThat(lamp.light(fuelled, service, kinds)).isTrue();
        assertThat(data(fuelled).lit()).isTrue();
        assertThat(blocks.stateAt(fuelled).orElseThrow().properties()).containsEntry("lit", "true");
        assertThat(lamp.light(dry, service, kinds)).as("TFC cancels the start for an unlit lamp even without fuel").isTrue();
        assertThat(data(dry).lit()).isFalse();
        assertThat(lamp.light(fuelled, service, kinds)).as("a lit lamp lets the start go on").isFalse();
    }

    @Test
    void aLitLampBurnsLazilyAndGoesOutWhenAJumpOfTimePassesItsFuel() {
        Block b = place("copper", 0, new LampData(new FluidAmount("tfp:tallow", 10), true, 0));
        lamp.sync(b, blocks.stateAt(b).orElseThrow(), data(b), "copper");
        assertThat(effects.callbacks).containsExactly(24_000L); // 10 mB x 2400 ticks
        clock.set(12_000);
        service.catchUp(b);
        assertThat(data(b).tank().amount()).isEqualTo(5);
        assertThat(data(b).lit()).isTrue();
        clock.set(10 * 24_000L);
        service.catchUp(b);
        assertThat(data(b).lit()).isFalse();
        assertThat(data(b).tank().isEmpty()).isTrue();
        // the run-dry callback (a neighbour update) turns the block unlit
        service.neighbourChanged(b);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("lit", "false");
        assertThat(effects.events).contains("extinguished");
    }

    @Test
    void aCallbackAfterATimeJumpShowsTheUnlitState() {
        Block b = place("steel", 0, new LampData(new FluidAmount(OIL, 1), true, 0));
        clock.set(7_200);
        service.neighbourChanged(b);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("lit", "false");
        assertThat(data(b).tank().isEmpty()).isTrue();
    }

    @Test
    void placingKeepsTheFuelUnlightsTheLampAndHangsItWhenLookingUp() {
        LampData fromItem = new LampData(new FluidAmount(OIL, 90), true, 123);
        Block b = place("copper", 0, fromItem);
        player.teleport(new Location(world, 0.5, 65, 5.5, 0f, -30f));
        service.placed(b, player, EquipmentSlot.HAND, new ItemStack(Material.PAPER));
        assertThat(data(b).lit()).isFalse();
        assertThat(data(b).carry()).isZero();
        assertThat(data(b).tank().amount()).isEqualTo(90);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("hanging", "true").containsEntry("lit", "false");
        player.teleport(new Location(world, 0.5, 65, 5.5, 0f, 20f));
        service.placed(b, player, EquipmentSlot.HAND, new ItemStack(Material.PAPER));
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("hanging", "false");
    }

    @Test
    void aLampThatLostItsSupportBreaksOff() {
        Block b = place("copper", 0, LampData.FRESH);
        service.neighbourChanged(b);
        assertThat(effects.events).doesNotContain("break");
        supported = false;
        service.neighbourChanged(b);
        assertThat(effects.events).contains("break");
    }

    @Test
    void theRecordSurvivesTheCodecWithTheFuelAndTheCarry() {
        Block b = place("copper", 0, new LampData(new FluidAmount(OIL, 12), true, 345));
        assertThat(data(b)).isEqualTo(new LampData(new FluidAmount(OIL, 12), true, 345));
        assertThat(LampRules.CAPACITY).isEqualTo(250);
    }
}
