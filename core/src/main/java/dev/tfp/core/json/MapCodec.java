/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

/**
 * Decoder for the fields of a JSON object, reading through an {@link ObjectReader} so that
 * several map codecs can share one object (TFC's flattened codecs: a sized ingredient is an
 * ingredient object plus {@code count}; a food definition is {@code ingredient} plus the food
 * fields). {@link #codec()} turns it into a whole-object codec that rejects unknown keys.
 */
@FunctionalInterface
public interface MapCodec<T> {
    T decode(ObjectReader reader);

    /** Whole-object codec: decodes, then fails on any key nobody read. */
    default Codec<T> codec() {
        return (json, ctx) -> {
            ObjectReader reader = ObjectReader.of(json, ctx);
            T value = decode(reader);
            reader.finish();
            return value;
        };
    }
}
