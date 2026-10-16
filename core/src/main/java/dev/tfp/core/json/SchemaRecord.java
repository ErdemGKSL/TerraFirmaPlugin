/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Immutable decoded object of an {@link ObjectSchema}: field name → decoded value (immutable
 * records, lists, strings, boxed numbers). Feature code converts it to its own typed record.
 *
 * @param schema the schema name (recipe type id or data kind)
 */
public record SchemaRecord(String schema, Map<String, Object> values) {
    public SchemaRecord {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean has(String field) {
        return values.containsKey(field);
    }

    public <T> Optional<T> get(String field, Class<T> type) {
        Object value = values.get(field);
        return value == null ? Optional.empty() : Optional.of(type.cast(value));
    }

    public <T> T require(String field, Class<T> type) {
        return get(field, type).orElseThrow(() -> new NoSuchElementException(schema + " has no field " + field));
    }
}
