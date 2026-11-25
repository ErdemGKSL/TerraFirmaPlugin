/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement,
 *       canBeReplaced, setPlacedBy, tick),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.drying;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.drying.DryingRules;
import dev.tfp.core.drying.DryingState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The drying bricks on their region thread (decide async, apply sync: here the decision is O(1) and pure, {@link DryingState},
 * so a callback decides and writes inline, as the pit kiln and the placed items do). Drying is calendar based and lazy:
 * a pile is settled at placement, a click, a neighbour change, chunk load and when its {@link DryingClock} entry is due; each
 * settle reads the rain at the block above (TFC {@code level.isRainingAt(pos.above())}), advances {@link DryingState} to the
 * player tick and, when the pile has dried, writes the {@code dried} block state.
 */
public final class DryingBricks {
    /** Block id prefix; the block and its wet item are {@code tfp:drying_bricks/<soil>}, the dry item {@code tfp:mud_brick/<soil>}. */
    public static final String PREFIX = DryingDevice.ID + "/";
    public static final String BRICK_PREFIX = "tfp:mud_brick/";
    public static final String COUNT = "count";
    public static final String DRIED = "dried";
    /**
     * How often a wet pile is looked at to sample the rain (player ticks): about the interval of TFC's random tick (3 of 4096
     * blocks per tick, so one in 1365 ticks); the dry tick comes first when it is nearer.
     */
    public static final long RECHECK_TICKS = 1200;

    /** TFC {@code level.isRainingAt(pos.above())} for the pile's block. Region thread. */
    @FunctionalInterface
    public interface Rain {
        Rain NONE = block -> false;

        boolean above(Block pile);
    }

    /**
     * @param writer    sets the state of a pile in the world (CraftEngine block data without physics), region thread
     * @param baseSturdy whether the block below a pile has a sturdy top face (TFC {@code BottomSupportedDeviceBlock})
     * @param threshold {@code mudBricksTicks}: ticks without a reset before a pile dries, or {@code -1} for never
     */
    public record Services(CustomBlocks blocks, CustomItems items, DeviceService devices,
            BiConsumer<Block, BlockStateRef> writer, Predicate<Block> baseSturdy, Rain rain, LongSupplier now, IntSupplier threshold, DryingClock clock, Scheduler scheduler) {
        public Services {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(devices, "devices");
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(baseSturdy, "baseSturdy");
            Objects.requireNonNull(rain, "rain");
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(threshold, "threshold");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(scheduler, "scheduler");
        }
    }

    private record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Services s;
    private volatile DeviceKind<DryingState> kind;
    /** Piles whose bricks were already handed out or voided: their removal drops nothing. */
    private final Set<Key> voided = ConcurrentHashMap.newKeySet();

    public DryingBricks(Services services) {
        this.s = Objects.requireNonNull(services, "services");
    }

    /** Sets the registered kind (the kind needs this object first). */
    public void kind(DeviceKind<DryingState> registered) {
        this.kind = Objects.requireNonNull(registered, "registered");
    }

    public DeviceKind<DryingState> kind() {
        return kind;
    }

    // ---- block states -------------------------------------------------------------------------------------------------

    public static boolean isDrying(BlockStateRef state) {
        return state != null && state.id().startsWith(PREFIX);
    }

    /** The soil of a drying bricks block id ({@code tfp:drying_bricks/alfisol}: {@code alfisol}). */
    public static String soil(String blockId) {
        return blockId.substring(PREFIX.length());
    }

