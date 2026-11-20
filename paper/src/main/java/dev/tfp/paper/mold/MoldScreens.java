/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/MoldLikeAlloyContainer.java @ c3b4d9536
 */
package dev.tfp.paper.mold;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.core.mold.MoldPouring;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.SlotPredicate;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-POT-05: the pour screen (TFC {@code MoldLikeAlloyContainer}): a one-slot screen opened by right-clicking a molten
 * vessel or a molten mold. A mold put in the slot is filled one mB per tick from the item in hand while the screen is
 * open; that is the only per-tick work and it exists only for the screen's viewer. Closing returns the mold.
 *
 * <p>Threads: every method runs on the viewer's thread (the entity scheduler task of the screen).
 */
public final class MoldScreens {
    /** The single mold slot, the middle of a one-row chest. */
    public static final int SLOT = 4;
    static final int OFF_HAND_INDEX = 40;
    static final String STATE = "mold.screen";
    static final String TASK = "mold.task";

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MoldItems molds;
    private final MoldPourer pourer;
    private final TfpGui screen;

    public MoldScreens(GuiService gui, Scheduler scheduler, Messages messages, MoldItems molds, MoldPourer pourer) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.molds = Objects.requireNonNull(molds, "molds");
        this.pourer = Objects.requireNonNull(pourer, "pourer");
        SlotPredicate accepts = (session, slot, stack) -> molds.isMold(stack);
        this.screen = TfpGui.builder(new ChestGeometry(1), new GuiLayers(null, List.of()))
            .label(p -> messages.get(p, "gui.mold_pour.title"))
            .input(SLOT, accepts)
            .onClose((session, player) -> close(session, player))
            .build();
    }

    /** The player inventory index of the item in {@code hand}. */
    static int indexOf(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND ? OFF_HAND_INDEX : player.getInventory().getHeldItemSlot();
    }

    /**
     * Opens the screen for the source in {@code hand}.
     *
     * @return the session, or null when the source is not molten or is no source
     */
    public GuiSession open(Player player, EquipmentSlot hand) {
        int index = indexOf(player, hand);
        ItemStack source = player.getInventory().getItem(index);
        if (source == null || !pourer.isSource(source) || !pourer.isMolten(source)) {
            return null;
        }
        GuiSession session = gui.open(player, screen);
        if (session == null) {
            return null;
        }
        session.put(STATE, index);
        Scheduler.TaskHandle task = scheduler.entityRepeating(player, 1, 1, () -> tick(session, player), () -> { });
        session.put(TASK, task);
        return session;
    }

    /** True when {@code session} is a pour screen. */
    public boolean isPourScreen(GuiSession session) {
        return session != null && session.gui() == screen;
    }

    /** The player inventory index of the source the screen pours from, or -1. */
    int sourceIndex(GuiSession session) {
        return session.get(STATE, -1);
    }

    /** One tick of the screen: puts one mB from the source into the mold in the slot (TFC {@code broadcastChanges}). */
    void tick(GuiSession session, Player player) {
        if (session.closed()) {
            cancel(session);
            return;
        }
        PlayerInventory inv = player.getInventory();
        int index = sourceIndex(session);
        ItemStack source = index < 0 ? null : inv.getItem(index);
        ItemStack inSlot = session.getInventory().getItem(SLOT);
        if (source == null || inSlot == null || inSlot.isEmpty() || !molds.isMold(inSlot)) {
            return;
        }
        if (inSlot.getAmount() > 1) { // a fluid goes into one mold: the rest of the stack goes back
            ItemStack rest = inSlot.clone();
            rest.setAmount(inSlot.getAmount() - 1);
            give(player, rest);
            inSlot.setAmount(1);
        }
        if (pourer.pour(source, inSlot, MoldPouring.STEP) > 0) {
            inv.setItem(index, source);
            session.getInventory().setItem(SLOT, inSlot);
        }
    }

    private void close(GuiSession session, Player player) {
        cancel(session);
        ItemStack inSlot = session.getInventory().getItem(SLOT);
        if (inSlot != null && !inSlot.isEmpty()) {
            session.getInventory().setItem(SLOT, null);
            give(player, inSlot);
        }
    }

    private static void cancel(GuiSession session) {
        Scheduler.TaskHandle task = session.get(TASK, null);
        if (task != null) {
            task.cancel();
        }
    }

    private static void give(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
