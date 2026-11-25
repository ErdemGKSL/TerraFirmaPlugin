/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/LobsterTrapBlock.java @ c3b4d9536 (useItemOn, useWithoutItem),
 *       src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536 (tryBait, takeContents,
 *       catchEntity, getSlotStackLimit, updateTrapState)
 */
package dev.tfp.paper.lobstertrap;

import dev.tfp.core.fishing.LobsterTrap;
import dev.tfp.core.hunt.HuntKill;
import dev.tfp.core.hunt.HuntYield;
import dev.tfp.core.lobstertrap.LobsterTrapState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FAUNA-19: the lobster trap as a device kind ({@code tfp:lobster_trap}): TFC's {@code LobsterTrapBlock} +
 * {@code LobsterTrapBlockEntity} on the device framework, with the rules of {@link LobsterTrap}.
 *
 * <ul>
 *   <li>Six slots of one item each; removal drops the contents ({@code InventoryRemoveBehavior.DROP}); the bait is lost.</li>
 *   <li>A right click with lobster bait on an open trap (no bait, no catch) puts three bait units in, uses one bait item (not in
 *       creative) and bubbles; the click with any other item passes, and only an empty hand reaches the take.</li>
 *   <li>An empty hand on a baited or closed trap gives out every catch (into the inventory, the rest dropped) and, when there was none,
 *       clears the bait.</li>
 *   <li>A mob that swims in is caught by {@link #catchMob}: the junk roll or the mob's loot, one item per slot, one bait unit used. The
 *       trap state ({@code trap} = open, baited, closed) is {@link LobsterTrap#state} of the bait and the occupied slots.</li>
 * </ul>
 * No timer: every rule is an event, so the device has no catch-up.
 */
public final class LobsterTrapDevice {
    public static final String ID = "tfp:lobster_trap";
    /** The block state property that shows {@link LobsterTrap.State}. */
    public static final String TRAP = "trap";
    /** TFC's kill by the trap: no attacker, and the {@code tfc:lobster_trap} damage type counts as a player kill. */
    static final HuntKill TRAP_KILL = new HuntKill(HuntKill.Killer.NONE, false, true, false, 1f, 0f, 0f);

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param isBait  whether a stack is in {@code tfc:lobster_bait}
     * @param effects the world effects
     */
    public record Services(Predicate<ItemStack> isBait, TrapEffects effects) {
        public Services {
            Objects.requireNonNull(isBait, "isBait");
            Objects.requireNonNull(effects, "effects");
        }
    }

    /** The outcome of a catch. */
    public record Caught(DeviceRecord<LobsterTrapState> record, int inserted) {}

    private LobsterTrapDevice() {
    }

    public static DeviceKind<LobsterTrapState> kind(Services services) {
        return DeviceKind.builder(ID, LobsterTrapState.CODEC, LobsterTrapState.FRESH)
            .slots(LobsterTrap.CATCH_SLOTS)
            .slotLimit(slot -> 1)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** The bait and the occupied slots as the rules read them. */
    public static LobsterTrap.Contents contents(DeviceRecord<LobsterTrapState> record) {
        return record.state().contents(record.nonEmptySlots().size());
    }

    /** The trap state of a record, TFC {@code updateTrapState}. */
    public static LobsterTrap.State trapState(DeviceRecord<LobsterTrapState> record) {
        return LobsterTrap.state(contents(record));
    }

    /** Whether a baited trap with a free slot would catch a mob now, TFC {@code canCatch}. */
    public static boolean canCatch(DeviceRecord<LobsterTrapState> record) {
        return LobsterTrap.canCatch(contents(record));
    }

    /** The block state of {@code current} with its {@code trap} property set to {@code state}. */
    public static BlockStateRef withTrap(BlockStateRef current, LobsterTrap.State state) {
        return current.with(TRAP, state.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * TFC {@code catchEntity}: rolls the junk or the mob's loot ({@link LobsterTrap#catchMob}), puts the items one per slot and uses
     * a bait unit. The caller discards the mob.
     *
     * @param species the species path ({@code lobster})
     * @param junk    the items of {@code tfc:lobster_trap_junk}
     * @param toStacks turns the rolled yields into stacks (the loot service: TFC's item ids to ours, loot functions)
     * @return the new record, or null when the trap cannot catch
     */
    public static Caught catchMob(DeviceKind<LobsterTrapState> kind, DeviceRecord<LobsterTrapState> record, StackCodec stacks,
            String species, List<String> junk, DoubleSupplier rng, Function<List<HuntYield>, List<ItemStack>> toStacks) {
        LobsterTrap.Contents before = contents(record);
        if (!LobsterTrap.canCatch(before)) {
            return null;
        }
        LobsterTrap.Catch rolled = LobsterTrap.catchMob(before, species, TRAP_KILL, junk, rng);
        DeviceInventory<LobsterTrapState> inv = new DeviceInventory<>(kind, record, stacks);
        for (ItemStack stack : toStacks.apply(rolled.inserted())) {
            inv = inv.insert(stack).inventory(); // TFC insertAllSlots: what does not fit is lost
        }
        DeviceRecord<LobsterTrapState> next = inv.record().withState(new LobsterTrapState(rolled.contents().bait()));
        return new Caught(next, next.nonEmptySlots().size() - before.occupied());
    }

    private record Handler(Services services) implements DeviceHandler<LobsterTrapState> {
        @Override
        public DeviceResult<LobsterTrapState> useWithItem(DeviceContext<LobsterTrapState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null || ctx.item().isEmpty() || !services.isBait().test(ctx.item())
                    || trapState(ctx.record()) != LobsterTrap.State.OPEN) {
                return DeviceResult.pass();
            }
            LobsterTrap.Contents after = LobsterTrap.bait(contents(ctx.record()), true);
            if (after.equals(contents(ctx.record()))) {
                return DeviceResult.pass();
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack held = player.getInventory().getItem(ctx.hand());
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItem(ctx.hand(), held.getAmount() <= 0 ? null : held);
            }
            DeviceRecord<LobsterTrapState> next = ctx.record().withState(LobsterTrapState.of(after));
            services.effects().sound(ctx.block(), TrapEffects.Sound.BAIT);
            services.effects().bubbles(ctx.block(), 4);
            show(ctx, next);
            return DeviceResult.consume(next);
        }

        /** TFC {@code useWithoutItem}: only an empty hand takes the contents (a held item falls through to its own use). */
        @Override
        public DeviceResult<LobsterTrapState> use(DeviceContext<LobsterTrapState> ctx) {
            Player player = ctx.player();
            if (player == null || !ctx.item().isEmpty() || trapState(ctx.record()) == LobsterTrap.State.OPEN) {
                return DeviceResult.pass();
            }
            LobsterTrap.Take take = LobsterTrap.take(contents(ctx.record()));
            if (take.items() == 0 && !take.baitCleared()) {
                return DeviceResult.consume();
            }
            DeviceInventory<LobsterTrapState> inv = ctx.inventory();
            for (ItemStack stack : inv.contents()) {
                player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            }
            DeviceRecord<LobsterTrapState> next = inv.cleared().record().withState(LobsterTrapState.of(take.contents()));
            show(ctx, next);
            return DeviceResult.consume(next);
        }

        @Override
        public DeviceResult<LobsterTrapState> placed(DeviceContext<LobsterTrapState> ctx) {
            services.effects().baited(ctx.block(), canCatch(ctx.record()));
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<LobsterTrapState> ctx) {
            services.effects().removed(ctx.block());
        }

        private void show(DeviceContext<LobsterTrapState> ctx, DeviceRecord<LobsterTrapState> next) {
            services.effects().show(ctx.block(), trapState(next));
            services.effects().baited(ctx.block(), canCatch(next));
        }
    }
}
