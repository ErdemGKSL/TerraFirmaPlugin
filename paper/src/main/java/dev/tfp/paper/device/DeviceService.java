/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DeviceBlock.java @ c3b4d9536 (onRemove, beforeRemove),
 *       src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java @ c3b4d9536 (ejectInventory),
 *       src/main/java/net/dries007/tfc/util/calendar/ICalendarTickable.java @ c3b4d9536 (catch-up on access)
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.persist.DataMap;
import dev.tfp.core.persist.PersistException;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The device framework's runtime (PAR-INFRA-02): finds the {@link DeviceKind} of a custom block, loads its record from
 * the {@link DeviceStore}, catches it up ({@link CatchUp}, TFC {@code ICalendarTickable}), runs the kind's
 * {@link DeviceHandler} and writes the record back only when it changed.
 *
 * <p>Catch-up runs on every access: an interaction, {@link #catchUp} (chunk-load trigger PAR-INFRA-11, GUI open
 * PAR-INFRA-03), a neighbour update and removal. The kind's {@code advance} is pure; it runs inline on the region
 * thread because one record is small. A kind whose catch-up must change other blocks returns the decision in its
 * record and hands the world change to an {@code AsyncDecisionPipeline}.
 *
 * <p>Unreadable records (a newer plugin version, corruption) are never overwritten: the device answers nothing until
 * the data is readable again. Handler exceptions are logged and the interaction passes; nothing is written.
 *
 * <p>{@link DeviceKind.RemoveBehaviour#SAVE} (PAR-INFRA-18): removal keeps the caught-up record for a few seconds
 * under the block position; the block's own drop (its custom item id equals the block id) spawning there gets it as
 * item PDC {@link #ITEM_KEY} ({@link #stampSavedDrop}, from {@code ItemSpawnEvent}), and placing that item restores it
 * ({@link #fromItem}, from CraftEngine's {@code loadCustomDataFromItem}).
 *
 * <p>Threads: every method runs on the region thread that owns the block (the {@link CustomBlocks} and
 * {@link DeviceStore} calls enforce it). The registry and the pending saves are thread-safe.
 */
public final class DeviceService implements DeviceRouter {
    /** Item PDC key of a saved device record ({@link DeviceKind.RemoveBehaviour#SAVE}): the kind codec's bytes. */
    public static final NamespacedKey ITEM_KEY = new NamespacedKey("tfp", "device");
    /** How long a removed SAVE device waits for its dropped item (drops spawn in the same tick). */
    static final long SAVE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5);

    /** What {@code /tfp debug device} shows. */
    public record Inspection(String kindId, BlockStateRef state, Optional<DeviceRecord<?>> record, long now,
            String problem) {
    }

    private record Loaded<S>(DeviceKind<S> kind, DeviceRecord<S> record, boolean stored) {
    }

    private record SaveKey(UUID world, int x, int y, int z) {
    }

    private record PendingSave(String blockId, byte[] bytes, long expiresNanos) {
    }

    private final DeviceKinds kinds;
    private final CustomBlocks blocks;
    private final Supplier<DeviceStore> store;
    private final LongSupplier playerTicks;
    private final StackCodec stacks;
    private final CustomItems items;
    private final Logger logger;
    private final Map<SaveKey, PendingSave> pendingSaves = new ConcurrentHashMap<>();
    /** Monotonic nanos (tests replace it). */
    LongSupplier nanos = System::nanoTime;

    /**
     * @param blocks      custom block lookups (to find the block id)
     * @param store       the device data store (read per call: the CraftEngine adapter installs it in onLoad)
     * @param playerTicks the calendar's player ticks (TFC {@code Calendars.SERVER.getTicks()}); one read per call
     * @param items       custom item lookups (to recognise a SAVE device's own dropped item)
     */
    public DeviceService(DeviceKinds kinds, CustomBlocks blocks, Supplier<DeviceStore> store, LongSupplier playerTicks,
            StackCodec stacks, CustomItems items, Logger logger) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.store = Objects.requireNonNull(store, "store");
        this.playerTicks = Objects.requireNonNull(playerTicks, "playerTicks");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.items = Objects.requireNonNull(items, "items");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public DeviceKinds kinds() {
        return kinds;
    }

    /** Registers a kind (any time; usually in a feature module's {@code enable}). */
    public <S> DeviceKind<S> register(DeviceKind<S> kind) {
        return kinds.register(kind);
    }

    public StackCodec stacks() {
        return stacks;
    }

    // ---- routed callbacks ------------------------------------------------------------------------

    @Override
    public boolean use(Block block, Player player, EquipmentSlot hand, ItemStack item) {
        ItemStack held = StackCodec.isEmpty(item) ? ItemStack.empty() : item.clone();
        return route(block, player, hand, held, Op.USE);
    }

    /** A left click on the device (Bukkit {@code PlayerInteractEvent}, {@link DeviceListener}). */
    public boolean attack(Block block, Player player) {
        return route(block, player, EquipmentSlot.HAND, ItemStack.empty(), Op.ATTACK);
    }

    /**
     * A player placed the device (after the placement event, {@link DeviceListener}).
     *
     * @param item a copy of the placed stack, taken during the event (TFC {@code setPlacedBy}'s stack)
     */
    public void placed(Block block, Player player, EquipmentSlot hand, ItemStack item) {
        route(block, player, hand, StackCodec.isEmpty(item) ? ItemStack.empty() : item.clone(), Op.PLACED);
    }

    @Override
    public Optional<DataMap> created(String blockId) {
        Optional<DeviceKind<?>> kind = kinds.find(blockId);
        return kind.map(k -> stamped(k, playerTicks.getAsLong()));
    }

    private static <S> DataMap stamped(DeviceKind<S> kind, long now) {
        return kind.codec().encode(kind.fresh().withLastUpdateTick(now));
    }

    @Override
    public void neighbourChanged(Block block) {
        route(block, null, null, ItemStack.empty(), Op.NEIGHBOUR);
    }

    /**
     * Catches the device at {@code block} up to now and stores the result (the hook for chunk load and GUI open).
     *
     * @return the caught-up record, empty when {@code block} is not a readable device
     */
    public Optional<DeviceRecord<?>> catchUp(Block block) {
        Optional<DeviceKind<?>> kind = kindAt(block).map(KindAt::kind);
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return Optional.<DeviceRecord<?>>ofNullable(catchUp(block, kind.get()));
    }

    private <S> DeviceRecord<S> catchUp(Block block, DeviceKind<S> kind) {
        Loaded<S> loaded = load(block, kind);
        if (loaded == null) {
            return null;
        }
        DeviceRecord<S> next = CatchUp.apply(loaded.record(), playerTicks.getAsLong(), kind.advance()).record();
        save(block, loaded, next);
        return next;
    }

    @Override
    public void removed(World world, int x, int y, int z, String blockId, DataMap data) {
        Optional<DeviceKind<?>> kind = kinds.find(blockId);
        if (kind.isEmpty()) {
            return;
        }
        try {
            removed(world, x, y, z, BlockStateRef.of(blockId), kind.get(), data);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Device " + blockId + " at " + world.getName() + " " + x + "," + y + "," + z
                    + ": removal failed; its contents may be lost", e);
        }
    }

    private <S> void removed(World world, int x, int y, int z, BlockStateRef state, DeviceKind<S> kind, DataMap data) {
        DeviceRecord<S> record;
        try {
            record = data.size() == 0 ? kind.fresh() : kind.codec().decode(data);
        } catch (PersistException e) {
            logger.warning("Device " + state + " at " + world.getName() + " " + x + "," + y + "," + z
                    + " was removed with unreadable data; nothing dropped: " + e.getMessage());
            return;
        }
        long now = playerTicks.getAsLong();
        record = CatchUp.apply(record, now, kind.advance()).record();
        Block block = world.getBlockAt(x, y, z);
        DeviceContext<S> ctx = new DeviceContext<>(kind, block, state, null, null, ItemStack.empty(), record, now, stacks);
        kind.handler().removed(ctx);
        if (kind.removal() == DeviceKind.RemoveBehaviour.SAVE) {
            long t = nanos.getAsLong();
            pendingSaves.values().removeIf(p -> t - p.expiresNanos() > 0);
            pendingSaves.put(new SaveKey(world.getUID(), x, y, z),
                    new PendingSave(state.id(), kind.codec().toBytes(record), t + SAVE_WINDOW_NANOS));
        } else if (kind.removal() == DeviceKind.RemoveBehaviour.DROP) {
            // TFC Helpers.spawnItem(level, pos, stack): new ItemEntity at the block centre, vanilla's random motion,
            // pickup delay 0. CraftWorld.dropItem sets pickupDelay = 10 before the consumer runs, so reset it there.
            Location centre = new Location(world, x + 0.5, y + 0.5, z + 0.5);
            for (ItemStack stack : ctx.inventory().contents()) {
                world.dropItem(centre, stack, item -> item.setPickupDelay(0));
            }
        }
    }

    // ---- SAVE: record on the dropped item ------------------------------------------------------------

    /**
     * An item entity spawned ({@code ItemSpawnEvent}): if a SAVE device was just removed at its block position and the
     * item is that device's own block item, the device's record goes onto it as {@link #ITEM_KEY}. Region thread of
     * the item.
     *
     * @return true if the item was stamped
     */
    public boolean stampSavedDrop(Item entity) {
        if (pendingSaves.isEmpty()) {
            return false;
        }
        Location l = entity.getLocation();
        SaveKey key = new SaveKey(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        PendingSave p = pendingSaves.get(key);
        if (p == null) {
            return false;
        }
        if (nanos.getAsLong() - p.expiresNanos() > 0) {
            pendingSaves.remove(key, p);
            return false;
        }
        ItemStack stack = entity.getItemStack();
        if (!items.idOf(stack).filter(p.blockId()::equals).isPresent() || !pendingSaves.remove(key, p)) {
            return false;
        }
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BYTE_ARRAY,
                p.bytes()));
        entity.setItemStack(stack);
        return true;
    }

    /** Saves still waiting for their dropped item (tests). */
    int pendingSaves() {
        return pendingSaves.size();
    }

    /** The record saved on {@code item} ({@link #ITEM_KEY}), or empty. */
    public static Optional<byte[]> savedRecord(ItemStack item) {
        if (StackCodec.isEmpty(item) || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        return Optional.ofNullable(meta.getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.BYTE_ARRAY));
    }

    @Override
    public Optional<DataMap> fromItem(String blockId, ItemStack item) {
        Optional<byte[]> saved = savedRecord(item);
        Optional<DeviceKind<?>> kind = kinds.find(blockId);
        if (saved.isEmpty() || kind.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(restamped(kind.get(), saved.get(), playerTicks.getAsLong()));
        } catch (PersistException e) {
            logger.warning("Device " + blockId + ": the record saved on the placed item is unreadable; placed empty: "
                    + e.getMessage());
            return Optional.empty();
        }
    }

    private static <S> DataMap restamped(DeviceKind<S> kind, byte[] bytes, long now) {
        return kind.codec().encode(kind.codec().fromBytes(bytes).withLastUpdateTick(now));
    }

    // ---- inspection --------------------------------------------------------------------------------

    /** The device at {@code block} as stored, without catching it up. Empty for a block that is no device kind. */
    public Optional<Inspection> inspect(Block block) {
        Optional<KindAt> at = kindAt(block);
        if (at.isEmpty()) {
            return Optional.empty();
        }
        long now = playerTicks.getAsLong();
        DeviceKind<?> kind = at.get().kind();
        Optional<DataMap> raw = store.get().read(block);
        if (raw.isEmpty()) {
            return Optional.of(new Inspection(kind.id(), at.get().state(), Optional.empty(), now,
                    "no device block entity (is the block's behaviour tfp:device?) or its data is unreadable"));
        }
        try {
            DeviceRecord<?> record = raw.get().size() == 0 ? kind.fresh() : kind.codec().decode(raw.get());
            return Optional.of(new Inspection(kind.id(), at.get().state(), Optional.of(record), now,
                    raw.get().size() == 0 ? "never written" : ""));
        } catch (PersistException e) {
            return Optional.of(new Inspection(kind.id(), at.get().state(), Optional.empty(), now,
                    "unreadable: " + e.getMessage()));
        }
    }

    // ---- internals -----------------------------------------------------------------------------------

    private enum Op {
        USE, ATTACK, PLACED, NEIGHBOUR
    }

    private record KindAt(DeviceKind<?> kind, BlockStateRef state) {
    }

    private Optional<KindAt> kindAt(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        return kinds.find(state.get().id()).map(k -> new KindAt(k, state.get()));
    }

    private boolean route(Block block, Player player, EquipmentSlot hand, ItemStack item, Op op) {
        Optional<KindAt> at = kindAt(block);
        if (at.isEmpty()) {
            return false;
        }
        try {
            return route(block, at.get().state(), at.get().kind(), player, hand, item, op);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Device " + at.get().state() + " at " + block.getWorld().getName() + " "
                    + block.getX() + "," + block.getY() + "," + block.getZ() + ": " + op + " failed", e);
            return false;
        }
    }

    private <S> boolean route(Block block, BlockStateRef state, DeviceKind<S> kind, Player player, EquipmentSlot hand,
            ItemStack item, Op op) {
        Loaded<S> loaded = load(block, kind);
        if (loaded == null) {
            return false;
        }
        long now = playerTicks.getAsLong();
        DeviceRecord<S> record = CatchUp.apply(loaded.record(), now, kind.advance()).record();
        DeviceHandler<S> handler = kind.handler();
        DeviceResult<S> result;
        switch (op) {
            case USE -> {
                DeviceContext<S> ctx = new DeviceContext<>(kind, block, state, player, hand, item, record, now, stacks);
                // vanilla ServerPlayerGameMode.useItemOn: useItemOn for the clicking hand (also when empty), then
                // useWithoutItem only for the main hand (CraftEngine: TRY_EMPTY_HAND && hand == MAIN_HAND)
                DeviceResult<S> first = handler.useWithItem(ctx);
                if (first.consumed() || hand != EquipmentSlot.HAND) {
                    result = first;
                } else {
                    // TFC: PASS_TO_DEFAULT_BLOCK_INTERACTION → useWithoutItem, on the record useWithItem left
                    DeviceRecord<S> after = first.updated().orElse(record);
                    DeviceResult<S> plain = handler.use(
                            new DeviceContext<>(kind, block, state, player, hand, item, after, now, stacks));
                    result = new DeviceResult<>(plain.consumed(), plain.updated().orElse(after));
                }
            }
            case ATTACK -> result = handler.attack(
                    new DeviceContext<>(kind, block, state, player, hand, item, record, now, stacks));
            case PLACED -> result = handler.placed(
                    new DeviceContext<>(kind, block, state, player, hand, item, record, now, stacks));
            case NEIGHBOUR -> result = handler.neighbourChanged(
                    new DeviceContext<>(kind, block, state, player, hand, item, record, now, stacks));
            default -> throw new IllegalStateException(op.name());
        }
        save(block, loaded, result.updated().orElse(record));
        return result.consumed();
    }

    /** The stored record, a fresh one when never written, or null when there is no readable device here. */
    private <S> Loaded<S> load(Block block, DeviceKind<S> kind) {
        Optional<DataMap> raw = store.get().read(block);
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.get().size() == 0) {
            return new Loaded<>(kind, kind.fresh(), false);
        }
        try {
            return new Loaded<>(kind, kind.codec().decode(raw.get()), true);
        } catch (PersistException e) {
            logger.warning("Device " + kind.id() + " at " + block.getWorld().getName() + " " + block.getX() + ","
                    + block.getY() + "," + block.getZ() + " has unreadable data; leaving it untouched: " + e.getMessage());
            return null;
        }
    }

    private <S> void save(Block block, Loaded<S> loaded, DeviceRecord<S> next) {
        if (loaded.stored() && next.equals(loaded.record())) {
            return;
        }
        if (!store.get().write(block, loaded.kind().codec().encode(next))) {
            logger.warning("Device " + loaded.kind().id() + " at " + block.getX() + "," + block.getY() + ","
                    + block.getZ() + ": its block entity refused the write");
        }
    }
}
