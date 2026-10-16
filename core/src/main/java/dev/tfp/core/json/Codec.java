/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Strict JSON decoder (Gson tree in, immutable value out). Decoders throw
 * {@link JsonDataException} with the file and JSON path on any mismatch: wrong JSON type,
 * unknown key, unknown {@code type}, value out of range.
 *
 * <p>Decode-only on purpose: our data flows TFC JSON → datagen → content → this loader. Nothing
 * at runtime writes these files back.
 */
@FunctionalInterface
public interface Codec<T> {
    T decode(JsonElement json, DecodeContext ctx);

    /** Decodes with a throwaway context named {@code source} (tests, ad-hoc use). */
    default T decode(JsonElement json, String source) {
        return decode(json, DecodeContext.root(source));
    }

    default <R> Codec<R> map(Function<? super T, ? extends R> f) {
        return (json, ctx) -> f.apply(decode(json, ctx));
    }

    /** Fails with {@code message} unless {@code test} holds for the decoded value. */
    default Codec<T> validate(Predicate<? super T> test, String message) {
        return (json, ctx) -> {
            T value = decode(json, ctx);
            if (!test.test(value)) {
                throw ctx.error(message + " (got " + json + ")");
            }
            return value;
        };
    }

    default Codec<List<T>> listOf() {
        return listOf(0, Integer.MAX_VALUE);
    }

    /** JSON array with {@code min..max} elements, decoded into an immutable list. */
    default Codec<List<T>> listOf(int min, int max) {
        Codec<T> element = this;
        return (json, ctx) -> {
            if (!json.isJsonArray()) {
                throw ctx.error("expected an array, got " + Codecs.describe(json));
            }
            JsonArray array = json.getAsJsonArray();
            if (array.size() < min || array.size() > max) {
                throw ctx.error("expected " + (max == Integer.MAX_VALUE ? "at least " + min : min + ".." + max)
                        + " elements, got " + array.size());
            }
            List<T> out = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                out.add(element.decode(array.get(i), ctx.index(i)));
            }
            return List.copyOf(out);
        };
    }
}
