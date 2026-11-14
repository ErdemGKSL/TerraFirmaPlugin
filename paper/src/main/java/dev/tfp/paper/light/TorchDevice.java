/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (useItemOn, randomTick, setPlacedBy),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCWallTorchBlock.java @ c3b4d9536 (useItemOn, randomTick, setPlacedBy),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick,
 *       resetCounter)
 */
package dev.tfp.paper.light;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.light.TorchRules;
import dev.tfp.core.light.TorchRules.Kind;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-10: the lit torch and wall torch as device kinds ({@code tfp:torch}, {@code tfp:wall_torch}; the CraftEngine
 * blocks carry the {@code tfp:device} behaviour). The record holds TFC's {@code TickCounterBlockEntity}: the calendar
 * tick the torch was placed or lit at. The dead torches are plain blocks without a record, as TFC's
 * {@code DeadTorchBlock} has no block entity.
 *
 * <ul>
 *   <li>Placed ({@code setPlacedBy}): the counter is set to now and a burnout callback is queued for when the burn time
 *       is over.</li>
 *   <li>Right click ({@code useItemOn}): always consumed; holding an item of {@code #tfc:can_be_lit_on_torch} takes one
 *       and gives a lit torch.</li>
 *   <li>A torch whose burn time is over when it is clicked or notified ({@code randomTick}'s swap) is handed to the
 *       burnout pipeline and behaves as the dead torch it is about to become (no use).</li>
 * </ul>
 */
public final class TorchDevice {
    /** The tick the torch was lit at; {@link #NEVER_LIT} for a torch that was never stamped (placed without a player). */
    public record State(long litTick) {
    }

    /**
     * TFC {@code TickCounterBlockEntity.lastUpdateTick}'s initial value: a torch placed without {@code setPlacedBy} (a
     * command, another plugin) counts as burnt out, as in TFC, where it dies at its first random tick.
     */
    public static final long NEVER_LIT = Integer.MIN_VALUE;
    public static final State FRESH = new State(NEVER_LIT);
    /** TFC {@code TickCounterBlockEntity}'s NBT {@code tick}. */
    public static final VersionedCodec<State> CODEC = VersionedCodec.<State>builder("torch", 1)
        .build((s, out) -> out.putLong("lit", s.litTick()), in -> new State(in.requireLong("lit")));

    /**
     * What the torch needs from the server.
     *
     * @param torchTicks TFC {@code torchTicks} (read per use; {@code -1} disables burnout)
     * @param canBeLit   whether a stack is in {@code #tfc:can_be_lit_on_torch}
     * @param torch      a new lit torch item ({@code tfp:torch})
     */
    public record Services(IntSupplier torchTicks, Predicate<ItemStack> canBeLit, Supplier<ItemStack> torch,
                           TorchEffects effects) {
        public Services {
            Objects.requireNonNull(torchTicks, "torchTicks");
            Objects.requireNonNull(canBeLit, "canBeLit");
            Objects.requireNonNull(torch, "torch");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private final Services services;
    private final DeviceKind<State> torch;
    private final DeviceKind<State> wallTorch;

    public TorchDevice(Services services) {
        this.services = Objects.requireNonNull(services, "services");
        this.torch = build(Kind.TORCH);
        this.wallTorch = build(Kind.WALL_TORCH);
    }

    private DeviceKind<State> build(Kind kind) {
        return DeviceKind.builder(kind.id(), CODEC, FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(kind))
            .build();
    }

    /** Both kinds: {@code tfp:torch} and {@code tfp:wall_torch}. */
    public List<DeviceKind<State>> kinds() {
        return List.of(torch, wallTorch);
    }

    /** The device kind of a lit torch kind. */
    public DeviceKind<State> kind(Kind kind) {
        if (!kind.lit()) {
            throw new IllegalArgumentException(kind + " is no device: the dead torches have no counter");
        }
        return kind.wall() ? wallTorch : torch;
    }

    /** Whether the torch of {@code record} is burnt out at {@code now} (TFC {@code onRandomTick}'s test). */
    boolean burntOut(Kind kind, DeviceRecord<State> record, long now) {
        return TorchRules.afterCatchUp(kind, record.state().litTick(), now, services.torchTicks().getAsInt()) != kind;
    }

    /** Queues the burnout callback of a torch lit at {@code litTick}, if it can burn out. */
    void queueBurnout(Block block, long litTick, long now) {
        long due = TorchRules.burnoutTick(litTick, services.torchTicks().getAsInt());
        if (due != LightSourceRules.NEVER) {
            services.effects().burnoutIn(block, Math.max(1L, due - now));
        }
    }

    private final class Handler implements DeviceHandler<State> {
        private final Kind kind;

        Handler(Kind kind) {
            this.kind = kind;
        }

        /** TFC {@code setPlacedBy}: {@code resetCounter}. */
        @Override
        public DeviceResult<State> placed(DeviceContext<State> ctx) {
            queueBurnout(ctx.block(), ctx.now(), ctx.now());
            return DeviceResult.update(ctx.record().withState(new State(ctx.now())));
        }

        /** TFC {@code useItemOn}; the wall torch asks the standing torch, which is the same rule. */
        @Override
        public DeviceResult<State> useWithItem(DeviceContext<State> ctx) {
            DeviceRecord<State> record = ctx.record();
            if (burntOut(kind, record, ctx.now())) {
                services.effects().burnout(ctx.block());
                return DeviceResult.pass(); // the dead torch it becomes has no use
            }
            boolean canBeLit = !StackCodec.isEmpty(ctx.item()) && services.canBeLit().test(ctx.item());
            if (TorchRules.lightsHeldItem(kind, canBeLit) && ctx.player() != null && ctx.hand() != null) {
                ItemStack lit = services.torch().get();
                if (!StackCodec.isEmpty(lit)) {
                    services.effects().takeOne(ctx.player(), ctx.hand());
                    services.effects().give(ctx.player(), lit);
                }
            }
            return TorchRules.useConsumes(kind) ? DeviceResult.consume() : DeviceResult.pass();
        }

        /**
         * A neighbour update checks the burn time (TFC's random tick would have swapped the torch by now). A torch not
         * stamped yet is skipped: placing one updates its neighbours before its placement callback stamps it.
         */
        @Override
        public DeviceResult<State> neighbourChanged(DeviceContext<State> ctx) {
            if (ctx.record().state().litTick() != NEVER_LIT && burntOut(kind, ctx.record(), ctx.now())) {
                services.effects().burnout(ctx.block());
            }
            return DeviceResult.pass();
        }
    }
}
