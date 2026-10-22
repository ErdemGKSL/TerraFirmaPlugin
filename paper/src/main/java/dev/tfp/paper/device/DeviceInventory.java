/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/InventoryBlockEntity.java @ c3b4d9536 (inventory, clearContent)
 */
package dev.tfp.paper.device;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.itemdata.ItemSlotRules;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * The fixed slot list of a device (TFC {@code InventoryBlockEntity}'s {@code ItemStackHandler}) as an immutable view
 * over a {@link DeviceRecord} (PAR-INFRA-02). Changes return a new inventory; {@link #record()} is what a handler
 * stores. Insert follows TFC's handler: the kind's {@link DeviceKind.SlotFilter} ({@code isItemValid}), the slot limit
 * and the item's max stack size ({@link ItemSlotRules}). Stacks handed out are copies.
 *
 * <p>Threads: region thread of the device (stacks are decoded there).
 */
public final class DeviceInventory<S> {
    /** The inventory after an insert and what was left of the inserted stack. */
    public record Insert<S>(DeviceInventory<S> inventory, ItemStack remainder) {
    }

    /** The inventory after an extract and the stack taken out (empty when nothing was). */
    public record Extract<S>(DeviceInventory<S> inventory, ItemStack extracted) {
    }

    private final DeviceKind<S> kind;
    private final DeviceRecord<S> record;
    private final StackCodec stacks;

    public DeviceInventory(DeviceKind<S> kind, DeviceRecord<S> record, StackCodec stacks) {
        this.kind = kind;
        this.record = record;
        this.stacks = stacks;
    }

    public DeviceRecord<S> record() {
        return record;
    }

    public int size() {
        return record.slots().size();
    }

    /** A copy of the slot's stack, {@link ItemStack#empty()} for an empty slot. */
    public ItemStack get(int slot) {
        return stacks.decode(record.slot(slot));
    }

    /** Replaces the slot (no filter check: TFC {@code setStackInSlot}). */
    public DeviceInventory<S> set(int slot, ItemStack stack) {
        return with(record.withSlot(slot, stacks.encode(stack)));
    }

    /** TFC {@code isItemValid}: the kind's filter, for a slot the kind declares. */
    public boolean canInsert(int slot, ItemStack stack) {
        return slot >= 0 && slot < kind.slots() && !StackCodec.isEmpty(stack) && kind.filter().canInsert(slot, stack);
    }

    /** TFC {@code insertItem(slot, stack, false)}. {@code stack} is not modified. */
    public Insert<S> insert(int slot, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return new Insert<>(this, ItemStack.empty());
        }
        ItemStack content = get(slot);
        boolean empty = StackCodec.isEmpty(content);
        ItemSlotRules.Insert r = ItemSlotRules.insert(empty ? 0 : content.getAmount(), stack.getAmount(),
                !empty && content.isSimilar(stack), canInsert(slot, stack), stack.getMaxStackSize(), kind.slotLimit());
        if (r.remainderCount() == stack.getAmount()) {
            return new Insert<>(this, stack.clone());
        }
        ItemStack next = (empty ? stack : content).clone();
        next.setAmount(r.contentCount());
        ItemStack remainder = r.remainderCount() == 0 ? ItemStack.empty() : stack.clone();
        if (r.remainderCount() > 0) {
            remainder.setAmount(r.remainderCount());
        }
        return new Insert<>(set(slot, next), remainder);
    }

    /** Inserts into the slots in order until nothing remains (TFC {@code ItemHandlerHelper.insertItem}). */
    public Insert<S> insert(ItemStack stack) {
        DeviceInventory<S> inv = this;
        ItemStack rest = stack == null ? ItemStack.empty() : stack.clone();
        for (int slot = 0; slot < kind.slots() && !StackCodec.isEmpty(rest); slot++) {
            Insert<S> r = inv.insert(slot, rest);
            inv = r.inventory();
            rest = r.remainder();
        }
        return new Insert<>(inv, rest);
    }

    /** TFC {@code extractItem(slot, amount, false)}. */
    public Extract<S> extract(int slot, int amount) {
        ItemStack content = get(slot);
        if (StackCodec.isEmpty(content) || amount <= 0) {
            return new Extract<>(this, ItemStack.empty());
        }
        ItemSlotRules.Extract r = ItemSlotRules.extract(content.getAmount(), amount);
        ItemStack out = content.clone();
        out.setAmount(r.extractedCount());
        ItemStack left = content.clone();
        left.setAmount(r.contentCount());
        return new Extract<>(set(slot, r.contentCount() == 0 ? ItemStack.empty() : left), out);
    }

    /** The non-empty stacks in slot order (what {@link DeviceKind.RemoveBehaviour#DROP} spawns). */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemBlob b : record.nonEmptySlots()) {
            out.add(stacks.decode(b));
        }
        return out;
    }

    /** TFC {@code clearContent}. */
    public DeviceInventory<S> cleared() {
        return with(record.cleared());
    }

    private DeviceInventory<S> with(DeviceRecord<S> next) {
        return new DeviceInventory<>(kind, next, stacks);
    }
}
