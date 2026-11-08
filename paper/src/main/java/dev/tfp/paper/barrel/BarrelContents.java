/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (BarrelInventory: the tank, the
 * three slots, the excess list, insertItemWithOverflow; serverTick: the excess move)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.StackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/**
 * A barrel's contents as real stacks, an immutable value: every stack is copied in and out, so a contents value can be
 * shared and compared ({@link ItemStack#equals} is deep).
 *
 * @param fluid        the tank
 * @param main         TFC {@code SLOT_ITEM}
 * @param excess       TFC's excess list (the stacks that did not fit the main slot)
 * @param containerIn  TFC {@code SLOT_FLUID_CONTAINER_IN}
 * @param containerOut TFC {@code SLOT_FLUID_CONTAINER_OUT}
 */
public record BarrelContents(FluidAmount fluid, ItemStack main,
        List<ItemStack> excess, ItemStack containerIn,
        ItemStack containerOut) {

    public BarrelContents {
        Objects.requireNonNull(fluid, "fluid");
        main = copy(main);
        containerIn = copy(containerIn);
        containerOut = copy(containerOut);
        List<ItemStack> ex = new ArrayList<>();
        for (ItemStack s : excess) {
            if (!StackCodec.isEmpty(s)) {
                ex.add(s.clone());
            }
        }
        excess = List.copyOf(ex);
    }

    @Override
    public ItemStack main() {
        return main.clone();
    }

    @Override
    public ItemStack containerIn() {
        return containerIn.clone();
    }

    @Override
    public ItemStack containerOut() {
        return containerOut.clone();
    }

    @Override
    public List<ItemStack> excess() {
        return excess.stream().map(ItemStack::clone).toList();
    }

    public boolean mainEmpty() {
        return StackCodec.isEmpty(main);
    }

    public BarrelContents withFluid(FluidAmount next) {
        return new BarrelContents(next, main, excess, containerIn, containerOut);
    }

    public BarrelContents withMain(ItemStack next) {
        return new BarrelContents(fluid, next, excess, containerIn, containerOut);
    }

    public BarrelContents withExcess(List<ItemStack> next) {
        return new BarrelContents(fluid, main, next, containerIn, containerOut);
    }

    public BarrelContents withContainers(ItemStack in, ItemStack out) {
        return new BarrelContents(fluid, main, excess, in, out);
    }

    /** TFC serverTick: an excess stack into an empty main slot. */
    public BarrelContents flushed() {
        if (excess.isEmpty() || !mainEmpty()) {
            return this;
        }
        return new BarrelContents(fluid, excess.getFirst(), excess.subList(1, excess.size()), containerIn, containerOut);
    }

    /**
     * TFC {@code insertItemWithOverflow}: into the main slot (NeoForge {@code ItemStackHandler.insertItem}: an empty slot
     * takes up to the max stack size, a similar stack fills up to it), the rest onto the excess list.
     */
    public BarrelContents insertWithOverflow(ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return this;
        }
        int limit = Math.min(64, stack.getMaxStackSize());
        ItemStack nextMain = main.clone();
        ItemStack rest = stack.clone();
        if (StackCodec.isEmpty(main)) {
            int put = Math.min(limit, stack.getAmount());
            nextMain = stack.clone();
            nextMain.setAmount(put);
            rest.setAmount(stack.getAmount() - put);
        } else if (main.isSimilar(stack)) {
            int put = Math.max(0, Math.min(limit - main.getAmount(), stack.getAmount()));
            nextMain.setAmount(main.getAmount() + put);
            rest.setAmount(stack.getAmount() - put);
        }
        List<ItemStack> ex = new ArrayList<>(excess);
        if (rest.getAmount() > 0) {
            ex.add(rest);
        }
        return new BarrelContents(fluid, nextMain, ex, containerIn, containerOut);
    }

    private static ItemStack copy(ItemStack s) {
        return StackCodec.isEmpty(s) ? ItemStack.empty() : s.clone();
    }

    static BarrelContents empty() {
        return new BarrelContents(FluidAmount.EMPTY, null, List.of(), null, null);
    }
}
