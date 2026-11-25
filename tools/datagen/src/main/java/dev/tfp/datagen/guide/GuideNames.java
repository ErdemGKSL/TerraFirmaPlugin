/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/assets/tfc/lang/en_us.json @ c3b4d9536 (the English names the guide's recipe text uses)
 */
package dev.tfp.datagen.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.TfcCheckout;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * English display names for the field guide's recipe text (PAR-DATA-04), from TFC's {@code en_us} lang: items
 * ({@code item.<ns>.<path>} then {@code block.<ns>.<path>}), tags ({@code tag.item.<ns>.<path>}), fluids
 * ({@code fluid.<ns>.<path>}), entities and any other key. A vanilla or unnamed id falls back to its last path segment in
 * title case ({@code minecraft:blue_dye} is "Blue Dye"); vanilla's own names are not in TFC's lang.
 */
public final class GuideNames {
    public static final String LANG = TfcCheckout.ASSETS + "lang/en_us.json";

    private final Map<String, String> lang;

    public GuideNames(Map<String, String> lang) {
        this.lang = Map.copyOf(lang);
    }

    /** TFC's English lang, or no names when the checkout has none. */
    public static GuideNames read(TfcCheckout tfc) {
        Map<String, String> map = new HashMap<>();
        if (tfc.exists(LANG)) {
            JsonObject o = Json.parse(tfc.readString(LANG), LANG).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        return new GuideNames(map);
    }

    /** A lang key, or null. */
    public String key(String key) {
        return lang.get(key);
    }

    public String item(String id) {
        String bare = strip(id);
        return first(humanize(bare), "item." + dotted(bare), "block." + dotted(bare));
    }

    public String block(String state) {
        String bare = strip(state);
        return first(humanize(bare), "block." + dotted(bare), "item." + dotted(bare));
    }

    public String tag(String tag) {
        String bare = tag.startsWith("#") ? tag.substring(1) : tag;
        return first(humanizeTag(bare), "tag.item." + dotted(bare), "tag.block." + dotted(bare));
    }

    /** A tag without a name: its last two path segments, last first ({@code c:rods/wooden} is "Wooden Rods"). */
    static String humanizeTag(String tag) {
        String path = tag.substring(tag.indexOf(':') + 1);
        String[] parts = path.split("/");
        if (parts.length < 2) {
            return humanize(path);
        }
        return humanize(parts[parts.length - 1]) + " " + humanize(parts[parts.length - 2]);
    }

    public String fluid(String id) {
        String bare = strip(id);
        return first(humanize(bare), "fluid." + dotted(bare), "fluid_type." + dotted(bare));
    }

    public String entity(String id) {
        String bare = strip(id);
        return first(humanize(bare), "entity." + dotted(bare));
    }

    /** {@code ns:a/b} as {@code ns.a.b}; a bare path is in TFC's namespace. */
    static String dotted(String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? Json.FROM_NS : id.substring(0, colon);
        return ns + "." + id.substring(colon + 1).replace('/', '.');
    }

    /** Drops item components ({@code [..]}), NBT ({@code {..}}) and block state properties from an id. */
    static String strip(String id) {
        int cut = id.length();
        for (char c : new char[] {'[', '{'}) {
            int i = id.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return id.substring(0, cut).trim();
    }

    /** The last path segment of an id in title case: {@code minecraft:blue_dye} is "Blue Dye". */
    public static String humanize(String id) {
        String s = id;
        int colon = s.indexOf(':');
        if (colon >= 0) {
            s = s.substring(colon + 1);
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0) {
            s = s.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("_")) {
            if (w.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }

    private String first(String fallback, String... keys) {
        for (String k : keys) {
            String v = lang.get(k);
            if (v != null) {
                return v;
            }
        }
        return fallback;
    }
}
