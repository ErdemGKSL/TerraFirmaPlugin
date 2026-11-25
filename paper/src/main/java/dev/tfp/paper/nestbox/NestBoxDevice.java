/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/NestBoxBlock.java @ c3b4d9536 (useItemOn: openMenu unless
 *       sneaking, onRemove: the sitter stops riding),
 *       src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java @ c3b4d9536 (SLOTS, getSlotStackLimit,
 *       isItemValid, serverTick: the sitter's lay, the hatch scan)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.livestock.products.NestRules;
import dev.tfp.core.nestbox.NestBoxRules;
import dev.tfp.core.nestbox.NestBoxState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FAUNA-07: the nest box as a device kind ({@code tfp:nest_box}): TFC's {@code NestBoxBlock} +
 * {@code NestBoxBlockEntity} on the device framework, with the rules of {@code core/nestbox} and {@link NestRules}.
 *
 * <ul>
 *   <li>Four slots, one item each, only eggs with the egg record that are not rotten ({@link NestBoxRules#accepts}).
 *       Removal drops the contents ({@code InventoryRemoveBehavior.DROP}).</li>
 *   <li>Right click without sneaking opens the screen; sneaking passes (TFC {@code useItemOn}).</li>
 *   <li>A sitting hen is evaluated lazily from the tick she sat: {@link NestRules#advance} walks the 30-tick checks that
 *       passed and returns the first event: she laid (egg into the first free slot, or a plain egg dropped; her
 *       fertilization is cleared and her product cooldown starts), or she stood up (not ready, or the nest was full when
 *       the 1 in 7 roll hit). While she still sits, the next check is queued ({@link NestEffects#wake}).</li>
 *   <li>Every fertilized, fresh egg whose hatch day has come hatches at the nest and its slot empties; the next hatch is
 *       queued the same way.</li>
 * </ul>
 * Every path (use, wake, neighbour update, chunk load) calls {@link #process}, which is idempotent: it only acts on what is
 * due, and rolls each check once ({@link NestBoxState#checkedThrough}).
 */
public final class NestBoxDevice {
    public static final String ID = "tfp:nest_box";
    /** A wake is never queued further than this: a calendar jump is picked up at most this late. */
    public static final long MAX_WAKE_TICKS = 6000L;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param rotten  TFC {@code FoodCapability.isRotten} of an egg stack
     * @param clock   the global clock
     * @param random  the nest random ({@code bird.getRandom()} in TFC), one per call
     * @param hens    the sitting hen
     * @param effects the world effects
     * @param open    opens the screen for the player (TFC {@code openMenu})
     */
    public record Services(Predicate<ItemStack> rotten, Supplier<ClockSnapshot> clock,
            Supplier<NestRules.NestRandom> random, NestHens hens, NestEffects effects, BiConsumer<Player, Block> open) {
        public Services {
            Objects.requireNonNull(rotten, "rotten");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(hens, "hens");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(open, "open");
        }
    }

    private NestBoxDevice() {
    }

    public static DeviceKind<NestBoxState> kind(Services services) {
        return DeviceKind.builder(ID, NestBoxState.CODEC, NestBoxState.FRESH)
            .slots(NestBoxRules.SLOTS)
            .slotLimit(NestBoxRules.SLOT_LIMIT)
            .filter((slot, stack) -> accepts(stack, services))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** TFC {@code isItemValid}. */
    public static boolean accepts(ItemStack stack, Services services) {
        return stack != null && !stack.isEmpty() && NestBoxRules.accepts(NestEggs.isEggItem(stack), services.rotten().test(stack));
    }

    /** The slots as the rules see them. */
    static List<NestBoxRules.Slot> slots(DeviceRecord<NestBoxState> record, StackCodec stacks, Services services) {
        List<NestBoxRules.Slot> out = new ArrayList<>(NestBoxRules.SLOTS);
        for (int i = 0; i < NestBoxRules.SLOTS; i++) {
            ItemStack stack = stacks.decode(record.slot(i));
            if (StackCodec.isEmpty(stack)) {
                out.add(new NestBoxRules.Empty());
                continue;
            }
            out.add(NestEggs.record(stack)
                .<NestBoxRules.Slot>map(e -> new NestBoxRules.Egg(e.fertilized(), e.hatchDay(), services.rotten().test(stack)))
                .orElseGet(NestBoxRules.Other::new));
        }
        return out;
    }

    /**
     * Brings the nest up to date at {@code now}: the sitter's checks, then the hatch scan (TFC's {@code serverTick} order),
     * then queues the next wake. Region thread of {@code block}.
     */
    public static DeviceRecord<NestBoxState> process(DeviceRecord<NestBoxState> record, long now, Block block,
            StackCodec stacks, Services services) {
        ClockSnapshot clock = services.clock().get();
        if (!clock.isSet()) {
            return record;
        }
        DeviceRecord<NestBoxState> next = record;
        if (next.state().occupied()) {
            next = sitter(next, now, block, stacks, services, clock);
        }
        next = hatch(next, block, stacks, services, clock);
        scheduleHatch(next, now, block, stacks, services);
        return next;
    }

    /** Queues the wake of the next hatch, if any egg will hatch. */
    static void scheduleHatch(DeviceRecord<NestBoxState> record, long now, Block block, StackCodec stacks, Services services) {
        ClockSnapshot clock = services.clock().get();
        if (!clock.isSet()) {
            return;
        }
        long delay = NestBoxRules.delayToHatch(slots(record, stacks, services), now, clock.calendarTicks());
        if (delay >= 0) {
            services.effects().wake(block, Math.min(MAX_WAKE_TICKS, Math.max(1L, delay)));
        }
    }

    private static DeviceRecord<NestBoxState> sitter(DeviceRecord<NestBoxState> record, long now, Block block,
            StackCodec stacks, Services services, ClockSnapshot clock) {
        NestBoxState state = record.state();
        Optional<NestHens.Hen> found = services.hens().find(block, state.sitter());
        if (found.isEmpty()) {
            return record.withState(state.vacated()); // she died, left or is not loaded
        }
        NestHens.Hen hen = found.get();
        boolean open = NestBoxRules.hasOpenSlot(slots(record, stacks, services));
        NestRules.Outcome outcome = NestRules.advance(hen.spec(), hen.livestock(), hen.state(), hen.product(),
            state.seatedTick(), now, open, services.random().get(), t -> clock.calendarTicks() - (now - t));
        if (outcome instanceof NestRules.Laid laid) {
            DeviceRecord<NestBoxState> next = record.withState(state.vacated());
            services.effects().laidSound(block);
            if (laid.nestEgg().isPresent()) {
                ItemStack egg = NestEggs.fertile(hen.species(), laid.nestEgg().get());
                OptionalInt slot = NestBoxRules.firstFree(slots(record, stacks, services));
                if (slot.isPresent() && accepts(egg, services)) {
                    next = next.withSlot(slot.getAsInt(), stacks.encode(egg));
                } else {
                    // TFC: the nest somehow got full or refuses the egg; the egg is spawned so the lay is not wasted
                    services.effects().dropEgg(block, egg);
                }
            } else {
                services.effects().dropEgg(block, NestEggs.plain()); // TFC: a plain egg is not valid in the nest
            }
            services.hens().laid(block, hen, laid.hen(), laid.product());
            return next;
        }
        if (outcome instanceof NestRules.Left) {
            services.hens().release(block, state.sitter());
            return record.withState(state.vacated());
        }
        services.effects().wake(block, NestBoxRules.delayToCheck(now, 1));
        return record.withState(state.checkedThrough(now));
    }

    private static DeviceRecord<NestBoxState> hatch(DeviceRecord<NestBoxState> record, Block block, StackCodec stacks,
            Services services, ClockSnapshot clock) {
        List<Integer> due = NestBoxRules.hatching(slots(record, stacks, services), clock.calendarTicks());
        DeviceRecord<NestBoxState> next = record;
        for (int slot : due) {
            NestEggs.Chick chick = NestEggs.chickOf(stacks.decode(record.slot(slot)));
            services.effects().hatch(block, chick.species(), chick.familiarity());
            next = next.withSlot(slot, ItemBlob.EMPTY);
        }
        return next;
    }

    private record Handler(Services services) implements DeviceHandler<NestBoxState> {
        @Override
        public DeviceResult<NestBoxState> useWithItem(DeviceContext<NestBoxState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null || player.isSneaking()) {
                return DeviceResult.pass();
            }
            DeviceRecord<NestBoxState> next = process(ctx.record(), ctx.now(), ctx.block(), ctx.stacks(), services);
            services.open().accept(player, ctx.block());
            return DeviceResult.consume(next);
        }

        @Override
        public DeviceResult<NestBoxState> placed(DeviceContext<NestBoxState> ctx) {
            services.effects().placed(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<NestBoxState> neighbourChanged(DeviceContext<NestBoxState> ctx) {
            DeviceRecord<NestBoxState> next = process(ctx.record(), ctx.now(), ctx.block(), ctx.stacks(), services);
            return next.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(next);
        }

        @Override
        public void removed(DeviceContext<NestBoxState> ctx) {
            NestBoxState state = ctx.record().state();
            services.effects().removed(ctx.block());
            if (state.occupied()) {
                services.hens().release(ctx.block(), state.sitter()); // TFC NestBoxBlock.onRemove: sitter.stopRiding()
            }
        }
    }
}
