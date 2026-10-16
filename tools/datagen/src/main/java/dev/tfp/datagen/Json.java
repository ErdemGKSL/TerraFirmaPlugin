/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.regex.Pattern;

/** JSON parsing, the tfc → tfp rename, and the one stable output format (2-space indent, LF, trailing newline). */
public final class Json {
    public static final String FROM_NS = "tfc";
    public static final String TO_NS = "tfp";

    /** {@code tfc:} at the start of an id or after {@code #} (tag refs); never inside another word. */
    private static final Pattern NAMESPACE = Pattern.compile("(?<![A-Za-z0-9_.\\-/])" + FROM_NS + ":");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {
    }

    public static JsonElement parse(String text, String source) {
        try {
            return JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new DatagenException("invalid JSON in " + source + ": " + e.getMessage());
        }
    }

    public static String write(JsonElement e) {
        return GSON.toJson(e).replace("\r\n", "\n") + "\n";
    }

    /** Renames every {@code tfc:} id prefix in a string. */
    public static String renameId(String s) {
        return NAMESPACE.matcher(s).replaceAll(TO_NS + ":");
    }

    /**
     * Deep copy with every string value and object key renamed, and mcresources' {@code __comment__} keys
     * dropped (they are not part of any schema). Key order is preserved.
     */
    public static JsonElement rename(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("__comment__")) {
                    continue;
                }
                out.add(renameId(en.getKey()), rename(en.getValue()));
            }
            return out;
        }
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement c : e.getAsJsonArray()) {
                out.add(rename(c));
            }
            return out;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(renameId(e.getAsString()));
        }
        return e.deepCopy();
    }
}
