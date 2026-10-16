/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Decodes an object by its {@code "type"} key (TFC's registry {@code dispatch} codecs). An unknown
 * type fails with the list of known ones; it is never skipped. Types are registered under TFC's
 * names; the renamed {@code tfp:} form written by datagen is accepted too ({@link Ids#toTfc}).
 */
public final class TypeDispatch<T> implements MapCodec<T> {
    private final String what;
    private final Map<String, MapCodec<? extends T>> types;

    private TypeDispatch(String what, Map<String, MapCodec<? extends T>> types) {
        this.what = what;
        this.types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    public static <T> Builder<T> builder(String what) {
        return new Builder<>(what);
    }

    public Set<String> types() {
        return types.keySet();
    }

    public boolean has(String type) {
        return types.containsKey(type);
    }

    @Override
    public T decode(ObjectReader reader) {
        String type = reader.required("type", Codecs.ID);
        MapCodec<? extends T> codec = types.get(type);
        if (codec == null) {
            codec = types.get(Ids.toTfc(type));
        }
        if (codec == null) {
            throw reader.context().field("type").error("unknown " + what + " type '" + type + "' (known: " + types.keySet().stream().sorted().toList() + ")");
        }
        return codec.decode(reader);
    }

    public static final class Builder<T> {
        private final String what;
        private final Map<String, MapCodec<? extends T>> types = new LinkedHashMap<>();

        private Builder(String what) {
            this.what = what;
        }

        public Builder<T> register(String type, MapCodec<? extends T> codec) {
            if (types.putIfAbsent(Ids.parse(type), codec) != null) {
                throw new IllegalStateException("duplicate " + what + " type " + type);
            }
            return this;
        }

        /** A type without fields that always decodes to {@code instance}. */
        public Builder<T> unit(String type, T instance) {
            return register(type, r -> instance);
        }

        public TypeDispatch<T> build() {
            return new TypeDispatch<>(what, types);
        }
    }
}
