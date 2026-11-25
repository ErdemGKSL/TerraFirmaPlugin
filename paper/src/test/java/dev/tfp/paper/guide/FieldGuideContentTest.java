/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.guide.BookFont;
import dev.tfp.core.guide.BookPaginator;
import dev.tfp.core.guide.FieldGuide;
import dev.tfp.core.guide.GuideBooks;
import dev.tfp.core.guide.GuideLink;
import dev.tfp.core.guide.GuideRun;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PAR-DATA-04 on datagen's real output ({@code content/guide/field_guide.json}): the whole field guide lays out into
 * vanilla books within the client's limits and every link inside it resolves.
 */
class FieldGuideContentTest {
    static FieldGuide guide;
    static GuideBooks books;

    @BeforeAll
    static void load() throws IOException {
        String data = System.getProperty("tfp.contentData");
        Assumptions.assumeTrue(data != null, "no tfp.contentData");
        Path file = Path.of(data).resolveSibling("guide").resolve("field_guide.json");
        Assumptions.assumeTrue(Files.isRegularFile(file), "no generated field guide at " + file);
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            guide = GuideData.read(r);
        }
        books = new GuideBooks(guide, "tfp");
    }

    static List<List<BookPaginator.Page>> allBooks() {
        List<List<BookPaginator.Page>> out = new ArrayList<>();
        out.add(books.index());
        for (FieldGuide.Entry e : guide.entries()) {
            for (int v = 1; v <= books.volumes(e.id()); v++) {
                out.add(books.entry(e.id(), v));
            }
        }
        return out;
    }

    @Test
    void everyCategoryAndEntryOfTheBookIsThere() {
        assertThat(guide.name()).isEqualTo("Field Guide");
        assertThat(guide.categories()).hasSize(7);
        assertThat(guide.entries()).hasSize(81);
        assertThat(guide.entries()).allSatisfy(e -> assertThat(e.pages()).as(e.id()).isNotEmpty());
        assertThat(books.index().size()).isLessThanOrEqualTo(GuideBooks.MAX_PAGES);
    }

    @Test
    void everyBookFitsTheVanillaLimits() {
        for (List<BookPaginator.Page> book : allBooks()) {
            assertThat(book.size()).isBetween(1, GuideBooks.MAX_PAGES);
            for (BookPaginator.Page p : book) {
                assertThat(p.lines().size()).isLessThanOrEqualTo(BookFont.PAGE_LINES);
                for (List<GuideRun> line : p.lines()) {
                    int w = 0;
                    for (GuideRun r : line) {
                        if (r instanceof GuideRun.Text t) {
                            w += BookFont.width(t.text(), t.style().bold());
                        } else if (r instanceof GuideRun.Keybind k) {
                            w += BookFont.keyWidth(k.key(), k.style().bold());
                        }
                    }
                    assertThat(w).isLessThanOrEqualTo(BookFont.LINE_WIDTH);
                }
            }
        }
    }

    @Test
    void everyLinkResolves() {
        int internal = 0;
        for (List<BookPaginator.Page> book : allBooks()) {
            for (BookPaginator.Page p : book) {
                for (List<GuideRun> line : p.lines()) {
                    for (GuideRun r : line) {
                        String link = r instanceof GuideRun.Text t ? t.style().link() : null;
                        if (link == null || link.startsWith(GuideLink.URL)) {
                            continue;
                        }
                        if (link.startsWith(GuideLink.PAGE)) {
                            int page = Integer.parseInt(link.substring(GuideLink.PAGE.length()));
                            assertThat(page).isBetween(1, book.size());
                            continue;
                        }
                        assertThat(books.book(link)).as(link).isNotNull();
                        internal++;
                    }
                }
            }
        }
        assertThat(internal).isGreaterThan(500);
    }

    @Test
    void everyBookRendersToAdventure() {
        List<String> targets = new ArrayList<>();
        GuideBookRenderer renderer = new GuideBookRenderer(guide, target -> {
            targets.add(target);
            return ClickEvent.runCommand("/noop");
        });
        for (List<BookPaginator.Page> pages : allBooks()) {
            Book book = renderer.book(pages);
            assertThat(book.pages()).hasSize(pages.size());
        }
        assertThat(targets).contains(GuideLink.INDEX, "entry:tfp:getting_started/firepit");
    }
}
