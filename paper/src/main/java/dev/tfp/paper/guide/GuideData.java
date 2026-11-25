/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.guide.FieldGuide;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads datagen's {@value #FILE} (PAR-DATA-04) into the core {@link FieldGuide}: the book's English name and landing
 * text, the categories in reading order and each entry's converted pages.
 */
public final class GuideData {
    public static final String FILE = "content/guide/field_guide.json";
    /** TFC's other book locales (PAR-DATA-21), in {@code content/guide/field_guide_<locale>.json}. */
    public static final List<String> LOCALES = List.of("ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn",
        "zh_hk", "zh_tw");

    /** The jar path of the guide of {@code locale} ({@code en_us} is {@link #FILE}). */
    public static String file(String locale) {
        return locale.equals("en_us") ? FILE : "content/guide/field_guide_" + locale + ".json";
    }

    private GuideData() {
    }

    /** Reads the guide from {@code loader} (the plugin's class loader, which holds the content jar). */
    public static FieldGuide load(ClassLoader loader) throws IOException {
        return load(loader, "en_us");
    }

    /** Reads the guide of one book locale. */
    public static FieldGuide load(ClassLoader loader, String locale) throws IOException {
        String file = file(locale);
        try (InputStream in = loader.getResourceAsStream(file)) {
            if (in == null) {
                throw new IOException("missing " + file + " in the plugin jar");
            }
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public static FieldGuide read(Reader reader) throws IOException {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject book = root.getAsJsonObject("book");
            Map<String, List<FieldGuide.Entry>> byCategory = new LinkedHashMap<>();
            Map<String, JsonObject> categories = new LinkedHashMap<>();
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject c = e.getAsJsonObject();
                categories.put(c.get("id").getAsString(), c);
                byCategory.put(c.get("id").getAsString(), new ArrayList<>());
            }
            for (JsonElement e : root.getAsJsonArray("entries")) {
                JsonObject o = e.getAsJsonObject();
                List<FieldGuide.Page> pages = new ArrayList<>();
                if (o.has("content")) {
                    for (JsonElement p : o.getAsJsonArray("content")) {
                        JsonObject po = p.getAsJsonObject();
                        pages.add(new FieldGuide.Page(po.has("title") ? po.get("title").getAsString() : null,
                            po.get("text").getAsString()));
                    }
                }
                String category = o.get("category").getAsString();
                List<FieldGuide.Entry> list = byCategory.get(category);
                if (list == null) {
                    throw new IOException("field guide entry " + o.get("id") + " has an unknown category " + category);
                }
                list.add(new FieldGuide.Entry(o.get("id").getAsString(), category, o.get("title").getAsString(), pages));
            }
            List<FieldGuide.Category> cats = new ArrayList<>();
            for (Map.Entry<String, JsonObject> c : categories.entrySet()) {
                cats.add(new FieldGuide.Category(c.getKey(), c.getValue().get("name").getAsString(),
                    c.getValue().get("description").getAsString(), byCategory.get(c.getKey())));
            }
            return new FieldGuide(book.get("name").getAsString(), book.get("landing_text").getAsString(), cats);
        } catch (RuntimeException e) {
            throw new IOException("malformed field guide data: " + e, e);
        }
    }
}
