/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The field guide as vanilla written books (PAR-DATA-04, the form the owner chose): one contents book and one book per
 * entry, every page laid out by {@link BookPaginator}. Immutable and thread-safe; everything is laid out once, in the
 * constructor.
 *
 * <p>The contents book: the book's name and landing text and a list of the categories (each a link to its page), then one
 * page run per category with its description and its entries as links. An entry book: a link back to the contents, the
 * entry's title, then every converted page (its title in bold) separated by a blank line. A book holds at most
 * {@link #MAX_PAGES} pages, the vanilla limit; a longer entry is split into volumes joined by "continued" links.
 */
public final class GuideBooks {
    /** Vanilla's written book page limit. */
    public static final int MAX_PAGES = 100;
    public static final int MUTED = 0x555555;
    public static final String BACK = "« Contents";
    public static final String CONTINUED = "Continued »";
    public static final String PREVIOUS = "« Previous part";

    private final FieldGuide guide;
    private final String namespace;
    private final BookPaginator paginator;
    private final List<BookPaginator.Page> index;
    private final Map<String, List<List<BookPaginator.Page>>> volumes = new HashMap<>();

    public GuideBooks(FieldGuide guide, String namespace) {
        this(guide, namespace, new BookPaginator(), MAX_PAGES);
    }

    GuideBooks(FieldGuide guide, String namespace, BookPaginator paginator, int maxPages) {
        this.guide = guide;
        this.namespace = namespace;
        this.paginator = paginator;
        this.index = layoutIndex();
        if (index.size() > maxPages) {
            throw new IllegalStateException("the contents book has " + index.size() + " pages, over " + maxPages);
        }
        for (FieldGuide.Entry e : guide.entries()) {
            volumes.put(e.id(), split(e.id(), paginator.paginate(entryRuns(e)), maxPages));
        }
    }

    public FieldGuide guide() {
        return guide;
    }

    /** The contents book. */
    public List<BookPaginator.Page> index() {
        return index;
    }

    /** The number of volumes of an entry, or 0 for an unknown id. */
    public int volumes(String entryId) {
        List<List<BookPaginator.Page>> v = volumes.get(entryId);
        return v == null ? 0 : v.size();
    }

    /** Volume {@code volume} (1-based, clamped) of an entry's book, or null for an unknown id. */
    public List<BookPaginator.Page> entry(String entryId, int volume) {
        List<List<BookPaginator.Page>> v = volumes.get(entryId);
        if (v == null) {
            return null;
        }
        return v.get(Math.max(0, Math.min(v.size(), volume) - 1));
    }

    /** The book of a link target ({@link GuideLink#INDEX} or an {@code entry:} target), or null. */
    public List<BookPaginator.Page> book(String target) {
        if (GuideLink.INDEX.equals(target)) {
            return index;
        }
        String id = GuideLink.entryId(target);
        return id == null ? null : entry(id, GuideLink.volume(target));
    }

    private List<BookPaginator.Page> layoutIndex() {
        List<List<BookPaginator.Page>> sections = new ArrayList<>();
        for (FieldGuide.Category c : guide.categories()) {
            List<GuideRun> runs = new ArrayList<>();
            runs.add(new GuideRun.Text(c.name(), GuideStyle.PLAIN.withBold(true)));
            runs.add(GuideRun.BREAK);
            runs.addAll(PatchouliMarkup.parse(c.description(), GuideStyle.PLAIN.withColor(MUTED), namespace));
            runs.add(GuideRun.BREAK);
            runs.add(GuideRun.BREAK);
            for (FieldGuide.Entry e : c.entries()) {
                runs.add(new GuideRun.Text(e.title(), GuideStyle.PLAIN.withColor(PatchouliMarkup.LINK_COLOR)
                    .withLink(GuideLink.entry(e.id()))));
                runs.add(GuideRun.BREAK);
            }
            sections.add(paginator.paginate(runs));
        }
        int frontPages = paginator.paginate(front(new int[sections.size()])).size();
        int[] start = new int[sections.size()];
        int page = frontPages + 1;
        for (int i = 0; i < sections.size(); i++) {
            start[i] = page;
            page += sections.get(i).size();
        }
        List<BookPaginator.Page> pages = new ArrayList<>(paginator.paginate(front(start)));
        sections.forEach(pages::addAll);
        return List.copyOf(pages);
    }

    private List<GuideRun> front(int[] categoryPages) {
        List<GuideRun> runs = new ArrayList<>();
        runs.add(new GuideRun.Text(guide.name(), GuideStyle.PLAIN.withBold(true)));
        runs.add(GuideRun.BREAK);
        runs.add(GuideRun.BREAK);
        runs.addAll(PatchouliMarkup.parse(guide.landingText(), GuideStyle.PLAIN, namespace));
        runs.add(GuideRun.BREAK);
        runs.add(GuideRun.BREAK);
        for (int i = 0; i < guide.categories().size(); i++) {
            runs.add(new GuideRun.Text(guide.categories().get(i).name(), GuideStyle.PLAIN
                .withColor(PatchouliMarkup.LINK_COLOR).withLink(GuideLink.page(categoryPages[i]))));
            runs.add(GuideRun.BREAK);
        }
        return runs;
    }

    private List<GuideRun> entryRuns(FieldGuide.Entry e) {
        List<GuideRun> runs = new ArrayList<>();
        runs.add(new GuideRun.Text(BACK, GuideStyle.PLAIN.withColor(PatchouliMarkup.LINK_COLOR).withLink(GuideLink.INDEX)));
        runs.add(GuideRun.BREAK);
        runs.add(GuideRun.BREAK);
        runs.add(new GuideRun.Text(e.title(), GuideStyle.PLAIN.withBold(true)));
        runs.add(GuideRun.BREAK);
        for (FieldGuide.Page p : e.pages()) {
            runs.add(GuideRun.BREAK);
            if (p.title() != null && !p.title().isBlank()) {
                runs.add(new GuideRun.Text(p.title(), GuideStyle.PLAIN.withBold(true)));
                runs.add(GuideRun.BREAK);
            }
            runs.addAll(PatchouliMarkup.parse(p.text(), GuideStyle.PLAIN, namespace));
            runs.add(GuideRun.BREAK);
        }
        return runs;
    }

    /** Splits an entry's pages into volumes of at most {@code maxPages}, joined by navigation pages. */
    private List<List<BookPaginator.Page>> split(String id, List<BookPaginator.Page> pages, int maxPages) {
        if (pages.size() <= maxPages) {
            return List.of(pages);
        }
        int perVolume = maxPages - 2;
        List<List<BookPaginator.Page>> out = new ArrayList<>();
        int count = (pages.size() + perVolume - 1) / perVolume;
        for (int v = 0; v < count; v++) {
            List<BookPaginator.Page> vol = new ArrayList<>();
            if (v > 0) {
                vol.add(nav(PREVIOUS, GuideLink.entry(id, v)));
            }
            vol.addAll(pages.subList(v * perVolume, Math.min(pages.size(), (v + 1) * perVolume)));
            if (v < count - 1) {
                vol.add(nav(CONTINUED, GuideLink.entry(id, v + 2)));
            }
            out.add(List.copyOf(vol));
        }
        return List.copyOf(out);
    }

    private static BookPaginator.Page nav(String text, String target) {
        return new BookPaginator.Page(List.of(List.of(new GuideRun.Text(text,
            GuideStyle.PLAIN.withColor(PatchouliMarkup.LINK_COLOR).withLink(target)))));
    }
}
