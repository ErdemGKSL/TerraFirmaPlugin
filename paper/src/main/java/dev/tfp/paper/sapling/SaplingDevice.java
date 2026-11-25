/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java @ c3b4d9536 (randomTick, setPlacedBy, getTicksToGrow),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.sapling.SaplingRules;
import dev.tfp.core.sapling.SaplingState;
import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.Wood;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import org.bukkit.block.Block;

/**
 * The twenty wood saplings ({@code tfp:wood/sapling/<wood>}, PAR-FARM-10) as device kinds with one handler. A sapling stores the
 * calendar tick it was planted on; nothing is ticked. It is settled when it is placed, when a neighbour changes, and by the catch-up
 * kind {@link #CATCH_UP_KIND}: once more calendar ticks than the wood needs have passed, with light 9 above it, the wood's tree
 * feature is planned over the world and, if it fits, replaces the sapling (TFC {@code advanceTree}).
 */
public final class SaplingDevice {
    public static final String ID = "tfp:sapling";
    public static final String CATCH_UP_KIND = ID;
    /** A sapling without a stored planting tick (placed without our handler) starts counting when it is first settled. */
    public static final SaplingState UNSET = new SaplingState(-1L);

    public record Services(SaplingEffects effects, Supplier<ClockSnapshot> clock, DoubleSupplier growthModifier) {
        public Services {
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(growthModifier, "growthModifier");
        }

        public Services(SaplingEffects effects, Supplier<ClockSnapshot> clock) {
            this(effects, clock, () -> SaplingRules.DEFAULT_MODIFIER);
        }
    }

    /** What settling a sapling decided: the state to store, and the tree that replaces it (empty to stay a sapling). */
    public record Outcome(SaplingState state, Optional<Tree> tree) {}

    private SaplingDevice() {
    }

    /** The device kinds, one per wood, sharing one handler. */
    public static List<DeviceKind<SaplingState>> kinds(Services services) {
        Handler handler = new Handler(services);
        return java.util.Arrays.stream(Wood.values())
            .map(wood -> DeviceKind.builder(SaplingRules.blockId(wood), SaplingState.CODEC, UNSET)
                .slots(0)
                .removal(DeviceKind.RemoveBehaviour.NOOP)
                .handler(handler)
                .build())
            .toList();
    }

    /** All block ids of the kinds, for tests. */
    public static String[] allIds() {
        return java.util.Arrays.stream(Wood.values()).map(SaplingRules::blockId).toArray(String[]::new);
    }

    /**
     * TFC {@code randomTick} without the dice: the sapling grows when its counter is past the wood's ticks, there is light above it
     * and the feature fits. Pure apart from the reads of {@link SaplingEffects}.
     */
    public static Outcome settle(Services s, Block block, Wood wood, SaplingState stored) {
        long now = s.clock().get().calendarTicks();
        if (stored.plantedAt() < 0 || stored.plantedAt() > now) {
            return new Outcome(new SaplingState(now), Optional.empty());
        }
        long needed = SaplingRules.ticksToGrow(wood, s.growthModifier().getAsDouble());
        if (!SaplingRules.elapsed(stored.ticksSince(now), needed) || !SaplingRules.lightOk(s.effects().lightAbove(block))) {
            return new Outcome(stored, Optional.empty());
        }
        return new Outcome(stored, s.effects().plan(block, wood, seed(block, now)));
    }

    /** The random seed of one growth attempt: the position and the calendar tick (a retry on another tick draws another tree). */
    static long seed(Block block, long now) {
        long h = block.getWorld().getSeed();
        h = h * 0x9E3779B97F4A7C15L + block.getX();
        h = h * 0x9E3779B97F4A7C15L + block.getY();
        h = h * 0x9E3779B97F4A7C15L + block.getZ();
        return h * 0x9E3779B97F4A7C15L + now;
    }

    private record Handler(Services services) implements DeviceHandler<SaplingState> {
        /** TFC {@code setPlacedBy}: the counter starts now. */
        @Override
        public DeviceResult<SaplingState> placed(DeviceContext<SaplingState> ctx) {
            if (SaplingRules.woodOf(ctx.state().id()) == null) {
                return DeviceResult.pass();
            }
            services.effects().track(ctx.block());
            return DeviceResult.update(ctx.record().withState(new SaplingState(services.clock().get().calendarTicks())));
        }

        @Override
        public DeviceResult<SaplingState> neighbourChanged(DeviceContext<SaplingState> ctx) {
            Wood wood = SaplingRules.woodOf(ctx.state().id());
            if (wood == null) {
                return DeviceResult.pass();
            }
            Outcome outcome = settle(services, ctx.block(), wood, ctx.deviceState());
            if (outcome.tree().isPresent()) {
                services.effects().grow(ctx.block(), outcome.tree().get());
                return DeviceResult.pass();
            }
            return outcome.state().equals(ctx.deviceState()) ? DeviceResult.pass() : DeviceResult.update(ctx.record().withState(outcome.state()));
        }
    }
}
