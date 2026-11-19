/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (GLOW_ARROW),
 *       src/main/java/net/dries007/tfc/common/items/GlowArrowItem.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/glow_arrow.json @ c3b4d9536
 */
package dev.tfp.datagen.tool;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PAR-TOOL-10: TFC's glow arrow as {@code tfp:glow_arrow} ({@code configuration/tool/glow_arrow.yml}) on a vanilla arrow
 * material, so bows and crossbows shoot it as an arrow; the Paper listener ({@code paper/arrow}) gives its target the glow
 * effect. The model, texture and name are TFC's.
 */
public final class GlowArrowItem {
    static final String ID = "glow_arrow";

    private GlowArrowItem() {
    }

    /** Whether the checkout has the glow arrow (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(TfcCheckout.ASSETS + "models/item/glow_arrow.json")
            && tfc.readString(RockTools.ITEMS_SOURCE).contains("register(\"glow_arrow\", () -> new GlowArrowItem(");
    }

    /** The CE entry. */
    public static Map<String, Object> entry() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("item_lang_key", "item." + Json.TO_NS + "." + ID);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("template", "tfp:item/arrow");
        e.put("arguments", args);
        return e;
    }

    /** Writes the entry, model, texture and names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put(Json.TO_NS + ":" + ID, entry());
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        out.put(RockTools.CONFIG + "glow_arrow.yml", Yaml.write(RockTools.HEADER.replace("PAR-TOOL-01, dev.tfp.datagen.tool",
            "PAR-TOOL-10, dev.tfp.datagen.tool"), root));
        RockTools.copyModel(tfc, out, new RockTools.Item(ID, "arrow", "glow", true));
        RockTools.writeLang(tfc, out, java.util.List.of(new RockTools.Item(ID, "arrow", "glow", true)), "arrow");
    }
}
