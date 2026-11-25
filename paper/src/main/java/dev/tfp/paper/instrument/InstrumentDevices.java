/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java @ c3b4d9536 (ATTACHED, POWER, FACING),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java @ c3b4d9536 (MODE, FACING,
 *       useWithoutItem), src/main/java/net/dries007/tfc/common/blocks/devices/VaneBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/AnemometerBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blockentities/ThermometerBlockEntity.java @ c3b4d9536 (updatePower)
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.instrument.Instruments.ClockMode;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.instrument.InstrumentReadout.Capture;
import dev.tfp.paper.instrument.InstrumentReadout.Kind;
import dev.tfp.paper.instrument.InstrumentReadout.Result;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * PAR-CLIM-09: the thermometer, anemometer, weather vane and calendar clock as four device kinds on the device framework.
 * They keep no state of their own (the record is empty): every reading is a pure function of the world and the calendar
 * ({@link InstrumentReadout}), taken when a player right-clicks (no block ticking: TFC's 2 second power updates and its
 * client-side needle animation have no counterpart on a vanilla client).
 *
 * <ul>
 *   <li>A right click shows the instrument's line on the player's action bar. The capture is taken here, on the block's
 *       region thread; the climate is computed on the worker pool; the line is sent on the player's own thread.</li>
 *   <li>A thermometer also writes its {@code power} state from the reading (the needle picture), re-checking the block on
 *       its region thread first. It is refreshed on a click, on placement and when a neighbour changes.</li>
 *   <li>A clock cycles its {@code mode} state on a right click of a player who may build (TFC {@code useWithoutItem}).</li>
 * </ul>
 */
public final class InstrumentDevices {
    public static final String THERMOMETER = "tfp:thermometer";
    public static final String ANEMOMETER = "tfp:anemometer";
    public static final String VANE = "tfp:vane";
    public static final String CALENDAR_CLOCK = "tfp:calendar_clock";

    /** The vane arrow's resting step 0..15 and the clock's dial (PAR-CLIM-28; see the instrument datagen). */
    public static final String WIND = "wind";
    public static final String DIAL = "dial";
    /** TFC {@code BlockStateProperties.POWER}. */
    public static final String POWER = "power";
    /** TFC {@code TFCBlockStateProperties.THERMOMETER_ATTACHED}. */
    public static final String ATTACHED = "attached";
    /** TFC {@code TFCBlockStateProperties.CLOCK_MODE} ({@code hour}, {@code month}, {@code timer}). */
    public static final String MODE = "mode";
    /** TFC {@code HORIZONTAL_FACING} (thermometer) and {@code FACING} (clock). */
    public static final String FACING = "facing";

    /** The empty state every instrument keeps. */
    public record State() {
        public static final State INSTANCE = new State();
        public static final VersionedCodec<State> CODEC = VersionedCodec.<State>builder("instrument", 1)
            .build((s, out) -> { }, in -> INSTANCE);
    }

    /**
     * What the instruments read from the rest of the plugin.
     *
     * @param sources    the device behind a thermometer or clock
     * @param clock      the published calendar clock
     * @param windScale  TFC {@code oceanWindScale}
     * @param compute    runs a pure computation off the region thread and hands the result to the callback (the worker pool;
     *                   tests run it inline); the callback runs wherever the computation ran
     * @param toPlayer   runs a task on the thread that owns the player
     * @param toBlock    runs a task on the region thread that owns the block
     * @param show       writes a block state (the module's {@code BlockVisuals}), region thread
     * @param send       sends a readout line to a player (the action bar), player thread
     * @param support    the world questions of the placement, support and attach rules (PAR-CLIM-28)
     * @param remove     breaks the instrument (its loot drops), region thread
     */
    public record Services(InstrumentSources sources, Supplier<ClockSnapshot> clock, int windScale, Computer compute,
            BiConsumer<Player, Runnable> toPlayer, BiConsumer<Block, Runnable> toBlock, BiConsumer<Block, BlockStateRef> show,
            BiConsumer<Player, InstrumentReadout.Readout> send, InstrumentRules.Support support, java.util.function.Consumer<Block> remove) {
        public Services {
            Objects.requireNonNull(sources, "sources");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(compute, "compute");
            Objects.requireNonNull(toPlayer, "toPlayer");
            Objects.requireNonNull(toBlock, "toBlock");
            Objects.requireNonNull(show, "show");
            Objects.requireNonNull(send, "send");
            Objects.requireNonNull(support, "support");
            Objects.requireNonNull(remove, "remove");
        }
    }

