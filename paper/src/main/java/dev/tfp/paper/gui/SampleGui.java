/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/LargeVesselContainer.java @ c3b4d9536 (clicked: container slots
 *       refuse clicks while sealed; moveStack: no shift-click while sealed),
 *       src/main/java/net/dries007/tfc/common/container/Container.java @ c3b4d9536 (giveItemStackToPlayerOrDrop)
 */
package dev.tfp.paper.gui;

import dev.tfp.core.gui.SampleScreen;
import dev.tfp.paper.i18n.Messages;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-03: the {@code /tfp debug gui sample} screen ({@link SampleScreen}): TFC's large vessel art in a 3-row chest,
 * nine storage slots and the seal button. Sealing shows the sealed button frame and locks the slots, as TFC's large vessel
 * does; the contents go back to the player when the screen closes (nothing is stored).
 */
public final class SampleGui {
    static final String SEALED = "sealed";

    private SampleGui() {
    }

    public static TfpGui create(GuiService service, Messages messages) {
        TfpGui.Builder b = TfpGui.builder(SampleScreen.CHEST, SampleScreen.LAYERS)
            .label(p -> messages.get(p, "gui.sample.title"));
        SlotPredicate notAButton = (session, slot, stack) -> !GuiButtons.isButton(stack);
        for (int chestSlot : SampleScreen.SLOTS.fillOrder()) {
            b.input(chestSlot, notAButton);
        }
        return b.button(SampleScreen.BUTTON_SLOT,
                s -> GuiButtons.icon(service.glyphs(),
                    messages.get(s.viewer(), sealed(s) ? "gui.sample.unseal" : "gui.sample.seal")),
                (s, click) -> {
                    s.put(SEALED, !sealed(s));
                    s.refresh();
                })
            .frames(s -> Map.of(SampleScreen.SEAL_LAYER, sealed(s) ? SampleScreen.FRAME_SEALED : SampleScreen.FRAME_UNSEALED))
            .lockedWhen(SampleGui::sealed)
            .onClose(SampleGui::giveBack)
            .build();
    }

    static boolean sealed(GuiSession s) {
        return s.get(SEALED, Boolean.FALSE);
    }

    private static void giveBack(GuiSession session, Player player) {
        for (int slot : SampleScreen.SLOTS.fillOrder()) {
            ItemStack stack = session.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            session.getInventory().setItem(slot, null);
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), rest);
            }
        }
    }
}
