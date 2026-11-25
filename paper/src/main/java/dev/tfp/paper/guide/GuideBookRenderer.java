/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import dev.tfp.core.guide.BookPaginator;
import dev.tfp.core.guide.FieldGuide;
import dev.tfp.core.guide.GuideLink;
import dev.tfp.core.guide.GuideRun;
import dev.tfp.core.guide.GuideStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Turns laid-out field guide pages ({@link BookPaginator.Page}) into an Adventure {@link Book} (PAR-DATA-04). Each line
 * of a page ends with a newline, so the client draws the layout as it is. Links: {@code page:} is the book's own page
 * turn, {@code url:} opens the page (the client asks first), and the contents and entry targets use the click event
 * {@code internal} gives for the target (the plugin's click callback; a test's stand-in). Thread-safe.
 */
public final class GuideBookRenderer {
    private final Function<String, ClickEvent> internal;
    private final Map<String, FieldGuide.Entry> entries;
    private final String title;

    public GuideBookRenderer(FieldGuide guide, Function<String, ClickEvent> internal) {
        this.internal = internal;
        this.entries = guide.byId();
        this.title = guide.name();
    }

    public Book book(List<BookPaginator.Page> pages) {
        List<Component> out = new ArrayList<>(pages.size());
        for (BookPaginator.Page p : pages) {
            out.add(page(p));
        }
        return Book.book(Component.text(title), Component.empty(), out);
    }

    Component page(BookPaginator.Page page) {
        TextComponent.Builder b = Component.text();
        List<List<GuideRun>> lines = page.lines();
        for (int i = 0; i < lines.size(); i++) {
            for (GuideRun r : lines.get(i)) {
                switch (r) {
                    case GuideRun.Text t -> b.append(Component.text(t.text(), style(t.style())));
                    case GuideRun.Keybind k -> b.append(Component.keybind(k.key(), style(k.style())));
                    case GuideRun.Break br -> b.append(Component.newline());
                    case GuideRun.PageBreak pb -> { }
                }
            }
            if (i < lines.size() - 1) {
                b.append(Component.newline());
            }
        }
        return b.build();
    }

    Style style(GuideStyle s) {
        Style.Builder b = Style.style()
            .color(s.color() == GuideStyle.NO_COLOR ? null : TextColor.color(s.color()))
            .decoration(TextDecoration.BOLD, s.bold())
            .decoration(TextDecoration.ITALIC, s.italic())
            .decoration(TextDecoration.UNDERLINED, s.underlined())
            .decoration(TextDecoration.STRIKETHROUGH, s.strikethrough())
            .decoration(TextDecoration.OBFUSCATED, s.obfuscated());
        String hover = s.tooltip();
        if (s.link() != null) {
            String link = s.link();
            if (link.startsWith(GuideLink.PAGE)) {
                b.clickEvent(ClickEvent.changePage(Integer.parseInt(link.substring(GuideLink.PAGE.length()))));
            } else if (link.startsWith(GuideLink.URL)) {
                String url = link.substring(GuideLink.URL.length());
                b.clickEvent(ClickEvent.openUrl(url));
                hover = hover != null ? hover : url;
            } else {
                b.clickEvent(internal.apply(link));
                String id = GuideLink.entryId(link);
                FieldGuide.Entry e = id == null ? null : entries.get(id);
                if (hover == null && e != null) {
                    hover = e.title();
                }
            }
        }
        if (hover != null) {
            b.hoverEvent(HoverEvent.showText(Component.text(hover)));
        }
        return b.build();
    }
}
