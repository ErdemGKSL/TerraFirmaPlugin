/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.knap;

import java.util.Map;
import java.util.TreeMap;

/**
 * The item a knapping recipe produces: an item id, a count and the data components it sets (for example
 * {@code minecraft:instrument} on the goat horns). Components are kept sorted so equal results compare equal.
 */
public record KnappingResult(String item, int count, Map<String, String> components) {
    public KnappingResult {
        if (item == null || item.isEmpty()) throw new IllegalArgumentException("knapping result needs an item");
        if (count < 1) throw new IllegalArgumentException("knapping result " + item + ": count must be positive");
        components = java.util.Collections.unmodifiableMap(new TreeMap<>(components));
    }
}
