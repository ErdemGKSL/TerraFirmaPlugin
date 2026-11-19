/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType CHISEL, PROPICK, TUYERE, SHEARS),
 *       src/main/java/net/dries007/tfc/common/items/{ChiselItem,PropickItem}.java @ c3b4d9536 (mineable tags),
 *       src/main/resources/assets/tfc/models/item/metal/{chisel,propick,tuyere,shears}/*.json @ c3b4d9536
 */
package dev.tfp.datagen.toolitems;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.tool.RockTools;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-TOOL-06: the metal chisel, prospector's pick (propick), tuyere and shears as CE items
 * ({@code configuration/tool/metal_other.yml}), so later items (PAR-TOOL-08 propick, PAR-TOOL-09 chisel, the bloomery tuyere)
 * can hand them out. The chisel and propick are {@code tfp:item/tool} with TFC's {@code mineable/chisel} and
 * {@code mineable/propick} block tags and attack values; the shears are vanilla shears with the tier's uses; the tuyere is a
 * plain item (TFC's {@code TieredItem} has neither uses nor tool rules). Per-metal models and names come from TFC.
 */
public final class MetalToolItems {
    static final String METAL = "src/main/java/net/dries007/tfc/util/Metal.java";
    static final List<String> KINDS = List.of("chisel", "propick", "shears", "tuyere");
    private static final Pattern ATTACK = Pattern.compile(
        "(CHISEL|PROPICK)\\(PartType\\.ALL, metal -> new \\w+\\([^;\\n]*?tool\\(metal, (-?[\\d.]+)f, (-?[\\d.]+)f\\)");

    private MetalToolItems() {
    }

    /** Whether the checkout has the metal items (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(METAL) && tfc.exists(RockTools.TIERS_SOURCE)
            && !tfc.list(TfcCheckout.ASSETS + "models/item/metal/chisel/").isEmpty();
    }

    /** TFC's attack factor and speed of the chisel and propick. */
    public static Map<String, RockTools.Attack> attacks(TfcCheckout tfc) {
        Map<String, RockTools.Attack> out = new LinkedHashMap<>();
        Matcher m = ATTACK.matcher(tfc.readString(METAL));
        while (m.find()) {
            out.put(m.group(1).toLowerCase(java.util.Locale.ROOT),
                new RockTools.Attack(new BigDecimal(m.group(2)), new BigDecimal(m.group(3))));
        }
        if (!out.keySet().containsAll(List.of("chisel", "propick"))) {
            throw new DatagenException(METAL + ": chisel and propick attack values not found, got " + out.keySet());
        }
        return out;
    }

    /** One item per metal model of each kind (heads and blades are plain metal items of the metal families). */
    public static List<RockTools.Item> items(TfcCheckout tfc) {
        List<RockTools.Item> out = new ArrayList<>();
        for (String kind : KINDS) {
            for (String f : tfc.list(TfcCheckout.ASSETS + "models/item/metal/" + kind + "/")) {
                String name = f.substring(f.lastIndexOf('/') + 1);
                if (name.endsWith(".json")) {
                    String metal = name.substring(0, name.length() - 5);
                    out.add(new RockTools.Item("metal/" + kind + "/" + metal, kind, metal, true));
                }
            }
        }
        return out;
    }

    /** Writes {@code configuration/tool/metal_other.yml}, the models, textures and names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        RockTools.write(tfc, out, items(tfc), RockTools.tiers(tfc), attacks(tfc), "metal_other.yml", "other");
    }
}
