/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (one per block entity)
 */
package dev.tfp.paper.device.automation;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.device.automation.SideTable;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.bukkit.block.Block;

/**
 * PAR-INFRA-60: the registry of device kinds that hoppers feed and empty (TFC builds a {@code SidedHandler} per block
 * entity when its {@code *EnableAutomation} is on). A device module registers its kind with
 * {@code services.require(DeviceAutomation.class).register(Spec.of(kindId, sides))}; {@link DeviceAutomationModule}
 * registers the devices that existed when it was written. A later registration of the same kind id replaces the earlier
 * one, so a device's own module can take over from the defaults.
 *
 * <p>Threads: thread-safe (a concurrent map of immutable specs).
 */
public final class DeviceAutomation {
    /** The sides of a device in the given block state (the barrel's {@code facing}). Pure, any thread. */
    @FunctionalInterface
    public interface Sides {
        SideTable at(BlockStateRef state);
    }

    /** What a device does to its slots after a hopper moved an item (TFC {@code setAndUpdateSlots}). Pure. */
    @FunctionalInterface
    public interface SlotFix {
        SlotFix NONE = slots -> slots;

        List<ItemBlob> apply(List<ItemBlob> slots);
    }

    /**
     * A change of the record after a move that needs the world (TFC's {@code serverTick}: the barrel's fluid IO slots
     * settle within 5 ticks of a hopper insert; here at once). Region thread of the device.
     */
    @FunctionalInterface
    public interface Settle {
        Settle NONE = (record, now, block) -> record;

        DeviceRecord<?> apply(DeviceRecord<?> record, long now, Block block);
    }

    /**
     * PAR-INFRA-66: a device's open screen, kept in step with a hopper move instead of pausing automation near any screen.
     * The screen keeps a copy of the slots; {@link #edit} commits it before the move, {@link #reload} re-reads the device
     * into it after, so nothing dupes or is lost. {@link #unsyncable} is true when the viewer is on another region (Folia),
     * where neither can run inline: the move then waits. Region thread of the device.
     */
    public interface ScreenSync {
        ScreenSync NONE = new ScreenSync() {
            @Override
            public boolean unsyncable(Block device) {
                return false;
            }

            @Override
            public void edit(Block device) {
            }

            @Override
            public void reload(Block device) {
            }
        };

        boolean unsyncable(Block device);

        void edit(Block device);

        void reload(Block device);
    }

    /**
     * One device kind's automation.
     *
     * @param kindId   the device kind id ({@code DeviceKind#id}; covers {@code id} and {@code id/...})
     * @param sides    the side table per block state
     * @param enabled  TFC's {@code *EnableAutomation}, read at each move
     * @param locked   TFC's {@code canModify} gate on the record's state (a sealed barrel or keg refuses both ways)
     * @param afterMove the slot fix after a move (the firepit's fuel cascade)
     * @param busy     a device-specific reason to wait (the block on the region thread); the hopper retries
     * @param settle   the world-dependent change of the record after a move (the barrel's fluid IO)
     * @param screens  the open screen of the device (edit before, reload after the move)
     */
    public record Spec(String kindId, Sides sides, BooleanSupplier enabled, Predicate<Object> locked, SlotFix afterMove,
            Predicate<Block> busy, Settle settle, ScreenSync screens) {
        public Spec {
            Objects.requireNonNull(kindId, "kindId");
            Objects.requireNonNull(sides, "sides");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(locked, "locked");
            Objects.requireNonNull(afterMove, "afterMove");
            Objects.requireNonNull(busy, "busy");
            Objects.requireNonNull(settle, "settle");
            Objects.requireNonNull(screens, "screens");
        }

        /** Enabled, never locked, no slot fix, never busy. */
        public static Spec of(String kindId, Sides sides) {
            return new Spec(kindId, sides, () -> true, state -> false, SlotFix.NONE, block -> false, Settle.NONE,
                ScreenSync.NONE);
        }

        public Spec withEnabled(BooleanSupplier value) {
            return new Spec(kindId, sides, value, locked, afterMove, busy, settle, screens);
        }

        public Spec withLocked(Predicate<Object> value) {
            return new Spec(kindId, sides, enabled, value, afterMove, busy, settle, screens);
        }

        public Spec withAfterMove(SlotFix value) {
            return new Spec(kindId, sides, enabled, locked, value, busy, settle, screens);
        }

        public Spec withBusy(Predicate<Block> value) {
            return new Spec(kindId, sides, enabled, locked, afterMove, value, settle, screens);
        }

        public Spec withSettle(Settle value) {
            return new Spec(kindId, sides, enabled, locked, afterMove, busy, value, screens);
        }

        public Spec withScreens(ScreenSync value) {
            return new Spec(kindId, sides, enabled, locked, afterMove, busy, settle, value);
        }

        /** Whether a move has to wait: the device's own reason, or an open screen that cannot be kept in step. */
        public boolean waiting(Block device) {
            return busy.test(device) || screens.unsyncable(device);
        }

        /** {@code DeviceKind#covers}: the id itself or one of its sub-ids. */
        public boolean covers(String blockId) {
            return blockId.equals(kindId) || blockId.startsWith(kindId + "/");
        }
    }

    private final Map<String, Spec> specs = new ConcurrentHashMap<>();

    /** Registers (or replaces) the automation of {@code spec.kindId()}; closing removes this registration. */
    public AutoCloseable register(Spec spec) {
        Objects.requireNonNull(spec, "spec");
        specs.put(spec.kindId(), spec);
        return () -> specs.remove(spec.kindId(), spec);
    }

    /** The spec covering {@code blockId}, the longest kind id first. */
    public Optional<Spec> find(String blockId) {
        Spec best = null;
        for (Spec s : specs.values()) {
            if (s.covers(blockId) && (best == null || s.kindId().length() > best.kindId().length())) {
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The registered kind ids (debug). */
    public List<String> kindIds() {
        return specs.keySet().stream().sorted().toList();
    }
}
