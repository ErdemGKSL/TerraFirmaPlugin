/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (POTTERY_HEAT_CAPACITY)
 */
package dev.tfp.datagen.potteryheat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.pottery.PotteryItems;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-POT-11: {@code item_heat} data for fired pottery. TFC gives {@code VesselItem} and {@code MoldItem} the heat
 * component {@code HeatComponent.of(POTTERY_HEAT_CAPACITY)} as a default item component, so TFC has no {@code item_heat}
 * file for them; our runtime reads heat capacity only from {@code item_heat} data, so one definition lists every fired vessel
 * (plain and 16 glazed) and every fired mold (metal molds, bell, fire ingot) with capacity 1.2. Unfired items and the jug have
 * no default heat component in TFC and get none.
 */
public final class PotteryHeat {
    /** {@code HeatCapability.POTTERY_HEAT_CAPACITY}. */
    public static final float POTTERY_HEAT_CAPACITY = 1.2f;
    static final String PATH = "data/tfp/tfp/item_heat/ceramic/fired_pottery.json";

    private PotteryHeat() {
    }

    /** Whether a ceramic item id (path under {@code tfp:}) is a fired vessel or mold. */
    public static boolean fired(String id) {
        if (id.contains("unfired") || !id.startsWith("ceramic/")) {
            return false;
        }
        return id.equals("ceramic/vessel") || id.endsWith("_glazed_vessel") || id.endsWith("_mold");
    }

    /** The item ids (with namespace) that get the pottery heat capacity. */
    public static List<String> ids(TfcCheckout tfc) {
        List<String> out = new ArrayList<>();
        for (PotteryItems.PotteryItem i : PotteryItems.items(tfc)) {
            if (fired(i.id())) {
                out.add(Json.TO_NS + ":" + i.id());
            }
        }
        return out;
    }

    public static void run(TfcCheckout tfc, OutputTree out) {
        JsonArray list = new JsonArray();
        for (String id : ids(tfc)) {
            JsonObject o = new JsonObject();
            o.addProperty("item", id);
            list.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("heat_capacity", POTTERY_HEAT_CAPACITY);
        root.add("ingredient", list);
        out.put(PATH, Json.write(root));
    }
}
