/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.tag;

/** Read access to resolved tags; implemented by {@link TagRegistry}. Thread-safe. */
public interface TagLookup {
    /** Whether {@code id} is in tag {@code #tag} of {@code type} (nested tags already flattened). */
    boolean contains(TagType type, String tag, String id);

    /** Whether the tag is known (defined in our data, or declared external by the id table). */
    boolean exists(TagType type, String tag);

    TagLookup EMPTY = new TagLookup() {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return false;
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return false;
        }
    };
}
