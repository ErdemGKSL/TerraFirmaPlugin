/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fluid;

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
 * The fluid names and fluid tooltip patterns of TFC's own language files (PAR-FLUID-01), read from the plugin data
 * datagen writes to {@code content/lang/<locale>.json} (TFC's keys verbatim, every TFC locale). Only the keys this
 * feature uses are kept: {@code fluid.tfc.*} and {@code tfc.tooltip.fluid*}.
 *
 * <p>Lookup: the exact locale, else {@value #FALLBACK}, else {@code null}. Locales are loaded with {@link #load(String)}
 * (blocking IO; call it off the Netty thread, at enable); {@link #get} never loads and is safe on any thread.
 */
public final class FluidLang {
    public static final String FALLBACK = "en_us";
    /** Where datagen's lang data lives in the plugin jar. */
    public static final String JAR_DIR = "content/lang/";

    private final Function<String, InputStream> opener;
    private final Map<String, Map<String, String>> locales = new ConcurrentHashMap<>();

    /** @param opener opens {@code <locale>.json} by its file name, or returns {@code null} when there is none */
    public FluidLang(Function<String, InputStream> opener) {
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /** The lang files of the plugin jar. */
    public static FluidLang fromJar(ClassLoader loader) {
        return new FluidLang(file -> loader.getResourceAsStream(JAR_DIR + file));
    }

    /** Whether {@code key} is one this class keeps. */
    static boolean wanted(String key) {
        return key.startsWith("fluid.tfc.") || key.startsWith("tfc.tooltip.fluid");
    }

    /**
     * Loads one locale (a no-op when it is loaded already or has no file). Blocking IO.
     *
     * @return whether the locale is available afterwards
     */
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
                if (wanted(key) && reader.peek() == JsonToken.STRING) {
                    out.put(key, reader.nextString());
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }
        return out;
    }

    /** The text of {@code key} in {@code locale} (else {@value #FALLBACK}), or {@code null}. Any thread; never loads. */
    public String get(String locale, String key) {
        Map<String, String> exact = locales.get(locale.toLowerCase(Locale.ROOT).replace('-', '_'));
        String value = exact == null ? null : exact.get(key);
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = locales.get(FALLBACK);
        return fallback == null ? null : fallback.get(key);
    }

    /** The number of loaded locales. */
    public int loadedLocales() {
        return locales.size();
    }
}
