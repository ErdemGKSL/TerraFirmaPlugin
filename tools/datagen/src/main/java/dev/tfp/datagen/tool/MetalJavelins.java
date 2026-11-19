/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (ItemType.JAVELIN),
 *       src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536,
 *       src/main/resources/assets/tfc/models/item/metal/javelin/*_gui.json @ c3b4d9536
 */
package dev.tfp.datagen.tool;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-TOOL-05: the metal javelins ({@code tfp:metal/javelin/<metal>}, one per metal that has a model folder entry) as
 * {@code tfp:item/javelin} items in {@code configuration/tool/metal_javelin.yml}, with TFC's {@code Metal.ItemType.JAVELIN}
 * attack (factor 0.7, speed -2.6) over the metal's tier. The javelin heads are plain metal items of the metal families.
 */
public final class MetalJavelins {
    private static final Pattern JAVELIN = Pattern.compile(
        "JAVELIN\\(PartType\\.ALL, metal -> new JavelinItem\\([^;\\n]*?tool\\(metal, (-?[\\d.]+)f, (-?[\\d.]+)f\\)");
    private static final String MODELS = TfcCheckout.ASSETS + "models/item/metal/javelin/";

    private MetalJavelins() {
    }

    /** Whether the checkout has metal javelins (the small test fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(RockTools.TIERS_SOURCE) && tfc.exists("src/main/java/net/dries007/tfc/util/Metal.java")
            && !tfc.list(MODELS).isEmpty();
    }

    /** TFC's attack of the metal javelin. */
    public static RockTools.Attack attack(TfcCheckout tfc) {
        Matcher m = JAVELIN.matcher(tfc.readString("src/main/java/net/dries007/tfc/util/Metal.java"));
        if (!m.find()) {
            throw new DatagenException("Metal.java: ItemType.JAVELIN is no longer a JavelinItem with tool(metal, f, f)");
        }
        return new RockTools.Attack(new BigDecimal(m.group(1)), new BigDecimal(m.group(2)));
    }

    /** One item per metal model (the {@code _gui}, {@code _in_hand} and {@code _throwing} files are parts). */
    public static List<RockTools.Item> items(TfcCheckout tfc) {
        List<RockTools.Item> out = new ArrayList<>();
        for (String f : tfc.list(MODELS)) {
            String name = f.substring(f.lastIndexOf('/') + 1);
            if (name.endsWith(".json") && !name.endsWith("_gui.json") && !name.endsWith("_in_hand.json")
                    && !name.contains("_throwing")) {
                String metal = name.substring(0, name.length() - 5);
                out.add(new RockTools.Item("metal/javelin/" + metal, "javelin", metal, true));
            }
        }
        return out;
    }

    /** Writes {@code configuration/tool/metal_javelin.yml}, the models, textures and names. */
    public static void run(TfcCheckout tfc, OutputTree out) {
        RockTools.write(tfc, out, items(tfc), RockTools.tiers(tfc), Map.of("javelin", attack(tfc)), "metal_javelin.yml", "javelin");
    }
}
