/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;

/**
 * Strict JSON text parser: RFC 8259 syntax only (Gson's {@code JsonParser} is always lenient),
 * duplicate object keys are an error (Gson silently keeps the last), and trailing content after
 * the document is an error.
 */
public final class StrictJson {
    private StrictJson() {
    }

    public static JsonElement parse(String text, String source) {
        return parse(new StringReader(text), source);
    }

    public static JsonElement parse(Reader in, String source) {
        try (JsonReader reader = new JsonReader(in)) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = read(reader, source);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonDataException(source, "trailing content after the JSON document");
            }
            return root;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new JsonDataException(source, "malformed JSON: " + e.getMessage());
        }
    }

    private static JsonElement read(JsonReader reader, String source) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new JsonDataException(source, "duplicate key '" + name + "' at " + reader.getPath());
                    }
                    object.add(name, read(reader, source));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(read(reader, source));
                }
                reader.endArray();
                return array;
            }
            case STRING -> {
                return new JsonPrimitive(reader.nextString());
            }
            case NUMBER -> {
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            }
            case BOOLEAN -> {
                return new JsonPrimitive(reader.nextBoolean());
            }
            case NULL -> {
                reader.nextNull();
                return JsonNull.INSTANCE;
            }
            default -> throw new JsonDataException(source, "unexpected token " + reader.peek() + " at " + reader.getPath());
        }
    }
}
