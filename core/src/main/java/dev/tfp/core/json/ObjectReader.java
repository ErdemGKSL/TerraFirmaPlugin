/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Strict reader over one JSON object. Every key read is marked consumed; {@link #finish()}
 * fails if any key was not consumed, which is how unknown or misspelled keys fail the load.
 * {@code null} values are rejected.
 */
public final class ObjectReader {
    private final JsonObject object;
    private final DecodeContext ctx;
    private final Set<String> consumed = new HashSet<>();

    private ObjectReader(JsonObject object, DecodeContext ctx) {
        this.object = object;
        this.ctx = ctx;
    }

    public static ObjectReader of(JsonElement json, DecodeContext ctx) {
        if (!json.isJsonObject()) {
            throw ctx.error("expected an object, got " + Codecs.describe(json));
        }
        return new ObjectReader(json.getAsJsonObject(), ctx);
    }

    public DecodeContext context() {
        return ctx;
    }

    public boolean has(String key) {
        return object.has(key);
    }

    /** Marks {@code key} consumed and returns its raw value, or {@code null} when absent. */
    public JsonElement take(String key) {
        consumed.add(key);
        JsonElement value = object.get(key);
        if (value != null && value.isJsonNull()) {
            throw ctx.field(key).error("null is not allowed");
        }
        return value;
    }

    public <T> T required(String key, Codec<T> codec) {
        JsonElement value = take(key);
        if (value == null) {
            throw ctx.error("missing required key '" + key + "'");
        }
        return codec.decode(value, ctx.field(key));
    }

    public <T> Optional<T> optional(String key, Codec<T> codec) {
        JsonElement value = take(key);
        return value == null ? Optional.empty() : Optional.of(codec.decode(value, ctx.field(key)));
    }

    public <T> T optional(String key, Codec<T> codec, T fallback) {
        return optional(key, codec).orElse(fallback);
    }

    /** Fails if the object has keys nobody read. */
    public void finish() {
        List<String> unknown = object.keySet().stream().filter(k -> !consumed.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw ctx.error("unknown key(s) " + unknown);
        }
    }
}
