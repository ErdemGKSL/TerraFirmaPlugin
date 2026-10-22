/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.core.gui.TitleGlyphs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * PAR-INFRA-03 {@code fw-title-glyphs}: a container title from the glyph tags and a text label. The tags are plain text
 * that CraftEngine's container packet interception turns into glyphs; they are white, because the title colour
 * (0x404040) multiplies glyph pixels and would darken the art to a quarter. The label follows in the default title
 * colour, back at the vanilla label position. Without CraftEngine there is no pack and no interception, so the title is
 * the label alone.
 */
public final class GuiTitles {
    private GuiTitles() {
    }

    /** The title component (used when opening the screen). */
    public static Component title(boolean glyphs, TitleGlyphs g, String label) {
        if (!glyphs) {
            return Component.text(label);
        }
        return Component.text()
            .append(Component.text(g.tags(), NamedTextColor.WHITE))
            .append(Component.text(label))
            .build();
    }

    /**
     * The same title in the legacy section form {@code InventoryView#setTitle(String)} takes: {@code §f} for the tags,
     * {@code §r} to return to the default title colour for the label.
     */
    public static String legacy(boolean glyphs, TitleGlyphs g, String label) {
        if (!glyphs) {
            return label;
        }
        return "§f" + g.tags() + "§r" + label;
    }
}
