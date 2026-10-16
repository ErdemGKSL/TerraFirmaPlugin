/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Primitive strict codecs. Numbers must be JSON numbers (no numeric strings), booleans JSON booleans. */
public final class Codecs {
    private Codecs() {
    }

    public static final Codec<Boolean> BOOL = (json, ctx) -> {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            return json.getAsBoolean();
        }
        throw ctx.error("expected a boolean, got " + describe(json));
    };

    public static final Codec<String> STRING = (json, ctx) -> {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            return json.getAsString();
        }
        throw ctx.error("expected a string, got " + describe(json));
    };

    public static final Codec<Integer> INT = (json, ctx) -> {
        BigDecimal value = number(json, ctx);
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw ctx.error("expected an integer, got " + json);
        }
    };

    public static final Codec<Float> FLOAT = (json, ctx) -> {
        float value = number(json, ctx).floatValue();
        if (!Float.isFinite(value)) {
            throw ctx.error("number out of float range: " + json);
        }
        return value;
    };

    public static final Codec<Double> DOUBLE = (json, ctx) -> number(json, ctx).doubleValue();

    public static final Codec<Integer> NON_NEGATIVE_INT = intRange(0, Integer.MAX_VALUE);
    public static final Codec<Integer> POSITIVE_INT = intRange(1, Integer.MAX_VALUE);
    public static final Codec<Float> POSITIVE_FLOAT = FLOAT.validate(f -> f > 0f, "expected a positive number");

    /** A namespaced id, normalised (missing namespace = {@code minecraft}). Not resolved. */
    public static final Codec<String> ID = (json, ctx) -> {
        String raw = STRING.decode(json, ctx);
        try {
            return Ids.parse(raw);
        } catch (IllegalArgumentException e) {
            throw ctx.error(e.getMessage());
        }
    };

    /** Raw JSON, deep-copied so the stored value cannot be changed through the parse tree. */
    public static final Codec<JsonElement> JSON = (json, ctx) -> json.deepCopy();

    /** A raw JSON object (deep copy), e.g. item/fluid components kept for later systems. */
    public static final Codec<JsonObject> JSON_OBJECT = (json, ctx) -> {
        if (!json.isJsonObject()) {
            throw ctx.error("expected an object, got " + describe(json));
        }
        return json.getAsJsonObject().deepCopy();
    };

    /** Defers to {@code codec.get()} on use, for recursive codecs. */
    public static <T> Codec<T> lazy(Supplier<Codec<T>> codec) {
        return (json, ctx) -> codec.get().decode(json, ctx);
    }

    public static Codec<Integer> intRange(int min, int max) {
        return INT.validate(v -> v >= min && v <= max, "expected an integer in " + min + ".." + max);
    }

    public static Codec<Float> floatRange(float min, float max) {
        return FLOAT.validate(v -> v >= min && v <= max, "expected a number in " + min + ".." + max);
    }

    /** One of the given lowercase names (TFC's {@code StringRepresentable} enums). */
    public static Codec<String> oneOf(String... values) {
        Set<String> allowed = Set.of(values);
        List<String> sorted = allowed.stream().sorted().toList();
        return (json, ctx) -> {
            String value = STRING.decode(json, ctx);
            if (!allowed.contains(value)) {
                throw ctx.error("'" + value + "' is not one of " + sorted);
            }
            return value;
        };
    }

    /** One of the given TFC registry ids (e.g. chisel modes); the datagen-renamed {@code tfp:} form is accepted and normalised to TFC's. */
    public static Codec<String> oneOfTfcIds(String... tfcIds) {
        Set<String> allowed = Set.of(tfcIds);
        List<String> sorted = allowed.stream().sorted().toList();
        return (json, ctx) -> {
            String value = Ids.toTfc(ID.decode(json, ctx));
            if (!allowed.contains(value)) {
                throw ctx.error("'" + value + "' is not one of " + sorted);
            }
            return value;
        };
    }

    /** An id that must exist in {@code registry}; recorded as a {@link Reference} and checked after the load. */
    public static Codec<String> ref(String registry) {
        return (json, ctx) -> {
            String id = ID.decode(json, ctx);
            ctx.reference(registry, id, false);
            return id;
        };
    }

    /** A tag id written without {@code #} (TFC's {@code TagKey.codec}); recorded as a tag reference. */
    public static Codec<String> tagRef(String registry) {
        return (json, ctx) -> {
            String raw = STRING.decode(json, ctx);
            if (raw.startsWith("#")) {
                throw ctx.error("tag id must not start with '#': " + raw);
            }
            String id = ID.decode(json, ctx);
            ctx.reference(registry, id, true);
            return id;
        };
    }

    /**
     * Fixed grid of strings ({@code rows} strings of exactly {@code width} chars from {@code chars}),
     * as used by sewing recipes.
     */
    public static Codec<List<String>> grid(int width, int rows, String chars) {
        return STRING.validate(s -> s.length() == width && s.chars().allMatch(c -> chars.indexOf(c) >= 0),
                "expected " + width + " chars from \"" + chars + "\"").listOf(rows, rows);
    }

    static BigDecimal number(JsonElement json, DecodeContext ctx) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            try {
                return json.getAsBigDecimal();
            } catch (NumberFormatException e) {
                throw ctx.error("malformed number " + json);
            }
        }
        throw ctx.error("expected a number, got " + describe(json));
    }

    /** Short description of a JSON value for error messages. */
    public static String describe(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return "null";
        }
        if (json.isJsonObject()) {
            return "an object";
        }
        if (json.isJsonArray()) {
            return "an array";
        }
        JsonPrimitive p = json.getAsJsonPrimitive();
        return (p.isString() ? "string " : p.isNumber() ? "number " : "boolean ") + p;
    }
}
