/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/patchouli_books/field_guide/en_us/ @ c3b4d9536 (the book's structure)
 */
package dev.tfp.core.guide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TFC's field guide as data (PAR-DATA-04): the book's name and landing text, its categories in reading order, each with
 * its entries in reading order, and each entry's converted pages (Patchouli markup, recipes already written out as text
 * by datagen). Immutable.
 */
public record FieldGuide(String name, String landingText, List<Category> categories) {
    public FieldGuide {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(landingText, "landingText");
        categories = List.copyOf(categories);
    }

    /** A category: {@code id} is {@code tfp:<name>}. */
    public record Category(String id, String name, String description, List<Entry> entries) {
        public Category {
            entries = List.copyOf(entries);
        }
    }

    /** An entry: {@code id} is {@code tfp:<category>/<name>}. */
    public record Entry(String id, String category, String title, List<Page> pages) {
        public Entry {
            pages = List.copyOf(pages);
        }
    }

    /** One converted Patchouli page: an optional title (plain text, or null) and its Patchouli markup text. */
    public record Page(String title, String text) {
        public Page {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * This (translated) guide with {@code base}'s structure: the categories and entries of {@code base} in its reading
     * order, each taken from this guide when it has it and from {@code base} otherwise, so a locale missing an entry still
     * shows it, in the base language (PAR-DATA-21).
     */
    public FieldGuide withFallback(FieldGuide base) {
        Map<String, Category> mine = new HashMap<>();
        categories.forEach(c -> mine.put(c.id(), c));
        Map<String, Entry> myEntries = byId();
        List<Category> out = new java.util.ArrayList<>();
        for (Category b : base.categories()) {
            Category c = mine.get(b.id());
            List<Entry> entries = new java.util.ArrayList<>();
            for (Entry e : b.entries()) {
                entries.add(myEntries.getOrDefault(e.id(), e));
            }
            out.add(new Category(b.id(), c != null ? c.name() : b.name(), c != null ? c.description() : b.description(),
                entries));
        }
        return new FieldGuide(name, landingText, out);
    }

    /** Every entry in reading order. */
    public List<Entry> entries() {
        return categories.stream().flatMap(c -> c.entries().stream()).toList();
    }

    /** Entries by id. */
    public Map<String, Entry> byId() {
        Map<String, Entry> map = new HashMap<>();
        for (Entry e : entries()) {
            map.put(e.id(), e);
        }
        return Map.copyOf(map);
    }
}
