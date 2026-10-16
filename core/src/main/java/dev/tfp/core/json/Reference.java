/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

/**
 * An id or tag that a data file refers to, recorded while decoding and resolved after the whole
 * load (so files may refer to each other in any order).
 *
 * @param registry  what is referenced: {@code item}, {@code block}, {@code fluid}, {@code entity_type},
 *                  {@code food_trait}, or the name of one of our data registries (e.g. {@code knapping_type})
 * @param id        namespaced id (without {@code #})
 * @param tag       whether {@code id} names a tag
 * @param location  where the reference was made, for error messages
 */
public record Reference(String registry, String id, boolean tag, String location) {
    @Override
    public String toString() {
        return registry + (tag ? " tag #" : " ") + id + " at " + location;
    }
}
