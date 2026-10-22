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
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
 * <p>Threads: every method runs on the region thread that owns the block (the {@link CustomBlocks} and
 * {@link DeviceStore} calls enforce it). The registry is thread-safe.
 */
public final class DeviceService implements DeviceRouter {
    /** What {@code /tfp debug device} shows. */
    public record Inspection(String kindId, BlockStateRef state, Optional<DeviceRecord<?>> record, long now,
            String problem) {
    }

    private record Loaded<S>(DeviceKind<S> kind, DeviceRecord<S> record, boolean stored) {
    }

    private final DeviceKinds kinds;
    private final CustomBlocks blocks;
    private final Supplier<DeviceStore> store;
    private final LongSupplier playerTicks;
    private final StackCodec stacks;
    private final Logger logger;

    /**
     * @param blocks      custom block lookups (to find the block id)
     * @param store       the device data store (read per call: the CraftEngine adapter installs it in onLoad)
     * @param playerTicks the calendar's player ticks (TFC {@code Calendars.SERVER.getTicks()}); one read per call
     */
    public DeviceService(DeviceKinds kinds, CustomBlocks blocks, Supplier<DeviceStore> store, LongSupplier playerTicks,
            StackCodec stacks, Logger logger) {
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.store = Objects.requireNonNull(store, "store");
        this.playerTicks = Objects.requireNonNull(playerTicks, "playerTicks");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
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

    /** A player placed the device (one tick after the placement, {@link DeviceListener}). */
    public void placed(Block block, Player player) {
        route(block, player, null, ItemStack.empty(), Op.PLACED);
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
        if (kind.removal() == DeviceKind.RemoveBehaviour.DROP) {
            // TFC Helpers.spawnItem(level, pos, stack): an item entity at the block centre, vanilla's random motion.
            Location centre = new Location(world, x + 0.5, y + 0.5, z + 0.5);
            for (ItemStack stack : ctx.inventory().contents()) {
                world.dropItem(centre, stack);
            }
        }
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
                DeviceResult<S> first = StackCodec.isEmpty(item) ? DeviceResult.pass() : handler.useWithItem(ctx);
                if (first.consumed()) {
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
