/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (getStateForPlacement,
 *       randomTick, tick, growAndPropagate, placeBlockAndResetCounter, getNewState, getDeadState),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn, stateAfterPicking, setPlacedBy),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBerryBushBlock.java @ c3b4d9536 (useItemOn passes, randomTick),
 *       src/main/java/net/dries007/tfc/common/blockentities/BerryBushBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.berry;

import dev.tfp.core.berry.BerryBush;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.BerryTicker;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The stationary berry bushes (PAR-FARM-06) as one device kind {@code tfp:berry_bush}: the blocks are {@code tfp:berry_bush/<species>}
 * (properties {@code lifecycle}, {@code stage}) and {@code tfp:berry_bush/dead} (property {@code stage}); the species are
 * {@link BushSpecies} without the cranberry (a waterlogged bush, PAR-FARM-07). The rules are {@link BerryRules} and {@link BerryTicker};
 * the bush is never ticked. It is brought up to date by {@link #settle} when it is placed, clicked, updated by a neighbour, and by the
 * catch-up kind {@code tfp:berry_bush} ({@link BerryModule}) on chunk load and the visible-area sweep.
 *
 * <p>Right click on a fruiting bush with any item: the product item goes to the player and the bush turns healthy, its last-picked tick set
 * (TFC {@code useItemOn}); any other click passes. Propagation (TFC {@code growAndPropagate}) runs from the catch-up: the crowding test, up
 * to three triangle-offset tries, the child with its banked cycles and growth budget and TFC's immediate tick.
 */
public final class BerryBushDevice {
    public static final String ID = "tfp:berry_bush";
    public static final String DEAD_ID = ID + "/dead";
    /** TFC {@code SeasonalPlantBlock.LIFECYCLE} and {@code STAGE} ({@code STAGE_2}). */
    public static final String LIFECYCLE = "lifecycle";
    public static final String STAGE = "stage";
    /** The catch-up kind id of the chunk index. */
    public static final String CATCH_UP_KIND = ID;
    /** The food item id of a species' berry (TFC {@code Food.<SPECIES>}). */
    public static final String PRODUCT_PREFIX = "tfp:food/";

    /** @param clock the calendar clock (player ticks, calendar ticks, days per month) */
    public record Services(BerryEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings) {
        public Services {
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(settings, "settings");
        }
    }

    private BerryBushDevice() {
    }

    /** The species of a block id: {@code tfp:berry_bush/snowberry} is snowberry; the dead bush and the cranberry have none. */
    public static Optional<BushSpecies> species(String blockId) {
        String prefix = ID + "/";
        if (!blockId.startsWith(prefix) || blockId.equals(DEAD_ID)) {
            return Optional.empty();
        }
        String name = blockId.substring(prefix.length()).toUpperCase(Locale.ROOT);
        for (BushSpecies s : BushSpecies.values()) {
            if (s != BushSpecies.CRANBERRY && s.name().equals(name)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** The block id of a species. */
    public static String blockId(BushSpecies species) {
        return ID + "/" + species.name().toLowerCase(Locale.ROOT);
    }

    /** The product item id of a species. */
    public static String productId(BushSpecies species) {
        return PRODUCT_PREFIX + species.name().toLowerCase(Locale.ROOT);
    }

    /** The species whose bush blocks the kind covers: every species but the cranberry. */
    public static List<BushSpecies> species() {
        List<BushSpecies> out = new ArrayList<>();
        for (BushSpecies s : BushSpecies.values()) {
            if (s != BushSpecies.CRANBERRY) {
                out.add(s);
            }
        }
        return out;
    }

    /** The block state of a stored bush: the dead block keeps the stage. */
    public static BlockStateRef state(BushSpecies species, BerryState state) {
        BerryBush b = state.bush();
        if (state.dead()) {
            return BlockStateRef.of(DEAD_ID).with(STAGE, Integer.toString(b.stage()));
        }
        return BlockStateRef.of(blockId(species)).with(LIFECYCLE, b.lifecycle().name().toLowerCase(Locale.ROOT))
            .with(STAGE, Integer.toString(b.stage()));
    }

    /** The device kind: no slots, no framework catch-up (the position-aware {@link #settle} is the catch-up). */
    public static DeviceKind<BerryState> kind(Services services) {
        BerryBush initial = new BerryBush(Lifecycle.HEALTHY, 0, BerryRules.GROWTHS_INITIAL, 0L, BerryState.NEVER_PICKED);
        return DeviceKind.builder(ID, BerryState.CODEC, new BerryState(false, initial, 0L, 50, 10f, true))
            .slots(0)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(services))
            .build();
    }

    /** The clock anchored at {@code now} for a bush in the given hemisphere. */
    static BerryClock clock(ClockSnapshot snapshot, long now, boolean northern) {
        return new BerryClock(snapshot.playerTicks(), snapshot.calendarTicks(), snapshot.daysInMonth(), northern).at(now);
    }

    /** What {@link #settle} decided; {@link #apply} writes it to the world after the record is stored. */
    public record Settled(BerryState state, BushSpecies species, BerryTicker.Settled step, long now) {
    }

    /**
     * Brings a stored bush up to {@code now} with a fresh environment sample (TFC reads the world at each random tick): pure but for the
     * sample read. Region thread of {@code block}.
     */
    public static Settled settle(Services s, Block block, BushSpecies species, BerryState stored, long now) {
        BerryEffects.Environment env = s.effects().environment(block);
        BerryState sampled = stored.withEnvironment(env.hydration(), env.temperature(), env.northern());
        BerryClock clock = clock(s.clock().get(), now, env.northern());
        BerryTicker.Settled step = BerryTicker.settle(species, sampled, now, clock, s.settings());
        return new Settled(step.state(), species, step, now);
    }

    /**
     * The world effects of a settled bush, after its record was stored: remove a dead bush that is old enough, show the visual state, list the
     * bush for catch-up, run the propagations.
     */
    public static void apply(Services s, Block block, Settled settled) {
        BerryEffects fx = s.effects();
        if (settled.step().remove()) {
            fx.remove(block);
            return;
        }
        fx.show(block, state(settled.species(), settled.state()), settled.state());
        fx.track(block);
        propagate(s, block, settled.species(), settled.step().propagations(), settled.now(), 0);
    }

    private static void propagate(Services s, Block parent, BushSpecies species, List<BerryRules.Propagation> propagations, long now, int depth) {
        if (depth >= BerryTicker.MAX_CASCADE) {
            return;
        }
        BerryEffects fx = s.effects();
        String id = blockId(species);
        for (BerryRules.Propagation p : propagations) {
            if (BerryTicker.crowded(fx.bushesInBox(parent, id))) {
                return;
            }
            Optional<BerryTicker.Child> child = BerryTicker.propagate(fx.random(), p, now, s.settings(),
                d -> fx.canPlant(parent.getRelative(d[0], d[1], d[2])));
            if (child.isEmpty()) {
                continue;
            }
            BerryTicker.Child c = child.get();
            Block target = parent.getRelative(c.dx(), c.dy(), c.dz());
            BerryEffects.Environment env = fx.environment(target);
            BerryClock clock = clock(s.clock().get(), now, env.northern());
            // TFC placeBlockAndResetCounter ends with the new block's own tick (onUpdate and the growth scan, no climate check).
            BerryRules.Tick tick = BerryRules.randomTick(species, c.bush(), now, clock, s.settings());
            BerryState state = new BerryState(false, tick.bush(), 0L, env.hydration(), env.temperature(), env.northern());
            fx.plant(target, state(species, state), state);
            fx.track(target);
            propagate(s, target, species, tick.propagations(), now, depth + 1);
        }
    }

    private record Handler(Services services) implements DeviceHandler<BerryState> {
        /** TFC {@code getStateForPlacement} and {@code setPlacedBy}: healthy in the growing season, dormant otherwise; counters reset. */
        @Override
        public DeviceResult<BerryState> placed(DeviceContext<BerryState> ctx) {
            Optional<BushSpecies> species = species(ctx.state().id());
            if (species.isEmpty()) {
                return DeviceResult.pass();
            }
            BerryEffects.Environment env = services.effects().environment(ctx.block());
            BerryClock clock = clock(services.clock().get(), ctx.now(), env.northern());
            BerryBush bush = BerryRules.place(species.get(), ctx.now(), clock);
            BerryState state = new BerryState(false, bush, 0L, env.hydration(), env.temperature(), env.northern());
            services.effects().show(ctx.block(), state(species.get(), state), state);
            services.effects().track(ctx.block());
            return DeviceResult.update(ctx.record().withState(state));
        }

        /** TFC {@code SeasonalPlantBlock.useItemOn}: a fruiting bush is picked; the dead bush and every other state pass. */
        @Override
        public DeviceResult<BerryState> useWithItem(DeviceContext<BerryState> ctx) {
            Optional<BushSpecies> species = species(ctx.state().id());
            if (species.isEmpty() || ctx.player() == null) {
                return DeviceResult.pass();
            }
            Settled settled = settle(services, ctx.block(), species.get(), ctx.deviceState(), ctx.now());
            BerryState state = settled.state();
            if (state.dead() || state.bush().lifecycle() != Lifecycle.FRUITING) {
                return result(ctx, settled, false);
            }
            Optional<ItemStack> product = services.effects().product(species.get());
            if (product.isEmpty()) {
                return result(ctx, settled, false);
            }
            services.effects().pickSound(ctx.block());
            services.effects().give(ctx.player(), product.get());
            BerryRules.Pick pick = BerryRules.pick(state.bush(), ctx.now());
            Settled picked = new Settled(state.withBush(pick.bush()), species.get(), settled.step(), ctx.now());
            return result(ctx, picked, true);
        }

        /** A neighbour update is the catch-up's visit: settle and show. */
        @Override
        public DeviceResult<BerryState> neighbourChanged(DeviceContext<BerryState> ctx) {
            Optional<BushSpecies> species = species(ctx.state().id());
            if (species.isEmpty()) {
                return DeviceResult.pass();
            }
            return result(ctx, settle(services, ctx.block(), species.get(), ctx.deviceState(), ctx.now()), false);
        }

        private DeviceResult<BerryState> result(DeviceContext<BerryState> ctx, Settled settled, boolean consumed) {
            apply(services, ctx.block(), settled);
            if (settled.step().remove()) {
                return DeviceResult.pass(); // the block is gone: nothing to store
            }
            DeviceRecord<BerryState> record = ctx.record().withState(settled.state());
            if (consumed) {
                return DeviceResult.consume(record);
            }
            return settled.state().equals(ctx.deviceState()) ? DeviceResult.pass() : DeviceResult.update(record);
        }
    }
}
