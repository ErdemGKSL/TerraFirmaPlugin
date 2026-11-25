/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType FISHING_ROD),
 *       src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/metal/fishing_rod/*.json @ c3b4d9536
 */
package dev.tfp.datagen.fishing;

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
 * PAR-FAUNA-35: the nine metal fishing rods ({@code tfp:metal/fishing_rod/<metal>}) as CE items on the vanilla
 * {@code fishing_rod} material ({@code configuration/tool/fishing_rod.yml}), with the tier's uses as TFC's
 * {@code TFCFishingRodItem} ({@code Metal.ItemType.FISHING_ROD}: {@code durability(metal.toolTier().getUses())}). TFC switches the
 * model through its {@code tfc:cast} item predicate; here the 1.21.4 item model is a {@code minecraft:fishing_rod/cast}
 * condition, true while the holder's hook is out. The runtime (bait, cast, retrieve) is {@code paper/fishing} (PAR-FAUNA-18).
 */
public final class FishingRodItems {
    static final String MODELS = TfcCheckout.ASSETS + "models/item/metal/fishing_rod/";
    static final String RP = RockTools.PACK + "resourcepack/assets/" + Json.TO_NS + "/";
    static final String CONFIG = RockTools.CONFIG + "fishing_rod.yml";
    static final String PREFIX = "metal/fishing_rod/";
    static final String LANG_RP = RockTools.PACK + "resourcepack/assets/tfp_tool_fishing_rod/lang/";
    static final int EXPECTED = 9;

    private FishingRodItems() {
    }

    /** Whether the checkout has the metal fishing rods (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(RockTools.TIERS_SOURCE) && !tfc.list(MODELS).isEmpty();
    }

    /** The metals that have a rod model (the uncast one, not {@code <metal>_cast}). */
    public static List<String> metals(TfcCheckout tfc) {
        List<String> out = new ArrayList<>();
        for (String f : tfc.list(MODELS)) {
            String n = f.substring(f.lastIndexOf('/') + 1);
            if (n.endsWith(".json") && !n.endsWith("_cast.json")) {
                out.add(n.substring(0, n.length() - 5));
            }
        }
        if (out.size() != EXPECTED) {
            throw new DatagenException(MODELS + ": expected " + EXPECTED + " fishing rod models, got " + out);
        }
        return out;
    }

    /** The CE entry of one rod: cast and uncast model on a condition. */
    public static Map<String, Object> entry(String metal, int uses) {
        String base = Json.TO_NS + ":item/" + PREFIX + metal;
        Map<String, Object> on = new LinkedHashMap<>();
        on.put("type", "minecraft:model");
        on.put("path", base + "_cast");
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("type", "minecraft:model");
        off.put("path", base);
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("type", "minecraft:condition");
        model.put("property", "minecraft:fishing_rod/cast");
        model.put("on_true", on);
        model.put("on_false", off);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item_name", "<!i><lang:item." + Json.TO_NS + ".metal.fishing_rod." + metal + ">");
        data.put("max_stack_size", 1);
        data.put("max_damage", uses);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("material", "fishing_rod");
        e.put("data", data);
        e.put("model", model);
        return e;
    }

    /** Writes the entries, models, textures and names. Returns the metals. */
    public static List<String> run(TfcCheckout tfc, OutputTree out) {
        Map<String, RockTools.Tier> tiers = RockTools.tiers(tfc);
        Map<String, Object> items = new TreeMap<>();
        List<RockTools.Item> langItems = new ArrayList<>();
        List<String> metals = metals(tfc);
        for (String metal : metals) {
            RockTools.Tier tier = tiers.get(metal);
            if (tier == null) {
                throw new DatagenException("metal fishing rod " + metal + " has no tier in " + RockTools.TIERS_SOURCE);
            }
            copyModel(tfc, out, PREFIX + metal);
            copyModel(tfc, out, PREFIX + metal + "_cast");
            items.put(Json.TO_NS + ":" + PREFIX + metal, entry(metal, tier.uses()));
            langItems.add(new RockTools.Item(PREFIX + metal, "fishing_rod", metal, true));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        out.put(CONFIG, Yaml.write(RockTools.HEADER.replace("PAR-TOOL-01, dev.tfp.datagen.tool",
            "PAR-FAUNA-35, dev.tfp.datagen.fishing"), root));
        RockTools.writeLang(tfc, out, langItems, "fishing_rod");
        return metals;
    }

    /** Copies a rod model (the cast switch is the item's condition, not a predicate override) and its tfc textures. */
    private static void copyModel(TfcCheckout tfc, OutputTree out, String id) {
        String src = TfcCheckout.ASSETS + "models/item/" + id + ".json";
        JsonObject model = Json.parse(tfc.readString(src), src).getAsJsonObject();
        if (model.has("loader") || !model.has("textures")) {
            throw new DatagenException(src + ": unexpected loader or no textures");
        }
        model.remove("overrides");
        model.remove("__comment__");
        for (Map.Entry<String, JsonElement> t : model.getAsJsonObject("textures").entrySet()) {
            String ref = t.getValue().getAsString();
            if (!ref.startsWith(Json.FROM_NS + ":")) {
                continue; // vanilla's own cast sprite
            }
            String path = ref.substring(Json.FROM_NS.length() + 1);
            String png = TfcCheckout.ASSETS + "textures/" + path + ".png";
            if (!tfc.exists(png)) {
                throw new DatagenException(src + " uses missing texture " + ref);
            }
            out.put(RP + "textures/" + path + ".png", tfc.read(png));
        }
        out.put(RP + "models/item/" + id + ".json", Json.write(Json.rename(model)));
    }
}
