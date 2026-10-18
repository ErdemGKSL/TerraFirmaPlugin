/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/loot_table/entities/*.json (small_fish and caught_in_lobster_traps species) @ c3b4d9536
 * (golden values only)
 */
package dev.tfp.core.fishing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.hunt.HuntCondition;
import dev.tfp.core.hunt.HuntDrop;
import dev.tfp.core.hunt.HuntDrops;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the fishing and lobster-trap loot tables. {@code fishing_loot_golden.tsv} in this package's test resources is
 * exported from the 23 TFC loot tables of the {@code small_fish} and {@code caught_in_lobster_traps} tags at c3b4d9536 by a scratch
 * script kept outside the repository: one row per entry. The tests check the file and row counts, each table's total weight, that
 * the trap species are the lobster-trap tag, and that the hunt drop table the rules roll agrees with every row.
 */
class FishingLootGoldenTest {
    /** The TFC loot table files of the two tags. */
    private static final int LOOT_TABLE_FILES = 23;
    /** The species of {@code tfc:small_fish}. */
    private static final int SMALL_FISH = 19;
    /** The species of {@code tfc:caught_in_lobster_traps}. */
    private static final int TRAP_SPECIES = 4;
    private static final String RESOURCE = "/dev/tfp/core/fishing/fishing_loot_golden.tsv";

    record Row(String group, String species, int pool, int index, String item, double rolls, int weight, String conditions) {}

    @Test
    void fileAndRowCountsMatchTfc() throws IOException {
        List<Row> rows = rows();
        Set<String> species = new TreeSet<>();
        Map<String, Integer> perGroup = new TreeMap<>();
        for (Row row : rows) {
            species.add(row.species());
            perGroup.merge(row.group(), 1, Integer::sum);
        }
        assertThat(rows).hasSize(LOOT_TABLE_FILES);
        assertThat(species).hasSize(LOOT_TABLE_FILES);
        assertThat(perGroup).containsEntry("small_fish", SMALL_FISH).containsEntry("lobster_trap", TRAP_SPECIES);
    }

    @Test
    void eachTableHasOneSinglePoolWithTotalWeightOne() throws IOException {
        Map<String, Integer> weightPerSpecies = new TreeMap<>();
        Map<String, Integer> poolsPerSpecies = new TreeMap<>();
        for (Row row : rows()) {
            weightPerSpecies.merge(row.species(), row.weight(), Integer::sum);
            poolsPerSpecies.merge(row.species(), row.pool(), Math::max);
            assertThat(row.rolls()).as(row.species()).isEqualTo(1.0);
        }
        for (String species : weightPerSpecies.keySet()) {
            assertThat(weightPerSpecies.get(species)).as(species + " total weight").isEqualTo(1);
            assertThat(poolsPerSpecies.get(species)).as(species + " pools").isEqualTo(1);
        }
    }

    @Test
    void theLobsterTrapSpeciesAreTheLobsterTrapTag() throws IOException {
        Set<String> fromFile = new TreeSet<>();
        for (Row row : rows()) {
            if (row.group().equals("lobster_trap")) {
                fromFile.add(row.species());
            }
        }
        assertThat(LobsterTrap.CAUGHT_SPECIES).isEqualTo(fromFile);
    }

    @Test
    void theHuntDropTableAgreesWithEveryRow() throws IOException {
        for (Row row : rows()) {
            List<HuntDrop> drops = HuntDrops.drops(row.species());
            assertThat(drops).as(row.species()).hasSize(1);
            HuntDrop drop = drops.get(0);
            assertThat(drop.item()).as(row.species()).isEqualTo(row.item());
            assertThat(drop.conditions()).as(row.species()).isEqualTo(expectedConditions(row.conditions()));
        }
    }

    /** The conditions the hunt rules hold for a row: any_of killed-by-player or player-kill damage is the player-kill condition. */
    private static List<HuntCondition> expectedConditions(String token) {
        return switch (token) {
            case "killed_by_player" -> List.of(HuntCondition.KILLED_BY_PLAYER);
            case "any_of(killed_by_player|counts_as_player_kill)" -> List.of(HuntCondition.PLAYER_KILL);
            default -> throw new IllegalArgumentException(token);
        };
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = FishingLootGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("golden TSV on the test classpath").isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] c = line.split("\t", -1);
                rows.add(new Row(c[0], c[1], Integer.parseInt(c[2]), Integer.parseInt(c[3]), c[4], Double.parseDouble(c[5]), Integer.parseInt(c[6]), c[7]));
            }
        }
        return rows;
    }
}
