/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuideBooksTest {
    static FieldGuide guide(int longPages) {
        List<FieldGuide.Page> many = new ArrayList<>();
        for (int i = 0; i < longPages; i++) {
            many.add(new FieldGuide.Page("Page " + i, "Some text. ".repeat(30)));
        }
        return new FieldGuide("Field Guide", "Welcome traveller!", List.of(
            new FieldGuide.Category("tfp:getting_started", "Getting Started", "Start $(thing)here$().", List.of(
                new FieldGuide.Entry("tfp:getting_started/introduction", "tfp:getting_started", "Introduction",
                    List.of(new FieldGuide.Page(null, "See the $(l:getting_started/firepit)firepit$()."))),
                new FieldGuide.Entry("tfp:getting_started/firepit", "tfp:getting_started", "Pits of Fire",
                    List.of(new FieldGuide.Page("Firepit", "Make one."))))),
            new FieldGuide.Category("tfp:mechanics", "Miscellaneous", "Everything else.", List.of(
                new FieldGuide.Entry("tfp:mechanics/long", "tfp:mechanics", "Long", many)))));
    }

    static List<GuideRun> all(List<BookPaginator.Page> pages) {
        List<GuideRun> out = new ArrayList<>();
        pages.forEach(p -> p.lines().forEach(out::addAll));
        return out;
    }

    static List<String> links(List<BookPaginator.Page> pages) {
        List<String> out = new ArrayList<>();
        for (GuideRun r : all(pages)) {
            if (r instanceof GuideRun.Text t && t.style().link() != null) {
                out.add(t.style().link());
            }
        }
        return out;
    }

    @Test
    void contentsLinksEveryCategoryPageAndEveryEntry() {
        GuideBooks books = new GuideBooks(guide(1), "tfp");
        List<BookPaginator.Page> index = books.index();
        assertThat(index).hasSize(3); // front page, one page per category
        assertThat(links(index)).containsExactly("page:2", "page:3", "entry:tfp:getting_started/introduction",
            "entry:tfp:getting_started/firepit", "entry:tfp:mechanics/long");
        assertThat(BookPaginatorTest.lineText(index.get(1).lines().getFirst())).isEqualTo("Getting Started");
    }

    @Test
    void entryBookStartsWithBackLinkAndTitleAndKeepsLinks() {
        GuideBooks books = new GuideBooks(guide(1), "tfp");
        List<BookPaginator.Page> intro = books.entry("tfp:getting_started/introduction", 1);
        assertThat(links(intro)).containsExactly(GuideLink.INDEX, "entry:tfp:getting_started/firepit");
        assertThat(BookPaginatorTest.lineText(intro.getFirst().lines().get(2))).isEqualTo("Introduction");
        assertThat(books.book("entry:tfp:getting_started/firepit")).isNotNull();
        assertThat(books.book("entry:tfp:nope")).isNull();
        assertThat(books.book(GuideLink.INDEX)).isSameAs(books.index());
    }

    @Test
    void longEntriesSplitIntoVolumesUnderTheLimit() {
        GuideBooks books = new GuideBooks(guide(40), "tfp", new BookPaginator(), 10);
        int volumes = books.volumes("tfp:mechanics/long");
        assertThat(volumes).isGreaterThan(1);
        for (int v = 1; v <= volumes; v++) {
            List<BookPaginator.Page> vol = books.entry("tfp:mechanics/long", v);
            assertThat(vol.size()).isLessThanOrEqualTo(10);
            if (v < volumes) {
                assertThat(links(List.of(vol.getLast()))).containsExactly("entry:tfp:mechanics/long@" + (v + 1));
            }
            if (v > 1) {
                assertThat(links(List.of(vol.getFirst()))).containsExactly(GuideLink.entry("tfp:mechanics/long", v - 1));
            }
        }
        assertThat(books.book("entry:tfp:mechanics/long@2")).isEqualTo(books.entry("tfp:mechanics/long", 2));
    }

    @Test
    void contentsOverTheLimitIsRefused() {
        assertThatThrownBy(() -> new GuideBooks(guide(1), "tfp", new BookPaginator(), 2))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkTargets() {
        assertThat(GuideLink.fromPatchouli("food/decay", "tfp")).isEqualTo("entry:tfp:food/decay");
        assertThat(GuideLink.entryId("entry:tfp:food/decay@3")).isEqualTo("tfp:food/decay");
        assertThat(GuideLink.volume("entry:tfp:food/decay@3")).isEqualTo(3);
        assertThat(GuideLink.volume("entry:tfp:food/decay")).isEqualTo(1);
        assertThat(GuideLink.entryId("index")).isNull();
    }
}
