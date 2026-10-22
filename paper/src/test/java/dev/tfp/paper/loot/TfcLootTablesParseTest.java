/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Every loot table of the TFC checkout (src/main and src/generated, c3b4d9536) parses with no unsupported rule, and
 * evaluates without error under a spread of contexts. Skipped when the checkout is absent ({@code -Dtfc.checkout}, default
 * {@code /opt/tfc}).
 */
class TfcLootTablesParseTest {
    private static Path checkout() {
        Path root = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        assumeTrue(Files.isDirectory(root.resolve("src/main/resources/data/tfc/loot_table")), "no TFC checkout at " + root);
        return root;
    }

    @Test
    void everyTfcTableParsesAndEvaluates() {
        Path root = checkout();
        LootTables main = LootTables.load(root.resolve("src/main/resources/data"));
        LootTables generated = LootTables.load(root.resolve("src/generated/resources/data"));
        assertThat(main.errors()).isEmpty();
        assertThat(generated.errors()).isEmpty();
        assertThat(main.size() + generated.size()).isGreaterThan(4500);
        assertThat(main.forBlock("tfc:rock/loose/granite")).isPresent();
        assertThat(generated.forEntity("tfc:cow")).isPresent();

        SplittableRandom seed = new SplittableRandom(9);
        List<LootContext> contexts = List.of(
                LootContext.builder().build(),
                LootContext.builder().explosionRadius(3f).isolated(true).burntOut(true).sluice(true).build(),
                LootContext.builder().tool(LootContext.Tool.of("minecraft:shears", Map.of("minecraft:silk_touch", 1,
                        "minecraft:fortune", 3), tag -> true)).cropYield(1f).blockEntity(true).build(),
                LootContext.builder().animal(new LootContext.Animal(20f, 0.8f, true)).lastDamagePlayer(5.0)
                        .attacker(LootContext.Attacker.PREDATOR).damageTags(java.util.Set.of("tfc:counts_as_player_kill"))
                        .build());
        for (LootTables tables : List.of(main, generated)) {
            for (LootTable table : tables.tables().values()) {
                for (LootContext context : contexts) {
                    for (LootDrop drop : LootEvaluator.evaluate(table, context, LootRandom.of(seed.split()))) {
                        assertThat(drop.count()).as(table.id()).isPositive();
                    }
                }
            }
        }
    }
}
