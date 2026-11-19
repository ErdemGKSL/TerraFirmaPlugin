/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (content line and percentage)
 */
package dev.tfp.core.alloy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a vessel or crucible shows of its alloy: the metals with their amounts and percentages, and the alloy they make
 * (a bronze mix, a single metal, or unknown). Pure and thread-safe.
 */
public final class AlloyDisplay {
    /** The fluid id of an alloy no recipe makes, in this plugin's namespace ({@link FluidAlloy#UNKNOWN} is TFC's). */
    public static final String UNKNOWN = "tfp:metal/unknown";

    private AlloyDisplay() {
    }

    /**
     * One metal of the mix.
     *
     * @param fluid   the fluid id
     * @param amount  the metal's amount in mB
     * @param percent TFC's text of the share, {@code "%2.1f"} of the amount over the total rounded to a tenth
     */
    public record Entry(String fluid, double amount, String percent) {
    }

    /**
     * The view of an alloy.
     *
     * @param result  the fluid id the alloy is, empty for an empty alloy
     * @param amount  the total amount in mB
     * @param entries the metals above the display tolerance, in the order they were added
     */
    public record View(String result, int amount, List<Entry> entries) {
        public View {
            entries = List.copyOf(entries);
        }

        /** Whether the mix is several metals that no recipe makes. */
        public boolean unknown() {
            return result.equals(UNKNOWN);
        }
    }

    /** TFC {@code CrucibleScreen}: {@code String.format("%2.1f", Math.round(1000 * value / total) / 10f)}. */
    public static String percent(double value, int total) {
        return String.format(Locale.ROOT, "%2.1f", Math.round(1000 * value / total) / 10f);
    }

    /** Builds the view; {@code recipes} are the alloy recipes in lookup order. */
    public static View of(FluidAlloy alloy, List<AlloyRecipe> recipes) {
        if (alloy.isEmpty()) {
            return new View("", 0, List.of());
        }
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : alloy.contents().entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue(), percent(entry.getValue(), alloy.amount())));
        }
        String result = alloy.result(recipes);
        return new View(FluidAlloy.UNKNOWN.equals(result) ? UNKNOWN : result, alloy.amount(), entries);
    }
}
