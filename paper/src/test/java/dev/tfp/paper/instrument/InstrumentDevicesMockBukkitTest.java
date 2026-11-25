/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.instrument.Instruments.ClockMode;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.instrument.InstrumentDevices.State;
import dev.tfp.paper.instrument.InstrumentReadout.Readout;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-CLIM-09: the instrument handlers on MockBukkit, with every executor run inline. */
class InstrumentDevicesMockBukkitTest extends MockServerTest {
    private static final ClockSnapshot CLOCK = new ClockSnapshot(1, 0L, 13_500L, 8, true);

    private final List<Readout> sent = new ArrayList<>();
    private final List<BlockStateRef> shown = new ArrayList<>();
    private final List<Block> removed = new ArrayList<>();
    private final java.util.Map<String, String> ids = new java.util.HashMap<>();
    private boolean sturdy = true;
    private boolean readable = false;
    private boolean center = true;
    private InstrumentSources sources = InstrumentSources.NONE;
    private World world;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("instruments");
        player = server.addPlayer();
    }

    private InstrumentDevices.Services services() {
        return new InstrumentDevices.Services(sources, () -> CLOCK, 5000, new InstrumentDevices.Computer() {
            @Override
            public <T> void run(java.util.function.Supplier<T> work, java.util.function.Consumer<T> then) {
                then.accept(work.get());
            }
        }, (p, task) -> task.run(), (b, task) -> task.run(), (b, s) -> shown.add(s), (p, r) -> sent.add(r), support(), removed::add);
    }

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private InstrumentRules.Support support() {
        return new InstrumentRules.Support() {
            @Override
            public boolean readable(Block block, InstrumentReadout.Kind kind) {
                return readable;
            }

            @Override
            public boolean sturdyFace(Block block, org.bukkit.block.BlockFace face) {
                return sturdy;
            }

            @Override
            public boolean centerSupportUp(Block block) {
                return center;
            }

            @Override
            public Optional<String> id(Block block) {
                return Optional.ofNullable(ids.get(key(block)));
            }
        };
    }

    private void neighbourChanged(String id, BlockStateRef state) {
        DeviceKind<State> k = kind(id);
        k.handler().neighbourChanged(new DeviceContext<>(k, world.getBlockAt(10, 70, 10), state, player, EquipmentSlot.HAND,
            ItemStack.empty(), k.fresh(), 1L, StackCodec.PAPER));
    }

    private DeviceKind<State> kind(String id) {
        return InstrumentDevices.kinds(services()).stream().filter(k -> k.id().equals(id)).findFirst().orElseThrow();
    }

    private DeviceResult<State> use(String id, BlockStateRef state, EquipmentSlot hand) {
        DeviceKind<State> k = kind(id);
        Block block = world.getBlockAt(10, 70, 10);
        DeviceContext<State> ctx = new DeviceContext<>(k, block, state, player, hand, ItemStack.empty(), k.fresh(), 1L,
            StackCodec.PAPER);
        return k.handler().use(ctx);
    }

    @Test
    void fourKinds() {
        assertThat(InstrumentDevices.kinds(services())).extracting(DeviceKind::id).containsExactly(
            "tfp:thermometer", "tfp:anemometer", "tfp:vane", "tfp:calendar_clock");
    }

    @Test
    void thermometerShowsTheReadoutAndWritesItsPower() {
        player.setGameMode(GameMode.SURVIVAL);
        DeviceResult<State> r = use(InstrumentDevices.THERMOMETER,
            BlockStateRef.of(InstrumentDevices.THERMOMETER).with("power", "0").with("attached", "false").with("facing", "north"),
            EquipmentSlot.HAND);
        assertThat(r.consumed()).isTrue();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).key()).isEqualTo("instrument.thermometer.ambient");
    }

    @Test
    void attachedThermometerReadsTheDeviceBehindIt() {
        sources = new InstrumentSources() {
            @Override
            public Optional<Float> deviceTemperature(Block device) {
                return device.getX() == 10 && device.getZ() == 11 ? Optional.of(800f) : Optional.empty();
            }

            @Override
            public Optional<Timer> timer(Block device) {
                return Optional.empty();
            }
        };
        // facing north: mounted on the block to the south (z + 1)
        use(InstrumentDevices.THERMOMETER,
            BlockStateRef.of(InstrumentDevices.THERMOMETER).with("power", "0").with("attached", "true").with("facing", "north"),
            EquipmentSlot.HAND);
        assertThat(sent.get(0).key()).isEqualTo("instrument.thermometer.device");
        assertThat(sent.get(0).args()).hasSize(1);
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).properties()).containsEntry("power", "7"); // floor(800 / 1600 * 15)
    }

    @Test
    void offHandPasses() {
        DeviceResult<State> r = use(InstrumentDevices.ANEMOMETER, BlockStateRef.of(InstrumentDevices.ANEMOMETER),
            EquipmentSlot.OFF_HAND);
        assertThat(r.consumed()).isFalse();
        assertThat(sent).isEmpty();
    }

    @Test
    void anemometerAndVaneAnswer() {
        use(InstrumentDevices.ANEMOMETER, BlockStateRef.of(InstrumentDevices.ANEMOMETER), EquipmentSlot.HAND);
        use(InstrumentDevices.VANE, BlockStateRef.of(InstrumentDevices.VANE), EquipmentSlot.HAND);
        assertThat(sent).extracting(Readout::key).containsExactly("instrument.anemometer.speed", "instrument.vane.direction");
        // only the vane's arrow follows the reading (the cups rest: no spinning without ticking)
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).id()).isEqualTo(InstrumentDevices.VANE);
        assertThat(Integer.parseInt(shown.get(0).properties().get("wind"))).isBetween(0, 15);
    }

    @Test
    void clockCyclesItsModeForABuilderAndShowsTheNewMode() {
        player.setGameMode(GameMode.SURVIVAL);
        BlockStateRef hour = BlockStateRef.of(InstrumentDevices.CALENDAR_CLOCK).with("mode", "hour").with("facing", "north");
        use(InstrumentDevices.CALENDAR_CLOCK, hour, EquipmentSlot.HAND);
        assertThat(shown).hasSize(2); // the new mode, then the hands for it
        assertThat(shown.get(0).properties()).containsEntry("mode", "month");
        assertThat(sent.get(0).key()).isEqualTo("instrument.clock.month");
        // month without a timer device behind it goes back to hour
        BlockStateRef month = shown.get(0);
        use(InstrumentDevices.CALENDAR_CLOCK, month, EquipmentSlot.HAND);
        assertThat(shown).anyMatch(r -> r.properties().get("mode").equals("hour"));
        // the hands follow: the month hand rests at a twelfth of the year (13500 ticks into the calendar: month 0)
        assertThat(shown).anyMatch(r -> r.properties().containsKey("dial"));
    }

    @Test
    void clockOffersTimerModeWhenADeviceHasATimer() {
        sources = new InstrumentSources() {
            @Override
            public Optional<Float> deviceTemperature(Block device) {
                return Optional.empty();
            }

            @Override
            public Optional<Timer> timer(Block device) {
                return device.getZ() == 12 ? Optional.of(new Timer(100, 40)) : Optional.empty(); // two behind a north clock
            }
        };
        use(InstrumentDevices.CALENDAR_CLOCK,
            BlockStateRef.of(InstrumentDevices.CALENDAR_CLOCK).with("mode", "month").with("facing", "north"), EquipmentSlot.HAND);
        assertThat(shown.get(0).properties()).containsEntry("mode", "timer");
    }

    @Test
    void clockOfAnAdventurerOnlyShowsTheTime() {
        player.setGameMode(GameMode.ADVENTURE);
        use(InstrumentDevices.CALENDAR_CLOCK,
            BlockStateRef.of(InstrumentDevices.CALENDAR_CLOCK).with("mode", "hour").with("facing", "north"), EquipmentSlot.HAND);
        assertThat(shown).allMatch(r -> r.properties().get("mode").equals("hour")); // only the hands move
        assertThat(sent.get(0).key()).isEqualTo("instrument.clock.hour");
    }

    @Test
    void placementTakesTheFirstSupportedDirectionTheBuilderLooksAlong() {
        DeviceKind<State> k = kind(InstrumentDevices.THERMOMETER);
        Block block = world.getBlockAt(10, 70, 10);
        player.setLocation(new org.bukkit.Location(world, 10.5, 70, 8.5, 0, 0)); // looking south (+z)
        BlockStateRef state = BlockStateRef.of(InstrumentDevices.THERMOMETER).with("power", "3").with("attached", "false")
            .with("facing", "south");
        readable = true;
        k.handler().placed(new DeviceContext<>(k, block, state, player, EquipmentSlot.HAND, ItemStack.empty(), k.fresh(), 1L,
            StackCodec.PAPER));
        // looking south: the support is to the south, so the thermometer faces north, attached to the readable device
        assertThat(shown).isNotEmpty();
        BlockStateRef first = shown.get(0);
        assertThat(first.properties()).containsEntry("facing", "north").containsEntry("attached", "true");
        assertThat(removed).isEmpty();
    }

    @Test
    void placementWithNoSupportBreaksTheInstrument() {
        sturdy = false;
        DeviceKind<State> k = kind(InstrumentDevices.CALENDAR_CLOCK);
        Block block = world.getBlockAt(10, 70, 10);
        k.handler().placed(new DeviceContext<>(k, block,
            BlockStateRef.of(InstrumentDevices.CALENDAR_CLOCK).with("mode", "hour").with("facing", "up").with("dial", "0"), player,
            EquipmentSlot.HAND, ItemStack.empty(), k.fresh(), 1L, StackCodec.PAPER));
        assertThat(removed).hasSize(1);
        assertThat(shown).isEmpty();
    }

    @Test
    void aLostSupportBreaksTheThermometerAndTheClock() {
        BlockStateRef thermometer = BlockStateRef.of(InstrumentDevices.THERMOMETER).with("power", "0").with("attached", "false")
            .with("facing", "north");
        BlockStateRef clock = BlockStateRef.of(InstrumentDevices.CALENDAR_CLOCK).with("mode", "hour").with("facing", "up")
            .with("dial", "0");
        neighbourChanged(InstrumentDevices.THERMOMETER, thermometer);
        neighbourChanged(InstrumentDevices.CALENDAR_CLOCK, clock);
        assertThat(removed).isEmpty();
        sturdy = false;
        neighbourChanged(InstrumentDevices.THERMOMETER, thermometer);
        neighbourChanged(InstrumentDevices.CALENDAR_CLOCK, clock);
        assertThat(removed).hasSize(2);
        // a readable device behind holds them without a sturdy face (TFC thermometer_readable / clock_readable)
        removed.clear();
        readable = true;
        neighbourChanged(InstrumentDevices.THERMOMETER, thermometer);
        neighbourChanged(InstrumentDevices.CALENDAR_CLOCK, clock);
        assertThat(removed).isEmpty();
    }

    @Test
    void theAnemometerNeedsACentreSupportAndTheVaneAnAnemometerOrACentreSupport() {
        BlockStateRef anemometer = BlockStateRef.of(InstrumentDevices.ANEMOMETER).with("attached_wind_devices", "false");
        BlockStateRef vane = BlockStateRef.of(InstrumentDevices.VANE).with("attached_wind_devices", "false").with("wind", "0");
        center = false;
        neighbourChanged(InstrumentDevices.ANEMOMETER, anemometer);
        neighbourChanged(InstrumentDevices.VANE, vane);
        assertThat(removed).hasSize(2);
        removed.clear();
        ids.put("10,69,10", InstrumentDevices.ANEMOMETER); // an anemometer below holds a vane without a centre support
        neighbourChanged(InstrumentDevices.VANE, vane);
        assertThat(removed).isEmpty();
        assertThat(shown.get(0).properties()).containsEntry("attached_wind_devices", "true");
    }

    @Test
    void theWindDevicesAttachWhenTheirPartnerAppearsAbove() {
        BlockStateRef anemometer = BlockStateRef.of(InstrumentDevices.ANEMOMETER).with("attached_wind_devices", "false");
        ids.put("10,71,10", InstrumentDevices.VANE);
        neighbourChanged(InstrumentDevices.ANEMOMETER, anemometer);
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).properties()).containsEntry("attached_wind_devices", "true");
        // and detach when the vane goes
        ids.clear();
        shown.clear();
        neighbourChanged(InstrumentDevices.ANEMOMETER, anemometer.with("attached_wind_devices", "true"));
        assertThat(shown.get(0).properties()).containsEntry("attached_wind_devices", "false");
    }

    @Test
    void aThermometerFollowsItsReadingOnANeighbourChange() {
        BlockStateRef state = BlockStateRef.of(InstrumentDevices.THERMOMETER).with("power", "3").with("attached", "true")
            .with("facing", "north");
        neighbourChanged(InstrumentDevices.THERMOMETER, state);
        // nothing answers for the device behind: temperature 0 gives power 0, written because it differs from 3
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).properties()).containsEntry("power", "0");
    }
}
