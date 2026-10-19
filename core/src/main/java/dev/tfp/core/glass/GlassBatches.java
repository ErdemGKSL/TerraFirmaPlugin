/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tags/item/glass_batches*.json @ c3b4d9536 (the four batch tags and their nesting)
 */
package dev.tfp.core.glass;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The glass batch items and the item tags that group them. Tags are resolved recursively, the way TFC's tag loader does, so
 * {@code #tfc:glass_batches} is every batch and {@code #tfc:glass_batches_tier_2} is silica and hematitic.
 */
public final class GlassBatches {

    private static final Map<String, List<String>> TAGS = Map.of(
        "tfc:glass_batches", List.of("#tfc:glass_batches_tier_3", "tfc:volcanic_glass_batch"),
        "tfc:glass_batches_tier_2", List.of("tfc:silica_glass_batch", "tfc:hematitic_glass_batch"),
        "tfc:glass_batches_tier_3", List.of("#tfc:glass_batches_tier_2", "tfc:olivine_glass_batch"),
        "tfc:glass_batches_not_tier_1", List.of("tfc:hematitic_glass_batch", "tfc:olivine_glass_batch", "tfc:volcanic_glass_batch")
    );

    private GlassBatches() {}

    /**
     * The batch items a recipe ingredient accepts: an item id is itself, a {@code #tag} is its expanded members. The result is
     * sorted, without duplicates, and immutable. An unknown tag expands to nothing.
     */
    public static List<String> members(String ref) {
        final TreeSet<String> out = new TreeSet<>();
        collect(ref, out, 0);
        return List.copyOf(out);
    }

    private static void collect(String ref, TreeSet<String> out, int depth) {
        if (depth > TAGS.size()) {
            throw new IllegalStateException("glass batch tag cycle at " + ref);
        }
        if (ref.startsWith("#")) {
            for (String value : TAGS.getOrDefault(ref.substring(1), List.of())) {
                collect(value, out, depth + 1);
            }
        } else {
            out.add(ref);
        }
    }

    /** Whether {@code item} is a member of the ingredient {@code ref}. */
    public static boolean accepts(String ref, String item) {
        return members(ref).contains(item);
    }
}
