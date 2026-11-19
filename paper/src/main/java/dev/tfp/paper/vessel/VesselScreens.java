/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SmallVesselInventoryContainer.java @ c3b4d9536 (four slots,
 *       stillValid: inventory mode only),
 *       src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (use: open, too hot, solid alloy)
 */
package dev.tfp.paper.vessel;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.vessel.VesselRules;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.SlotPredicate;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-POT-04: the small vessel's four-slot screen on the GUI framework. A plain three-row chest (no background art yet:
 * the textured screen is PAR-POT-10) with the four slots in a 2 x 2 block. The screen holds a copy of the vessel's
 * slots; every click commits it to the vessel item one tick later and closing commits it again, so the item is always the
 * authority. The vessel stays where it was when the screen opened ({@link Handle}); {@link VesselListener} refuses clicks
 * that would move it (TFC's container keeps the open stack in its slot too).
 *
 * <p>Threads: the viewer's thread (the entity scheduler); nothing is shared between viewers.
 */
public final class VesselScreens {
    /** The four screen slots, a 2 x 2 block in the middle of the three-row chest. */
    public static final int[] SLOTS = {12, 13, 21, 22};
    static final String STATE = "vessel.screen";
    static final String PENDING = "vessel.pending";

    /** Where the open vessel is: the player inventory index (0-35 main, 40 off hand). */
    record Handle(int index) {
        static Handle of(Player player, EquipmentSlot hand) {
            return new Handle(hand == EquipmentSlot.OFF_HAND ? OFF_HAND_INDEX : player.getInventory().getHeldItemSlot());
        }
    }

    static final int OFF_HAND_INDEX = 40;

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final VesselItems items;
    private final TfpGui screen;

    public VesselScreens(GuiService gui, Scheduler scheduler, Messages messages, VesselItems items) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.items = Objects.requireNonNull(items, "items");
        this.screen = build();
    }

    private TfpGui build() {
        TfpGui.Builder b = TfpGui.builder(new ChestGeometry(3), new GuiLayers(null, List.of()))
            .label(p -> messages.get(p, "gui.vessel.title"));
        SlotPredicate accepts = (session, slot, stack) -> items.canContain(stack);
        for (int slot : SLOTS) {
            b.input(slot, accepts);
        }
        return b.onClose((session, player) -> commit(session, player, true)).build();
    }

    /**
     * TFC {@code VesselItem.use}: opens the screen of the vessel in {@code hand}, or tells the player why not. Call on the
     * player's thread.
     *
     * @return the session, or null when the vessel does not open
     */
    public GuiSession use(Player player, EquipmentSlot hand) {
        Handle handle = Handle.of(player, hand);
        PlayerInventory inv = player.getInventory();
        ItemStack vessel = inv.getItem(handle.index());
        if (!items.isVessel(vessel)) {
            return null;
        }
        if (items.settle(vessel)) {
            inv.setItem(handle.index(), vessel);
        }
        if (!items.isInventory(vessel)) {
            if (items.isMolten(vessel)) {
                return null; // pouring into a mold is PAR-POT-05
            }
            boolean alloy = !items.fluid(vessel).isEmpty();
            player.sendActionBar(Component.text(messages.get(player, alloy ? "vessel.alloy_solid" : "vessel.too_hot")));
            return null;
        }
        GuiSession session = gui.open(player, screen);
        if (session == null) {
            return null;
        }
        session.put(STATE, handle);
        List<ItemStack> slots = items.slots(vessel);
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack s = slots.get(i);
            session.getInventory().setItem(SLOTS[i], s == null || s.isEmpty() ? null : s);
        }
        return session;
    }

    /** True when {@code session} is a vessel screen. */
    public boolean isVesselScreen(GuiSession session) {
        return session != null && session.gui() == screen;
    }

    /** The player inventory index of the vessel a vessel screen holds open, or -1. */
    int handleIndex(GuiSession session) {
        Handle h = session.get(STATE, null);
        return h == null ? -1 : h.index();
    }

    /** Commits {@code session} one tick from now (once per tick), on the viewer's thread. */
    void commitSoon(GuiSession session) {
        if (session.get(PENDING, Boolean.FALSE)) {
            return;
        }
        session.put(PENDING, Boolean.TRUE);
        scheduler.entityDelayed(session.viewer(), 1, () -> {
            session.put(PENDING, Boolean.FALSE);
            if (!session.closed()) {
                commit(session, session.viewer(), false);
            }
        }, () -> { });
    }

    /**
     * Writes the screen's slots to the vessel item. Items above the slot capacity, and every item when the vessel is
     * gone or no longer in inventory mode, go back to the player (so nothing is lost or copied).
     *
     * @param closing whether the screen is closing: the leftovers are returned and the screen's slots cleared
     */
    void commit(GuiSession session, Player player, boolean closing) {
        Handle handle = session.get(STATE, null);
        if (handle == null) {
            return;
        }
        Inventory top = session.getInventory();
        PlayerInventory inv = player.getInventory();
        ItemStack vessel = inv.getItem(handle.index());
        boolean valid = items.isVessel(vessel) && items.isInventory(vessel);
        List<ItemStack> keep = new ArrayList<>(SLOTS.length);
        List<ItemStack> back = new ArrayList<>();
        for (int slot : SLOTS) {
            ItemStack s = top.getItem(slot);
            if (s == null || s.isEmpty()) {
                keep.add(ItemStack.empty());
                continue;
            }
            int max = Math.min(s.getMaxStackSize(), VesselRules.SLOT_CAPACITY);
            if (!valid || !items.canContain(s)) {
                back.add(s.clone());
                top.setItem(slot, null);
                keep.add(ItemStack.empty());
            } else if (s.getAmount() > max) {
                ItemStack rest = s.clone();
                rest.setAmount(s.getAmount() - max);
                back.add(rest);
                ItemStack in = s.clone();
                in.setAmount(max);
                top.setItem(slot, in);
                keep.add(in);
            } else {
                keep.add(s.clone());
            }
        }
        if (valid && dev.tfp.paper.itemdata.ContentsPdc.write(vessel, keep)) {
            inv.setItem(handle.index(), vessel);
        } else if (valid) {
            for (int slot : SLOTS) { // a newer data version: never overwrite, hand everything back
                ItemStack s = top.getItem(slot);
                if (s != null && !s.isEmpty()) {
                    back.add(s.clone());
                    top.setItem(slot, null);
                }
            }
        }
        for (ItemStack s : back) {
            for (ItemStack rest : inv.addItem(s).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        if (closing) {
            for (int slot : SLOTS) {
                top.setItem(slot, null);
            }
        }
    }
}
