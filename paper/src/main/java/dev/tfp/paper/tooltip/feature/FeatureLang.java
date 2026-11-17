/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The part of TFC's own language files the feature lines need (PAR-INFRA-50): the tooltip patterns
 * ({@code tfc.tooltip.*}), the glass operation names ({@code glass_operation.tfc.*}) and the item, block and fluid names
 * ({@code item.tfc.*}, {@code block.tfc.*}, {@code fluid.tfc.*}), read from the {@code content/lang/<locale>.json} files
 * datagen writes (TFC's keys verbatim, every TFC locale).
 *
 * <p>{@link #get} never blocks: a locale that is not loaded yet answers from {@value #FALLBACK} and is loaded in the
 * background through the {@code loader} executor, so a client in that language sees its own names from the next item
 * packet on. {@link #load} is blocking IO for enable and tests.
 */
public final class FeatureLang {
    public static final String FALLBACK = "en_us";
    public static final String JAR_DIR = "content/lang/";
    private static final String[] PREFIXES = {
        "tfc.tooltip.", "glass_operation.tfc.", "item.tfc.", "block.tfc.", "fluid.tfc."
    };

    private final Function<String, InputStream> opener;
    private final Consumer<Runnable> loader;
    private final Map<String, Map<String, String>> locales = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    /**
     * @param opener opens {@code <locale>.json} by file name, or returns {@code null} when there is none
     * @param loader runs a background load (the IO executor); {@code Runnable::run} loads in place (tests)
     */
    public FeatureLang(Function<String, InputStream> opener, Consumer<Runnable> loader) {
        this.opener = Objects.requireNonNull(opener, "opener");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /** The lang files of the plugin jar. */
    public static FeatureLang fromJar(ClassLoader classLoader, Consumer<Runnable> loader) {
        return new FeatureLang(file -> classLoader.getResourceAsStream(JAR_DIR + file), loader);
    }

    static boolean wanted(String key) {
        for (String prefix : PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static String code(String locale) {
        return locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** Loads one locale (no-op when loaded or without a file). Blocking IO. @return whether it is available */
    public boolean load(String locale) {
        String code = code(locale);
        requested.add(code);
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

    /**
     * The text of {@code key} in {@code locale}, else in {@value #FALLBACK}, else {@code null}. Any thread; never blocks
     * (an unloaded locale is queued for a background load).
     */
    public String get(String locale, String key) {
        String code = code(locale);
        Map<String, String> exact = locales.get(code);
        if (exact == null && requested.add(code)) {
            loader.accept(() -> {
                try {
                    load(code);
                } catch (RuntimeException ignored) {
                    // a broken file leaves the fallback in use
                }
            });
            exact = locales.get(code);
        }
        String value = exact == null ? null : exact.get(key);
        if (value != null) {
            return value;
        }
        Map<String, String> fallback = locales.get(FALLBACK);
        return fallback == null ? null : fallback.get(key);
    }

    /** Whether {@code locale} is loaded. */
    public boolean isLoaded(String locale) {
        return locales.containsKey(code(locale));
    }
}
