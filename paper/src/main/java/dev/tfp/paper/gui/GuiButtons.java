/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.core.gui.GuiImages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-INFRA-03 {@code fw-buttons}: the item a button slot holds. Its item model is {@code tfp:gui/blank} (an empty model
 * the datagen ships), so the client draws nothing and the button art under the slot (background or overlay glyph)
 * shows; the item's name is the button's tooltip. Without CraftEngine (no pack) the model is left alone so the button
 * is a visible paper.
 *
 * <p>Every icon carries {@link #MARKER} in its PDC, so a button that escaped a screen (a client desync, a crash between
 * the click and the cancel) can be recognised and removed.
 */
public final class GuiButtons {
    public static final NamespacedKey MARKER = new NamespacedKey("tfp", "gui_button");
    public static final NamespacedKey BLANK = NamespacedKey.fromString(GuiImages.BLANK_MODEL);

    private GuiButtons() {
    }

    /** A button icon with {@code tooltip} as its name. */
    public static ItemStack icon(boolean blankModel, String tooltip) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        style(meta, blankModel, tooltip);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Makes {@code meta} a button's: name, blank model (when {@code blankModel}), stack size 1, the marker. */
    public static void style(ItemMeta meta, boolean blankModel, String tooltip) {
        meta.customName(Component.text(tooltip).decoration(TextDecoration.ITALIC, false));
        if (blankModel) {
            meta.setItemModel(BLANK);
        }
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
    }

    /** True for a button icon. */
    public static boolean isButton(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasItemMeta()
            && stack.getItemMeta().getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }
}
