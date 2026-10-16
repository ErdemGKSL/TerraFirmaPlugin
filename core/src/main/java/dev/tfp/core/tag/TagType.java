/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

import java.util.Optional;

/**
 * Tag registries we load ourselves ({@code data/<ns>/tags/<directory>/}). Vanilla's tags don't
 * contain CraftEngine blocks/items, so gameplay code asks our {@link TagRegistry} instead.
 * Other tag directories (damage_type, worldgen, ...) stay in the shipped datapack.
 */
public enum TagType {
    BLOCK("block"),
    ITEM("item"),
    FLUID("fluid"),
    ENTITY_TYPE("entity_type");

    private final String directory;

    TagType(String directory) {
        this.directory = directory;
    }

    /** Directory under {@code tags/}, also the registry name used in {@link dev.tfp.core.json.Reference}s. */
    public String directory() {
        return directory;
    }

    public static Optional<TagType> byDirectory(String directory) {
        for (TagType t : values()) {
            if (t.directory.equals(directory)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
