/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (OBSIDIAN_MACUAHUITL),
 *       src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (OBSIDIAN),
 *       src/main/resources/assets/tfc/models/item/obsidian_macuahuitl.json @ c3b4d9536
 */
package dev.tfp.datagen.weapon;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.tool.RockTools;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-TOOL-04: the obsidian macuahuitl, TFC's {@code SwordItem} of {@code TFCTiers.OBSIDIAN} with
 * {@code productAttributes(OBSIDIAN, 1.0, -2.4)}, as {@code tfp:obsidian_macuahuitl} on the metal weapon template
 * ({@code tfp:item/weapon}, {@code configuration/tool/macuahuitl.yml}). The metal swords and maces are generated with the
 * metal families (their damage is TFC's factor x tier damage, the attack speed TFC's modifier).
 */
public final class Macuahuitl {
    static final String ID = "obsidian_macuahuitl";
    private static final Pattern REGISTER = Pattern.compile(
        "register\\(\"obsidian_macuahuitl\", \\(\\) -> new SwordItem\\(TFCTiers\\.OBSIDIAN, new Properties\\(\\)"
            + "\\.attributes\\(ToolItem\\.productAttributes\\(TFCTiers\\.OBSIDIAN, (-?[\\d.]+)f, (-?[\\d.]+)f\\)\\)\\)\\)");

    private Macuahuitl() {
    }

    /** Whether the checkout has the macuahuitl (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(RockTools.ITEMS_SOURCE) && tfc.exists(TfcCheckout.ASSETS + "models/item/" + ID + ".json");
    }

    /** The factor and attack speed of the registration. */
    public static RockTools.Attack attack(TfcCheckout tfc) {
        Matcher m = REGISTER.matcher(tfc.readString(RockTools.ITEMS_SOURCE));
        if (!m.find()) {
            throw new DatagenException(RockTools.ITEMS_SOURCE + ": obsidian_macuahuitl is no longer a SwordItem of the obsidian tier");
        }
        return new RockTools.Attack(new BigDecimal(m.group(1)), new BigDecimal(m.group(2)));
    }

    /** The CE entry: damage = factor x tier damage. */
    public static Map<String, Object> entry(RockTools.Tier tier, RockTools.Attack attack) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("item_lang_key", "item." + Json.TO_NS + "." + ID);
        args.put("damage", tier.damage().multiply(attack.factor()).stripTrailingZeros());
        args.put("attack_speed", attack.speed());
        args.put("durability", tier.uses());
        args.put("break_power", tier.level());
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("template", "tfp:item/weapon");
        e.put("arguments", args);
        return e;
    }

    /** Writes the CE entry, the model, the texture and the names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        RockTools.Tier tier = RockTools.tiers(tfc).get("obsidian");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put(Json.TO_NS + ":" + ID, entry(tier, attack(tfc)));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items);
        out.put(RockTools.CONFIG + "macuahuitl.yml", Yaml.write(RockTools.HEADER.replace("PAR-TOOL-01, dev.tfp.datagen.tool",
            "PAR-TOOL-04, dev.tfp.datagen.weapon"), root));
        RockTools.copyModel(tfc, out, new RockTools.Item(ID, "sword", "obsidian", true));
        RockTools.writeLang(tfc, out, java.util.List.of(new RockTools.Item(ID, "sword", "obsidian", true)), "weapon");
    }
}
