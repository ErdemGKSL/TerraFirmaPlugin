/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays styled field guide text out into vanilla written book pages (PAR-DATA-04): word wrap at
 * {@link BookFont#LINE_WIDTH}, {@link BookFont#PAGE_LINES} lines per page, explicit line and page breaks. Every line is
 * ended by the layout, so the client never wraps it again. Blank lines at the top of a page and at its end are dropped.
 * Pure and thread-safe.
 */
public final class BookPaginator {
    /** One page: its lines, each a list of {@link GuideRun.Text} / {@link GuideRun.Keybind} runs. */
    public record Page(List<List<GuideRun>> lines) {
        public Page {
            lines = lines.stream().map(List::copyOf).toList();
        }
    }

    private final int width;
    private final int lines;

    public BookPaginator() {
        this(BookFont.LINE_WIDTH, BookFont.PAGE_LINES);
    }

    public BookPaginator(int width, int lines) {
        if (width < 8 || lines < 1) {
            throw new IllegalArgumentException("page too small");
        }
        this.width = width;
        this.lines = lines;
    }

    /** A piece of a word: styled text or a key name. */
    private record Piece(GuideRun run, int width) {
    }

    /** The pages of {@code runs}. Never empty: no text gives one empty page. */
    public List<Page> paginate(List<GuideRun> runs) {
        Layout l = new Layout();
        List<Piece> word = new ArrayList<>();
        GuideStyle spaceStyle = null;
        for (GuideRun r : runs) {
            switch (r) {
                case GuideRun.Text t -> {
                    String s = t.text();
                    int start = 0;
                    for (int i = 0; i <= s.length(); i++) {
                        boolean end = i == s.length();
                        if (end || s.charAt(i) == ' ') {
                            if (i > start) {
                                String part = s.substring(start, i);
                                word.add(new Piece(new GuideRun.Text(part, t.style()), BookFont.width(part, t.style().bold())));
                            }
                            if (!end) {
                                l.word(word, spaceStyle);
                                word = new ArrayList<>();
                                spaceStyle = t.style();
                            }
                            start = i + 1;
                        }
                    }
                }
                case GuideRun.Keybind k -> word.add(new Piece(k, BookFont.keyWidth(k.key(), k.style().bold())));
                case GuideRun.Break b -> {
                    l.word(word, spaceStyle);
                    word = new ArrayList<>();
                    spaceStyle = null;
                    l.newLine(true);
                }
                case GuideRun.PageBreak p -> {
                    l.word(word, spaceStyle);
                    word = new ArrayList<>();
                    spaceStyle = null;
                    l.newPage();
                }
            }
        }
        l.word(word, spaceStyle);
        return l.finish();
    }

    private final class Layout {
        private final List<Page> pages = new ArrayList<>();
        private List<List<GuideRun>> page = new ArrayList<>();
        private List<GuideRun> line = new ArrayList<>();
        private int lineWidth;

        /** Places a word, after a space in {@code spaceStyle} when that is non-null and the line is not empty. */
        void word(List<Piece> word, GuideStyle spaceStyle) {
            if (word.isEmpty()) {
                return;
            }
            int w = word.stream().mapToInt(Piece::width).sum();
            int space = spaceStyle == null || line.isEmpty() ? 0 : BookFont.width(" ", spaceStyle.bold());
            if (lineWidth + space + w <= width) {
                if (space > 0) {
                    append(new GuideRun.Text(" ", spaceStyle), space);
                }
                word.forEach(p -> append(p.run(), p.width()));
                return;
            }
            if (!line.isEmpty()) {
                newLine(false);
            }
            for (Piece p : word) {
                if (lineWidth + p.width() <= width) {
                    append(p.run(), p.width());
                } else if (p.run() instanceof GuideRun.Text t) {
                    hardSplit(t);
                } else {
                    if (!line.isEmpty()) {
                        newLine(false);
                    }
                    append(p.run(), p.width());
                }
            }
        }

        /** A word wider than the line: breaks it between characters. */
        private void hardSplit(GuideRun.Text t) {
            StringBuilder sb = new StringBuilder();
            int sbWidth = 0;
            String s = t.text();
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                int cw = BookFont.width(cp, t.style().bold());
                if (lineWidth + sbWidth + cw > width && (sbWidth > 0 || !line.isEmpty())) {
                    if (sbWidth > 0) {
                        append(new GuideRun.Text(sb.toString(), t.style()), sbWidth);
                    }
                    sb.setLength(0);
                    sbWidth = 0;
                    newLine(false);
                }
                sb.appendCodePoint(cp);
                sbWidth += cw;
                i += Character.charCount(cp);
            }
            if (sbWidth > 0) {
                append(new GuideRun.Text(sb.toString(), t.style()), sbWidth);
            }
        }

        private void append(GuideRun run, int w) {
            if (run instanceof GuideRun.Text t && !line.isEmpty() && line.getLast() instanceof GuideRun.Text last
                && last.style().equals(t.style())) {
                line.set(line.size() - 1, new GuideRun.Text(last.text() + t.text(), t.style()));
            } else {
                line.add(run);
            }
            lineWidth += w;
        }

        /** Ends the line; an explicit break keeps an empty line (a blank line) unless it is at the top of a page. */
        void newLine(boolean explicit) {
            if (line.isEmpty() && (!explicit || page.isEmpty())) {
                return;
            }
            page.add(line);
            line = new ArrayList<>();
            lineWidth = 0;
            if (page.size() >= lines) {
                closePage();
            }
        }

        void newPage() {
            if (!line.isEmpty()) {
                newLine(false);
            }
            if (!page.isEmpty()) {
                closePage();
            }
        }

        private void closePage() {
            while (!page.isEmpty() && page.getLast().isEmpty()) {
                page.removeLast();
            }
            if (!page.isEmpty()) {
                pages.add(new Page(page));
            }
            page = new ArrayList<>();
        }

        List<Page> finish() {
            newPage();
            if (pages.isEmpty()) {
                pages.add(new Page(List.of()));
            }
            return List.copyOf(pages);
        }
    }
}
