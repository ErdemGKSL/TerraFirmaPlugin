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
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-DATA-21: TFC's nine other book locales. The real output ({@code content/guide/field_guide_<locale>.json}) lays out
 * within the client's limits in every locale (CJK and Cyrillic count the wide glyph width, so no line can wrap again), and
 * the service gives each player the book of their client language, English where there is none.
 */
class FieldGuideLocaleTest extends MockServerTest {
    private static FieldGuide read(String locale) throws IOException {
        String data = System.getProperty("tfp.contentData");
        Assumptions.assumeTrue(data != null, "no tfp.contentData");
        Path file = Path.of(data).resolveSibling("guide").resolve(locale.equals("en_us") ? "field_guide.json"
            : "field_guide_" + locale + ".json");
        Assumptions.assumeTrue(Files.isRegularFile(file), "no generated field guide at " + file);
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GuideData.read(r);
        }
    }

    @Test
    void everyLocaleHasTheEnglishEntriesAndFitsTheVanillaLimits() throws IOException {
        FieldGuide en = read("en_us");
        for (String locale : GuideData.LOCALES) {
            FieldGuide g = read(locale).withFallback(en);
            assertThat(g.entries()).as(locale).extracting(FieldGuide.Entry::id)
                .containsExactlyElementsOf(en.entries().stream().map(FieldGuide.Entry::id).toList());
            assertThat(g.name()).as(locale + " name").isNotBlank();
            GuideBooks books = new GuideBooks(g, "tfp");
            List<List<BookPaginator.Page>> all = new ArrayList<>();
            all.add(books.index());
            for (FieldGuide.Entry e : g.entries()) {
                for (int v = 1; v <= books.volumes(e.id()); v++) {
                    all.add(books.entry(e.id(), v));
                }
            }
            for (List<BookPaginator.Page> book : all) {
                assertThat(book.size()).as(locale).isBetween(1, GuideBooks.MAX_PAGES);
                for (BookPaginator.Page p : book) {
                    assertThat(p.lines().size()).as(locale).isLessThanOrEqualTo(BookFont.PAGE_LINES);
                    for (List<GuideRun> line : p.lines()) {
                        int w = 0;
                        for (GuideRun r : line) {
                            if (r instanceof GuideRun.Text t) {
                                w += BookFont.width(t.text(), t.style().bold());
                            } else if (r instanceof GuideRun.Keybind k) {
                                w += BookFont.keyWidth(k.key(), k.style().bold());
                            }
                        }
                        assertThat(w).as(locale).isLessThanOrEqualTo(BookFont.LINE_WIDTH);
                    }
                }
            }
        }
    }

    private static final String EN = """
        {"book": {"name": "Field Guide", "landing_text": "Welcome"},
         "categories": [{"id": "tfp:a", "name": "Start", "description": "Here."}],
         "entries": [
          {"id": "tfp:a/one", "category": "tfp:a", "title": "One", "content": [{"text": "one text"}]},
          {"id": "tfp:a/two", "category": "tfp:a", "title": "Two", "content": [{"text": "two text"}]}]}
        """;
    private static final String ZH = """
        {"book": {"name": "\\u91ce\\u5916\\u6307\\u5357", "landing_text": "\\u6b22\\u8fce"},
         "categories": [{"id": "tfp:a", "name": "\\u5f00\\u59cb", "description": "\\u8fd9\\u91cc"}],
         "entries": [
          {"id": "tfp:a/one", "category": "tfp:a", "title": "\\u4e00", "content": [{"text": "\\u4e00\\u6587"}]}]}
        """;

    private static FieldGuideService.Variant variant(FieldGuide g) {
        return new FieldGuideService.Variant(new GuideBooks(g, "tfp"),
            new GuideBookRenderer(g, t -> ClickEvent.runCommand("/x")));
    }

    private static String flatten(Book book) {
        StringBuilder sb = new StringBuilder();
        for (var page : book.pages()) {
            append(page, sb);
        }
        return sb.toString();
    }

    private static void append(net.kyori.adventure.text.Component c, StringBuilder sb) {
        if (c instanceof TextComponent t) {
            sb.append(t.content());
        }
        c.children().forEach(child -> append(child, sb));
    }

    @Test
    void aPlayerGetsTheBookOfTheirClientLanguageAndMissingEntriesFallBackToEnglish() throws Exception {
        FieldGuide en = GuideData.read(new StringReader(EN));
        FieldGuide zh = GuideData.read(new StringReader(ZH)).withFallback(en);
        List<Runnable> queued = new ArrayList<>();
        FieldGuideService service = new FieldGuideService(variant(en), Map.of("zh_cn", variant(zh)),
            (p, task) -> queued.add(task), new AtomicLong()::get);

        assertThat(service.localeKey(Locale.of("zh", "CN"))).isEqualTo("zh_cn");
        assertThat(service.localeKey(Locale.of("zh", "TW"))).isEqualTo("zh_cn"); // no zh_tw book here: same language
        assertThat(service.localeKey(Locale.of("de", "DE"))).isEqualTo("en_us");
        assertThat(service.localeKey(Locale.US)).isEqualTo("en_us");
        assertThat(service.localeKey(null)).isEqualTo("en_us");

        assertThat(flatten(service.book("zh_cn", GuideLink.INDEX))).contains("野外指南").doesNotContain("Welcome");
        assertThat(flatten(service.book("en_us", GuideLink.INDEX))).contains("Welcome");
        assertThat(flatten(service.book("zh_cn", "entry:tfp:a/one"))).contains("一文");
        // the entry the translation lacks is shown in English, inside the Chinese book
        assertThat(flatten(service.book("zh_cn", "entry:tfp:a/two"))).contains("two text");
        assertThat(service.book("zh_cn", "entry:tfp:nope")).isNull();
        assertThat(service.book("xx_xx", GuideLink.INDEX)).isSameAs(service.book("en_us", GuideLink.INDEX));

        PlayerMock chinese = server.addPlayer("Zh");
        chinese.setLocale(Locale.of("zh", "CN"));
        assertThat(service.open(chinese, "entry:tfp:a/one")).isTrue();
        PlayerMock english = server.addPlayer("En");
        assertThat(service.open(english, "entry:tfp:a/one")).isTrue();
        assertThat(queued).hasSize(2);
    }
}
