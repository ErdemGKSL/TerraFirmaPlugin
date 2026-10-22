/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DeviceBlock.java @ c3b4d9536 (InventoryRemoveBehavior),
 *       src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java @ c3b4d9536 (slots)
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.CatchUp;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.DeviceRecordCodec;
import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.inventory.ItemStack;

/**
 * One kind of device block (PAR-INFRA-02): which CraftEngine blocks it covers, how its state persists, its inventory,
 * its lazy simulation and its reactions. The TFC counterpart is a {@code DeviceBlock} plus its
 * {@code InventoryBlockEntity} type; here the block is any CraftEngine block whose behaviour is {@code tfp:device}, and
 * the kind is found by its block id ({@link DeviceKinds}).
 *
 * <p>{@link #id()} names one block ({@code tfp:device/firepit}) or a family by prefix: a kind {@code tfp:crucible}
 * also covers {@code tfp:crucible/<anything>}. The longest matching id wins.
 *
 * <p>Immutable, any thread. Holds no world or CraftEngine reference, so a {@code /ce reload} leaves it valid.
 *
 * @param <S> the kind's own state, immutable with value equality
 */
public final class DeviceKind<S> {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** TFC {@code DeviceBlock.InventoryRemoveBehavior}. {@code SAVE} (keep contents on the dropped item) is PAR-INFRA-18. */
    public enum RemoveBehaviour {
        /** The contents vanish with the block. */
        NOOP,
        /** TFC {@code ejectInventory}: every non-empty slot is spawned at the block centre. */
        DROP
    }

    /** TFC {@code IItemHandler.isItemValid(slot, stack)}: whether {@code stack} may go into {@code slot}. */
    @FunctionalInterface
    public interface SlotFilter {
        SlotFilter ANY = (slot, stack) -> true;

        boolean canInsert(int slot, ItemStack stack);
    }

    private final String id;
    private final VersionedCodec<S> stateCodec;
    private final S initial;
    private final int slots;
    private final int slotLimit;
    private final SlotFilter filter;
    private final CatchUp.Advance<S> advance;
    private final DeviceHandler<S> handler;
    private final RemoveBehaviour removal;
    private final VersionedCodec<DeviceRecord<S>> codec;

    private DeviceKind(Builder<S> b) {
        this.id = b.id;
        this.stateCodec = b.stateCodec;
        this.initial = b.initial;
        this.slots = b.slots;
        this.slotLimit = b.slotLimit;
        this.filter = b.filter;
        this.advance = b.advance;
        this.handler = b.handler;
        this.removal = b.removal;
        this.codec = DeviceRecordCodec.of("device " + id, stateCodec, slots);
    }

    /**
     * @param id         the block id or id prefix ({@code namespace:path})
     * @param stateCodec the kind's state codec (its own version and migrations)
     * @param initial    the state of a freshly placed or generated device
     */
    public static <S> Builder<S> builder(String id, VersionedCodec<S> stateCodec, S initial) {
        return new Builder<>(id, stateCodec, initial);
    }

    public String id() {
        return id;
    }

    /** True if this kind covers {@code blockId}: equal, or a sub-path of {@link #id()}. */
    public boolean covers(String blockId) {
        return blockId.equals(id) || blockId.startsWith(id + "/");
    }

    public VersionedCodec<S> stateCodec() {
        return stateCodec;
    }

    /** The persisted layout of a whole record of this kind. */
    public VersionedCodec<DeviceRecord<S>> codec() {
        return codec;
    }

    public int slots() {
        return slots;
    }

    /** TFC {@code getSlotLimit}: the most items one slot holds (also capped by the item's max stack size). */
    public int slotLimit() {
        return slotLimit;
    }

    public SlotFilter filter() {
        return filter;
    }

    public CatchUp.Advance<S> advance() {
        return advance;
    }

    public DeviceHandler<S> handler() {
        return handler;
    }

    public RemoveBehaviour removal() {
        return removal;
    }

    /** A device that was never stored: the initial state, empty slots, never updated. */
    public DeviceRecord<S> fresh() {
        return DeviceRecord.fresh(initial, slots);
    }

    @Override
    public String toString() {
        return "DeviceKind[" + id + ", " + slots + " slot(s), " + removal + "]";
    }

    /** Builder; defaults: no slots, slot limit 64, any item, no simulation, no reactions, {@link RemoveBehaviour#DROP}. */
    public static final class Builder<S> {
        private final String id;
        private final VersionedCodec<S> stateCodec;
        private final S initial;
        private int slots;
        private int slotLimit = 64;
        private SlotFilter filter = SlotFilter.ANY;
        private CatchUp.Advance<S> advance = CatchUp.Advance.none();
        private DeviceHandler<S> handler = new DeviceHandler<>() {
        };
        private RemoveBehaviour removal = RemoveBehaviour.DROP;

        private Builder(String id, VersionedCodec<S> stateCodec, S initial) {
            if (id == null || !ID.matcher(id).matches() || id.endsWith("/")) {
                throw new IllegalArgumentException("device kind id must be namespace:path, got '" + id + "'");
            }
            this.id = id;
            this.stateCodec = Objects.requireNonNull(stateCodec, "stateCodec");
            this.initial = Objects.requireNonNull(initial, "initial");
        }

        public Builder<S> slots(int count) {
            if (count < 0 || count > DeviceRecordCodec.MAX_SLOTS) {
                throw new IllegalArgumentException(id + ": slots outside 0.." + DeviceRecordCodec.MAX_SLOTS);
            }
            this.slots = count;
            return this;
        }

        public Builder<S> slotLimit(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException(id + ": slotLimit < 1");
            }
            this.slotLimit = limit;
            return this;
        }

        public Builder<S> filter(SlotFilter filter) {
            this.filter = Objects.requireNonNull(filter, "filter");
            return this;
        }

        public Builder<S> advance(CatchUp.Advance<S> advance) {
            this.advance = Objects.requireNonNull(advance, "advance");
            return this;
        }

        public Builder<S> handler(DeviceHandler<S> handler) {
            this.handler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        public Builder<S> removal(RemoveBehaviour removal) {
            this.removal = Objects.requireNonNull(removal, "removal");
            return this;
        }

        public DeviceKind<S> build() {
            return new DeviceKind<>(this);
        }
    }
}
