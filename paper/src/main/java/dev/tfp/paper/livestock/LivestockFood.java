/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestock;

import dev.tfp.core.animal.Carrier;

/** The item tag of a livestock species' food: {@code tfp:cow_food}; both camels share {@code tfp:camel_food}. */
public final class LivestockFood {
    private LivestockFood() {
    }

    public static String tag(Carrier carrier) {
        String name = carrier.name();
        if (name.endsWith("_camel")) {
            name = "camel";
        }
        return "tfp:" + name + "_food";
    }
}
