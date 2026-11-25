/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BookPaginatorTest {
    private static List<GuideRun> text(String s) {
        return PatchouliMarkup.parse(s, GuideStyle.PLAIN, "tfp");
    }

    static String lineText(List<GuideRun> line) {
        return PatchouliMarkup.plain(line);
    }

    static int width(List<GuideRun> line) {
        int w = 0;
        for (GuideRun r : line) {
            if (r instanceof GuideRun.Text t) {
                w += BookFont.width(t.text(), t.style().bold());
            } else if (r instanceof GuideRun.Keybind k) {
                w += BookFont.keyWidth(k.key(), k.style().bold());
            }
        }
        return w;
    }

    @Test
    void vanillaFontWidths() {
        assertThat(BookFont.width("i", false)).isEqualTo(2);
        assertThat(BookFont.width("Fire", false)).isEqualTo(6 + 2 + 6 + 6);
        assertThat(BookFont.width("Fire", true)).isEqualTo(24);
        assertThat(BookFont.width(" ", false)).isEqualTo(4);
        assertThat(BookFont.width("-X", false)).isEqualTo(12);
        assertThat(BookFont.width("■", false)).isEqualTo(BookFont.WIDE);
    }

    @Test
    void wrapsAtWordsWithinTheLineWidth() {
        String s = "Fire is an important technological advancement. In order to create fire, you will need a Firestarter.";
        List<BookPaginator.Page> pages = new BookPaginator().paginate(text(s));
        assertThat(pages).hasSize(1);
        for (List<GuideRun> line : pages.getFirst().lines()) {
            assertThat(width(line)).isLessThanOrEqualTo(BookFont.LINE_WIDTH);
            assertThat(lineText(line)).doesNotStartWith(" ").doesNotEndWith(" ");
        }
        assertThat(String.join(" ", pages.getFirst().lines().stream().map(BookPaginatorTest::lineText).toList()))
            .isEqualTo(s);
    }

    @Test
    void fillsFourteenLinesThenStartsAPage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("line").append(i).append("$(br)");
        }
        List<BookPaginator.Page> pages = new BookPaginator().paginate(text(sb.toString()));
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).lines()).hasSize(BookFont.PAGE_LINES);
        assertThat(lineText(pages.get(1).lines().getFirst())).isEqualTo("line14");
    }

    @Test
    void blankLinesAtTheTopOfAPageAreDropped() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append("x$(br)");
        }
        sb.append("$(br2)next");
        List<BookPaginator.Page> pages = new BookPaginator().paginate(text(sb.toString()));
        assertThat(pages).hasSize(2);
        assertThat(lineText(pages.get(1).lines().getFirst())).isEqualTo("next");
    }

    @Test
    void longWordsSplitBetweenCharactersAndStylesMerge() {
        String word = "a".repeat(40); // 240 px
        List<BookPaginator.Page> pages = new BookPaginator().paginate(text(word));
        List<List<GuideRun>> lines = pages.getFirst().lines();
        assertThat(lines).hasSize(3);
        assertThat(lineText(lines.get(0))).hasSize(18);
        assertThat(lines.get(0)).hasSize(1);
    }

    @Test
    void pageBreakStartsANewPageButNotAnEmptyOne() {
        List<GuideRun> runs = new java.util.ArrayList<>(text("a"));
        runs.add(GuideRun.PAGE_BREAK);
        runs.add(GuideRun.PAGE_BREAK);
        runs.addAll(text("b"));
        assertThat(new BookPaginator().paginate(runs)).hasSize(2);
        assertThat(new BookPaginator().paginate(List.of())).hasSize(1);
    }

    @Test
    void keybindsCountTheirLabelWidth() {
        List<BookPaginator.Page> pages = new BookPaginator().paginate(text("aaaaaaaa $(k:key.use) b"));
        // 8 a (48) + space + key.use (70) does not fit 112, so the key moves to the second line
        assertThat(pages.getFirst().lines()).hasSize(2);
        assertThat(pages.getFirst().lines().get(1).getFirst()).isInstanceOf(GuideRun.Keybind.class);
    }
}
