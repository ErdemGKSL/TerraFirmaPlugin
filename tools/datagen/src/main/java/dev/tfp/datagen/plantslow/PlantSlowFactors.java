/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (speedFactor)
 */
package dev.tfp.datagen.plantslow;

import com.google.gson.JsonObject;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PAR-GATHER-10: TFC's per-plant movement speed factor ({@code Plant.speedFactor}, the second constructor argument of every
 * enum entry) as {@code data/tfp/data_maps/plant_speed_factors.json}: {@code {"plants": {"<name>": factor}}}, read by the
 * Paper slow-block provider. The table is parsed from {@code Plant.java}, never copied by hand.
 */
public final class PlantSlowFactors {
    public static final String PLANT_JAVA = "src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java";
    public static final String FILE = "data/tfp/data_maps/plant_speed_factors.json";

    private static final Pattern ENTRY = Pattern.compile(
        "^ {4}([A-Z_]+)\\(BlockType\\.[A-Z_]+,\\s*(-?[0-9]*\\.?[0-9]+)[fF]", Pattern.MULTILINE);

    private PlantSlowFactors() {}

    /** The speed factor of every plant, by lower-case name. */
    public static Map<String, Float> parse(TfcCheckout tfc) {
        String src = tfc.readString(PLANT_JAVA);
        Map<String, Float> factors = new TreeMap<>();
        Matcher m = ENTRY.matcher(src);
        while (m.find()) {
            factors.put(m.group(1).toLowerCase(Locale.ROOT), Float.parseFloat(m.group(2)));
        }
        if (factors.isEmpty()) {
            throw new DatagenException(PLANT_JAVA + ": no plant entries found");
        }
        return factors;
    }

    public static int run(TfcCheckout tfc, OutputTree out) {
        Map<String, Float> factors = parse(tfc);
        JsonObject plants = new JsonObject();
        factors.forEach(plants::addProperty);
        JsonObject root = new JsonObject();
        root.add("plants", plants);
        out.put(FILE, Json.write(root));
        return factors.size();
    }
}
