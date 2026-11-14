/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.vessel.FluidAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-09: a barrel that lives only while its screen is open ({@code /tfp debug barrelgui}), to see and check the
 * screen before the barrel block opens it (PAR-PROC-35). Sealing drops the container slots to the viewer (TFC
 * {@code onSeal} spawns them at the block); closing gives every slot back. No recipes run.
 */
public final class DemoBarrelLink implements BarrelLink {
    private BarrelSnapshot barrel;

    public DemoBarrelLink(FluidAmount fluid, int capacity) {
        List<ItemStack> empty = List.of(ItemStack.empty(), ItemStack.empty(), ItemStack.empty());
        this.barrel = new BarrelSnapshot(false, fluid, capacity, empty);
    }

    @Override
    public Optional<BarrelSnapshot> snapshot() {
        return Optional.of(barrel);
    }

    @Override
    public Optional<BarrelSnapshot> commit(List<ItemStack> slots) {
        barrel = barrel.withSlots(slots);
        return snapshot();
    }

    @Override
    public Optional<BarrelSnapshot> toggleSeal(Player viewer) {
        if (!barrel.sealed()) {
            List<ItemStack> slots = new ArrayList<>(barrel.slots());
            give(viewer, slots.set(BarrelScreenLayout.TFC_FLUID_IN, ItemStack.empty()));
            give(viewer, slots.set(BarrelScreenLayout.TFC_FLUID_OUT, ItemStack.empty()));
            barrel = barrel.withSlots(slots);
        }
        barrel = barrel.withSealed(!barrel.sealed());
        return snapshot();
    }

    @Override
    public List<String> sealedLines(Player viewer, BarrelSnapshot shown) {
        return List.of("tfp debug barrelgui");
    }

    @Override
    public void closed(Player viewer) {
        for (ItemStack s : barrel.slots()) {
            give(viewer, s);
        }
        barrel = barrel.withSlots(List.of(ItemStack.empty(), ItemStack.empty(), ItemStack.empty()));
    }

    private static void give(Player viewer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : viewer.getInventory().addItem(stack.clone()).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), rest);
        }
    }
}
