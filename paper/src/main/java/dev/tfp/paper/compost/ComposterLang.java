/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.compost;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The composter messages of TFC's own language files ({@code tfc.composter.*}), read from the plugin data datagen writes to
 * {@code content/lang/<locale>.json} (TFC's keys verbatim, every TFC locale). Lookup: the exact locale, else
 * {@value #FALLBACK}, else the key. {@link #load} is blocking IO (at enable, off the region threads); {@link #get} never
 * loads and is safe on any thread.
 */
public final class ComposterLang {
    public static final String FALLBACK = "en_us";
    public static final String JAR_DIR = "content/lang/";
    static final String PREFIX = "tfc.composter.";

    private final Function<String, InputStream> opener;
    private final Map<String, Map<String, String>> locales = new ConcurrentHashMap<>();

    /** @param opener opens {@code <locale>.json} by its file name, or returns {@code null} when there is none */
    public ComposterLang(Function<String, InputStream> opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    public static ComposterLang fromJar(ClassLoader loader) {
        return new ComposterLang(file -> loader.getResourceAsStream(JAR_DIR + file));
    }

    /** Loads one locale (no-op when loaded or missing). Returns whether it is available afterwards. */
    public boolean load(String locale) {
        String code = locale.toLowerCase(Locale.ROOT);
        if (locales.containsKey(code)) {
            return true;
        }
        try (InputStream in = opener.apply(code + ".json")) {
            if (in == null) {
                return false;
            }
            locales.putIfAbsent(code, Map.copyOf(read(in)));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + JAR_DIR + code + ".json", e);
        }
    }

    private static Map<String, String> read(InputStream in) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.startsWith(PREFIX) && reader.peek() == JsonToken.STRING) {
                    out.put(key, reader.nextString());
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return out;
    }

    /** The text of {@code key} in {@code locale}, else in {@value #FALLBACK}, else the key itself. Any thread. */
    public String get(String locale, String key) {
        Map<String, String> exact = locales.get(locale.toLowerCase(Locale.ROOT).replace('-', '_'));
        String value = exact == null ? null : exact.get(key);
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = locales.get(FALLBACK);
        value = fallback == null ? null : fallback.get(key);
        return value == null ? key : value;
    }
}
