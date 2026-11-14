/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java @ c3b4d9536 (toggleSeal, useItemOn,
 *       onPlace, neighborChanged, onBlockExploded),
 *       src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (serverTick, explode,
 *       setLit, isItemValid, PowderkegInventory),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/SealableDeviceBlock.java @ c3b4d9536 (beforeRemove: a sealed
 *       keg keeps its contents)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.powderkeg.PowderkegConfig;
import dev.tfp.core.powderkeg.PowderkegRules;
import dev.tfp.core.powderkeg.PowderkegState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-18: the powderkeg as a device kind ({@code tfp:powderkeg}): TFC's {@code PowderkegBlock} +
 * {@code PowderkegBlockEntity} on the device framework, with the rules of {@code core/powderkeg}.
 *
 * <ul>
 *   <li>Twelve slots of {@code #tfc:usable_in_powder_keg}; while sealed they do not change ({@code canModify}).</li>
 *   <li>Right click, TFC's order: sneaking with an empty hand puts a lit keg out, else toggles the seal (unsealing a lit
 *       keg puts it out; sealing checks the redstone signal); any other click opens the screen.</li>
 *   <li>Lit by a strong fire start on a sealed keg ({@link #lightAt} from the module's {@code FireLightable}), by a
 *       redstone signal at a sealed keg (placement, neighbour update, sealing) and by another explosion (the blast
 *       listener). Lazy fuse: the record keeps the tick the fuse ends ({@link PowderkegState}); a callback is queued
 *       for it ({@link PowderkegEffects#fuse}) and re-queued at every neighbour update and chunk load, so a fuse whose
 *       callback was dropped (chunk unload) goes off at the next one.</li>
 *   <li>At the fuse's end: strength from the item count ({@link PowderkegConfig#strength}), a sealed keg's contents are
 *       used up (TFC: the block becomes air and a sealed keg does not eject), the keg becomes air and explodes
 *       ({@link PowderkegEffects#explode}).</li>
 * </ul>
 */
public final class PowderkegDevice {
    public static final String ID = "tfp:powderkeg";
    /** TFC {@code SealableDeviceBlock.SEALED}. */
    public static final String SEALED = "sealed";
    /** TFC {@code PowderkegBlock.LIT}. */
    public static final String LIT = "lit";

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param config TFC's powderkeg server options
     * @param fuel   TFC {@code isItemValid}: the stack is {@code #tfc:usable_in_powder_keg}
     * @param signal TFC {@code level.hasNeighborSignal(pos)} (region thread)
     * @param effects the world effects
     * @param open   opens the keg's screen for the player (TFC {@code openMenu})
     */
    public record Services(PowderkegConfig config, Predicate<ItemStack> fuel, Predicate<Block> signal,
            PowderkegEffects effects, BiConsumer<Player, Block> open) {
        public Services {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(signal, "signal");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(open, "open");
        }
    }

    private PowderkegDevice() {
    }

    public static DeviceKind<PowderkegState> kind(Services services) {
        return DeviceKind.builder(ID, PowderkegState.CODEC, PowderkegState.FRESH)
            .slots(PowderkegRules.SLOTS)
            .filter((slot, stack) -> services.fuel().test(stack))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** The block state of {@code state}. */
    public static BlockStateRef state(PowderkegState state) {
        return BlockStateRef.of(ID).with(SEALED, Boolean.toString(state.sealed())).with(LIT, Boolean.toString(state.lit()));
    }

    /** The items in the keg (TFC {@code getStrength}'s count). */
    static <S> int itemCount(DeviceContext<S> ctx) {
        int n = 0;
        for (ItemStack s : ctx.inventory().contents()) {
            n += s.getAmount();
        }
        return n;
    }

    /**
     * TFC {@code setLit(true, igniter)}: refused (with TFC's message to a player igniter) when powderkegs are disabled,
     * else the keg is lit with a fresh fuse, the primed sound plays and the fuse callback is queued.
     *
     * @return the lit record, or {@code null} when nothing changed
     */
    static DeviceRecord<PowderkegState> light(DeviceRecord<PowderkegState> record, long now, Block block, Player igniter,
            Services services) {
        if (!services.config().enabled()) {
            if (igniter != null) {
                services.effects().disabled(igniter);
            }
            return null;
        }
        int fuse = services.config().fuseTicks();
        PowderkegState next = record.state().lit(now, fuse);
        services.effects().sound(block, PowderkegEffects.Sound.PRIMED);
        services.effects().fuse(block, fuse);
        services.effects().show(block, next);
        return record.withState(next);
    }

    /**
     * Lights the keg at {@code block} from outside a device callback (a fire start, another explosion) when
     * {@code when} holds for its caught-up state. Region thread of {@code block}.
     *
     * @return true when the keg was lit
     */
    public static boolean lightAt(DeviceService devices, DeviceKind<PowderkegState> kind, Block block, Player igniter,
            Predicate<PowderkegState> when, Services services) {
        boolean[] lit = {false};
        Optional<DeviceRecord<PowderkegState>> stored = devices.edit(block, kind, (record, now) -> {
            if (!when.test(record.state())) {
                return record;
            }
            DeviceRecord<PowderkegState> next = light(record, now, block, igniter, services);
            lit[0] = next != null;
            return next == null ? record : next;
        });
        return stored.isPresent() && lit[0];
    }

    /** TFC {@code setLit(false, ...)}: the cool sound (or TFC's disabled message), fuse cleared. Null when refused. */
    static DeviceRecord<PowderkegState> unlight(DeviceRecord<PowderkegState> record, Block block, Player player,
            Services services) {
        if (!services.config().enabled()) {
            if (player != null) {
                services.effects().disabled(player);
            }
            return null;
        }
        services.effects().sound(block, PowderkegEffects.Sound.COOL);
        return record.withState(record.state().unlit());
    }

    /**
     * TFC {@code toggleSeal}: the seal flips; unsealing a lit keg puts it out ({@code setLit(false, null)}, no message,
     * and the extinguish sound); sealing runs {@code neighborChanged}, so a redstone signal lights it. The block state
     * follows.
     */
    static DeviceRecord<PowderkegState> toggleSeal(DeviceRecord<PowderkegState> record, long now, Block block,
            Services services) {
        PowderkegState.Toggle toggle = record.state().toggleSeal();
        DeviceRecord<PowderkegState> next = record.withState(toggle.state());
        if (toggle.extinguished()) {
            if (services.config().enabled()) {
                services.effects().sound(block, PowderkegEffects.Sound.COOL);
            }
            services.effects().sound(block, PowderkegEffects.Sound.EXTINGUISH);
        }
        if (toggle.checkSignal() && toggle.state().lightsFromSignal(services.signal().test(block))) {
            DeviceRecord<PowderkegState> lit = light(next, now, block, null, services);
            if (lit != null) {
                next = lit;
            }
        }
        services.effects().show(block, next.state());
        return next;
    }

    private record Handler(Services services) implements DeviceHandler<PowderkegState> {
        @Override
        public DeviceResult<PowderkegState> useWithItem(DeviceContext<PowderkegState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            Block block = ctx.block();
            DeviceRecord<PowderkegState> record = ctx.record();
            PowderkegState state = record.state();
            switch (state.click(StackCodec.isEmpty(ctx.item()), player.isSneaking())) {
                case EXTINGUISH -> {
                    DeviceRecord<PowderkegState> next = unlight(record, block, player, services);
                    services.effects().sound(block, PowderkegEffects.Sound.EXTINGUISH);
                    if (next == null) {
                        return DeviceResult.consume();
                    }
                    services.effects().show(block, next.state());
                    return DeviceResult.consume(next);
                }
                case TOGGLE_SEAL -> {
                    DeviceRecord<PowderkegState> next = toggleSeal(record, ctx.now(), block, services);
                    services.effects().sound(block, PowderkegEffects.Sound.PLACE);
                    return DeviceResult.consume(next);
                }
                default -> {
                    services.open().accept(player, block);
                    return DeviceResult.consume();
                }
            }
        }

        /** TFC {@code onPlace}: a signal lights a sealed keg (a placed keg is unsealed here, see the ledger). */
        @Override
        public DeviceResult<PowderkegState> placed(DeviceContext<PowderkegState> ctx) {
            return neighbourChanged(ctx);
        }

        /**
         * TFC {@code neighborChanged} (a signal lights a sealed keg) and {@code serverTick}'s fuse end; also the fuse
         * callback and the chunk-load callback of a lit keg.
         */
        @Override
        public DeviceResult<PowderkegState> neighbourChanged(DeviceContext<PowderkegState> ctx) {
            Block block = ctx.block();
            DeviceRecord<PowderkegState> record = ctx.record();
            PowderkegState state = record.state();
            long now = ctx.now();
            if (state.due(now)) {
                int strength = services.config().strength(itemCount(ctx));
                // TFC: setBlock(AIR) before the explosion; a sealed keg's beforeRemove ejects nothing
                DeviceRecord<PowderkegState> spent = state.sealed() ? record.cleared() : record;
                services.effects().explode(block, strength);
                return DeviceResult.update(spent.withState(state.unlit()));
            }
            if (state.lightsFromSignal(services.signal().test(block))) {
                DeviceRecord<PowderkegState> lit = light(record, now, block, null, services);
                return lit == null ? DeviceResult.pass() : DeviceResult.update(lit);
            }
            if (state.lit()) {
                services.effects().fuse(block, Math.max(1, state.fuseEnd() - now));
            }
            return DeviceResult.pass();
        }
    }
}
