/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/**
 * An output template's data components ({@code "components": {"minecraft:instrument": "..."}}, as in TFC's
 * {@code ItemStack.CODEC}) written in the item-stack command syntax {@code id[component=SNBT,...]}, which Paper's
 * {@code ItemFactory.createItemStack(String)} parses.
 */
public final class ItemSyntax {
    private ItemSyntax() {
    }

    public static String of(String id, JsonObject components) {
        if (components.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, JsonElement> e : components.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            String key = e.getKey();
            if (key.startsWith("!")) {
                sb.append(key);
                continue;
            }
            sb.append(key).append('=');
            snbt(e.getValue(), sb);
        }
        return sb.append(']').toString();
    }

    /** JSON → SNBT: objects and lists as compounds and lists, strings quoted, numbers and booleans as written. */
    static void snbt(JsonElement json, StringBuilder sb) {
        if (json.isJsonObject()) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(e.getKey(), sb);
                sb.append(':');
                snbt(e.getValue(), sb);
            }
            sb.append('}');
        } else if (json.isJsonArray()) {
            JsonArray a = json.getAsJsonArray();
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                snbt(a.get(i), sb);
            }
            sb.append(']');
        } else if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isString()) {
                quote(p.getAsString(), sb);
            } else {
                sb.append(p.getAsString());
            }
        } else {
            sb.append("{}");
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
    }
}
