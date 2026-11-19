/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType.SHIELD),
 *       src/main/java/net/dries007/tfc/common/items/TFCShieldItem.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/metal/shield/*.json @ c3b4d9536
 */
package dev.tfp.datagen.shield;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.tool.RockTools;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * PAR-TOOL-07: the metal shields ({@code tfp:metal/shield/<metal>}, 9 metals) as CE items on the vanilla {@code shield}
 * material with the tier's uses ({@code configuration/tool/shield.yml}). The held model switches to TFC's
 * {@code <metal>_blocking} model while the item is in use ({@code minecraft:using_item} condition). The damage a block
 * absorbs is {@code paper/shield}'s (TFC's {@code onShieldBlock}).
 */
public final class ShieldItems {
    static final String MODELS = TfcCheckout.ASSETS + "models/item/metal/shield/";
    static final String RP = RockTools.PACK + "resourcepack/assets/" + Json.TO_NS + "/";

    private ShieldItems() {
    }

    /** Whether the checkout has metal shields (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(RockTools.TIERS_SOURCE) && !tfc.list(MODELS).isEmpty();
    }

    /** The metals that have a shield model. */
    public static List<String> metals(TfcCheckout tfc) {
        List<String> out = new ArrayList<>();
        for (String f : tfc.list(MODELS)) {
            String n = f.substring(f.lastIndexOf('/') + 1);
            if (n.endsWith(".json") && !n.endsWith("_blocking.json")) {
                out.add(n.substring(0, n.length() - 5));
            }
        }
        return out;
    }

    /** The CE entry of one shield. */
    public static Map<String, Object> entry(String metal, int uses) {
        String base = Json.TO_NS + ":item/metal/shield/" + metal;
        Map<String, Object> on = new LinkedHashMap<>();
        on.put("type", "minecraft:model");
        on.put("path", base + "_blocking");
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("type", "minecraft:model");
        off.put("path", base);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "minecraft:condition");
        model.put("property", "minecraft:using_item");
        model.put("on_true", on);
        model.put("on_false", off);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_name", "<!i><lang:item." + Json.TO_NS + ".metal.shield." + metal + ">");
        data.put("max_stack_size", 1);
        data.put("max_damage", uses);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("material", "shield");
        e.put("data", data);
        e.put("model", model);
        return e;
    }

    /** Writes the entries, models, textures and names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        Map<String, RockTools.Tier> tiers = RockTools.tiers(tfc);
        Map<String, Object> items = new TreeMap<>();
        List<RockTools.Item> langItems = new ArrayList<>();
        for (String metal : metals(tfc)) {
            RockTools.Tier tier = tiers.get(metal);
            if (tier == null) {
                throw new DatagenException("metal shield " + metal + " has no tier in " + RockTools.TIERS_SOURCE);
            }
            for (String suffix : List.of("", "_blocking")) {
                copyModel(tfc, out, "metal/shield/" + metal + suffix);
            }
            items.put(Json.TO_NS + ":metal/shield/" + metal, entry(metal, tier.uses()));
            langItems.add(new RockTools.Item("metal/shield/" + metal, "shield", metal, true));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        out.put(RockTools.CONFIG + "shield.yml", Yaml.write(RockTools.HEADER.replace("PAR-TOOL-01, dev.tfp.datagen.tool",
            "PAR-TOOL-07, dev.tfp.datagen.shield"), root));
        RockTools.writeLang(tfc, out, langItems, "shield");
    }

    /** Copies a standalone (Blockbench) item model and its textures. */
    private static void copyModel(TfcCheckout tfc, OutputTree out, String id) {
        String src = TfcCheckout.ASSETS + "models/item/" + id + ".json";
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (model.has("loader") || model.has("parent") && !model.get("parent").getAsString().startsWith("minecraft:")
                && !model.get("parent").getAsString().startsWith("item/")) {
            throw new DatagenException(src + ": unexpected loader, overrides or parent");
        }
        model.remove("overrides"); // the blocking switch is the item's using_item condition
        if (model.has("textures")) {
            for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
                String ref = t.getValue().getAsString();
                if (!ref.startsWith(Json.FROM_NS + ":")) {
                    continue;
                }
                String path = ref.substring(Json.FROM_NS.length() + 1);
                String png = TfcCheckout.ASSETS + "textures/" + path + ".png";
                if (!tfc.exists(png)) {
                    throw new DatagenException(src + " uses missing texture " + ref);
                }
                out.put(RP + "textures/" + path + ".png", tfc.read(png));
            }
        }
        out.put(RP + "models/item/" + id + ".json", Json.write(Json.rename(model)));
    }
}
