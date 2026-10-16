/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative list of the fields of a JSON object, each with its codec. Decodes into a
 * {@link SchemaRecord}. Used for data kinds whose typed record is ported later (recipe types,
 * definition kinds): they are already validated strictly (every field typed, unknown keys fail,
 * ids/tags collected for resolution) before the feature that consumes them exists.
 *
 * @param name   schema name (recipe type id or data kind), carried into the record
 * @param fields the fields, in declaration order
 */
public record ObjectSchema(String name, List<Field> fields, Check check) implements MapCodec<SchemaRecord> {
    /** Cross-field validation run after all fields decoded; throws via {@link DecodeContext#error}. */
    @FunctionalInterface
    public interface Check {
        void check(SchemaRecord record, DecodeContext ctx);

        Check NONE = (record, ctx) -> { };
    }

    public ObjectSchema {
        fields = List.copyOf(fields);
        if (fields.stream().map(Field::name).distinct().count() != fields.size()) {
            throw new IllegalArgumentException("duplicate field in schema " + name);
        }
    }

    /** One field. Optional fields without a value are absent from the record. */
    public record Field(String name, Codec<?> codec, boolean required) {
    }

    public static Field required(String name, Codec<?> codec) {
        return new Field(name, codec, true);
    }

    public static Field optional(String name, Codec<?> codec) {
        return new Field(name, codec, false);
    }

    public static ObjectSchema of(String name, Field... fields) {
        return new ObjectSchema(name, Arrays.asList(fields), Check.NONE);
    }

    /** This schema plus {@code more} fields (TFC's codec composition, e.g. sealed barrel = barrel + duration). */
    public ObjectSchema with(String newName, Field... more) {
        List<Field> all = new ArrayList<>(fields);
        all.addAll(Arrays.asList(more));
        return new ObjectSchema(newName, all, check);
    }

    /** The same schema with an added cross-field check. */
    public ObjectSchema checked(Check extra) {
        Check first = check;
        return new ObjectSchema(name, fields, (record, ctx) -> {
            first.check(record, ctx);
            extra.check(record, ctx);
        });
    }

    /** This schema plus all fields of {@code other} (flattened map codecs such as TFC's {@code FoodData.MAP_CODEC}). */
    public ObjectSchema with(String newName, List<Field> more) {
        return with(newName, more.toArray(Field[]::new));
    }

    @Override
    public SchemaRecord decode(ObjectReader reader) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : fields) {
            if (field.required()) {
                values.put(field.name(), reader.required(field.name(), field.codec()));
            } else {
                reader.optional(field.name(), field.codec()).ifPresent(v -> values.put(field.name(), v));
            }
        }
        SchemaRecord record = new SchemaRecord(name, values);
        check.check(record, reader.context());
        return record;
    }
}