    public static int count(BlockStateRef state) {
        try {
            return Math.max(1, Math.min(DryingRules.MAX_COUNT, Integer.parseInt(state.properties().getOrDefault(COUNT, "1"))));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static boolean dried(BlockStateRef state) {
        return Boolean.parseBoolean(state.properties().getOrDefault(DRIED, "false"));
    }

    public static BlockStateRef state(String soil, int count, boolean dried) {
        return BlockStateRef.of(PREFIX + soil).with(COUNT, Integer.toString(count)).with(DRIED, Boolean.toString(dried));
    }

    /** The item a pile gives: the dry mud brick once dried, else the wet brick (the block's own item). */
    public static String itemId(String soil, boolean dried) {
        return (dried ? BRICK_PREFIX : PREFIX) + soil;
    }

    private void write(Block block, BlockStateRef state) {
        s.writer().accept(block, state);
    }

    // ---- placing, settling ------------------------------------------------------------------------------------------

    /** TFC {@code setPlacedBy}: the counter is reset. Region thread. */
    DeviceRecord<DryingState> placed(Block block, DeviceRecord<DryingState> record) {
        long now = s.now().getAsLong();
        DryingState next = DryingState.reset(now, s.rain().above(block));
        s.clock().track(block, Math.min(now + RECHECK_TICKS, dueOf(next)));
        return record.withState(next);
    }

    private long dueOf(DryingState state) {
        long due = state.dueTick(s.threshold().getAsInt());
        return due == DryingRules.Pile.NOT_DRIED ? Long.MAX_VALUE : due;
    }

    /** Hands {@code block} to {@link #evaluate} on its region thread, after the current callback. */
    public void later(Block block) {
        s.scheduler().region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> evaluate(block));
    }

    /** TFC {@code BottomSupportedDeviceBlock.updateShape} (no sturdy top face below: the pile pops), then {@link #settle}. */
    public void evaluate(Block block) {
        Optional<BlockStateRef> st = s.blocks().stateAt(block);
        if (st.isEmpty() || !isDrying(st.get())) {
            s.clock().untrack(block);
            return;
        }
        if (!s.baseSturdy().test(block)) {
            s.clock().untrack(block);
            s.blocks().remove(block); // the bricks drop through removed()
            return;
        }
        settle(block);
    }

    /**
     * TFC {@code tick}, lazily: reads the rain above now, advances the counter to the player tick, writes {@code dried} when the
     * pile dried and lists a wet pile on the clock. Returns the state, empty when the block is no readable pile. Region thread.
     */
    public Optional<DryingState> settle(Block block) {
        Optional<BlockStateRef> st = s.blocks().stateAt(block);
        if (st.isEmpty() || !isDrying(st.get()) || kind == null) {
            s.clock().untrack(block);
            return Optional.empty();
        }
        BlockStateRef ref = st.get();
        int count = count(ref);
        if (dried(ref)) {
            s.clock().untrack(block);
            return Optional.empty();
        }
        long now = s.now().getAsLong();
        int threshold = s.threshold().getAsInt();
        boolean raining = s.rain().above(block);
        DryingState[] out = new DryingState[1];
        Optional<DeviceRecord<DryingState>> r = s.devices().edit(block, kind, (record, t) -> {
            DryingState next = record.state().withCount(count).settle(now, threshold, raining);
            out[0] = next;
            return record.withState(next);
        });
        if (r.isEmpty()) {
            return Optional.empty();
        }
        DryingState next = out[0];
        if (next.dried()) {
            s.clock().untrack(block);
            write(block, state(soil(ref.id()), count, true));
        } else {
            long due = dueOf(next);
            if (due == Long.MAX_VALUE) {
                s.clock().untrack(block); // mudBricksTicks = -1: never dries
            } else {
                s.clock().track(block, Math.min(due, now + RECHECK_TICKS));
            }
        }
        return Optional.of(next);
    }

    // ---- hands --------------------------------------------------------------------------------------------------------

    /**
     * TFC {@code useItemOn} with the pile's own item: not sneaking, not dry, fewer than four bricks: one more brick, the counter
     * reset, the item used (not in creative). Returns whether the click was used.
     */
    public boolean add(Player player, Block block, ItemStack held) {
        Optional<BlockStateRef> st = s.blocks().stateAt(block);
        if (st.isEmpty() || !isDrying(st.get()) || kind == null || dried(st.get()) || count(st.get()) >= DryingRules.MAX_COUNT) {
            return false;
        }
        String soil = soil(st.get().id());
        if (!s.items().idOf(held).map(id -> id.equals(itemId(soil, false))).orElse(false)) {
            return false;
        }
        int count = count(st.get());
        long now = s.now().getAsLong();
        boolean raining = s.rain().above(block);
        Optional<DeviceRecord<DryingState>> r = s.devices().edit(block, kind,
            (record, t) -> record.withState(record.state().withCount(count).added(now, raining)));
        if (r.isEmpty()) {
            return false;
        }
        write(block, state(soil, count + 1, false));
        s.clock().track(block, Math.min(now + RECHECK_TICKS, dueOf(r.get().state())));
        sound(block);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        return true;
    }

    /**
     * TFC {@code useItemOn} with an empty hand while sneaking: one brick comes off (the dry brick once dried), the counter is not
     * reset; the last one removes the pile. Returns whether the click was used.
     */
    public boolean take(Player player, Block block) {
        Optional<BlockStateRef> st = s.blocks().stateAt(block);
        if (st.isEmpty() || !isDrying(st.get()) || kind == null) {
            return false;
        }
        String soil = soil(st.get().id());
        boolean dried = dried(st.get());
        int count = count(st.get());
        Optional<ItemStack> drop = s.items().create(itemId(soil, dried), 1);
        if (drop.isEmpty()) {
            return false;
        }
        for (ItemStack rest : player.getInventory().addItem(drop.get()).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
        if (count > 1) {
            s.devices().edit(block, kind, (record, t) -> record.withState(record.state().withCount(count).taken()));
            write(block, state(soil, count - 1, dried));
        } else {
            voided.add(Key.of(block));
            s.clock().untrack(block);
            s.blocks().remove(block);
            voided.remove(Key.of(block));
        }
        sound(block);
        return true;
    }

    /** A creative player broke the pile: loot tables do not drop in creative, so its removal drops nothing. */
    public void markVoid(Block block) {
        voided.add(Key.of(block));
    }

    /**
     * The pile was removed (broken, burnt, exploded, pushed, no support) or replaced by another state of the same block (then
     * it is still a pile and nothing drops). TFC's loot: {@code count} of the wet item, or of the dry mud brick once dried.
     */
    void removed(Block block, String blockId, DeviceRecord<DryingState> record) {
        s.clock().untrack(block);
        Optional<BlockStateRef> now = s.blocks().stateAt(block);
        if (now.isPresent() && isDrying(now.get())) {
            return;
        }
        if (voided.remove(Key.of(block)) || !blockId.startsWith(PREFIX)) {
            return;
        }
        DryingState state = record.state();
        s.items().create(itemId(soil(blockId), state.dried()), state.count()).ifPresent(stack -> {
            Location centre = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
            block.getWorld().dropItem(centre, stack, item -> item.setPickupDelay(0));
        });
    }

    /** TFC {@code playSound}: the block's place sound at volume (v + 1) / 2 and pitch x 0.8. */
    private static void sound(Block block) {
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        block.getWorld().playSound(at, Sound.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
    }

    /** Debug and tests: a pile of {@code count} bricks placed now (a player's placement sets the same record). */
    public boolean place(Block block, String soil, int count, boolean dried) {
        if (kind == null || !s.blocks().place(block, state(soil, count, dried), false)) {
            return false;
        }
        long now = s.now().getAsLong();
        boolean raining = s.rain().above(block);
        Optional<DeviceRecord<DryingState>> r = s.devices().edit(block, kind, (record, t) ->
            record.withState(DryingState.reset(now, raining).withCount(count)));
        if (r.isPresent() && !dried) {
            s.clock().track(block, Math.min(now + RECHECK_TICKS, dueOf(r.get().state())));
        }
        return r.isPresent();
    }
}
