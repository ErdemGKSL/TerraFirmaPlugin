/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.TitleGlyphs;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * PAR-SURV-07: a page's container title. With CraftEngine: the background and overlay glyph tags (white, so the title
 * colour does not darken the art), then every text piece as a shift tag and the text in its line font
 * ({@link TabFont}), then a shift back and the page name at the vanilla title position. CraftEngine replaces the tags in
 * place and keeps the other components, fonts included. Without CraftEngine (no pack, no fonts) the title is the page name.
 */
public final class TabTitles {
    private TabTitles() {
    }

    public static Component title(boolean glyphs, TabPage page) {
        if (!glyphs) {
            return Component.text(page.label());
        }
        TitleGlyphs g = TabLayout.layers(page.page()).compose(page.frames());
        TextComponent.Builder b = Component.text().append(Component.text(g.tags(), NamedTextColor.WHITE));
        int pen = ChestGeometry.TITLE_X; // compose ends back at the label position
        for (TabPage.Text t : page.lines()) {
            if (t.text().isEmpty()) {
                continue;
            }
            b.append(Component.text(shift(t.x() - pen)));
            Component text = Component.text(t.text()).font(Key.key(TabFont.font(t.y())));
            if (t.color() != TabPage.DEFAULT_COLOR) {
                text = text.color(TextColor.color(t.color()));
            }
            b.append(text);
            pen = t.x() + TabFont.width(t.text());
        }
        String back = shift(ChestGeometry.TITLE_X - pen);
        if (!back.isEmpty()) {
            b.append(Component.text(back));
        }
        return b.append(Component.text(page.label())).build();
    }

    /** CraftEngine shift tags for {@code px} (none for 0). */
    static String shift(int px) {
        return px == 0 ? "" : new TitleGlyphs(List.of(new TitleGlyphs.Shift(px)), 0).tags();
    }
}
