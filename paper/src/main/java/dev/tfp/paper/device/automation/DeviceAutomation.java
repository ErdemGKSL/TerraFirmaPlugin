/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/SidedHandler.java @ c3b4d9536 (one per block entity)
 */
package dev.tfp.paper.device.automation;

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
     * One device kind's automation.
     *
     * @param kindId   the device kind id ({@code DeviceKind#id}; covers {@code id} and {@code id/...})
     * @param sides    the side table per block state
     * @param enabled  TFC's {@code *EnableAutomation}, read at each move
     * @param locked   TFC's {@code canModify} gate on the record's state (a sealed barrel or keg refuses both ways)
     * @param afterMove the slot fix after a move (the firepit's fuel cascade)
     * @param busy     a device-specific reason to wait (the block on the region thread); the hopper retries
     */
    public record Spec(String kindId, Sides sides, BooleanSupplier enabled, Predicate<Object> locked, SlotFix afterMove,
            Predicate<Block> busy) {
        public Spec {
            Objects.requireNonNull(kindId, "kindId");
            Objects.requireNonNull(sides, "sides");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(locked, "locked");
            Objects.requireNonNull(afterMove, "afterMove");
            Objects.requireNonNull(busy, "busy");
        }

        /** Enabled, never locked, no slot fix, never busy. */
        public static Spec of(String kindId, Sides sides) {
            return new Spec(kindId, sides, () -> true, state -> false, SlotFix.NONE, block -> false);
        }

        public Spec withEnabled(BooleanSupplier value) {
            return new Spec(kindId, sides, value, locked, afterMove, busy);
        }

        public Spec withLocked(Predicate<Object> value) {
            return new Spec(kindId, sides, enabled, value, afterMove, busy);
        }

        public Spec withAfterMove(SlotFix value) {
            return new Spec(kindId, sides, enabled, locked, value, busy);
        }

        public Spec withBusy(Predicate<Block> value) {
            return new Spec(kindId, sides, enabled, locked, afterMove, value);
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
