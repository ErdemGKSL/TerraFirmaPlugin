/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/FluidAlloy.java @ c3b4d9536 (codec of the fluid map and total)
 */
package dev.tfp.core.alloy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Text form of a {@link FluidAlloy} for storage on an item: one {@code "<fluid id>=<amount>"} line per metal, the total kept
 * beside it by the caller. Pure and thread-safe; a malformed line is skipped.
 */
public final class AlloyCodec {
    private AlloyCodec() {
    }

    /** The lines of an alloy's fluid map, in order. */
    public static List<String> encode(FluidAlloy alloy) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Double> entry : alloy.contents().entrySet()) {
            lines.add(entry.getKey() + "=" + entry.getValue());
        }
        return lines;
    }

    /** The alloy of the lines and the total; the empty alloy for no valid line. */
    public static FluidAlloy decode(int amount, List<String> lines) {
        Map<String, Double> content = new LinkedHashMap<>();
        for (String line : lines) {
            int eq = line.lastIndexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                double value = Double.parseDouble(line.substring(eq + 1));
                if (value > 0d && Double.isFinite(value)) {
                    content.merge(line.substring(0, eq), value, Double::sum);
                }
            } catch (NumberFormatException ignored) {
                // skip the line
            }
        }
        return FluidAlloy.of(amount, content);
    }
}
