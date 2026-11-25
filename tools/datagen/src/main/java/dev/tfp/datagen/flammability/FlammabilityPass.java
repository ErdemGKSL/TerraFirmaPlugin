/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/ExtendedProperties.java @ c3b4d9536 (flammable, ignitedByLava)
 */
package dev.tfp.datagen.flammability;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-INFRA-47: gives every emitted CraftEngine block whose TFC counterpart is flammable CraftEngine's fire settings.
 * Runs last among the block generators and edits their {@code blocks:} entries by id (text insertion of the three
 * {@code settings} keys; the generators' files are not touched). The vanilla {@code FireBlock.setFlammable(block,
 * encouragement, flammability)} mapping: CE {@code burn_chance} (vanilla igniteOdds) = TFC {@code fireSpreadSpeed},
 * {@code fire_spread_chance} (vanilla burnOdds) = TFC {@code flammability}, {@code burnable} (ignited by lava, registered
 * with fire) = flammable. CE takes 0 to 100, so TFC's few values above 100 (lignite 120, dead bushes 120) are clamped.
 * An entry that already has its own {@code burn_chance} is left as it is (the guard test compares its values).
 *
 * <p>TFC registers a block with fire when {@code flammability > 0 || fireSpreadSpeed > 0}; {@code ignitedByLava} is
 * folded into {@code burnable}: a block that is flammable but not ignited by lava (TFC's {@code flammable(20, 30)} shelves)
 * is still {@code burnable: true} in CE, because CE has no separate lava flag (ledger divergence).
 */
public final class FlammabilityPass {
    static final String DIR = "craftengine/tfp/configuration/";
    private static final Pattern ENTRY = Pattern.compile("^  (tfp:[^\\s:]+):\\s*$");

    private FlammabilityPass() {
    }

    /** Whether the checkout has the sources the table reads (the small fixtures do not). */
    public static boolean available(TfcCheckout tfc) {
        return tfc.exists(FlammabilityTable.BLOCKS + "wood/Wood.java") && tfc.exists(FlammabilityTable.BLOCKS + "TFCBlocks.java")
            && tfc.exists(FlammabilityTable.BLOCKS + "plant/Plant.java");
    }

    /** CE's three settings for {@code fire}, as indented YAML lines. */
    static List<String> lines(FlammabilityTable.Fire fire, String indent) {
        return List.of(indent + "burnable: true", indent + "burn_chance: " + Math.min(100, fire.fireSpreadSpeed()),
            indent + "fire_spread_chance: " + Math.min(100, fire.flammability()));
    }

    /** Returns the number of block entries changed. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        FlammabilityTable.Table table = FlammabilityTable.all(tfc);
        int changed = 0;
        for (Map.Entry<String, byte[]> e : new ArrayList<>(out.files().entrySet())) {
            if (!e.getKey().startsWith(DIR) || !e.getKey().endsWith(".yml")) {
                continue;
            }
            String text = new String(e.getValue(), StandardCharsets.UTF_8);
            int[] count = {0};
            String edited = edit(text, table, count);
            if (count[0] > 0) {
                out.replace(e.getKey(), edited.getBytes(StandardCharsets.UTF_8));
                changed += count[0];
            }
        }
        return changed;
    }

    /** Inserts the settings in the {@code blocks:} section of {@code text}. */
    static String edit(String text, FlammabilityTable.Table table, int[] count) {
        String[] in = text.split("\n", -1);
        List<String> res = new ArrayList<>();
        boolean blocks = false;
        for (int i = 0; i < in.length; i++) {
            String line = in[i];
            if (!line.isEmpty() && !line.startsWith(" ") && !line.startsWith("#")) {
                blocks = line.equals("blocks:");
            }
            res.add(line);
            Matcher m = blocks ? ENTRY.matcher(line) : null;
            if (m == null || !m.matches()) {
                continue;
            }
            FlammabilityTable.Fire fire = table.fireOf(m.group(1));
            if (fire == null) {
                continue;
            }
            int end = i + 1;
            boolean settings = false;
            boolean has = false;
            int settingsAt = -1;
            while (end < in.length && (in[end].startsWith("    ") || in[end].isEmpty())) {
                if (in[end].equals("    settings:")) {
                    settings = true;
                    settingsAt = end;
                }
                if (in[end].contains("burn_chance:")) {
                    has = true;
                }
                end++;
            }
            if (has) {
                continue;
            }
            count[0]++;
            if (settings) {
                for (int j = i + 1; j <= settingsAt; j++) {
                    res.add(in[j]);
                }
                res.addAll(lines(fire, "      "));
                i = settingsAt;
            } else {
                res.add("    settings:");
                res.addAll(lines(fire, "      "));
            }
        }
        return String.join("\n", res);
    }
}
