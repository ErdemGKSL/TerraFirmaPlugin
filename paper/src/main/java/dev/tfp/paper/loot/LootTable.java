/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.List;
import java.util.Objects;

/**
 * One parsed loot table (vanilla's JSON schema, as TFC writes it). Immutable; shared across threads.
 *
 * @param id the table id ({@code ns:blocks/...}, {@code ns:entities/...})
 * @param type the context type ({@code minecraft:block}, {@code minecraft:entity}, {@code minecraft:empty}, ...)
 */
public record LootTable(String id, String type, List<Pool> pools, List<LootFunction> functions) {
    public LootTable {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        pools = List.copyOf(pools);
        functions = List.copyOf(functions);
    }

    /** One pool: when its conditions pass, {@code rolls + floor(bonusRolls * luck)} weighted picks among its entries. */
    public record Pool(LootNumber rolls, LootNumber bonusRolls, List<LootEntry> entries, List<LootCondition> conditions,
            List<LootFunction> functions) {
        public Pool {
            Objects.requireNonNull(rolls, "rolls");
            Objects.requireNonNull(bonusRolls, "bonusRolls");
            entries = List.copyOf(entries);
            conditions = List.copyOf(conditions);
            functions = List.copyOf(functions);
        }
    }
}
