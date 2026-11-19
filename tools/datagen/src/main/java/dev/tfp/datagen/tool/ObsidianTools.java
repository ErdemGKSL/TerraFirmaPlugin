/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536 (OBSIDIAN_AXE .. OBSIDIAN_SHOVEL_HEAD),
 *       src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (OBSIDIAN),
 *       src/main/resources/assets/tfc/models/item/obsidian_*.json @ c3b4d9536
 */
package dev.tfp.datagen.tool;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-TOOL-11: TFC's obsidian tool set (axe, hammer, hoe, javelin, knife, shovel and their heads, the knapping and crafting
 * recipes come from the generated recipe pipelines) as CraftEngine items in {@code configuration/tool/obsidian.yml}, on the same
 * templates as the rock tools ({@link RockTools}) with {@code TFCTiers.OBSIDIAN} (level 0, 60 uses, speed 5.0, damage 2.5).
 * The attack factors and speeds are read from each {@code TFCItems} registration's {@code productAttributes}.
 */
public final class ObsidianTools {
    static final String TIER = "obsidian";
    private static final Pattern REGISTER = Pattern.compile(
        "register\\(\"obsidian_(axe|hammer|hoe|javelin|knife|shovel)\", \\(\\) -> new \\w+\\(TFCTiers\\.OBSIDIAN, [^;\\n]*?new Properties\\(\\)"
            + "\\.attributes\\(ToolItem\\.productAttributes\\(TFCTiers\\.OBSIDIAN, (-?[\\d.]+)f, (-?[\\d.]+)f\\)\\)\\)\\)");

    private ObsidianTools() {
    }

    /** Whether the checkout has the obsidian tools (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(RockTools.ITEMS_SOURCE) && tfc.exists(RockTools.TIERS_SOURCE)
            && tfc.exists(TfcCheckout.ASSETS + "models/item/obsidian_axe.json");
    }

    /** The 12 items: per kind, the head then the tool. */
    public static List<RockTools.Item> items() {
        List<RockTools.Item> out = new ArrayList<>();
        for (String kind : RockTools.KINDS) {
            out.add(new RockTools.Item("obsidian_" + kind + "_head", kind, TIER, false));
            out.add(new RockTools.Item("obsidian_" + kind, kind, TIER, true));
        }
        return out;
    }

    /** The attack factor and speed of each kind, from the registrations. */
    public static Map<String, RockTools.Attack> attacks(TfcCheckout tfc) {
        Map<String, RockTools.Attack> out = new TreeMap<>();
        Matcher m = REGISTER.matcher(tfc.readString(RockTools.ITEMS_SOURCE));
        while (m.find()) {
            out.put(m.group(1), new RockTools.Attack(new BigDecimal(m.group(2)), new BigDecimal(m.group(3))));
        }
        if (!out.keySet().containsAll(RockTools.KINDS)) {
            throw new DatagenException(RockTools.ITEMS_SOURCE + ": obsidian tools " + RockTools.KINDS + " not all found, got "
                + out.keySet());
        }
        return out;
    }

    /** Writes {@code configuration/tool/obsidian.yml}, the models, textures and names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        RockTools.write(tfc, out, items(), RockTools.tiers(tfc), attacks(tfc), "obsidian.yml", "obsidian");
    }
}
