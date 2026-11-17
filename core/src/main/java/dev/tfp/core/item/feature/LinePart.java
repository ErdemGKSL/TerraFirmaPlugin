/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item.feature;

import java.util.List;
import java.util.Objects;

/**
 * One piece of a feature tooltip line (PAR-INFRA-50), free of any text library: the platform side turns it into a
 * chat component in the viewer's language. Mirrors the shapes TFC builds its lines from ({@code Component.literal},
 * {@code Component.translatable(key, args)}, {@code stack.getHoverName()}, {@code fluid.getHoverName()}).
 */
public sealed interface LinePart {
    /** Plain text ({@code Component.literal}); numbers are written as {@code String.valueOf} would. */
    record Literal(String text) implements LinePart {
        public Literal {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A translation key with its arguments ({@code Component.translatable(key, args...)}). */
    record Key(String key, List<LinePart> args) implements LinePart {
        public Key {
            Objects.requireNonNull(key, "key");
            args = List.copyOf(args);
        }
    }

    /** The name of an item ({@code ItemStack.getHoverName()}), by item id. */
    record ItemName(String itemId) implements LinePart {
        public ItemName {
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    /** The name of a fluid ({@code FluidStack.getHoverName()}), by fluid id. */
    record FluidName(String fluidId) implements LinePart {
        public FluidName {
            Objects.requireNonNull(fluidId, "fluidId");
        }
    }

    static LinePart literal(Object value) {
        return new Literal(String.valueOf(value));
    }

    static LinePart key(String key, LinePart... args) {
        return new Key(key, List.of(args));
    }
}
