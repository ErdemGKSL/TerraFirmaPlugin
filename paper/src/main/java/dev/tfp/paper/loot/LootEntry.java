/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.List;
import java.util.Objects;

/** A loot pool entry: {@code minecraft:item}, {@code minecraft:empty}, and the composites alternatives, group and sequence. */
public sealed interface LootEntry {

    List<LootCondition> conditions();

    /** A leaf entry (item or empty): weighted, with its own functions. */
    sealed interface Singleton extends LootEntry {
        int weight();

        int quality();

        List<LootFunction> functions();
    }

    /** {@code minecraft:item}. */
    record Item(String name, int weight, int quality, List<LootCondition> conditions, List<LootFunction> functions)
            implements Singleton {
        public Item {
            Objects.requireNonNull(name, "name");
            conditions = List.copyOf(conditions);
            functions = List.copyOf(functions);
        }
    }

    /** {@code minecraft:empty}: takes a weight share and yields nothing. */
    record Empty(int weight, int quality, List<LootCondition> conditions, List<LootFunction> functions)
            implements Singleton {
        public Empty {
            conditions = List.copyOf(conditions);
            functions = List.copyOf(functions);
        }
    }

    /** The composites; {@link Kind} picks how children expand. */
    record Composite(Kind kind, List<LootEntry> children, List<LootCondition> conditions) implements LootEntry {
        public Composite {
            Objects.requireNonNull(kind, "kind");
            children = List.copyOf(children);
            conditions = List.copyOf(conditions);
        }
    }

    enum Kind {
        /** {@code minecraft:alternatives}: the first child that expands. */
        ALTERNATIVES,
        /** {@code minecraft:group}: every child. */
        GROUP,
        /** {@code minecraft:sequence}: children in order until one does not expand. */
        SEQUENCE
    }
}