    /** Runs {@code work} off the region thread, then {@code then} with its result. */
    @FunctionalInterface
    public interface Computer {
        <T> void run(Supplier<T> work, java.util.function.Consumer<T> then);
    }

    private InstrumentDevices() {
    }

    /** The four kinds. */
    public static java.util.List<DeviceKind<State>> kinds(Services services) {
        return java.util.List.of(kind(THERMOMETER, Kind.THERMOMETER, services), kind(ANEMOMETER, Kind.ANEMOMETER, services),
            kind(VANE, Kind.VANE, services), kind(CALENDAR_CLOCK, Kind.CALENDAR_CLOCK, services));
    }

    static DeviceKind<State> kind(String id, Kind kind, Services services) {
        return DeviceKind.builder(id, State.CODEC, State.INSTANCE)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(id, kind, services))
            .build();
    }

    /** The block face a state property names ({@code facing=north}), or null. */
    static BlockFace face(BlockStateRef state) {
        String v = state.properties().get(FACING);
        if (v == null) {
            return null;
        }
        try {
            return BlockFace.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** TFC {@code pos.relative(facing.getOpposite())}: the block the instrument is mounted on. */
    static Block behind(Block block, BlockStateRef state) {
        BlockFace f = face(state);
        return f == null ? null : block.getRelative(f.getOppositeFace());
    }

    static ClockMode mode(BlockStateRef state) {
        String v = state.properties().get(MODE);
        if (v == null) {
            return ClockMode.HOUR;
        }
        try {
            return ClockMode.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ClockMode.HOUR;
        }
    }

    /** The timer of the device behind a clock, or two behind it (TFC {@code CalendarClockBlockEntity.serverTick}). */
    static InstrumentSources.Timer timerBehind(Services s, Block block, BlockStateRef state) {
        BlockFace f = face(state);
        if (f == null) {
            return null;
        }
        Block one = block.getRelative(f.getOppositeFace());
        Optional<InstrumentSources.Timer> t = s.sources().timer(one);
        if (t.isEmpty()) {
            t = s.sources().timer(one.getRelative(f.getOppositeFace()));
        }
        return t.orElse(null);
    }

    /** Takes the capture of the instrument at {@code block}. Region thread. */
    static Capture capture(Services s, Block block, BlockStateRef state, Kind kind) {
        boolean attached = "true".equals(state.properties().get(ATTACHED));
        float device = Float.NaN;
        if (kind == Kind.THERMOMETER && attached) {
            Block b = behind(block, state);
            if (b != null) {
                device = s.sources().deviceTemperature(b).orElse(Float.NaN);
            }
        }
        InstrumentSources.Timer timer = kind == Kind.CALENDAR_CLOCK ? timerBehind(s, block, state) : null;
        String biome = block.getBiome().getKey().getKey();
        return new Capture(block.getWorld().getSeed(), block.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL,
            new BlockPos(block.getX(), block.getY(), block.getZ()), InstrumentReadout.OCEAN_BIOMES.contains(biome),
            block.getWorld().getTime() % 24_000 < 12_000, s.clock().get(), s.windScale(), attached, device, mode(state), timer);
    }

    private record Handler(String id, Kind kind, Services services) implements DeviceHandler<State> {

        @Override
        public DeviceResult<State> use(DeviceContext<State> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() != EquipmentSlot.HAND) {
                return DeviceResult.pass();
            }
            BlockStateRef state = ctx.state();
            final Block block = ctx.block();
            if (kind == Kind.CALENDAR_CLOCK && mayBuild(player)) {
                state = cycleMode(block, state); // the readout then names the new mode
            }
            final BlockStateRef shown = state;
            Capture c = capture(services, block, shown, kind);
            services.compute().run(() -> InstrumentReadout.compute(c, kind), result -> {
                services.toPlayer().accept(player, () -> services.send().accept(player, result.readout()));
                applyVisual(block, shown, result);
            });
            return DeviceResult.consume();
        }

        /**
         * TFC {@code getStateForPlacement}: a thermometer or clock takes the facing of the first look direction that has a
         * support behind it (none: the placement is refused); a thermometer on a readable device is {@code attached}; a vane
         * on an anemometer (or the other way round) is {@code attached_wind_devices}.
         */
        @Override
        public DeviceResult<State> placed(DeviceContext<State> ctx) {
            Block block = ctx.block();
            BlockStateRef state = ctx.state();
            Player player = ctx.player();
            if ((kind == Kind.THERMOMETER || kind == Kind.CALENDAR_CLOCK) && player != null) {
                BlockFace f = InstrumentRules.placementFacing(kind, block, player.getEyeLocation().getDirection(),
                    kind == Kind.THERMOMETER, services.support());
                if (f == null) {
                    services.remove().accept(block);
                    return DeviceResult.pass();
                }
                state = state.with(FACING, f.name().toLowerCase(Locale.ROOT));
                if (kind == Kind.THERMOMETER) {
                    state = state.with(ATTACHED, Boolean.toString(InstrumentRules.thermometerAttached(block, f, services.support())));
                }
                state = write(block, ctx.state(), state);
            }
            return settle(ctx, state);
        }

        /** TFC {@code updateShape}: a lost support breaks the instrument; the wind devices re-attach. */
        @Override
        public DeviceResult<State> neighbourChanged(DeviceContext<State> ctx) {
            return settle(ctx, ctx.state());
        }

        private DeviceResult<State> settle(DeviceContext<State> ctx, BlockStateRef state) {
            Block block = ctx.block();
            if (!InstrumentRules.canSurvive(kind, block, state, services.support())) {
                services.remove().accept(block);
                return DeviceResult.pass();
            }
            if (kind == Kind.ANEMOMETER || kind == Kind.VANE) {
                state = write(block, state, state.with("attached_wind_devices",
                    Boolean.toString(InstrumentRules.windAttached(kind, block, services.support()))));
            }
            refresh(block, state);
            return DeviceResult.pass();
        }

        /** Writes {@code next} when it differs from {@code was} (region thread); returns the state the block now has. */
        private BlockStateRef write(Block block, BlockStateRef was, BlockStateRef next) {
            if (!next.equals(was)) {
                services.show().accept(block, next);
            }
            return next;
        }

        /**
         * TFC {@code needsInstantUpdate}: the moving parts follow the reading (a thermometer's power, the vane arrow, the
         * clock hands); decide off-thread, apply on the region.
         */
        private void refresh(Block block, BlockStateRef state) {
            if (kind == Kind.ANEMOMETER) {
                return;
            }
            Capture c = capture(services, block, state, kind);
            services.compute().run(() -> InstrumentReadout.compute(c, kind), result -> applyVisual(block, state, result));
        }

        /** Decide async, apply sync: the write re-checks that the block is still the state the reading was taken for. */
        private void applyVisual(Block block, BlockStateRef was, Result result) {
            BlockStateRef next = was;
            switch (kind) {
                case THERMOMETER -> next = set(next, POWER, result.power());
                case VANE -> next = set(next, WIND, result.wind());
                case CALENDAR_CLOCK -> next = set(next, DIAL, result.dial());
                default -> { }
            }
            if (next.equals(was)) {
                return;
            }
            BlockStateRef target = next;
            services.toBlock().accept(block, () -> services.show().accept(block, target));
        }

        private static BlockStateRef set(BlockStateRef state, String property, int value) {
            return value < 0 ? state : state.with(property, Integer.toString(value));
        }

        /** TFC {@code useWithoutItem} for a player who {@code mayBuild}: next mode, written to the block. */
        private BlockStateRef cycleMode(Block block, BlockStateRef state) {
            InstrumentSources.Timer timer = timerBehind(services, block, state);
            ClockMode next = InstrumentReadout.nextMode(mode(state), timer != null);
            BlockStateRef changed = state.with(MODE, next.name().toLowerCase(Locale.ROOT));
            services.show().accept(block, changed);
            return changed;
        }

        /** TFC {@code Player.mayBuild}: not in adventure or spectator mode. */
        private static boolean mayBuild(Player player) {
            GameMode m = player.getGameMode();
            return m != GameMode.ADVENTURE && m != GameMode.SPECTATOR;
        }
    }

    /** For the tests: the record every instrument keeps. */
    static DeviceRecord<State> fresh() {
        return DeviceRecord.fresh(State.INSTANCE, 0);
    }

    /** For the tests: the properties of a state. */
    static Map<String, String> properties(BlockStateRef state) {
        return state.properties();
    }
}
