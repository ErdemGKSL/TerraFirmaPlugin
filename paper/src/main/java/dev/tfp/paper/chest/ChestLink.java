/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCChestBlock.java @ c3b4d9536 (MENU_PROVIDER_COMBINER:
 *       acceptDouble builds a CompoundContainer of the first and the second chest)
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.chest.ChestState;
import dev.tfp.core.chest.ChestType;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DECO-04: one chest or one double chest as a list of device slots. A single chest is its 18 slots; a double chest is
 * the 36 slots of TFC's {@code CompoundContainer(first, second)}, the right half first. The devices stay the authority:
 * the screen's copy is written back with {@link #commit}. Region thread of every block of the link
 * ({@link #resolve} returns empty when a half is owned by another region).
 */
public final class ChestLink {
    /** A block position inside a world. */
    public record Key(UUID world, int x, int y, int z) {
        public static Key of(Block block) {
            return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    /** What a screen shows: the slots (copies). */
    public record Snapshot(List<ItemStack> slots) {
        public Snapshot {
            slots = List.copyOf(slots);
        }

        /** Whether two slot lists hold equal stacks (empty and null alike). */
        public static boolean sameSlots(List<ItemStack> a, List<ItemStack> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                ItemStack x = a.get(i);
                ItemStack y = b.get(i);
                boolean ex = StackCodec.isEmpty(x);
                boolean ey = StackCodec.isEmpty(y);
                if (ex != ey || (!ex && !x.equals(y))) {
                    return false;
                }
            }
            return true;
        }
    }

    /** @param overflow what the chest refused (an item that does not fit a chest); the screen gives it back */
    public record Committed(Snapshot snapshot, List<ItemStack> overflow) {
    }

    private final DeviceService devices;
    private final List<Block> halves;
    private final List<DeviceKind<ChestState>> kinds;
    private final Predicate<ItemStack> fits;

    private ChestLink(DeviceService devices, List<Block> halves, List<DeviceKind<ChestState>> kinds, Predicate<ItemStack> fits) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.halves = List.copyOf(halves);
        this.kinds = List.copyOf(kinds);
        this.fits = Objects.requireNonNull(fits, "fits");
    }

    /**
     * The link of the chest at {@code block}: the block alone, or with its partner when the two agree (same wood and kind,
     * same facing, opposite types, the partner on the connected side). A stale half reads as a single chest.
     *
     * @return empty when {@code block} is no chest, or a half lies in a chunk this thread does not own
     */
    @SuppressWarnings("unchecked")
    public static Optional<ChestLink> resolve(DeviceService devices, CustomBlocks blocks, Scheduler scheduler, Block block,
            Predicate<ItemStack> fits) {
        Optional<ChestBlocks.Found> self = ChestBlocks.at(blocks, block);
        if (self.isEmpty()) {
            return Optional.empty();
        }
        Block other = null;
        ChestBlocks.Found otherFound = null;
        if (self.get().chest().type() != ChestType.SINGLE) {
            Block p = ChestBlocks.relative(block, ChestRules.partnerSide(self.get().chest().facing(), self.get().chest().type()));
            Optional<ChestBlocks.Found> pf = ChestBlocks.at(blocks, p);
            if (pf.isPresent() && pf.get().id().equals(self.get().id())
                && pf.get().chest().facing() == self.get().chest().facing()
                && pf.get().chest().type() == self.get().chest().type().opposite()) {
                other = p;
                otherFound = pf.get();
            }
        }
        List<Block> halves = new ArrayList<>(2);
        if (other == null) {
            halves.add(block);
        } else if (ChestRules.isFirst(self.get().chest().type())) {
            halves.add(block);
            halves.add(other);
        } else {
            halves.add(other);
            halves.add(block);
        }
        List<DeviceKind<ChestState>> kinds = new ArrayList<>(2);
        for (Block half : halves) {
            if (!scheduler.isOwner(half.getWorld(), half.getX() >> 4, half.getZ() >> 4)) {
                return Optional.empty();
            }
            Optional<ChestBlocks.Found> f = half == block ? self : Optional.ofNullable(otherFound);
            Optional<DeviceKind<?>> kind = devices.kinds().find(f.get().id());
            if (kind.isEmpty()) {
                return Optional.empty();
            }
            kinds.add((DeviceKind<ChestState>) kind.get());
        }
        return Optional.of(new ChestLink(devices, halves, kinds, fits));
    }

    /** Positions of the blocks, first half first. */
    public List<Key> keys() {
        return halves.stream().map(Key::of).toList();
    }

    public boolean isDouble() {
        return halves.size() == 2;
    }

    /** 18 or 36. */
    public int size() {
        return halves.size() * ChestRules.SINGLE_SLOTS;
    }

    public Block firstBlock() {
        return halves.getFirst();
    }

    /** The slots as stored (empty when a half is no longer a readable chest device). */
    public Optional<Snapshot> snapshot() {
        StackCodec stacks = devices.stacks();
        List<ItemStack> slots = new ArrayList<>(size());
        for (Block half : halves) {
            Optional<DeviceService.Inspection> in = devices.inspect(half);
            if (in.isEmpty() || in.get().record().isEmpty()) {
                return Optional.empty();
            }
            DeviceRecord<?> record = in.get().record().get();
            for (int i = 0; i < ChestRules.SINGLE_SLOTS; i++) {
                slots.add(stacks.decode(record.slot(i)));
            }
        }
        return Optional.of(new Snapshot(slots));
    }

    /**
     * Stores the screen's slots. A stack TFC's chest would refuse ({@link #fits}) is not stored and comes back as overflow.
     *
     * @return empty when a half is gone
     */
    public Optional<Committed> commit(List<ItemStack> slots) {
        if (slots.size() != size()) {
            throw new IllegalArgumentException(slots.size() + " slots for " + size());
        }
        StackCodec stacks = devices.stacks();
        List<ItemStack> overflow = new ArrayList<>();
        List<ItemStack> stored = new ArrayList<>(size());
        for (int h = 0; h < halves.size(); h++) {
            int base = h * ChestRules.SINGLE_SLOTS;
            Optional<DeviceRecord<ChestState>> r = devices.edit(halves.get(h), kinds.get(h), (record, now) -> {
                DeviceRecord<ChestState> next = record;
                for (int i = 0; i < ChestRules.SINGLE_SLOTS; i++) {
                    ItemStack s = slots.get(base + i);
                    if (StackCodec.isEmpty(s)) {
                        next = next.withSlot(i, stacks.encode(ItemStack.empty()));
                    } else if (!fits.test(s)) {
                        overflow.add(s.clone());
                        next = next.withSlot(i, stacks.encode(ItemStack.empty()));
                    } else {
                        next = next.withSlot(i, stacks.encode(s.clone()));
                    }
                }
                return next;
            });
            if (r.isEmpty()) {
                return Optional.empty();
            }
            for (int i = 0; i < ChestRules.SINGLE_SLOTS; i++) {
                stored.add(stacks.decode(r.get().slot(i)));
            }
        }
        return Optional.of(new Committed(new Snapshot(stored), List.copyOf(overflow)));
    }
}
